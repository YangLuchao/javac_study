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
import javax.tools.JavaFileObject;
import javax.tools.JavaFileManager;

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Scope.*;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.jvm.*;
import com.sun.tools.javac.main.RecognizedOptions.PkgInfo;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.tree.JCTree.*;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.util.List;


import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.*;

/** This class enters symbols for all encountered definitions into
 *  the symbol table. The pass consists of two phases, organized as
 *  follows:
 *
 *  <p>In the first phase, all class symbols are intered into their
 *  enclosing scope, descending recursively down the tree for classes
 *  which are members of other classes. The class symbols are given a
 *  MemberEnter object as completer.
 *
 *  <p>In the second phase classes are completed using
 *  MemberEnter.complete().  Completion might occur on demand, but
 *  any classes that are not completed that way will be eventually
 *  completed by processing the `uncompleted' queue.  Completion
 *  entails (1) determination of a class's parameters, supertype and
 *  interfaces, as well as (2) entering all symbols defined in the
 *  class into its scope, with the exception of class symbols which
 *  have been entered in phase 1.  (2) depends on (1) having been
 *  completed for a class and all its superclasses and enclosing
 *  classes. That's why, after doing (1), we put classes in a
 *  `halfcompleted' queue. Only when we have performed (1) for a class
 *  and all it's superclasses and enclosing classes, we proceed to
 *  (2).
 *
 *  <p>Whereas the first phase is organized as a sweep through all
 *  compiled syntax trees, the second phase is demand. Members of a
 *  class are entered when the contents of a class are first
 *  accessed. This is accomplished by installing completer objects in
 *  class symbols for compiled classes which invoke the member-enter
 *  phase for the corresponding class tree.
 *
 *  <p>Classes migrate from one phase to the next via queues:
 *
 *  <pre>
 *  class enter -> (Enter.uncompleted)         --> member enter (1)
 *              -> (MemberEnter.halfcompleted) --> member enter (2)
 *              -> (Todo)                      --> attribute
 *                                              (only for toplevel classes)
 *  </pre>
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
// 填充符号表的过程主要由com.sun.tools.javac.comp.Enter和com.sun.tools.javac.comp.MemberEnter类来完成
// 自上而下遍历抽象语法树，将遇到的符号定义填充到符号表中
// 将Entry对象填充到Scope对象的table数组中
/*
符号输入的第一阶段要将当前编译单元下所有的非本地类的类符号输入到对应owner类的members_field变量中，
对于编译单元内的顶层类来说，会输入到namedImportScope与packge中。
 */
public class Enter extends JCTree.Visitor {
    protected static final Context.Key<Enter> enterKey =
        new Context.Key<Enter>();

    Log log;
    Symtab syms;
    Check chk;
    TreeMaker make;
    ClassReader reader;
    Annotate annotate;
    MemberEnter memberEnter;
    Types types;
    Lint lint;
    Names names;
    JavaFileManager fileManager;
    PkgInfo pkginfoOpt;

    private final Todo todo;

    public static Enter instance(Context context) {
        Enter instance = context.get(enterKey);
        if (instance == null)
            instance = new Enter(context);
        return instance;
    }

    protected Enter(Context context) {
        context.put(enterKey, this);

        log = Log.instance(context);
        reader = ClassReader.instance(context);
        make = TreeMaker.instance(context);
        syms = Symtab.instance(context);
        chk = Check.instance(context);
        memberEnter = MemberEnter.instance(context);
        types = Types.instance(context);
        annotate = Annotate.instance(context);
        lint = Lint.instance(context);
        names = Names.instance(context);

        predefClassDef = make.ClassDef(
            make.Modifiers(PUBLIC),
            syms.predefClass.name, null, null, null, null);
        predefClassDef.sym = syms.predefClass;
        todo = Todo.instance(context);
        fileManager = context.get(JavaFileManager.class);

        Options options = Options.instance(context);
        pkginfoOpt = PkgInfo.get(options);
    }

