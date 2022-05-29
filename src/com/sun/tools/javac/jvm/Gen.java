/*
 * Copyright (c) 1999, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.tools.javac.jvm;
import java.util.*;

import javax.lang.model.element.ElementKind;

import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.comp.*;
import com.sun.tools.javac.tree.*;

import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.jvm.Code.*;
import com.sun.tools.javac.jvm.Items.*;
import com.sun.tools.javac.tree.JCTree.*;

import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.*;
import static com.sun.tools.javac.code.TypeTags.*;
import static com.sun.tools.javac.jvm.ByteCodes.*;
import static com.sun.tools.javac.jvm.CRTFlags.*;
import static com.sun.tools.javac.main.OptionName.*;

/** This pass maps flat Java (i.e. without inner classes) to bytecodes.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
// Gen类继承了JCTree.Visitor类并覆写了visitXxx()方法，可以根据标注语法树生成对应的字节码指令
public class Gen extends JCTree.Visitor {
    protected static final Context.Key<Gen> genKey =
        new Context.Key<Gen>();

    private final Log log;
    private final Symtab syms;
    private final Check chk;
    private final Resolve rs;
    private final TreeMaker make;
    private final Names names;
    private final Target target;
    private final Type stringBufferType;
    private final Map<Type,Symbol> stringBufferAppend;
    private Name accessDollar;
    private final Types types;

    /** Switch: GJ mode?
     */
    private final boolean allowGenerics;

    /** Set when Miranda method stubs are to be generated. */
    private final boolean generateIproxies;

    /** Format of stackmap tables to be generated. */
    private final Code.StackMapFormat stackMap;

    /** A type that serves as the expected type for all method expressions.
     */
    private final Type methodType;

    public static Gen instance(Context context) {
        Gen instance = context.get(genKey);
        if (instance == null)
            instance = new Gen(context);
        return instance;
    }

    protected Gen(Context context) {
        context.put(genKey, this);

        names = Names.instance(context);
        log = Log.instance(context);
        syms = Symtab.instance(context);
        chk = Check.instance(context);
        rs = Resolve.instance(context);
        make = TreeMaker.instance(context);
        target = Target.instance(context);
        types = Types.instance(context);
        methodType = new MethodType(null, null, null, syms.methodClass);
        allowGenerics = Source.instance(context).allowGenerics();
        stringBufferType = target.useStringBuilder()
            ? syms.stringBuilderType
            : syms.stringBufferType;
        stringBufferAppend = new HashMap<Type,Symbol>();
        accessDollar = names.
            fromString("access" + target.syntheticNameChar());

        Options options = Options.instance(context);
        lineDebugInfo =
            options.isUnset(G_CUSTOM) ||
            options.isSet(G_CUSTOM, "lines");
        varDebugInfo =
            options.isUnset(G_CUSTOM)
            ? options.isSet(G)
            : options.isSet(G_CUSTOM, "vars");
        genCrt = options.isSet(XJCOV);
        debugCode = options.isSet("debugcode");
        allowInvokedynamic = target.hasInvokedynamic() || options.isSet("invokedynamic");

        generateIproxies =
            target.requiresIproxy() ||
            options.isSet("miranda");

        if (target.generateStackMapTable()) {
            // ignore cldc because we cannot have both stackmap formats
            this.stackMap = StackMapFormat.JSR202;
        } else {
            if (target.generateCLDCStackmap()) {
                this.stackMap = StackMapFormat.CLDC;
            } else {
                this.stackMap = StackMapFormat.NONE;
            }
        }

        // by default, avoid jsr's for simple finalizers
        int setjsrlimit = 50;
        String jsrlimitString = options.get("jsrlimit");
        if (jsrlimitString != null) {
            try {
                setjsrlimit = Integer.parseInt(jsrlimitString);
            } catch (NumberFormatException ex) {
                // ignore ill-formed numbers for jsrlimit
            }
        }
        this.jsrlimit = setjsrlimit;
        this.useJsrLocally = false; // reset in visitTry
    }

    /** Switches
     */
    private final boolean lineDebugInfo;
    private final boolean varDebugInfo;
    private final boolean genCrt;
    private final boolean debugCode;
    private final boolean allowInvokedynamic;

    /** Default limit of (approximate) size of finalizer to inline.
     *  Zero means always use jsr.  100 or greater means never use
     *  jsr.
     */
    private final int jsrlimit;

    /** True if jsr is used.
     */
    private boolean useJsrLocally;

    /* Constant pool, reset by genClass.
     */
    private Pool pool = new Pool();

    /** Code buffer, set by genMethod.
     */
    private Code code;

    /** Items structure, set by genMethod.
     */
    private Items items;

    /** Environment for symbol lookup, set by genClass
     */
    private Env<AttrContext> attrEnv;

    /** The top level tree.
     */
    private JCCompilationUnit toplevel;

    /** The number of code-gen errors in this class.
     */
    private int nerrs = 0;

    /** A hash table mapping syntax trees to their ending source positions.
     */
    private Map<JCTree, Integer> endPositions;

    /** Generate code to load an integer constant.
     *  @param n     The integer to be loaded.
     */
    void loadIntConst(int n) {
        items.makeImmediateItem(syms.intType, n).load();
    }

    /** The opcode that loads a zero constant of a given type code.
     *  @param tc   The given type code (@see ByteCode).
     */
    public static int zero(int tc) {
        switch(tc) {
        case INTcode: case BYTEcode: case SHORTcode: case CHARcode:
            return iconst_0;
        case LONGcode:
            return lconst_0;
        case FLOATcode:
            return fconst_0;
        case DOUBLEcode:
            return dconst_0;
        default:
            throw new AssertionError("zero");
        }
    }

    /** The opcode that loads a one constant of a given type code.
     *  @param tc   The given type code (@see ByteCode).
     */
    public static int one(int tc) {
        return zero(tc) + 1;
    }

    /** Generate code to load -1 of the given type code (either int or long).
     *  @param tc   The given type code (@see ByteCode).
     */
    void emitMinusOne(int tc) {
        if (tc == LONGcode) {
            items.makeImmediateItem(syms.longType, new Long(-1)).load();
        } else {
            code.emitop0(iconst_m1);
        }
    }

    /** Construct a symbol to reflect the qualifying type that should
     *  appear in the byte code as per JLS 13.1.
     *
     *  For target >= 1.2: Clone a method with the qualifier as owner (except
     *  for those cases where we need to work around VM bugs).
     *
     *  For target <= 1.1: If qualified variable or method is defined in a
     *  non-accessible class, clone it with the qualifier class as owner.
     *
     *  @param sym    The accessed symbol
     *  @param site   The qualifier's type.
     */
    Symbol binaryQualifier(Symbol sym, Type site) {

        if (site.tag == ARRAY) {
            if (sym == syms.lengthVar ||
                sym.owner != syms.arrayClass)
                return sym;
            // array clone can be qualified by the array type in later targets
            Symbol qualifier = target.arrayBinaryCompatibility()
                ? new ClassSymbol(Flags.PUBLIC, site.tsym.name,
                                  site, syms.noSymbol)
                : syms.objectType.tsym;
            return sym.clone(qualifier);
        }

        if (sym.owner == site.tsym ||
            (sym.flags() & (STATIC | SYNTHETIC)) == (STATIC | SYNTHETIC)) {
            return sym;
        }
        if (!target.obeyBinaryCompatibility())
            return rs.isAccessible(attrEnv, (TypeSymbol)sym.owner)
                ? sym
                : sym.clone(site.tsym);

        if (!target.interfaceFieldsBinaryCompatibility()) {
            if ((sym.owner.flags() & INTERFACE) != 0 && sym.kind == VAR)
                return sym;
        }

        // leave alone methods inherited from Object
        // JLS 13.1.
        if (sym.owner == syms.objectType.tsym)
            return sym;

        if (!target.interfaceObjectOverridesBinaryCompatibility()) {
            if ((sym.owner.flags() & INTERFACE) != 0 &&
                syms.objectType.tsym.members().lookup(sym.name).scope != null)
                return sym;
        }

        return sym.clone(site.tsym);
    }

    /** Insert a reference to given type in the constant pool,
     *  checking for an array with too many dimensions;
     *  return the reference's index.
     *  @param type   The type for which a reference is inserted.
     */
    int makeRef(DiagnosticPosition pos, Type type) {
        checkDimension(pos, type);
        return pool.put(type.tag == CLASS ? (Object)type.tsym : (Object)type);
    }

    /** Check if the given type is an array with too many dimensions.
     */
    private void checkDimension(DiagnosticPosition pos, Type t) {
        switch (t.tag) {
        case METHOD:
            checkDimension(pos, t.getReturnType());
            for (List<Type> args = t.getParameterTypes(); args.nonEmpty(); args = args.tail)
                checkDimension(pos, args.head);
            break;
        case ARRAY:
            if (types.dimensions(t) > ClassFile.MAX_DIMENSIONS) {
                log.error(pos, "limit.dimensions");
                nerrs++;
            }
            break;
        default:
            break;
        }
    }

    /** Create a tempory variable.
     *  @param type   The variable's type.
     */
    LocalItem makeTemp(Type type) {
        VarSymbol v = new VarSymbol(Flags.SYNTHETIC,
                                    names.empty,
                                    type,
                                    env.enclMethod.sym);
        code.newLocal(v);
        return items.makeLocalItem(v);
    }

    /** Generate code to call a non-private method or constructor.
     *  @param pos         Position to be used for error reporting.
     *  @param site        The type of which the method is a member.
     *  @param name        The method's name.
     *  @param argtypes    The method's argument types.
     *  @param isStatic    A flag that indicates whether we call a
     *                     static or instance method.
     */
    void callMethod(DiagnosticPosition pos,
                    Type site, Name name, List<Type> argtypes,
                    boolean isStatic) {
        Symbol msym = rs.
            resolveInternalMethod(pos, attrEnv, site, name, argtypes, null);
        if (isStatic) items.makeStaticItem(msym).invoke();
        else items.makeMemberItem(msym, name == names.init).invoke();
    }

    /** Is the given method definition an access method
     *  resulting from a qualified super? This is signified by an odd
     *  access code.
     */
    private boolean isAccessSuper(JCMethodDecl enclMethod) {
        return
            (enclMethod.mods.flags & SYNTHETIC) != 0 &&
            isOddAccessName(enclMethod.name);
    }

    /** Does given name start with "access$" and end in an odd digit?
     */
    private boolean isOddAccessName(Name name) {
        return
            name.startsWith(accessDollar) &&
            (name.getByteAt(name.getByteLength() - 1) & 1) == 1;
    }

/* ************************************************************************
 * Non-local exits
 *************************************************************************/

    /** Generate code to invoke the finalizer associated with given
     *  environment.
     *  Any calls to finalizers are appended to the environments `cont' chain.
     *  Mark beginning of gap in catch all range for finalizer.
     */
    void genFinalizer(Env<GenContext> env) {
        if (code.isAlive() && env.info.finalize != null)
            // 调用env.info.finalize的gen()方法其实就是调用在visitTry()方法中创建的GenFinalizer匿名类中覆写的方法
            // 覆写的gen()方法可以记录冗余指令的起始位置及生成冗余指令
            // 需要注意的是，当code.isAlive()方法返回true时才会做这个操作，也就是代码可达，如果try语句的body体中最后是return语句，则调用code.isAlive()方法将返回false，需要执行其他的逻辑生成冗余指令。
            env.info.finalize.gen();
    }

    /** Generate code to call all finalizers of structures aborted by
     *  a non-local
     *  exit.  Return target environment of the non-local exit.
     *  @param target      The tree representing the structure that's aborted
     *  @param env         The environment current at the non-local exit.
     */
    Env<GenContext> unwind(JCTree target, Env<GenContext> env) {
        Env<GenContext> env1 = env;
        while (true) {
            genFinalizer(env1);
            if (env1.tree == target) break;
            env1 = env1.next;
        }
        return env1;
    }

    /** Mark end of gap in catch-all range for finalizer.
     *  @param env   the environment which might contain the finalizer
     *               (if it does, env.info.gaps != null).
     */
    void endFinalizerGap(Env<GenContext> env) {
        // 当env.info.gaps列表不为空并且列表中元素的数量为奇数时，才会向列表末尾追加冗余指令的结束位置，
        // 判断列表中元素的数量为奇数主要是为了保证记录起始位置，这样才会记录结束位置。
        if (env.info.gaps != null && env.info.gaps.length() % 2 == 1)
            env.info.gaps.append(code.curPc());
    }

    /** Mark end of all gaps in catch-all ranges for finalizers of environments
     *  lying between, and including to two environments.
     *  @param from    the most deeply nested environment to mark
     *  @param to      the least deeply nested environment to mark
     */
    void endFinalizerGaps(Env<GenContext> from, Env<GenContext> to) {
        Env<GenContext> last = null;
        while (last != to) {
            endFinalizerGap(from);
            last = from;
            from = from.next;
        }
    }

    /** Do any of the structures aborted by a non-local exit have
     *  finalizers that require an empty stack?
     *  @param target      The tree representing the structure that's aborted
     *  @param env         The environment current at the non-local exit.
     */
    boolean hasFinally(JCTree target, Env<GenContext> env) {
        while (env.tree != target) {
            if (env.tree.getTag() == JCTree.TRY && env.info.finalize.hasFinalizer())
                return true;
            env = env.next;
        }
        return false;
    }

