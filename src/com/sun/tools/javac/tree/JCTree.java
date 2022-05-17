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

package com.sun.tools.javac.tree;

import com.sun.source.tree.*;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Scope.ImportScope;
import com.sun.tools.javac.code.Scope.StarImportScope;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.MethodSymbol;
import com.sun.tools.javac.code.Symbol.PackageSymbol;
import com.sun.tools.javac.code.Symbol.VarSymbol;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeKind;
import javax.tools.JavaFileObject;
import java.io.IOException;
import java.io.StringWriter;
import java.util.Map;
import java.util.Set;

/**
 * Root class for abstract syntax tree nodes. It provides definitions
 * for specific tree nodes as subclasses nested inside.
 *
 * <p>Each subclass is highly standardized.  It generally contains
 * only tree fields for the syntactic subcomponents of the node.  Some
 * classes that represent identifier uses or definitions also define a
 * Symbol field that denotes the represented identifier.  Classes for
 * non-local jumps also carry the jump target as a field.  The root
 * class Tree itself defines fields for the tree's type and position.
 * No other fields are kept in a tree node; instead parameters are
 * passed to methods accessing the node.
 *
 * <p>Except for the methods defined by com.sun.source, the only
 * method defined in subclasses is `visit' which applies a given
 * visitor to the tree. The actual tree processing is done by visitor
 * classes in other packages. The abstract class Visitor, as well as
 * an Factory interface for trees, are defined as inner classes in
 * Tree.
 *
 * <p>To avoid ambiguities with the Tree API in com.sun.source all sub
 * classes should, by convention, start with JC (javac).
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 *
 * @see TreeMaker
 * @see TreeInfo
 * @see TreeTranslator
 * @see Pretty
 */
public abstract class JCTree implements Tree, Cloneable, DiagnosticPosition {

    /* Tree tag values, identifying kinds of trees */

    /** Toplevel nodes, of type TopLevel, representing entire source files.
     */
    public static final int  TOPLEVEL = 1;

    /** Import clauses, of type Import.
     */
    public static final int IMPORT = TOPLEVEL + 1;

    /** Class definitions, of type ClassDef.
     */
    public static final int CLASSDEF = IMPORT + 1;

    /** Method definitions, of type MethodDef.
     */
    public static final int METHODDEF = CLASSDEF + 1;

    /** Variable definitions, of type VarDef.
     */
    public static final int VARDEF = METHODDEF + 1;

    /** The no-op statement ";", of type Skip
     */
    public static final int SKIP = VARDEF + 1;

    /** Blocks, of type Block.
     */
    public static final int BLOCK = SKIP + 1;

    /** Do-while loops, of type DoLoop.
     */
    public static final int DOLOOP = BLOCK + 1;

    /** While-loops, of type WhileLoop.
     */
    public static final int WHILELOOP = DOLOOP + 1;

    /** For-loops, of type ForLoop.
     */
    public static final int FORLOOP = WHILELOOP + 1;

    /** Foreach-loops, of type ForeachLoop.
     */
    public static final int FOREACHLOOP = FORLOOP + 1;

    /** Labelled statements, of type Labelled.
     */
    public static final int LABELLED = FOREACHLOOP + 1;

    /** Switch statements, of type Switch.
     */
    public static final int SWITCH = LABELLED + 1;

    /** Case parts in switch statements, of type Case.
     */
    public static final int CASE = SWITCH + 1;

    /** Synchronized statements, of type Synchonized.
     */
    public static final int SYNCHRONIZED = CASE + 1;

    /** Try statements, of type Try.
     */
    public static final int TRY = SYNCHRONIZED + 1;

    /** Catch clauses in try statements, of type Catch.
     */
    public static final int CATCH = TRY + 1;

    /** Conditional expressions, of type Conditional.
     */
    public static final int CONDEXPR = CATCH + 1;

    /** Conditional statements, of type If.
     */
    public static final int IF = CONDEXPR + 1;

    /** Expression statements, of type Exec.
     */
    public static final int EXEC = IF + 1;

    /** Break statements, of type Break.
     */
    public static final int BREAK = EXEC + 1;

    /** Continue statements, of type Continue.
     */
    public static final int CONTINUE = BREAK + 1;

    /** Return statements, of type Return.
     */
    public static final int RETURN = CONTINUE + 1;

    /** Throw statements, of type Throw.
     */
    public static final int THROW = RETURN + 1;

    /** Assert statements, of type Assert.
     */
    public static final int ASSERT = THROW + 1;

    /** Method invocation expressions, of type Apply.
     */
    public static final int APPLY = ASSERT + 1;

    /** Class instance creation expressions, of type NewClass.
     */
    public static final int NEWCLASS = APPLY + 1;

    /** Array creation expressions, of type NewArray.
     */
    public static final int NEWARRAY = NEWCLASS + 1;

    /** Parenthesized subexpressions, of type Parens.
     */
    public static final int PARENS = NEWARRAY + 1;

    /** Assignment expressions, of type Assign.
     */
    public static final int ASSIGN = PARENS + 1;

    /** Type cast expressions, of type TypeCast.
     */
    public static final int TYPECAST = ASSIGN + 1;

    /** Type test expressions, of type TypeTest.
     */
    public static final int TYPETEST = TYPECAST + 1;

    /** Indexed array expressions, of type Indexed.
     */
    public static final int INDEXED = TYPETEST + 1;

    /** Selections, of type Select.
     */
    public static final int SELECT = INDEXED + 1;

    /** Simple identifiers, of type Ident.
     */
    public static final int IDENT = SELECT + 1;

    /** Literals, of type Literal.
     */
    public static final int LITERAL = IDENT + 1;

    /** Basic type identifiers, of type TypeIdent.
     */
    public static final int TYPEIDENT = LITERAL + 1;

    /** Array types, of type TypeArray.
     * 可变长度参数
     */
    public static final int TYPEARRAY = TYPEIDENT + 1;

    /** Parameterized types, of type TypeApply.
     * 参数化类型
     */
    public static final int TYPEAPPLY = TYPEARRAY + 1;

    /** Union types, of type TypeUnion
     * 组合类型
     */
    public static final int TYPEUNION = TYPEAPPLY + 1;

    /** Formal type parameters, of type TypeParameter.
     */
    public static final int TYPEPARAMETER = TYPEUNION + 1;

    /** Type argument.
     */
    public static final int WILDCARD = TYPEPARAMETER + 1;

    /** Bound kind: extends, super, exact, or unbound
     */
    public static final int TYPEBOUNDKIND = WILDCARD + 1;

    /** metadata: Annotation.
     */
    public static final int ANNOTATION = TYPEBOUNDKIND + 1;

    /** metadata: Modifiers
     */
    public static final int MODIFIERS = ANNOTATION + 1;

    public static final int ANNOTATED_TYPE = MODIFIERS + 1;

    /** Error trees, of type Erroneous.
     */
    public static final int ERRONEOUS = ANNOTATED_TYPE + 1;

    /** Unary operators, of type Unary.
     */
    public static final int POS = ERRONEOUS + 1;             // +
    public static final int NEG = POS + 1;                   // -
    public static final int NOT = NEG + 1;                   // !
    public static final int COMPL = NOT + 1;                 // ~
    public static final int PREINC = COMPL + 1;              // ++ _
    public static final int PREDEC = PREINC + 1;             // -- _
    public static final int POSTINC = PREDEC + 1;            // _ ++
    public static final int POSTDEC = POSTINC + 1;           // _ --

    /** unary operator for null reference checks, only used internally.
     */
    public static final int NULLCHK = POSTDEC + 1;

    /** Binary operators, of type Binary.
     */
    public static final int OR = NULLCHK + 1;                // ||
    public static final int AND = OR + 1;                    // &&
    public static final int BITOR = AND + 1;                 // |
    public static final int BITXOR = BITOR + 1;              // ^
    public static final int BITAND = BITXOR + 1;             // &
    public static final int EQ = BITAND + 1;                 // ==
    public static final int NE = EQ + 1;                     // !=
    public static final int LT = NE + 1;                     // <
    public static final int GT = LT + 1;                     // >
    public static final int LE = GT + 1;                     // <=
    public static final int GE = LE + 1;                     // >=
    public static final int SL = GE + 1;                     // <<
    public static final int SR = SL + 1;                     // >>
    public static final int USR = SR + 1;                    // >>>
    public static final int PLUS = USR + 1;                  // +
    public static final int MINUS = PLUS + 1;                // -
    public static final int MUL = MINUS + 1;                 // *
    public static final int DIV = MUL + 1;                   // /
    public static final int MOD = DIV + 1;                   // %

    /** Assignment operators, of type Assignop.
     */
    public static final int BITOR_ASG = MOD + 1;             // |=
    public static final int BITXOR_ASG = BITOR_ASG + 1;      // ^=
    public static final int BITAND_ASG = BITXOR_ASG + 1;     // &=