    /** A hashtable mapping classes and packages to the environments current
     *  at the points of their definitions.
     */
    // 保存了TypeSymbol对象到Env对象的映射
    // 符号输入阶段在循环处理uncompleted中的ClassSymbol对象时，如果需要上下文环境，可直接通过typeEnvs变量获取即可
    Map<TypeSymbol,Env<AttrContext>> typeEnvs =
            new HashMap<TypeSymbol,Env<AttrContext>>();

    /** Accessor for typeEnvs
     */
    public Env<AttrContext> getEnv(TypeSymbol sym) {
        return typeEnvs.get(sym);
    }

    public Env<AttrContext> getClassEnv(TypeSymbol sym) {
        Env<AttrContext> localEnv = getEnv(sym);
        Env<AttrContext> lintEnv = localEnv;
        while (lintEnv.info.lint == null)
            lintEnv = lintEnv.next;
        localEnv.info.lint = lintEnv.info.lint.augment(sym.attributes_field, sym.flags());
        return localEnv;
    }

    /** The queue of all classes that might still need to be completed;
     *  saved and initialized by main().
     */
    // 没有编译过的类符号缓冲列表
    ListBuffer<ClassSymbol> uncompleted;

    /** A dummy class to serve as enclClass for toplevel environments.
     */
    // 每个类型声明
    private JCClassDecl predefClassDef;

/* ************************************************************************
 * environment construction
 *************************************************************************/


    /** Create a fresh environment for class bodies.
     *  This will create a fresh scope for local symbols of a class, referred
     *  to by the environments info.scope field.
     *  This scope will contain
     *    - symbols for this and super
     *    - symbols for any type parameters
     *  In addition, it serves as an anchor for scopes of methods and initializers
     *  which are nested in this scope via Scope.dup().
     *  This scope should not be confused with the members scope of a class.
     *
     *  @param tree     The class definition.
     *  @param env      The environment current outside of the class definition.
     */
    public Env<AttrContext> classEnv(JCClassDecl tree, Env<AttrContext> env) {
        // 根据tree所处的上下文环境env创建localEnv
        // 如果tree为顶层类，参数env就是调用topLevelEnv()方法生成的上下文环境
        // 在创建过程中，由于类会形成一个新的作用域，所以创建了一个新的Scope对象
        Env<AttrContext> localEnv =
            env.dup(tree, env.info.dup(new Scope(tree.sym)));
        localEnv.enclClass = tree;
        localEnv.outer = env;
        localEnv.info.isSelfCall = false;
        localEnv.info.lint = null; // leave this to be filled in by Attr,
                                   // when annotations have been processed
        return localEnv;
    }

    /** Create a fresh environment for toplevels.
     *  @param tree     The toplevel tree.
     */
    // 创建编译单元对应的环境
    Env<AttrContext> topLevelEnv(JCCompilationUnit tree) {
        // 创建编译单元形成的上下文环境localEnv，设置各个变量的值
        Env<AttrContext> localEnv = new Env<AttrContext>(tree, new AttrContext());
        // 设置编译单元
        localEnv.toplevel = tree;
        // 设置类声明
        localEnv.enclClass = predefClassDef;
        // ImportScope与StarImportScope对象，这两个变量将保存导入声明导入的符号
        tree.namedImportScope = new ImportScope(tree.packge);
        tree.starImportScope = new StarImportScope(tree.packge);
        // tree.namedImportScope与localEnv.info.scope指向的是同一个ImportScope对象
        localEnv.info.scope = tree.namedImportScope;
        localEnv.info.lint = lint;
        // 在后续符号输入的过程中，会将当前编译单元内的所有顶层类输入到localEnv.info.scope中，
        // 所以tree.namedImportScope中也包含有当前编译单元中所有顶层类的符号
        return localEnv;
    }

