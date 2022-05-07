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

package com.sun.tools.javac.code;

import java.util.EnumSet;
import java.util.Collections;
import java.util.Map;
import java.util.Set;
import javax.lang.model.element.Modifier;

/** Access flags and other modifiers for Java classes and members.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
// 修饰符类
// public修饰符用第1位来表示，static修饰符用第4位来表示
// 如果定义一个只有public与static修饰的类，
// 那么这个类的JCModifiers对象flags的值应该通过如下表达式计算
// (1<<0)+(1<<3)
// 最终计算的值为9
public class Flags {

    private Flags() {} // uninstantiable

    public static String toString(long flags) {
        StringBuilder buf = new StringBuilder();
        String sep = "";
        for (Flag s : asFlagSet(flags)) {
            buf.append(sep);
            buf.append(s);
            sep = " ";
        }
        return buf.toString();
    }

    public static EnumSet<Flag> asFlagSet(long mask) {
        EnumSet<Flag> flags = EnumSet.noneOf(Flag.class);
        if ((mask&PUBLIC) != 0) flags.add(Flag.PUBLIC);
        if ((mask&PRIVATE) != 0) flags.add(Flag.PRIVATE);
        if ((mask&PROTECTED) != 0) flags.add(Flag.PROTECTED);
        if ((mask&STATIC) != 0) flags.add(Flag.STATIC);
        if ((mask&FINAL) != 0) flags.add(Flag.FINAL);
        if ((mask&SYNCHRONIZED) != 0) flags.add(Flag.SYNCHRONIZED);
        if ((mask&VOLATILE) != 0) flags.add(Flag.VOLATILE);
        if ((mask&TRANSIENT) != 0) flags.add(Flag.TRANSIENT);
        if ((mask&NATIVE) != 0) flags.add(Flag.NATIVE);
        if ((mask&INTERFACE) != 0) flags.add(Flag.INTERFACE);
        if ((mask&ABSTRACT) != 0) flags.add(Flag.ABSTRACT);
        if ((mask&STRICTFP) != 0) flags.add(Flag.STRICTFP);
        if ((mask&BRIDGE) != 0) flags.add(Flag.BRIDGE);
        if ((mask&SYNTHETIC) != 0) flags.add(Flag.SYNTHETIC);
        if ((mask&DEPRECATED) != 0) flags.add(Flag.DEPRECATED);
        if ((mask&HASINIT) != 0) flags.add(Flag.HASINIT);
        if ((mask&ENUM) != 0) flags.add(Flag.ENUM);
        if ((mask&IPROXY) != 0) flags.add(Flag.IPROXY);
        if ((mask&NOOUTERTHIS) != 0) flags.add(Flag.NOOUTERTHIS);
        if ((mask&EXISTS) != 0) flags.add(Flag.EXISTS);
        if ((mask&COMPOUND) != 0) flags.add(Flag.COMPOUND);
        if ((mask&CLASS_SEEN) != 0) flags.add(Flag.CLASS_SEEN);
        if ((mask&SOURCE_SEEN) != 0) flags.add(Flag.SOURCE_SEEN);
        if ((mask&LOCKED) != 0) flags.add(Flag.LOCKED);
        if ((mask&UNATTRIBUTED) != 0) flags.add(Flag.UNATTRIBUTED);
        if ((mask&ANONCONSTR) != 0) flags.add(Flag.ANONCONSTR);
        if ((mask&ACYCLIC) != 0) flags.add(Flag.ACYCLIC);
        if ((mask&PARAMETER) != 0) flags.add(Flag.PARAMETER);
        if ((mask&VARARGS) != 0) flags.add(Flag.VARARGS);
        return flags;
    }

    /* Standard Java flags.
     */
    // public
    public static final int PUBLIC       = 1<<0;
    // private
    public static final int PRIVATE      = 1<<1;
    // protected
    public static final int PROTECTED    = 1<<2;
    // static
    public static final int STATIC       = 1<<3;
    // final
    public static final int FINAL        = 1<<4;
    // synchronized
    public static final int SYNCHRONIZED = 1<<5;
    // volatile
    public static final int VOLATILE     = 1<<6;
    // transient
    public static final int TRANSIENT    = 1<<7;
    // native
    public static final int NATIVE       = 1<<8;
    // interface
    public static final int INTERFACE    = 1<<9;
    // abstract
    public static final int ABSTRACT     = 1<<10;
    // synthetic 精确浮点数
    public static final int STRICTFP     = 1<<11;

    /* Flag that marks a symbol synthetic, added in classfile v49.0. */
    // synthetic 表示javac等编译器合成的符号(桥方法)
    public static final int SYNTHETIC    = 1<<12;

    /** Flag that marks attribute interfaces, added in classfile v49.0. */
    // annotation 声明一个注解
    public static final int ANNOTATION   = 1<<13;

    /** An enumeration type or an enumeration constant, added in
     *  classfile v49.0. */
    // enum 声明一个枚举
    public static final int ENUM         = 1<<14;

    public static final int StandardFlags = 0x0fff;
    public static final int ModifierFlags = StandardFlags & ~INTERFACE;

    // Because the following access flags are overloaded with other
    // bit positions, we translate them when reading and writing class
    // files into unique bits positions: ACC_SYNTHETIC <-> SYNTHETIC,
    // for example.
    // 1.0.2之后编译出来的类都需要设置这个标志
    public static final int ACC_SUPER    = 0x0020;
    // 由Javac等编译器生成的桥方法
    public static final int ACC_BRIDGE   = 0x0040;
    // 方法参数中含有变长参数类型
    public static final int ACC_VARARGS  = 0x0080;

    /*****************************************
     * Internal compiler flags (no bits in the lower 16).
     *****************************************/

    /** Flag is set if symbol is deprecated.
     */
    // 不建议使用的符号，例如，在方法上使用了@Deprecated
    public static final int DEPRECATED   = 1<<17;

    /** Flag is set for a variable symbol if the variable's definition
     *  has an initializer part.
     */
    // 变量有初始化部分
    public static final int HASINIT          = 1<<18;

    /** Flag is set for compiler-generated anonymous method symbols
     *  that `own' an initializer block.
     */
    // 表示匿名块，如静态块与实例块

    public static final int BLOCK            = 1<<20;

    /** Flag is set for compiler-generated abstract methods that implement
     *  an interface method (Miranda methods).
     */
    // 米兰达方法，为了弥补低版本虚拟机出现的Bug而需要生成相关的方法
    public static final int IPROXY           = 1<<21;

    /** Flag is set for nested classes that do not access instance members
     *  or `this' of an outer class and therefore don't need to be passed
     *  a this$n reference.  This flag is currently set only for anonymous
     *  classes in superclass constructor calls and only for pre 1.4 targets.
     *  todo: use this flag for optimizing away this$n parameters in
     *  other cases.
     */
    // 表示嵌套类没有引用外部类相关的实例成员或者使用this关键字引用外部类实例
    public static final int NOOUTERTHIS  = 1<<22;

    /** Flag is set for package symbols if a package has a member or
     *  directory and therefore exists.
     */
    // 包下有文件或者目录时在包符号上标注此值
    public static final int EXISTS           = 1<<23;

    /** Flag is set for compiler-generated compound classes
     *  representing multiple variable bounds
     */
    // 当一个类型变量有多个上界时，多个上界可以看作一个类，这个类需要标注此值
    public static final int COMPOUND     = 1<<24;

    /** Flag is set for class symbols if a class file was found for this class.
     */
    // 当前类型符号有对应的Class文件
    public static final int CLASS_SEEN   = 1<<25;

    /** Flag is set for class symbols if a source file was found for this
     *  class.
     */
    // 当前的类型符号有对应的Java源文件
    public static final int SOURCE_SEEN  = 1<<26;

    /* State flags (are reset during compilation).
     */

    /** Flag for class symbols is set and later re-set as a lock in
     *  Enter to detect cycles in the superclass/superinterface
     *  relations.  Similarly for constructor call cycle detection in
     *  Attr.
     */
    // 辅助对继承或者构造方法调用可能形成的循环进行检查
    public static final int LOCKED           = 1<<27;

    /** Flag for class symbols is set and later re-set to indicate that a class
     *  has been entered but has not yet been attributed.
     */
    // 当前输入的类还未标注
    public static final int UNATTRIBUTED = 1<<28;

    /** Flag for synthesized default constructors of anonymous classes.
     */
    // 为匿名类合成构造方法需要标注此值
    public static final int ANONCONSTR   = 1<<29;

    /** Flag for class symbols to indicate it has been checked and found
     *  acyclic.
     */
    // 对已经定义的类进行了检查并且没有循环定义
    public static final int ACYCLIC          = 1<<30;

    /** Flag that marks bridge methods.
     */
    // 标注桥接方法
    public static final long BRIDGE          = 1L<<31;

    /** Flag that marks formal parameters.
     */
    // 标注形式参数，例如方法中的形式参数
    public static final long PARAMETER   = 1L<<33;

    /** Flag that marks varargs methods.
     */
    // 标注带有可变参数的方法(Object ...)
    public static final long VARARGS   = 1L<<34;

    /** Flag for annotation type symbols to indicate it has been
     *  checked and found acyclic.
     */
    // 对已经定义的类符号进行了检查且有循环出现
    public static final long ACYCLIC_ANN      = 1L<<35;

    /** Flag that marks a generated default constructor.
     */
    // 标注由Javac生成的默认构造方法
    public static final long GENERATEDCONSTR   = 1L<<36;

    /** Flag that marks a hypothetical method that need not really be
     *  generated in the binary, but is present in the symbol table to
     *  simplify checking for erasure clashes.
     */
    // 假想的方法，辅助实现在泛型擦除时的冲突检查
    public static final long HYPOTHETICAL   = 1L<<37;

    /**
     * Flag that marks an internal proprietary class.
     */
    // 标注内部专用的符号
    public static final long PROPRIETARY = 1L<<38;

    /**
     * Flag that marks a a multi-catch parameter
     */
    // 当形式参数是用multicatch语法声明时标注此值
    public static final long UNION = 1L<<39;

    /**
     * Flag that marks a signature-polymorphic invoke method.
     * (These occur inside java.lang.invoke.MethodHandle.)
     */
    // 当符号被@PolymorphicSignature注解标注时其标注此值
    public static final long POLYMORPHIC_SIGNATURE = 1L<<40;

    /**
     * Flag that marks a special kind of bridge methods (the ones that
     * come from restricted supertype bounds)
     */
    // 一种特殊的桥接方法
    public static final long OVERRIDE_BRIDGE = 1L<<41;

    /**
     * Flag that marks an 'effectively final' local variable
     */
    // 局部变量标注此值后可被局部内部类和匿名内部类访问，表示局部变量已经被final修饰
    public static final long EFFECTIVELY_FINAL = 1L<<42;

    /**
     * Flag that marks non-override equivalent methods with the same signature
     */
    // 签名相同的方法，但是相互并不覆写时标注此值，表示出现冲突
    public static final long CLASH = 1L<<43;

    /** Modifier masks.
     */
    public static final int
        AccessFlags           = PUBLIC | PROTECTED | PRIVATE,
        LocalClassFlags       = FINAL | ABSTRACT | STRICTFP | ENUM | SYNTHETIC,
        MemberClassFlags      = LocalClassFlags | INTERFACE | AccessFlags,
        ClassFlags            = LocalClassFlags | INTERFACE | PUBLIC | ANNOTATION,
        InterfaceVarFlags     = FINAL | STATIC | PUBLIC,
        VarFlags              = AccessFlags | FINAL | STATIC |
                                VOLATILE | TRANSIENT | ENUM,
        ConstructorFlags      = AccessFlags,
        InterfaceMethodFlags  = ABSTRACT | PUBLIC,
        MethodFlags           = AccessFlags | ABSTRACT | STATIC | NATIVE |
                                SYNCHRONIZED | FINAL | STRICTFP;
    public static final long
        LocalVarFlags         = FINAL | PARAMETER;

    public static Set<Modifier> asModifierSet(long flags) {
        Set<Modifier> modifiers = modifierSets.get(flags);
        if (modifiers == null) {
            modifiers = java.util.EnumSet.noneOf(Modifier.class);
            if (0 != (flags & PUBLIC))    modifiers.add(Modifier.PUBLIC);
            if (0 != (flags & PROTECTED)) modifiers.add(Modifier.PROTECTED);
            if (0 != (flags & PRIVATE))   modifiers.add(Modifier.PRIVATE);
            if (0 != (flags & ABSTRACT))  modifiers.add(Modifier.ABSTRACT);
            if (0 != (flags & STATIC))    modifiers.add(Modifier.STATIC);
            if (0 != (flags & FINAL))     modifiers.add(Modifier.FINAL);
            if (0 != (flags & TRANSIENT)) modifiers.add(Modifier.TRANSIENT);
            if (0 != (flags & VOLATILE))  modifiers.add(Modifier.VOLATILE);
            if (0 != (flags & SYNCHRONIZED))
                                          modifiers.add(Modifier.SYNCHRONIZED);
            if (0 != (flags & NATIVE))    modifiers.add(Modifier.NATIVE);
            if (0 != (flags & STRICTFP))  modifiers.add(Modifier.STRICTFP);
            modifiers = Collections.unmodifiableSet(modifiers);
            modifierSets.put(flags, modifiers);
        }
        return modifiers;
    }

    // Cache of modifier sets.
    private static Map<Long, Set<Modifier>> modifierSets =
        new java.util.concurrent.ConcurrentHashMap<Long, Set<Modifier>>(64);

    public static boolean isStatic(Symbol symbol) {
        return (symbol.flags() & STATIC) != 0;
    }

    public static boolean isEnum(Symbol symbol) {
        return (symbol.flags() & ENUM) != 0;
    }

    public static boolean isConstant(Symbol.VarSymbol symbol) {
        return symbol.getConstValue() != null;
    }

    public enum Flag {

        PUBLIC("public"),
        PRIVATE("private"),
        PROTECTED("protected"),
        STATIC("static"),
        FINAL("final"),
        SYNCHRONIZED("synchronized"),
        VOLATILE("volatile"),
        TRANSIENT("transient"),
        NATIVE("native"),
        INTERFACE("interface"),
        ABSTRACT("abstract"),
        STRICTFP("strictfp"),
        BRIDGE("bridge"),
        SYNTHETIC("synthetic"),
        DEPRECATED("deprecated"),
        HASINIT("hasinit"),
        ENUM("enum"),
        IPROXY("iproxy"),
        NOOUTERTHIS("noouterthis"),
        EXISTS("exists"),
        COMPOUND("compound"),
        CLASS_SEEN("class_seen"),
        SOURCE_SEEN("source_seen"),
        LOCKED("locked"),
        UNATTRIBUTED("unattributed"),
        ANONCONSTR("anonconstr"),
        ACYCLIC("acyclic"),
        PARAMETER("parameter"),
        VARARGS("varargs"),
        PACKAGE("package");

        String name;

        Flag(String name) {
            this.name = name;
        }

        public String toString() {
            return name;
        }
    }
}