    public static final int SL_ASG = SL + BITOR_ASG - BITOR; // <<=
    public static final int SR_ASG = SL_ASG + 1;             // >>=
    public static final int USR_ASG = SR_ASG + 1;            // >>>=
    public static final int PLUS_ASG = USR_ASG + 1;          // +=
    public static final int MINUS_ASG = PLUS_ASG + 1;        // -=
    public static final int MUL_ASG = MINUS_ASG + 1;         // *=
    public static final int DIV_ASG = MUL_ASG + 1;           // /=
    public static final int MOD_ASG = DIV_ASG + 1;           // %=

    /** A synthetic let expression, of type LetExpr.
     */
    public static final int LETEXPR = MOD_ASG + 1;           // ala scheme


    /** The offset between assignment operators and normal operators.
     */
    public static final int ASGOffset = BITOR_ASG - BITOR;

    /* The (encoded) position in the source file. @see util.Position.
     */
    public int pos;

    /* The type of this node.
     */
    // 当前树节点的类型
    public Type type;

    /* The tag of this node -- one of the constants declared above.
     */
    public abstract int getTag();

    /** Convert a tree to a pretty-printed string. */
    @Override
    public String toString() {
        StringWriter s = new StringWriter();
        try {
            new Pretty(s, false).printExpr(this);
        }
        catch (IOException e) {
            // should never happen, because StringWriter is defined
            // never to throw any IOExceptions
            throw new AssertionError(e);
        }
        return s.toString();
    }

    /** Set position field and return this tree.
     */
    public JCTree setPos(int pos) {
        this.pos = pos;
        return this;
    }

    /** Set type field and return this tree.
     */
    public JCTree setType(Type type) {
        this.type = type;
        return this;
    }

    /** Visit this tree with a given visitor.
     */
    public abstract void accept(Visitor v);

    public abstract <R,D> R accept(TreeVisitor<R,D> v, D d);

    /** Return a shallow copy of this tree.
     */
    @Override
    public Object clone() {
        try {
            return super.clone();
        } catch(CloneNotSupportedException e) {
            throw new RuntimeException(e);
        }
    }

    /** Get a default position for this tree node.
     */
    public DiagnosticPosition pos() {
        return this;
    }

    // for default DiagnosticPosition
    public JCTree getTree() {
        return this;
    }

    // for default DiagnosticPosition
    public int getStartPosition() {
        return TreeInfo.getStartPos(this);
    }

    // for default DiagnosticPosition
    public int getPreferredPosition() {
        return pos;
    }

    // for default DiagnosticPosition
    public int getEndPosition(Map<JCTree, Integer> endPosTable) {
        return TreeInfo.getEndPos(this, endPosTable);
    }

    /**
     * Everything in one source file is kept in a TopLevel structure.
     * @param pid              The tree representing the package clause.
     * @param sourcefile       The source file name.
     * @param defs             All definitions in this file (ClassDef, Import, and Skip)
     * @param packge           The package it belongs to.
     * @param namedImportScope A scope for all named imports.
     * @param starImportScope  A scope for all import-on-demands.
     * @param lineMap          Line starting positions, defined only
     *                         if option -g is set.
     * @param docComments      A hashtable that stores all documentation comments
     *                         indexed by the tree nodes they refer to.
     *                         defined only if option -s is set.
     * @param endPositions     A hashtable that stores ending positions of source
     *                         ranges indexed by the tree nodes they belong to.
     *                         Defined only if option -Xjcov is set.
     */
    // 每个编译单元（Compilation Unit）都是一个JCCompilationUnit对象。
    // 一般，一个Java源文件对应一个编译单元，
    // 如果一个Java源文件中定义了多个类，则这些类也属于同一个编译单元。
    // JCCompilationUnit对象是抽象语法树顶层的树节点，或者说是根节点
    // 一个编译单元由3部分构成：包声明、导入声明和类型声明
    public static class JCCompilationUnit extends JCTree implements CompilationUnitTree {
        // 保存多个包注解
        public List<JCAnnotation> packageAnnotations;
        // 保存包声明：表达式
        public JCExpression pid;
        // 保存导入声明及类声明
        // 保存在defs中的类型一定是顶层类或顶层接口
        public List<JCTree> defs;
        public JavaFileObject sourcefile;
        // 定义包符号
        public PackageSymbol packge;
        // 不同编译单元下的类在编译时可能会依赖同一个类型，
        // 那么最终填充到不同编译单元的namedImportScope变量中的ClassSymbol对象也会是同一个
        public ImportScope namedImportScope;
        // 导入声明导入的依赖对应的符号会填充starImportScope
        // 同一个编译单元中定义的所有顶层类也会填充到这个变量中
        public StarImportScope starImportScope;
        public long flags;
        public Position.LineMap lineMap = null;
        public Map<JCTree, String> docComments = null;
        public Map<JCTree, Integer> endPositions = null;
        protected JCCompilationUnit(List<JCAnnotation> packageAnnotations,
                        JCExpression pid,
                        List<JCTree> defs,
                        JavaFileObject sourcefile,
                        PackageSymbol packge,
                        ImportScope namedImportScope,
                        StarImportScope starImportScope) {
            this.packageAnnotations = packageAnnotations;
            this.pid = pid;
            this.defs = defs;
            this.sourcefile = sourcefile;
            this.packge = packge;
            this.namedImportScope = namedImportScope;
            this.starImportScope = starImportScope;
        }
        @Override
        public void accept(Visitor v) { v.visitTopLevel(this); }

        public Kind getKind() { return Kind.COMPILATION_UNIT; }
        public List<JCAnnotation> getPackageAnnotations() {
            return packageAnnotations;
        }
        public List<JCImport> getImports() {
            ListBuffer<JCImport> imports = new ListBuffer<JCImport>();
            for (JCTree tree : defs) {
                int tag = tree.getTag();
                if (tag == IMPORT)
                    imports.append((JCImport)tree);
                else if (tag != SKIP)
                    break;
            }
            return imports.toList();
        }
        public JCExpression getPackageName() { return pid; }
        public JavaFileObject getSourceFile() {
            return sourcefile;
        }
        public Position.LineMap getLineMap() {
            return lineMap;
        }
        public List<JCTree> getTypeDecls() {
            List<JCTree> typeDefs;
            for (typeDefs = defs; !typeDefs.isEmpty(); typeDefs = typeDefs.tail)
                if (typeDefs.head.getTag() != IMPORT)
                    break;
            return typeDefs;
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitCompilationUnit(this, d);
        }

        @Override
        public int getTag() {
            return TOPLEVEL;
        }
    }

    /**
     * An import clause.
     * @param qualid    The imported class(es).
     */
    // 导入声明对象
    /*
    导入的四种导入形式
    import chapter4.TestImportDecl;
    import chapter4.TestImportDecl.*;
    import static chapter4.TestImportDecl.StaticClass;
    import static chapter4.TestImportDecl.*;
     */
    public static class JCImport extends JCTree implements ImportTree {
        // 是否为静态导入声明
        public boolean staticImport;
        // 保存具体声明内容
        public JCTree qualid;
        protected JCImport(JCTree qualid, boolean importStatic) {
            this.qualid = qualid;
            this.staticImport = importStatic;
        }
        @Override
        public void accept(Visitor v) { v.visitImport(this); }

        public boolean isStatic() { return staticImport; }
        public JCTree getQualifiedIdentifier() { return qualid; }

        public Kind getKind() { return Kind.IMPORT; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitImport(this, d);
        }

        @Override
        public int getTag() {
            return IMPORT;
        }
    }

    // 所有能表示语句的类都继承了JCStatement抽象类
    public static abstract class JCStatement extends JCTree implements StatementTree {
        @Override
        public JCStatement setType(Type type) {
            super.setType(type);
            return this;
        }
        @Override
        public JCStatement setPos(int pos) {
            super.setPos(pos);
            return this;
        }
    }

    // 一个复杂表达式可由基本表达式和运算符构成，
    // 所有能表示表达式的类都会继承JCExpression抽象类
    public static abstract class JCExpression extends JCTree implements ExpressionTree {
        @Override
        public JCExpression setType(Type type) {
            super.setType(type);
            return this;
        }
        @Override
        public JCExpression setPos(int pos) {
            super.setPos(pos);
            return this;
        }
    }

    /**
     * A class definition.
     * @param modifiers the modifiers
     * @param name the name of the class
     * @param typarams formal class parameters
     * @param extending the classes this class extends
     * @param implementing the interfaces implemented by this class
     * @param defs all variables and methods defined in this class
     * @param sym the symbol
     */
    // 每个类型声明（Class Declaration）或者说类型定义都是一个JCClassDecl对象，
    // 包括接口、类，以及作为特殊接口的注解类和作为特殊类的枚举类
    // 注解类是接口，枚举类是类
    public static class JCClassDecl extends JCStatement implements ClassTree {
        // 区分类和接口，保存类和接口的修饰符
        public JCModifiers mods;
        public Name name;
        // 保存类型上声明的多个类型参数(泛型)
        public List<JCTypeParameter> typarams;
        public JCExpression extending;
        // 实现的接口
        public List<JCExpression> implementing;
        // 保存类内部的一些成员，成员变量、方法等
        public List<JCTree> defs;
        // 定义类符号
        public ClassSymbol sym;
        protected JCClassDecl(JCModifiers mods,
                           Name name,
                           List<JCTypeParameter> typarams,
                           JCExpression extending,
                           List<JCExpression> implementing,
                           List<JCTree> defs,
                           ClassSymbol sym)
        {
            this.mods = mods;
            this.name = name;
            this.typarams = typarams;
            this.extending = extending;
            this.implementing = implementing;
            this.defs = defs;
            this.sym = sym;
        }
        @Override
        public void accept(Visitor v) { v.visitClassDef(this); }