    public Env<AttrContext> getTopLevelEnv(JCCompilationUnit tree) {
        Env<AttrContext> localEnv = new Env<AttrContext>(tree, new AttrContext());
        localEnv.toplevel = tree;
        localEnv.enclClass = predefClassDef;
        localEnv.info.scope = tree.namedImportScope;
        localEnv.info.lint = lint;
        return localEnv;
    }

    /** The scope in which a member definition in environment env is to be entered
     *  This is usually the environment's scope, except for class environments,
     *  where the local scope is for type variables, and the this and super symbol
     *  only, and members go into the class member scope.
     */
    // 调用enterScope()方法查找封闭类的作用域
    Scope enterScope(Env<AttrContext> env) {
        // 当env.tree为JCClassDecl对象时，说明tree是env.tree的成员类，
        // 方法将返回env.tree类的members_field，成员类的ClassSymbol对象最终会填充到宿主类的members_field中
        return (env.tree.getTag() == JCTree.CLASSDEF)
            ? ((JCClassDecl) env.tree).sym.members_field
            // 当env.tree为JCCompilationUnit对象时，那么enterScope()方法返回env.info.scope
            // scope变量被赋值为tree.namedImportScope，所以在visitClassDef()方法中将所有顶层类的符号输入到env.info.scope中，
            // 其实也相当于输入到了tree.namedImportScope中
            : env.info.scope;
    }

/* ************************************************************************
 * Visitor methods for phase 1: class enter
 *************************************************************************/

    /** Visitor argument: the current environment.
     */
    protected Env<AttrContext> env;

    /** Visitor result: the computed type.
     */
    Type result;

    /** Visitor method: enter all classes in given tree, catching any
     *  completion failure exceptions. Return the tree's type.
     *
     *  @param tree    The tree to be visited.
     *  @param env     The environment visitor argument.
     */
    Type classEnter(JCTree tree, Env<AttrContext> env) {
        // 如果要通过方法进行参数传递，可能需要在JCTree.Visitor类中定义许多不同的accept()方法，非常麻烦
        // 但是定义为成员变量后，由于不同语法节点对应着不同的env，每次调用classEnter()方法时都需要通过prevEnv保存当前的成员变量值，
        // 当处理完当前节点后再利用prevEnv还原env的值，类似于通过栈结构来保存不同的env值
        Env<AttrContext> prevEnv = this.env;
        try {
            // env保存了即将分析的语法树节点的上下文环境
            this.env = env;
            tree.accept(this);
            // result保存了处理当前语法节点tree后得到的类型
            return result;
        }  catch (CompletionFailure ex) {
            return chk.completionError(tree.pos(), ex);
        } finally {
            this.env = prevEnv;
        }
    }

    /** Visitor method: enter classes of a list of trees, returning a list of types.
     */
    // 符号输入的第一阶段
    // 调用classEnter()方法完成类符号输入，
    // 同时还会将除本地类外的所有类对应的ClassSymbol对象存储到uncompleted列表中，
    // 这样下一个符号输入阶段就可以直接循环uncompleted列表并调用clazz.complete()方法完成每个类中成员符号的填充了。
    <T extends JCTree> List<Type> classEnter(List<T> trees, Env<AttrContext> env) {
        ListBuffer<Type> ts = new ListBuffer<Type>();
        for (List<T> l = trees; l.nonEmpty(); l = l.tail) {
            // 调用另一个classEnter
            Type t = classEnter(l.head, env);
            if (t != null)
                ts.append(t);
        }
        return ts.toList();
    }