/* ************************************************************************
 * Normalizing class-members.
 *************************************************************************/

    /** Distribute member initializer code into constructors and <clinit>
     *  method.
     *  @param defs         The list of class member declarations.
     *  @param c            The enclosing class.
     */
    // 重构<init>方法和<clinit>方法，辅助后续的字节码指令生成
    List<JCTree> normalizeDefs(List<JCTree> defs, ClassSymbol c) {
        ListBuffer<JCStatement> initCode = new ListBuffer<JCStatement>();
        ListBuffer<JCStatement> clinitCode = new ListBuffer<JCStatement>();
        ListBuffer<JCTree> methodDefs = new ListBuffer<JCTree>();
        // 将定义分类为三个列表缓冲区：
        // - 实例初始化程序的 initCode
        // - 类初始化程序的 clinitCode
        // - 方法定义的 methodDefs
        for (List<JCTree> l = defs; l.nonEmpty(); l = l.tail) {
            JCTree def = l.head;
            switch (def.getTag()) {
            case JCTree.BLOCK: // 对括号的重构
                // normalizeDefs()方法对匿名块重构的实现
                JCBlock block = (JCBlock)def;
                // 这样块的花括号可以形成一个作用域，能更好地避免块之间及块与方法中相关定义的冲突，也能更好地完成初始化工作
                if ((block.flags & STATIC) != 0)
                    // 将静态匿名块追加到<clinit>方法中，
                    clinitCode.append(block);
                else
                    // 将非静态匿名块追加到<init>方法
                    initCode.append(block);
                break;
            case JCTree.METHODDEF:
                methodDefs.append(def);
                break;
            case JCTree.VARDEF: // 对变量的重构
                JCVariableDecl vdef = (JCVariableDecl) def;
                VarSymbol sym = vdef.sym;
                checkDimension(vdef.pos(), sym.type);
                // 对变量进行了初始化（vdef.init不为空）
                if (vdef.init != null) {
                    if ((sym.flags() & STATIC) == 0) {
                        // 对于实例变量来说，创建JCAssign树节点后追加到<init>方法中
                        JCStatement init = make.at(vdef.pos()).
                            Assignment(sym, vdef.init);
                        initCode.append(init);
                        if (endPositions != null) {
                            Integer endPos = endPositions.remove(vdef);
                            if (endPos != null)
                                endPositions.put(init, endPos);
                        }
                    } else if (sym.getConstValue() == null) {
                        // 对于静态变量来说，如果在编译期不能确定具体的值，同样会创建JCAssign树节点并追加到<clinit>方法中
                        JCStatement init = make.at(vdef.pos).
                            Assignment(sym, vdef.init);
                        clinitCode.append(init);
                        if (endPositions != null) {
                            Integer endPos = endPositions.remove(vdef);
                            if (endPos != null) endPositions.put(init, endPos);
                        }
                    } else {
                        checkStringConstant(vdef.init.pos(), sym.getConstValue());
                    }
                }
                break;
            default:
                Assert.error();
            }
        }
        // 调整完成后还需要循环构造方法，处理<init>方法
        if (initCode.length() != 0) {
            List<JCStatement> inits = initCode.toList();
            for (JCTree t : methodDefs) {
                // 如果一个类中有多个构造方法，则需要将initCode列表中的内容追加到<init>方法中
                normalizeMethod((JCMethodDecl)t, inits);
            }
        }
        // 处理<clinit>方法
        if (clinitCode.length() != 0) {
            MethodSymbol clinit = new MethodSymbol(
                STATIC, names.clinit,
                new MethodType(
                    List.<Type>nil(), syms.voidType,
                    List.<Type>nil(), syms.methodClass),
                c);
            c.members().enter(clinit);
            List<JCStatement> clinitStats = clinitCode.toList();
            JCBlock block = make.at(clinitStats.head.pos()).Block(0, clinitStats);
            block.endpos = TreeInfo.endPos(clinitStats.last());
            methodDefs.append(make.MethodDef(clinit, block));
        }
        // Return all method definitions.
        return methodDefs.toList();
    }

    /** Check a constant value and report if it is a string that is
     *  too large.
     */
    private void checkStringConstant(DiagnosticPosition pos, Object constValue) {
        if (nerrs != 0 || // only complain about a long string once
            constValue == null ||
            !(constValue instanceof String) ||
            ((String)constValue).length() < Pool.MAX_STRING_LENGTH)
            return;
        log.error(pos, "limit.string");
        nerrs++;
    }

    /** Insert instance initializer code into initial constructor.
     *  @param md        The tree potentially representing a
     *                   constructor's definition.
     *  @param initCode  The list of instance initializer statements.
     */
    // 将初始化语句及非静态匿名块插入到构造方法中
    void normalizeMethod(JCMethodDecl md, List<JCStatement> initCode) {
        // 不对首个形式为this(...)语句的构造方法追加内容，也就是如果有this(...)形式的语句，
        // 则调用TreeInfo类的isInitialConstructor()方法将返回false
        // 主要还是保证在创建对应类的对象时，能够执行initCode列表中的语句而且只执行一次
        if (md.name == names.init && TreeInfo.isInitialConstructor(md)) {
            // We are seeing a constructor that does not call another
            // constructor of the same class.
            List<JCStatement> stats = md.body.stats;
            ListBuffer<JCStatement> newstats = new ListBuffer<JCStatement>();

            if (stats.nonEmpty()) {
                // Copy initializers of synthetic variables generated in
                // the translation of inner classes.
                while (TreeInfo.isSyntheticInit(stats.head)) {
                    newstats.append(stats.head);
                    stats = stats.tail;
                }
                // Copy superclass constructor call
                // 需要注意的是，在向newstats列表中追加语句时需要严格保证语句的顺序。
                newstats.append(stats.head);
                stats = stats.tail;
                // Copy remaining synthetic initializers.
                while (stats.nonEmpty() &&
                       TreeInfo.isSyntheticInit(stats.head)) {
                    newstats.append(stats.head);
                    stats = stats.tail;
                }
                // Now insert the initializer code.
                // 将初始化语句及非静态匿名块插入到构造方法中
                newstats.appendList(initCode);
                // And copy all remaining statements.
                while (stats.nonEmpty()) {
                    newstats.append(stats.head);
                    stats = stats.tail;
                }
            }
            md.body.stats = newstats.toList();
            if (md.body.endpos == Position.NOPOS)
                md.body.endpos = TreeInfo.endPos(md.body.stats.last());
        }
    }

/* ********************************************************************
 * Adding miranda methods
 *********************************************************************/

    /** Add abstract methods for all methods defined in one of
     *  the interfaces of a given class,
     *  provided they are not already implemented in the class.
     *
     *  @param c      The class whose interfaces are searched for methods
     *                for which Miranda methods should be added.
     */
    void implementInterfaceMethods(ClassSymbol c) {
        implementInterfaceMethods(c, c);
    }

    /** Add abstract methods for all methods defined in one of
     *  the interfaces of a given class,
     *  provided they are not already implemented in the class.
     *
     *  @param c      The class whose interfaces are searched for methods
     *                for which Miranda methods should be added.
     *  @param site   The class in which a definition may be needed.
     */
    void implementInterfaceMethods(ClassSymbol c, ClassSymbol site) {
        for (List<Type> l = types.interfaces(c.type); l.nonEmpty(); l = l.tail) {
            ClassSymbol i = (ClassSymbol)l.head.tsym;
            for (Scope.Entry e = i.members().elems;
                 e != null;
                 e = e.sibling)
            {
                if (e.sym.kind == MTH && (e.sym.flags() & STATIC) == 0)
                {
                    MethodSymbol absMeth = (MethodSymbol)e.sym;
                    MethodSymbol implMeth = absMeth.binaryImplementation(site, types);
                    if (implMeth == null)
                        addAbstractMethod(site, absMeth);
                    else if ((implMeth.flags() & IPROXY) != 0)
                        adjustAbstractMethod(site, implMeth, absMeth);
                }
            }
            implementInterfaceMethods(i, site);
        }
    }

    /** Add an abstract methods to a class
     *  which implicitly implements a method defined in some interface
     *  implemented by the class. These methods are called "Miranda methods".
     *  Enter the newly created method into its enclosing class scope.
     *  Note that it is not entered into the class tree, as the emitter
     *  doesn't need to see it there to emit an abstract method.
     *
     *  @param c      The class to which the Miranda method is added.
     *  @param m      The interface method symbol for which a Miranda method
     *                is added.
     */
    private void addAbstractMethod(ClassSymbol c,
                                   MethodSymbol m) {
        MethodSymbol absMeth = new MethodSymbol(
            m.flags() | IPROXY | SYNTHETIC, m.name,
            m.type, // was c.type.memberType(m), but now only !generics supported
            c);
        c.members().enter(absMeth); // add to symbol table
    }

    private void adjustAbstractMethod(ClassSymbol c,
                                      MethodSymbol pm,
                                      MethodSymbol im) {
        MethodType pmt = (MethodType)pm.type;
        Type imt = types.memberType(c.type, im);
        pmt.thrown = chk.intersect(pmt.getThrownTypes(), imt.getThrownTypes());
    }