        public Kind getKind() {
            if ((mods.flags & Flags.ANNOTATION) != 0)
                return Kind.ANNOTATION_TYPE;
            else if ((mods.flags & Flags.INTERFACE) != 0)
                return Kind.INTERFACE;
            else if ((mods.flags & Flags.ENUM) != 0)
                return Kind.ENUM;
            else
                return Kind.CLASS;
        }

        public JCModifiers getModifiers() { return mods; }
        public Name getSimpleName() { return name; }
        public List<JCTypeParameter> getTypeParameters() {
            return typarams;
        }
        public JCTree getExtendsClause() { return extending; }
        public List<JCExpression> getImplementsClause() {
            return implementing;
        }
        public List<JCTree> getMembers() {
            return defs;
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitClass(this, d);
        }

        @Override
        public int getTag() {
            return CLASSDEF;
        }
    }

    /**
     * A method definition.
     * @param modifiers method modifiers
     * @param name method name
     * @param restype type of method return value
     * @param typarams type parameters
     * @param params value parameters
     * @param thrown exceptions thrown by this method
     * @param stats statements in the method
     * @param sym method symbol
     */
    // 每个方法都是一个JCMethodDecl对象，包括抽象方法和非抽象方法
    // 在类与枚举类、接口与注解类中定义的所有方法都用JCMethodDecl类来表示
    public static class JCMethodDecl extends JCTree implements MethodTree {
        public JCModifiers mods;
        public Name name;
        public JCExpression restype;
        public List<JCTypeParameter> typarams;
        public List<JCVariableDecl> params;
        public List<JCExpression> thrown;
        public JCBlock body;
        // 注解类方法中指定的默认值
        public JCExpression defaultValue; // for annotation types
        // 定义方法符号
        public MethodSymbol sym;
        protected JCMethodDecl(JCModifiers mods,
                            Name name,
                            JCExpression restype,
                            List<JCTypeParameter> typarams,
                            List<JCVariableDecl> params,
                            List<JCExpression> thrown,
                            JCBlock body,
                            JCExpression defaultValue,
                            MethodSymbol sym)
        {
            this.mods = mods;
            this.name = name;
            this.restype = restype;
            this.typarams = typarams;
            this.params = params;
            this.thrown = thrown;
            this.body = body;
            this.defaultValue = defaultValue;
            this.sym = sym;
        }
        @Override
        public void accept(Visitor v) { v.visitMethodDef(this); }

        public Kind getKind() { return Kind.METHOD; }
        public JCModifiers getModifiers() { return mods; }
        public Name getName() { return name; }
        public JCTree getReturnType() { return restype; }
        public List<JCTypeParameter> getTypeParameters() {
            return typarams;
        }
        public List<JCVariableDecl> getParameters() {
            return params;
        }
        public List<JCExpression> getThrows() {
            return thrown;
        }
        public JCBlock getBody() { return body; }
        public JCTree getDefaultValue() { // for annotation types
            return defaultValue;
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitMethod(this, d);
        }

        @Override
        public int getTag() {
            return METHODDEF;
        }
  }

    /**
     * A variable definition.
     * @param modifiers variable modifiers
     * @param name variable name
     * @param vartype type of the variable
     * @param init variables initial value
     * @param sym symbol
     */
    // 每个成员变量（Field）或局部变量（Variable）都是一个JCVariableDecl对象
    // 对于形式参数来说，虽然与块内声明的局部变量稍有不同，但都是局部变量
    public static class JCVariableDecl extends JCStatement implements VariableTree {
        // 变量的修饰符
        public JCModifiers mods;
        public Name name;
        // 变量声明的类型
        public JCExpression vartype;
        // 变量初始化值
        public JCExpression init;
        // 定义变量的符号
        public VarSymbol sym;
        protected JCVariableDecl(JCModifiers mods,
                         Name name,
                         JCExpression vartype,
                         JCExpression init,
                         VarSymbol sym) {
            this.mods = mods;
            this.name = name;
            this.vartype = vartype;
            this.init = init;
            this.sym = sym;
        }
        @Override
        public void accept(Visitor v) { v.visitVarDef(this); }

        public Kind getKind() { return Kind.VARIABLE; }
        public JCModifiers getModifiers() { return mods; }
        public Name getName() { return name; }
        public JCTree getType() { return vartype; }
        public JCExpression getInitializer() {
            return init;
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitVariable(this, d);
        }

        @Override
        public int getTag() {
            return VARDEF;
        }
    }

      /**
     * A no-op statement ";".
     */
      // JCSkip表示空语句（the empty statement），空语句中只是一个单独的分号
    public static class JCSkip extends JCStatement implements EmptyStatementTree {
        protected JCSkip() {
        }
        @Override
        public void accept(Visitor v) { v.visitSkip(this); }

        public Kind getKind() { return Kind.EMPTY_STATEMENT; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitEmptyStatement(this, d);
        }

        @Override
        public int getTag() {
            return SKIP;
        }
    }

    /**
     * A statement block.
     * @param stats statements
     * @param flags flags
     */
    // 除了类的body体外，每对花括号“{}”扩起来的块都是一个JCBlock对象
    // 块内一般都是一系列语句，所以为了处理方便，
    // 表示类声明的JCClassDecl类与表示变量声明的JCVariableDecl类也继承了JCStatement类，
    // 这样就可以和其他语句一样按顺序保存到stats列表中了
    public static class JCBlock extends JCStatement implements BlockTree {
        public long flags;
        // 有序的语句列表
        public List<JCStatement> stats;
        /** Position of closing brace, optional. */
        public int endpos = Position.NOPOS;
        protected JCBlock(long flags, List<JCStatement> stats) {
            this.stats = stats;
            this.flags = flags;
        }
        @Override
        public void accept(Visitor v) { v.visitBlock(this); }

        public Kind getKind() { return Kind.BLOCK; }
        public List<JCStatement> getStatements() {
            return stats;
        }
        public boolean isStatic() { return (flags & Flags.STATIC) != 0; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitBlock(this, d);
        }

        @Override
        public int getTag() {
            return BLOCK;
        }
    }

    /**
     * A do loop
     */
    // do-while循环
    public static class JCDoWhileLoop extends JCStatement implements DoWhileLoopTree {
        public JCStatement body;
        public JCExpression cond;
        protected JCDoWhileLoop(JCStatement body, JCExpression cond) {
            this.body = body;
            this.cond = cond;
        }
        @Override
        public void accept(Visitor v) { v.visitDoLoop(this); }

        public Kind getKind() { return Kind.DO_WHILE_LOOP; }
        public JCExpression getCondition() { return cond; }
        public JCStatement getStatement() { return body; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitDoWhileLoop(this, d);
        }

        @Override
        public int getTag() {
            return DOLOOP;
        }
    }

    /**
     * A while loop
     */
    // while循环
    public static class JCWhileLoop extends JCStatement implements WhileLoopTree {
        public JCExpression cond;
        public JCStatement body;
        protected JCWhileLoop(JCExpression cond, JCStatement body) {
            this.cond = cond;
            this.body = body;
        }
        @Override
        public void accept(Visitor v) { v.visitWhileLoop(this); }

        public Kind getKind() { return Kind.WHILE_LOOP; }
        public JCExpression getCondition() { return cond; }
        public JCStatement getStatement() { return body; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitWhileLoop(this, d);
        }

        @Override
        public int getTag() {
            return WHILELOOP;
        }
    }

    /**
     * A for loop.
     */
    // for循环
    // for(int i =0; i < 10; i++){}
    // 在数据流分时，任何for语句的tree.init和tree.cond肯定会执行，而tree.body和tree.step会选择执行
    public static class JCForLoop extends JCStatement implements ForLoopTree {
        // int i = 0
        public List<JCStatement> init;
        // int i < 10
        public JCExpression cond;
        // i ++
        public List<JCExpressionStatement> step;
        // {}
        public JCStatement body;
        protected JCForLoop(List<JCStatement> init,
                          JCExpression cond,
                          List<JCExpressionStatement> update,
                          JCStatement body)
        {
            this.init = init;
            this.cond = cond;
            this.step = update;
            this.body = body;
        }
        @Override
        public void accept(Visitor v) { v.visitForLoop(this); }

        public Kind getKind() { return Kind.FOR_LOOP; }
        public JCExpression getCondition() { return cond; }
        public JCStatement getStatement() { return body; }
        public List<JCStatement> getInitializer() {
            return init;
        }
        public List<JCExpressionStatement> getUpdate() {
            return step;
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitForLoop(this, d);
        }

