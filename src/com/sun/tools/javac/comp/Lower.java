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

package com.sun.tools.javac.comp;

import java.util.*;

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.jvm.*;
import com.sun.tools.javac.main.RecognizedOptions.PkgInfo;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.List;

import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.code.Type.*;

import com.sun.tools.javac.jvm.Target;

import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.*;
import static com.sun.tools.javac.code.TypeTags.*;
import static com.sun.tools.javac.jvm.ByteCodes.*;

/** This pass translates away some syntactic sugar: inner classes,
 *  class literals, assertions, foreach loops, etc.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
// 在Javac中，解语法糖主要由com.sun.tools.javac.comp.Lower类来完成，这个类继承了TreeScanner类并选择性覆写了相关的visitXxx()方法，这些方法对语法糖相关的树节点进行解语法糖。
// 语言中的语法糖分为如下几种：
//  泛型，Java语言的泛型完全由Javac等编译器支持，所以也相当于是一颗语法糖。
//  一些简单的语法糖，如类型装箱转换与类型拆箱转换、方法中的变长参数和条件编译等。
//  一些语句的语法糖，如增强for循环、选择表达式的类型为枚举类或字符串类的switch语句等。
//内部类与枚举类，内部类和枚举类最终都会转换为一个普通的类并使用单独的Class文件保存。
public class Lower extends TreeTranslator {
    protected static final Context.Key<Lower> lowerKey =
        new Context.Key<Lower>();

    public static Lower instance(Context context) {
        Lower instance = context.get(lowerKey);
        if (instance == null)
            instance = new Lower(context);
        return instance;
    }

    private Names names;
    private Log log;
    private Symtab syms;
    private Resolve rs;
    private Check chk;
    private Attr attr;
    private TreeMaker make;
    private DiagnosticPosition make_pos;
    private ClassWriter writer;
    private ClassReader reader;
    private ConstFold cfolder;
    private Target target;
    private Source source;
    private boolean allowEnums;
    private final Name dollarAssertionsDisabled;
    private final Name classDollar;
    private Types types;
    private boolean debugLower;
    private PkgInfo pkginfoOpt;

    protected Lower(Context context) {
        context.put(lowerKey, this);
        names = Names.instance(context);
        log = Log.instance(context);
        syms = Symtab.instance(context);
        rs = Resolve.instance(context);
        chk = Check.instance(context);
        attr = Attr.instance(context);
        make = TreeMaker.instance(context);
        writer = ClassWriter.instance(context);
        reader = ClassReader.instance(context);
        cfolder = ConstFold.instance(context);
        target = Target.instance(context);
        source = Source.instance(context);
        allowEnums = source.allowEnums();
        dollarAssertionsDisabled = names.
            fromString(target.syntheticNameChar() + "assertionsDisabled");
        classDollar = names.
            fromString("class" + target.syntheticNameChar());

        types = Types.instance(context);
        Options options = Options.instance(context);
        debugLower = options.isSet("debuglower");
        pkginfoOpt = PkgInfo.get(options);
    }

    /** The currently enclosing class.
     */
    ClassSymbol currentClass;

    /** A queue of all translated classes.
     */
    ListBuffer<JCTree> translated;

    /** Environment for symbol lookup, set by translateTopLevelClass.
     */
    Env<AttrContext> attrEnv;

    /** A hash table mapping syntax trees to their ending source positions.
     */
    Map<JCTree, Integer> endPositions;

/**************************************************************************
 * Global mappings
 *************************************************************************/

    /** A hash table mapping local classes to their definitions.
     */
    Map<ClassSymbol, JCClassDecl> classdefs;

    /** A hash table mapping virtual accessed symbols in outer subclasses
     *  to the actually referred symbol in superclasses.
     */
    // actualsSymbols变量中保存的信息将为后面生成具体的获取方法提供必要的信息
    Map<Symbol,Symbol> actualSymbols;

    /** The current method definition.
     */
    JCMethodDecl currentMethodDef;

    /** The current method symbol.
     */
    MethodSymbol currentMethodSym;

    /** The currently enclosing outermost class definition.
     */
    JCClassDecl outermostClassDef;

    /** The currently enclosing outermost member definition.
     */
    JCTree outermostMemberDef;

    /** A navigator class for assembling a mapping from local class symbols
     *  to class definition trees.
     *  There is only one case; all other cases simply traverse down the tree.
     */
    class ClassMap extends TreeScanner {

        /** All encountered class defs are entered into classdefs table.
         */
        public void visitClassDef(JCClassDecl tree) {
            classdefs.put(tree.sym, tree);
            super.visitClassDef(tree);
        }
    }
    ClassMap classMap = new ClassMap();

    /** Map a class symbol to its definition.
     *  @param c    The class symbol of which we want to determine the definition.
     */
    JCClassDecl classDef(ClassSymbol c) {
        // First lookup the class in the classdefs table.
        JCClassDecl def = classdefs.get(c);
        if (def == null && outermostMemberDef != null) {
            // If this fails, traverse outermost member definition, entering all
            // local classes into classdefs, and try again.
            classMap.scan(outermostMemberDef);
            def = classdefs.get(c);
        }
        if (def == null) {
            // If this fails, traverse outermost class definition, entering all
            // local classes into classdefs, and try again.
            classMap.scan(outermostClassDef);
            def = classdefs.get(c);
        }
        return def;
    }

    /** A hash table mapping class symbols to lists of free variables.
     *  accessed by them. Only free variables of the method immediately containing
     *  a class are associated with that class.
     */
    // 缓存查找过的自由变量列表
    Map<ClassSymbol,List<VarSymbol>> freevarCache;

    /** A navigator class for collecting the free variables accessed
     *  from a local class.
     *  There is only one case; all other cases simply traverse down the tree.
     */
    class FreeVarCollector extends TreeScanner {

        /** The owner of the local class.
         */
        // owner是FreeVarCollector类中声明的一个类型为MethodSymbol的成员变量，在创建FreeVarCollector对象时初始化
        Symbol owner;

        /** The local class.
         */
        ClassSymbol clazz;

        /** The list of owner's variables accessed from within the local class,
         *  without any duplicates.
         */
        List<VarSymbol> fvs;

        FreeVarCollector(ClassSymbol clazz) {
            this.clazz = clazz;
            this.owner = clazz.owner;
            this.fvs = List.nil();
        }

        /** Add free variable to fvs list unless it is already there.
         */
        private void addFreeVar(VarSymbol v) {
            for (List<VarSymbol> l = fvs; l.nonEmpty(); l = l.tail)
                if (l.head == v)
                    return;
            // 当fvs列表中不含v时，将v添加到列表头部
            fvs = fvs.prepend(v);
        }

        /** Add all free variables of class c to fvs list
         *  unless they are already there.
         */
        private void addFreeVars(ClassSymbol c) {
            List<VarSymbol> fvs = freevarCache.get(c);
            if (fvs != null) {
                // 如果fvs列表不为空，将c中访问的自由变量也添加到当前类的自由变量列表中
                for (List<VarSymbol> l = fvs; l.nonEmpty(); l = l.tail) {
                    addFreeVar(l.head);
                }
            }
            // 首先查找被引用的类c是否有对应的自由变量列表，如果没有，说明c不是本地类或者c没有访问任何自由变量
        }

        /** If tree refers to a variable in owner of local class, add it to
         *  free variables list.
         */
        public void visitIdent(JCIdent tree) {
            result = tree;
            visitSymbol(tree.sym);
        }
        // where
        // 访问符号，判断是否为自由变量，是的话加入到fvs中
        private void visitSymbol(Symbol _sym) {
            Symbol sym = _sym;
            if (sym.kind == VAR || sym.kind == MTH) {
                while (sym != null && sym.owner != owner)
                    sym = proxies.lookup(proxyName(sym.name)).sym;
                if (sym != null && sym.owner == owner) {
                    VarSymbol v = (VarSymbol)sym;
                    // 调用v.getConstValue()方法的返回值不为空，所以不是自由变量
                    if (v.getConstValue() == null) {
                        // 当参数_sym是定义在方法中的变量并且不是编译时常量时，
                        // 调用addFreeVar()方法向fvs列表中追加自由变量
                        addFreeVar(v);
                    }
                } else {
                    if (outerThisStack.head != null &&
                        outerThisStack.head != _sym)
                        visitSymbol(outerThisStack.head);
                }
            }
        }

        /** If tree refers to a class instance creation expression
         *  add all free variables of the freshly created class.
         */
        // 如果树引用类实例创建表达式，则添加新创建的类的所有自由变量。
        public void visitNewClass(JCNewClass tree) {
            ClassSymbol c = (ClassSymbol)tree.constructor.owner;
            addFreeVars(c);
            if (tree.encl == null &&
                c.hasOuterInstance() &&
                outerThisStack.head != null)
                visitSymbol(outerThisStack.head);
            super.visitNewClass(tree);
        }

        /** If tree refers to a qualified this or super expression
         *  for anything but the current class, add the outer this
         *  stack as a free variable.
         */
        public void visitSelect(JCFieldAccess tree) {
            if ((tree.name == names._this || tree.name == names._super) &&
                tree.selected.type.tsym != clazz &&
                outerThisStack.head != null)
                visitSymbol(outerThisStack.head);
            super.visitSelect(tree);
        }

        /** If tree refers to a superclass constructor call,
         *  add all free variables of the superclass.
         */
        // 如果 tree 引用了超类构造函数调用，则添加超类的所有自由变量。
        public void visitApply(JCMethodInvocation tree) {
            if (TreeInfo.name(tree.meth) == names._super) {
                // 当处理形如super(...)这样的调用父类构造方法的语句时，需要调用addFreeVars()方法处理父类，
                // 如果父类有对自由变量的引用也会添加到当前类的自由变量中
                addFreeVars((ClassSymbol) TreeInfo.symbol(tree.meth).owner);
                Symbol constructor = TreeInfo.symbol(tree.meth);
                ClassSymbol c = (ClassSymbol)constructor.owner;
                if (c.hasOuterInstance() &&
                    tree.meth.getTag() != JCTree.SELECT &&
                    outerThisStack.head != null)
                    visitSymbol(outerThisStack.head);
            }
            super.visitApply(tree);
        }
    }

    /** Return the variables accessed from within a local class, which
     *  are declared in the local class' owner.
     *  (in reverse order of first access).
     */
    List<VarSymbol> freevars(ClassSymbol c)  {
        if ((c.owner.kind & (VAR | MTH)) != 0) {
            // 当c为本地类或匿名类并且之前没有针对此类进行自由变量收集时，需要获取自由变量列表fvs。
            List<VarSymbol> fvs = freevarCache.get(c);
            if (fvs == null) {
                // 创建FreeVarCollector对象
                FreeVarCollector collector = new FreeVarCollector(c);
                // 调用scan()方法扫描JCClassDecl语法树
                collector.scan(classDef(c));
                fvs = collector.fvs;
                // 放到缓存里
                freevarCache.put(c, fvs);
            }
            // 返回
            return fvs;
        } else {
            return List.nil();
        }
    }

    Map<TypeSymbol,EnumMapping> enumSwitchMap = new LinkedHashMap<TypeSymbol,EnumMapping>();

    EnumMapping mapForEnum(DiagnosticPosition pos, TypeSymbol enumClass) {
        EnumMapping map = enumSwitchMap.get(enumClass);
        if (map == null)
            enumSwitchMap.put(enumClass, map = new EnumMapping(pos, enumClass));
        return map;
    }

    /** This map gives a translation table to be used for enum
     *  switches.
     *
     *  <p>For each enum that appears as the type of a switch
     *  expression, we maintain an EnumMapping to assist in the
     *  translation, as exemplified by the following example:
     *
     *  <p>we translate
     *  <pre>
     *          switch(colorExpression) {
     *          case red: stmt1;
     *          case green: stmt2;
     *          }
     *  </pre>
     *  into
     *  <pre>
     *          switch(Outer$0.$EnumMap$Color[colorExpression.ordinal()]) {
     *          case 1: stmt1;
     *          case 2: stmt2
     *          }
     *  </pre>
     *  with the auxiliary table initialized as follows:
     *  <pre>
     *          class Outer$0 {
     *              synthetic final int[] $EnumMap$Color = new int[Color.values().length];
     *              static {
     *                  try { $EnumMap$Color[red.ordinal()] = 1; } catch (NoSuchFieldError ex) {}
     *                  try { $EnumMap$Color[green.ordinal()] = 2; } catch (NoSuchFieldError ex) {}
     *              }
     *          }
     *  </pre>
     *  class EnumMapping provides mapping data and support methods for this translation.
     */
    class EnumMapping {
        EnumMapping(DiagnosticPosition pos, TypeSymbol forEnum) {
            this.forEnum = forEnum;
            this.values = new LinkedHashMap<VarSymbol,Integer>();
            this.pos = pos;
            // 在构造方法中初始化为$SwitchMap$chapter15$Fruit，这个VarSymbol对象的owner为chapter15.Test$1。
            Name varName = names
                .fromString(target.syntheticNameChar() +
                            "SwitchMap" +
                            target.syntheticNameChar() +
                            writer.xClassName(forEnum.type).toString()
                            .replace('/', '.')
                            .replace('.', target.syntheticNameChar()));
            ClassSymbol outerCacheClass = outerCacheClass();
            this.mapVar = new VarSymbol(STATIC | SYNTHETIC | FINAL,
                                        varName,
                                        new ArrayType(syms.intType, syms.arrayClass),
                                        outerCacheClass);
            enterSynthetic(pos, mapVar, outerCacheClass.members());
        }

        DiagnosticPosition pos = null;

        // the next value to use
        int next = 1; // 0 (unused map elements) go to the default label

        // the enum for which this is a map
        final TypeSymbol forEnum;

        // the field containing the map
        final VarSymbol mapVar;

        // the mapped values
        final Map<VarSymbol,Integer> values;

        JCLiteral forConstant(VarSymbol v) {
            // values为Map<VarSymbol,Integer>类型，保存各个变量到整数的映射关系
            Integer result = values.get(v);
            if (result == null)
                // next是int类型变量，初始值为1，所有的非默认分支按顺序从上到下分配的整数都是从1开始递增的。
                values.put(v, result = next++);
            // 最后组成一个新的enumSwitch语法树节点并返回
            return make.Literal(result);
        }

        // generate the field initializer for the map
        // 建一个名称为mapVar的整数类型的数组，然后在静态块内完成对数组的填充。
        // 其中数组的下标通过调用各个枚举常量的ordinal()方法获取，而枚举常量对应的整数值已经事先保存到values中了，
        // 只需要按对应关系获取即可
        void translate() {
            make.at(pos.getStartPosition());
            JCClassDecl owner = classDef((ClassSymbol)mapVar.owner);

            // synthetic static final int[] $SwitchMap$Color = new int[Color.values().length];
            MethodSymbol valuesMethod = lookupMethod(pos,
                                                     names.values,
                                                     forEnum.type,
                                                     List.<Type>nil());
            JCExpression size = make // Color.values().length
                .Select(make.App(make.QualIdent(valuesMethod)),
                        syms.lengthVar);
            JCExpression mapVarInit = make
                .NewArray(make.Type(syms.intType), List.of(size), null)
                .setType(new ArrayType(syms.intType, syms.arrayClass));

            // try { $SwitchMap$Color[red.ordinal()] = 1; } catch (java.lang.NoSuchFieldError ex) {}
            ListBuffer<JCStatement> stmts = new ListBuffer<JCStatement>();
            Symbol ordinalMethod = lookupMethod(pos,
                                                names.ordinal,
                                                forEnum.type,
                                                List.<Type>nil());
            List<JCCatch> catcher = List.<JCCatch>nil()
                .prepend(make.Catch(make.VarDef(new VarSymbol(PARAMETER, names.ex,
                                                              syms.noSuchFieldErrorType,
                                                              syms.noSymbol),
                                                null),
                                    make.Block(0, List.<JCStatement>nil())));
            for (Map.Entry<VarSymbol,Integer> e : values.entrySet()) {
                VarSymbol enumerator = e.getKey();
                Integer mappedValue = e.getValue();
                JCExpression assign = make
                    .Assign(make.Indexed(mapVar,
                                         make.App(make.Select(make.QualIdent(enumerator),
                                                              ordinalMethod))),
                            make.Literal(mappedValue))
                    .setType(syms.intType);
                JCStatement exec = make.Exec(assign);
                JCStatement _try = make.Try(make.Block(0, List.of(exec)), catcher, null);
                stmts.append(_try);
            }

            owner.defs = owner.defs
                .prepend(make.Block(STATIC, stmts.toList()))
                .prepend(make.VarDef(mapVar, mapVarInit));
        }
    }


/**************************************************************************
 * Tree building blocks
 *************************************************************************/

    /** Equivalent to make.at(pos.getStartPosition()) with side effect of caching
     *  pos as make_pos, for use in diagnostics.
     **/
    TreeMaker make_at(DiagnosticPosition pos) {
        make_pos = pos;
        return make.at(pos);
    }

    /** Make an attributed tree representing a literal. This will be an
     *  Ident node in the case of boolean literals, a Literal node in all
     *  other cases.
     *  @param type       The literal's type.
     *  @param value      The literal's value.
     */
    JCExpression makeLit(Type type, Object value) {
        return make.Literal(type.tag, value).setType(type.constType(value));
    }

    /** Make an attributed tree representing null.
     */
    JCExpression makeNull() {
        return makeLit(syms.botType, null);
    }

    /** Make an attributed class instance creation expression.
     *  @param ctype    The class type.
     *  @param args     The constructor arguments.
     */
    JCNewClass makeNewClass(Type ctype, List<JCExpression> args) {
        JCNewClass tree = make.NewClass(null,
            null, make.QualIdent(ctype.tsym), args, null);
        tree.constructor = rs.resolveConstructor(
            make_pos, attrEnv, ctype, TreeInfo.types(args), null, false, false);
        tree.type = ctype;
        return tree;
    }

    /** Make an attributed unary expression.
     *  @param optag    The operators tree tag.
     *  @param arg      The operator's argument.
     */
    JCUnary makeUnary(int optag, JCExpression arg) {
        JCUnary tree = make.Unary(optag, arg);
        tree.operator = rs.resolveUnaryOperator(
            make_pos, optag, attrEnv, arg.type);
        tree.type = tree.operator.type.getReturnType();
        return tree;
    }

    /** Make an attributed binary expression.
     *  @param optag    The operators tree tag.
     *  @param lhs      The operator's left argument.
     *  @param rhs      The operator's right argument.
     */
    JCBinary makeBinary(int optag, JCExpression lhs, JCExpression rhs) {
        JCBinary tree = make.Binary(optag, lhs, rhs);
        tree.operator = rs.resolveBinaryOperator(
            make_pos, optag, attrEnv, lhs.type, rhs.type);
        tree.type = tree.operator.type.getReturnType();
        return tree;
    }

    /** Make an attributed assignop expression.
     *  @param optag    The operators tree tag.
     *  @param lhs      The operator's left argument.
     *  @param rhs      The operator's right argument.
     */
    JCAssignOp makeAssignop(int optag, JCTree lhs, JCTree rhs) {
        JCAssignOp tree = make.Assignop(optag, lhs, rhs);
        tree.operator = rs.resolveBinaryOperator(
            make_pos, tree.getTag() - JCTree.ASGOffset, attrEnv, lhs.type, rhs.type);
        tree.type = lhs.type;
        return tree;
    }

    /** Convert tree into string object, unless it has already a
     *  reference type..
     */
    JCExpression makeString(JCExpression tree) {
        if (tree.type.tag >= CLASS) {
            return tree;
        } else {
            Symbol valueOfSym = lookupMethod(tree.pos(),
                                             names.valueOf,
                                             syms.stringType,
                                             List.of(tree.type));
            return make.App(make.QualIdent(valueOfSym), List.of(tree));
        }
    }

    /** Create an empty anonymous class definition and enter and complete
     *  its symbol. Return the class definition's symbol.
     *  and create
     *  @param flags    The class symbol's flags
     *  @param owner    The class symbol's owner
     */
    ClassSymbol makeEmptyClass(long flags, ClassSymbol owner) {
        // Create class symbol.
        ClassSymbol c = reader.defineClass(names.empty, owner);
        c.flatname = chk.localClassName(c);
        c.sourcefile = owner.sourcefile;
        c.completer = null;
        c.members_field = new Scope(c);
        c.flags_field = flags;
        ClassType ctype = (ClassType) c.type;
        ctype.supertype_field = syms.objectType;
        ctype.interfaces_field = List.nil();

        JCClassDecl odef = classDef(owner);

        // Enter class symbol in owner scope and compiled table.
        enterSynthetic(odef.pos(), c, owner.members());
        chk.compiled.put(c.flatname, c);

        // Create class definition tree.
        JCClassDecl cdef = make.ClassDef(
            make.Modifiers(flags), names.empty,
            List.<JCTypeParameter>nil(),
            null, List.<JCExpression>nil(), List.<JCTree>nil());
        cdef.sym = c;
        cdef.type = c.type;

        // Append class definition tree to owner's definitions.
        odef.defs = odef.defs.prepend(cdef);

        return c;
    }