/* ************************************************************************
 * Traversal methods
 *************************************************************************/

    /** Visitor argument: The current environment.
     */
    // 当前环境
    Env<GenContext> env;

    /** Visitor argument: The expected type (prototype).
     */
    // 期望的类型
    Type pt;

    /** Visitor result: The item representing the computed value.
     */
    // 实际的类型
    Item result;

    /** Visitor method: generate code for a definition, catching and reporting
     *  any completion failures.
     *  @param tree    The definition to be visited.
     *  @param env     The environment current at the definition.
     */
    // 调用genDef()方法对cdef中定义的每个成员进行处理，如果成员为方法就会为方法生成字节码指令
    // Gen类只会为方法生成字节码指令，但是一个类中的成员变量与块中也会含有需要生成字节码指令的表达式或语句，
    public void genDef(JCTree tree, Env<GenContext> env) {
        Env<GenContext> prevEnv = this.env;
        try {
            this.env = env;
            tree.accept(this);
        } catch (CompletionFailure ex) {
            chk.completionError(tree.pos(), ex);
        } finally {
            this.env = prevEnv;
        }
    }

    /** Derived visitor method: check whether CharacterRangeTable
     *  should be emitted, if so, put a new entry into CRTable
     *  and call method to generate bytecode.
     *  If not, just call method to generate bytecode.
     *
     *  @param  tree     The tree to be visited.
     *  @param  env      The environment to use.
     *  @param  crtFlags The CharacterRangeTable flags
     *                   indicating type of the entry.
     */
    // 遍历语句
    public void genStat(JCTree tree, Env<GenContext> env, int crtFlags) {
        if (!genCrt) {
            genStat(tree, env);
            return;
        }
        int startpc = code.curPc();
        genStat(tree, env);
        if (tree.getTag() == JCTree.BLOCK) crtFlags |= CRT_BLOCK;
        code.crt.put(tree, crtFlags, startpc, code.curPc());
    }

    /** Derived visitor method: generate code for a statement.
     */
    // 遍历语句
    public void genStat(JCTree tree, Env<GenContext> env) {
        if (code.isAlive()) {
            code.statBegin(tree.pos);
            genDef(tree, env);
        } else if (env.info.isSwitch && tree.getTag() == JCTree.VARDEF) {
            // variables whose declarations are in a switch
            // can be used even if the decl is unreachable.
            code.newLocal(((JCVariableDecl) tree).sym);
        }
    }

    /** Derived visitor method: check whether CharacterRangeTable
     *  should be emitted, if so, put a new entry into CRTable
     *  and call method to generate bytecode.
     *  If not, just call method to generate bytecode.
     *  @see    #genStats(List, Env)
     *
     *  @param  trees    The list of trees to be visited.
     *  @param  env      The environment to use.
     *  @param  crtFlags The CharacterRangeTable flags
     *                   indicating type of the entry.
     */
    public void genStats(List<JCStatement> trees, Env<GenContext> env, int crtFlags) {
        if (!genCrt) {
            genStats(trees, env);
            return;
        }
        if (trees.length() == 1) {        // mark one statement with the flags
            genStat(trees.head, env, crtFlags | CRT_STATEMENT);
        } else {
            int startpc = code.curPc();
            genStats(trees, env);
            code.crt.put(trees, crtFlags, startpc, code.curPc());
        }
    }

    /** Derived visitor method: generate code for a list of statements.
     */
    // 遍历语句
    public void genStats(List<? extends JCTree> trees, Env<GenContext> env) {
        for (List<? extends JCTree> l = trees; l.nonEmpty(); l = l.tail)
            genStat(l.head, env, CRT_STATEMENT);
    }

    /** Derived visitor method: check whether CharacterRangeTable
     *  should be emitted, if so, put a new entry into CRTable
     *  and call method to generate bytecode.
     *  If not, just call method to generate bytecode.
     *
     *  @param  tree     The tree to be visited.
     *  @param  crtFlags The CharacterRangeTable flags
     *                   indicating type of the entry.
     */
    public CondItem genCond(JCTree tree, int crtFlags) {
        if (!genCrt) return genCond(tree, false);
        int startpc = code.curPc();
        CondItem item = genCond(tree, (crtFlags & CRT_FLOW_CONTROLLER) != 0);
        code.crt.put(tree, crtFlags, startpc, code.curPc());
        return item;
    }

    /** Derived visitor method: generate code for a boolean
     *  expression in a control-flow context.
     *  @param _tree         The expression to be visited.
     *  @param markBranches The flag to indicate that the condition is
     *                      a flow controller so produced conditions
     *                      should contain a proper tree to generate
     *                      CharacterRangeTable branches for them.
     */
    public CondItem genCond(JCTree _tree, boolean markBranches) {
        JCTree inner_tree = TreeInfo.skipParens(_tree);
        if (inner_tree.getTag() == JCTree.CONDEXPR) {
            // 对三元表达式处理
            JCConditional tree = (JCConditional)inner_tree;
            CondItem cond = genCond(tree.cond, CRT_FLOW_CONTROLLER);
            if (cond.isTrue()) {
                // 为true时回填cond.trueJumps分支的地址
                code.resolve(cond.trueJumps);
                CondItem result = genCond(tree.truepart, CRT_FLOW_TARGET);
                if (markBranches) result.tree = tree.truepart;
                return result;
            }
            if (cond.isFalse()) {
                // 为false时回填cond.falseJumps分支的地址
                code.resolve(cond.falseJumps);
                CondItem result = genCond(tree.falsepart, CRT_FLOW_TARGET);
                if (markBranches) result.tree = tree.falsepart;
                return result;
            }
            // 当tree.cond表达式结果不为编译时常量时，其处理逻辑相对复杂，因为涉及3个布尔类型的表达式共6个分支的跳转
            Chain secondJumps = cond.jumpFalse();
            code.resolve(cond.trueJumps);
            CondItem first = genCond(tree.truepart, CRT_FLOW_TARGET);
            if (markBranches)
                first.tree = tree.truepart;
            Chain falseJumps = first.jumpFalse();
            code.resolve(first.trueJumps);
            Chain trueJumps = code.branch(goto_);
            code.resolve(secondJumps);
            CondItem second = genCond(tree.falsepart, CRT_FLOW_TARGET);
            CondItem result = items.makeCondItem(second.opcode,
                                      Code.mergeChains(trueJumps, second.trueJumps),
                                      Code.mergeChains(falseJumps, second.falseJumps));
            if (markBranches)
                result.tree = tree.falsepart;
            return result;
        } else {
            // 一元或二元表达式
            // 作为条件判断表达式的一元表达式中含有一元运算符非“!”
            // 二元表达式含有二元运算符或“||”或与“&&”时，可调用genExpr()方法得到CondItem对象
            // 调用这个对象的mkCond()方法返回自身
            CondItem result = genExpr(_tree, syms.booleanType).mkCond();
            if (markBranches) result.tree = _tree;
            return result;
        }
    }

    /** Visitor method: generate code for an expression, catching and reporting
     *  any completion failures.
     *  @param tree    The expression to be visited.
     *  @param pt      The expression's expected type (proto-type).
     */
    // 遍历方法表达式
    // tree 语法树m,pt 期望类型
    public Item genExpr(JCTree tree, Type pt) {
        Type prevPt = this.pt;
        try {
            if (tree.type.constValue() != null) {
                // Short circuit any expressions which are constants
                checkStringConstant(tree.pos(), tree.type.constValue());
                result = items.makeImmediateItem(tree.type, tree.type.constValue());
            } else {
                this.pt = pt;
                tree.accept(this);
            }
            return result.coerce(pt);
        } catch (CompletionFailure ex) {
            chk.completionError(tree.pos(), ex);
            code.state.stacksize = 1;
            return items.makeStackItem(pt);
        } finally {
            this.pt = prevPt;
        }
    }

    /** Derived visitor method: generate code for a list of method arguments.
     *  @param trees    The argument expressions to be visited.
     *  @param pts      The expression's expected types (i.e. the formal parameter
     *                  types of the invoked method).
     */
    // 遍历参数
    public void genArgs(List<JCExpression> trees, List<Type> pts) {
        for (List<JCExpression> l = trees; l.nonEmpty(); l = l.tail) {
            genExpr(l.head, pts.head).load();
            pts = pts.tail;
        }
        // require lists be of same length
        Assert.check(pts.isEmpty());
    }

/* ************************************************************************
 * Visitor methods for statements and definitions
 *************************************************************************/

    /** Thrown when the byte code size exceeds limit.
     */
    public static class CodeSizeOverflow extends RuntimeException {
        private static final long serialVersionUID = 0;
        public CodeSizeOverflow() {}
    }

    public void visitMethodDef(JCMethodDecl tree) {
        // Create a new local environment that points pack at method
        // definition.
        Env<GenContext> localEnv = env.dup(tree);
        localEnv.enclMethod = tree;

        // The expected type of every return statement in this method
        // is the method's return type.
        this.pt = tree.sym.erasure(types).getReturnType();

        checkDimension(tree.pos(), tree.sym.erasure(types));
        genMethod(tree, localEnv, false);
    }