        @Override
        public int getTag() {
            return FORLOOP;
        }
    }

    /**
     * The enhanced for loop.
     */
    // foreach循环
    // for(Integer i : List<Integer>){}
    public static class JCEnhancedForLoop extends JCStatement implements EnhancedForLoopTree {
        // Integer i
        public JCVariableDecl var;
        // List<Integer>
        public JCExpression expr;
        // {}
        public JCStatement body;
        protected JCEnhancedForLoop(JCVariableDecl var, JCExpression expr, JCStatement body) {
            this.var = var;
            this.expr = expr;
            this.body = body;
        }
        @Override
        public void accept(Visitor v) { v.visitForeachLoop(this); }

        public Kind getKind() { return Kind.ENHANCED_FOR_LOOP; }
        public JCVariableDecl getVariable() { return var; }
        public JCExpression getExpression() { return expr; }
        public JCStatement getStatement() { return body; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitEnhancedForLoop(this, d);
        }
        @Override
        public int getTag() {
            return FOREACHLOOP;
        }
    }

    /**
     * A labelled expression or statement.
     */
    // 带label标记的语句(地址回填用)
    public static class JCLabeledStatement extends JCStatement implements LabeledStatementTree {
        public Name label;
        public JCStatement body;
        protected JCLabeledStatement(Name label, JCStatement body) {
            this.label = label;
            this.body = body;
        }
        @Override
        public void accept(Visitor v) { v.visitLabelled(this); }
        public Kind getKind() { return Kind.LABELED_STATEMENT; }
        public Name getLabel() { return label; }
        public JCStatement getStatement() { return body; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitLabeledStatement(this, d);
        }
        @Override
        public int getTag() {
            return LABELLED;
        }
    }

    /**
     * A "switch ( ) { }" construction.
     */
    // switch
    public static class JCSwitch extends JCStatement implements SwitchTree {
        public JCExpression selector;
        public List<JCCase> cases;
        protected JCSwitch(JCExpression selector, List<JCCase> cases) {
            this.selector = selector;
            this.cases = cases;
        }
        @Override
        public void accept(Visitor v) { v.visitSwitch(this); }

        public Kind getKind() { return Kind.SWITCH; }
        public JCExpression getExpression() { return selector; }
        public List<JCCase> getCases() { return cases; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitSwitch(this, d);
        }
        @Override
        public int getTag() {
            return SWITCH;
        }
    }

    /**
     * A "case  :" of a switch.
     */
    // case
    public static class JCCase extends JCStatement implements CaseTree {
        // 保存具体label的值
        public JCExpression pat;
        public List<JCStatement> stats;
        protected JCCase(JCExpression pat, List<JCStatement> stats) {
            this.pat = pat;
            this.stats = stats;
        }
        @Override
        public void accept(Visitor v) { v.visitCase(this); }

        public Kind getKind() { return Kind.CASE; }
        public JCExpression getExpression() { return pat; }
        public List<JCStatement> getStatements() { return stats; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitCase(this, d);
        }
        @Override
        public int getTag() {
            return CASE;
        }
    }

    /**
     * A synchronized block.
     */
    // synchronized
    public static class JCSynchronized extends JCStatement implements SynchronizedTree {
        public JCExpression lock;
        public JCBlock body;
        protected JCSynchronized(JCExpression lock, JCBlock body) {
            this.lock = lock;
            this.body = body;
        }
        @Override
        public void accept(Visitor v) { v.visitSynchronized(this); }

        public Kind getKind() { return Kind.SYNCHRONIZED; }
        public JCExpression getExpression() { return lock; }
        public JCBlock getBlock() { return body; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitSynchronized(this, d);
        }
        @Override
        public int getTag() {
            return SYNCHRONIZED;
        }
    }

    /**
     * A "try { } catch ( ) { } finally { }" block.
     */
    // try { } catch ( ) { } finally { }
    public static class JCTry extends JCStatement implements TryTree {
        public JCBlock body;
        public List<JCCatch> catchers;
        public JCBlock finalizer;
        public List<JCTree> resources;
        protected JCTry(List<JCTree> resources,
                        JCBlock body,
                        List<JCCatch> catchers,
                        JCBlock finalizer) {
            this.body = body;
            this.catchers = catchers;
            this.finalizer = finalizer;
            this.resources = resources;
        }
        @Override
        public void accept(Visitor v) { v.visitTry(this); }

        public Kind getKind() { return Kind.TRY; }
        public JCBlock getBlock() { return body; }
        public List<JCCatch> getCatches() {
            return catchers;
        }
        public JCBlock getFinallyBlock() { return finalizer; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitTry(this, d);
        }
        @Override
        public List<? extends JCTree> getResources() {
            return resources;
        }
        @Override
        public int getTag() {
            return TRY;
        }
    }

    /**
     * A catch block.
     */
    // catch
    public static class JCCatch extends JCTree implements CatchTree {
        // cache(Exception e)
        public JCVariableDecl param;
        // {}
        public JCBlock body;
        protected JCCatch(JCVariableDecl param, JCBlock body) {
            this.param = param;
            this.body = body;
        }
        @Override
        public void accept(Visitor v) { v.visitCatch(this); }

        public Kind getKind() { return Kind.CATCH; }
        public JCVariableDecl getParameter() { return param; }
        public JCBlock getBlock() { return body; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitCatch(this, d);
        }
        @Override
        public int getTag() {
            return CATCH;
        }
    }

    /**
     * A ( ) ? ( ) : ( ) conditional expression
     */
    // 三元表达式
    public static class JCConditional extends JCExpression implements ConditionalExpressionTree {
        // 保存条件判断表达式
        public JCExpression cond;
        // 条件表达式为真时执行的表达式
        public JCExpression truepart;
        // 条件表达式为假时执行的表达式
        public JCExpression falsepart;
        protected JCConditional(JCExpression cond,
                              JCExpression truepart,
                              JCExpression falsepart)
        {
            this.cond = cond;
            this.truepart = truepart;
            this.falsepart = falsepart;
        }
        @Override
        public void accept(Visitor v) { v.visitConditional(this); }

        public Kind getKind() { return Kind.CONDITIONAL_EXPRESSION; }
        public JCExpression getCondition() { return cond; }
        public JCExpression getTrueExpression() { return truepart; }
        public JCExpression getFalseExpression() { return falsepart; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitConditionalExpression(this, d);
        }
        @Override
        public int getTag() {
            return CONDEXPR;
        }
    }

    /**
     * An "if ( ) { } else { }" block
     */
    // if语句在词法分析的过程中要解决的一个问题就是悬空else（dangling else）
    /*
        if(res1)
            if(res2){
                ...
            }
        else{
            ...
            }
            代码编写者的意图是，
            第1个if语句的条件判断表达式为false时执行else部分，但是很明显这个else是属于第2个if语句的一部分
     */
    public static class JCIf extends JCStatement implements IfTree {
        // 条件表达式部分
        public JCExpression cond;
        // if部分
        public JCStatement thenpart;
        // else部分
        public JCStatement elsepart;
        protected JCIf(JCExpression cond,
                     JCStatement thenpart,
                     JCStatement elsepart)
        {
            this.cond = cond;
            this.thenpart = thenpart;
            this.elsepart = elsepart;
        }
        @Override
        public void accept(Visitor v) { v.visitIf(this); }

        public Kind getKind() { return Kind.IF; }
        public JCExpression getCondition() { return cond; }
        public JCStatement getThenStatement() { return thenpart; }
        public JCStatement getElseStatement() { return elsepart; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitIf(this, d);
        }
        @Override
        public int getTag() {
            return IF;
        }
    }

    /**
     * an expression statement
     * @param expr expression structure
     */
    // JCExpressionStatement类可以将表达式转换为语句，
    // 因为类的body体或块内不允许直接包含表达式，
    // 所以如果有表达式出现，需要通过JCExpressionStatement类封装为语句
    public static class JCExpressionStatement extends JCStatement implements ExpressionStatementTree {
        // 被语句封装的表达式，类型为JCExpression
        public JCExpression expr;
        protected JCExpressionStatement(JCExpression expr)
        {
            this.expr = expr;
        }
        @Override
        public void accept(Visitor v) { v.visitExec(this); }

        public Kind getKind() { return Kind.EXPRESSION_STATEMENT; }
        public JCExpression getExpression() { return expr; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitExpressionStatement(this, d);
        }
        @Override
        public int getTag() {
            return EXEC;
        }
    }

    /**
     * A break from a loop or switch.
     */
    // break
    public static class JCBreak extends JCStatement implements BreakTree {
        public Name label;
        // 指向另一个label的标记
        public JCTree target;
        protected JCBreak(Name label, JCTree target) {
            this.label = label;
            this.target = target;
        }
        @Override
        public void accept(Visitor v) { v.visitBreak(this); }