/**************************************************************************
 * Symbol manipulation utilities
 *************************************************************************/

    /** Enter a synthetic symbol in a given scope, but complain if there was already one there.
     *  @param pos           Position for error reporting.
     *  @param sym           The symbol.
     *  @param s             The scope.
     */
    private void enterSynthetic(DiagnosticPosition pos, Symbol sym, Scope s) {
        s.enter(sym);
    }

    /** Create a fresh synthetic name within a given scope - the unique name is
     *  obtained by appending '$' chars at the end of the name until no match
     *  is found.
     *
     * @param name base name
     * @param s scope in which the name has to be unique
     * @return fresh synthetic name
     */
    private Name makeSyntheticName(Name name, Scope s) {
        do {
            name = name.append(
                    target.syntheticNameChar(),
                    names.empty);
        } while (lookupSynthetic(name, s) != null);
        return name;
    }

    /** Check whether synthetic symbols generated during lowering conflict
     *  with user-defined symbols.
     *
     *  @param translatedTrees lowered class trees
     */
    void checkConflicts(List<JCTree> translatedTrees) {
        for (JCTree t : translatedTrees) {
            t.accept(conflictsChecker);
        }
    }

    JCTree.Visitor conflictsChecker = new TreeScanner() {

        TypeSymbol currentClass;

        @Override
        public void visitMethodDef(JCMethodDecl that) {
            chk.checkConflicts(that.pos(), that.sym, currentClass);
            super.visitMethodDef(that);
        }

        @Override
        public void visitVarDef(JCVariableDecl that) {
            if (that.sym.owner.kind == TYP) {
                chk.checkConflicts(that.pos(), that.sym, currentClass);
            }
            super.visitVarDef(that);
        }

        @Override
        public void visitClassDef(JCClassDecl that) {
            TypeSymbol prevCurrentClass = currentClass;
            currentClass = that.sym;
            try {
                super.visitClassDef(that);
            }
            finally {
                currentClass = prevCurrentClass;
            }
        }
    };

    /** Look up a synthetic name in a given scope.
     *  @param scope        The scope.
     *  @param name         The name.
     */
    private Symbol lookupSynthetic(Name name, Scope s) {
        Symbol sym = s.lookup(name).sym;
        return (sym==null || (sym.flags()&SYNTHETIC)==0) ? null : sym;
    }

    /** Look up a method in a given scope.
     */
    private MethodSymbol lookupMethod(DiagnosticPosition pos, Name name, Type qual, List<Type> args) {
        return rs.resolveInternalMethod(pos, attrEnv, qual, name, args, null);
    }

    /** Look up a constructor.
     */
    private MethodSymbol lookupConstructor(DiagnosticPosition pos, Type qual, List<Type> args) {
        return rs.resolveInternalConstructor(pos, attrEnv, qual, args, null);
    }

    /** Look up a field.
     */
    private VarSymbol lookupField(DiagnosticPosition pos, Type qual, Name name) {
        return rs.resolveInternalField(pos, attrEnv, qual, name);
    }

    /** Anon inner classes are used as access constructor tags.
     * accessConstructorTag will use an existing anon class if one is available,
     * and synthethise a class (with makeEmptyClass) if one is not available.
     * However, there is a small possibility that an existing class will not
     * be generated as expected if it is inside a conditional with a constant
     * expression. If that is found to be the case, create an empty class here.
     */
    private void checkAccessConstructorTags() {
        for (List<ClassSymbol> l = accessConstrTags; l.nonEmpty(); l = l.tail) {
            ClassSymbol c = l.head;
            if (isTranslatedClassAvailable(c))
                continue;
            // Create class definition tree.
            JCClassDecl cdef = make.ClassDef(
                make.Modifiers(STATIC | SYNTHETIC), names.empty,
                List.<JCTypeParameter>nil(),
                null, List.<JCExpression>nil(), List.<JCTree>nil());
            cdef.sym = c;
            cdef.type = c.type;
            // add it to the list of classes to be generated
            translated.append(cdef);
        }
    }
    // where
    private boolean isTranslatedClassAvailable(ClassSymbol c) {
        for (JCTree tree: translated) {
            if (tree.getTag() == JCTree.CLASSDEF
                    && ((JCClassDecl) tree).sym == c) {
                return true;
            }
        }
        return false;
    }

/**************************************************************************
 * Access methods
 *************************************************************************/

    /** Access codes for dereferencing, assignment,
     *  and pre/post increment/decrement.
     *  Access codes for assignment operations are determined by method accessCode
     *  below.
     *
     *  All access codes for accesses to the current class are even.
     *  If a member of the superclass should be accessed instead (because
     *  access was via a qualified super), add one to the corresponding code
     *  for the current class, making the number odd.
     *  This numbering scheme is used by the backend to decide whether
     *  to issue an invokevirtual or invokespecial call.
     *
     *  @see Gen.visitSelect(Select tree)
     */
    private static final int
        DEREFcode = 0,
        ASSIGNcode = 2,
        PREINCcode = 4,
        PREDECcode = 6,
        POSTINCcode = 8,
        POSTDECcode = 10,
        FIRSTASGOPcode = 12;

    /** Number of access codes
     */
    private static final int NCODES = accessCode(ByteCodes.lushrl) + 2;

    /** A mapping from symbols to their access numbers.
     */
    // accessNums在生成获取方法的名称时提供编号：access$000、access$100和access$200
    private Map<Symbol,Integer> accessNums;

    /** A mapping from symbols to an array of access symbols, indexed by
     *  access code.
     */
    // accessSyms存储了成员到获取方法的映射关系，同一个成员的使用方式不同，就会生成不同的获取方法，
    // 所以一个具体的vsym对应一个MethodSymbol类型的数组，初始化时指定大小为98
    private Map<Symbol,MethodSymbol[]> accessSyms;

    /** A mapping from (constructor) symbols to access constructor symbols.
     */
    // 保存了原构造方法到新构造方法的映射
    private Map<Symbol,MethodSymbol> accessConstrs;

    /** A list of all class symbols used for access constructor tags.
     */
    private List<ClassSymbol> accessConstrTags;

    /** A queue for all accessed symbols.
     */
    // 所有可访问的符号队列
    private ListBuffer<Symbol> accessed;

    /** Map bytecode of binary operation to access code of corresponding
     *  assignment operation. This is always an even number.
     *  将二进制操作的字节码映射到相应赋值操作的访问码。这始终是偶数。
     */
    // 助词符是ByteCodes类中定义的，编号从96~131与实际的虚拟机指令编码对应，而256及270~275都是Javac虚拟出来的指令，
    // 并没有Java虚拟机对应的指令存在。在代码生成的过程中会将270~275的虚拟指令对应映射到120~125上，这样Java语言就支持了移位时右侧操作数可以为long类型的语法。
    // 调用accessCode(int bytecode)方法可能返回12~96之间的任何偶数，所以与前面从0~12之间定义的常量合并后，
    // 方法accessCode(JCTree tree,JCTree enclOp)可能返回0~96之间的任何偶数
    private static int accessCode(int bytecode) {
        if (ByteCodes.iadd <= bytecode && bytecode <= ByteCodes.lxor)
            return (bytecode - iadd) * 2 + FIRSTASGOPcode;
        else if (bytecode == ByteCodes.string_add)
            return (ByteCodes.lxor + 1 - iadd) * 2 + FIRSTASGOPcode;
        else if (ByteCodes.ishll <= bytecode && bytecode <= ByteCodes.lushrl)
            return (bytecode - ishll + ByteCodes.lxor + 2 - iadd) * 2 + FIRSTASGOPcode;
        else
            return -1;
    }

    /** return access code for identifier,
     *  @param tree     The tree representing the identifier use.
     *  @param enclOp   The closest enclosing operation node of tree,
     *                  null if tree is not a subtree of an operation.
     */
    // acode算出来的是0～96的所有偶数
    private static int accessCode(JCTree tree, JCTree enclOp) {
        if (enclOp == null)
            // 当enclOp为null时返回DEREFcode
            return DEREFcode;
        else if (enclOp.getTag() == JCTree.ASSIGN &&
                 tree == TreeInfo.skipParens(((JCAssign) enclOp).lhs))
            // 当enclOp等于JCTree.ASSIGN时返回ASSIGNcode
            return ASSIGNcode;
        else if (JCTree.PREINC <= enclOp.getTag() && enclOp.getTag() <= JCTree.POSTDEC &&
                 tree == TreeInfo.skipParens(((JCUnary) enclOp).arg))
            // 当enclOp大于等于JCTree.PREINC而小于等于JCTree.POSTDEC时，
            // 返回对应的PREINCcode、PREDECcode、POSTINCcode或POSTDECcode；
            return (enclOp.getTag() - JCTree.PREINC) * 2 + PREINCcode;
        else if (JCTree.BITOR_ASG <= enclOp.getTag() && enclOp.getTag() <= JCTree.MOD_ASG &&
                 tree == TreeInfo.skipParens(((JCAssignOp) enclOp).lhs))
            // 当enclOp大于等于JCTree.BITOR_ASG而小于等于JCTree.MOD_ASG时，
            // 其实就是判断当前的二元表达式对应的树节点中运算符是否为复合运算符，
            // JCTree.BITOR_ASG就是|=，对应值为76，而JCTree.MOD_ASG就是%=，对应值为92，
            // 在数值76~92之间都是复合操作符
            // 对于JCAssignOp树节点来说，其operator属性的类型为OperatorSymbol，在符号引用消解时会找到对应的预先建立的OperatorSymbol对象，
            // 获取opcode就是ByteCodes中的编码，然后调用accessCode()方法
            return accessCode(((OperatorSymbol) ((JCAssignOp) enclOp).operator).opcode);
        else
            return DEREFcode;
    }

    /** Return binary operator that corresponds to given access code.
     */
    // 返回二元运算符
    private OperatorSymbol binaryAccessOperator(int acode) {
        for (Scope.Entry e = syms.predefClass.members().elems;
             e != null;
             e = e.sibling) {
            if (e.sym instanceof OperatorSymbol) {
                OperatorSymbol op = (OperatorSymbol)e.sym;
                // 通过acode取出对应的OperatorSymbol对象并返回
                if (accessCode(op.opcode) == acode)
                    return op;
            }
        }
        return null;
    }

    /** Return tree tag for assignment operation corresponding
     *  to given binary operator.
     */
    private static int treeTag(OperatorSymbol operator) {
        switch (operator.opcode) {
        case ByteCodes.ior: case ByteCodes.lor:
            return JCTree.BITOR_ASG;
        case ByteCodes.ixor: case ByteCodes.lxor:
            return JCTree.BITXOR_ASG;
        case ByteCodes.iand: case ByteCodes.land:
            return JCTree.BITAND_ASG;
        case ByteCodes.ishl: case ByteCodes.lshl:
        case ByteCodes.ishll: case ByteCodes.lshll:
            return JCTree.SL_ASG;
        case ByteCodes.ishr: case ByteCodes.lshr:
        case ByteCodes.ishrl: case ByteCodes.lshrl:
            return JCTree.SR_ASG;
        case ByteCodes.iushr: case ByteCodes.lushr:
        case ByteCodes.iushrl: case ByteCodes.lushrl:
            return JCTree.USR_ASG;
        case ByteCodes.iadd: case ByteCodes.ladd:
        case ByteCodes.fadd: case ByteCodes.dadd:
        case ByteCodes.string_add:
            return JCTree.PLUS_ASG;
        case ByteCodes.isub: case ByteCodes.lsub:
        case ByteCodes.fsub: case ByteCodes.dsub:
            return JCTree.MINUS_ASG;
        case ByteCodes.imul: case ByteCodes.lmul:
        case ByteCodes.fmul: case ByteCodes.dmul:
            return JCTree.MUL_ASG;
        case ByteCodes.idiv: case ByteCodes.ldiv:
        case ByteCodes.fdiv: case ByteCodes.ddiv:
            return JCTree.DIV_ASG;
        case ByteCodes.imod: case ByteCodes.lmod:
        case ByteCodes.fmod: case ByteCodes.dmod:
            return JCTree.MOD_ASG;
        default:
            throw new AssertionError();
        }
    }

    /** The name of the access method with number `anum' and access code `acode'.
     */
    Name accessName(int anum, int acode) {
        return names.fromString(
            "access" + target.syntheticNameChar() + anum + acode / 10 + acode % 10);
    }

    /** Return access symbol for a private or protected symbol from an inner class.
     *  从内部类返回私有或受保护符号的访问符号。
     *  @param sym        The accessed private symbol.
     *  @param tree       The accessing tree.
     *  @param enclOp     The closest enclosing operation node of tree,
     *                    null if tree is not a subtree of an operation.
     *  @param protAccess Is access to a protected symbol in another
     *                    package?
     *  @param refSuper   Is access via a (qualified) C.super?
     */
    MethodSymbol accessSymbol(Symbol sym, JCTree tree, JCTree enclOp,
                              boolean protAccess, boolean refSuper) {
        // accessSymbol()方法首先计算accOwner的值，获取方法会作为accOwner的一个成员存在，
        // 所以获取方法最终会填充到accOwner.members_field中
        ClassSymbol accOwner = refSuper && protAccess
            ? (ClassSymbol)((JCFieldAccess) tree).selected.type.tsym
            // 如果refSuper或protAccess中至少有一个为false时，会调用accessClass()方法计算accOwner
            : accessClass(sym, protAccess, tree);

        Symbol vsym = sym;
        if (sym.owner != accOwner) {
            vsym = sym.clone(accOwner);
            actualSymbols.put(vsym, sym);
        }

        Integer anum              // The access number of the access method.
            = accessNums.get(vsym);
        if (anum == null) {
            anum = accessed.length();
            accessNums.put(vsym, anum);
            accessSyms.put(vsym, new MethodSymbol[NCODES]);
            accessed.append(vsym);
            // System.out.println("accessing " + vsym + " in " + vsym.location());
        }

        int acode;                // 生成的获取方法的编号($000)
        List<Type> argtypes;      // 生成的获取方法的形参列表
        Type restype;             // 生成的获取方法的返回值类型
        List<Type> thrown;        // 生成的获取方法的异常抛出类型
        switch (vsym.kind) {
        case VAR:
            // acode为获取方法名称的一部分，当vsym为变量时，调用accessCode()方法计算acode值。
            acode = accessCode(tree, enclOp);
            if (acode >= FIRSTASGOPcode) {
                // 当acode大于等于FIRSTASGOPcode时，也就是enclOp为复合赋值表达式时，
                // 调用binarAccessOperator()方法获取OperatorSymbol对象
                OperatorSymbol operator = binaryAccessOperator(acode);
                // 通过这个对象可以获取在调用获取方法时使用的实际参数的类型
                if (operator.opcode == string_add)
                    // 如果operator的opcode为string_add，
                    // 例15-23
                    argtypes = List.of(syms.objectType);
                else
                    // 否则追加operator的形式参数中除第一个类型外的所有类型
                    argtypes = operator.type.getParameterTypes().tail;
            } else if (acode == ASSIGNcode)
                argtypes = List.of(vsym.erasure(types));
            else
                argtypes = List.nil();
            restype = vsym.erasure(types);
            thrown = List.nil();
            break;
        case MTH:
            // 当访问的成员是方法时，获取方法的名称中acode的值永远为DEREFcode，也就是0
            acode = DEREFcode;
            argtypes = vsym.erasure(types).getParameterTypes();
            restype = vsym.erasure(types).getReturnType();
            thrown = vsym.type.getThrownTypes();
            break;
        default:
            throw new AssertionError();
        }

        // 当protAccess与refSuper的属性值同时为true时，acode加1，
        // 这样就相当于可能取0~97之间的任何数据。一个成员对应生成的获取方法，
        // 可根据使用方式的不同生成最多98个获取方法，
        // 这就是将accessSyms集合中MethodSymbol数组的大小初始化为98的原因
        // 例15-22
        if (protAccess && refSuper)
            acode++;

        // 已经计算好获取方法的相关信息

        if ((vsym.flags() & STATIC) == 0) {
            // 当vsym代表的是实例成员时还需要为生成方法的形式参数追加类型
            // 通过vsym.owner.erauser(types)获取
            argtypes = argtypes.prepend(vsym.owner.erasure(types));
        }
        // 然后获取accessors数组中指定的MethodSymbol对象accessor
        MethodSymbol[] accessors = accessSyms.get(vsym);
        MethodSymbol accessor = accessors[acode];
        if (accessor == null) {
            // 如果对象不存在，则需要合成
            accessor = new MethodSymbol(
                STATIC | SYNTHETIC,
                accessName(anum.intValue(), acode),
                new MethodType(argtypes, restype, thrown, syms.methodClass),
                accOwner);
            enterSynthetic(tree.pos(), accessor, accOwner.members());
            accessors[acode] = accessor;
        }
        // 返回合成的accessor
        return accessor;
    }

    /** The qualifier to be used for accessing a symbol in an outer class.
     *  This is either C.sym or C.this.sym, depending on whether or not
     *  sym is static.
     *  @param sym   The accessed symbol.
     */
    JCExpression accessBase(DiagnosticPosition pos, Symbol sym) {
        return (sym.flags() & STATIC) != 0
            ? access(make.at(pos.getStartPosition()).QualIdent(sym.owner))
            : makeOwnerThis(pos, sym, true);
    }

    /** Do we need an access method to reference private symbol?
     */
    // 判断是否需要处理对私有构造方法的调用
    boolean needsPrivateAccess(Symbol sym) {
        if ((sym.flags() & PRIVATE) == 0 || sym.owner == currentClass) {
            // 如果为非私有成员或者在当前类中使用私有成员
            return false;
        } else if (sym.name == names.init && (sym.owner.owner.kind & (VAR | MTH)) != 0) {
            // 如果有调用本地类的私有构造方法，则直接去掉构造方法的private修饰符
            // 例15-16
            sym.flags_field &= ~PRIVATE;
            return false;
        } else {
            return true;
        }
    }

    /** Do we need an access method to reference symbol in other package?
     */
    boolean needsProtectedAccess(Symbol sym, JCTree tree) {
        // 其中sym就是被引用的符号，currentClass就是引用sym的那个类
        if ((sym.flags() & PROTECTED) == 0 ||
            // 由protected修饰的sym是否需要添加获取方法，
            // 当sym没有protected修饰时直接返回false，表示不需要添加获取方法
            sym.owner.owner == currentClass.owner ||
            // 被引用的符号所定义的类与引用符号的类处在同一个包下，那么protected成员在相同包中仍然能够正常访问，不需要添加获取方法
            sym.packge() == currentClass.packge())
            return false;
        if (!currentClass.isSubClass(sym.owner, types))
            // 如果当前类currentClass不为sym.owner的子类则返回true，表示需要添加获取方法
            return true;
        if ((sym.flags() & STATIC) != 0 ||
            tree.getTag() != JCTree.SELECT ||
            TreeInfo.name(((JCFieldAccess) tree).selected) == names._super)
            return false;
        // 因为如果为非子类，则没有权限获取父类的由protected修饰的成员
        return !((JCFieldAccess) tree).selected.type.tsym.isSubClass(currentClass, types);
    }

    /** The class in which an access method for given symbol goes.
     *  @param sym        The access symbol
     *  @param protAccess Is access to a protected symbol in another
     *                    package?
     */
    // 给定符号的访问方法所在的类。
    ClassSymbol accessClass(Symbol sym, boolean protAccess, JCTree tree) {
        if (protAccess) {
            // 当protAccess为true时，说明refSuper为false，这样才会调用accessClass()方法，
            // 这个方法主要是计算ClassSymbol对象c的值，也就是计算获取方法需要添加到哪个类中
            Symbol qualifier = null;
            ClassSymbol c = currentClass;
            if (tree.getTag() == JCTree.SELECT && (sym.flags() & STATIC) == 0) {
                qualifier = ((JCFieldAccess) tree).selected.type.tsym;
                while (!qualifier.isSubClass(c, types)) {
                    c = c.owner.enclClass();
                }
                return c;
            } else {
                while (!c.isSubClass(sym.owner, types)) {
                    c = c.owner.enclClass();
                }
            }
            return c;
        } else {
            // 当protAccess为false时，直接返回sym.owner.enclClass()表达式的值，也就是sym肯定是私有成员
            return sym.owner.enclClass();
        }
    }

    /** Ensure that identifier is accessible, return tree accessing the identifier.
     *  @param sym      The accessed symbol.
     *  @param tree     The tree referring to the symbol.
     *  @param enclOp   The closest enclosing operation node of tree,
     *                  null if tree is not a subtree of an operation.
     *  @param refSuper Is access via a (qualified) C.super?
     */
    JCExpression access(Symbol sym, JCExpression tree, JCExpression enclOp, boolean refSuper) {
        // Access a free variable via its proxy, or its proxy's proxy
        while (sym.kind == VAR && sym.owner.kind == MTH &&
            sym.owner.enclClass() != currentClass) {
            // 条件判断保存引用的是自由变量
            Object cv = ((VarSymbol)sym).getConstValue();
            if (cv != null) {
                // // 当cv不等于null时，表示是字面量值，
                // 直接将引用的自由变量替换为对应的字面量值
                make.at(tree.pos);
                return makeLit(sym.type, cv);
            }
            // 引用的自由变量更新为引用自由变量对应合成的成员变量，所以从proxies中查找
            sym = proxies.lookup(proxyName(sym.name)).sym;
            Assert.check(sym != null && (sym.flags_field & FINAL) != 0);
            tree = make.at(tree.pos).Ident(sym);
        }
        // 如果sym是实例成员，base就是具体的实例，要访问base实例中的sym成员变量，需要向获取方法传递参数
        // 如果sym是静态成员，base可以是类型名称或具体的实例
        // 例15-19
        JCExpression base = (tree.getTag() == JCTree.SELECT) ? ((JCFieldAccess) tree).selected : null;
        switch (sym.kind) {
        case TYP:
            if (sym.owner.kind != PCK) {
                // Convert type idents to
                // <flat name> or <package name> . <flat name>
                Name flatname = Convert.shortName(sym.flatName());
                while (base != null &&
                       TreeInfo.symbol(base) != null &&
                       TreeInfo.symbol(base).kind != PCK) {
                    base = (base.getTag() == JCTree.SELECT)
                        ? ((JCFieldAccess) base).selected
                        : null;
                }
                if (tree.getTag() == JCTree.IDENT) {
                    ((JCIdent) tree).name = flatname;
                } else if (base == null) {
                    tree = make.at(tree.pos).Ident(sym);
                    ((JCIdent) tree).name = flatname;
                } else {
                    ((JCFieldAccess) tree).selected = base;
                    ((JCFieldAccess) tree).name = flatname;
                }
            }
            break;
        case MTH: case VAR:
            if (sym.owner.kind == TYP) {

                // 引用成员解语法糖
                // 访问外部类的一些成员在解语法糖阶段需要通过调用获取方法的方式来访问，
                // 对于成员类型来说，如果有protected或private修饰，则最终作为一个单独的顶层类时会去掉权限访问符，去掉后不会影响对此类的访问，所以不需要特殊处理；
                // 对于方法和成员变量来说，可以通过access()方法判断是否需要为成员添加获取方法
                // 例15-17
                boolean protAccess = refSuper && !needsPrivateAccess(sym)
                    // 调用needsProtectedAccess(sym, tree)方法返回true
                    || needsProtectedAccess(sym, tree);
                // sym就是被引用的成员符号。当accReq的值为true时就会添加对应的获取方法
                // 调用needsPrivateAccess(sym)方法返回true
                boolean accReq = protAccess || needsPrivateAccess(sym);

                // A base has to be supplied for
                //  - simple identifiers accessing variables in outer classes.
                boolean baseReq =
                    base == null &&
                    sym.owner != syms.predefClass &&
                    !sym.isMemberOf(currentClass, types);

                if (accReq || baseReq) {
                    make.at(tree.pos);

                    // Constants are replaced by their constant value.
                    if (sym.kind == VAR) {
                        Object cv = ((VarSymbol)sym).getConstValue();
                        if (cv != null) return makeLit(sym.type, cv);
                    }

                    // 私有变量和方法被对其访问方法的调用所取代。
                    if (accReq) {
                        // 参数sym就是被引用的符号，tree就是引用的树节点
                        List<JCExpression> args = List.nil();
                        if ((sym.flags() & STATIC) == 0) {
                            // 如果sym没有static修饰，在调用获取方法时需要追加base参数到实际参数列表头部
                            if (base == null)
                                // 例如实例15-19中对变量a的引用，调用makeOwnerThis()方法获取的base为JCIdent(this$0)。
                                base = makeOwnerThis(tree.pos(), sym, true);
                            // 如果sym没有static修饰，在调用获取方法时需要追加base参数到实际参数列表头部
                            args = args.prepend(base);
                            base = null;   // so we don't duplicate code
                        }
                        // 调用accessSymbol()方法添加获取方法，同时也要更新访问方式
                        // 例15-19
                        Symbol access = accessSymbol(sym, tree,
                                                     enclOp, protAccess,
                                                     refSuper);
                        // 有了调用获取方法的实际参数列表后，就会创建调用获取方法的表达式receiver了，
                        // 对于a变量来说，计算的receiver为JCFieldAccess(Outer.access$000)，
                        // 最后access()方法返回JCMethodInvocation对象
                        JCExpression receiver = make.Select(
                            base != null ? base : make.QualIdent(access.owner),
                            access);
                        return make.App(receiver, args);

                    // Other accesses to members of outer classes get a
                    // qualifier.
                    } else if (baseReq) {
                        return make.at(tree.pos).Select(
                            accessBase(tree.pos(), sym), sym).setType(tree.type);
                    }
                }
            }
        }
        return tree;
    }

    /** Ensure that identifier is accessible, return tree accessing the identifier.
     *  @param tree     The identifier tree.
     */
    JCExpression access(JCExpression tree) {
        Symbol sym = TreeInfo.symbol(tree);
        return sym == null ? tree : access(sym, tree, null, false);
    }

    /** Return access constructor for a private constructor,
     *  or the constructor itself, if no access constructor is needed.
     *  如果不需要访问构造函数，则返回私有构造函数或构造函数本身的访问构造函数。
     *  @param pos       The position to report diagnostics, if any.
     *  @param constr    The private constructor.
     */
    Symbol accessConstructor(DiagnosticPosition pos, Symbol constr) {
        // 调用needsPrivateAccess()方法判断是否需要处理对私有构造方法的调用
        if (needsPrivateAccess(constr)) {
            // 合成新的构造方法aconstr
            ClassSymbol accOwner = constr.owner.enclClass();
            // 查看是否已经合成过次私有方法对应的构造方法
            MethodSymbol aconstr = accessConstrs.get(constr);
            // 没有就合成一个
            if (aconstr == null) {
                List<Type> argtypes = constr.type.getParameterTypes();
                if ((accOwner.flags_field & ENUM) != 0)
                    argtypes = argtypes
                        .prepend(syms.intType)
                        .prepend(syms.stringType);
                // 更新的是标注语法树，所以合成构造方法也一定要标注对应的符号和类型
                aconstr = new MethodSymbol(
                    SYNTHETIC,
                    names.init,
                    // 在创建MethodType对象时，为形式参数列表argtypes中追加了一个类型，
                    // 首先通过调用accessConstructorTag()方法创建一个ClassSymbol对象，
                    // 然后调用erasure()方法获取对应的类型。
                    new MethodType(
                        argtypes.append(
                            accessConstructorTag().erasure(types)),
                        constr.type.getReturnType(),
                        constr.type.getThrownTypes(),
                        syms.methodClass),
                    accOwner);
                // 调用enterSynthetic()方法将aconstr填充到accOwner.members_field中
                // 也就是说合成的构造方法会添加到accOwner中。
                enterSynthetic(pos, aconstr, accOwner.members());
                // 通过accessConstrs保存私有构造方法到合成构造方法的对应关系，这样下次需要为私有构造方法合成构造方法时就可以重用这个已经合成的构造方法
                accessConstrs.put(constr, aconstr);
                // 另外，私有构造方法还会保存到类型为ListBuffer<Symbol>的accessed变量中，
                // 后序将调用translateTopLevelClass()方法循环这个列表中的值，
                // 然后通过accessConstrs取出所有合成的构造方法，然后合成JCMethodDecl树节点并添加到标注语法树上
                accessed.append(constr);
            }
            return aconstr;
        } else {
            return constr;
        }
    }

    /** Return an anonymous class nested in this toplevel class.
     */
    // 对于实例15-15来说，调用当前方法可以获取到一个Outer$1类型，然后追加到形式参数列表的末尾。
    ClassSymbol accessConstructorTag() {
        ClassSymbol topClass = currentClass.outermostClass();
        Name flatname = names.fromString("" + topClass.getQualifiedName() +
                                         target.syntheticNameChar() +
                                         "1");
        ClassSymbol ctag = chk.compiled.get(flatname);
        if (ctag == null)
            ctag = makeEmptyClass(STATIC | SYNTHETIC, topClass);
        // keep a record of all tags, to verify that all are generated as required
        accessConstrTags = accessConstrTags.prepend(ctag);
        return ctag;
    }

    /** Add all required access methods for a private symbol to enclosing class.
     *  将私有符号的所有必需访问方法添加到封闭类
     *  @param sym       The symbol.
     */
    void makeAccessible(Symbol sym) {
        JCClassDecl cdef = classDef(sym.owner.enclClass());
        if (cdef == null) Assert.error("class def not found: " + sym + " in " + sym.owner);
        if (sym.name == names.init) {
            // accessConstrs取出所有合成的构造方法，然后合成JCMethodDecl树节点并添加到标注语法树上
            // 当sym为构造方法时，调用accessConstructorDef()方法合成新的构造方法对应的语法树节点，然后追加到cdef.defs中
            cdef.defs = cdef.defs.prepend(
                accessConstructorDef(cdef.pos, sym, accessConstrs.get(sym)));
        } else {
            MethodSymbol[] accessors = accessSyms.get(sym);
            for (int i = 0; i < NCODES; i++) {
                if (accessors[i] != null)
                    cdef.defs = cdef.defs.prepend(
                        accessDef(cdef.pos, sym, accessors[i], i));
            }
        }
    }

    /** Construct definition of an access method.
     *  @param pos        The source code position of the definition.
     *  @param vsym       The private or protected symbol.
     *  @param accessor   The access method for the symbol.
     *  @param acode      The access code.
     */
    JCTree accessDef(int pos, Symbol vsym, MethodSymbol accessor, int acode) {
//      System.err.println("access " + vsym + " with " + accessor);//DEBUG
        currentClass = vsym.owner.enclClass();
        make.at(pos);
        JCMethodDecl md = make.MethodDef(accessor, null);

        // Find actual symbol
        Symbol sym = actualSymbols.get(vsym);
        if (sym == null) sym = vsym;

        JCExpression ref;           // The tree referencing the private symbol.
        List<JCExpression> args;    // Any additional arguments to be passed along.
        if ((sym.flags() & STATIC) != 0) {
            ref = make.Ident(sym);
            args = make.Idents(md.params);
        } else {
            ref = make.Select(make.Ident(md.params.head), sym);
            args = make.Idents(md.params.tail);
        }
        JCStatement stat;          // The statement accessing the private symbol.
        if (sym.kind == VAR) {
            // Normalize out all odd access codes by taking floor modulo 2:
            int acode1 = acode - (acode & 1);

            JCExpression expr;      // The access method's return value.
            switch (acode1) {
            case DEREFcode:
                expr = ref;
                break;
            case ASSIGNcode:
                expr = make.Assign(ref, args.head);
                break;
            case PREINCcode: case POSTINCcode: case PREDECcode: case POSTDECcode:
                expr = makeUnary(
                    ((acode1 - PREINCcode) >> 1) + JCTree.PREINC, ref);
                break;
            default:
                expr = make.Assignop(
                    treeTag(binaryAccessOperator(acode1)), ref, args.head);
                ((JCAssignOp) expr).operator = binaryAccessOperator(acode1);
            }
            stat = make.Return(expr.setType(sym.type));
        } else {
            stat = make.Call(make.App(ref, args));
        }
        md.body = make.Block(0, List.of(stat));

        // Make sure all parameters, result types and thrown exceptions
        // are accessible.
        for (List<JCVariableDecl> l = md.params; l.nonEmpty(); l = l.tail)
            l.head.vartype = access(l.head.vartype);
        md.restype = access(md.restype);
        for (List<JCExpression> l = md.thrown; l.nonEmpty(); l = l.tail)
            l.head = access(l.head);

        return md;
    }

    /** Construct definition of an access constructor.
     *
     *  @param pos        The source code position of the definition.
     *  @param constr     The private constructor.
     *  @param accessor   The access method for the constructor.
     */
    // 构造一个可访问的构造函数
    // 根据一定的规则创建一个新的JCMethodDecl语法树节点，然后标注相关的语法树节点
    JCTree accessConstructorDef(int pos, Symbol constr, MethodSymbol accessor) {
        make.at(pos);
        JCMethodDecl md = make.MethodDef(accessor,
                                      accessor.externalType(types),
                                      null);
        JCIdent callee = make.Ident(names._this);
        callee.sym = constr;
        callee.type = constr.type;
        md.body =
            make.Block(0, List.<JCStatement>of(
                make.Call(
                    make.App(
                        callee,
                        make.Idents(md.params.reverse().tail.reverse())))));
        return md;
    }

