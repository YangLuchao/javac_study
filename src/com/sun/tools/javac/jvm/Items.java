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

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.jvm.Code.*;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.util.Assert;

import static com.sun.tools.javac.jvm.ByteCodes.*;

/** A helper class for code generation. Items are objects
 *  that stand for addressable entities in the bytecode. Each item
 *  supports a fixed protocol for loading the item on the stack, storing
 *  into it, converting it into a jump condition, and several others.
 *  There are many individual forms of items, such as local, static,
 *  indexed, or instance variables, values on the top of stack, the
 *  special values this or super, etc. Individual items are represented as
 *  inner classes in class Items.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Items {

    /** The current constant pool.
     */
    // 当前的常量池
    Pool pool;

    /** The current code buffer.
     */
    // 当前的额code对象
    Code code;

    /** The current symbol table.
     */
    // 当前的符号对象
    Symtab syms;

    /** Type utilities. */
    Types types;

    /** Items that exist only once (flyweight pattern).
     */
    private final Item voidItem;
    private final Item thisItem;
    private final Item superItem;
    // item数组
    // stackItem数组中存储的是StackItem或者Item匿名类对象，不同的对象由typecode来区分
    // 例如，当typecode值为0时是INTcode，代表一个整数类型的实体。
    private final Item[] stackItem = new Item[TypeCodeCount];

    public Items(Pool pool, Code code, Symtab syms, Types types) {
        this.code = code;
        this.pool = pool;
        this.types = types;
        voidItem = new Item(VOIDcode) {
                public String toString() { return "void"; }
            };
        thisItem = new SelfItem(false);
        superItem = new SelfItem(true);
        // 初始化stackItem
        for (int i = 0; i < VOIDcode; i++)
            stackItem[i] = new StackItem(i);
        stackItem[VOIDcode] = voidItem;
        this.syms = syms;
    }

    /** Make a void item
     */
    Item makeVoidItem() {
        return voidItem;
    }
    /** Make an item representing `this'.
     */
    Item makeThisItem() {
        return thisItem;
    }

    /** Make an item representing `super'.
     */
    Item makeSuperItem() {
        return superItem;
    }

    /** Make an item representing a value on stack.
     *  @param type    The value's type.
     */
    Item makeStackItem(Type type) {
        return stackItem[Code.typecode(type)];
    }

    /** Make an item representing an indexed expression.
     *  @param type    The expression's type.
     */
    Item makeIndexedItem(Type type) {
        return new IndexedItem(type);
    }

    /** Make an item representing a local variable.
     *  @param v    The represented variable.
     */
    LocalItem makeLocalItem(VarSymbol v) {
        return new LocalItem(v.erasure(types), v.adr);
    }

    /** Make an item representing a local anonymous variable.
     *  @param type  The represented variable's type.
     *  @param reg   The represented variable's register.
     */
    private LocalItem makeLocalItem(Type type, int reg) {
        return new LocalItem(type, reg);
    }

    /** Make an item representing a static variable or method.
     *  @param member   The represented symbol.
     */
    Item makeStaticItem(Symbol member) {
        return new StaticItem(member);
    }

    /** Make an item representing an instance variable or method.
     *  @param member       The represented symbol.
     *  @param nonvirtual   Is the reference not virtual? (true for constructors
     *                      and private members).
     */
    Item makeMemberItem(Symbol member, boolean nonvirtual) {
        return new MemberItem(member, nonvirtual);
    }

    /** Make an item representing a literal.
     *  @param type     The literal's type.
     *  @param value    The literal's value.
     */
    Item makeImmediateItem(Type type, Object value) {
        return new ImmediateItem(type, value);
    }

    /** Make an item representing an assignment expression.
     *  @param lhs      The item representing the assignment's left hand side.
     */
    Item makeAssignItem(Item lhs) {
        return new AssignItem(lhs);
    }

    /** Make an item representing a conditional or unconditional jump.
     *  @param opcode      The jump's opcode.
     *  @param trueJumps   A chain encomassing all jumps that can be taken
     *                     if the condition evaluates to true.
     *  @param falseJumps  A chain encomassing all jumps that can be taken
     *                     if the condition evaluates to false.
     */
    CondItem makeCondItem(int opcode, Chain trueJumps, Chain falseJumps) {
        return new CondItem(opcode, trueJumps, falseJumps);
    }

    /** Make an item representing a conditional or unconditional jump.
     *  @param opcode      The jump's opcode.
     */
    CondItem makeCondItem(int opcode) {
        return makeCondItem(opcode, null, null);
    }

    /** The base class of all items, which implements default behavior.
     */
    // Item及相关的子类代表可寻址的实体
    abstract class Item {

        /** The type code of values represented by this item.
         */
        // typecode保存了类型，值已经在ByteCodes类中预先进行了定义
        // @see  com.sun.tools.javac.jvm.ByteCodes
        int typecode;

        Item(int typecode) {
            this.typecode = typecode;
        }

        /** Generate code to load this item onto stack.
         */
        // load()方法：将当前的实体加载到操作数栈中
        Item load() {
            throw new AssertionError();
        }

        /** Generate code to store top of stack into this item.
         */
        // store()方法：将操作数栈栈顶项存储到这个实体中
        void store() {
            throw new AssertionError("store unsupported: " + this);
        }

        /** Generate code to invoke method represented by this item.
         */
        // invoke()方法：调用由这个实体所代表的方法。
        Item invoke() {
            throw new AssertionError(this);
        }

        /** Generate code to use this item twice.
         */
        // duplicate()方法：复制栈顶项。
        void duplicate() {}

        /** Generate code to avoid having to use this item.
         */
        // drop()方法：丢弃当前的实体。
        void drop() {}

        /** Generate code to stash a copy of top of stack - of typecode toscode -
         *  under this item.
         */
        // stash()方法：复制栈顶项，插入到当前实体的下面。
        void stash(int toscode) {
            // 传入的toscode表示当前操作数栈顶的数据类型，调用duplicate()方法复制一个
            stackItem[toscode].duplicate();
        }

        /** Generate code to turn item into a testable condition.
         */
        CondItem mkCond() {
            load();
            return makeCondItem(ifne);
        }

        /** Generate code to coerce item to given type code.
         *  @param targetcode    The type code to coerce to.
         */
        Item coerce(int targetcode) {
            if (typecode == targetcode)
                return this;
            else {
                load();
                int typecode1 = Code.truncate(typecode);
                int targetcode1 = Code.truncate(targetcode);
                if (typecode1 != targetcode1) {
                    int offset = targetcode1 > typecode1 ? targetcode1 - 1
                        : targetcode1;
                    code.emitop0(i2l + typecode1 * 3 + offset);
                }
                if (targetcode != targetcode1) {
                    code.emitop0(int2byte + targetcode - BYTEcode);
                }
                return stackItem[targetcode];
            }
        }

        /** Generate code to coerce item to given type.
         *  @param targettype    The type to coerce to.
         */
        Item coerce(Type targettype) {
            return coerce(Code.typecode(targettype));
        }

        /** Return the width of this item on stack as a number of words.
         */
        int width() {
            return 0;
        }

        public abstract String toString();
    }

    /** An item representing a value on stack.
     */
    // 每个StackItem对象代表一个操作数栈中的数据，对于Javac来说，这个数据就是一个类型
    class StackItem extends Item {

        StackItem(int typecode) {
            super(typecode);
        }

        Item load() {
            return this;
        }

        // duplicate()方法会生成dup2或dup指令，
        // 调用code.emitop0()方法在生成指令的同时还要复制操作数栈顶的内容
        void duplicate() {
            // dup:复制栈顶数值并将复制值压入栈顶
            // dup2:复制栈顶一个(long或double类型)或两个
            //      (非long或double的其他类型)数值并将复制值压入栈顶
            code.emitop0(width() == 2 ? dup2 : dup);
        }

        void drop() {
            // pop2:将栈顶的一个(非long或double类型)或两个数值
            //      (非long或double的其他类型)弹出
            // pop:将栈顶数值弹出(数值不能是long或double类型的)
            code.emitop0(width() == 2 ? pop2 : pop);
        }

        // stash()方法会生成dup_x1、dup2_x1或dup_x2、dup2_x2指令
        void stash(int toscode) {
            // dup_x2:复制栈顶数值并将三个(或两个)复制值压入栈顶
            // dop_x1:复制栈顶数值并将三个(或两个)复制值压入栈顶
            code.emitop0(
                (width() == 2 ? dup_x2 : dup_x1) + 3 * (Code.width(toscode) - 1));
        }

        int width() {
            return Code.width(typecode);
        }

        public String toString() {
            return "stack(" + typecodeNames[typecode] + ")";
        }
    }

    /** An item representing an indexed expression.
     */
    // 每个IndexedItem对象代表一个索引表达式
    // 例16-9
    class IndexedItem extends Item {

        IndexedItem(Type type) {
            super(Code.typecode(type));
        }

        Item load() {
            code.emitop0(iaload + typecode);
            return stackItem[typecode];
        }

        void store() {
            code.emitop0(iastore + typecode);
        }

        void duplicate() {
            code.emitop0(dup2);
        }

        void drop() {
            code.emitop0(pop2);
        }

        void stash(int toscode) {
            code.emitop0(dup_x2 + 3 * (Code.width(toscode) - 1));
        }

        int width() {
            return 2;
        }

        public String toString() {
            return "indexed(" + ByteCodes.typecodeNames[typecode] + ")";
        }
    }

    /** An item representing `this' or `super'.
     */
    // SelfItem代表Java中的关键字this或super
    // 如果一个类没有明确声明构造方法，则Javac会添加一个默认构造方法
    // 例16-8
    class SelfItem extends Item {

        /** Flag which determines whether this item represents `this' or `super'.
         */
        // 当isSuper值为true时则表示关键字super
        boolean isSuper;

        SelfItem(boolean isSuper) {
            super(OBJECTcode);
            this.isSuper = isSuper;
        }

        Item load() {
            code.emitop0(aload_0);
            return stackItem[typecode];
        }

        public String toString() {
            return isSuper ? "super" : "this";
        }
    }

    /** An item representing a local variable.
     */
    // 每个LocalItem对象代表一个本地变量
    // 本地变量一般存储到本地变量表中，因而load()与store()方法生成的指令也都与本地变量表相关
    class LocalItem extends Item {

        /** The variable's register.
         */
        // reg指明了当前变量存储在本地变量表中的位置
        int reg;

        /** The variable's type.
         */
        // type指明了本地变量的类型
        Type type;

        LocalItem(Type type, int reg) {
            // 在构造方法中通过调用Code.typecode()方法初始化Item类中的typecode变量
            super(Code.typecode(type));
            Assert.check(reg >= 0);
            this.type = type;
            this.reg = reg;
        }

        // load()方法将本地变量表中reg指定位置的数据压入栈顶，
        // 如果指定的索引值reg小于等于3，那么可直接使用本身带有操作数的一些指令来完成
        Item load() {
            if (reg <= 3)
                // 其中code.truncate(typecode)*4用来辅助选择具体的指令
                // 将第一个int型本地变量推送至栈顶
                code.emitop0(iload_0 + Code.truncate(typecode) * 4 + reg);
            else
                // 如果指定的索引值reg大于3，则使用指定操作数的指令，code.truncate(typecode)同样用来辅助选择具体的指令
                code.emitop1w(iload + Code.truncate(typecode), reg);
            return stackItem[typecode];
        }

        void store() {
            if (reg <= 3)
                // 将栈顶int型数值存入第一个本地变量
                code.emitop0(istore_0 + Code.truncate(typecode) * 4 + reg);
            else
                // 将栈顶int型数值存入指定本地变量
                code.emitop1w(istore + Code.truncate(typecode), reg);
            code.setDefined(reg);
        }

        // incr()方法直接或间接对本地变量表中存储的数值进行增减操作
        // 如果指定的操作数大小在32768~32767范围内，直接使用iinc指令即可，否则需要借助操作数栈来完成
        // 例16-3
        void incr(int x) {
            if (typecode == INTcode && x >= -32768 && x <= 32767) {
                // 将指定int型变量增加指定值(如i++, i–, i+=2等)
                code.emitop1w(iinc, reg, x);
            } else {
                // 将当前LocalItem对象代表的本地变量加载到操作数栈中
                load();
                if (x >= 0) {
                    // 将另外一个操作数加载到栈中
                    makeImmediateItem(syms.intType, x).load();
                    // 将栈顶两int型数值相加并将结果压入栈顶
                    code.emitop0(iadd);
                } else {
                    // 将另外一个操作数加载到栈中
                    makeImmediateItem(syms.intType, -x).load();
                    // 将栈顶两int型数值相减并将结果压入栈顶
                    code.emitop0(isub);
                }
                makeStackItem(syms.intType).coerce(typecode);
                // 完成之后调用store()方法将栈顶的值更新到本地变量表
                store();
            }
        }

        public String toString() {
            return "localItem(type=" + type + "; reg=" + reg + ")";
        }
    }

    /** An item representing a static variable or method.
     */
    // 每个StaticItem对象代表一个静态变量或者静态方法
    // 例16-6
    class StaticItem extends Item {

        /** The represented symbol.
         */
        // member变量保存了具体的变量或方法的符号
        // 如果member保存的是静态变量，则可以调用load()或store()方法来完成加载或存储操作
        // 如果member保存的是静态方法，则可以调用invoke()方法执行静态方法。
        Symbol member;

        StaticItem(Symbol member) {
            super(Code.typecode(member.erasure(types)));
            this.member = member;
        }

        Item load() {
            // 首先调用pool.put()方法将member存储到常量池中并返回常量池索引
            // 然后调用code.emitop2()方法生成getstatic指令，这个指令在运行前不需要栈中的数据
            // 但是运行后将在栈内生成一个typecode类型的数据
            code.emitop2(getstatic, pool.put(member));
            // load()方法返回的stackItem [typecode],表示这个新生成的栈顶数据
            return stackItem[typecode];
        }

        void store() {
            // store()方法生成putstatic指令，这个指令会消耗栈顶的一个数据，用来设置对象字段的值
            code.emitop2(putstatic, pool.put(member));
            // 运行putstatic指令不会产生新的类型数据，因此不需要后续的操作，store()方法无返回值
        }

        // 如果member是静态方法，则可以调用invoke()方法以生成方法调用相关的指令
        Item invoke() {
            MethodType mtype = (MethodType)member.erasure(types);
            int rescode = Code.typecode(mtype.restype);
            // 首先调用pool.put()方法将member存储到常量池中并返回常量池索引
            // 然后调用code.emitInvokestatic()方法生成方法调用指令
            code.emitInvokestatic(pool.put(member), mtype);
            return stackItem[rescode];
        }

        public String toString() {
            return "static(" + member + ")";
        }
    }

    /** An item representing an instance variable or method.
     */
    // 每个MemberItem对象代表一个实例变量或者实例方法
    // 通过MemberItem类可以辅助生成使用实例成员的表达式的字节码指令
    // 例16-7
    class MemberItem extends Item {

        /** The represented symbol.
         */
        Symbol member;

        /** Flag that determines whether or not access is virtual.
         */
        boolean nonvirtual;

        MemberItem(Symbol member, boolean nonvirtual) {
            super(Code.typecode(member.erasure(types)));
            this.member = member;
            this.nonvirtual = nonvirtual;
        }

        // load()与store()方法的实现与StaticItem类中的load()与store()方法的实现类似
        Item load() {
            code.emitop2(getfield, pool.put(member));
            return stackItem[typecode];
        }

        // load()与store()方法的实现与StaticItem类中的load()与store()方法的实现类似
        void store() {
            code.emitop2(putfield, pool.put(member));
        }

        Item invoke() {
            MethodType mtype = (MethodType)member.externalType(types);
            int rescode = Code.typecode(mtype.restype);
            if ((member.owner.flags() & Flags.INTERFACE) != 0) {
                // 如果当前对象表示的是接口中定义的方法，则生成invokeinterface指令
                code.emitInvokeinterface(pool.put(member), mtype);
            } else if (nonvirtual) {
                // 如果nonvirtual为true，则生成invokespecial指令
                code.emitInvokespecial(pool.put(member), mtype);
                // 在以下情况下，nonvirtual的值为true，则表示使用invokespecial指令调用当前方法
                // 1:调用构造方法，也就是调用名称为<init>的方法；
                // 2:由private修饰的私有方法；
                // 3:通过super关键字调用父类方法。
            } else {
                // 其他情况下生成invokevirtual指令
                code.emitInvokevirtual(pool.put(member), mtype);
            }
            return stackItem[rescode];
        }

        void duplicate() {
            stackItem[OBJECTcode].duplicate();
        }

        void drop() {
            stackItem[OBJECTcode].drop();
        }

        void stash(int toscode) {
            stackItem[OBJECTcode].stash(toscode);
        }

        int width() {
            return 1;
        }

        public String toString() {
            return "member(" + member + (nonvirtual ? " nonvirtual)" : ")");
        }
    }

    /** An item representing a literal.
     */
    // 每个ImmediateItem对象代表一个常量
    // 例16-4
    class ImmediateItem extends Item {

        /** The literal's value.
         */
        // value保存了常量值，
        Object value;

        ImmediateItem(Type type, Object value) {
            super(Code.typecode(type));
            this.value = value;
        }

        // 将大数放入常量池中，然后使用ldc2w、ldc1或者ldc2指令将常量值推送到栈顶
        private void ldc() {
            int idx = pool.put(value);
            if (typecode == LONGcode || typecode == DOUBLEcode) {
                // 将long或double型常量值从常量池中推送至栈顶(宽索引)
                code.emitop2(ldc2w, idx);
            } else if (idx <= 255) {
                // 将int,float或String型常量值从常量池中推送至栈顶
                code.emitop1(ldc1, idx);
            } else {
                // 将int,float或String型常量值从常量池中推送至栈顶(宽索引)
                code.emitop2(ldc2, idx);
            }
        }

        // 类中只覆写了load()方法
        Item load() {
            switch (typecode) {
            case INTcode: case BYTEcode: case SHORTcode: case CHARcode:
                int ival = ((Number)value).intValue();
                // 当typecode为int、byte、short与char类型
                if (-1 <= ival && ival <= 5)
                    // 并且操作数又不大时会选择iconst_X（X为大于等于-1小于等于5的整数）指令
                    // 将int型0推送至栈顶 + inval
                    code.emitop0(iconst_0 + ival);
                else if (Byte.MIN_VALUE <= ival && ival <= Byte.MAX_VALUE)
                    // 将单字节的常量值(-128~127)推送至栈顶
                    code.emitop1(bipush, ival);
                else if (Short.MIN_VALUE <= ival && ival <= Short.MAX_VALUE)
                    // 将一个短整型常量(-32768~32767)推送至栈顶
                    code.emitop2(sipush, ival);
                else
                    // 将一个常量推向栈顶
                    ldc();
                break;
            case LONGcode:
                long lval = ((Number)value).longValue();
                if (lval == 0 || lval == 1)
                    code.emitop0(lconst_0 + (int)lval);
                else
                    ldc();
                break;
            case FLOATcode:
                float fval = ((Number)value).floatValue();
                if (isPosZero(fval) || fval == 1.0 || fval == 2.0)
                    code.emitop0(fconst_0 + (int)fval);
                else {
                    ldc();
                }
                break;
            case DOUBLEcode:
                double dval = ((Number)value).doubleValue();
                if (isPosZero(dval) || dval == 1.0)
                    code.emitop0(dconst_0 + (int)dval);
                else
                    ldc();
                break;
            case OBJECTcode:
                ldc();
                break;
            default:
                Assert.error();
            }
            return stackItem[typecode];
        }
        //where
            /** Return true iff float number is positive 0.
             */
            private boolean isPosZero(float x) {
                return x == 0.0f && 1.0f / x > 0.0f;
            }
            /** Return true iff double number is positive 0.
             */
            private boolean isPosZero(double x) {
                return x == 0.0d && 1.0d / x > 0.0d;
            }

        CondItem mkCond() {
            int ival = ((Number)value).intValue();
            return makeCondItem(ival != 0 ? goto_ : dontgoto);
        }

        Item coerce(int targetcode) {
            if (typecode == targetcode) {
                return this;
            } else {
                switch (targetcode) {
                case INTcode:
                    if (Code.truncate(typecode) == INTcode)
                        return this;
                    else
                        return new ImmediateItem(
                            syms.intType,
                            ((Number)value).intValue());
                case LONGcode:
                    return new ImmediateItem(
                        syms.longType,
                        ((Number)value).longValue());
                case FLOATcode:
                    return new ImmediateItem(
                        syms.floatType,
                        ((Number)value).floatValue());
                case DOUBLEcode:
                    return new ImmediateItem(
                        syms.doubleType,
                        ((Number)value).doubleValue());
                case BYTEcode:
                    return new ImmediateItem(
                        syms.byteType,
                        (int)(byte)((Number)value).intValue());
                case CHARcode:
                    return new ImmediateItem(
                        syms.charType,
                        (int)(char)((Number)value).intValue());
                case SHORTcode:
                    return new ImmediateItem(
                        syms.shortType,
                        (int)(short)((Number)value).intValue());
                default:
                    return super.coerce(targetcode);
                }
            }
        }

        public String toString() {
            return "immediate(" + value + ")";
        }
    }

    /** An item representing an assignment expressions.
     */
    // 每个AssignItem对象代表一个赋值表达式左侧的表达式
    // 例16-5
    class AssignItem extends Item {

        /** The item representing the assignment's left hand side.
         */
        // lhs代表赋值表达式左侧的可寻址实体
        Item lhs;

        AssignItem(Item lhs) {
            super(lhs.typecode);
            this.lhs = lhs;
        }

        Item load() {
            lhs.stash(typecode);
            lhs.store();
            return stackItem[typecode];
        }

        void duplicate() {
            load().duplicate();
        }

        void drop() {
            lhs.store();
        }

        void stash(int toscode) {
            Assert.error();
        }

        int width() {
            return lhs.width() + Code.width(typecode);
        }

        public String toString() {
            return "assign(lhs = " + lhs + ")";
        }
    }

    /** An item representing a conditional or unconditional jump.
     */
    // CondItem代表一个有条件或无条件跳转，这个类对于条件判断表达式的处理比较重要
    class CondItem extends Item {

        /** A chain encomassing all jumps that can be taken
         *  if the condition evaluates to true.
         */
        Chain trueJumps;

        /** A chain encomassing all jumps that can be taken
         *  if the condition evaluates to false.
         */
        Chain falseJumps;

        /** The jump's opcode.
         */
        int opcode;

        /*
         *  An abstract syntax tree of this item. It is needed
         *  for branch entries in 'CharacterRangeTable' attribute.
         */
        JCTree tree;

        CondItem(int opcode, Chain truejumps, Chain falsejumps) {
            super(BYTEcode);
            this.opcode = opcode;
            this.trueJumps = truejumps;
            this.falseJumps = falsejumps;
        }

        Item load() {
            Chain trueChain = null;
            Chain falseChain = jumpFalse();
            if (!isFalse()) {
                code.resolve(trueJumps);
                code.emitop0(iconst_1);
                trueChain = code.branch(goto_);
            }
            if (falseChain != null) {
                code.resolve(falseChain);
                code.emitop0(iconst_0);
            }
            code.resolve(trueChain);
            return stackItem[typecode];
        }

        void duplicate() {
            load().duplicate();
        }

        void drop() {
            load().drop();
        }

        void stash(int toscode) {
            Assert.error();
        }

        CondItem mkCond() {
            return this;
        }

        Chain jumpTrue() {
            if (tree == null) return Code.mergeChains(trueJumps, code.branch(opcode));
            // we should proceed further in -Xjcov mode only
            int startpc = code.curPc();
            Chain c = Code.mergeChains(trueJumps, code.branch(opcode));
            code.crt.put(tree, CRTable.CRT_BRANCH_TRUE, startpc, code.curPc());
            return c;
        }

        Chain jumpFalse() {
            if (tree == null) return Code.mergeChains(falseJumps, code.branch(Code.negate(opcode)));
            // we should proceed further in -Xjcov mode only
            int startpc = code.curPc();
            Chain c = Code.mergeChains(falseJumps, code.branch(Code.negate(opcode)));
            code.crt.put(tree, CRTable.CRT_BRANCH_FALSE, startpc, code.curPc());
            return c;
        }

        CondItem negate() {
            CondItem c = new CondItem(Code.negate(opcode), falseJumps, trueJumps);
            c.tree = tree;
            return c;
        }

        int width() {
            // a CondItem doesn't have a size on the stack per se.
            throw new AssertionError();
        }

        boolean isTrue() {
            return falseJumps == null && opcode == goto_;
        }

        boolean isFalse() {
            return trueJumps == null && opcode == dontgoto;
        }

        public String toString() {
            return "cond(" + Code.mnem(opcode) + ")";
        }
    }
}