        public Kind getKind() { return Kind.BREAK; }
        public Name getLabel() { return label; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitBreak(this, d);
        }
        @Override
        public int getTag() {
            return BREAK;
        }
    }

    /**
     * A continue of a loop.
     */
    // continue
    public static class JCContinue extends JCStatement implements ContinueTree {
        public Name label;
        // 指向另一个label的标记
        public JCTree target;
        protected JCContinue(Name label, JCTree target) {
            this.label = label;
            this.target = target;
        }
        @Override
        public void accept(Visitor v) { v.visitContinue(this); }

        public Kind getKind() { return Kind.CONTINUE; }
        public Name getLabel() { return label; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitContinue(this, d);
        }
        @Override
        public int getTag() {
            return CONTINUE;
        }
    }

    /**
     * A return statement.
     */
    // return
    public static class JCReturn extends JCStatement implements ReturnTree {
        public JCExpression expr;
        protected JCReturn(JCExpression expr) {
            this.expr = expr;
        }
        @Override
        public void accept(Visitor v) { v.visitReturn(this); }

        public Kind getKind() { return Kind.RETURN; }
        public JCExpression getExpression() { return expr; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitReturn(this, d);
        }
        @Override
        public int getTag() {
            return RETURN;
        }
    }

    /**
     * A throw statement.
     */
    // throw 树节点
    public static class JCThrow extends JCStatement implements ThrowTree {
        // 抛出的异常的表达式
        public JCExpression expr;
        protected JCThrow(JCTree expr) {
            this.expr = (JCExpression)expr;
        }
        @Override
        public void accept(Visitor v) { v.visitThrow(this); }

        public Kind getKind() { return Kind.THROW; }
        public JCExpression getExpression() { return expr; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitThrow(this, d);
        }
        @Override
        public int getTag() {
            return THROW;
        }
    }

    /**
     * An assert statement.
     */
    // assert
    public static class JCAssert extends JCStatement implements AssertTree {
        public JCExpression cond;
        public JCExpression detail;
        protected JCAssert(JCExpression cond, JCExpression detail) {
            this.cond = cond;
            this.detail = detail;
        }
        @Override
        public void accept(Visitor v) { v.visitAssert(this); }

        public Kind getKind() { return Kind.ASSERT; }
        public JCExpression getCondition() { return cond; }
        public JCExpression getDetail() { return detail; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitAssert(this, d);
        }
        @Override
        public int getTag() {
            return ASSERT;
        }
    }

    /**
     * A method invocation
     */
    // 方法调用
    public static class JCMethodInvocation extends JCExpression implements MethodInvocationTree {
        // 传递的实际类型参数列表
        public List<JCExpression> typeargs;
        // 指定要调用的方法，JCIdent、JCFieldAccess对象
        public JCExpression meth;
        // 方法调用的实参列表
        public List<JCExpression> args;
        public Type varargsElement;
        protected JCMethodInvocation(List<JCExpression> typeargs,
                        JCExpression meth,
                        List<JCExpression> args)
        {
            this.typeargs = (typeargs == null) ? List.<JCExpression>nil()
                                               : typeargs;
            this.meth = meth;
            this.args = args;
        }
        @Override
        public void accept(Visitor v) { v.visitApply(this); }

        public Kind getKind() { return Kind.METHOD_INVOCATION; }
        public List<JCExpression> getTypeArguments() {
            return typeargs;
        }
        public JCExpression getMethodSelect() { return meth; }
        public List<JCExpression> getArguments() {
            return args;
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitMethodInvocation(this, d);
        }
        @Override
        public JCMethodInvocation setType(Type type) {
            super.setType(type);
            return this;
        }
        @Override
        public int getTag() {
            return(APPLY);
        }
    }

    /**
     * A new(...) operation.
     */
    // ClassInstanceCreationExpression文法
        /*
        ClassInstanceCreationExpression:
          new TypeArgumentsopt TypeDeclSpecifier TypeArgumentsOrDiamondopt
                ( ArgumentListopt ) ClassBodyopt
          Primary . new TypeArgumentsopt Identifier TypeArgumentsOrDiamondopt
                 ( ArgumentListopt ) ClassBodyopt
        TypeArgumentsOrDiamond:
          TypeArguments
          <>
        ArgumentList:
          Expression
          ArgumentList , Expression
         */
    public static class JCNewClass extends JCExpression implements NewClassTree {
        // encl表示文法中的Primary，所以encl可能是Primary文法产生式中的任何一个表达式
        public JCExpression encl;
        public List<JCExpression> typeargs;
        // clazz表示ClassInstanceCreationExpression文法中
        // 第1个文法产生式中的TypeDeclSpecifier或第2个文法产生式中的Identifier，
        // 所以具体的类型可能为JCFieldAccess或JCIdent；
        public JCExpression clazz;
        public List<JCExpression> args;
        // def保存ClassBody部分，如果当前创建的是匿名类对象，那么def不为空
        public JCClassDecl def;
        // 构造器的符号
        public Symbol constructor;
        public Type varargsElement;
        public Type constructorType;
        protected JCNewClass(JCExpression encl,
                           List<JCExpression> typeargs,
                           JCExpression clazz,
                           List<JCExpression> args,
                           JCClassDecl def)
        {
            this.encl = encl;
            this.typeargs = (typeargs == null) ? List.<JCExpression>nil()
                                               : typeargs;
            this.clazz = clazz;
            this.args = args;
            this.def = def;
        }
        @Override
        public void accept(Visitor v) { v.visitNewClass(this); }

        public Kind getKind() { return Kind.NEW_CLASS; }
        public JCExpression getEnclosingExpression() { // expr.new C< ... > ( ... )
            return encl;
        }
        public List<JCExpression> getTypeArguments() {
            return typeargs;
        }
        public JCExpression getIdentifier() { return clazz; }
        public List<JCExpression> getArguments() {
            return args;
        }
        public JCClassDecl getClassBody() { return def; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitNewClass(this, d);
        }
        @Override
        public int getTag() {
            return NEWCLASS;
        }
    }

    /**
     * A new[...] operation.
     */
    // 新建数组
        /*
        int[][] a = new int[2][4];
        int[] b = new int[] { 1, 2 };
         */
    public static class JCNewArray extends JCExpression implements NewArrayTree {
        // 保存数组元素的类型
        public JCExpression elemtype;
        // 每个维度的大小
        public List<JCExpression> dims;
        // 元素初始化部分
        public List<JCExpression> elems;
        protected JCNewArray(JCExpression elemtype,
                           List<JCExpression> dims,
                           List<JCExpression> elems)
        {
            this.elemtype = elemtype;
            this.dims = dims;
            this.elems = elems;
        }
        @Override
        public void accept(Visitor v) { v.visitNewArray(this); }

        public Kind getKind() { return Kind.NEW_ARRAY; }
        public JCExpression getType() { return elemtype; }
        public List<JCExpression> getDimensions() {
            return dims;
        }
        public List<JCExpression> getInitializers() {
            return elems;
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitNewArray(this, d);
        }
        @Override
        public int getTag() {
            return NEWARRAY;
        }
    }

    /**
     * A parenthesized subexpression ( ... )
     */
    // 有括号的表达式使用JCParens类来表示
    public static class JCParens extends JCExpression implements ParenthesizedTree {
        // expr保存括号内的表达式内容
        public JCExpression expr;
        protected JCParens(JCExpression expr) {
            this.expr = expr;
        }
        @Override
        public void accept(Visitor v) { v.visitParens(this); }

        public Kind getKind() { return Kind.PARENTHESIZED; }
        public JCExpression getExpression() { return expr; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitParenthesized(this, d);
        }
        @Override
        public int getTag() {
            return PARENS;
        }
    }

    /**
     * A assignment with "=".
     */
    // 赋值运算符 =
    public static class JCAssign extends JCExpression implements AssignmentTree {
        // 表示运算符"+"的左操作数，可以为表达式
        public JCExpression lhs;
        // 表示运算符"+"的右操作数，可以为表达式
        public JCExpression rhs;
        protected JCAssign(JCExpression lhs, JCExpression rhs) {
            this.lhs = lhs;
            this.rhs = rhs;
        }
        @Override
        public void accept(Visitor v) { v.visitAssign(this); }

        public Kind getKind() { return Kind.ASSIGNMENT; }
        public JCExpression getVariable() { return lhs; }
        public JCExpression getExpression() { return rhs; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitAssignment(this, d);
        }
        @Override
        public int getTag() {
            return ASSIGN;
        }
    }

    /**
     * An assignment with "+=", "|=" ...
     */
    // 复合赋值运算符
    public static class JCAssignOp extends JCExpression implements CompoundAssignmentTree {
        // opcode的值表示不同的复合赋值运算符，这些值已经在JCTree类中预先进行了定义
        private int opcode;
        // 复合赋值运算符的左操作数，可以为表达式
        public JCExpression lhs;
        // 复合赋值运算符的右操作数，可以为表达式
        public JCExpression rhs;
        // 操作符的符号
        public Symbol operator;
        protected JCAssignOp(int opcode, JCTree lhs, JCTree rhs, Symbol operator) {
            this.opcode = opcode;
            this.lhs = (JCExpression)lhs;
            this.rhs = (JCExpression)rhs;
            this.operator = operator;
        }
        @Override
        public void accept(Visitor v) { v.visitAssignop(this); }