/**************************************************************************
 * Free variables proxies and this$n
 *************************************************************************/

    /** A scope containing all free variable proxies for currently translated
     *  class, as well as its this$n symbol (if needed).
     *  Proxy scopes are nested in the same way classes are.
     *  Inside a constructor, proxies and any this$n symbol are duplicated
     *  in an additional innermost scope, where they represent the constructor
     *  parameters.
     */
    Scope proxies;

    /** A scope containing all unnamed resource variables/saved
     *  exception variables for translated TWR blocks
     */
    Scope twrVars;

    /** A stack containing the this$n field of the currently translated
     *  classes (if needed) in innermost first order.
     *  Inside a constructor, proxies and any this$n symbol are duplicated
     *  in an additional innermost scope, where they represent the constructor
     *  parameters.
     */
    List<VarSymbol> outerThisStack;

    /** The name of a free variable proxy.
     */
    Name proxyName(Name name) {
        // 新合成的成员变量的名称只需要在自由变量的名称之前追加“val$”字符串即可
        return names.fromString("val" + target.syntheticNameChar() + name);
    }

    /** Proxy definitions for all free variables in given list, in reverse order.
     *  给定列表中所有自由变量的代理定义，以相反的顺序。
     *  @param pos        The source code position of the definition.
     *  @param freevars   The free variables.
     *  @param owner      The class in which the definitions go.
     */
    List<JCVariableDecl> freevarDefs(int pos, List<VarSymbol> freevars, Symbol owner) {
        long flags = FINAL | SYNTHETIC;
        if (owner.kind == TYP &&
            target.usePrivateSyntheticFields())
            flags |= PRIVATE;
        List<JCVariableDecl> defs = List.nil();
        // 循环为每个被引用的自由变量创建对应的VarSymbol对象和JCVariableDecl对象，
        // 然后将创建好的VarSymbol对象填充到proxies作用域中。
        for (List<VarSymbol> l = freevars; l.nonEmpty(); l = l.tail) {
            VarSymbol v = l.head;
            VarSymbol proxy = new VarSymbol(
                    // 创建VarSymbol对象之前，需要调用proxyName()方法创建变量名称
                flags, proxyName(v.name), v.erasure(types), owner);
            proxies.enter(proxy);
            JCVariableDecl vd = make.at(pos).VarDef(proxy, null);
            vd.vartype = access(vd.vartype);
            defs = defs.prepend(vd);
        }
        return defs;
    }

    /** The name of a this$n field
     *  @param type   The class referenced by the this$n field
     */
    // 返回this$n的Name对象
    Name outerThisName(Type type, Symbol owner) {
        Type t = type.getEnclosingType();
        int nestingLevel = 0;
        // 通过循环得到当前类型type嵌套的层次nestingLevel
        while (t.tag == CLASS) {
            t = t.getEnclosingType();
            nestingLevel++;
        }
        // 然后创建一个result对象，其中调用target.syntheticNameChar()方法返回字符'$'
        Name result = names.fromString("this" + target.syntheticNameChar() + nestingLevel);
        // 向新合成的字符串名称末尾追加一个或多个'$'字符来避免冲突
        while (owner.kind == TYP && ((ClassSymbol)owner).members().lookup(result).scope != null)
            result = names.fromString(result.toString() + target.syntheticNameChar());
        return result;
    }

    /** Definition for this$n field.
     *  @param pos        The source code position of the definition.
     *  @param owner      The class in which the definition goes.
     */
    JCVariableDecl outerThisDef(int pos, Symbol owner) {
        long flags = FINAL | SYNTHETIC;
        if (owner.kind == TYP &&
            target.usePrivateSyntheticFields())
            flags |= PRIVATE;
        Type target = types.erasure(owner.enclClass().type.getEnclosingType());
        // outerThisDef()方法在创建VarSymbol对象outerThis之前，
        // 会调用outerThisName()方法合成一个名称以“this$”字符串开头的变量，
        // 同时将outerThis追加到outerThisStack列表的头部
        VarSymbol outerThis = new VarSymbol(
            flags, outerThisName(target, owner), target, owner);
        outerThisStack = outerThisStack.prepend(outerThis);
        JCVariableDecl vd = make.at(pos).VarDef(outerThis, null);
        vd.vartype = access(vd.vartype);
        return vd;
    }

    /** Return a list of trees that load the free variables in given list,
     *  in reverse order.
     *  @param pos          The source code position to be used for the trees.
     *  @param freevars     The list of free variables.
     */
    // loadFreevars()方法得到自由变量对应的语法树
    List<JCExpression> loadFreevars(DiagnosticPosition pos, List<VarSymbol> freevars) {
        List<JCExpression> args = List.nil();
        for (List<VarSymbol> l = freevars; l.nonEmpty(); l = l.tail)
            args = args.prepend(loadFreevar(pos, l.head));
        return args;
    }