//where
        /** Generate code for a method.
         *  @param tree     The tree representing the method definition.
         *  @param env      The environment current for the method body.
         *  @param fatcode  A flag that indicates whether all jumps are
         *                  within 32K.  We first invoke this method under
         *                  the assumption that fatcode == false, i.e. all
         *                  jumps are within 32K.  If this fails, fatcode
         *                  is set to true and we try again.
         */
        void genMethod(JCMethodDecl tree, Env<GenContext> env, boolean fatcode) {
            MethodSymbol meth = tree.sym;
//      System.err.println("Generating " + meth + " in " + meth.owner); //DEBUG
            if (Code.width(types.erasure(env.enclMethod.sym.type).getParameterTypes())  +
                (((tree.mods.flags & STATIC) == 0 || meth.isConstructor()) ? 1 : 0) >
                ClassFile.MAX_PARAMETERS) {
                log.error(tree.pos(), "limit.parameters");
                nerrs++;
            }

            else if (tree.body != null) {
                // Create a new code structure and initialize it.
                int startpcCrt = initCode(tree, env, fatcode);

                try {
                    genStat(tree.body, env);
                } catch (CodeSizeOverflow e) {
                    // Failed due to code limit, try again with jsr/ret
                    startpcCrt = initCode(tree, env, fatcode);
                    genStat(tree.body, env);
                }

                if (code.state.stacksize != 0) {
                    log.error(tree.body.pos(), "stack.sim.error", tree);
                    throw new AssertionError();
                }

                // If last statement could complete normally, insert a
                // return at the end.
                if (code.isAlive()) {
                    code.statBegin(TreeInfo.endPos(tree.body));
                    if (env.enclMethod == null ||
                        env.enclMethod.sym.type.getReturnType().tag == VOID) {
                        code.emitop0(return_);
                    } else {
                        // sometime dead code seems alive (4415991);
                        // generate a small loop instead
                        int startpc = code.entryPoint();
                        CondItem c = items.makeCondItem(goto_);
                        code.resolve(c.jumpTrue(), startpc);
                    }
                }
                if (genCrt)
                    code.crt.put(tree.body,
                                 CRT_BLOCK,
                                 startpcCrt,
                                 code.curPc());

                code.endScopes(0);

                // If we exceeded limits, panic
                if (code.checkLimits(tree.pos(), log)) {
                    nerrs++;
                    return;
                }

                // If we generated short code but got a long jump, do it again
                // with fatCode = true.
                if (!fatcode && code.fatcode) genMethod(tree, env, true);

                // Clean up
                if(stackMap == StackMapFormat.JSR202) {
                    code.lastFrame = null;
                    code.frameBeforeLast = null;
                }
            }
        }

        private int initCode(JCMethodDecl tree, Env<GenContext> env, boolean fatcode) {
            MethodSymbol meth = tree.sym;

            // Create a new code structure.
            meth.code = code = new Code(meth,
                                        fatcode,
                                        lineDebugInfo ? toplevel.lineMap : null,
                                        varDebugInfo,
                                        stackMap,
                                        debugCode,
                                        genCrt ? new CRTable(tree, env.toplevel.endPositions)
                                               : null,
                                        syms,
                                        types,
                                        pool);
            items = new Items(pool, code, syms, types);
            if (code.debugCode)
                System.err.println(meth + " for body " + tree);

            // If method is not static, create a new local variable address
            // for `this'.
            if ((tree.mods.flags & STATIC) == 0) {
                Type selfType = meth.owner.type;
                if (meth.isConstructor() && selfType != syms.objectType)
                    selfType = UninitializedType.uninitializedThis(selfType);
                code.setDefined(
                        code.newLocal(
                            new VarSymbol(FINAL, names._this, selfType, meth.owner)));
            }

            // Mark all parameters as defined from the beginning of
            // the method.
            for (List<JCVariableDecl> l = tree.params; l.nonEmpty(); l = l.tail) {
                checkDimension(l.head.pos(), l.head.sym.type);
                code.setDefined(code.newLocal(l.head.sym));
            }

            // Get ready to generate code for method body.
            int startpcCrt = genCrt ? code.curPc() : 0;
            code.entryPoint();

            // Suppress initial stackmap
            code.pendingStackMap = false;

            return startpcCrt;
        }

    // 访问本地变量
    // 例16-3
    public void visitVarDef(JCVariableDecl tree) {
        VarSymbol v = tree.sym;
        code.newLocal(v);
        if (tree.init != null) {
            checkStringConstant(tree.init.pos(), v.getConstValue());
            if (v.getConstValue() == null || varDebugInfo) {
                genExpr(tree.init, v.erasure(types)).load();
                items.makeLocalItem(v).store();
            }
        }
        checkDimension(tree.pos(), v.type);
    }

    public void visitSkip(JCSkip tree) {
    }

    public void visitBlock(JCBlock tree) {
        int limit = code.nextreg;
        Env<GenContext> localEnv = env.dup(tree, new GenContext());
        genStats(tree.stats, localEnv);
        // End the scope of all block-local variables in variable info.
        if (env.tree.getTag() != JCTree.METHODDEF) {
            code.statBegin(tree.endpos);
            code.endScopes(limit);
            code.pendingStatPos = Position.NOPOS;
        }
    }

    public void visitDoLoop(JCDoWhileLoop tree) {
        genLoop(tree, tree.body, tree.cond, List.<JCExpressionStatement>nil(), false);
    }

    public void visitWhileLoop(JCWhileLoop tree) {
        genLoop(tree, tree.body, tree.cond, List.<JCExpressionStatement>nil(), true);
    }

    public void visitForLoop(JCForLoop tree) {
        int limit = code.nextreg;
        genStats(tree.init, env);
        genLoop(tree, tree.body, tree.cond, tree.step, true);
        code.endScopes(limit);
    }
    //where
        /** Generate code for a loop.
         *  @param loop       The tree representing the loop.
         *  @param body       The loop's body.
         *  @param cond       The loop's controling condition.
         *  @param step       "Step" statements to be inserted at end of
         *                    each iteration.
         *  @param testFirst  True if the loop test belongs before the body.
         */
        // 生成循环代码
        private void genLoop(JCStatement loop,
                             JCStatement body,
                             JCExpression cond,
                             List<JCExpressionStatement> step,
                             // 在处理循环语句for与while时，testFirst参数的值为true，表示先进行条件判断后执行body体语句
                             // 在处理循环语句do-while时，testFirst参数的值为false，表示先执行body体语句后进行条件判断。
                             boolean testFirst) {
            Env<GenContext> loopEnv = env.dup(loop, new GenContext());
            // 调用code.entryPoint()方法获取循环语句生成的第一个字节码指令的地址，
            int startpc = code.entryPoint();
            if (testFirst) {
                CondItem c;
                if (cond != null) {
                    // 当cond不为null时可调用genCond()方法创建一个CondItem对象c
                    code.statBegin(cond.pos);
                    c = genCond(TreeInfo.skipParens(cond), CRT_FLOW_CONTROLLER);
                } else {
                    // 否则生成一个无条件跳转的CondItem对象c。
                    // 因为for语句中的条件判断表达式可以为空，当为空时相当于条件判断表达式的结果永恒为true，这样会执行body体中的语句。
                    c = items.makeCondItem(goto_);
                }
                // 同时调用c.jumpFalse()方法获取条件判断表达式的结果为false时的跳转分支
                Chain loopDone = c.jumpFalse();
                // 调用code.resolve()方法处理c.trueJumps
                code.resolve(c.trueJumps);
                // 生成body的字节码指令
                genStat(body, loopEnv, CRT_STATEMENT | CRT_FLOW_TARGET);
                // 回填loopEnv.info.cont变量保存的Chain对象的地址,
                // 对于for语句来说，遇到跳转目标为当前for语句的continue语句，跳转目标肯定是step对应生成的第一条字节码指令地址
                code.resolve(loopEnv.info.cont);
                // 生成step的字节码指令
                genStats(step, loopEnv);
                // 调用genStat()方法生成step的字节码指令之后，可调用code.branch()方法生成一个无条件跳转分支
                // 跳转目标就是循环语句开始处，也就是startpc保存的位置，这样就可以多次执行循环语句生成的相关字节码指令了
                code.resolve(code.branch(goto_), startpc);
                // 调用code.resolve()方法处理loopDone，当生成完循环语句的字节码指令后，下一个指令生成时就会回填loopDone的跳转地址
                code.resolve(loopDone);
            } else {
                genStat(body, loopEnv, CRT_STATEMENT | CRT_FLOW_TARGET);
                code.resolve(loopEnv.info.cont);
                genStats(step, loopEnv);
                CondItem c;
                if (cond != null) {
                    code.statBegin(cond.pos);
                    c = genCond(TreeInfo.skipParens(cond), CRT_FLOW_CONTROLLER);
                } else {
                    c = items.makeCondItem(goto_);
                }
                code.resolve(c.jumpTrue(), startpc);
                code.resolve(c.falseJumps);
            }
            // 任何循环语句，最后都要处理loopEnv.info.exit，这个地址一般与loopDone的跳转地址相同，因此两个Chain对象会合并为一个对象并赋值给pendingJumps。
            code.resolve(loopEnv.info.exit);
        }

    public void visitForeachLoop(JCEnhancedForLoop tree) {
        throw new AssertionError(); // should have been removed by Lower.
    }

    public void visitLabelled(JCLabeledStatement tree) {
        Env<GenContext> localEnv = env.dup(tree, new GenContext());
        genStat(tree.body, localEnv, CRT_STATEMENT);
        code.resolve(localEnv.info.exit);
    }

    // 访问Switch节点
    public void visitSwitch(JCSwitch tree) {
        int limit = code.nextreg;
        Assert.check(tree.selector.type.tag != CLASS);
        int startpcCrt = genCrt ? code.curPc() : 0;
        // 在解语法糖阶段已经将tree.selector表达式的类型都转换为了int类型，
        // 因此在调用genExpr()方法处理tree.selector时，给出了期望的类型为syms.intType。
        Item sel = genExpr(tree.selector, syms.intType);
        List<JCCase> cases = tree.cases;
        if (cases.isEmpty()) {
            // We are seeing:  switch <sel> {}
            // 当cases分支为空时处理非常简单，可直接调用sel.load()方法加载Item对象sel，
            // 因为没有分支使用，所以调用drop()方法抛弃
            sel.load().drop();
            if (genCrt)
                code.crt.put(TreeInfo.skipParens(tree.selector),
                             CRT_FLOW_CONTROLLER, startpcCrt, code.curPc());
        } else {
            // 当switch语句中有分支时，首先要进行指令选择，也就是要选择lookupswitch指令还是tableswitch指令
            sel.load();
            if (genCrt)
                code.crt.put(TreeInfo.skipParens(tree.selector),
                             CRT_FLOW_CONTROLLER, startpcCrt, code.curPc());
            Env<GenContext> switchEnv = env.dup(tree, new GenContext());
            switchEnv.info.isSwitch = true;

            // Compute number of labels and minimum and maximum label values.
            // For each case, store its label in an array.
            int lo = Integer.MAX_VALUE;  // 保存label的最小值
            int hi = Integer.MIN_VALUE;  // 保存label的最大值
            int nlabels = 0;             // 保存在label的数量

            // labels就是case个数
            int[] labels = new int[cases.length()];
            int defaultIndex = -1;

            // 更新lo、hi、nlabels与defaultIndex变量的值
            List<JCCase> l = cases;
            for (int i = 0; i < labels.length; i++) {
                if (l.head.pat != null) {
                    int val = ((Number)l.head.pat.type.constValue()).intValue();
                    labels[i] = val;
                    if (val < lo)
                        // 计算出所有label中的最小值并保存到lo中
                        lo = val;
                    if (hi < val)
                        // 计算出所有label中的最大值并保存到hi中
                        hi = val;
                    nlabels++;
                } else {
                    Assert.check(defaultIndex == -1);
                    defaultIndex = i;
                }
                l = l.tail;
            }

            // 通过粗略计算使用lookupswitch指令与tableswitch指令的时间与空间消耗来选择指令
            // 在hi和lo的差值不大且label数偏多的情况下，会选择tableswitch指令；
            // 当差值很大而label数不多的情况下，会选择lookupswitch指令。
            long table_space_cost = 4 + ((long) hi - lo + 1); // words
            long table_time_cost = 3; // comparisons
            long lookup_space_cost = 3 + 2 * (long) nlabels;
            long lookup_time_cost = nlabels;
            int opcode =
                nlabels > 0 &&
                table_space_cost + 3 * table_time_cost <=
                lookup_space_cost + 3 * lookup_time_cost
                ?
                tableswitch : lookupswitch;

            int startpc = code.curPc();    // the position of the selector operation
            code.emitop0(opcode);
            code.align(4);
            int tableBase = code.curPc();  // 保存跳转表开始的位置
            // 在生成lookupswitch指令时，保存对应分支到跳转的目标地址的偏移量
            int[] offsets = null;
            // 为默认的跳转地址预留空间
            code.emit4(-1);                // leave space for default offset
            if (opcode == tableswitch) {
                // 使用tableswitch指令
                // 例17-3
                code.emit4(lo);            // minimum label
                code.emit4(hi);            // maximum label
                for (long i = lo; i <= hi; i++) {  // leave space for jump table
                    // 为跳转表预留空间
                    // 对于tableswitch指令来说，为lo到hi之间的所有整数都执行了code.emit4()方法，也就是这之间的任何整数都有一个跳转地址
                    code.emit4(-1);
                }
            } else {
                // 使用lookupswitch指令
                // 首先输入分支数量nlabels
                code.emit4(nlabels);    // number of labels
                for (int i = 0; i < nlabels; i++) {
                    // 接下来就是预留nlabels组匹配坐标
                    code.emit4(-1); code.emit4(-1); // leave space for lookup table
                }
                // 最后还初始化了一个offsets数组，这个数组会保存对应分支到跳转的目标地址的偏移量，以便后续进行地址回填。
                offsets = new int[labels.length];
            }
            Code.State stateSwitch = code.state.dup();

            // visitSwitch()方法中关于地址回填的实现
            code.markDead();

            // For each case do:
            l = cases;
            // 循环各个case分支并生成字节码指令，同时回填部分跳转地址
            for (int i = 0; i < labels.length; i++) {
                JCCase c = l.head;
                l = l.tail;

                int pc = code.entryPoint(stateSwitch);
                // Insert offset directly into code or else into the
                // offsets table.
                if (i != defaultIndex) {
                    if (opcode == tableswitch) {
                        code.put4(
                            tableBase + 4 * (labels[i] - lo + 3),
                            pc - startpc);
                    } else {
                        offsets[i] = pc - startpc;
                    }
                } else {
                    code.put4(tableBase, pc - startpc);
                }

                // 生成case分支所含的语句的字节码指定
                genStats(c.stats, switchEnv, CRT_FLOW_TARGET);
            }

            // 处理所有的break语句
            code.resolve(switchEnv.info.exit);

            // 如果还没有设置默认分支的偏移地址时，设置默认分支的偏移地址
            if (code.get4(tableBase) == -1) {
                code.put4(tableBase, code.entryPoint(stateSwitch) - startpc);
            }

            // 继续进行地址回填
            if (opcode == tableswitch) {
                // 对tableswitch指令进行地址回填
                int defaultOffset = code.get4(tableBase);
                for (long i = lo; i <= hi; i++) {
                    int t = (int)(tableBase + 4 * (i - lo + 3));
                    if (code.get4(t) == -1)
                        // 对没有填充的虚拟case分支设置跳转地址，这个地址就是默认分支的跳转地址
                        code.put4(t, defaultOffset);
                }
            } else {
                // 对lookupswitch指令进行地址回填
                if (defaultIndex >= 0)
                    for (int i = defaultIndex; i < labels.length - 1; i++) {
                        labels[i] = labels[i+1];
                        // 在循环生成各个分支所含语句的字节码指令时，将地址偏移量暂时保存到offsets数组中
                        offsets[i] = offsets[i+1];
                    }
                if (nlabels > 0)
                    // loopupswitch中会对所有case分支生成的匹配坐标按照分支中的数值进行排序，以方便使用二分查找来加快查找对应case分支的效率
                    qsort2(labels, offsets, 0, nlabels - 1);
                // 随后根据offsets数组中保存的对应关系进行地址回填
                for (int i = 0; i < nlabels; i++) {
                    int caseidx = tableBase + 8 * (i + 1);
                    code.put4(caseidx, labels[i]);
                    code.put4(caseidx + 4, offsets[i]);
                }
            }
        }
        code.endScopes(limit);
    }