    @Override
    // 对编译单元进行处理
    public void visitTopLevel(JCCompilationUnit tree) {
        JavaFileObject prev = log.useSource(tree.sourcefile);
        boolean addEnv = false;
        boolean isPkgInfo = tree.sourcefile.isNameCompatible("package-info",
                                                             JavaFileObject.Kind.SOURCE);
        // 包声明不为空
        if (tree.pid != null) {
            tree.packge = reader.enterPackage(TreeInfo.fullName(tree.pid));
            if (tree.packageAnnotations.nonEmpty() || pkginfoOpt == PkgInfo.ALWAYS) {
                if (isPkgInfo) {
                    addEnv = true;
                } else {
                    log.error(tree.packageAnnotations.head.pos(),
                              "pkg.annotations.sb.in.package-info.java");
                }
            }
        } else {
            // 否则设置为没有名字的包
            tree.packge = syms.unnamedPackage;
        }
        // 完成包下符号的填充
        // 调用tree.packge的complete()方法其实还是调用ClassReader类中的complete()方法完成符号填充
        tree.packge.complete(); // Find all classes in package.
        // 创建编译单元对应的环境
        Env<AttrContext> topEnv = topLevelEnv(tree);

        // Save environment of package-info.java file.
        if (isPkgInfo) {
            Env<AttrContext> env0 = typeEnvs.get(tree.packge);
            if (env0 == null) {
                typeEnvs.put(tree.packge, topEnv);
            } else {
                JCCompilationUnit tree0 = env0.toplevel;
                if (!fileManager.isSameFile(tree.sourcefile, tree0.sourcefile)) {
                    log.warning(tree.pid != null ? tree.pid.pos()
                                                 : null,
                                "pkg-info.already.seen",
                                tree.packge);
                    if (addEnv || (tree0.packageAnnotations.isEmpty() &&
                                   tree.docComments != null &&
                                   tree.docComments.get(tree) != null)) {
                        typeEnvs.put(tree.packge, topEnv);
                    }
                }
            }

            for (Symbol q = tree.packge; q != null && q.kind == PCK; q = q.owner)
                q.flags_field |= EXISTS;

            Name name = names.package_info;
            ClassSymbol c = reader.enterClass(name, tree.packge);
            c.flatname = names.fromString(tree.packge + "." + name);
            c.sourcefile = tree.sourcefile;
            c.completer = null;
            c.members_field = new Scope(c);
            tree.packge.package_info = c;
        }
        // 遍历当前编译单元下的成员
        classEnter(tree.defs, topEnv);
        if (addEnv) {
            todo.append(topEnv);
        }
        log.useSource(prev);
        result = null;
    }