//where
        JCExpression loadFreevar(DiagnosticPosition pos, VarSymbol v) {
            return access(v, make.at(pos).Ident(v), null, false);
        }

    /** Construct a tree simulating the expression <C.this>.
     *  @param pos           The source code position to be used for the tree.
     *  @param c             The qualifier class.
     */
    JCExpression makeThis(DiagnosticPosition pos, TypeSymbol c) {
        if (currentClass == c) {
            // in this case, `this' works fine
            return make.at(pos).This(c.erasure(types));
        } else {
            // need to go via this$n
            return makeOuterThis(pos, c);
        }
    }

    /**
     * Optionally replace a try statement with the desugaring of a
     * try-with-resources statement.  The canonical desugaring of
     *
     * try ResourceSpecification
     *   Block
     *
     * is
     *
     * {
     *   final VariableModifiers_minus_final R #resource = Expression;
     *   Throwable #primaryException = null;
     *
     *   try ResourceSpecificationtail
     *     Block
     *   catch (Throwable #t) {
     *     #primaryException = t;
     *     throw #t;
     *   } finally {
     *     if (#resource != null) {
     *       if (#primaryException != null) {
     *         try {
     *           #resource.close();
     *         } catch(Throwable #suppressedException) {
     *           #primaryException.addSuppressed(#suppressedException);
     *         }
     *       } else {
     *         #resource.close();
     *       }
     *     }
     *   }
     *
     * @param tree  The try statement to inspect.
     * @return A a desugared try-with-resources tree, or the original
     * try block if there are no resources to manage.
     */
    // 解 try-with-resources 语法糖
    JCTree makeTwrTry(JCTry tree) {
        make_at(tree.pos());
        twrVars = twrVars.dup();
        // 调用makeTwrBlock()方法创建新的JCBlock语法树节点
        JCBlock twrBlock = makeTwrBlock(tree.resources, tree.body, 0);
        if (tree.catchers.isEmpty() && tree.finalizer == null)
            // 没有catch块
            result = translate(twrBlock);
        else
            // 有catch块
            result = translate(make.Try(twrBlock, tree.catchers, tree.finalizer));
        twrVars = twrVars.leave();
        return result;
    }

    // 创建新的JCBlock语法树节点
    // 在makeTwrBlock()方法中，当声明的资源变量多于一个时，会递归调用makeTwrBlock()方法进行处理，也就是通过嵌套的方式解语法糖
    // 例15-12
    private JCBlock makeTwrBlock(List<JCTree> resources, JCBlock block, int depth) {
        if (resources.isEmpty())
            return block;

        // 将资源声明当作块的一个语句添加到stats列表中
        ListBuffer<JCStatement> stats = new ListBuffer<JCStatement>();
        JCTree resource = resources.head;
        JCExpression expr = null;
        if (resource instanceof JCVariableDecl) {
            JCVariableDecl var = (JCVariableDecl) resource;
            expr = make.Ident(var.sym).setType(resource.type);
            stats.add(var);
        } else {
            Assert.check(resource instanceof JCExpression);
            VarSymbol syntheticTwrVar =
            new VarSymbol(SYNTHETIC | FINAL,
                          makeSyntheticName(names.fromString("twrVar" +
                                           depth), twrVars),
                          (resource.type.tag == TypeTags.BOT) ?
                          syms.autoCloseableType : resource.type,
                          currentMethodSym);
            twrVars.enter(syntheticTwrVar);
            JCVariableDecl syntheticTwrVarDecl =
                make.VarDef(syntheticTwrVar, (JCExpression)resource);
            expr = (JCExpression)make.Ident(syntheticTwrVar);
            stats.add(syntheticTwrVarDecl);
        }

        /* 对于实例15-10来说，合成如下的语句
        /*synthetic*\/ Throwable primaryException0$ = null;
        */
        VarSymbol primaryException =
            new VarSymbol(SYNTHETIC,
                          makeSyntheticName(names.fromString("primaryException" +
                          depth), twrVars),
                          syms.throwableType,
                          currentMethodSym);
        twrVars.enter(primaryException);

        JCVariableDecl primaryExceptionTreeDecl = make.VarDef(primaryException, makeNull());
        stats.add(primaryExceptionTreeDecl);

    /* 对于实例15-10来说，合成如下的语句
    catch (/*synthetic*\/ final Throwable t$) {
        primaryException0$ = t$;
        throw t$;
    }
    */
        VarSymbol param =
            new VarSymbol(FINAL|SYNTHETIC,
                          names.fromString("t" +
                                           target.syntheticNameChar()),
                          syms.throwableType,
                          currentMethodSym);
        JCVariableDecl paramTree = make.VarDef(param, null);
        JCStatement assign = make.Assignment(primaryException, make.Ident(param));
        JCStatement rethrowStat = make.Throw(make.Ident(param));
        JCBlock catchBlock = make.Block(0L, List.<JCStatement>of(assign, rethrowStat));
        JCCatch catchClause = make.Catch(paramTree, catchBlock);

        int oldPos = make.pos;
        make.at(TreeInfo.endPos(block));
        // 调用makeTwrFinallyClause()方法合成finally语句
        JCBlock finallyClause = makeTwrFinallyClause(primaryException, expr);
        make.at(oldPos);
        // 递归调用makeTwrBlock()方法处理resources列表
        JCTry outerTry = make.Try(makeTwrBlock(resources.tail, block, depth + 1),
                                  List.<JCCatch>of(catchClause),
                                  finallyClause);
        stats.add(outerTry);
        return make.Block(0L, stats.toList());
    }

    // 合成finally语句
    private JCBlock makeTwrFinallyClause(Symbol primaryException, JCExpression resource) {
        // 对于实例15-10来说，合成primaryException0$.addSuppressed(x2)语句
        VarSymbol catchException =
            new VarSymbol(0, make.paramName(2),
                          syms.throwableType,
                          currentMethodSym);
        JCStatement addSuppressionStatement =
            make.Exec(makeCall(make.Ident(primaryException),
                               names.addSuppressed,
                               List.<JCExpression>of(make.Ident(catchException))));

    /* 对于实例15-10来说，合成如下的语句
    try {
        br.close();
    } catch (Throwable x2) {
        primaryException0$.addSuppressed(x2);
    }
    */
        JCBlock tryBlock =
            make.Block(0L, List.<JCStatement>of(makeResourceCloseInvocation(resource)));
        JCVariableDecl catchExceptionDecl = make.VarDef(catchException, null);
        JCBlock catchBlock = make.Block(0L, List.<JCStatement>of(addSuppressionStatement));
        List<JCCatch> catchClauses = List.<JCCatch>of(make.Catch(catchExceptionDecl, catchBlock));
        JCTry tryTree = make.Try(tryBlock, catchClauses, null);

/* 对于实例15-10来说，合成如下的语句
    if (primaryException0$ != null)
        try {
          br.close();
        } catch (Throwable x2) {
           primaryException0$.addSuppressed(x2);
        } else br.close();
    */
        JCIf closeIfStatement = make.If(makeNonNullCheck(make.Ident(primaryException)),
                                        tryTree,
                                        makeResourceCloseInvocation(resource));

/* 对于实例15-10来说，合成如下的语句
    {
        if (primaryException0$ != null)
            try {
              br.close();
            } catch (Throwable x2) {
              primaryException0$.addSuppressed(x2);
            }
        else br.close();
    }
    */
        return make.Block(0L,
                          List.<JCStatement>of(make.If(makeNonNullCheck(resource),
                                                       closeIfStatement,
                                                       null)));
    }

    private JCStatement makeResourceCloseInvocation(JCExpression resource) {
        // create resource.close() method invocation
        JCExpression resourceClose = makeCall(resource,
                                              names.close,
                                              List.<JCExpression>nil());
        return make.Exec(resourceClose);
    }

    private JCExpression makeNonNullCheck(JCExpression expression) {
        return makeBinary(JCTree.NE, expression, makeNull());
    }

    /** Construct a tree that represents the outer instance
     *  <C.this>. Never pick the current `this'.
     *  @param pos           The source code position to be used for the tree.
     *  @param c             The qualifier class.
     */
    JCExpression makeOuterThis(DiagnosticPosition pos, TypeSymbol c) {
        List<VarSymbol> ots = outerThisStack;
        if (ots.isEmpty()) {
            log.error(pos, "no.encl.instance.of.type.in.scope", c);
            Assert.error();
            return makeNull();
        }
        VarSymbol ot = ots.head;
        JCExpression tree = access(make.at(pos).Ident(ot));
        TypeSymbol otc = ot.type.tsym;
        while (otc != c) {
            do {
                ots = ots.tail;
                if (ots.isEmpty()) {
                    log.error(pos,
                              "no.encl.instance.of.type.in.scope",
                              c);
                    Assert.error(); // should have been caught in Attr
                    return tree;
                }
                ot = ots.head;
            } while (ot.owner != otc);
            if (otc.owner.kind != PCK && !otc.hasOuterInstance()) {
                chk.earlyRefError(pos, c);
                Assert.error(); // should have been caught in Attr
                return makeNull();
            }
            tree = access(make.at(pos).Select(tree, ot));
            otc = ot.type.tsym;
        }
        return tree;
    }

    /** Construct a tree that represents the closest outer instance
     *  <C.this> such that the given symbol is a member of C.
     *  @param pos           The source code position to be used for the tree.
     *  @param sym           The accessed symbol.
     *  @param preciseMatch  should we accept a type that is a subtype of
     *                       sym's owner, even if it doesn't contain sym
     *                       due to hiding, overriding, or non-inheritance
     *                       due to protection?
     */
    JCExpression makeOwnerThis(DiagnosticPosition pos, Symbol sym, boolean preciseMatch) {
        Symbol c = sym.owner;
        if (preciseMatch ? sym.isMemberOf(currentClass, types)
                         : currentClass.isSubClass(sym.owner, types)) {
            // in this case, `this' works fine
            return make.at(pos).This(c.erasure(types));
        } else {
            // need to go via this$n
            return makeOwnerThisN(pos, sym, preciseMatch);
        }
    }

    /**
     * Similar to makeOwnerThis but will never pick "this".
     */
    JCExpression makeOwnerThisN(DiagnosticPosition pos, Symbol sym, boolean preciseMatch) {
        Symbol c = sym.owner;
        List<VarSymbol> ots = outerThisStack;
        if (ots.isEmpty()) {
            log.error(pos, "no.encl.instance.of.type.in.scope", c);
            Assert.error();
            return makeNull();
        }
        VarSymbol ot = ots.head;
        JCExpression tree = access(make.at(pos).Ident(ot));
        TypeSymbol otc = ot.type.tsym;
        while (!(preciseMatch ? sym.isMemberOf(otc, types) : otc.isSubClass(sym.owner, types))) {
            do {
                ots = ots.tail;
                if (ots.isEmpty()) {
                    log.error(pos,
                        "no.encl.instance.of.type.in.scope",
                        c);
                    Assert.error();
                    return tree;
                }
                ot = ots.head;
            } while (ot.owner != otc);
            tree = access(make.at(pos).Select(tree, ot));
            otc = ot.type.tsym;
        }
        return tree;
    }

    /** Return tree simulating the assignment <this.name = name>, where
     *  name is the name of a free variable.
     */
    // 返回树模拟赋值 <this.name = name>，其中 name 是自由变量的名称。
    JCStatement initField(int pos, Name name) {
        // 对于合成的名称name，从proxies中查找对应的Scope.Entry对象
        Scope.Entry e = proxies.lookup(name);
        Symbol rhs = e.sym;
        Assert.check(rhs.owner.kind == MTH);
        // 在获取lhs的值时，需要调用e.next()方法获取Scope.Entry对象的shadowed变量的值，
        // 因为两个符号的名称相同，并且合成的成员变量一定会先填充到proxies中，
        // 而合成的构造方法中的形式参数后填充到proxies中，所以后填充的同名符号在前，通过shadowed指向先添加的符号
        Symbol lhs = e.next().sym;
        Assert.check(rhs.owner.owner == lhs.owner);
        make.at(pos);
        return
            make.Exec(
                make.Assign(
                    make.Select(make.This(lhs.owner.erasure(types)), lhs),
                    make.Ident(rhs)).setType(lhs.erasure(types)));
    }

    /** Return tree simulating the assignment <this.this$n = this$n>.
     */
    // 返回树模拟赋值 <this.this = this>
    JCStatement initOuterThis(int pos) {
        // 从outerThisStack中取出构造方法合成的形式参数对应的VarSymbol对象
        VarSymbol rhs = outerThisStack.head;
        Assert.check(rhs.owner.kind == MTH);
        VarSymbol lhs = outerThisStack.tail.head;
        Assert.check(rhs.owner.owner == lhs.owner);
        make.at(pos);
        // 然后取出封闭类的实例对应的成员变量的VarSymbol对象进行赋值操作。
        return
            make.Exec(
                make.Assign(
                    make.Select(make.This(lhs.owner.erasure(types)), lhs),
                    make.Ident(rhs)).setType(lhs.erasure(types)));
    }

/**************************************************************************
 * Code for .class
 *************************************************************************/

    /** Return the symbol of a class to contain a cache of
     *  compiler-generated statics such as class$ and the
     *  $assertionsDisabled flag.  We create an anonymous nested class
     *  (unless one already exists) and return its symbol.  However,
     *  for backward compatibility in 1.4 and earlier we use the
     *  top-level class itself.
     */
    private ClassSymbol outerCacheClass() {
        ClassSymbol clazz = outermostClassDef.sym;
        if ((clazz.flags() & INTERFACE) == 0 &&
            !target.useInnerCacheClass()) return clazz;
        Scope s = clazz.members();
        for (Scope.Entry e = s.elems; e != null; e = e.sibling)
            if (e.sym.kind == TYP &&
                e.sym.name == names.empty &&
                (e.sym.flags() & INTERFACE) == 0) return (ClassSymbol) e.sym;
        return makeEmptyClass(STATIC | SYNTHETIC, clazz);
    }

    /** Return symbol for "class$" method. If there is no method definition
     *  for class$, construct one as follows:
     *
     *    class class$(String x0) {
     *      try {
     *        return Class.forName(x0);
     *      } catch (ClassNotFoundException x1) {
     *        throw new NoClassDefFoundError(x1.getMessage());
     *      }
     *    }
     */
    private MethodSymbol classDollarSym(DiagnosticPosition pos) {
        ClassSymbol outerCacheClass = outerCacheClass();
        MethodSymbol classDollarSym =
            (MethodSymbol)lookupSynthetic(classDollar,
                                          outerCacheClass.members());
        if (classDollarSym == null) {
            classDollarSym = new MethodSymbol(
                STATIC | SYNTHETIC,
                classDollar,
                new MethodType(
                    List.of(syms.stringType),
                    types.erasure(syms.classType),
                    List.<Type>nil(),
                    syms.methodClass),
                outerCacheClass);
            enterSynthetic(pos, classDollarSym, outerCacheClass.members());

            JCMethodDecl md = make.MethodDef(classDollarSym, null);
            try {
                md.body = classDollarSymBody(pos, md);
            } catch (CompletionFailure ex) {
                md.body = make.Block(0, List.<JCStatement>nil());
                chk.completionError(pos, ex);
            }
            JCClassDecl outerCacheClassDef = classDef(outerCacheClass);
            outerCacheClassDef.defs = outerCacheClassDef.defs.prepend(md);
        }
        return classDollarSym;
    }

    /** Generate code for class$(String name). */
    JCBlock classDollarSymBody(DiagnosticPosition pos, JCMethodDecl md) {
        MethodSymbol classDollarSym = md.sym;
        ClassSymbol outerCacheClass = (ClassSymbol)classDollarSym.owner;

        JCBlock returnResult;

        // in 1.4.2 and above, we use
        // Class.forName(String name, boolean init, ClassLoader loader);
        // which requires we cache the current loader in cl$
        if (target.classLiteralsNoInit()) {
            // clsym = "private static ClassLoader cl$"
            VarSymbol clsym = new VarSymbol(STATIC|SYNTHETIC,
                                            names.fromString("cl" + target.syntheticNameChar()),
                                            syms.classLoaderType,
                                            outerCacheClass);
            enterSynthetic(pos, clsym, outerCacheClass.members());

            // emit "private static ClassLoader cl$;"
            JCVariableDecl cldef = make.VarDef(clsym, null);
            JCClassDecl outerCacheClassDef = classDef(outerCacheClass);
            outerCacheClassDef.defs = outerCacheClassDef.defs.prepend(cldef);

            // newcache := "new cache$1[0]"
            JCNewArray newcache = make.
                NewArray(make.Type(outerCacheClass.type),
                         List.<JCExpression>of(make.Literal(INT, 0).setType(syms.intType)),
                         null);
            newcache.type = new ArrayType(types.erasure(outerCacheClass.type),
                                          syms.arrayClass);

            // forNameSym := java.lang.Class.forName(
            //     String s,boolean init,ClassLoader loader)
            Symbol forNameSym = lookupMethod(make_pos, names.forName,
                                             types.erasure(syms.classType),
                                             List.of(syms.stringType,
                                                     syms.booleanType,
                                                     syms.classLoaderType));
            // clvalue := "(cl$ == null) ?
            // $newcache.getClass().getComponentType().getClassLoader() : cl$"
            JCExpression clvalue =
                make.Conditional(
                    makeBinary(JCTree.EQ, make.Ident(clsym), makeNull()),
                    make.Assign(
                        make.Ident(clsym),
                        makeCall(
                            makeCall(makeCall(newcache,
                                              names.getClass,
                                              List.<JCExpression>nil()),
                                     names.getComponentType,
                                     List.<JCExpression>nil()),
                            names.getClassLoader,
                            List.<JCExpression>nil())).setType(syms.classLoaderType),
                    make.Ident(clsym)).setType(syms.classLoaderType);

            // returnResult := "{ return Class.forName(param1, false, cl$); }"
            List<JCExpression> args = List.of(make.Ident(md.params.head.sym),
                                              makeLit(syms.booleanType, 0),
                                              clvalue);
            returnResult = make.
                Block(0, List.<JCStatement>of(make.
                              Call(make. // return
                                   App(make.
                                       Ident(forNameSym), args))));
        } else {
            // forNameSym := java.lang.Class.forName(String s)
            Symbol forNameSym = lookupMethod(make_pos,
                                             names.forName,
                                             types.erasure(syms.classType),
                                             List.of(syms.stringType));
            // returnResult := "{ return Class.forName(param1); }"
            returnResult = make.
                Block(0, List.of(make.
                          Call(make. // return
                              App(make.
                                  QualIdent(forNameSym),
                                  List.<JCExpression>of(make.
                                                        Ident(md.params.
                                                              head.sym))))));
        }

        // catchParam := ClassNotFoundException e1
        VarSymbol catchParam =
            new VarSymbol(0, make.paramName(1),
                          syms.classNotFoundExceptionType,
                          classDollarSym);

        JCStatement rethrow;
        if (target.hasInitCause()) {
            // rethrow = "throw new NoClassDefFoundError().initCause(e);
            JCTree throwExpr =
                makeCall(makeNewClass(syms.noClassDefFoundErrorType,
                                      List.<JCExpression>nil()),
                         names.initCause,
                         List.<JCExpression>of(make.Ident(catchParam)));
            rethrow = make.Throw(throwExpr);
        } else {
            // getMessageSym := ClassNotFoundException.getMessage()
            Symbol getMessageSym = lookupMethod(make_pos,
                                                names.getMessage,
                                                syms.classNotFoundExceptionType,
                                                List.<Type>nil());
            // rethrow = "throw new NoClassDefFoundError(e.getMessage());"
            rethrow = make.
                Throw(makeNewClass(syms.noClassDefFoundErrorType,
                          List.<JCExpression>of(make.App(make.Select(make.Ident(catchParam),
                                                                     getMessageSym),
                                                         List.<JCExpression>nil()))));
        }

        // rethrowStmt := "( $rethrow )"
        JCBlock rethrowStmt = make.Block(0, List.of(rethrow));

        // catchBlock := "catch ($catchParam) $rethrowStmt"
        JCCatch catchBlock = make.Catch(make.VarDef(catchParam, null),
                                      rethrowStmt);

        // tryCatch := "try $returnResult $catchBlock"
        JCStatement tryCatch = make.Try(returnResult,
                                        List.of(catchBlock), null);

        return make.Block(0, List.of(tryCatch));
    }
    // where
        /** Create an attributed tree of the form left.name(). */
        private JCMethodInvocation makeCall(JCExpression left, Name name, List<JCExpression> args) {
            Assert.checkNonNull(left.type);
            Symbol funcsym = lookupMethod(make_pos, name, left.type,
                                          TreeInfo.types(args));
            return make.App(make.Select(left, funcsym), args);
        }

    /** The Name Of The variable to cache T.class values.
     *  @param sig      The signature of type T.
     */
    private Name cacheName(String sig) {
        StringBuffer buf = new StringBuffer();
        if (sig.startsWith("[")) {
            buf = buf.append("array");
            while (sig.startsWith("[")) {
                buf = buf.append(target.syntheticNameChar());
                sig = sig.substring(1);
            }
            if (sig.startsWith("L")) {
                sig = sig.substring(0, sig.length() - 1);
            }
        } else {
            buf = buf.append("class" + target.syntheticNameChar());
        }
        buf = buf.append(sig.replace('.', target.syntheticNameChar()));
        return names.fromString(buf.toString());
    }

    /** The variable symbol that caches T.class values.
     *  If none exists yet, create a definition.
     *  @param sig      The signature of type T.
     *  @param pos      The position to report diagnostics, if any.
     */
    private VarSymbol cacheSym(DiagnosticPosition pos, String sig) {
        ClassSymbol outerCacheClass = outerCacheClass();
        Name cname = cacheName(sig);
        VarSymbol cacheSym =
            (VarSymbol)lookupSynthetic(cname, outerCacheClass.members());
        if (cacheSym == null) {
            cacheSym = new VarSymbol(
                STATIC | SYNTHETIC, cname, types.erasure(syms.classType), outerCacheClass);
            enterSynthetic(pos, cacheSym, outerCacheClass.members());

            JCVariableDecl cacheDef = make.VarDef(cacheSym, null);
            JCClassDecl outerCacheClassDef = classDef(outerCacheClass);
            outerCacheClassDef.defs = outerCacheClassDef.defs.prepend(cacheDef);
        }
        return cacheSym;
    }

    /** The tree simulating a T.class expression.
     *  @param clazz      The tree identifying type T.
     */
    private JCExpression classOf(JCTree clazz) {
        return classOfType(clazz.type, clazz.pos());
    }

    private JCExpression classOfType(Type type, DiagnosticPosition pos) {
        switch (type.tag) {
        case BYTE: case SHORT: case CHAR: case INT: case LONG: case FLOAT:
        case DOUBLE: case BOOLEAN: case VOID:
            // replace with <BoxedClass>.TYPE
            ClassSymbol c = types.boxedClass(type);
            Symbol typeSym =
                rs.access(
                    rs.findIdentInType(attrEnv, c.type, names.TYPE, VAR),
                    pos, c.type, names.TYPE, true);
            if (typeSym.kind == VAR)
                ((VarSymbol)typeSym).getConstValue(); // ensure initializer is evaluated
            return make.QualIdent(typeSym);
        case CLASS: case ARRAY:
            if (target.hasClassLiterals()) {
                VarSymbol sym = new VarSymbol(
                        STATIC | PUBLIC | FINAL, names._class,
                        syms.classType, type.tsym);
                return make_at(pos).Select(make.Type(type), sym);
            }
            // replace with <cache == null ? cache = class$(tsig) : cache>
            // where
            //  - <tsig>  is the type signature of T,
            //  - <cache> is the cache variable for tsig.
            String sig =
                writer.xClassName(type).toString().replace('/', '.');
            Symbol cs = cacheSym(pos, sig);
            return make_at(pos).Conditional(
                makeBinary(JCTree.EQ, make.Ident(cs), makeNull()),
                make.Assign(
                    make.Ident(cs),
                    make.App(
                        make.Ident(classDollarSym(pos)),
                        List.<JCExpression>of(make.Literal(CLASS, sig)
                                              .setType(syms.stringType))))
                .setType(types.erasure(syms.classType)),
                make.Ident(cs)).setType(types.erasure(syms.classType));
        default:
            throw new AssertionError();
        }
    }