//where
        /** Sort (int) arrays of keys and values
         */
        // 二分法查找
       static void qsort2(int[] keys, int[] values, int lo, int hi) {
            int i = lo;
            int j = hi;
            int pivot = keys[(i+j)/2];
            do {
                while (keys[i] < pivot) i++;
                while (pivot < keys[j]) j--;
                if (i <= j) {
                    int temp1 = keys[i];
                    keys[i] = keys[j];
                    keys[j] = temp1;
                    int temp2 = values[i];
                    values[i] = values[j];
                    values[j] = temp2;
                    i++;
                    j--;
                }
            } while (i <= j);
            if (lo < j) qsort2(keys, values, lo, j);
            if (i < hi) qsort2(keys, values, i, hi);
        }

    public void visitSynchronized(JCSynchronized tree) {
        int limit = code.nextreg;
        // Generate code to evaluate lock and save in temporary variable.
        final LocalItem lockVar = makeTemp(syms.objectType);
        genExpr(tree.lock, tree.lock.type).load().duplicate();
        lockVar.store();

        // Generate code to enter monitor.
        code.emitop0(monitorenter);
        code.state.lock(lockVar.reg);

        // Generate code for a try statement with given body, no catch clauses
        // in a new environment with the "exit-monitor" operation as finalizer.
        final Env<GenContext> syncEnv = env.dup(tree, new GenContext());
        syncEnv.info.finalize = new GenFinalizer() {
            void gen() {
                genLast();
                Assert.check(syncEnv.info.gaps.length() % 2 == 0);
                syncEnv.info.gaps.append(code.curPc());
            }
            void genLast() {
                if (code.isAlive()) {
                    lockVar.load();
                    code.emitop0(monitorexit);
                    code.state.unlock(lockVar.reg);
                }
            }
        };
        syncEnv.info.gaps = new ListBuffer<Integer>();
        genTry(tree.body, List.<JCCatch>nil(), syncEnv);
        code.endScopes(limit);
    }

    // 访问try节点
    public void visitTry(final JCTry tree) {
        // 首先调用env.dup()方法来获取Env对象tryEnv，Env对象中的info变量保存的是GenContext对象，这个对象可以辅助生成try语句的字节码指令。
        final Env<GenContext> tryEnv = env.dup(tree, new GenContext());
        final Env<GenContext> oldEnv = env;
        if (!useJsrLocally) {
            useJsrLocally =
                (stackMap == StackMapFormat.NONE) &&
                (jsrlimit <= 0 ||
                jsrlimit < 100 &&
                estimateCodeComplexity(tree.finalizer)>jsrlimit);
        }
        tryEnv.info.finalize = new GenFinalizer() {
            void gen() {
                if (useJsrLocally) {
                    if (tree.finalizer != null) {
                        Code.State jsrState = code.state.dup();
                        jsrState.push(Code.jsrReturnValue);
                        tryEnv.info.cont =
                            new Chain(code.emitJump(jsr),
                                      tryEnv.info.cont,
                                      jsrState);
                    }
                    Assert.check(tryEnv.info.gaps.length() % 2 == 0);
                    tryEnv.info.gaps.append(code.curPc());
                } else {
                    Assert.check(tryEnv.info.gaps.length() % 2 == 0);
                    tryEnv.info.gaps.append(code.curPc());
                    genLast();
                }
            }
            void genLast() {
                if (tree.finalizer != null)
                    genStat(tree.finalizer, oldEnv, CRT_BLOCK);
            }
            boolean hasFinalizer() {
                return tree.finalizer != null;
            }
        };
        tryEnv.info.gaps = new ListBuffer<Integer>();
        // 初始化了tryEnv.info.finalizer后就可以调用genTry()方法生成try语句的字节码指令了
        genTry(tree.body, tree.catchers, tryEnv);
    }
    //where
        /** Generate code for a try or synchronized statement
         *  @param body      The body of the try or synchronized statement.
         *  @param catchers  The lis of catch clauses.
         *  @param env       the environment current for the body.
         */
        void genTry(JCTree body, List<JCCatch> catchers, Env<GenContext> env) {
            int limit = code.nextreg;
            // startpc与endpc记录了try语句body体生成的字节码指令的范围,对于实例17-5来说，这两个值分别为0和4
            int startpc = code.curPc();
            Code.State stateTry = code.state.dup();
            genStat(body, env, CRT_BLOCK);
            // startpc与endpc记录了try语句body体生成的字节码指令的范围,对于实例17-5来说，这两个值分别为0和4
            // 调用env.info.gaps的toList()方法初始化gaps，由于toList()方法会重新创建一个列表，因此如果往gaps中追加值时不会影响env.inof.gaps列表中的值。
            // 在实例17-5中，gaps与env.info.gaps值都为空。
            int endpc = code.curPc();
            boolean hasFinalizer =
                env.info.finalize != null &&
                env.info.finalize.hasFinalizer();
            // 在genTry()方法中为try语句body体生成的字节码指令生成冗余的字节码指令
            List<Integer> gaps = env.info.gaps.toList();
            code.statBegin(TreeInfo.endPos(body));
            // 调用genFinalizer()方法记录冗余指令的起始位置
            genFinalizer(env);
            code.statBegin(TreeInfo.endPos(env.tree));
            Chain exitChain = code.branch(goto_);
            // 调用endFinalizerGap()方法生成冗余指令并记录冗余指令的结束位置
            endFinalizerGap(env);
            // 现在生成了try语句body体的字节码指令并且也记录了冗余指令的起始与结束位置，下面生成各个catch语句的字节码指令
            if (startpc != endpc) {
                // genTry()方法首先判断startpc不等于endpc，这样可以保证try语句body体有字节码指令生成，
                // 因为有执行的字节码指令才可能抛出异常进入catch语句。
                for (List<JCCatch> l = catchers; l.nonEmpty(); l = l.tail) {
                    // 循环遍历catch语句，调用code.entryPoint()方法向操作数栈压入抛出的异常类型，这也是模拟Java虚拟机运行时的情况
                    // 当try语句的body体中抛出异常时，Java虚拟机会将对应的异常压入栈顶
                    code.entryPoint(stateTry, l.head.param.sym.type);
                    // 调用genCatch()方法生成catch语句的body体的字节码指令。
                    genCatch(l.head, env, startpc, endpc, gaps);
                    genFinalizer(env);
                    if (hasFinalizer || l.tail.nonEmpty()) {
                        // 有finally语句或有后续catch语句的话，那么生成的字节码指令要无条件跳转到目标位置，
                        // 也就是当前try语句之后的第一条指令位置，这个地址要回填，因此使用exitChain链接起来
                        code.statBegin(TreeInfo.endPos(env.tree));
                        exitChain = Code.mergeChains(exitChain,
                                code.branch(goto_));
                    }
                    endFinalizerGap(env);
                }
            }
            if (hasFinalizer) {
                // 将nextreg的值设置为max_locals来避免冲突
                code.newRegSegment();
                // 当try语句的body体发生异常时，将异常压入操作数栈顶，调用code.entryPoint()方法压入异常类型后获取到catchallpc
                int catchallpc = code.entryPoint(stateTry, syms.throwableType);

                int startseg = startpc;
                // 为try语句body体中抛出而各个catch语句无法捕获的异常加上执行catch语句时可能抛出的异常
                // 生成异常记录
                // 抛出异常，而这里异常最终都会存储到栈顶，等待finally进行重抛
                while (env.info.gaps.nonEmpty()) {
                    int endseg = env.info.gaps.next().intValue();
                    // 然后向异常表中继续插入try语句的body体中抛出的而各个catch语句无法捕获的异常加上执行catch语句时可能抛出的异常
                    //（这些异常不一定要使用throw关键字显式进行抛出）
                    registerCatch(body.pos(), startseg, endseg,
                                  catchallpc, 0);
                    startseg = env.info.gaps.next().intValue();
                }
                code.statBegin(TreeInfo.finalizerPos(env.tree));
                code.markStatBegin();
                // 将try语句body体或catch语句抛出的异常保存到本地变量表中，在生成完finally语句的字节码指令后
                // 将异常加载到操作数栈顶并使用athrow指令抛出
                Item excVar = makeTemp(syms.throwableType);
                excVar.store();
                genFinalizer(env);
                excVar.load();
                registerCatch(body.pos(), startseg,
                              env.info.gaps.next().intValue(),
                              catchallpc, 0);
                code.emitop0(athrow);
                code.markDead();

                // If there are jsr's to this finalizer, ...
                if (env.info.cont != null) {
                    // Resolve all jsr's.
                    code.resolve(env.info.cont);

                    // Mark statement line number
                    code.statBegin(TreeInfo.finalizerPos(env.tree));
                    code.markStatBegin();

                    // Save return address.
                    LocalItem retVar = makeTemp(syms.throwableType);
                    retVar.store();

                    // Generate finalizer code.
                    env.info.finalize.genLast();

                    // Return.
                    code.emitop1w(ret, retVar.reg);
                    code.markDead();
                }
            }

            // 最后通过调用code.resolve()方法将需要回填的exitChain赋值给pendingJumps，
            // 这样在try语句后的第一条指令输入时，为break等语句对应的字节码指令进行地址回填。
            code.resolve(exitChain);

            code.endScopes(limit);
        }

        /** Generate code for a catch clause.
         *  @param tree     The catch clause.
         *  @param env      The environment current in the enclosing try.
         *  @param startpc  Start pc of try-block.
         *  @param endpc    End pc of try-block.
         */
        void genCatch(JCCatch tree,
                      Env<GenContext> env,
                      int startpc, int endpc,
                      List<Integer> gaps) {
            // 判断startpc不等于endpc,保证try语句body体有字节码指令生成
            if (startpc != endpc) {
                List<JCExpression> subClauses = TreeInfo.isMultiCatch(tree) ?
                        ((JCTypeUnion)tree.param.vartype).alternatives :
                        List.of(tree.param.vartype);
                while (gaps.nonEmpty()) {
                    for (JCExpression subCatch : subClauses) {
                        int catchType = makeRef(tree.pos(), subCatch.type);
                        int end = gaps.head.intValue();
                        // 调用registerCatch()方法向异常表中添加异常处理记录
                        // 如果catch语句中声明了N个异常捕获类型，则循环向异常表中添加N条异常处理记录
                        registerCatch(tree.pos(),
                                      startpc,  end, code.curPc(),
                                      catchType);
                    }
                    gaps = gaps.tail;
                    startpc = gaps.head.intValue();
                    gaps = gaps.tail;
                }
                // 如果try语句的body体有字节码指令生成，则向异常表插入异常记录
                if (startpc < endpc) {
                    for (JCExpression subCatch : subClauses) {
                        int catchType = makeRef(tree.pos(), subCatch.type);
                        registerCatch(tree.pos(),
                                      startpc, endpc, code.curPc(),
                                      catchType);
                    }
                }
                VarSymbol exparam = tree.param.sym;
                code.statBegin(tree.pos);
                code.markStatBegin();
                int limit = code.nextreg;
                int exlocal = code.newLocal(exparam);
                // 如果try语句的body体中抛出异常，则异常会被压入栈顶，将异常存储到本地变量表中
                items.makeLocalItem(exparam).store();
                code.statBegin(TreeInfo.firstStatPos(tree.body));
                genStat(tree.body, env, CRT_BLOCK);
                code.endScopes(limit);
                code.statBegin(TreeInfo.endPos(tree.body));
            }
        }

        /** Register a catch clause in the "Exceptions" code-attribute.
         */
        void registerCatch(DiagnosticPosition pos,
                           int startpc, int endpc,
                           int handler_pc, int catch_type) {
            if (startpc != endpc) {
                char startpc1 = (char)startpc;
                char endpc1 = (char)endpc;
                char handler_pc1 = (char)handler_pc;
                // 将startpc、endpc与handler_pc进行强制类型转换转为char类型后，通过恒等式“==”来判断它们是否与强制类型转换之前的值相等。
                if (startpc1 == startpc &&
                    endpc1 == endpc &&
                    handler_pc1 == handler_pc) {
                    // 向catchInfo列表中追加一个相关记录信息的字符数组
                    code.addCatch(startpc1, endpc1, handler_pc1,
                                  (char)catch_type);
                } else {
                    if (!useJsrLocally && !target.generateStackMapTable()) {
                        useJsrLocally = true;
                        throw new CodeSizeOverflow();
                    } else {
                        log.error(pos, "limit.code.too.large.for.try.stmt");
                        nerrs++;
                    }
                }
            }
        }

    /** Very roughly estimate the number of instructions needed for
     *  the given tree.
     */
    int estimateCodeComplexity(JCTree tree) {
        if (tree == null) return 0;
        class ComplexityScanner extends TreeScanner {
            int complexity = 0;
            public void scan(JCTree tree) {
                if (complexity > jsrlimit) return;
                super.scan(tree);
            }
            public void visitClassDef(JCClassDecl tree) {}
            public void visitDoLoop(JCDoWhileLoop tree)
                { super.visitDoLoop(tree); complexity++; }
            public void visitWhileLoop(JCWhileLoop tree)
                { super.visitWhileLoop(tree); complexity++; }
            public void visitForLoop(JCForLoop tree)
                { super.visitForLoop(tree); complexity++; }
            public void visitSwitch(JCSwitch tree)
                { super.visitSwitch(tree); complexity+=5; }
            public void visitCase(JCCase tree)
                { super.visitCase(tree); complexity++; }
            public void visitSynchronized(JCSynchronized tree)
                { super.visitSynchronized(tree); complexity+=6; }
            public void visitTry(JCTry tree)
                { super.visitTry(tree);
                  if (tree.finalizer != null) complexity+=6; }
            public void visitCatch(JCCatch tree)
                { super.visitCatch(tree); complexity+=2; }
            public void visitConditional(JCConditional tree)
                { super.visitConditional(tree); complexity+=2; }
            public void visitIf(JCIf tree)
                { super.visitIf(tree); complexity+=2; }
            // note: for break, continue, and return we don't take unwind() into account.
            public void visitBreak(JCBreak tree)
                { super.visitBreak(tree); complexity+=1; }
            public void visitContinue(JCContinue tree)
                { super.visitContinue(tree); complexity+=1; }
            public void visitReturn(JCReturn tree)
                { super.visitReturn(tree); complexity+=1; }
            public void visitThrow(JCThrow tree)
                { super.visitThrow(tree); complexity+=1; }
            public void visitAssert(JCAssert tree)
                { super.visitAssert(tree); complexity+=5; }
            public void visitApply(JCMethodInvocation tree)
                { super.visitApply(tree); complexity+=2; }
            public void visitNewClass(JCNewClass tree)
                { scan(tree.encl); scan(tree.args); complexity+=2; }
            public void visitNewArray(JCNewArray tree)
                { super.visitNewArray(tree); complexity+=5; }
            public void visitAssign(JCAssign tree)
                { super.visitAssign(tree); complexity+=1; }
            public void visitAssignop(JCAssignOp tree)
                { super.visitAssignop(tree); complexity+=2; }
            public void visitUnary(JCUnary tree)
                { complexity+=1;
                  if (tree.type.constValue() == null) super.visitUnary(tree); }
            public void visitBinary(JCBinary tree)
                { complexity+=1;
                  if (tree.type.constValue() == null) super.visitBinary(tree); }
            public void visitTypeTest(JCInstanceOf tree)
                { super.visitTypeTest(tree); complexity+=1; }
            public void visitIndexed(JCArrayAccess tree)
                { super.visitIndexed(tree); complexity+=1; }
            public void visitSelect(JCFieldAccess tree)
                { super.visitSelect(tree);
                  if (tree.sym.kind == VAR) complexity+=1; }
            public void visitIdent(JCIdent tree) {
                if (tree.sym.kind == VAR) {
                    complexity+=1;
                    if (tree.type.constValue() == null &&
                        tree.sym.owner.kind == TYP)
                        complexity+=1;
                }
            }
            public void visitLiteral(JCLiteral tree)
                { complexity+=1; }
            public void visitTree(JCTree tree) {}
            public void visitWildcard(JCWildcard tree) {
                throw new AssertionError(this.getClass().getName());
            }
        }
        ComplexityScanner scanner = new ComplexityScanner();
        tree.accept(scanner);
        return scanner.complexity;
    }

    // 访问if节点
    public void visitIf(JCIf tree) {
        // 通过局部变量limit保存了code.nextreg的值
        int limit = code.nextreg;
        Chain thenExit = null;
        // 调用genCond()方法获取到CondItem对象
        CondItem c = genCond(TreeInfo.skipParens(tree.cond),
                             CRT_FLOW_CONTROLLER);
        // 调用jumpFalse()方法，表示当条件判断表达式的值为false时需要进行地址回填
        Chain elseChain = c.jumpFalse();
        // 要分析的if语句的条件判断表达式的结果不为常量值false
        if (!c.isFalse()) {
            // 调用code.resolve()方法填写c.trueJumps中所有需要回填的地址
            code.resolve(c.trueJumps);
            // 当条件判断表达式的值不为常量值false时，可调用genStat()方法生成if语句body体中的字节码指令
            genStat(tree.thenpart, env, CRT_STATEMENT | CRT_FLOW_TARGET);
            // 调用code.branch()方法创建一个无条件跳转分支thenExit
            thenExit = code.branch(goto_);
        }
        // 如果有else分支时，则跳转目标为else分支生成的字节码指令后的下一个指令地址
        if (elseChain != null) {
            // 调用code.resolve()方法回填要跳转到else语句body体中的elseChain
            code.resolve(elseChain);
            // 这样在生成else语句body体中的第一条字节码指令时就会调用resolve(Chain chain,int target)方法回填地址
            if (tree.elsepart != null)
                // 当tree.elsepart不为空时，同样调用genStat()方法生成else语句body体中的字节码指令
                genStat(tree.elsepart, env,CRT_STATEMENT | CRT_FLOW_TARGET);
        }
        // 这样当if语句块没有else分支时下一条指令就是thenExit的跳转目标
        code.resolve(thenExit);
        // 执行完成后调用code.endScopes()方法将执行if语句而保存到本地变量表中的数据清除
        // 因为已经离开了if语句的作用域范围，这些数据是无效的状态。
        code.endScopes(limit);
    }

    // 访问表达式
    public void visitExec(JCExpressionStatement tree) {
        // 首先将后缀自增与自减的语句更改为前置自增与自减，这样可以简化处理，同时也是等价变换
        JCExpression e = tree.expr;
        switch (e.getTag()) {
            case JCTree.POSTINC:
                ((JCUnary) e).setTag(JCTree.PREINC);
                break;
            case JCTree.POSTDEC:
                ((JCUnary) e).setTag(JCTree.PREDEC);
                break;
        }
        // 调用genExpr()方法处理表达式
        // tree.expr.type：期望类型直接从标注语法树中获取即可
        // tree.expr：树节点
        genExpr(tree.expr, tree.expr.type).drop();
    }

    public void visitBreak(JCBreak tree) {
        Env<GenContext> targetEnv = unwind(tree.target, env);
        Assert.check(code.state.stacksize == 0);
        targetEnv.info.addExit(code.branch(goto_));
        endFinalizerGaps(env, targetEnv);
    }

    public void visitContinue(JCContinue tree) {
        Env<GenContext> targetEnv = unwind(tree.target, env);
        Assert.check(code.state.stacksize == 0);
        targetEnv.info.addCont(code.branch(goto_));
        endFinalizerGaps(env, targetEnv);
    }

    // 访问return树节点
    public void visitReturn(JCReturn tree) {
        int limit = code.nextreg;
        final Env<GenContext> targetEnv;
        if (tree.expr != null) {
            // 例16-3 调用genExpr()方法处理JCIdent(a)节点，则会返回LocalItem(type=int; reg=1)；调用load()方法将局部变量表中指定索引位置1的数加载到操作数栈中，会生成iload_1指令
            // 例16-9 调用genExpr()方法处理JCUnary(arr[a]++)树节点，得到StackItem对象，这是表示arr[a]的值已经在栈内，因此调用这个对象的load()方法无操作，最后会生成ireturn指令
            Item r = genExpr(tree.expr, pt).load();
            if (hasFinally(env.enclMethod, env)) {
                r = makeTemp(pt);
                r.store();
            }
            targetEnv = unwind(env.enclMethod, env);
            // LocalItem类的load()方法最终会返回一个StackItem对象，调用此对象的load()方法就是返回自身。
            // 之所以再次调用，是因为像LocalItem这样的对象，其load()方法表示的含义并不是加载数据到操作数栈中
            r.load();
            // visitReturn()方法最后根据pt的不同选择生成具体的ireturn指令，表示返回一个整数类型的值
            // 从当前方法返回int
            code.emitop0(ireturn + Code.truncate(Code.typecode(pt)));
        } else {
            targetEnv = unwind(env.enclMethod, env);
            code.emitop0(return_);
        }
        endFinalizerGaps(env, targetEnv);
        code.endScopes(limit);
    }

    // 访问异常抛出节点
    public void visitThrow(JCThrow tree) {
        // 调用genExpr()方法获取Item对象，对于以17-4实例来说，获取到的是表示本地变量e的LocalItem对象，这个对象的reg值为1
        // 表示变量存储到了本地变量表索引为1的位置，调用这个对象的load()方法生成aload_1指令并将异常类型压入操作数栈顶
        // 这样就可以调用code.emitop0()方法生成athrow指令并抛出栈顶保存的异常类型了
        genExpr(tree.expr, tree.expr.type).load();
        code.emitop0(athrow);
    }