        public Kind getKind() { return TreeInfo.tagToKind(getTag()); }
        public JCExpression getVariable() { return lhs; }
        public JCExpression getExpression() { return rhs; }
        public Symbol getOperator() {
            return operator;
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitCompoundAssignment(this, d);
        }
        @Override
        public int getTag() {
            return opcode;
        }
    }

    /**
     * A unary operation.
     */
    // 一元表达式
    public static class JCUnary extends JCExpression implements UnaryTree {
        // opcode的值表示不同的一元运算符
        // @see com/sun/tools/javac/tree/JCTree.java POS
        private int opcode;
        // 一元运算符的操作数，可以为基本类型和引用类型
        public JCExpression arg;
        // 操作符的符号
        public Symbol operator;
        protected JCUnary(int opcode, JCExpression arg) {
            this.opcode = opcode;
            this.arg = arg;
        }
        @Override
        public void accept(Visitor v) { v.visitUnary(this); }

        public Kind getKind() { return TreeInfo.tagToKind(getTag()); }
        public JCExpression getExpression() { return arg; }
        public Symbol getOperator() {
            return operator;
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitUnary(this, d);
        }
        @Override
        public int getTag() {
            return opcode;
        }

        public void setTag(int tag) {
            opcode = tag;
        }
    }

    /**
     * A binary operation.
     */
    // 二元运算符
    public static class JCBinary extends JCExpression implements BinaryTree {
        // 表示二元运算符 com/sun/tools/javac/tree/JCTree.java DIV
        private int opcode;
        // 二元表达式左边的表达式
        public JCExpression lhs;
        // 二元表达式右边的表达式
        public JCExpression rhs;
        // 操作符的符号
        public Symbol operator;
        protected JCBinary(int opcode,
                         JCExpression lhs,
                         JCExpression rhs,
                         Symbol operator) {
            this.opcode = opcode;
            this.lhs = lhs;
            this.rhs = rhs;
            this.operator = operator;
        }
        @Override
        public void accept(Visitor v) { v.visitBinary(this); }

        public Kind getKind() { return TreeInfo.tagToKind(getTag()); }
        public JCExpression getLeftOperand() { return lhs; }
        public JCExpression getRightOperand() { return rhs; }
        public Symbol getOperator() {
            return operator;
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitBinary(this, d);
        }
        @Override
        public int getTag() {
            return opcode;
        }
    }

    /**
     * A type cast.
     */
    // 强制类型转换
    public static class JCTypeCast extends JCExpression implements TypeCastTree {
        // 表示转换目标基本类型或引用类型
        // 可能为：可能为表示基本类型的JCPrimitiveTypeTree，或表示引用类型的JCTypeApply、JCIdent或JCFieldAccess
        public JCTree clazz;
        public JCExpression expr;
        protected JCTypeCast(JCTree clazz, JCExpression expr) {
            this.clazz = clazz;
            this.expr = expr;
        }
        @Override
        public void accept(Visitor v) { v.visitTypeCast(this); }

        public Kind getKind() { return Kind.TYPE_CAST; }
        public JCTree getType() { return clazz; }
        public JCExpression getExpression() { return expr; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitTypeCast(this, d);
        }
        @Override
        public int getTag() {
            return TYPECAST;
        }
    }

    /**
     * A type test.
     */
    // instanceof 运算符
    public static class JCInstanceOf extends JCExpression implements InstanceOfTree {
        // 表示instanceof运算符左侧的操作数，可以为表达式
        public JCExpression expr;
        // 表示instanceof运算符右侧的操作数，一定为引用类型
        // 具体的类型可能是JCIdent或JCFieldAccess
        public JCTree clazz;
        protected JCInstanceOf(JCExpression expr, JCTree clazz) {
            this.expr = expr;
            this.clazz = clazz;
        }
        @Override
        public void accept(Visitor v) { v.visitTypeTest(this); }

        public Kind getKind() { return Kind.INSTANCE_OF; }
        public JCTree getType() { return clazz; }
        public JCExpression getExpression() { return expr; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitInstanceOf(this, d);
        }
        @Override
        public int getTag() {
            return TYPETEST;
        }
    }

    /**
     * An array selection
     */
    // 访问数组
    public static class JCArrayAccess extends JCExpression implements ArrayAccessTree {
        // 数组对象
        public JCExpression indexed;
        // 要访问元素的下标
        public JCExpression index;
        protected JCArrayAccess(JCExpression indexed, JCExpression index) {
            this.indexed = indexed;
            this.index = index;
        }
        @Override
        public void accept(Visitor v) { v.visitIndexed(this); }

        public Kind getKind() { return Kind.ARRAY_ACCESS; }
        public JCExpression getExpression() { return indexed; }
        public JCExpression getIndex() { return index; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitArrayAccess(this, d);
        }
        @Override
        public int getTag() {
            return INDEXED;
        }
    }

    /**
     * Selects through packages and classes
     * @param selected selected Tree hierarchie
     * @param selector name of field to select thru
     * @param sym symbol of the selected class
     */
    // 类型声明：java.lang.List
    // FieldAccess文法产生式使用JCFieldAccess类来表
    // JCFieldAccess类除了表示FieldAccess外，还可以表示Type.class、void.class和ClassName.this
    public static class JCFieldAccess extends JCExpression implements MemberSelectTree {
        public JCExpression selected;
        public Name name;
        // 变量的符号
        public Symbol sym;
        protected JCFieldAccess(JCExpression selected, Name name, Symbol sym) {
            this.selected = selected;
            this.name = name;
            this.sym = sym;
        }
        @Override
        public void accept(Visitor v) { v.visitSelect(this); }

        public Kind getKind() { return Kind.MEMBER_SELECT; }
        public JCExpression getExpression() { return selected; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitMemberSelect(this, d);
        }
        public Name getIdentifier() { return name; }
        @Override
        public int getTag() {
            return SELECT;
        }
    }

    /**
     * An identifier
     * @param idname the name
     * @param sym the symbol
     */
    // this关键字使用JCIdent类来表示
    public static class JCIdent extends JCExpression implements IdentifierTree {
        // name的值就是this
        public Name name;
        // 变量的符号
        public Symbol sym;
        protected JCIdent(Name name, Symbol sym) {
            this.name = name;
            this.sym = sym;
        }
        @Override
        public void accept(Visitor v) { v.visitIdent(this); }

        public Kind getKind() { return Kind.IDENTIFIER; }
        public Name getName() { return name; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitIdentifier(this, d);
        }
        public int getTag() {
            return IDENT;
        }
    }

    /**
     * A constant value given literally.
     * @param value value representation
     */
    // 字面量都是一个JCLiteral对象
    public static class JCLiteral extends JCExpression implements LiteralTree {
        // 字面量类型
        // @see com/sun/tools/javac/code/TypeTags.java
        public int typetag;
        // 具体的字面量
        public Object value;
        /*
        int a = 1;
        long b = 2L;
        float c = 3f;
        double d =4d;
        Object e = null;
        String f = "aa";
        整数1的typetag值为TypeTags.INT；
        2L的typetag值为TypeTags.Long；
        3f的typetag值为TypeTags.FLOAT；
        4d的typetag值为TypeTags.DOUBLE；
        null的typetag值为TypeTags.BOT；
        "aa"的typetag值为TypeTags.CLASS
         */
        protected JCLiteral(int typetag, Object value) {
            this.typetag = typetag;
            this.value = value;
        }
        @Override
        public void accept(Visitor v) { v.visitLiteral(this); }

        public Kind getKind() {
            switch (typetag) {
            case TypeTags.INT:
                return Kind.INT_LITERAL;
            case TypeTags.LONG:
                return Kind.LONG_LITERAL;
            case TypeTags.FLOAT:
                return Kind.FLOAT_LITERAL;
            case TypeTags.DOUBLE:
                return Kind.DOUBLE_LITERAL;
            case TypeTags.BOOLEAN:
                return Kind.BOOLEAN_LITERAL;
            case TypeTags.CHAR:
                return Kind.CHAR_LITERAL;
            case TypeTags.CLASS:
                return Kind.STRING_LITERAL;
            case TypeTags.BOT:
                return Kind.NULL_LITERAL;
            default:
                throw new AssertionError("unknown literal kind " + this);
            }
        }
        public Object getValue() {
            switch (typetag) {
                case TypeTags.BOOLEAN:
                    int bi = (Integer) value;
                    return (bi != 0);
                case TypeTags.CHAR:
                    int ci = (Integer) value;
                    char c = (char) ci;
                    if (c != ci)
                        throw new AssertionError("bad value for char literal");
                    return c;
                default:
                    return value;
            }
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitLiteral(this, d);
        }
        @Override
        public JCLiteral setType(Type type) {
            super.setType(type);
            return this;
        }
        @Override
        public int getTag() {
            return LITERAL;
        }
    }