/**************************************************************************
 * Code for enabling/disabling assertions.
 *************************************************************************/

    // This code is not particularly robust if the user has
    // previously declared a member named '$assertionsDisabled'.
    // The same faulty idiom also appears in the translation of
    // class literals above.  We should report an error if a
    // previous declaration is not synthetic.

    private JCExpression assertFlagTest(DiagnosticPosition pos) {
        // Outermost class may be either true class or an interface.
        ClassSymbol outermostClass = outermostClassDef.sym;

        // note that this is a class, as an interface can't contain a statement.
        ClassSymbol container = currentClass;

        VarSymbol assertDisabledSym =
            (VarSymbol)lookupSynthetic(dollarAssertionsDisabled,
                                       container.members());
        if (assertDisabledSym == null) {
            assertDisabledSym =
                new VarSymbol(STATIC | FINAL | SYNTHETIC,
                              dollarAssertionsDisabled,
                              syms.booleanType,
                              container);
            enterSynthetic(pos, assertDisabledSym, container.members());
            Symbol desiredAssertionStatusSym = lookupMethod(pos,
                                                            names.desiredAssertionStatus,
                                                            types.erasure(syms.classType),
                                                            List.<Type>nil());
            JCClassDecl containerDef = classDef(container);
            make_at(containerDef.pos());
            JCExpression notStatus = makeUnary(JCTree.NOT, make.App(make.Select(
                    classOfType(types.erasure(outermostClass.type),
                                containerDef.pos()),
                    desiredAssertionStatusSym)));
            JCVariableDecl assertDisabledDef = make.VarDef(assertDisabledSym,
                                                   notStatus);
            containerDef.defs = containerDef.defs.prepend(assertDisabledDef);
        }
        make_at(pos);
        return makeUnary(JCTree.NOT, make.Ident(assertDisabledSym));
    }


/**************************************************************************
 * Building blocks for let expressions
 *************************************************************************/

    interface TreeBuilder {
        JCTree build(JCTree arg);
    }

    /** Construct an expression using the builder, with the given rval
     *  expression as an argument to the builder.  However, the rval
     *  expression must be computed only once, even if used multiple
     *  times in the result of the builder.  We do that by
     *  constructing a "let" expression that saves the rvalue into a
     *  temporary variable and then uses the temporary variable in
     *  place of the expression built by the builder.  The complete
     *  resulting expression is of the form
     *  <pre>
     *    (let <b>TYPE</b> <b>TEMP</b> = <b>RVAL</b>;
     *     in (<b>BUILDER</b>(<b>TEMP</b>)))
     *  </pre>
     *  where <code><b>TEMP</b></code> is a newly declared variable
     *  in the let expression.
     */
    JCTree abstractRval(JCTree rval, Type type, TreeBuilder builder) {
        rval = TreeInfo.skipParens(rval);
        switch (rval.getTag()) {
        case JCTree.LITERAL:
            return builder.build(rval);
        case JCTree.IDENT:
            JCIdent id = (JCIdent) rval;
            if ((id.sym.flags() & FINAL) != 0 && id.sym.owner.kind == MTH)
                return builder.build(rval);
        }
        VarSymbol var =
            new VarSymbol(FINAL|SYNTHETIC,
                          names.fromString(
                                          target.syntheticNameChar()
                                          + "" + rval.hashCode()),
                                      type,
                                      currentMethodSym);
        rval = convert(rval,type);
        JCVariableDecl def = make.VarDef(var, (JCExpression)rval); // XXX cast
        JCTree built = builder.build(make.Ident(var));
        JCTree res = make.LetExpr(def, built);
        res.type = built.type;
        return res;
    }

    // same as above, with the type of the temporary variable computed
    JCTree abstractRval(JCTree rval, TreeBuilder builder) {
        return abstractRval(rval, rval.type, builder);
    }

    // same as above, but for an expression that may be used as either
    // an rvalue or an lvalue.  This requires special handling for
    // Select expressions, where we place the left-hand-side of the
    // select in a temporary, and for Indexed expressions, where we
    // place both the indexed expression and the index value in temps.
    JCTree abstractLval(JCTree lval, final TreeBuilder builder) {
        lval = TreeInfo.skipParens(lval);
        switch (lval.getTag()) {
        case JCTree.IDENT:
            return builder.build(lval);
        case JCTree.SELECT: {
            final JCFieldAccess s = (JCFieldAccess)lval;
            JCTree selected = TreeInfo.skipParens(s.selected);
            Symbol lid = TreeInfo.symbol(s.selected);
            if (lid != null && lid.kind == TYP) return builder.build(lval);
            return abstractRval(s.selected, new TreeBuilder() {
                    public JCTree build(final JCTree selected) {
                        return builder.build(make.Select((JCExpression)selected, s.sym));
                    }
                });
        }
        case JCTree.INDEXED: {
            final JCArrayAccess i = (JCArrayAccess)lval;
            return abstractRval(i.indexed, new TreeBuilder() {
                    public JCTree build(final JCTree indexed) {
                        return abstractRval(i.index, syms.intType, new TreeBuilder() {
                                public JCTree build(final JCTree index) {
                                    JCTree newLval = make.Indexed((JCExpression)indexed,
                                                                (JCExpression)index);
                                    newLval.setType(i.type);
                                    return builder.build(newLval);
                                }
                            });
                    }
                });
        }
        case JCTree.TYPECAST: {
            return abstractLval(((JCTypeCast)lval).expr, builder);
        }
        }
        throw new AssertionError(lval);
    }

    // evaluate and discard the first expression, then evaluate the second.
    JCTree makeComma(final JCTree expr1, final JCTree expr2) {
        return abstractRval(expr1, new TreeBuilder() {
                public JCTree build(final JCTree discarded) {
                    return expr2;
                }
            });
    }