/* ************************************************************************
 * Visitor methods for expressions
 *************************************************************************/

    public void visitApply(JCMethodInvocation tree) {
        // Generate code for method.
        // 例16-8 调用genExpr()方法处理JCIdent(super)树节点，最终返回MemberItem(member.name=Object，nonvirtual=true)对象
        Item m = genExpr(tree.meth, methodType);
        // Generate code for all arguments, where the expected types are
        // the parameters of the method's external type (that is, any implicit
        // outer instance of a super(...) call appears as first parameter).
        genArgs(tree.args,
                TreeInfo.symbol(tree.meth).externalType(types).getParameterTypes());
        // 例16-8 调用这个对象的invoke()方法生成方法调用的相关指令
        result = m.invoke();
    }

    public void visitConditional(JCConditional tree) {
        Chain thenExit = null;
        CondItem c = genCond(tree.cond, CRT_FLOW_CONTROLLER);
        Chain elseChain = c.jumpFalse();
        if (!c.isFalse()) {
            code.resolve(c.trueJumps);
            int startpc = genCrt ? code.curPc() : 0;
            genExpr(tree.truepart, pt).load();
            code.state.forceStackTop(tree.type);
            if (genCrt) code.crt.put(tree.truepart, CRT_FLOW_TARGET,
                                     startpc, code.curPc());
            thenExit = code.branch(goto_);
        }
        if (elseChain != null) {
            code.resolve(elseChain);
            int startpc = genCrt ? code.curPc() : 0;
            genExpr(tree.falsepart, pt).load();
            code.state.forceStackTop(tree.type);
            if (genCrt) code.crt.put(tree.falsepart, CRT_FLOW_TARGET,
                                     startpc, code.curPc());
        }
        code.resolve(thenExit);
        result = items.makeStackItem(pt);
    }

    public void visitNewClass(JCNewClass tree) {
        // Enclosing instances or anonymous classes should have been eliminated
        // by now.
        Assert.check(tree.encl == null && tree.def == null);

        code.emitop2(new_, makeRef(tree.pos(), tree.type));
        code.emitop0(dup);

        // Generate code for all arguments, where the expected types are
        // the parameters of the constructor's external type (that is,
        // any implicit outer instance appears as first parameter).
        genArgs(tree.args, tree.constructor.externalType(types).getParameterTypes());

        items.makeMemberItem(tree.constructor, true).invoke();
        result = items.makeStackItem(tree.type);
    }

    public void visitNewArray(JCNewArray tree) {

        if (tree.elems != null) {
            Type elemtype = types.elemtype(tree.type);
            loadIntConst(tree.elems.length());
            Item arr = makeNewArray(tree.pos(), tree.type, 1);
            int i = 0;
            for (List<JCExpression> l = tree.elems; l.nonEmpty(); l = l.tail) {
                arr.duplicate();
                loadIntConst(i);
                i++;
                genExpr(l.head, elemtype).load();
                items.makeIndexedItem(elemtype).store();
            }
            result = arr;
        } else {
            for (List<JCExpression> l = tree.dims; l.nonEmpty(); l = l.tail) {
                genExpr(l.head, syms.intType).load();
            }
            result = makeNewArray(tree.pos(), tree.type, tree.dims.length());
        }
    }