    /**
     * Identifies a basic type.
     * @param tag the basic type id
     * @see TypeTags
     */
    // 基本类型
    public static class JCPrimitiveTypeTree extends JCExpression implements PrimitiveTypeTree {
        // 具体的类型
        public int typetag;
        protected JCPrimitiveTypeTree(int typetag) {
            this.typetag = typetag;
        }
        @Override
        public void accept(Visitor v) { v.visitTypeIdent(this); }

        public Kind getKind() { return Kind.PRIMITIVE_TYPE; }
        public TypeKind getPrimitiveTypeKind() {
            switch (typetag) {
            case TypeTags.BOOLEAN:
                return TypeKind.BOOLEAN;
            case TypeTags.BYTE:
                return TypeKind.BYTE;
            case TypeTags.SHORT:
                return TypeKind.SHORT;
            case TypeTags.INT:
                return TypeKind.INT;
            case TypeTags.LONG:
                return TypeKind.LONG;
            case TypeTags.CHAR:
                return TypeKind.CHAR;
            case TypeTags.FLOAT:
                return TypeKind.FLOAT;
            case TypeTags.DOUBLE:
                return TypeKind.DOUBLE;
            case TypeTags.VOID:
                return TypeKind.VOID;
            default:
                throw new AssertionError("unknown primitive type " + this);
            }
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitPrimitiveType(this, d);
        }
        @Override
        public int getTag() {
            return TYPEIDENT;
        }
    }

    /**
     * An array type, A[]
     */
    // 数组类型的参数化类型
    public static class JCArrayTypeTree extends JCExpression implements ArrayTypeTree {
        // elemtype保存组成数组的元素类型
        // 如果当前是一个二维或多维数组，则elemtype也是一个JCArrayTypeTree对象
        public JCExpression elemtype;
        protected JCArrayTypeTree(JCExpression elemtype) {
            this.elemtype = elemtype;
        }
        @Override
        public void accept(Visitor v) { v.visitTypeArray(this); }

        public Kind getKind() { return Kind.ARRAY_TYPE; }
        public JCTree getType() { return elemtype; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitArrayType(this, d);
        }
        @Override
        public int getTag() {
            return TYPEARRAY;
        }
    }

    /**
     * A parameterized type, T<...>
     */
    // 举个例子

    /*
     来源：com.sun.tools.javac.util.List
      public class List<A> extends AbstractCollection<A> implements java.util.List<A> {
      public List<A> tail;
        ...
      }
      父类AbstractCollection<A>与实现接口java.util.List<A>都是参数化类型，通过JCTypeApply对象来表示
      而当前定义的类List<A>并不是参数化类型，通过JCClassDecl对象来表示
      */
    // 参数化类型：java.lang.List<String>
    public static class JCTypeApply extends JCExpression implements ParameterizedTypeTree {
        // clazz的具体类型可能为JCIdent或JCFieldAccess
        public JCExpression clazz;
        // 多个实际类型参数列表
        public List<JCExpression> arguments;
        protected JCTypeApply(JCExpression clazz, List<JCExpression> arguments) {
            this.clazz = clazz;
            this.arguments = arguments;
        }
        @Override
        public void accept(Visitor v) { v.visitTypeApply(this); }

        public Kind getKind() { return Kind.PARAMETERIZED_TYPE; }
        public JCTree getType() { return clazz; }
        public List<JCExpression> getTypeArguments() {
            return arguments;
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitParameterizedType(this, d);
        }
        @Override
        public int getTag() {
            return TYPEAPPLY;
        }
    }

    /**
     * A union type, T1 | T2 | ... Tn (used in multicatch statements)
     */
    /*
    cache(AException | BException | CException e)
     */
    //异常参数
    public static class JCTypeUnion extends JCExpression implements UnionTypeTree {
        // 异常参数列表
        public List<JCExpression> alternatives;

        protected JCTypeUnion(List<JCExpression> components) {
            this.alternatives = components;
        }
        @Override
        public void accept(Visitor v) { v.visitTypeUnion(this); }

        public Kind getKind() { return Kind.UNION_TYPE; }

        public List<JCExpression> getTypeAlternatives() {
            return alternatives;
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitUnionType(this, d);
        }
        @Override
        public int getTag() {
            return TYPEUNION;
        }
    }

    /**
     * A formal class parameter.
     * @param name name
     * @param bounds bounds
     */
    // 类型参数对象，泛型
    // 每个形式类型参数都是一个JCTypeParameter对象
    // 可以表示类型（接口或类）或者方法声明的类型参数
    public static class JCTypeParameter extends JCTree implements TypeParameterTree {
        // 保存类型参数中类型变量的名称
        public Name name;
        // bounds保存类型变量的上界，可以有多个(泛型)
        public List<JCExpression> bounds;
        protected JCTypeParameter(Name name, List<JCExpression> bounds) {
            this.name = name;
            this.bounds = bounds;
        }
        @Override
        public void accept(Visitor v) { v.visitTypeParameter(this); }

        public Kind getKind() { return Kind.TYPE_PARAMETER; }
        public Name getName() { return name; }
        public List<JCExpression> getBounds() {
            return bounds;
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitTypeParameter(this, d);
        }
        @Override
        public int getTag() {
            return TYPEPARAMETER;
        }
    }

    // 通配符类型
    public static class JCWildcard extends JCExpression implements WildcardTree {
        // 保存通配符的类型
        public TypeBoundKind kind;
        // 通配符类型上界或下界
        public JCTree inner;
        protected JCWildcard(TypeBoundKind kind, JCTree inner) {
            kind.getClass(); // null-check
            this.kind = kind;
            this.inner = inner;
        }
        @Override
        public void accept(Visitor v) { v.visitWildcard(this); }