/**************************************************************************
 * Translation methods
 *************************************************************************/

    /** Visitor argument: enclosing operator node.
     */
    private JCExpression enclOp;

    /** Visitor method: Translate a single node.
     *  Attach the source position from the old tree to its replacement tree.
     */
    public <T extends JCTree> T translate(T tree) {
        if (tree == null) {
            return null;
        } else {
            make_at(tree.pos());
            T result = super.translate(tree);
            if (endPositions != null && result != tree) {
                Integer endPos = endPositions.remove(tree);
                if (endPos != null) endPositions.put(result, endPos);
            }
            return result;
        }
    }

    /** Visitor method: Translate a single node, boxing or unboxing if needed.
     */
    public <T extends JCTree> T translate(T tree, Type type) {
        return (tree == null) ? null : boxIfNeeded(translate(tree), type);
    }

    /** Visitor method: Translate tree.
     */
    public <T extends JCTree> T translate(T tree, JCExpression enclOp) {
        JCExpression prevEnclOp = this.enclOp;
        this.enclOp = enclOp;
        T res = translate(tree);
        this.enclOp = prevEnclOp;
        return res;
    }

    /** Visitor method: Translate list of trees.
     */
    public <T extends JCTree> List<T> translate(List<T> trees, JCExpression enclOp) {
        JCExpression prevEnclOp = this.enclOp;
        this.enclOp = enclOp;
        List<T> res = translate(trees);
        this.enclOp = prevEnclOp;
        return res;
    }

    /** Visitor method: Translate list of trees.
     */
    public <T extends JCTree> List<T> translate(List<T> trees, Type type) {
        if (trees == null) return null;
        for (List<T> l = trees; l.nonEmpty(); l = l.tail)
            l.head = translate(l.head, type);
        return trees;
    }

    public void visitTopLevel(JCCompilationUnit tree) {
        if (needPackageInfoClass(tree)) {
            Name name = names.package_info;
            long flags = Flags.ABSTRACT | Flags.INTERFACE;
            if (target.isPackageInfoSynthetic())
                // package-info is marked SYNTHETIC in JDK 1.6 and later releases
                flags = flags | Flags.SYNTHETIC;
            JCClassDecl packageAnnotationsClass
                = make.ClassDef(make.Modifiers(flags,
                                               tree.packageAnnotations),
                                name, List.<JCTypeParameter>nil(),
                                null, List.<JCExpression>nil(), List.<JCTree>nil());
            ClassSymbol c = tree.packge.package_info;
            c.flags_field |= flags;
            c.attributes_field = tree.packge.attributes_field;
            ClassType ctype = (ClassType) c.type;
            ctype.supertype_field = syms.objectType;
            ctype.interfaces_field = List.nil();
            packageAnnotationsClass.sym = c;

            translated.append(packageAnnotationsClass);
        }
    }
    // where
    private boolean needPackageInfoClass(JCCompilationUnit tree) {
        switch (pkginfoOpt) {
            case ALWAYS:
                return true;
            case LEGACY:
                return tree.packageAnnotations.nonEmpty();
            case NONEMPTY:
                for (Attribute.Compound a: tree.packge.attributes_field) {
                    Attribute.RetentionPolicy p = types.getRetention(a);
                    if (p != Attribute.RetentionPolicy.SOURCE)
                        return true;
                }
                return false;
        }
        throw new AssertionError();
    }

    // 为本地类合成了封闭类实例对应的成员变量和自由变量对应的成员变量
    public void visitClassDef(JCClassDecl tree) {
        ClassSymbol currentClassPrev = currentClass;
        MethodSymbol currentMethodSymPrev = currentMethodSym;
        currentClass = tree.sym;
        currentMethodSym = null;
        classdefs.put(currentClass, tree);

        // 对于内部类来说，需要保持一个对外部类实例的引用，所以在处理本地类时，
        // 会在Lower类的visitClassDef()方法中合成一个名称以“this$”字符串开头的成员变量
        // proxies用来保存自由变量对应的成员变量的符号
        proxies = proxies.dup(currentClass);
        // outerThisStack用来保存封装类的实例变量，也就是调用outerThisDef()方法合成的名称以“this$”开头的变量
        List<VarSymbol> prevOuterThisStack = outerThisStack;

        // If this is an enum definition
        if ((tree.mods.flags & ENUM) != 0 &&
            (types.supertype(currentClass.type).tsym.flags() & ENUM) == 0)
            visitEnumDef(tree);

        // If this is a nested class, define a this$n field for
        // it and add to proxies.
        JCVariableDecl otdef = null;
        // 内部类都会调用outerThisDef()方法合成一个名称以“this$”开头的变量
        // 将这个变量对应的语法树追加到tree.defs中，同时将这个变量对应的符号otdef.sym填充到currentClass.members_field中，
        // 这样这个变量就是当前类的一个成员变量了。
        if (currentClass.hasOuterInstance())
            otdef = outerThisDef(tree.pos, currentClass);

        // 如果这是一个本地类，则为其所有自由变量定义代理
        // 1.收集被本地类引用的自由变量:freevars(currentClass)
        // currentClass就是当前类对应的ClassSymbol对象，
        // 调用freevars()方法收集所有currentClass中使用到的自由变量
        // 当已经合成了外部类实例的成员后就需要合成引用的自由变量所对应的成员变量了
        List<JCVariableDecl> fvdefs = freevarDefs(
                // 当currentClass为本地类时，调用freevars()方法获取本地类中引用的自由变量，
                // 然后调用freevarDefs()方法在本地类中合成对应的成员变量
                // 将成员变量对应的语法树追加到tree.defs中并将相关符号填充到currentClass.members_field中
            tree.pos, freevars(currentClass), currentClass);

        // Recursively translate superclass, interfaces.
        tree.extending = translate(tree.extending);
        tree.implementing = translate(tree.implementing);

        // Recursively translate members, taking into account that new members
        // might be created during the translation and prepended to the member
        // list `tree.defs'.
        List<JCTree> seen = List.nil();
        while (tree.defs != seen) {
            List<JCTree> unseen = tree.defs;
            for (List<JCTree> l = unseen; l.nonEmpty() && l != seen; l = l.tail) {
                JCTree outermostMemberDefPrev = outermostMemberDef;
                if (outermostMemberDefPrev == null) outermostMemberDef = l.head;
                l.head = translate(l.head);
                outermostMemberDef = outermostMemberDefPrev;
            }
            seen = unseen;
        }

        // Convert a protected modifier to public, mask static modifier.
        if ((tree.mods.flags & PROTECTED) != 0) tree.mods.flags |= PUBLIC;
        tree.mods.flags &= ClassFlags;

        // Convert name to flat representation, replacing '.' by '$'.
        tree.name = Convert.shortName(currentClass.flatName());

        // Add this$n and free variables proxy definitions to class.
        for (List<JCVariableDecl> l = fvdefs; l.nonEmpty(); l = l.tail) {
            tree.defs = tree.defs.prepend(l.head);
            enterSynthetic(tree.pos(), l.head.sym, currentClass.members());
        }
        if (currentClass.hasOuterInstance()) {
            tree.defs = tree.defs.prepend(otdef);
            enterSynthetic(tree.pos(), otdef.sym, currentClass.members());
        }

        proxies = proxies.leave();
        outerThisStack = prevOuterThisStack;

        // Append translated tree to `translated' queue.
        translated.append(tree);

        currentClass = currentClassPrev;
        currentMethodSym = currentMethodSymPrev;

        // Return empty block {} as a placeholder for an inner class.
        result = make_at(tree.pos()).Block(0, List.<JCStatement>nil());
    }

    /** Translate an enum class. */
    // 解枚举语法糖
    // 例15-13
    // Fruit枚举类经过解语法糖后就变成了一个普通的类，这个类继承了Enum<Fruit>类，Enum是所有枚举类的父类，
    // 不过代码编写者不能为枚举类明确指定父类，包括Enum
    // 枚举类中的常量通过new关键字初始化，为构造方法传递的$enum$name与常量名称一致，
    // 而$enum$ordinal是从0开始，按常量声明的先后顺序依次加1。
    // Javac会为每个枚举类生成values()与valueOf()方法，两个方法通过静态数组$VALUES实现相应的功能。
    private void visitEnumDef(JCClassDecl tree) {
        make_at(tree.pos());

        // add the supertype, if needed
        if (tree.extending == null)
            tree.extending = make.Type(types.supertype(tree.type));

        // classOfType adds a cache field to tree.defs unless
        // target.hasClassLiterals().
        JCExpression e_class = classOfType(tree.sym.type, tree.pos()).
            setType(types.erasure(syms.classType));

        // process each enumeration constant, adding implicit constructor parameters
        int nextOrdinal = 0;
        ListBuffer<JCExpression> values = new ListBuffer<JCExpression>();
        ListBuffer<JCTree> enumDefs = new ListBuffer<JCTree>();
        ListBuffer<JCTree> otherDefs = new ListBuffer<JCTree>();
        for (List<JCTree> defs = tree.defs;
             defs.nonEmpty();
             defs=defs.tail) {
            if (defs.head.getTag() == JCTree.VARDEF && (((JCVariableDecl) defs.head).mods.flags & ENUM) != 0) {
                JCVariableDecl var = (JCVariableDecl)defs.head;
                visitEnumConstantDef(var, nextOrdinal++);
                values.append(make.QualIdent(var.sym));
                enumDefs.append(var);
            } else {
                otherDefs.append(defs.head);
            }
        }

        // private static final T[] #VALUES = { a, b, c };
        Name valuesName = names.fromString(target.syntheticNameChar() + "VALUES");
        while (tree.sym.members().lookup(valuesName).scope != null) // avoid name clash
            valuesName = names.fromString(valuesName + "" + target.syntheticNameChar());
        Type arrayType = new ArrayType(types.erasure(tree.type), syms.arrayClass);
        VarSymbol valuesVar = new VarSymbol(PRIVATE|FINAL|STATIC|SYNTHETIC,
                                            valuesName,
                                            arrayType,
                                            tree.type.tsym);
        JCNewArray newArray = make.NewArray(make.Type(types.erasure(tree.type)),
                                          List.<JCExpression>nil(),
                                          values.toList());
        newArray.type = arrayType;
        enumDefs.append(make.VarDef(valuesVar, newArray));
        tree.sym.members().enter(valuesVar);

        Symbol valuesSym = lookupMethod(tree.pos(), names.values,
                                        tree.type, List.<Type>nil());
        List<JCStatement> valuesBody;
        if (useClone()) {
            // return (T[]) $VALUES.clone();
            JCTypeCast valuesResult =
                make.TypeCast(valuesSym.type.getReturnType(),
                              make.App(make.Select(make.Ident(valuesVar),
                                                   syms.arrayCloneMethod)));
            valuesBody = List.<JCStatement>of(make.Return(valuesResult));
        } else {
            // template: T[] $result = new T[$values.length];
            Name resultName = names.fromString(target.syntheticNameChar() + "result");
            while (tree.sym.members().lookup(resultName).scope != null) // avoid name clash
                resultName = names.fromString(resultName + "" + target.syntheticNameChar());
            VarSymbol resultVar = new VarSymbol(FINAL|SYNTHETIC,
                                                resultName,
                                                arrayType,
                                                valuesSym);
            JCNewArray resultArray = make.NewArray(make.Type(types.erasure(tree.type)),
                                  List.of(make.Select(make.Ident(valuesVar), syms.lengthVar)),
                                  null);
            resultArray.type = arrayType;
            JCVariableDecl decl = make.VarDef(resultVar, resultArray);

            // template: System.arraycopy($VALUES, 0, $result, 0, $VALUES.length);
            if (systemArraycopyMethod == null) {
                systemArraycopyMethod =
                    new MethodSymbol(PUBLIC | STATIC,
                                     names.fromString("arraycopy"),
                                     new MethodType(List.<Type>of(syms.objectType,
                                                            syms.intType,
                                                            syms.objectType,
                                                            syms.intType,
                                                            syms.intType),
                                                    syms.voidType,
                                                    List.<Type>nil(),
                                                    syms.methodClass),
                                     syms.systemType.tsym);
            }
            JCStatement copy =
                make.Exec(make.App(make.Select(make.Ident(syms.systemType.tsym),
                                               systemArraycopyMethod),
                          List.of(make.Ident(valuesVar), make.Literal(0),
                                  make.Ident(resultVar), make.Literal(0),
                                  make.Select(make.Ident(valuesVar), syms.lengthVar))));

            // template: return $result;
            JCStatement ret = make.Return(make.Ident(resultVar));
            valuesBody = List.<JCStatement>of(decl, copy, ret);
        }

        JCMethodDecl valuesDef =
             make.MethodDef((MethodSymbol)valuesSym, make.Block(0, valuesBody));

        enumDefs.append(valuesDef);

        if (debugLower)
            System.err.println(tree.sym + ".valuesDef = " + valuesDef);

        /** The template for the following code is:
         *
         *     public static E valueOf(String name) {
         *         return (E)Enum.valueOf(E.class, name);
         *     }
         *
         *  where E is tree.sym
         */
        MethodSymbol valueOfSym = lookupMethod(tree.pos(),
                         names.valueOf,
                         tree.sym.type,
                         List.of(syms.stringType));
        Assert.check((valueOfSym.flags() & STATIC) != 0);
        VarSymbol nameArgSym = valueOfSym.params.head;
        JCIdent nameVal = make.Ident(nameArgSym);
        JCStatement enum_ValueOf =
            make.Return(make.TypeCast(tree.sym.type,
                                      makeCall(make.Ident(syms.enumSym),
                                               names.valueOf,
                                               List.of(e_class, nameVal))));
        JCMethodDecl valueOf = make.MethodDef(valueOfSym,
                                           make.Block(0, List.of(enum_ValueOf)));
        nameVal.sym = valueOf.params.head.sym;
        if (debugLower)
            System.err.println(tree.sym + ".valueOf = " + valueOf);
        enumDefs.append(valueOf);

        enumDefs.appendList(otherDefs.toList());
        tree.defs = enumDefs.toList();

        // Add the necessary members for the EnumCompatibleMode
        if (target.compilerBootstrap(tree.sym)) {
            addEnumCompatibleMembers(tree);
        }
    }
        // where
        private MethodSymbol systemArraycopyMethod;
        private boolean useClone() {
            try {
                Scope.Entry e = syms.objectType.tsym.members().lookup(names.clone);
                return (e.sym != null);
            }
            catch (CompletionFailure e) {
                return false;
            }
        }

    /** Translate an enumeration constant and its initializer. */
    private void visitEnumConstantDef(JCVariableDecl var, int ordinal) {
        JCNewClass varDef = (JCNewClass)var.init;
        varDef.args = varDef.args.
            prepend(makeLit(syms.intType, ordinal)).
            prepend(makeLit(syms.stringType, var.name.toString()));
    }

    public void visitMethodDef(JCMethodDecl tree) {
        if (tree.name == names.init && (currentClass.flags_field&ENUM) != 0) {
            // Add "String $enum$name, int $enum$ordinal" to the beginning of the
            // argument list for each constructor of an enum.
            JCVariableDecl nameParam = make_at(tree.pos()).
                Param(names.fromString(target.syntheticNameChar() +
                                       "enum" + target.syntheticNameChar() + "name"),
                      syms.stringType, tree.sym);
            nameParam.mods.flags |= SYNTHETIC; nameParam.sym.flags_field |= SYNTHETIC;

            JCVariableDecl ordParam = make.
                Param(names.fromString(target.syntheticNameChar() +
                                       "enum" + target.syntheticNameChar() +
                                       "ordinal"),
                      syms.intType, tree.sym);
            ordParam.mods.flags |= SYNTHETIC; ordParam.sym.flags_field |= SYNTHETIC;

            tree.params = tree.params.prepend(ordParam).prepend(nameParam);

            MethodSymbol m = tree.sym;
            Type olderasure = m.erasure(types);
            m.erasure_field = new MethodType(
                olderasure.getParameterTypes().prepend(syms.intType).prepend(syms.stringType),
                olderasure.getReturnType(),
                olderasure.getThrownTypes(),
                syms.methodClass);

            if (target.compilerBootstrap(m.owner)) {
                // Initialize synthetic name field
                Symbol nameVarSym = lookupSynthetic(names.fromString("$name"),
                                                    tree.sym.owner.members());
                JCIdent nameIdent = make.Ident(nameParam.sym);
                JCIdent id1 = make.Ident(nameVarSym);
                JCAssign newAssign = make.Assign(id1, nameIdent);
                newAssign.type = id1.type;
                JCExpressionStatement nameAssign = make.Exec(newAssign);
                nameAssign.type = id1.type;
                tree.body.stats = tree.body.stats.prepend(nameAssign);

                // Initialize synthetic ordinal field
                Symbol ordinalVarSym = lookupSynthetic(names.fromString("$ordinal"),
                                                       tree.sym.owner.members());
                JCIdent ordIdent = make.Ident(ordParam.sym);
                id1 = make.Ident(ordinalVarSym);
                newAssign = make.Assign(id1, ordIdent);
                newAssign.type = id1.type;
                JCExpressionStatement ordinalAssign = make.Exec(newAssign);
                ordinalAssign.type = id1.type;
                tree.body.stats = tree.body.stats.prepend(ordinalAssign);
            }
        }

        JCMethodDecl prevMethodDef = currentMethodDef;
        MethodSymbol prevMethodSym = currentMethodSym;
        try {
            currentMethodDef = tree;
            currentMethodSym = tree.sym;
            visitMethodDefInternal(tree);
        } finally {
            currentMethodDef = prevMethodDef;
            currentMethodSym = prevMethodSym;
        }
    }
    // 更新原有构造方法，在更新后的构造方法中初始化这些成员变量
    private void visitMethodDefInternal(JCMethodDecl tree) {
        // 如果tree为内部类或本地类的构造方法时，需要对构造方法进行更新
        if (tree.name == names.init &&
            (currentClass.isInner() ||
             (currentClass.owner.kind & (VAR | MTH)) != 0)) {
            // 内部类的构造方法
            MethodSymbol m = tree.sym;

            // 从proxies中查找及保存成员变量和构造方法的形式参数
            proxies = proxies.dup(m);
            // 从outerThisStack中查找及保存封闭类的实例变量和构造方法的形式参数
            List<VarSymbol> prevOuterThisStack = outerThisStack;
            List<VarSymbol> fvs = freevars(currentClass);
            JCVariableDecl otdef = null;
            if (currentClass.hasOuterInstance())
                otdef = outerThisDef(tree.pos, m);
            List<JCVariableDecl> fvdefs = freevarDefs(tree.pos, fvs, m);

            // Recursively translate result type, parameters and thrown list.
            tree.restype = translate(tree.restype);
            tree.params = translateVarDefs(tree.params);
            tree.thrown = translate(tree.thrown);

            // when compiling stubs, don't process body
            if (tree.body == null) {
                result = tree;
                return;
            }

            // 向构造方法的形式参数的末尾追加能初始化自由变量对应的成员变量的参数
            // 将fvdefs列表中的值追加到构造方法形式参数列表的末尾。
            tree.params = tree.params.appendList(fvdefs);
            if (currentClass.hasOuterInstance())
                // 将otdef追加到构造方法形式参数列表的头部
                tree.params = tree.params.prepend(otdef);

            // If this is an initial constructor, i.e., it does not start with
            // this(...), insert initializers for this$n and proxies
            // before (pre-1.4, after) the call to superclass constructor.
            JCStatement selfCall = translate(tree.body.stats.head);

            List<JCStatement> added = List.nil();
            if (fvs.nonEmpty()) {
                List<Type> addedargtypes = List.nil();
                // 在构造方法中初始化合成的成员变量
                for (List<VarSymbol> l = fvs; l.nonEmpty(); l = l.tail) {
                    // 调用TreeInfo.isInitialConstructor()方法判断当前构造方法的第一个语句不为this(...)形式
                    // 如果为this(...)形式，不会添加变量初始化语句。
                    // 原因也很好理解，这个构造方法调用了另外的构造方法，如果在每个构造方法中进行初始化，那么变量就会被初始化多次
                    if (TreeInfo.isInitialConstructor(tree))
                        // 对于自由变量对应的成员变量的初始化，调用proxyName()方法得到变量名称后调用initField()方法，
                        // 这个方法会返回初始化语句对应的语法树，将这些语法树追加到added列表中
                        added = added.prepend(initField(tree.body.pos, proxyName(l.head.name)));
                    addedargtypes = addedargtypes.prepend(l.head.erasure(types));
                }
                Type olderasure = m.erasure(types);
                m.erasure_field = new MethodType(
                    olderasure.getParameterTypes().appendList(addedargtypes),
                    olderasure.getReturnType(),
                    olderasure.getThrownTypes(),
                    syms.methodClass);
            }
            // 向构造方法的形式参数的头部追加能初始化封闭类的实例变量的参数
            if (currentClass.hasOuterInstance() &&
                TreeInfo.isInitialConstructor(tree))
            {
                // 调用initOuterThis()方法创建封装类的实例变量的初始化语句所对应的语法树
                added = added.prepend(initOuterThis(tree.body.pos));
            }

            // 离开方法时，保存的构造方法的形式参数失效
            proxies = proxies.leave();

            // recursively translate following local statements and
            // combine with this- or super-call
            List<JCStatement> stats = translate(tree.body.stats.tail);
            if (target.initializeFieldsBeforeSuper())
                tree.body.stats = stats.prepend(selfCall).prependList(added);
            else
                tree.body.stats = stats.prependList(added).prepend(selfCall);
            // 离开方法时，保存的构造方法的形式参数失效
            outerThisStack = prevOuterThisStack;
        } else {
            super.visitMethodDef(tree);
        }
        result = tree;
    }

    public void visitTypeCast(JCTypeCast tree) {
        tree.clazz = translate(tree.clazz);
        if (tree.type.isPrimitive() != tree.expr.type.isPrimitive())
            tree.expr = translate(tree.expr, tree.type);
        else
            tree.expr = translate(tree.expr);
        result = tree;
    }

    // 对构造方法解语法糖
    public void visitNewClass(JCNewClass tree) {
        ClassSymbol c = (ClassSymbol)tree.constructor.owner;

        // Box arguments, if necessary
        boolean isEnum = (tree.constructor.owner.flags() & ENUM) != 0;
        List<Type> argTypes = tree.constructor.type.getParameterTypes();
        if (isEnum)
            argTypes = argTypes.prepend(syms.intType).prepend(syms.stringType);
        tree.args = boxArgs(argTypes, tree.args, tree.varargsElement);
        tree.varargsElement = null;

        // 如果c.owner为本地类，为对象创建表达式追加参数，也就是自由变量
        if ((c.owner.kind & (VAR | MTH)) != 0) {
            tree.args = tree.args.appendList(loadFreevars(tree.pos(), freevars(c)));
        }

        // 如果使用访问构造函数，则附加 null 作为最后一个参数
        // 例15-15
        // 首先调用accessConstructor()方法获取constructor，
        // 这个对象对于实例15-15来说就是Outer类中的合成构造方法。
        // 如果constructor与原来的tree.constructor不一样，说明这是一个合成的构造方法，
        // 需要将之前的调用私有构造方法改为调用合成的构造方法，在调用时向实际参数列表末尾追加一个null参数，
        // 然后将tree.constructor更新为constructor
        Symbol constructor = accessConstructor(tree.pos(), tree.constructor);
        if (constructor != tree.constructor) {
            tree.args = tree.args.append(makeNull());
            tree.constructor = constructor;
        }

        // 如果cs有封闭类实例，为对象创建表达式追加参数，也就是封闭类的实例
        if (c.hasOuterInstance()) {
            JCExpression thisArg;
            if (tree.encl != null) {
                thisArg = attr.makeNullCheck(translate(tree.encl));
                thisArg.type = tree.encl.type;
            } else if ((c.owner.kind & (MTH | VAR)) != 0) {
                // 调用makeThis()方法合成外部类的实例对应的语法树
                thisArg = makeThis(tree.pos(), c.type.getEnclosingType().tsym);
            } else {
                // nested class
                thisArg = makeOwnerThis(tree.pos(), c, false);
            }
            tree.args = tree.args.prepend(thisArg);
        }
        tree.encl = null;

        // If we have an anonymous class, create its flat version, rather
        // than the class or interface following new.
        if (tree.def != null) {
            translate(tree.def);
            tree.clazz = access(make_at(tree.clazz.pos()).Ident(tree.def.sym));
            tree.def = null;
        } else {
            tree.clazz = access(c, tree.clazz, enclOp, false);
        }
        result = tree;
    }

    // Simplify conditionals with known constant controlling expressions.
    // This allows us to avoid generating supporting declarations for
    // the dead code, which will not be eliminated during code generation.
    // Note that Flow.isFalse and Flow.isTrue only return true
    // for constant expressions in the sense of JLS 15.27, which
    // are guaranteed to have no side-effects.  More aggressive
    // constant propagation would require that we take care to
    // preserve possible side-effects in the condition expression.

    /** Visitor method for conditional expressions.
     */
    public void visitConditional(JCConditional tree) {
        JCTree cond = tree.cond = translate(tree.cond, syms.booleanType);
        if (cond.type.isTrue()) {
            result = convert(translate(tree.truepart, tree.type), tree.type);
        } else if (cond.type.isFalse()) {
            result = convert(translate(tree.falsepart, tree.type), tree.type);
        } else {
            // Condition is not a compile-time constant.
            tree.truepart = translate(tree.truepart, tree.type);
            tree.falsepart = translate(tree.falsepart, tree.type);
            result = tree;
        }
    }