    @Override
    // 对定义的类进行处理
    // 任何类（包括本地类）都会调用visitClassDef()方法为当前的类型生成对应的ClassSymbol对象，
    // 然后将此对象标注到语法树上，同时也会填充到相关作用域的符号表内
    public void visitClassDef(JCClassDecl tree) {
        // -------------------------------------------------------------------具体实现的第一部分
        // env就是当前类所处的上下文环境，如果tree表示的是顶层类，
        // 那么env就是之前调用visitTopLevel()方法时创建的topEnv。调用enclScope()方法查找封闭类的作用域
        Symbol owner = env.info.scope.owner;
        // 调用enterScope()方法查找封闭类的作用域
        Scope enclScope = enterScope(env);
        ClassSymbol c;
        // 宿主的类型为PCK,表示tree为顶层类
        if (owner.kind == PCK) {
            PackageSymbol packge = (PackageSymbol)owner;
            for (Symbol q = packge; q != null && q.kind == PCK; q = q.owner)
                q.flags_field |= EXISTS;
            // 调用ClassReader对象reader的enterClass()方法生成顶层类的ClassSymbol对象
            c = reader.enterClass(tree.name, packge);
            // 将这个对象填充到所属包符号的members_field中
            packge.members().enterIfAbsent(c);
            if ((tree.mods.flags & PUBLIC) != 0 && !classNameMatchesFileName(c, env)) {
                log.error(tree.pos(),
                          "class.public.should.be.in.file", tree.name);
            }
        } else {
            if (!tree.name.isEmpty() &&
                !chk.checkUniqueClassName(tree.pos(), tree.name, enclScope)) {
                result = null;
                return;
            }
            // 宿主的类型为TYP,表明tree是成员类
            if (owner.kind == TYP) {
                // 调用reader.enterClass()方法生成ClassSymbol对象
                c = reader.enterClass(tree.name, (TypeSymbol)owner);
                if ((owner.flags_field & INTERFACE) != 0) {
                    tree.mods.flags |= PUBLIC | STATIC;
                }
            } else { // tree是本地类
                // 调用reader.enterClass()方法生成ClassSymbol对象
                c = reader.defineClass(tree.name, owner);
                // (匿名内部类)调用Check类中的localClassName()方法设置对象的flatname值，
                // localClassName()方法为本地类生成了flatname
                c.flatname = chk.localClassName(c);
                if (!c.name.isEmpty())
                    chk.checkTransparentClass(tree.pos(), c, env.info.scope);
            }
        }
        // 将获取到的ClassSymbol对象保存到tree.sym中，这样就完成了符号的标注
        tree.sym = c;

        // Enter class into `compiled' table and enclosing scope.
        if (chk.compiled.get(c.flatname) != null) {
            duplicateClass(tree.pos(), c);
            result = types.createErrorType(tree.name, (TypeSymbol)owner, Type.noType);
            tree.sym = (ClassSymbol)result.tsym;
            return;
        }
        chk.compiled.put(c.flatname, c);
        // 填充到宿主类符号的members_field中
        enclScope.enter(c);
        // -------------------------------------------------------------------具体实现的第一部分
        // -------------------------------------------------------------------具体实现的第二部分
        // 使用env中保存的上下文信息分析当前类
        // 如果要分析当前类中的成员，那么就需要调用classEnv()方法创建当前类形成的上下文环境
        Env<AttrContext> localEnv = classEnv(tree, env);
        // 通过成员变量typeEnvs保存创建出来的localEnv
        typeEnvs.put(c, localEnv);
        // -------------------------------------------------------------------具体实现的第二部分
        // -------------------------------------------------------------------具体实现的第三部分
        // c是之前获取到的ClassSymbol对象,为这个对象的completer、sourcefile与members_field变量赋值
        // 其中completer被赋值为MemberEnter对象memberEnter
        // MemberEnter类可以完成第二阶段的符号输入，
        // 也就是将类中的成员符号填充到对应类符号的members_field变量中
        c.completer = memberEnter;
        c.flags_field = chk.checkFlags(tree.pos(), tree.mods.flags, c, tree);
        c.sourcefile = env.toplevel.sourcefile;
        c.members_field = new Scope(c);

        ClassType ct = (ClassType)c.type;
        if (owner.kind != PCK && (c.flags_field & STATIC) == 0) {
            Symbol owner1 = owner;
            while ((owner1.kind & (VAR | MTH)) != 0 &&
                   (owner1.flags_field & STATIC) == 0) {
                owner1 = owner1.owner;
            }
            if (owner1.kind == TYP) {
                ct.setEnclosingType(owner1.type);
            }
        }

        // 处理类型声明的类型参数(泛型)
        ct.typarams_field = classEnter(tree.typarams, localEnv);

        // 将非本地类的ClassSymbol对象存储到uncompleted列表中，符号输入第二阶段
        // 将循环这个列表完成符号输入
        if (!c.isLocal() && uncompleted != null)
            uncompleted.append(c);

        // 处理类中的成员，主要是处理成员类
        classEnter(tree.defs, localEnv);
        // -------------------------------------------------------------------具体实现的第三部分

        result = c.type;
    }
    //where
        /** Does class have the same name as the file it appears in?
         */
        private static boolean classNameMatchesFileName(ClassSymbol c,
                                                        Env<AttrContext> env) {
            return env.toplevel.sourcefile.isNameCompatible(c.name.toString(),
                                                            JavaFileObject.Kind.SOURCE);
        }