//where
        /** Generate code to create an array with given element type and number
         *  of dimensions.
         */
        Item makeNewArray(DiagnosticPosition pos, Type type, int ndims) {
            Type elemtype = types.elemtype(type);
            if (types.dimensions(type) > ClassFile.MAX_DIMENSIONS) {
                log.error(pos, "limit.dimensions");
                nerrs++;
            }
            int elemcode = Code.arraycode(elemtype);
            if (elemcode == 0 || (elemcode == 1 && ndims == 1)) {
                code.emitAnewarray(makeRef(pos, elemtype), type);
            } else if (elemcode == 1) {
                code.emitMultianewarray(ndims, makeRef(pos, type), type);
            } else {
                code.emitNewarray(elemcode, type);
            }
            return items.makeStackItem(type);
        }

    public void visitParens(JCParens tree) {
        result = genExpr(tree.expr, tree.expr.type);
    }

    // 访问赋值表达式v
    public void visitAssign(JCAssign tree) {
        // 创建对应的item节点并返回
        Item l = genExpr(tree.lhs, tree.lhs.type);
        // 当调用genExpr()方法处理JCBinary(a+1)树节点时，会调用visitBinary()方法，该方法返回一个StackItem对象
        // 表示栈中a+1执行后会产生一个int类型的数值
        genExpr(tree.rhs, tree.lhs.type).load();
        // 将l作为参数调用items.makeAssignItem()方法创建一个AssignItem对象并赋值给result。
        result = items.makeAssignItem(l);
    }

    public void visitAssignop(JCAssignOp tree) {
        OperatorSymbol operator = (OperatorSymbol) tree.operator;
        Item l;
        if (operator.opcode == string_add) {
            // Generate code to make a string buffer
            makeStringBuffer(tree.pos());

            // Generate code for first string, possibly save one
            // copy under buffer
            l = genExpr(tree.lhs, tree.lhs.type);
            if (l.width() > 0) {
                code.emitop0(dup_x1 + 3 * (l.width() - 1));
            }

            // Load first string and append to buffer.
            l.load();
            appendString(tree.lhs);

            // Append all other strings to buffer.
            appendStrings(tree.rhs);

            // Convert buffer to string.
            bufferToString(tree.pos());
        } else {
            // Generate code for first expression
            l = genExpr(tree.lhs, tree.lhs.type);

            // If we have an increment of -32768 to +32767 of a local
            // int variable we can use an incr instruction instead of
            // proceeding further.
            if ((tree.getTag() == JCTree.PLUS_ASG || tree.getTag() == JCTree.MINUS_ASG) &&
                l instanceof LocalItem &&
                tree.lhs.type.tag <= INT &&
                tree.rhs.type.tag <= INT &&
                tree.rhs.type.constValue() != null) {
                int ival = ((Number) tree.rhs.type.constValue()).intValue();
                if (tree.getTag() == JCTree.MINUS_ASG) ival = -ival;
                ((LocalItem)l).incr(ival);
                result = l;
                return;
            }
            // Otherwise, duplicate expression, load one copy
            // and complete binary operation.
            l.duplicate();
            l.coerce(operator.type.getParameterTypes().head).load();
            completeBinop(tree.lhs, tree.rhs, operator).coerce(tree.lhs.type);
        }
        result = items.makeAssignItem(l);
    }

    // 访问一元表达式
    public void visitUnary(JCUnary tree) {
        // 表达式对象
        OperatorSymbol operator = (OperatorSymbol)tree.operator;
        if (tree.getTag() == JCTree.NOT) {
            // 对非语句单独处理
            // 调用genCond()方法将得到CondItem对象od
            CondItem od = genCond(tree.arg, false);
            // 然后调用od.negate()方法进行逻辑取反，这是非运算符语义的体现。
            result = od.negate();
        } else {
            // 例16-9 调用genExpr()方法处理JCArrayAccess(arr[a])树节点，得到IndexedItem(int)对象
            Item od = genExpr(tree.arg, operator.type.getParameterTypes().head);
            switch (tree.getTag()) {
            case JCTree.POS: // +
                result = od.load();
                break;
            case JCTree.NEG: // -
                result = od.load();
                code.emitop0(operator.opcode);
                break;
            case JCTree.COMPL: // ~
                result = od.load();
                emitMinusOne(od.typecode);
                code.emitop0(operator.opcode);
                break;
            case JCTree.PREINC: case JCTree.PREDEC: // ++_,--_
                od.duplicate();
                if (od instanceof LocalItem &&
                    (operator.opcode == iadd || operator.opcode == isub)) {
                    // 调用od的incr()方法以生成iinc指令
                    ((LocalItem)od).incr(tree.getTag() == JCTree.PREINC ? 1 : -1);
                    result = od;
                } else {
                    od.load();
                    code.emitop0(one(od.typecode));
                    code.emitop0(operator.opcode);
                    // Perform narrowing primitive conversion if byte,
                    // char, or short.  Fix for 4304655.
                    if (od.typecode != INTcode &&
                        Code.truncate(od.typecode) == INTcode)
                      code.emitop0(int2byte + od.typecode - BYTEcode);
                    result = items.makeAssignItem(od);
                }
                break;
            case JCTree.POSTINC: case JCTree.POSTDEC:// _++,_--
                // 例16-9 调用IndexedItem(int)对象的duplicate()方法将生成dup2指令，
                od.duplicate();
                if (od instanceof LocalItem &&
                    (operator.opcode == iadd || operator.opcode == isub)) {
                    Item res = od.load();
                    ((LocalItem)od).incr(tree.getTag() == JCTree.POSTINC ? 1 : -1);
                    result = res;
                } else {
                    // 例16-9 调用IndexedItem(int)对象的load()方法将生成iaload指令
                    Item res = od.load();
                    // 例16-9 调用IndexedItem(int)对象的stash()方法将生成dup_x2指令；
                    od.stash(od.typecode);
                    // 例16-9 调用code.emitop0()方法将生成iconst_1与iadd指令
                    code.emitop0(one(od.typecode));
                    code.emitop0(operator.opcode);
                    // Perform narrowing primitive conversion if byte,
                    // char, or short.  Fix for 4304655.
                    if (od.typecode != INTcode &&
                        Code.truncate(od.typecode) == INTcode)
                      code.emitop0(int2byte + od.typecode - BYTEcode);
                    // 例16-9 调用od.store()方法将生成iastore指令
                    od.store();
                    result = res;
                }
                break;
            case JCTree.NULLCHK:
                result = od.load();
                code.emitop0(dup);
                genNullCheck(tree.pos());
                break;
            default:
                Assert.error();
            }
        }
    }

    /** Generate a null check from the object value at stack top. */
    private void genNullCheck(DiagnosticPosition pos) {
        callMethod(pos, syms.objectType, names.getClass,
                   List.<Type>nil(), false);
        code.emitop0(pop);
    }

    // 访问二元表达式
    public void visitBinary(JCBinary tree) {
        OperatorSymbol operator = (OperatorSymbol)tree.operator;
        if (operator.opcode == string_add) {
            // 对字符串的+进行处理
            // Create a string buffer.
            makeStringBuffer(tree.pos());
            // Append all strings to buffer.
            appendStrings(tree);
            // Convert buffer to string.
            bufferToString(tree.pos());
            result = items.makeStackItem(syms.stringType);
        } else if (tree.getTag() == JCTree.AND) {
            // 特殊处理&&
            // 处理二元表达式左侧表达式，得到一个CondItem对象lcond
            CondItem lcond = genCond(tree.lhs, CRT_FLOW_CONTROLLER);
            if (!lcond.isFalse()) {
                // 如果这个对象代表的条件判断表达式的结果不为编译时的常量false
                // 当tree.lhs条件判断表达式的结果不为编译时常量的false时获取falseJumps
                // 也就是当条件判断表达式的结果为false时要跳转的分支时，其跳转的目标应该为二元表达式执行完成后的第一条指令地址
                Chain falseJumps = lcond.jumpFalse();
                // 调用code.resolve()方法对lcond.trueJumps进行地址回填
                code.resolve(lcond.trueJumps);
                // 这样当调用genCond()方法生成tree.rhs的字节码指令时，第一条指令的地址就是具体的回填地址。
                // 处理二元表达式右侧表达式
                // tree.rhs就有可能被执行，也需要调用genCond()方法生成tree.rhs的字节码指令，否则只生成tree.lhs的字节码指令即可。
                CondItem rcond = genCond(tree.rhs, CRT_FLOW_TARGET);
                result = items.
                    makeCondItem(rcond.opcode,
                                 rcond.trueJumps,
                                 // 最后调用Code类的mergeChains()方法将falseJumps与rcond.falseJumps进行合并，
                                 // 因为“&&”运算符左侧与右侧的表达式为false时跳转的目标地址一样，所以可以连接在一起。
                                 Code.mergeChains(falseJumps,
                                                  rcond.falseJumps));
            } else {
                // 左侧的表达式的结果一定为true，lcond.trueJumps的跳转地址为右侧表达式生成的第一条指令地址
                result = lcond;
            }
        } else if (tree.getTag() == JCTree.OR) {
            // 特殊处理||
            CondItem lcond = genCond(tree.lhs, CRT_FLOW_CONTROLLER);
            if (!lcond.isTrue()) {
                Chain trueJumps = lcond.jumpTrue();
                code.resolve(lcond.falseJumps);
                CondItem rcond = genCond(tree.rhs, CRT_FLOW_TARGET);
                result = items.
                    makeCondItem(rcond.opcode,
                                 Code.mergeChains(trueJumps, rcond.trueJumps),
                                 rcond.falseJumps);
            } else {
                result = lcond;
            }
        } else {
            // 例16-6 调用genExpr()方法处理JCIdent(a)树节点并返回StaticItem对象，
            // 例16-7 当调用genExpr()方法处理JCIdent(a)树节点时则会调用visitIdent()方法处理，visitIdent()方法会生成一个aload_0指令并且返回一个MemberItem对象
            Item od = genExpr(tree.lhs, operator.type.getParameterTypes().head);
            // 例16-6 调用这个对象的load()方法生成getstatic指令,则表示获取变量a的值并压入到操作数栈顶
            // 例16-7 调用load()方法生成getfield指令，表示获取实例变量a的值并压入到操作数栈顶
            od.load();
            // 例16-6 调用completeBinop()方法处理JCLiteral(1)树节点
            // 例16-6 completeBinaop()方法对JCLiteral(1)的处理与实例16-6的处理逻辑类似，生成iconst_1与iadd指令并返回一个StackItem对象，代表栈中产生了一个int类型的数据
            result = completeBinop(tree.lhs, tree.rhs, operator);
        }
    }