//where
        private JCTree convert(JCTree tree, Type pt) {
            if (tree.type == pt || tree.type.tag == TypeTags.BOT)
                return tree;
            JCTree result = make_at(tree.pos()).TypeCast(make.Type(pt), (JCExpression)tree);
            result.type = (tree.type.constValue() != null) ? cfolder.coerce(tree.type, pt)
                                                           : pt;
            return result;
        }

    /** Visitor method for if statements.
     */
    // 解条件编译语法糖
    /*
    public void test() {
        if (true) {
            System.out.println("true");
        } else {
            System.out.println("false");
        }
    }
    解语法糖后为：
    {
        System.out.println("true");
    }
     */
    public void visitIf(JCIf tree) {
        JCTree cond = tree.cond = translate(tree.cond, syms.booleanType);
        if (cond.type.isTrue()) {
            // 当if语句的条件判断表达式的结果为常量值true时
            // 调用translate()方法处理tree.thenpart
            result = translate(tree.thenpart);
        } else if (cond.type.isFalse()) {
            // 当if语句的条件判断表达式的结果为常量值false时
            if (tree.elsepart != null) {
                // 如果elsepart不为空，调用translate()方法处理tree.elsepart
                result = translate(tree.elsepart);
            } else {
                // tree.elsepart为空，则跳过
                result = make.Skip();
            }
        } else {
            // Condition is not a compile-time constant.
            tree.thenpart = translate(tree.thenpart);
            tree.elsepart = translate(tree.elsepart);
            result = tree;
        }
    }

    /** Visitor method for assert statements. Translate them away.
     */
    public void visitAssert(JCAssert tree) {
        DiagnosticPosition detailPos = (tree.detail == null) ? tree.pos() : tree.detail.pos();
        tree.cond = translate(tree.cond, syms.booleanType);
        if (!tree.cond.type.isTrue()) {
            JCExpression cond = assertFlagTest(tree.pos());
            List<JCExpression> exnArgs = (tree.detail == null) ?
                List.<JCExpression>nil() : List.of(translate(tree.detail));
            if (!tree.cond.type.isFalse()) {
                cond = makeBinary
                    (JCTree.AND,
                     cond,
                     makeUnary(JCTree.NOT, tree.cond));
            }
            result =
                make.If(cond,
                        make_at(detailPos).
                           Throw(makeNewClass(syms.assertionErrorType, exnArgs)),
                        null);
        } else {
            result = make.Skip();
        }
    }

    public void visitApply(JCMethodInvocation tree) {
        Symbol meth = TreeInfo.symbol(tree.meth);
        List<Type> argtypes = meth.type.getParameterTypes();
        if (allowEnums &&
            meth.name==names.init &&
            meth.owner == syms.enumSym)
            argtypes = argtypes.tail.tail;
        tree.args = boxArgs(argtypes, tree.args, tree.varargsElement);
        tree.varargsElement = null;
        Name methName = TreeInfo.name(tree.meth);
        if (meth.name==names.init) {
            // We are seeing a this(...) or super(...) constructor call.
            // If an access constructor is used, append null as a last argument.
            Symbol constructor = accessConstructor(tree.pos(), meth);
            if (constructor != meth) {
                tree.args = tree.args.append(makeNull());
                TreeInfo.setSymbol(tree.meth, constructor);
            }

            // If we are calling a constructor of a local class, add
            // free variables after explicit constructor arguments.
            ClassSymbol c = (ClassSymbol)constructor.owner;
            if ((c.owner.kind & (VAR | MTH)) != 0) {
                tree.args = tree.args.appendList(loadFreevars(tree.pos(), freevars(c)));
            }

            // If we are calling a constructor of an enum class, pass
            // along the name and ordinal arguments
            if ((c.flags_field&ENUM) != 0 || c.getQualifiedName() == names.java_lang_Enum) {
                List<JCVariableDecl> params = currentMethodDef.params;
                if (currentMethodSym.owner.hasOuterInstance())
                    params = params.tail; // drop this$n
                tree.args = tree.args
                    .prepend(make_at(tree.pos()).Ident(params.tail.head.sym)) // ordinal
                    .prepend(make.Ident(params.head.sym)); // name
            }

            // If we are calling a constructor of a class with an outer
            // instance, and the call
            // is qualified, pass qualifier as first argument in front of
            // the explicit constructor arguments. If the call
            // is not qualified, pass the correct outer instance as
            // first argument.
            if (c.hasOuterInstance()) {
                JCExpression thisArg;
                if (tree.meth.getTag() == JCTree.SELECT) {
                    thisArg = attr.
                        makeNullCheck(translate(((JCFieldAccess) tree.meth).selected));
                    tree.meth = make.Ident(constructor);
                    ((JCIdent) tree.meth).name = methName;
                } else if ((c.owner.kind & (MTH | VAR)) != 0 || methName == names._this){
                    // local class or this() call
                    thisArg = makeThis(tree.meth.pos(), c.type.getEnclosingType().tsym);
                } else {
                    // super() call of nested class - never pick 'this'
                    thisArg = makeOwnerThisN(tree.meth.pos(), c, false);
                }
                tree.args = tree.args.prepend(thisArg);
            }
        } else {
            // We are seeing a normal method invocation; translate this as usual.
            tree.meth = translate(tree.meth);

            // If the translated method itself is an Apply tree, we are
            // seeing an access method invocation. In this case, append
            // the method arguments to the arguments of the access method.
            if (tree.meth.getTag() == JCTree.APPLY) {
                JCMethodInvocation app = (JCMethodInvocation)tree.meth;
                app.args = tree.args.prependList(app.args);
                result = app;
                return;
            }
        }
        result = tree;
    }

    // 解变长参数语法糖
    List<JCExpression> boxArgs(List<Type> parameters, List<JCExpression> _args, Type varargsElement) {
        // 如果调用的方法最后一个参数为变长参数，则varargsElement不为空
        List<JCExpression> args = _args;
        if (parameters.isEmpty()) return args;
        boolean anyChanges = false;
        ListBuffer<JCExpression> result = new ListBuffer<JCExpression>();
        while (parameters.tail.nonEmpty()) {
            JCExpression arg = translate(args.head, parameters.head);
            anyChanges |= (arg != args.head);
            result.append(arg);
            args = args.tail;
            parameters = parameters.tail;
        }
        Type parameter = parameters.head;
        if (varargsElement != null) {
            anyChanges = true;
            ListBuffer<JCExpression> elems = new ListBuffer<JCExpression>();
            while (args.nonEmpty()) {
                JCExpression arg = translate(args.head, varargsElement);
                elems.append(arg);
                args = args.tail;
            }
            JCNewArray boxedArgs = make.NewArray(make.Type(varargsElement),
                                               List.<JCExpression>nil(),
                                               elems.toList());
            boxedArgs.type = new ArrayType(varargsElement, syms.arrayClass);
            result.append(boxedArgs);
        } else {
            if (args.length() != 1) throw new AssertionError(args);
            JCExpression arg = translate(args.head, parameter);
            anyChanges |= (arg != args.head);
            result.append(arg);
            if (!anyChanges) return _args;
        }
        return result.toList();
    }

    /** Expand a boxing or unboxing conversion if needed. */
    @SuppressWarnings("unchecked") // XXX unchecked
    // boxIfNeeded()方法中完成类型拆箱转换与类型装箱转换
    // tree.type为原类型，参数type看为目标类型
    <T extends JCTree> T boxIfNeeded(T tree, Type type) {
        boolean havePrimitive = tree.type.isPrimitive();
        if (havePrimitive == type.isPrimitive())
            // 当两个都是基本类型或引用类型时不需要进行任何操作
            return tree;
        if (havePrimitive) {
            // 当原类型为基本类型而目标类型为引用类型时，需要对原类型进行类型装箱转换
            Type unboxedTarget = types.unboxedType(type);
            // 当unboxedTarget等于NONE时，也就是调用types.unboxedType()方法找不到type对应的基本类型
            if (unboxedTarget.tag != NONE) {
                if (!types.isSubtype(tree.type, unboxedTarget)) //e.g. Character c = 89;
                    // Character x = 89;  -> Character c = Character.valueOf(89);
                    // Object x = 1; -> Object x = Integer.valueOf(1);
                    tree.type = unboxedTarget.constType(tree.type.constValue());
                return (T)boxPrimitive((JCExpression)tree, type);
            } else {
                tree = (T)boxPrimitive((JCExpression)tree);
            }
        } else {
            // 当原类型为引用类型而目标类型为基本类型时，需要对原类型进行类型拆箱转换
            tree = (T)unbox((JCExpression)tree, type);
        }
        return tree;
    }

    /** Box up a single primitive expression. */
    JCExpression boxPrimitive(JCExpression tree) {
        // 根据types.boxedClass(tree.type).type反推tree的装箱类型
        return boxPrimitive(tree, types.boxedClass(tree.type).type);
    }

    /** Box up a single primitive expression. */
    // 基本类型装箱 返回表达式
    JCExpression boxPrimitive(JCExpression tree, Type box) {
        make_at(tree.pos());
        if (target.boxWithConstructors()) {
            Symbol ctor = lookupConstructor(tree.pos(),
                                            box,
                                            List.<Type>nil()
                                            .prepend(tree.type));
            return make.Create(ctor, List.of(tree));
        } else {
            // 调用loopkupMethod()方法在box类型中查找一个名称为valueOf的方法
            Symbol valueOfSym = lookupMethod(tree.pos(),
                                             names.valueOf,
                                             box,
                                             List.<Type>nil()
                                             .prepend(tree.type));
            return make.App(make.QualIdent(valueOfSym), List.of(tree));
        }
    }

    /** Unbox an object to a primitive value. */
    // 装箱类型拆箱
    JCExpression unbox(JCExpression tree, Type primitive) {
        Type unboxedType = types.unboxedType(tree.type);
        if (unboxedType.tag == NONE) {
            unboxedType = primitive;
            if (!unboxedType.isPrimitive())
                throw new AssertionError(unboxedType);
            make_at(tree.pos());
            tree = make.TypeCast(types.boxedClass(unboxedType).type, tree);
        } else {
            // There must be a conversion from unboxedType to primitive.
            if (!types.isSubtype(unboxedType, primitive))
                throw new AssertionError(tree);
        }
        make_at(tree.pos());
        // unboxedType为int类型，则在tree.type中查找名称为intValue的方法，
        // 方法名由基本类型的名称追加Value字符串组成
        Symbol valueSym = lookupMethod(tree.pos(),
                                       unboxedType.tsym.name.append(names.Value), // x.intValue()
                                       tree.type,
                                       List.<Type>nil());
        return make.App(make.Select(tree, valueSym));
    }

    /** Visitor method for parenthesized expressions.
     *  If the subexpression has changed, omit the parens.
     */
    public void visitParens(JCParens tree) {
        JCTree expr = translate(tree.expr);
        result = ((expr == tree.expr) ? tree : expr);
    }

    public void visitIndexed(JCArrayAccess tree) {
        tree.indexed = translate(tree.indexed);
        tree.index = translate(tree.index, syms.intType);
        result = tree;
    }

    public void visitAssign(JCAssign tree) {
        tree.lhs = translate(tree.lhs, tree);
        tree.rhs = translate(tree.rhs, tree.lhs.type);

        // If translated left hand side is an Apply, we are
        // seeing an access method invocation. In this case, append
        // right hand side as last argument of the access method.
        if (tree.lhs.getTag() == JCTree.APPLY) {
            JCMethodInvocation app = (JCMethodInvocation)tree.lhs;
            app.args = List.of(tree.rhs).prependList(app.args);
            result = app;
        } else {
            result = tree;
        }
    }

    public void visitAssignop(final JCAssignOp tree) {
        if (!tree.lhs.type.isPrimitive() &&
            tree.operator.type.getReturnType().isPrimitive()) {
            // boxing required; need to rewrite as x = (unbox typeof x)(x op y);
            // or if x == (typeof x)z then z = (unbox typeof x)((typeof x)z op y)
            // (but without recomputing x)
            JCTree newTree = abstractLval(tree.lhs, new TreeBuilder() {
                    public JCTree build(final JCTree lhs) {
                        int newTag = tree.getTag() - JCTree.ASGOffset;
                        // Erasure (TransTypes) can change the type of
                        // tree.lhs.  However, we can still get the
                        // unerased type of tree.lhs as it is stored
                        // in tree.type in Attr.
                        Symbol newOperator = rs.resolveBinaryOperator(tree.pos(),
                                                                      newTag,
                                                                      attrEnv,
                                                                      tree.type,
                                                                      tree.rhs.type);
                        JCExpression expr = (JCExpression)lhs;
                        if (expr.type != tree.type)
                            expr = make.TypeCast(tree.type, expr);
                        JCBinary opResult = make.Binary(newTag, expr, tree.rhs);
                        opResult.operator = newOperator;
                        opResult.type = newOperator.type.getReturnType();
                        JCTypeCast newRhs = make.TypeCast(types.unboxedType(tree.type),
                                                          opResult);
                        return make.Assign((JCExpression)lhs, newRhs).setType(tree.type);
                    }
                });
            result = translate(newTree);
            return;
        }
        tree.lhs = translate(tree.lhs, tree);
        tree.rhs = translate(tree.rhs, tree.operator.type.getParameterTypes().tail.head);

        // If translated left hand side is an Apply, we are
        // seeing an access method invocation. In this case, append
        // right hand side as last argument of the access method.
        if (tree.lhs.getTag() == JCTree.APPLY) {
            JCMethodInvocation app = (JCMethodInvocation)tree.lhs;
            // if operation is a += on strings,
            // make sure to convert argument to string
            JCExpression rhs = (((OperatorSymbol)tree.operator).opcode == string_add)
              ? makeString(tree.rhs)
              : tree.rhs;
            app.args = List.of(rhs).prependList(app.args);
            result = app;
        } else {
            result = tree;
        }
    }

    /** Lower a tree of the form e++ or e-- where e is an object type */
    JCTree lowerBoxedPostop(final JCUnary tree) {
        // translate to tmp1=lval(e); tmp2=tmp1; tmp1 OP 1; tmp2
        // or
        // translate to tmp1=lval(e); tmp2=tmp1; (typeof tree)tmp1 OP 1; tmp2
        // where OP is += or -=
        final boolean cast = TreeInfo.skipParens(tree.arg).getTag() == JCTree.TYPECAST;
        return abstractLval(tree.arg, new TreeBuilder() {
                public JCTree build(final JCTree tmp1) {
                    return abstractRval(tmp1, tree.arg.type, new TreeBuilder() {
                            public JCTree build(final JCTree tmp2) {
                                int opcode = (tree.getTag() == JCTree.POSTINC)
                                    ? JCTree.PLUS_ASG : JCTree.MINUS_ASG;
                                JCTree lhs = cast
                                    ? make.TypeCast(tree.arg.type, (JCExpression)tmp1)
                                    : tmp1;
                                JCTree update = makeAssignop(opcode,
                                                             lhs,
                                                             make.Literal(1));
                                return makeComma(update, tmp2);
                            }
                        });
                }
            });
    }

    public void visitUnary(JCUnary tree) {
        boolean isUpdateOperator =
            JCTree.PREINC <= tree.getTag() && tree.getTag() <= JCTree.POSTDEC;
        if (isUpdateOperator && !tree.arg.type.isPrimitive()) {
            switch(tree.getTag()) {
            case JCTree.PREINC:            // ++ e
                    // translate to e += 1
            case JCTree.PREDEC:            // -- e
                    // translate to e -= 1
                {
                    int opcode = (tree.getTag() == JCTree.PREINC)
                        ? JCTree.PLUS_ASG : JCTree.MINUS_ASG;
                    JCAssignOp newTree = makeAssignop(opcode,
                                                    tree.arg,
                                                    make.Literal(1));
                    result = translate(newTree, tree.type);
                    return;
                }
            case JCTree.POSTINC:           // e ++
            case JCTree.POSTDEC:           // e --
                {
                    result = translate(lowerBoxedPostop(tree), tree.type);
                    return;
                }
            }
            throw new AssertionError(tree);
        }

        tree.arg = boxIfNeeded(translate(tree.arg, tree), tree.type);

        if (tree.getTag() == JCTree.NOT && tree.arg.type.constValue() != null) {
            tree.type = cfolder.fold1(bool_not, tree.arg.type);
        }

        // If translated left hand side is an Apply, we are
        // seeing an access method invocation. In this case, return
        // that access method invocation as result.
        if (isUpdateOperator && tree.arg.getTag() == JCTree.APPLY) {
            result = tree.arg;
        } else {
            result = tree;
        }
    }

    public void visitBinary(JCBinary tree) {
        List<Type> formals = tree.operator.type.getParameterTypes();
        JCTree lhs = tree.lhs = translate(tree.lhs, formals.head);
        switch (tree.getTag()) {
        case JCTree.OR:
            if (lhs.type.isTrue()) {
                result = lhs;
                return;
            }
            if (lhs.type.isFalse()) {
                result = translate(tree.rhs, formals.tail.head);
                return;
            }
            break;
        case JCTree.AND:
            if (lhs.type.isFalse()) {
                result = lhs;
                return;
            }
            if (lhs.type.isTrue()) {
                result = translate(tree.rhs, formals.tail.head);
                return;
            }
            break;
        }
        tree.rhs = translate(tree.rhs, formals.tail.head);
        result = tree;
    }

    // visitIdent()方法更新本地类中引用自由变量的方式，替换为引用合成的成员变量
    public void visitIdent(JCIdent tree) {
        result = access(tree.sym, tree, enclOp, false);
    }

    /** Translate away the foreach loop.  */
    // 访问增强for循环 解语法糖
    public void visitForeachLoop(JCEnhancedForLoop tree) {
        if (types.elemtype(tree.expr.type) == null)
            // visitIterableForeachLoop()方法对遍历容器的foreach语句解语法糖
            visitIterableForeachLoop(tree);
        else
            // visitArrayForeachLoop()方法对遍历数组的foreach语句解语法糖
            // 例15-6
            visitArrayForeachLoop(tree);
    }
        // where
        /**
         * A statement of the form
         *
         * <pre>
         *     for ( T v : arrayexpr ) stmt;
         * </pre>
         *
         * (where arrayexpr is of an array type) gets translated to
         *
         * <pre>
         *     for ( { arraytype #arr = arrayexpr;
         *             int #len = array.length;
         *             int #i = 0; };
         *           #i < #len; i$++ ) {
         *         T v = arr$[#i];
         *         stmt;
         *     }
         * </pre>
         *
         * where #arr, #len, and #i are freshly named synthetic local variables.
         */
        // 数组的增强for解语法糖
        // 按照一定的形式重新生成新的语法树结构即可，并将最终生成的语法树节点赋值给result
        private void visitArrayForeachLoop(JCEnhancedForLoop tree) {
            make_at(tree.expr.pos());
            VarSymbol arraycache = new VarSymbol(0,
                                                 names.fromString("arr" + target.syntheticNameChar()),
                                                 tree.expr.type,
                                                 currentMethodSym);
            JCStatement arraycachedef = make.VarDef(arraycache, tree.expr);
            VarSymbol lencache = new VarSymbol(0,
                                               names.fromString("len" + target.syntheticNameChar()),
                                               syms.intType,
                                               currentMethodSym);
            JCStatement lencachedef = make.
                VarDef(lencache, make.Select(make.Ident(arraycache), syms.lengthVar));
            VarSymbol index = new VarSymbol(0,
                                            names.fromString("i" + target.syntheticNameChar()),
                                            syms.intType,
                                            currentMethodSym);

            JCVariableDecl indexdef = make.VarDef(index, make.Literal(INT, 0));
            indexdef.init.type = indexdef.type = syms.intType.constType(0);

            List<JCStatement> loopinit = List.of(arraycachedef, lencachedef, indexdef);
            JCBinary cond = makeBinary(JCTree.LT, make.Ident(index), make.Ident(lencache));

            JCExpressionStatement step = make.Exec(makeUnary(JCTree.PREINC, make.Ident(index)));

            Type elemtype = types.elemtype(tree.expr.type);
            JCExpression loopvarinit = make.Indexed(make.Ident(arraycache),
                                                    make.Ident(index)).setType(elemtype);
            JCVariableDecl loopvardef = (JCVariableDecl)make.VarDef(tree.var.mods,
                                                  tree.var.name,
                                                  tree.var.vartype,
                                                  loopvarinit).setType(tree.var.type);
            loopvardef.sym = tree.var.sym;
            JCBlock body = make.
                Block(0, List.of(loopvardef, tree.body));

            result = translate(make.
                               ForLoop(loopinit,
                                       cond,
                                       List.of(step),
                                       body));
            // 调用patchTargets()方法更新foreach语句body体中break与continue的跳转目标
            patchTargets(body, tree, result);
        }
        /** Patch up break and continue targets. */
        private void patchTargets(JCTree body, final JCTree src, final JCTree dest) {
            class Patcher extends TreeScanner {
                public void visitBreak(JCBreak tree) {
                    if (tree.target == src)
                        tree.target = dest;
                }
                public void visitContinue(JCContinue tree) {
                    if (tree.target == src)
                        tree.target = dest;
                }
                public void visitClassDef(JCClassDecl tree) {}
            }
            new Patcher().scan(body);
        }
        /**
         * A statement of the form
         *
         * <pre>
         *     for ( T v : coll ) stmt ;
         * </pre>
         *
         * (where coll implements Iterable<? extends T>) gets translated to
         *
         * <pre>
         *     for ( Iterator<? extends T> #i = coll.iterator(); #i.hasNext(); ) {
         *         T v = (T) #i.next();
         *         stmt;
         *     }
         * </pre>
         *
         * where #i is a freshly named synthetic local variable.
         */
        // 容器类型的增强for循环，解语法糖
        // 参数list的类型必须直接或间接实现Iterable接口，这样才能通过foreach语句循环遍历
        // 只要按照一定的形式重新生成新的语法树结构即可，
        // 同时也会调用patchTargets()方法更新foreach语句body体中break与continue的跳转目标
        private void visitIterableForeachLoop(JCEnhancedForLoop tree) {
            make_at(tree.expr.pos());
            Type iteratorTarget = syms.objectType;
            Type iterableType = types.asSuper(types.upperBound(tree.expr.type),
                                              syms.iterableType.tsym);
            if (iterableType.getTypeArguments().nonEmpty())
                iteratorTarget = types.erasure(iterableType.getTypeArguments().head);
            Type eType = tree.expr.type;
            tree.expr.type = types.erasure(eType);
            if (eType.tag == TYPEVAR && eType.getUpperBound().isCompound())
                tree.expr = make.TypeCast(types.erasure(iterableType), tree.expr);
            Symbol iterator = lookupMethod(tree.expr.pos(),
                                           names.iterator,
                                           types.erasure(syms.iterableType),
                                           List.<Type>nil());
            VarSymbol itvar = new VarSymbol(0, names.fromString("i" + target.syntheticNameChar()),
                                            types.erasure(iterator.type.getReturnType()),
                                            currentMethodSym);
            JCStatement init = make.
                VarDef(itvar,
                       make.App(make.Select(tree.expr, iterator)));
            Symbol hasNext = lookupMethod(tree.expr.pos(),
                                          names.hasNext,
                                          itvar.type,
                                          List.<Type>nil());
            JCMethodInvocation cond = make.App(make.Select(make.Ident(itvar), hasNext));
            Symbol next = lookupMethod(tree.expr.pos(),
                                       names.next,
                                       itvar.type,
                                       List.<Type>nil());
            JCExpression vardefinit = make.App(make.Select(make.Ident(itvar), next));
            if (tree.var.type.isPrimitive())
                vardefinit = make.TypeCast(types.upperBound(iteratorTarget), vardefinit);
            else
                vardefinit = make.TypeCast(tree.var.type, vardefinit);
            JCVariableDecl indexDef = (JCVariableDecl)make.VarDef(tree.var.mods,
                                                  tree.var.name,
                                                  tree.var.vartype,
                                                  vardefinit).setType(tree.var.type);
            indexDef.sym = tree.var.sym;
            JCBlock body = make.Block(0, List.of(indexDef, tree.body));
            body.endpos = TreeInfo.endPos(tree.body);
            result = translate(make.
                ForLoop(List.of(init),
                        cond,
                        List.<JCExpressionStatement>nil(),
                        body));
            patchTargets(body, tree, result);
        }

    public void visitVarDef(JCVariableDecl tree) {
        MethodSymbol oldMethodSym = currentMethodSym;
        tree.mods = translate(tree.mods);
        tree.vartype = translate(tree.vartype);
        if (currentMethodSym == null) {
            // A class or instance field initializer.
            currentMethodSym =
                new MethodSymbol((tree.mods.flags&STATIC) | BLOCK,
                                 names.empty, null,
                                 currentClass);
        }
        if (tree.init != null) tree.init = translate(tree.init, tree.type);
        result = tree;
        currentMethodSym = oldMethodSym;
    }

    public void visitBlock(JCBlock tree) {
        MethodSymbol oldMethodSym = currentMethodSym;
        if (currentMethodSym == null) {
            // Block is a static or instance initializer.
            currentMethodSym =
                new MethodSymbol(tree.flags | BLOCK,
                                 names.empty, null,
                                 currentClass);
        }
        super.visitBlock(tree);
        currentMethodSym = oldMethodSym;
    }

    public void visitDoLoop(JCDoWhileLoop tree) {
        tree.body = translate(tree.body);
        tree.cond = translate(tree.cond, syms.booleanType);
        result = tree;
    }

    public void visitWhileLoop(JCWhileLoop tree) {
        tree.cond = translate(tree.cond, syms.booleanType);
        tree.body = translate(tree.body);
        result = tree;
    }

    public void visitForLoop(JCForLoop tree) {
        tree.init = translate(tree.init);
        if (tree.cond != null)
            tree.cond = translate(tree.cond, syms.booleanType);
        tree.step = translate(tree.step);
        tree.body = translate(tree.body);
        result = tree;
    }

    public void visitReturn(JCReturn tree) {
        if (tree.expr != null)
            tree.expr = translate(tree.expr,
                                  types.erasure(currentMethodDef
                                                .restype.type));
        result = tree;
    }

    // 解Switch语法的语法糖
    // switch语句中的选择表达式的类型可以是Enum类型、String类型或int类型，如果为Enum与String类型，需要在visitSwitch()方法中解语法糖
    public void visitSwitch(JCSwitch tree) {
        // 调用types.supertype()方法获取tree.selector.type的父类，通过父类来判断选择表达式的类型是否为枚举类，
        // 因为任何一个枚举类默认都会继承Enum类
        Type selsuper = types.supertype(tree.selector.type);
        boolean enumSwitch = selsuper != null &&
            (tree.selector.type.tsym.flags() & ENUM) != 0;
        boolean stringSwitch = selsuper != null &&
            types.isSameType(tree.selector.type, syms.stringType);
        Type target = enumSwitch ? tree.selector.type :
            (stringSwitch? syms.stringType : syms.intType);
        tree.selector = translate(tree.selector, target);
        tree.cases = translateCases(tree.cases);
        if (enumSwitch) {
            // enum类型switch表达式解语法糖
            result = visitEnumSwitch(tree);
        } else if (stringSwitch) {
            // string类型Switch表达式解语法糖
            result = visitStringSwitch(tree);
        } else {
            result = tree;
        }
    }

    // 枚举类型表达式的Switch解语法糖
    // 当switch语句的选择表达式的类型为枚举类时，将枚举常量映射为整数，
    // 这个关系由一个匿名类Test$1中定义的一个静态数组保存。通过Test$1的静态匿名块可看到对静态数组的初始化过程，
    // 各个枚举常量的ordinal作为数组下标，值为一个对应的整数值，这个整数值从1开始递增
    // 例15-8
    public JCTree visitEnumSwitch(JCSwitch tree) {
        TypeSymbol enumSym = tree.selector.type.tsym;
        // 根据enumSym获取一个EnumMapping对象map
        EnumMapping map = mapForEnum(tree.pos(), enumSym);
        make_at(tree.pos());
        Symbol ordinalMethod = lookupMethod(tree.pos(),
                                            names.ordinal,
                                            tree.selector.type,
                                            List.<Type>nil());
        JCArrayAccess selector = make.Indexed(map.mapVar,
                                        make.App(make.Select(tree.selector,
                                                             ordinalMethod)));
        // 创建好switch语句的selector后就需要更新各个分支
        ListBuffer<JCCase> cases = new ListBuffer<JCCase>();
        for (JCCase c : tree.cases) {
            // c.pat不为空时表示非默认分支，创建新的引用形式
            if (c.pat != null) {
                VarSymbol label = (VarSymbol)TreeInfo.symbol(c.pat);
                // 调用map.forConstant()方法得到此分支的整数值
                JCLiteral pat = map.forConstant(label);
                cases.append(make.Case(pat, c.stats));
            } else {
                cases.append(c);
            }
        }
        JCSwitch enumSwitch = make.Switch(selector, cases.toList());
        // 但由于switch语句中可能会出现break语句，所以调用patchTargets()方法更新跳转目标
        patchTargets(enumSwitch, tree, enumSwitch);
        return enumSwitch;
    }

    // String类型的Switch表达式解语法糖
    // 例15-9
    // 解语法糖过程是利用字符串的哈希值的唯一性做了字符串到整数的映射，
    // 最终还是将字符串类型的选择表达式转换为int类型的选择表达式，
    // 因为在字符码指令的生成过程中，switch语句会选择tableswitch或lookupswitch指令来实现，
    // 而这两个指令的索引值只支持整数，这样处理可以更好地生成字节码。
    // visitStringSwitch()方法通过一定规则重新生成语法树结构，同时也会对switch语句中的各个分支调用patchTargets()方法更新break的跳转目标
    public JCTree visitStringSwitch(JCSwitch tree) {
        List<JCCase> caseList = tree.getCases();
        int alternatives = caseList.size();

        if (alternatives == 0) { // Strange but legal possibility
            return make.at(tree.pos()).Exec(attr.makeNullCheck(tree.getExpression()));
        } else {

            ListBuffer<JCStatement> stmtList = new ListBuffer<JCStatement>();

            // Map from String case labels to their original position in
            // the list of case labels.
            Map<String, Integer> caseLabelToPosition =
                new LinkedHashMap<String, Integer>(alternatives + 1, 1.0f);

            // Map of hash codes to the string case labels having that hashCode.
            Map<Integer, Set<String>> hashToString =
                new LinkedHashMap<Integer, Set<String>>(alternatives + 1, 1.0f);

            int casePosition = 0;
            for(JCCase oneCase : caseList) {
                JCExpression expression = oneCase.getExpression();

                if (expression != null) { // expression for a "default" case is null
                    String labelExpr = (String) expression.type.constValue();
                    Integer mapping = caseLabelToPosition.put(labelExpr, casePosition);
                    Assert.checkNull(mapping);
                    int hashCode = labelExpr.hashCode();

                    Set<String> stringSet = hashToString.get(hashCode);
                    if (stringSet == null) {
                        stringSet = new LinkedHashSet<String>(1, 1.0f);
                        stringSet.add(labelExpr);
                        hashToString.put(hashCode, stringSet);
                    } else {
                        boolean added = stringSet.add(labelExpr);
                        Assert.check(added);
                    }
                }
                casePosition++;
            }

            VarSymbol dollar_s = new VarSymbol(FINAL|SYNTHETIC,
                                               names.fromString("s" + tree.pos + target.syntheticNameChar()),
                                               syms.stringType,
                                               currentMethodSym);
            stmtList.append(make.at(tree.pos()).VarDef(dollar_s, tree.getExpression()).setType(dollar_s.type));

            VarSymbol dollar_tmp = new VarSymbol(SYNTHETIC,
                                                 names.fromString("tmp" + tree.pos + target.syntheticNameChar()),
                                                 syms.intType,
                                                 currentMethodSym);
            JCVariableDecl dollar_tmp_def =
                (JCVariableDecl)make.VarDef(dollar_tmp, make.Literal(INT, -1)).setType(dollar_tmp.type);
            dollar_tmp_def.init.type = dollar_tmp.type = syms.intType;
            stmtList.append(dollar_tmp_def);
            ListBuffer<JCCase> caseBuffer = ListBuffer.lb();
            // hashCode will trigger nullcheck on original switch expression
            JCMethodInvocation hashCodeCall = makeCall(make.Ident(dollar_s),
                                                       names.hashCode,
                                                       List.<JCExpression>nil()).setType(syms.intType);
            JCSwitch switch1 = make.Switch(hashCodeCall,
                                        caseBuffer.toList());
            for(Map.Entry<Integer, Set<String>> entry : hashToString.entrySet()) {
                int hashCode = entry.getKey();
                Set<String> stringsWithHashCode = entry.getValue();
                Assert.check(stringsWithHashCode.size() >= 1);

                JCStatement elsepart = null;
                for(String caseLabel : stringsWithHashCode ) {
                    JCMethodInvocation stringEqualsCall = makeCall(make.Ident(dollar_s),
                                                                   names.equals,
                                                                   List.<JCExpression>of(make.Literal(caseLabel)));
                    elsepart = make.If(stringEqualsCall,
                                       make.Exec(make.Assign(make.Ident(dollar_tmp),
                                                             make.Literal(caseLabelToPosition.get(caseLabel))).
                                                 setType(dollar_tmp.type)),
                                       elsepart);
                }

                ListBuffer<JCStatement> lb = ListBuffer.lb();
                JCBreak breakStmt = make.Break(null);
                breakStmt.target = switch1;
                lb.append(elsepart).append(breakStmt);

                caseBuffer.append(make.Case(make.Literal(hashCode), lb.toList()));
            }

            switch1.cases = caseBuffer.toList();
            stmtList.append(switch1);

            ListBuffer<JCCase> lb = ListBuffer.lb();
            JCSwitch switch2 = make.Switch(make.Ident(dollar_tmp), lb.toList());
            for(JCCase oneCase : caseList ) {
                patchTargets(oneCase, tree, switch2);

                boolean isDefault = (oneCase.getExpression() == null);
                JCExpression caseExpr;
                if (isDefault)
                    caseExpr = null;
                else {
                    caseExpr = make.Literal(caseLabelToPosition.get((String)oneCase.
                                                                    getExpression().
                                                                    type.constValue()));
                }

                lb.append(make.Case(caseExpr,
                                    oneCase.getStatements()));
            }

            switch2.cases = lb.toList();
            stmtList.append(switch2);

            return make.Block(0L, stmtList.toList());
        }
    }

    public void visitNewArray(JCNewArray tree) {
        tree.elemtype = translate(tree.elemtype);
        for (List<JCExpression> t = tree.dims; t.tail != null; t = t.tail)
            if (t.head != null) t.head = translate(t.head, syms.intType);
        tree.elems = translate(tree.elems, types.elemtype(tree.type));
        result = tree;
    }

    public void visitSelect(JCFieldAccess tree) {
        // need to special case-access of the form C.super.x
        // these will always need an access method.
        boolean qualifiedSuperAccess =
            tree.selected.getTag() == JCTree.SELECT &&
            TreeInfo.name(tree.selected) == names._super;
        tree.selected = translate(tree.selected);
        if (tree.name == names._class)
            result = classOf(tree.selected);
        else if (tree.name == names._this || tree.name == names._super)
            result = makeThis(tree.pos(), tree.selected.type.tsym);
        else
            result = access(tree.sym, tree, enclOp, qualifiedSuperAccess);
    }

    public void visitLetExpr(LetExpr tree) {
        tree.defs = translateVarDefs(tree.defs);
        tree.expr = translate(tree.expr, tree.type);
        result = tree;
    }

    // There ought to be nothing to rewrite here;
    // we don't generate code.
    public void visitAnnotation(JCAnnotation tree) {
        result = tree;
    }

    @Override
    // 扩展try-with-resources解语法糖解析
    public void visitTry(JCTry tree) {
        if (tree.resources.isEmpty()) {
            super.visitTry(tree);
        } else {
            // 当tree.resources不为空时，
            // 调用makeTwrTry()方法对扩展try-with-resources解语法糖
            result = makeTwrTry(tree);
        }
    }