    /** Complain about a duplicate class. */
    protected void duplicateClass(DiagnosticPosition pos, ClassSymbol c) {
        log.error(pos, "duplicate.class", c.fullname);
    }

    /** Class enter visitor method for type parameters.
     *  Enter a symbol for type parameter in local scope, after checking that it
     *  is unique.
     */
    @Override
    // 对类声明的类型变量进行处理(泛型)
    public void visitTypeParameter(JCTypeParameter tree) {
        // 获取JCTypeParameter对象对应的类型
        TypeVar a = (tree.type != null)
            ? (TypeVar)tree.type
            : new TypeVar(tree.name, env.info.scope.owner, syms.botType);
        // 将这个类型标注到了tree.type上
        tree.type = a;
        // env就是在visitClassDef()方法中调用classEnter()方法获取的localEnv
        if (chk.checkUnique(tree.pos(), a.tsym, env.info.scope)) {
            // 同时获取a.tsym符号并填充到env.info.scope中
            env.info.scope.enter(a.tsym);
        }
        result = a;
    }

    /** Default class enter visitor method: do nothing.
     */
    @Override
    // 对除JCCompilationUnit、JCClassDecl与TypeParameter树节点外的其他语法树节点进行处理。
    // 该方法基本是个空实现，表示第一阶段符号输入不对这些语法树节点进行处理
    public void visitTree(JCTree tree) {
        result = null;
    }

    /** Main method: enter all classes in a list of toplevel trees.
     *  @param trees      The list of trees to be processed.
     */
    public void main(List<JCCompilationUnit> trees) {
        complete(trees, null);
    }

    /** Main method: enter one class from a list of toplevel trees and
     *  place the rest on uncompleted for later processing.
     *  @param trees      The list of trees to be processed.
     *  @param c          The class symbol to be processed.
     */
    // 完成符号输入
    public void complete(List<JCCompilationUnit> trees, ClassSymbol c) {
        annotate.enterStart();
        ListBuffer<ClassSymbol> prevUncompleted = uncompleted;
        if (memberEnter.completionEnabled)
            uncompleted = new ListBuffer<ClassSymbol>();

        try {
            // 符号输入的第一阶段，将当前编译单元下所有的非本地类的类符号输入到对应owner类的members_field变量中
            // 调用classEnter()方法完成类符号输入，
            // 同时还会将除本地类外的所有类对应的ClassSymbol对象存储到uncompleted列表中，
            // 这样下一个符号输入阶段就可以直接循环uncompleted列表并调用clazz.complete()方法完成每个类中成员符号的填充了。
            classEnter(trees, null);

            // complete all uncompleted classes in memberEnter
            if  (memberEnter.completionEnabled) {
                // 符号输入的第二阶段
                while (uncompleted.nonEmpty()) {
                    ClassSymbol clazz = uncompleted.next();
                    if (c == null || c == clazz || prevUncompleted == null)
                        // 完成每个类中成员符号的填充了
                        // 调用Symbol的complete方法
                        // 这些ClassSymbol对象的completer在Enter类的visitClassDef()方法中被赋值为MemberEnter对象，
                        // 在Enter类的complete()方法中调用classEnter()方法完成第一阶段符号输入后，
                        // 接着会继续进行第二阶段的符号输入
                        clazz.complete();
                    else
                        // defer
                        prevUncompleted.append(clazz);
                }
                for (JCCompilationUnit tree : trees) {
                    if (tree.starImportScope.elems == null) {
                        JavaFileObject prev = log.useSource(tree.sourcefile);
                        Env<AttrContext> topEnv = topLevelEnv(tree);
                        // 符号输入的第二阶段
                        // 类中的方法和成员变量将在第二个符号输入阶段进行处理
                        memberEnter.memberEnter(tree, topEnv);
                        log.useSource(prev);
                    }
                }
            }
        } finally {
            uncompleted = prevUncompleted;
            annotate.enterDone();
        }
    }
}