        public Kind getKind() {
            switch (kind.kind) {
            case UNBOUND:
                return Kind.UNBOUNDED_WILDCARD;
            case EXTENDS:
                return Kind.EXTENDS_WILDCARD;
            case SUPER:
                return Kind.SUPER_WILDCARD;
            default:
                throw new AssertionError("Unknown wildcard bound " + kind);
            }
        }
        public JCTree getBound() { return inner; }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitWildcard(this, d);
        }
        @Override
        public int getTag() {
            return WILDCARD;
        }
    }

    // 通配符的类型
    public static class TypeBoundKind extends JCTree {
        // 通配符的类型
        public BoundKind kind;
        protected TypeBoundKind(BoundKind kind) {
            this.kind = kind;
        }
        @Override
        public void accept(Visitor v) { v.visitTypeBoundKind(this); }

        public Kind getKind() {
            throw new AssertionError("TypeBoundKind is not part of a public API");
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            throw new AssertionError("TypeBoundKind is not part of a public API");
        }
        @Override
        public int getTag() {
            return TYPEBOUNDKIND;
        }
    }

    // 注解表达式
    public static class JCAnnotation extends JCExpression implements AnnotationTree {
        // 保存的注解类型
        public JCTree annotationType;
        // 保存多个注解参数
        public List<JCExpression> args;
        protected JCAnnotation(JCTree annotationType, List<JCExpression> args) {
            this.annotationType = annotationType;
            this.args = args;
        }
        @Override
        public void accept(Visitor v) { v.visitAnnotation(this); }

        public Kind getKind() { return Kind.ANNOTATION; }
        public JCTree getAnnotationType() { return annotationType; }
        public List<JCExpression> getArguments() {
            return args;
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitAnnotation(this, d);
        }
        @Override
        public int getTag() {
            return ANNOTATION;
        }
    }

    // 表示修饰符，如public、abstract和native等，甚至还能表示注解
    public static class JCModifiers extends JCTree implements com.sun.source.tree.ModifiersTree {
        // 保存修饰符
        // @see src/com/sun/tools/javac/code/Flags.java
        public long flags;
        // 保存注解信息
        public List<JCAnnotation> annotations;
        protected JCModifiers(long flags, List<JCAnnotation> annotations) {
            this.flags = flags;
            this.annotations = annotations;
        }
        @Override
        public void accept(Visitor v) { v.visitModifiers(this); }

        public Kind getKind() { return Kind.MODIFIERS; }
        public Set<Modifier> getFlags() {
            return Flags.asModifierSet(flags);
        }
        public List<JCAnnotation> getAnnotations() {
            return annotations;
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitModifiers(this, d);
        }
        @Override
        public int getTag() {
            return MODIFIERS;
        }
    }

    public static class JCErroneous extends JCExpression
            implements com.sun.source.tree.ErroneousTree {
        public List<? extends JCTree> errs;
        protected JCErroneous(List<? extends JCTree> errs) {
            this.errs = errs;
        }
        @Override
        public void accept(Visitor v) { v.visitErroneous(this); }

        public Kind getKind() { return Kind.ERRONEOUS; }

        public List<? extends JCTree> getErrorTrees() {
            return errs;
        }

        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            return v.visitErroneous(this, d);
        }
        @Override
        public int getTag() {
            return ERRONEOUS;
        }
    }

    /** (let int x = 3; in x+2) */
    public static class LetExpr extends JCExpression {
        public List<JCVariableDecl> defs;
        public JCTree expr;
        protected LetExpr(List<JCVariableDecl> defs, JCTree expr) {
            this.defs = defs;
            this.expr = expr;
        }
        @Override
        public void accept(Visitor v) { v.visitLetExpr(this); }

        public Kind getKind() {
            throw new AssertionError("LetExpr is not part of a public API");
        }
        @Override
        public <R,D> R accept(TreeVisitor<R,D> v, D d) {
            throw new AssertionError("LetExpr is not part of a public API");
        }
        @Override
        public int getTag() {
            return LETEXPR;
        }
    }

    /** An interface for tree factories
     */
    public interface Factory {
        JCCompilationUnit TopLevel(List<JCAnnotation> packageAnnotations,
                                   JCExpression pid,
                                   List<JCTree> defs);
        JCImport Import(JCTree qualid, boolean staticImport);
        JCClassDecl ClassDef(JCModifiers mods,
                          Name name,
                          List<JCTypeParameter> typarams,
                          JCExpression extending,
                          List<JCExpression> implementing,
                          List<JCTree> defs);
        JCMethodDecl MethodDef(JCModifiers mods,
                            Name name,
                            JCExpression restype,
                            List<JCTypeParameter> typarams,
                            List<JCVariableDecl> params,
                            List<JCExpression> thrown,
                            JCBlock body,
                            JCExpression defaultValue);
        JCVariableDecl VarDef(JCModifiers mods,
                      Name name,
                      JCExpression vartype,
                      JCExpression init);
        JCSkip Skip();
        JCBlock Block(long flags, List<JCStatement> stats);
        JCDoWhileLoop DoLoop(JCStatement body, JCExpression cond);
        JCWhileLoop WhileLoop(JCExpression cond, JCStatement body);
        JCForLoop ForLoop(List<JCStatement> init,
                        JCExpression cond,
                        List<JCExpressionStatement> step,
                        JCStatement body);
        JCEnhancedForLoop ForeachLoop(JCVariableDecl var, JCExpression expr, JCStatement body);
        JCLabeledStatement Labelled(Name label, JCStatement body);
        JCSwitch Switch(JCExpression selector, List<JCCase> cases);
        JCCase Case(JCExpression pat, List<JCStatement> stats);
        JCSynchronized Synchronized(JCExpression lock, JCBlock body);
        JCTry Try(JCBlock body, List<JCCatch> catchers, JCBlock finalizer);
        JCTry Try(List<JCTree> resources,
                  JCBlock body,
                  List<JCCatch> catchers,
                  JCBlock finalizer);
        JCCatch Catch(JCVariableDecl param, JCBlock body);
        JCConditional Conditional(JCExpression cond,
                                JCExpression thenpart,
                                JCExpression elsepart);
        JCIf If(JCExpression cond, JCStatement thenpart, JCStatement elsepart);
        JCExpressionStatement Exec(JCExpression expr);
        JCBreak Break(Name label);
        JCContinue Continue(Name label);
        JCReturn Return(JCExpression expr);
        JCThrow Throw(JCTree expr);
        JCAssert Assert(JCExpression cond, JCExpression detail);
        JCMethodInvocation Apply(List<JCExpression> typeargs,
                    JCExpression fn,
                    List<JCExpression> args);
        JCNewClass NewClass(JCExpression encl,
                          List<JCExpression> typeargs,
                          JCExpression clazz,
                          List<JCExpression> args,
                          JCClassDecl def);
        JCNewArray NewArray(JCExpression elemtype,
                          List<JCExpression> dims,
                          List<JCExpression> elems);
        JCParens Parens(JCExpression expr);
        JCAssign Assign(JCExpression lhs, JCExpression rhs);
        JCAssignOp Assignop(int opcode, JCTree lhs, JCTree rhs);
        JCUnary Unary(int opcode, JCExpression arg);
        JCBinary Binary(int opcode, JCExpression lhs, JCExpression rhs);
        JCTypeCast TypeCast(JCTree expr, JCExpression type);
        JCInstanceOf TypeTest(JCExpression expr, JCTree clazz);
        JCArrayAccess Indexed(JCExpression indexed, JCExpression index);
        JCFieldAccess Select(JCExpression selected, Name selector);
        JCIdent Ident(Name idname);
        JCLiteral Literal(int tag, Object value);
        JCPrimitiveTypeTree TypeIdent(int typetag);
        JCArrayTypeTree TypeArray(JCExpression elemtype);
        JCTypeApply TypeApply(JCExpression clazz, List<JCExpression> arguments);
        JCTypeParameter TypeParameter(Name name, List<JCExpression> bounds);
        JCWildcard Wildcard(TypeBoundKind kind, JCTree type);
        TypeBoundKind TypeBoundKind(BoundKind kind);
        JCAnnotation Annotation(JCTree annotationType, List<JCExpression> args);
        JCModifiers Modifiers(long flags, List<JCAnnotation> annotations);
        JCErroneous Erroneous(List<? extends JCTree> errs);
        LetExpr LetExpr(List<JCVariableDecl> defs, JCTree expr);
    }

    /** A generic visitor class for trees.
     */
    // 访问者模式
    // 访问者
    public static abstract class Visitor {
        public void visitTopLevel(JCCompilationUnit that)    { visitTree(that); }
        public void visitImport(JCImport that)               { visitTree(that); }
        public void visitClassDef(JCClassDecl that)          { visitTree(that); }
        public void visitMethodDef(JCMethodDecl that)        { visitTree(that); }
        public void visitVarDef(JCVariableDecl that)         { visitTree(that); }
        public void visitSkip(JCSkip that)                   { visitTree(that); }
        public void visitBlock(JCBlock that)                 { visitTree(that); }
        public void visitDoLoop(JCDoWhileLoop that)          { visitTree(that); }
        public void visitWhileLoop(JCWhileLoop that)         { visitTree(that); }
        public void visitForLoop(JCForLoop that)             { visitTree(that); }
        public void visitForeachLoop(JCEnhancedForLoop that) { visitTree(that); }
        public void visitLabelled(JCLabeledStatement that)   { visitTree(that); }
        public void visitSwitch(JCSwitch that)               { visitTree(that); }
        public void visitCase(JCCase that)                   { visitTree(that); }
        public void visitSynchronized(JCSynchronized that)   { visitTree(that); }
        public void visitTry(JCTry that)                     { visitTree(that); }
        public void visitCatch(JCCatch that)                 { visitTree(that); }
        public void visitConditional(JCConditional that)     { visitTree(that); }
        public void visitIf(JCIf that)                       { visitTree(that); }
        public void visitExec(JCExpressionStatement that)    { visitTree(that); }
        public void visitBreak(JCBreak that)                 { visitTree(that); }
        public void visitContinue(JCContinue that)           { visitTree(that); }
        public void visitReturn(JCReturn that)               { visitTree(that); }
        public void visitThrow(JCThrow that)                 { visitTree(that); }
        public void visitAssert(JCAssert that)               { visitTree(that); }
        public void visitApply(JCMethodInvocation that)      { visitTree(that); }
        public void visitNewClass(JCNewClass that)           { visitTree(that); }
        public void visitNewArray(JCNewArray that)           { visitTree(that); }
        public void visitParens(JCParens that)               { visitTree(that); }
        public void visitAssign(JCAssign that)               { visitTree(that); }
        public void visitAssignop(JCAssignOp that)           { visitTree(that); }
        public void visitUnary(JCUnary that)                 { visitTree(that); }
        public void visitBinary(JCBinary that)               { visitTree(that); }
        public void visitTypeCast(JCTypeCast that)           { visitTree(that); }
        public void visitTypeTest(JCInstanceOf that)         { visitTree(that); }
        public void visitIndexed(JCArrayAccess that)         { visitTree(that); }
        public void visitSelect(JCFieldAccess that)          { visitTree(that); }
        public void visitIdent(JCIdent that)                 { visitTree(that); }
        public void visitLiteral(JCLiteral that)             { visitTree(that); }
        public void visitTypeIdent(JCPrimitiveTypeTree that) { visitTree(that); }
        public void visitTypeArray(JCArrayTypeTree that)     { visitTree(that); }
        public void visitTypeApply(JCTypeApply that)         { visitTree(that); }
        public void visitTypeUnion(JCTypeUnion that)         { visitTree(that); }
        public void visitTypeParameter(JCTypeParameter that) { visitTree(that); }
        public void visitWildcard(JCWildcard that)           { visitTree(that); }
        public void visitTypeBoundKind(TypeBoundKind that)   { visitTree(that); }
        public void visitAnnotation(JCAnnotation that)       { visitTree(that); }
        public void visitModifiers(JCModifiers that)         { visitTree(that); }
        public void visitErroneous(JCErroneous that)         { visitTree(that); }
        public void visitLetExpr(LetExpr that)               { visitTree(that); }

        public void visitTree(JCTree that)                   { Assert.error(); }
    }

}