/**************************************************************************
 * main method
 *************************************************************************/

    /** Translate a toplevel class and return a list consisting of
     *  the translated class and translated versions of all inner classes.
     *  @param env   The attribution environment current at the class definition.
     *               We need this for resolving some additional symbols.
     *  @param cdef  The tree representing the class definition.
     */
    // 生成顶级类
    public List<JCTree> translateTopLevelClass(Env<AttrContext> env, JCTree cdef, TreeMaker make) {
        ListBuffer<JCTree> translated = null;
        try {
            attrEnv = env;
            this.make = make;
            endPositions = env.toplevel.endPositions;
            currentClass = null;
            currentMethodDef = null;
            outermostClassDef = (cdef.getTag() == JCTree.CLASSDEF) ? (JCClassDecl)cdef : null;
            outermostMemberDef = null;
            this.translated = new ListBuffer<JCTree>();
            classdefs = new HashMap<ClassSymbol,JCClassDecl>();
            actualSymbols = new HashMap<Symbol,Symbol>();
            freevarCache = new HashMap<ClassSymbol,List<VarSymbol>>();
            proxies = new Scope(syms.noSymbol);
            twrVars = new Scope(syms.noSymbol);
            outerThisStack = List.nil();
            accessNums = new HashMap<Symbol,Integer>();
            accessSyms = new HashMap<Symbol,MethodSymbol[]>();
            accessConstrs = new HashMap<Symbol,MethodSymbol>();
            accessConstrTags = List.nil();
            accessed = new ListBuffer<Symbol>();
            translate(cdef, (JCExpression)null);
            // 循环所有可访问的符号，标注到语法树上
            for (List<Symbol> l = accessed.toList(); l.nonEmpty(); l = l.tail)
                makeAccessible(l.head);
            // 为枚举类型的Switch表达式生成赋值顶级类
            for (EnumMapping map : enumSwitchMap.values())
                // 调用map.translate()方法创建匿名类
                map.translate();
            checkConflicts(this.translated.toList());
            checkAccessConstructorTags();
            translated = this.translated;
        } finally {
            // note that recursive invocations of this method fail hard
            attrEnv = null;
            this.make = null;
            endPositions = null;
            currentClass = null;
            currentMethodDef = null;
            outermostClassDef = null;
            outermostMemberDef = null;
            this.translated = null;
            classdefs = null;
            actualSymbols = null;
            freevarCache = null;
            proxies = null;
            outerThisStack = null;
            accessNums = null;
            accessSyms = null;
            accessConstrs = null;
            accessConstrTags = null;
            accessed = null;
            enumSwitchMap.clear();
        }
        return translated.toList();
    }

    //////////////////////////////////////////////////////////////
    // The following contributed by Borland for bootstrapping purposes
    //////////////////////////////////////////////////////////////
    private void addEnumCompatibleMembers(JCClassDecl cdef) {
        make_at(null);

        // Add the special enum fields
        VarSymbol ordinalFieldSym = addEnumOrdinalField(cdef);
        VarSymbol nameFieldSym = addEnumNameField(cdef);

        // Add the accessor methods for name and ordinal
        MethodSymbol ordinalMethodSym = addEnumFieldOrdinalMethod(cdef, ordinalFieldSym);
        MethodSymbol nameMethodSym = addEnumFieldNameMethod(cdef, nameFieldSym);

        // Add the toString method
        addEnumToString(cdef, nameFieldSym);

        // Add the compareTo method
        addEnumCompareTo(cdef, ordinalFieldSym);
    }

    private VarSymbol addEnumOrdinalField(JCClassDecl cdef) {
        VarSymbol ordinal = new VarSymbol(PRIVATE|FINAL|SYNTHETIC,
                                          names.fromString("$ordinal"),
                                          syms.intType,
                                          cdef.sym);
        cdef.sym.members().enter(ordinal);
        cdef.defs = cdef.defs.prepend(make.VarDef(ordinal, null));
        return ordinal;
    }

    private VarSymbol addEnumNameField(JCClassDecl cdef) {
        VarSymbol name = new VarSymbol(PRIVATE|FINAL|SYNTHETIC,
                                          names.fromString("$name"),
                                          syms.stringType,
                                          cdef.sym);
        cdef.sym.members().enter(name);
        cdef.defs = cdef.defs.prepend(make.VarDef(name, null));
        return name;
    }

    private MethodSymbol addEnumFieldOrdinalMethod(JCClassDecl cdef, VarSymbol ordinalSymbol) {
        // Add the accessor methods for ordinal
        Symbol ordinalSym = lookupMethod(cdef.pos(),
                                         names.ordinal,
                                         cdef.type,
                                         List.<Type>nil());

        Assert.check(ordinalSym instanceof MethodSymbol);

        JCStatement ret = make.Return(make.Ident(ordinalSymbol));
        cdef.defs = cdef.defs.append(make.MethodDef((MethodSymbol)ordinalSym,
                                                    make.Block(0L, List.of(ret))));

        return (MethodSymbol)ordinalSym;
    }

    private MethodSymbol addEnumFieldNameMethod(JCClassDecl cdef, VarSymbol nameSymbol) {
        // Add the accessor methods for name
        Symbol nameSym = lookupMethod(cdef.pos(),
                                   names._name,
                                   cdef.type,
                                   List.<Type>nil());

        Assert.check(nameSym instanceof MethodSymbol);

        JCStatement ret = make.Return(make.Ident(nameSymbol));

        cdef.defs = cdef.defs.append(make.MethodDef((MethodSymbol)nameSym,
                                                    make.Block(0L, List.of(ret))));

        return (MethodSymbol)nameSym;
    }

    private MethodSymbol addEnumToString(JCClassDecl cdef,
                                         VarSymbol nameSymbol) {
        Symbol toStringSym = lookupMethod(cdef.pos(),
                                          names.toString,
                                          cdef.type,
                                          List.<Type>nil());

        JCTree toStringDecl = null;
        if (toStringSym != null)
            toStringDecl = TreeInfo.declarationFor(toStringSym, cdef);

        if (toStringDecl != null)
            return (MethodSymbol)toStringSym;

        JCStatement ret = make.Return(make.Ident(nameSymbol));

        JCTree resTypeTree = make.Type(syms.stringType);

        MethodType toStringType = new MethodType(List.<Type>nil(),
                                                 syms.stringType,
                                                 List.<Type>nil(),
                                                 cdef.sym);
        toStringSym = new MethodSymbol(PUBLIC,
                                       names.toString,
                                       toStringType,
                                       cdef.type.tsym);
        toStringDecl = make.MethodDef((MethodSymbol)toStringSym,
                                      make.Block(0L, List.of(ret)));

        cdef.defs = cdef.defs.prepend(toStringDecl);
        cdef.sym.members().enter(toStringSym);

        return (MethodSymbol)toStringSym;
    }

    private MethodSymbol addEnumCompareTo(JCClassDecl cdef, VarSymbol ordinalSymbol) {
        Symbol compareToSym = lookupMethod(cdef.pos(),
                                   names.compareTo,
                                   cdef.type,
                                   List.of(cdef.sym.type));

        Assert.check(compareToSym instanceof MethodSymbol);

        JCMethodDecl compareToDecl = (JCMethodDecl) TreeInfo.declarationFor(compareToSym, cdef);

        ListBuffer<JCStatement> blockStatements = new ListBuffer<JCStatement>();

        JCModifiers mod1 = make.Modifiers(0L);
        Name oName = names.fromString("o");
        JCVariableDecl par1 = make.Param(oName, cdef.type, compareToSym);

        JCIdent paramId1 = make.Ident(names.java_lang_Object);
        paramId1.type = cdef.type;
        paramId1.sym = par1.sym;

        ((MethodSymbol)compareToSym).params = List.of(par1.sym);

        JCIdent par1UsageId = make.Ident(par1.sym);
        JCIdent castTargetIdent = make.Ident(cdef.sym);
        JCTypeCast cast = make.TypeCast(castTargetIdent, par1UsageId);
        cast.setType(castTargetIdent.type);

        Name otherName = names.fromString("other");

        VarSymbol otherVarSym = new VarSymbol(mod1.flags,
                                              otherName,
                                              cdef.type,
                                              compareToSym);
        JCVariableDecl otherVar = make.VarDef(otherVarSym, cast);
        blockStatements.append(otherVar);

        JCIdent id1 = make.Ident(ordinalSymbol);

        JCIdent fLocUsageId = make.Ident(otherVarSym);
        JCExpression sel = make.Select(fLocUsageId, ordinalSymbol);
        JCBinary bin = makeBinary(JCTree.MINUS, id1, sel);
        JCReturn ret = make.Return(bin);
        blockStatements.append(ret);
        JCMethodDecl compareToMethod = make.MethodDef((MethodSymbol)compareToSym,
                                                   make.Block(0L,
                                                              blockStatements.toList()));
        compareToMethod.params = List.of(par1);
        cdef.defs = cdef.defs.append(compareToMethod);

        return (MethodSymbol)compareToSym;
    }
    //////////////////////////////////////////////////////////////
    // The above contributed by Borland for bootstrapping purposes
    //////////////////////////////////////////////////////////////
}