//where
        /** Make a new string buffer.
         */
        void makeStringBuffer(DiagnosticPosition pos) {
            code.emitop2(new_, makeRef(pos, stringBufferType));
            code.emitop0(dup);
            callMethod(
                pos, stringBufferType, names.init, List.<Type>nil(), false);
        }

        /** Append value (on tos) to string buffer (on tos - 1).
         */
        void appendString(JCTree tree) {
            Type t = tree.type.baseType();
            if (t.tag > lastBaseTag && t.tsym != syms.stringType.tsym) {
                t = syms.objectType;
            }
            items.makeMemberItem(getStringBufferAppend(tree, t), false).invoke();
        }
        Symbol getStringBufferAppend(JCTree tree, Type t) {
            Assert.checkNull(t.constValue());
            Symbol method = stringBufferAppend.get(t);
            if (method == null) {
                method = rs.resolveInternalMethod(tree.pos(),
                                                  attrEnv,
                                                  stringBufferType,
                                                  names.append,
                                                  List.of(t),
                                                  null);
                stringBufferAppend.put(t, method);
            }
            return method;
        }

        /** Add all strings in tree to string buffer.
         */
        void appendStrings(JCTree tree) {
            tree = TreeInfo.skipParens(tree);
            if (tree.getTag() == JCTree.PLUS && tree.type.constValue() == null) {
                JCBinary op = (JCBinary) tree;
                if (op.operator.kind == MTH &&
                    ((OperatorSymbol) op.operator).opcode == string_add) {
                    appendStrings(op.lhs);
                    appendStrings(op.rhs);
                    return;
                }
            }
            genExpr(tree, tree.type).load();
            appendString(tree);
        }

        /** Convert string buffer on tos to string.
         */
        void bufferToString(DiagnosticPosition pos) {
            callMethod(
                pos,
                stringBufferType,
                names.toString,
                List.<Type>nil(),
                false);
        }

        /** Complete generating code for operation, with left operand
         *  already on stack.
         *  @param lhs       The tree representing the left operand.
         *  @param rhs       The tree representing the right operand.
         *  @param operator  The operator symbol.
         */
        // 完成生成操作代码，左操作数已在堆栈上。
        Item completeBinop(JCTree lhs, JCTree rhs, OperatorSymbol operator) {
            MethodType optype = (MethodType)operator.type;
            int opcode = operator.opcode;
            if (opcode >= if_icmpeq && opcode <= if_icmple &&
                rhs.type.constValue() instanceof Number &&
                ((Number) rhs.type.constValue()).intValue() == 0) {
                opcode = opcode + (ifeq - if_icmpeq);
            } else if (opcode >= if_acmpeq && opcode <= if_acmpne &&
                       TreeInfo.isNull(rhs)) {
                opcode = opcode + (if_acmp_null - if_acmpeq);
            } else {
                Type rtype = operator.erasure(types).getParameterTypes().tail.head;
                if (opcode >= ishll && opcode <= lushrl) {
                    opcode = opcode + (ishl - ishll);
                    rtype = syms.intType;
                }
                // 调用genExpr()方法处理JCLiteral(1)并返回ImmediateItem对象
                // 调用这个对象的load()方法生成iconst_1指令，将常量值1压入操作数栈顶
                // 例16-6
                genExpr(rhs, rtype).load();
                if (opcode >= (1 << preShift)) {
                    code.emitop0(opcode >> preShift);
                    opcode = opcode & 0xFF;
                }
            }
            if (opcode >= ifeq && opcode <= if_acmpne ||
                opcode == if_acmp_null || opcode == if_acmp_nonnull) {
                return items.makeCondItem(opcode);
            } else {
                // 调用code.emitop0()方法生成iadd指令
                code.emitop0(opcode);
                // 最后创建一个StatckItem对象，类型为operator方法的返回类型
                return items.makeStackItem(optype.restype);
            }
        }

    public void visitTypeCast(JCTypeCast tree) {
        result = genExpr(tree.expr, tree.clazz.type).load();
        // Additional code is only needed if we cast to a reference type
        // which is not statically a supertype of the expression's type.
        // For basic types, the coerce(...) in genExpr(...) will do
        // the conversion.
        if (tree.clazz.type.tag > lastBaseTag &&
            types.asSuper(tree.expr.type, tree.clazz.type.tsym) == null) {
            code.emitop2(checkcast, makeRef(tree.pos(), tree.clazz.type));
        }
    }

    public void visitWildcard(JCWildcard tree) {
        throw new AssertionError(this.getClass().getName());
    }

    public void visitTypeTest(JCInstanceOf tree) {
        genExpr(tree.expr, tree.expr.type).load();
        code.emitop2(instanceof_, makeRef(tree.pos(), tree.clazz.type));
        result = items.makeStackItem(syms.booleanType);
    }

    // 例16-9 调用visitIndexed()方法处理JCArrayAccess(arr[a])树节点
    public void visitIndexed(JCArrayAccess tree) {
        // 例16-9 调用genExpr()方法处理JCIdent(arr)树节点，得到LocalItem(type=int[]; reg=1)对象，调用这个对象的load()方法将生成aload_1指令并将int[]类型压入操作数栈顶；
        genExpr(tree.indexed, tree.indexed.type).load();
        // 例16-9 调用genExpr()方法处理JCIdent(a)树节点，将得到LocalItem(type=int; reg=2)对象，调用这个对象的load()方法将生成aload_2指令并将int类型压入操作数栈顶
        genExpr(tree.index, syms.intType).load();
        // 最后会创建一个IndexedItem对象并返回。
        result = items.makeIndexedItem(tree.type);
    }

    public void visitIdent(JCIdent tree) {
        Symbol sym = tree.sym;
        if (tree.name == names._this || tree.name == names._super) {
            Item res = tree.name == names._this
                    // 当关键字为this时，则调用items.makeThisItem()方法创建SelfItem对象
                ? items.makeThisItem()
                    // 当关键字为super时，则调用items.makeSuperItem()方法创建SelfItem对象
                : items.makeSuperItem();
            if (sym.kind == MTH) {
                // Generate code to address the constructor.
                // 如果sym表示方法，则调用SelfItem对象的load()方法
                // 这个方法会生成aload_0指令
                res.load();
                res = items.makeMemberItem(sym, true);
            }
            // visitIdent()方法最后会返回MemberItem对象
            result = res;
        } else if (sym.kind == VAR && sym.owner.kind == MTH) {
            // 最终会返回一个LocalItem对象，这就是AssignItem对象的lhs变量中保存的实体。
            // 例16-9 由于arr与a都为变量，因而visitIdent()方法将会创建一个LocalItem对象并赋值给result，result将作为最终的结果返回给调用者
            result = items.makeLocalItem((VarSymbol)sym);
        } else if ((sym.flags() & STATIC) != 0) {
            if (!isAccessSuper(env.enclMethod))
                sym = binaryQualifier(sym, env.enclClass.type);
            result = items.makeStaticItem(sym);
        } else {
            // 创建一个SelfItem对象并调用load()方法，该方法会生成aload_0指令，表示将当前的实例压入栈内
            items.makeThisItem().load();
            sym = binaryQualifier(sym, env.enclClass.type);
            // 创建一个MemberItem对象并赋值给result
            result = items.makeMemberItem(sym, (sym.flags() & PRIVATE) != 0);
        }
    }

    public void visitSelect(JCFieldAccess tree) {
        Symbol sym = tree.sym;

        if (tree.name == names._class) {
            Assert.check(target.hasClassLiterals());
            code.emitop2(ldc2, makeRef(tree.pos(), tree.selected.type));
            result = items.makeStackItem(pt);
            return;
        }

        Symbol ssym = TreeInfo.symbol(tree.selected);

        // Are we selecting via super?
        boolean selectSuper =
            ssym != null && (ssym.kind == TYP || ssym.name == names._super);

        // Are we accessing a member of the superclass in an access method
        // resulting from a qualified super?
        boolean accessSuper = isAccessSuper(env.enclMethod);

        Item base = (selectSuper)
            ? items.makeSuperItem()
            : genExpr(tree.selected, tree.selected.type);

        if (sym.kind == VAR && ((VarSymbol) sym).getConstValue() != null) {
            // We are seeing a variable that is constant but its selecting
            // expression is not.
            if ((sym.flags() & STATIC) != 0) {
                if (!selectSuper && (ssym == null || ssym.kind != TYP))
                    base = base.load();
                base.drop();
            } else {
                base.load();
                genNullCheck(tree.selected.pos());
            }
            result = items.
                makeImmediateItem(sym.type, ((VarSymbol) sym).getConstValue());
        } else {
            if (!accessSuper)
                sym = binaryQualifier(sym, tree.selected.type);
            if ((sym.flags() & STATIC) != 0) {
                if (!selectSuper && (ssym == null || ssym.kind != TYP))
                    base = base.load();
                base.drop();
                result = items.makeStaticItem(sym);
            } else {
                base.load();
                if (sym == syms.lengthVar) {
                    code.emitop0(arraylength);
                    result = items.makeStackItem(syms.intType);
                } else {
                    result = items.
                        makeMemberItem(sym,
                                       (sym.flags() & PRIVATE) != 0 ||
                                       selectSuper || accessSuper);
                }
            }
        }
    }

    // 访问字面量
    public void visitLiteral(JCLiteral tree) {
        if (tree.type.tag == TypeTags.BOT) {
            code.emitop0(aconst_null);
            if (types.dimensions(pt) > 1) {
                code.emitop2(checkcast, makeRef(tree.pos(), pt));
                result = items.makeStackItem(pt);
            } else {
                result = items.makeStackItem(tree.type);
            }
        }
        else
            // 返回一个ImmediateItem对象，然后会在visitAssign()方法中调用这个对象的load()方法将常量值加载到栈中
            // 创建一个ImmediateItem对象并赋值给result。
            result = items.makeImmediateItem(tree.type, tree.value);
    }

    public void visitLetExpr(LetExpr tree) {
        int limit = code.nextreg;
        genStats(tree.defs, env);
        result = genExpr(tree.expr, tree.expr.type).load();
        code.endScopes(limit);
    }

/* ************************************************************************
 * main method
 *************************************************************************/

    // 调用JavaCompiler类的compile()方法会间接调用到Gen类的genClass()方法，
    // 每个类都会调用一次genClass()方法，因为每个类都可以定义自己的方法，需要为这些方法生成对应的字节码指令
    /** Generate code for a class definition.
     *  @param env   The attribution environment that belongs to the
     *               outermost class containing this class definition.
     *               We need this for resolving some additional symbols.
     *  @param cdef  The tree representing the class definition.
     *  @return      True if code is generated with no errors.
     */
    public boolean genClass(Env<AttrContext> env, JCClassDecl cdef) {
        try {
            attrEnv = env;
            ClassSymbol c = cdef.sym;
            this.toplevel = env.toplevel;
            this.endPositions = toplevel.endPositions;
            if (generateIproxies &&
                (c.flags() & (INTERFACE|ABSTRACT)) == ABSTRACT
                && !allowGenerics // no Miranda methods available with generics
                )
                implementInterfaceMethods(c);
            // 调用normalizeDefs()方法对一些语法树结构进行调整，主要是对匿名块和成员变量的初始化表达式进行调整
            // Javac会将这些具体的语法树结构重构到<init>方法与<cinit>方法中
            // 例16-1/16-2
            cdef.defs = normalizeDefs(cdef.defs, c);
            c.pool = pool;
            pool.reset();
            // 创建localEnv环境，Env对象localEnv的info变量的类型为GenContext
            Env<GenContext> localEnv =
                new Env<GenContext>(cdef, new GenContext());
            localEnv.toplevel = env.toplevel;
            localEnv.enclClass = cdef;
            for (List<JCTree> l = cdef.defs; l.nonEmpty(); l = l.tail) {
                // 调用genDef()方法来遍历类中定义的成员
                genDef(l.head, localEnv);
            }
            if (pool.numEntries() > Pool.MAX_ENTRIES) {
                log.error(cdef.pos(), "limit.pool");
                nerrs++;
            }
            if (nerrs != 0) {
                // if errors, discard code
                for (List<JCTree> l = cdef.defs; l.nonEmpty(); l = l.tail) {
                    if (l.head.getTag() == JCTree.METHODDEF)
                        ((JCMethodDecl) l.head).sym.code = null;
                }
            }
            cdef.defs = List.nil(); // discard trees
            return nerrs == 0;
        } finally {
            // note: this method does NOT support recursion.
            attrEnv = null;
            this.env = null;
            toplevel = null;
            endPositions = null;
            nerrs = 0;
        }
    }

/* ************************************************************************
 * Auxiliary classes
 *************************************************************************/

    /** An abstract class for finalizer generation.
     */
    abstract class GenFinalizer {
        /** Generate code to clean up when unwinding. */
        // 调用gen()方法可以记录生成finally语句对应的字节码指令的相关信息
        // 对于实例17-5来说，当需要生成finally语句对应的冗余的字节码指令时，调用gen()方法会获取当前的变量cp的值并追加到gaps列表中，这个值就是4、17或30
        abstract void gen();

        /** Generate code to clean up at last. */
        // 调用genLast()方法生成冗余的字节码指令。
        abstract void genLast();

        /** Does this finalizer have some nontrivial cleanup to perform? */
        // 调用hasFinalizer()方法判断try语句是否含有finally语句，如果含有finally语句，hasFinalizer()方法将返回true；
        boolean hasFinalizer() { return true; }
    }

    /** code generation contexts,
     *  to be used as type parameter for environments.
     */
    // 这个类中定义了一些变量和方法，用来辅助进行字节码指令的生成
    static class GenContext {

        /** A chain for all unresolved jumps that exit the current environment.
         */
        // 成员变量exit与cont的类型为Chain，在流程跳转进行地址回填时使用
        // 每个continue语句都会建立一个Chain对象
        Chain exit = null;

        /** A chain for all unresolved jumps that continue in the
         *  current environment.
         */
        // 成员变量exit与cont的类型为Chain，在流程跳转进行地址回填时使用
        Chain cont = null;

        /** A closure that generates the finalizer of the current environment.
         *  Only set for Synchronized and Try contexts.
         */
        // finalize是GenFinalizer类型的变量
        GenFinalizer finalize = null;

        /** Is this a switch statement?  If so, allocate registers
         * even when the variable declaration is unreachable.
         */
        boolean isSwitch = false;

        /** A list buffer containing all gaps in the finalizer range,
         *  where a catch all exception should not apply.
         */
        // gaps中保存了finally语句生成的冗余指令的范围
        // 对于实例17-5来说，gaps列表中按顺序保存的值为4、11、17、24、30与37，也就是指令编号在4~11、17~24、30~37之间
        // （包括起始编号但不包括结束编号）的所有指令都是冗余的字节码指令。
        ListBuffer<Integer> gaps = null;

        /** Add given chain to exit chain.
         */
        // break或者throw、return等语句会调用addExit()方法将多个Chain对象链接起来
        // 例17-2
        void addExit(Chain c)  {
            exit = Code.mergeChains(c, exit);
        }

        /** Add given chain to cont chain.
         */
        // 多个Chain对象通过调用addCont()方法链接起来
        void addCont(Chain c) {
            cont = Code.mergeChains(c, cont);
        }
    }
}
