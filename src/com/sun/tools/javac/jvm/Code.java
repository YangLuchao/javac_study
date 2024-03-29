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
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;

import static com.sun.tools.javac.code.TypeTags.*;
import static com.sun.tools.javac.jvm.ByteCodes.*;
import static com.sun.tools.javac.jvm.UninitializedType.*;
import static com.sun.tools.javac.jvm.ClassWriter.StackMapTableFrame;

/** An internal structure that corresponds to the code attribute of
 *  methods in a classfile. The class also provides some utility operations to
 *  generate bytecode instructions.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
// Javac在生成字节码指令时尽可能地模拟Java虚拟机运行时的过程，
// 用来进行类型验证及更好地生成字节码指令，也能为Java虚拟机运行时提供一些必要的参数
// Code类提供了许多生成Java虚拟机指令的方法，而且在指令生成过程中还会对本地变量表、操作数栈及常量池进行相应的操作
// 每当需要为一个方法生成字节码指令时，通常都会创建一个Code对象，每个对象都对应着唯一的本地变量表和操作数栈
// 最终会将生成的字节码指令存储到Class文件中对应方法的code属性上
public class Code {

    public final boolean debugCode;
    public final boolean needStackMap;

    public enum StackMapFormat {
        NONE,
        CLDC {
            Name getAttributeName(Names names) {
                return names.StackMap;
            }
        },
        JSR202 {
            Name getAttributeName(Names names) {
                return names.StackMapTable;
            }
        };
        Name getAttributeName(Names names) {
            return names.empty;
        }
    }

    final Types types;
    final Symtab syms;

/*---------- classfile fields: --------------- */

    /** The maximum stack size.
     */
    // 最大栈空间
    public int max_stack = 0;

    /** The maximum number of local variable slots.
     */
    // max_locals保存了本地变量表的最大容量
    // 这个值将写入Class文件中为Java虚拟机初始化本地变量表提供参考
    public int max_locals = 0;

    /** The code buffer.
     */
    // code存储生成的字节码指令，具体就是指令的操作码和操作数
    public byte[] code = new byte[64];

    /** the current code pointer.
     */
    // cp指向code数组中下一个可用的位置
    public int cp = 0;

    /** Check the code against VM spec limits; if
     *  problems report them and return true.
     */
    public boolean checkLimits(DiagnosticPosition pos, Log log) {
        if (cp > ClassFile.MAX_CODE) {
            log.error(pos, "limit.code");
            return true;
        }
        if (max_locals > ClassFile.MAX_LOCALS) {
            log.error(pos, "limit.locals");
            return true;
        }
        if (max_stack > ClassFile.MAX_STACK) {
            log.error(pos, "limit.stack");
            return true;
        }
        return false;
    }

    /** A buffer for expression catch data. Each enter is a vector
     *  of four unsigned shorts.
     */
    ListBuffer<char[]> catchInfo = new ListBuffer<char[]>();

    /** A buffer for line number information. Each entry is a vector
     *  of two unsigned shorts.
     */
    List<char[]> lineInfo = List.nil(); // handled in stack fashion

    /** The CharacterRangeTable
     */
    public CRTable crt;

/*---------- internal fields: --------------- */

    /** Are we generating code with jumps >= 32K?
     */
    public boolean fatcode;

    /** Code generation enabled?
     */
    // alive在数据流分析时表示语句的可达性，这里的alive与可达性类似，
    // 如果一个语句不可达，则不会生成对应的字节码指令；
    // 如果可达，向code数组中压入指令编码后将cp值加1。
    private boolean alive = true;

    /** The current machine state (registers and stack).
     */
    State state;

    /** Is it forbidden to compactify code, because something is
     *  pointing to current location?
     */
    private boolean fixedPc = false;

    /** The next available register.
     */
    // nextreg表示lvar数组中下一个可使用的存储位置
    // 初始值为0，表示本地变量表存储的索引位置是从0开始的
    public int nextreg = 0;

    /** A chain for jumps to be resolved before the next opcode is emitted.
     *  We do this lazily to avoid jumps to jumps.
     */
    // 跳转链
    // pendingJumps中保存着要跳转到当前opcode指令的分支
    // 将pendingJumps初始化为null，当pendingJumps有值时，则保存的所有Chain对象都会在输入下一条指令时进行地址回填
    Chain pendingJumps = null;
    // 当pendingJumps有值时，在生成下一条指令时就会对pendingJumps中所有的Chain对象进行地址回填，
    // 因而pendingJumps一旦被赋值，就确定下一个指令的地址就是所有pendingJumps中保存的Chain对象的回填地址。

    /** The position of the currently statement, if we are at the
     *  start of this statement, NOPOS otherwise.
     *  We need this to emit line numbers lazily, which we need to do
     *  because of jump-to-jump optimization.
     */
    int pendingStatPos = Position.NOPOS;

    /** Set true when a stackMap is needed at the current PC. */
    boolean pendingStackMap = false;

    /** The stack map format to be generated. */
    StackMapFormat stackMap;

    /** Switch: emit variable debug info.
     */
    boolean varDebugInfo;

    /** Switch: emit line number info.
     */
    boolean lineDebugInfo;

    /** Emit line number info if map supplied
     */
    Position.LineMap lineMap;

    /** The constant pool of the current class.
     */
    final Pool pool;

    final MethodSymbol meth;

    /** Construct a code object, given the settings of the fatcode,
     *  debugging info switches and the CharacterRangeTable.
     */
    public Code(MethodSymbol meth,
                boolean fatcode,
                Position.LineMap lineMap,
                boolean varDebugInfo,
                StackMapFormat stackMap,
                boolean debugCode,
                CRTable crt,
                Symtab syms,
                Types types,
                Pool pool) {
        this.meth = meth;
        this.fatcode = fatcode;
        this.lineMap = lineMap;
        this.lineDebugInfo = lineMap != null;
        this.varDebugInfo = varDebugInfo;
        this.crt = crt;
        this.syms = syms;
        this.types = types;
        this.debugCode = debugCode;
        this.stackMap = stackMap;
        switch (stackMap) {
        case CLDC:
        case JSR202:
            this.needStackMap = true;
            break;
        default:
            this.needStackMap = false;
        }
        state = new State();
        lvar = new LocalVar[20];
        this.pool = pool;
    }


/* **************************************************************************
 * Typecodes & related stuff
 ****************************************************************************/

    /** Given a type, return its type code (used implicitly in the
     *  JVM architecture).
     */
    // 将Javac中的类型映射为Java虚拟机支持的几种类型
    public static int typecode(Type type) {
        switch (type.tag) {
        case BYTE: return BYTEcode;
        case SHORT: return SHORTcode;
        case CHAR: return CHARcode;
        case INT: return INTcode;
        case LONG: return LONGcode;
        case FLOAT: return FLOATcode;
        case DOUBLE: return DOUBLEcode;
        case BOOLEAN: return BYTEcode;
        case VOID: return VOIDcode;
        case CLASS:
        case ARRAY:
        case METHOD:
        case BOT:
        case TYPEVAR:
        case UNINITIALIZED_THIS:
        case UNINITIALIZED_OBJECT:
            return OBJECTcode;
        default: throw new AssertionError("typecode " + type.tag);
        }
    }

    /** Collapse type code for subtypes of int to INTcode.
     */
    public static int truncate(int tc) {
        switch (tc) {
        case BYTEcode: case SHORTcode: case CHARcode: return INTcode;
        default: return tc;
        }
    }

    /** The width in bytes of objects of the type.
     */
    // width()方法获取type所占的本地变量表的槽位数
    // 这些数值代表了存储对应类型值所占用的本地变量表的槽（Slot）的数量
    // 在Javac中，每个槽对应着数组的一个存储位置
    // 在查找时，对于占用一个槽的类型可直接通过表示本地变量表的数组下标来查找，而对于占用两个槽的类型，如占用n与n+1两个槽位，则最终存储的索引值为n
    // Javac会将表示本地变量表的数组的n+1下标处的值设置为null，真正在Java虚拟机中会使用两个槽位存储实际的值。
    public static int width(int typecode) {
        switch (typecode) {
        // long类型与double类型返回2
        case LONGcode: case DOUBLEcode: return 2;
        // void类型返回0
        case VOIDcode: return 0;
        // 其他返回1
        default: return 1;
        }
    }

    public static int width(Type type) {
        return type == null ? 1 : width(typecode(type));
    }

    /** The total width taken up by a vector of objects.
     */
    public static int width(List<Type> types) {
        int w = 0;
        for (List<Type> l = types; l.nonEmpty(); l = l.tail)
            w = w + width(l.head);
        return w;
    }

    /** Given a type, return its code for allocating arrays of that type.
     */
    public static int arraycode(Type type) {
        switch (type.tag) {
        case BYTE: return 8;
        case BOOLEAN: return 4;
        case SHORT: return 9;
        case CHAR: return 5;
        case INT: return 10;
        case LONG: return 11;
        case FLOAT: return 6;
        case DOUBLE: return 7;
        case CLASS: return 0;
        case ARRAY: return 1;
        default: throw new AssertionError("arraycode " + type);
        }
    }


/* **************************************************************************
 * Emit code
 ****************************************************************************/

    /** The current output code pointer.
     */
    public int curPc() {
        if (pendingJumps != null)
            // 当pendingJumps不为null时，调用resolvePending()方法回填pendingJumps中所有需要进行地址回填的分支
            resolvePending();
        if (pendingStatPos != Position.NOPOS)
            markStatBegin();
        fixedPc = true;
        return cp;
    }

    // emitX系列方法
    // emitX()系列方法的名称中最后一个字符X代表数字1、2或4，这些数字表示可向code数组中存储1、2或4个字节的数据
    // emitXxx()系列的方法都直接或间接调用了上面的emit1()方法，它可以压入一个指令编码，
    // 如emitop()方法压入指令编码，或者调用N次压入一个指令编码的占用N个字节的操作数
    /** Emit a byte of code.
     */
    private  void emit1(int od) {
        if (!alive)
            return;
        if (cp == code.length) {
            byte[] newcode = new byte[cp * 2];
            System.arraycopy(code, 0, newcode, 0, cp);
            code = newcode;
        }
        code[cp++] = (byte)od;
    }

    /** Emit two bytes of code.
     */
    // emit2()与emit4()方法分别表示压入由2个字节和4个字节表示的操作数
    private void emit2(int od) {
        if (!alive) return;
        if (cp + 2 > code.length) {
            emit1(od >> 8);
            emit1(od);
        } else {
            code[cp++] = (byte)(od >> 8);
            code[cp++] = (byte)od;
        }
    }

    /** Emit four bytes of code.
     */
    // emit2()与emit4()方法分别表示压入由2个字节和4个字节表示的操作数
    public void emit4(int od) {
        if (!alive) return;
        if (cp + 4 > code.length) {
            emit1(od >> 24);
            emit1(od >> 16);
            emit1(od >> 8);
            emit1(od);
        } else {
            code[cp++] = (byte)(od >> 24);
            code[cp++] = (byte)(od >> 16);
            code[cp++] = (byte)(od >> 8);
            code[cp++] = (byte)od;
        }
    }
    // emitX系列方法

    /** Emit an opcode.
     */
    // 调用emitop()方法将对应指令的编码保存到code字节数组中，然后操作栈中的内容
    private void emitop(int op) {
        if (pendingJumps != null)
            // 有跳转，处理地址回填
            resolvePending();
        if (alive) {
            if (pendingStatPos != Position.NOPOS)
                markStatBegin();
            if (pendingStackMap) {
                pendingStackMap = false;
                emitStackMap();
            }
            if (debugCode)
                System.err.println("emit@" + cp + " stack=" +
                                   state.stacksize + ": " +
                                   mnem(op));
            emit1(op);
        }
    }

    void postop() {
        Assert.check(alive || state.stacksize == 0);
    }

    // emitXxx()系列方法
    /** Emit a multinewarray instruction.
     */
    // 生成multinewarray:指令，表示创建指定类型和指定维度的多维数组（执行该指
    // 令时，操作数栈中必须包含各维度的长度值)，并将其引用值压入栈顶
    public void emitMultianewarray(int ndims, int type, Type arrayType) {
        emitop(multianewarray);
        if (!alive) return;
        emit2(type);
        emit1(ndims);
        state.pop(ndims);
        state.push(arrayType);
    }

    /** Emit newarray.
     */
    // 生成newarray指令，表示创建一个引用型（如类、接口、数组）的数组，并将其引用值压入栈顶
    public void emitNewarray(int elemcode, Type arrayType) {
        // 在生成newarray指令时，伴随有操作数栈的弹出与压入操作
        emitop(newarray);
        if (!alive)
            return;
        // 调用emit1()方法生成newarray指令的操作数，该操作数代表要创建数组的元素类型
        emit1(elemcode);
        // 然后从栈中弹出要创建数组的大小
        state.pop(1);
        // 最后将创建好的数组的类型压入栈内
        state.push(arrayType);
    }

    /** Emit anewarray.
     */
    // 生成anewarray:指令，表示创建一个引用类型（如类、接口、数组）的数组，并将其引用值压入栈顶
    public void emitAnewarray(int od, Type arrayType) {
        emitop(anewarray);
        if (!alive) return;
        emit2(od);
        state.pop(1);
        state.push(arrayType);
    }

    /** Emit an invokeinterface instruction.
     */
    // 生成invokeinterface指令，表示调用接口方法
    public void emitInvokeinterface(int meth, Type mtype) {
        int argsize = width(mtype.getParameterTypes());
        emitop(invokeinterface);
        if (!alive) return;
        emit2(meth);
        emit1(argsize + 1);
        emit1(0);
        state.pop(argsize + 1);
        state.push(mtype.getReturnType());
    }

    /** Emit an invokespecial instruction.
     */
    // 生成invokespecial指令，表示调用超类构造方法，实例初始化方法或者私有方法
    public void emitInvokespecial(int meth, Type mtype) {
        // 在生成invokespecial指令时伴随有操作数栈和常量池的操作
        int argsize = width(mtype.getParameterTypes());
        // 调用emitop()方法生成invokespecial指令
        emitop(invokespecial);
        if (!alive)
            return;
        // 调用emit2()方法生成一个常量池索引，该索引指向的常量池项是一个方法的符号引用
        emit2(meth);
        Symbol sym = (Symbol)pool.pool[meth];
        // 调用state.pop()方法从栈中连续弹出方法的所有形式参数对应的类型；
        state.pop(argsize);
        if (sym.isConstructor())
            state.markInitialized((UninitializedType)state.peek());
        // 最后调用state.pop()方法弹出定义当前方法的类
        state.pop(1);
        // 再调用push()方法将方法的返回类型压入栈内
        state.push(mtype.getReturnType());
    }

    /** Emit an invokestatic instruction.
     */
    // 生成invokestatic指令，表示调用静态方法
    public void emitInvokestatic(int meth, Type mtype) {
        int argsize = width(mtype.getParameterTypes());
        emitop(invokestatic);
        if (!alive)
            return;
        // 调用emitop()与emit2()方法生成invokestatic指令及操作数
        emit2(meth);
        // 从栈中弹出方法调用的实际参数
        state.pop(argsize);
        // 运行invokestatic指令会产生一个新的数据，其类型就是调用方法的返回类型
        // 因此向栈中压入一个方法返回类型，同时在invoke()方法中返回一个stackItem[rescode]代表这个新产生的栈顶数据
        state.push(mtype.getReturnType());
    }

    /** Emit an invokevirtual instruction.
     */
    // 生成invokevirutal指令，表示调用实例方法
    public void emitInvokevirtual(int meth, Type mtype) {
        int argsize = width(mtype.getParameterTypes());
        emitop(invokevirtual);
        if (!alive) return;
        emit2(meth);
        state.pop(argsize + 1);
        state.push(mtype.getReturnType());
    }

    /** Emit an invokedynamic instruction.
     */
    public void emitInvokedynamic(int desc, Type mtype) {
        // N.B. this format is under consideration by the JSR 292 EG
        int argsize = width(mtype.getParameterTypes());
        emitop(invokedynamic);
        if (!alive) return;
        emit2(desc);
        emit2(0);
        state.pop(argsize);
        state.push(mtype.getReturnType());
    }
    // emitXxx()系列方法
    // emitopX()系列方法最后一个字符X代表数字，可以是0、1、2与4，这些数字表示生成指令时对应操作数所占用的字节数。
    /** Emit an opcode with no operand field.
     */
    // 生成无操作数的指令，如aaload、goto等指令
    public void emitop0(int op) {
        emitop(op);
        if (!alive) return;
        switch (op) {
        case aaload: {
            state.pop(1);// index
            Type a = state.stack[state.stacksize-1];
            state.pop(1);
            //sometimes 'null type' is treated as a one-dimensional array type
            //see Gen.visitLiteral - we should handle this case accordingly
            Type stackType = a.tag == BOT ?
                syms.objectType :
                types.erasure(types.elemtype(a));
            state.push(stackType); }
            break;
        case goto_:
            markDead();
            break;
        case nop:
        case ineg:
        case lneg:
        case fneg:
        case dneg:
            break;
        case aconst_null:
            state.push(syms.botType);
            break;
        case iconst_m1:
        case iconst_0:
        case iconst_1:
        case iconst_2:
        case iconst_3:
        case iconst_4:
        case iconst_5:
        case iload_0:
        case iload_1:
        case iload_2:
        case iload_3:
            state.push(syms.intType);
            break;
        case lconst_0:
        case lconst_1:
        case lload_0:
        case lload_1:
        case lload_2:
        case lload_3:
            state.push(syms.longType);
            break;
        case fconst_0:
        case fconst_1:
        case fconst_2:
        case fload_0:
        case fload_1:
        case fload_2:
        case fload_3:
            state.push(syms.floatType);
            break;
        case dconst_0:
        case dconst_1:
        case dload_0:
        case dload_1:
        case dload_2:
        case dload_3:
            state.push(syms.doubleType);
            break;
        case aload_0:
            state.push(lvar[0].sym.type);
            break;
        case aload_1:
            state.push(lvar[1].sym.type);
            break;
        case aload_2:
            state.push(lvar[2].sym.type);
            break;
        case aload_3:
            state.push(lvar[3].sym.type);
            break;
        case iaload:
        case baload:
        case caload:
        case saload:
            state.pop(2);
            state.push(syms.intType);
            break;
        case laload:
            state.pop(2);
            state.push(syms.longType);
            break;
        case faload:
            state.pop(2);
            state.push(syms.floatType);
            break;
        case daload:
            state.pop(2);
            state.push(syms.doubleType);
            break;
        case istore_0:
        case istore_1:
        case istore_2:
        case istore_3:
        case fstore_0:
        case fstore_1:
        case fstore_2:
        case fstore_3:
        case astore_0:
        case astore_1:
        case astore_2:
        case astore_3:
        case pop:
        case lshr:
        case lshl:
        case lushr:
            state.pop(1);
            break;
        case areturn:
        case ireturn:
        case freturn:
            Assert.check(state.nlocks == 0);
            state.pop(1);
            markDead();
            break;
        case athrow:
            state.pop(1);
            markDead();
            break;
        case lstore_0:
        case lstore_1:
        case lstore_2:
        case lstore_3:
        case dstore_0:
        case dstore_1:
        case dstore_2:
        case dstore_3:
        case pop2:
            state.pop(2);
            break;
        case lreturn:
        case dreturn:
            Assert.check(state.nlocks == 0);
            state.pop(2);
            markDead();
            break;
        case dup:
            // 对于dup指令来说，复制栈顶内容后压入栈顶
            state.push(state.stack[state.stacksize-1]);
            break;
        case return_:
            Assert.check(state.nlocks == 0);
            markDead();
            break;
        case arraylength:
            state.pop(1);
            state.push(syms.intType);
            break;
        case isub:
        case iadd:
        case imul:
        case idiv:
        case imod:
        case ishl:
        case ishr:
        case iushr:
        case iand:
        case ior:
        case ixor:
            state.pop(1);
            // state.pop(1);
            // state.push(syms.intType);
            break;
        case aastore:
            state.pop(3);
            break;
        case land:
        case lor:
        case lxor:
        case lmod:
        case ldiv:
        case lmul:
        case lsub:
        case ladd:
            state.pop(2);
            break;
        case lcmp:
            state.pop(4);
            state.push(syms.intType);
            break;
        case l2i:
            state.pop(2);
            state.push(syms.intType);
            break;
        case i2l:
            state.pop(1);
            state.push(syms.longType);
            break;
        case i2f:
            state.pop(1);
            state.push(syms.floatType);
            break;
        case i2d:
            state.pop(1);
            state.push(syms.doubleType);
            break;
        case l2f:
            state.pop(2);
            state.push(syms.floatType);
            break;
        case l2d:
            state.pop(2);
            state.push(syms.doubleType);
            break;
        case f2i:
            state.pop(1);
            state.push(syms.intType);
            break;
        case f2l:
            state.pop(1);
            state.push(syms.longType);
            break;
        case f2d:
            state.pop(1);
            state.push(syms.doubleType);
            break;
        case d2i:
            state.pop(2);
            state.push(syms.intType);
            break;
        case d2l:
            state.pop(2);
            state.push(syms.longType);
            break;
        case d2f:
            state.pop(2);
            state.push(syms.floatType);
            break;
        case tableswitch:
        case lookupswitch:
            state.pop(1);
            // the caller is responsible for patching up the state
            break;
        case dup_x1: {
            Type val1 = state.pop1();
            Type val2 = state.pop1();
            state.push(val1);
            state.push(val2);
            state.push(val1);
            break;
        }
        case bastore:
            state.pop(3);
            break;
        case int2byte:
        case int2char:
        case int2short:
            break;
        case fmul:
        case fadd:
        case fsub:
        case fdiv:
        case fmod:
            state.pop(1);
            break;
        case castore:
        case iastore:
        case fastore:
        case sastore:
            state.pop(3);
            break;
        case lastore:
        case dastore:
            state.pop(4);
            break;
        case dup2:
            // 可能复制的是long或double这样占两个槽位的类型，也可能是复制只占一个槽位的两个类型，
            // 因此emitop()方法在实现时分情况进行了处理。
            if (state.stack[state.stacksize-1] != null) {
                Type value1 = state.pop1();
                Type value2 = state.pop1();
                state.push(value2);
                state.push(value1);
                state.push(value2);
                state.push(value1);
            } else {
                Type value = state.pop2();
                state.push(value);
                state.push(value);
            }
            break;
        case dup2_x1:
            if (state.stack[state.stacksize-1] != null) {
                Type value1 = state.pop1();
                Type value2 = state.pop1();
                Type value3 = state.pop1();
                state.push(value2);
                state.push(value1);
                state.push(value3);
                state.push(value2);
                state.push(value1);
            } else {
                Type value1 = state.pop2();
                Type value2 = state.pop1();
                state.push(value1);
                state.push(value2);
                state.push(value1);
            }
            break;
        case dup2_x2:
            if (state.stack[state.stacksize-1] != null) {
                Type value1 = state.pop1();
                Type value2 = state.pop1();
                if (state.stack[state.stacksize-1] != null) {
                    // form 1
                    Type value3 = state.pop1();
                    Type value4 = state.pop1();
                    state.push(value2);
                    state.push(value1);
                    state.push(value4);
                    state.push(value3);
                    state.push(value2);
                    state.push(value1);
                } else {
                    // form 3
                    Type value3 = state.pop2();
                    state.push(value2);
                    state.push(value1);
                    state.push(value3);
                    state.push(value2);
                    state.push(value1);
                }
            } else {
                Type value1 = state.pop2();
                if (state.stack[state.stacksize-1] != null) {
                    // form 2
                    Type value2 = state.pop1();
                    Type value3 = state.pop1();
                    state.push(value1);
                    state.push(value3);
                    state.push(value2);
                    state.push(value1);
                } else {
                    // form 4
                    Type value2 = state.pop2();
                    state.push(value1);
                    state.push(value2);
                    state.push(value1);
                }
            }
            break;
        case dup_x2: {
            Type value1 = state.pop1();
            if (state.stack[state.stacksize-1] != null) {
                // form 1
                Type value2 = state.pop1();
                Type value3 = state.pop1();
                state.push(value1);
                state.push(value3);
                state.push(value2);
                state.push(value1);
            } else {
                // form 2
                Type value2 = state.pop2();
                state.push(value1);
                state.push(value2);
                state.push(value1);
            }
        }
            break;
        case fcmpl:
        case fcmpg:
            state.pop(2);
            state.push(syms.intType);
            break;
        case dcmpl:
        case dcmpg:
            state.pop(4);
            state.push(syms.intType);
            break;
        case swap: {
            Type value1 = state.pop1();
            Type value2 = state.pop1();
            state.push(value1);
            state.push(value2);
            break;
        }
        case dadd:
        case dsub:
        case dmul:
        case ddiv:
        case dmod:
            state.pop(2);
            break;
        case ret:
            markDead();
            break;
        case wide:
            // must be handled by the caller.
            return;
        case monitorenter:
        case monitorexit:
            state.pop(1);
            break;

        default:
            throw new AssertionError(mnem(op));
        }
        postop();
    }

    /** Emit an opcode with a one-byte operand field.
     */
    // 生成带有一个操作数的指令，如bipush、ldc
    public void emitop1(int op, int od) {
        emitop(op);
        if (!alive) return;
        emit1(od);
        switch (op) {
        case bipush:
            state.push(syms.intType);
            break;
        case ldc1:
            state.push(typeForPool(pool.pool[od]));
            break;
        default:
            throw new AssertionError(mnem(op));
        }
        postop();
    }

    /** Emit an opcode with a one-byte operand field;
     *  widen if field does not fit in a byte.
     */
    // 生成带有一个操作数的指令，如果操作数无法用一个字节来表示,则使用wide指令进行扩展
    public void emitop1w(int op, int od) {
        if (od > 0xFF) {
            emitop(wide);
            emitop(op);
            emit2(od);
        } else {
            emitop(op);
            emit1(od);
        }
        if (!alive) return;
        switch (op) {
        case iload:
            state.push(syms.intType);
            break;
        case lload:
            state.push(syms.longType);
            break;
        case fload:
            state.push(syms.floatType);
            break;
        case dload:
            state.push(syms.doubleType);
            break;
        case aload:
            state.push(lvar[od].sym.type);
            break;
        case lstore:
        case dstore:
            state.pop(2);
            break;
        case istore:
        case fstore:
        case astore:
            state.pop(1);
            break;
        case ret:
            markDead();
            break;
        default:
            throw new AssertionError(mnem(op));
        }
        postop();
    }

    /** Emit an opcode with two one-byte operand fields;
     *  widen if either field does not fit in a byte.
     */
    // 生成带有两个操作数的指令，如果操作数无法用一个字节来表示，则使用wide指令进行扩展，如iincr指令
    public void emitop1w(int op, int od1, int od2) {
        if (od1 > 0xFF || od2 < -128 || od2 > 127) {
            emitop(wide);
            emitop(op);
            emit2(od1);
            emit2(od2);
        } else {
            emitop(op);
            emit1(od1);
            emit1(od2);
        }
        if (!alive) return;
        switch (op) {
        case iinc:
            break;
        default:
            throw new AssertionError(mnem(op));
        }
    }

    /** Emit an opcode with a two-byte operand field.
     */
    // 生成带有一个操作数的指令，操作数使用两个字节来表示，
    // 如getstatic、putstatic、new和sipush等指令
    public void emitop2(int op, int od) {
        emitop(op);
        if (!alive)
            return;
        emit2(od);
        switch (op) {
        case getstatic:
            // 当指令为getstatic时会向栈中压入一个擦除泛型后的类型，表示运行getstatic指令后产生了一个此类型的数据
            state.push(((Symbol)(pool.pool[od])).erasure(types));
            break;
        case putstatic:
            state.pop(((Symbol)(pool.pool[od])).erasure(types));
            break;
        case new_:
            state.push(uninitializedObject(((Symbol)(pool.pool[od])).erasure(types), cp-3));
            break;
        case sipush:
            state.push(syms.intType);
            break;
        case if_acmp_null:
        case if_acmp_nonnull:
        case ifeq:
        case ifne:
        case iflt:
        case ifge:
        case ifgt:
        case ifle:
            state.pop(1);
            break;
        case if_icmpeq:
        case if_icmpne:
        case if_icmplt:
        case if_icmpge:
        case if_icmpgt:
        case if_icmple:
        case if_acmpeq:
        case if_acmpne:
            state.pop(2);
            break;
        case goto_:
            markDead();
            break;
        case putfield:
            state.pop(((Symbol)(pool.pool[od])).erasure(types));
            state.pop(1); // object ref
            break;
        case getfield:
            state.pop(1); // object ref
            state.push(((Symbol)(pool.pool[od])).erasure(types));
            break;
        case checkcast: {
            state.pop(1); // object ref
            Object o = pool.pool[od];
            Type t = (o instanceof Symbol)
                ? ((Symbol)o).erasure(types)
                : types.erasure(((Type)o));
            state.push(t);
            break; }
        case ldc2w:
            state.push(typeForPool(pool.pool[od]));
            break;
        case instanceof_:
            state.pop(1);
            state.push(syms.intType);
            break;
        case ldc2:
            state.push(typeForPool(pool.pool[od]));
            break;
        case jsr:
            break;
        default:
            throw new AssertionError(mnem(op));
        }
        // postop();
    }

    /** Emit an opcode with a four-byte operand field.
     */
    // 生成带有一个操作数的指令，操作数使用两个字节来表示，如goto_w与jsrw指令
    public void emitop4(int op, int od) {
        emitop(op);
        if (!alive) return;
        emit4(od);
        switch (op) {
        case goto_w:
            markDead();
            break;
        case jsr_w:
            break;
        default:
            throw new AssertionError(mnem(op));
        }
        // postop();
    }
    // emitopX()系列方法最后一个字符X代表数字，可以是0、1、2与4，这些数字表示生成指令时对应操作数所占用的字节数。

    /** The type of a constant pool entry. */
    private Type typeForPool(Object o) {
        if (o instanceof Integer) return syms.intType;
        if (o instanceof Float) return syms.floatType;
        if (o instanceof String) return syms.stringType;
        if (o instanceof Long) return syms.longType;
        if (o instanceof Double) return syms.doubleType;
        if (o instanceof ClassSymbol) return syms.classType;
        if (o instanceof Type.ArrayType) return syms.classType;
        throw new AssertionError(o);
    }
    /** Align code pointer to next `incr' boundary.
     */
    public void align(int incr) {
        if (alive)
            while (cp % incr != 0) emitop0(nop);
    }

    /** Place a byte into code at address pc. Pre: pc + 1 <= cp.
     */
    private void put1(int pc, int op) {
        code[pc] = (byte)op;
    }

    /** Place two bytes into code at address pc. Pre: pc + 2 <= cp.
     */
    private void put2(int pc, int od) {
        // pre: pc + 2 <= cp
        put1(pc, od >> 8);
        put1(pc+1, od);
    }

    /** Place four  bytes into code at address pc. Pre: pc + 4 <= cp.
     */
    public void put4(int pc, int od) {
        // pre: pc + 4 <= cp
        put1(pc  , od >> 24);
        put1(pc+1, od >> 16);
        put1(pc+2, od >> 8);
        put1(pc+3, od);
    }

    /** Return code byte at position pc as an unsigned int.
     */
    private int get1(int pc) {
        return code[pc] & 0xFF;
    }

    /** Return two code bytes at position pc as an unsigned int.
     */
    private int get2(int pc) {
        return (get1(pc) << 8) | get1(pc+1);
    }

    /** Return four code bytes at position pc as an int.
     */
    public int get4(int pc) {
        // pre: pc + 4 <= cp
        return
            (get1(pc) << 24) |
            (get1(pc+1) << 16) |
            (get1(pc+2) << 8) |
            (get1(pc+3));
    }

    /** Is code generation currently enabled?
     */
    public boolean isAlive() {
        return alive || pendingJumps != null;
    }

    /** Switch code generation on/off.
     */
    public void markDead() {
        alive = false;
    }

    /** Declare an entry point; return current code pointer
     */
    public int entryPoint() {
        // 调用了curPc()方法获取当前指令的地址
        int pc = curPc();
        // 将alive设置为true
        alive = true;
        pendingStackMap = needStackMap;
        return pc;
    }

    /** Declare an entry point with initial state;
     *  return current code pointer
     */
    public int entryPoint(State state) {
        int pc = curPc();
        alive = true;
        this.state = state.dup();
        Assert.check(state.stacksize <= max_stack);
        if (debugCode) System.err.println("entry point " + state);
        pendingStackMap = needStackMap;
        return pc;
    }

    /** Declare an entry point with initial state plus a pushed value;
     *  return current code pointer
     */
    public int entryPoint(State state, Type pushed) {
        int pc = curPc();
        alive = true;
        this.state = state.dup();
        Assert.check(state.stacksize <= max_stack);
        this.state.push(pushed);
        if (debugCode) System.err.println("entry point " + state);
        pendingStackMap = needStackMap;
        return pc;
    }


/**************************************************************************
 * Stack map generation
 *************************************************************************/

    /** An entry in the stack map. */
    static class StackMapFrame {
        int pc;
        Type[] locals;
        Type[] stack;
    }

    /** A buffer of cldc stack map entries. */
    StackMapFrame[] stackMapBuffer = null;

    /** A buffer of compressed StackMapTable entries. */
    StackMapTableFrame[] stackMapTableBuffer = null;
    int stackMapBufferSize = 0;

    /** The last PC at which we generated a stack map. */
    int lastStackMapPC = -1;

    /** The last stack map frame in StackMapTable. */
    StackMapFrame lastFrame = null;

    /** The stack map frame before the last one. */
    StackMapFrame frameBeforeLast = null;

    /** Emit a stack map entry.  */
    public void emitStackMap() {
        int pc = curPc();
        if (!needStackMap) return;



        switch (stackMap) {
            case CLDC:
                emitCLDCStackMap(pc, getLocalsSize());
                break;
            case JSR202:
                emitStackMapFrame(pc, getLocalsSize());
                break;
            default:
                throw new AssertionError("Should have chosen a stackmap format");
        }
        // DEBUG code follows
        if (debugCode) state.dump(pc);
    }

    private int getLocalsSize() {
        int nextLocal = 0;
        for (int i=max_locals-1; i>=0; i--) {
            if (state.defined.isMember(i) && lvar[i] != null) {
                nextLocal = i + width(lvar[i].sym.erasure(types));
                break;
            }
        }
        return nextLocal;
    }

    /** Emit a CLDC stack map frame. */
    void emitCLDCStackMap(int pc, int localsSize) {
        if (lastStackMapPC == pc) {
            // drop existing stackmap at this offset
            stackMapBuffer[--stackMapBufferSize] = null;
        }
        lastStackMapPC = pc;

        if (stackMapBuffer == null) {
            stackMapBuffer = new StackMapFrame[20];
        } else if (stackMapBuffer.length == stackMapBufferSize) {
            StackMapFrame[] newStackMapBuffer =
                new StackMapFrame[stackMapBufferSize << 1];
            System.arraycopy(stackMapBuffer, 0, newStackMapBuffer,
                             0, stackMapBufferSize);
            stackMapBuffer = newStackMapBuffer;
        }
        StackMapFrame frame =
            stackMapBuffer[stackMapBufferSize++] = new StackMapFrame();
        frame.pc = pc;

        frame.locals = new Type[localsSize];
        for (int i=0; i<localsSize; i++) {
            if (state.defined.isMember(i) && lvar[i] != null) {
                Type vtype = lvar[i].sym.type;
                if (!(vtype instanceof UninitializedType))
                    vtype = types.erasure(vtype);
                frame.locals[i] = vtype;
            }
        }
        frame.stack = new Type[state.stacksize];
        for (int i=0; i<state.stacksize; i++)
            frame.stack[i] = state.stack[i];
    }

    void emitStackMapFrame(int pc, int localsSize) {
        if (lastFrame == null) {
            // first frame
            lastFrame = getInitialFrame();
        } else if (lastFrame.pc == pc) {
            // drop existing stackmap at this offset
            stackMapTableBuffer[--stackMapBufferSize] = null;
            lastFrame = frameBeforeLast;
            frameBeforeLast = null;
        }

        StackMapFrame frame = new StackMapFrame();
        frame.pc = pc;

        int localCount = 0;
        Type[] locals = new Type[localsSize];
        for (int i=0; i<localsSize; i++, localCount++) {
            if (state.defined.isMember(i) && lvar[i] != null) {
                Type vtype = lvar[i].sym.type;
                if (!(vtype instanceof UninitializedType))
                    vtype = types.erasure(vtype);
                locals[i] = vtype;
                if (width(vtype) > 1) i++;
            }
        }
        frame.locals = new Type[localCount];
        for (int i=0, j=0; i<localsSize; i++, j++) {
            Assert.check(j < localCount);
            frame.locals[j] = locals[i];
            if (width(locals[i]) > 1) i++;
        }

        int stackCount = 0;
        for (int i=0; i<state.stacksize; i++) {
            if (state.stack[i] != null) {
                stackCount++;
            }
        }
        frame.stack = new Type[stackCount];
        stackCount = 0;
        for (int i=0; i<state.stacksize; i++) {
            if (state.stack[i] != null) {
                frame.stack[stackCount++] = types.erasure(state.stack[i]);
            }
        }

        if (stackMapTableBuffer == null) {
            stackMapTableBuffer = new StackMapTableFrame[20];
        } else if (stackMapTableBuffer.length == stackMapBufferSize) {
            StackMapTableFrame[] newStackMapTableBuffer =
                new StackMapTableFrame[stackMapBufferSize << 1];
            System.arraycopy(stackMapTableBuffer, 0, newStackMapTableBuffer,
                             0, stackMapBufferSize);
            stackMapTableBuffer = newStackMapTableBuffer;
        }
        stackMapTableBuffer[stackMapBufferSize++] =
                StackMapTableFrame.getInstance(frame, lastFrame.pc, lastFrame.locals, types);

        frameBeforeLast = lastFrame;
        lastFrame = frame;
    }

    StackMapFrame getInitialFrame() {
        StackMapFrame frame = new StackMapFrame();
        List<Type> arg_types = ((MethodType)meth.externalType(types)).argtypes;
        int len = arg_types.length();
        int count = 0;
        if (!meth.isStatic()) {
            Type thisType = meth.owner.type;
            frame.locals = new Type[len+1];
            if (meth.isConstructor() && thisType != syms.objectType) {
                frame.locals[count++] = UninitializedType.uninitializedThis(thisType);
            } else {
                frame.locals[count++] = types.erasure(thisType);
            }
        } else {
            frame.locals = new Type[len];
        }
        for (Type arg_type : arg_types) {
            frame.locals[count++] = types.erasure(arg_type);
        }
        frame.pc = -1;
        frame.stack = null;
        return frame;
    }


/**************************************************************************
 * Operations having to do with jumps
 * 与跳跃有关的操作
 *************************************************************************/

    /** A chain represents a list of unresolved jumps. Jump locations
     *  are sorted in decreasing order.
     */
    // 在进行条件或无条件跳转时需要生成跳转指令，同时要指定跳转地址，但是在某些情况下，
    // 跳转的目标指令还没有生成，跳转地址是个未知数，因此需要在生成目标指令之前，
    // 需要通过Chain对象来保存相关的跳转信息，在生成目标指令时回填这些地址
    public static class Chain {

        /** The position of the jump instruction.
         */
        // pc指向需要进行地址回填的指令的位置
        // 对于实例17-1来说，pc的值为2，当生成目标指令return时，
        // 会将此指令相应的偏移量8回填到编号为3和4的位置
        public final int pc;

        /** The machine state after the jump instruction.
         *  Invariant: all elements of a chain list have the same stacksize
         *  and compatible stack and register contents.
         */
        // 回填的操作数栈
        Code.State state;

        /** The next jump in the list.
         */
        // next将多个Chain对象连接起来，每个Chain对象都含有一个需要进行回填地址的跳转指令，
        // 这些跳转指令的跳转目标都一样，因此连接起来的多个Chain对象跳转的目标地址一定是相同的。
        // 例如，一个循环中如果有两个break语句的目标都是跳出当前循环，那么回填两个break语句生成的指令地址也一定相同。
        public final Chain next;

        /** Construct a chain from its jump position, stacksize, previous
         *  chain, and machine state.
         */
        public Chain(int pc, Chain next, Code.State state) {
            this.pc = pc;
            this.next = next;
            this.state = state;
        }
    }

    /** Negate a branch opcode.
     */
    // 获取与opcode相反逻辑的指令编码
    // 之所以要获取与自身逻辑相反的指令，是因为对于流程控制语句来说，在为条件判断表达式选取生成的指令时，
    // 通常会选择让条件判断表达式的结果为true的指令，而实际上最终生成的是让条件判断表达式的结果为false的指令。
    // 取反是为了跳出判断，执行后续代码
    // 例17-1
    public static int negate(int opcode) {
        if (opcode == if_acmp_null) return if_acmp_nonnull;
        else if (opcode == if_acmp_nonnull) return if_acmp_null;
        else return ((opcode + 1) ^ 1) - 1;
    }

    /** Emit a jump instruction.
     *  Return code pointer of instruction to be patched.
     */
    // emitJump()方法将opcode存储到生成的字节数组中
    public int emitJump(int opcode) {
        if (fatcode) {
            if (opcode == goto_ || opcode == jsr) {
                emitop4(opcode + goto_w - goto_, 0);
            } else {
                emitop2(negate(opcode), 8);
                emitop4(goto_w, 0);
                alive = true;
                pendingStackMap = needStackMap;
            }
            return cp - 5;
        } else {
            // 调用emitop2()方法生成跳转指令，指令的目标地址暂时设置为0
            emitop2(opcode, 0);
            // emitJump()方法最后返回这个指令的地址，在进行地址回填的时候使用
            return cp - 3;
        }
    }

    /** Emit a branch with given opcode; return its chain.
     *  branch differs from jump in that jsr is treated as no-op.
     */
    public Chain branch(int opcode) {
        // 方法参数opcode一定是一个控制转移指令
        Chain result = null;
        // 当要输入的opcode指令为无条件跳转指令时，pendingJumps中保存的Chain对象应该延后进行回填，
        // 将pendingJumps置为null，这样opcode生成的Chain对象就会和pendingJumps连接在一起，
        // 跳转到共同的目标，这个共同的目标就是opcode为无条件跳转指令时跳转的目标。
        if (opcode == goto_) {
            result = pendingJumps;
            pendingJumps = null;
        }
        // 对于branch()方法来说，当opcode不为无条件跳转指令时，
        // 可以在调用emitJump()方法生成opcode时就会对pendingJumps进行地址回填
        if (opcode != dontgoto && isAlive()) {
            result = new Chain(emitJump(opcode),
                               result,
                               state.dup());
            fixedPc = fatcode;
            if (opcode == goto_) alive = false;
        }
        return result;
    }

    /** Resolve chain to point to given target.
     */
    // 调用Code类的resolve()方法进行地址回填
    public void resolve(Chain chain, int target) {
        boolean changed = false;
        State newState = state;
        // 根据参数chain能够找到所有需要回填地址的分支，这些连接在一起的chain的目标跳转地址都为target，不过有时候需要更新target
        for (; chain != null; chain = chain.next) {
            Assert.check(state != chain.state
                    && (target > chain.pc || state.stacksize == 0));
            // 更新目标跳转地址target
            if (target >= cp) {
                // 当target大于等于cp时，由于cp指向下一条指令要存储的位置，因而直接将target的值更新为cp
                target = cp;
            } else if (get1(target) == goto_) {
                // 当跳转的目标地址target处的指令也是一个无条件跳转指令时，则更新target为这个无条件跳转指令的目标跳转地址。
                if (fatcode)
                    target = target + get4(target + 1);
                else
                    target = target + get2(target + 1);
            }
            if (get1(chain.pc) == goto_ &&
                chain.pc + 3 == target && target == cp && !fixedPc) {
                // 当无条件跳转的目标指令就是下一条指定时，则不需要这条goto指令
                cp = cp - 3;
                target = target - 3;
                if (chain.next == null) {
                    // 跳出当前的循环
                    alive = true;
                    break;
                }
            } else {
                if (fatcode)
                    put4(chain.pc + 1, target - chain.pc);
                else if (target - chain.pc < Short.MIN_VALUE ||
                         target - chain.pc > Short.MAX_VALUE)
                    fatcode = true;
                else
                    put2(chain.pc + 1, target - chain.pc);
                Assert.check(!alive ||
                    chain.state.stacksize == newState.stacksize &&
                    chain.state.nlocks == newState.nlocks);
            }
            fixedPc = true;
            if (cp == target) {
                changed = true;
                if (debugCode)
                    System.err.println("resolving chain state=" + chain.state);
                if (alive) {
                    newState = chain.state.join(newState);
                } else {
                    newState = chain.state;
                    alive = true;
                }
            }
        }
        Assert.check(!changed || state != newState);
        if (state != newState) {
            setDefined(newState.defined);
            state = newState;
            pendingStackMap = needStackMap;
        }
    }

    /** Resolve chain to point to current code pointer.
     */
    // 地址回填
    public void resolve(Chain chain) {
        Assert.check(
            !alive ||
            chain==null ||
            state.stacksize == chain.state.stacksize &&
            state.nlocks == chain.state.nlocks);
        // 调用了mergeChains()方法将chain与pendingJumps合并后再次赋值给成员变量pendingJumps。
        pendingJumps = mergeChains(chain, pendingJumps);
    }

    /** Resolve any pending jumps.
     */
    // 处理地址回填
    public void resolvePending() {
        Chain x = pendingJumps;
        // pendingJumps置为null
        pendingJumps = null;
        // 处理地址回填
        resolve(x, cp);
    }

    /** Merge the jumps in of two chains into one.
     */
    // 调用Code类的mergeChains()方法进行Chain对象的合并
    // 多个Chain对象通过next连接起来，不过Chain对象对需要进行回填地址的指令地址pc从大到小进行了排序。
    public static Chain mergeChains(Chain chain1, Chain chain2) {
        // recursive merge sort
        if (chain2 == null) return chain1;
        if (chain1 == null) return chain2;
        Assert.check(
            chain1.state.stacksize == chain2.state.stacksize &&
            chain1.state.nlocks == chain2.state.nlocks);
        if (chain1.pc < chain2.pc)
            return new Chain(
                chain2.pc,
                mergeChains(chain1, chain2.next),
                chain2.state);
        return new Chain(
                chain1.pc,
                mergeChains(chain1.next, chain2),
                chain1.state);
    }


/* **************************************************************************
 * Catch clauses
 ****************************************************************************/

    /** Add a catch clause to code.
     */
    public void addCatch(
        char startPc, char endPc, char handlerPc, char catchType) {
        // 向catchInfo列表中追加一个相关记录信息的字符数组
        catchInfo.append(new char[]{startPc, endPc, handlerPc, catchType});
    }


/* **************************************************************************
 * Line numbers
 ****************************************************************************/

    /** Add a line number entry.
     */
    public void addLineNumber(char startPc, char lineNumber) {
        if (lineDebugInfo) {
            if (lineInfo.nonEmpty() && lineInfo.head[0] == startPc)
                lineInfo = lineInfo.tail;
            if (lineInfo.isEmpty() || lineInfo.head[1] != lineNumber)
                lineInfo = lineInfo.prepend(new char[]{startPc, lineNumber});
        }
    }

    /** Mark beginning of statement.
     */
    public void statBegin(int pos) {
        if (pos != Position.NOPOS) {
            pendingStatPos = pos;
        }
    }

    /** Force stat begin eagerly
     */
    public void markStatBegin() {
        if (alive && lineDebugInfo) {
            int line = lineMap.getLineNumber(pendingStatPos);
            char cp1 = (char)cp;
            char line1 = (char)line;
            if (cp1 == cp && line1 == line)
                addLineNumber(cp1, line1);
        }
        pendingStatPos = Position.NOPOS;
    }


/* **************************************************************************
 * Simulated VM machine state
 ****************************************************************************/

    // 模拟操作数栈
    // 为了进行类型校验，Javac使用Type类型的数组模拟运行时类型的入栈与出栈，这样就可以在编译期间发现更多类型相关的错误，
    // 除此之外还能得出字节码指令在运行过程中需要使用的最大栈空间max_stack等信息。
    class State implements Cloneable {
        /** The set of registers containing values. */
        Bits defined;

        /** The (types of the) contents of the machine stack. */
        // 操作数栈模拟
        Type[] stack;

        /** The first stack position currently unused. */
        // 指的就是当前stack数组的大小，由于数组索引是从0开始，因此stacksize-1就是当前栈的栈顶位置
        int stacksize;

        /** The numbers of registers containing locked monitors. */
        int[] locks;
        int nlocks;

        State() {
            defined = new Bits();
            // 初始化stack数组，默认初始化大小为16，如果栈的深度超时16还会进行扩容
            stack = new Type[16];
        }

        // dup()方法可以复制操作数栈
        // dup()方法通常在分支跳转时使用，在分支跳转之前调用当前的方法保存栈的状态，等待地址回填时使用
        State dup() {
            try {
                // dup()方法调用super.clone()方法对state进行浅克隆
                State state = (State)super.clone();
                // 对state.stack进行了复制,防止两个State对象操作时相互影响
                state.defined = defined.dup();
                state.stack = stack.clone();
                if (locks != null)
                    state.locks = locks.clone();
                if (debugCode) {
                    System.err.println("duping state " + this);
                    dump();
                }
                return state;
            } catch (CloneNotSupportedException ex) {
                throw new AssertionError(ex);
            }
        }

        void lock(int register) {
            if (locks == null) {
                locks = new int[20];
            } else if (locks.length == nlocks) {
                int[] newLocks = new int[locks.length << 1];
                System.arraycopy(locks, 0, newLocks, 0, locks.length);
                locks = newLocks;
            }
            locks[nlocks] = register;
            nlocks++;
        }

        void unlock(int register) {
            nlocks--;
            Assert.check(locks[nlocks] == register);
            locks[nlocks] = -1;
        }

        // push()方法可以向栈中压入一个类型
        void push(Type t) {
            if (debugCode)
                System.err.println("   pushing " + t);
            switch (t.tag) {
            case TypeTags.VOID:
                // t为void类型时不需要做任何处理，直接返回即可
                return;
            case TypeTags.BYTE:
            case TypeTags.CHAR:
            case TypeTags.SHORT:
            case TypeTags.BOOLEAN:
                // 当t为byte、char、short与boolean类型时将t更新为int类型
                // 由于Java虚拟机大部分的指令都没有支持byte、char、short和boolean类型，
                // 因此Javac会在编译期将它们当int类型进行处理，其余的保持原有类型即可
                t = syms.intType;
                break;
            default:
                break;
            }
            if (stacksize+2 >= stack.length) {
                Type[] newstack = new Type[2*stack.length];
                System.arraycopy(stack, 0, newstack, 0, stack.length);
                stack = newstack;
            }
            // 将类型压入栈内，由此也可以看出，stacksize并不表示栈中存放的具体类型的数量，仅能表示栈的大小
            stack[stacksize++] = t;
            // 不过还需要对double与long类型做处理，因为这两个类型需要用两个连续的槽来存储，将第2个存储位置设置为null
            switch (width(t)) {
            case 1:
                break;
            case 2:
                stack[stacksize++] = null;
                break;
            default:
                throw new AssertionError(t);
            }
            if (stacksize > max_stack)
                // push()方法最后还可能会更新max_stack的值
                max_stack = stacksize;
        }

        // 出栈一个槽位
        Type pop1() {
            if (debugCode) System.err.println("   popping " + 1);
            stacksize--;
            Type result = stack[stacksize];
            // 数据出栈后将相应槽上的值设置为空
            stack[stacksize] = null;
            Assert.check(result != null && width(result) == 1);
            return result;
        }

        // peek()方法可以获取栈顶存储的类型，不进行弹栈操作
        Type peek() {
            return stack[stacksize-1];
        }

        // 出栈两个槽位
        Type pop2() {
            if (debugCode) System.err.println("   popping " + 2);
            stacksize -= 2;
            Type result = stack[stacksize];
            // 数据出栈后将相应槽上的值设置为空
            stack[stacksize] = null;
            Assert.check(stack[stacksize+1] == null
                    && result != null && width(result) == 2);
            return result;
        }

        // 根据个数出栈
        void pop(int n) {
            if (debugCode) System.err.println("   popping " + n);
            while (n > 0) {
                // 数据出栈后将相应槽上的值设置为空
                stack[--stacksize] = null;
                n--;
            }
        }

        // 按类型出栈,如果类型为long或者double，需要连续弹出两个槽中保存的值
        void pop(Type t) {
            pop(width(t));
        }

        /** Force the top of the stack to be treated as this supertype
         *  of its current type. */
        void forceStackTop(Type t) {
            if (!alive) return;
            switch (t.tag) {
            case CLASS:
            case ARRAY:
                int width = width(t);
                Type old = stack[stacksize-width];
                Assert.check(types.isSubtype(types.erasure(old),
                                       types.erasure(t)));
                stack[stacksize-width] = t;
                break;
            default:
            }
        }

        void markInitialized(UninitializedType old) {
            Type newtype = old.initializedType();
            for (int i=0; i<stacksize; i++)
                if (stack[i] == old) stack[i] = newtype;
            for (int i=0; i<lvar.length; i++) {
                LocalVar lv = lvar[i];
                if (lv != null && lv.sym.type == old) {
                    VarSymbol sym = lv.sym;
                    sym = sym.clone(sym.owner);
                    sym.type = newtype;
                    LocalVar newlv = lvar[i] = new LocalVar(sym);
                    // should the following be initialized to cp?
                    newlv.start_pc = lv.start_pc;
                }
            }
        }

        State join(State other) {
            defined = defined.andSet(other.defined);
            Assert.check(stacksize == other.stacksize
                    && nlocks == other.nlocks);
            for (int i=0; i<stacksize; ) {
                Type t = stack[i];
                Type tother = other.stack[i];
                Type result =
                    t==tother ? t :
                    types.isSubtype(t, tother) ? tother :
                    types.isSubtype(tother, t) ? t :
                    error();
                int w = width(result);
                stack[i] = result;
                if (w == 2) Assert.checkNull(stack[i+1]);
                i += w;
            }
            return this;
        }

        Type error() {
            throw new AssertionError("inconsistent stack types at join point");
        }

        void dump() {
            dump(-1);
        }

        void dump(int pc) {
            System.err.print("stackMap for " + meth.owner + "." + meth);
            if (pc == -1)
                System.out.println();
            else
                System.out.println(" at " + pc);
            System.err.println(" stack (from bottom):");
            for (int i=0; i<stacksize; i++)
                System.err.println("  " + i + ": " + stack[i]);

            int lastLocal = 0;
            for (int i=max_locals-1; i>=0; i--) {
                if (defined.isMember(i)) {
                    lastLocal = i;
                    break;
                }
            }
            if (lastLocal >= 0)
                System.err.println(" locals:");
            for (int i=0; i<=lastLocal; i++) {
                System.err.print("  " + i + ": ");
                if (defined.isMember(i)) {
                    LocalVar var = lvar[i];
                    if (var == null) {
                        System.err.println("(none)");
                    } else if (var.sym == null)
                        System.err.println("UNKNOWN!");
                    else
                        System.err.println("" + var.sym + " of type " +
                                           var.sym.erasure(types));
                } else {
                    System.err.println("undefined");
                }
            }
            if (nlocks != 0) {
                System.err.print(" locks:");
                for (int i=0; i<nlocks; i++) {
                    System.err.print(" " + locks[i]);
                }
                System.err.println();
            }
        }
    }

    static Type jsrReturnValue = new Type(TypeTags.INT, null);


/* **************************************************************************
 * Local variables
 ****************************************************************************/

    /** A live range of a local variable. */
    // 本地变量信息对象
    // 模拟本地变量表
    static class LocalVar {
        // 表示局部变量的符号
        final VarSymbol sym;
        // 表示这个局部变量在本地变量表中的存储位置
        final char reg;
        char start_pc = Character.MAX_VALUE;
        char length = Character.MAX_VALUE;
        LocalVar(VarSymbol v) {
            this.sym = v;
            // 构造方法中获取v.adr值进行初始化
            this.reg = (char)v.adr;
        }
        public LocalVar dup() {
            return new LocalVar(sym);
        }
        public String toString() {
            return "" + sym + " in register " + ((int)reg) + " starts at pc=" + ((int)start_pc) + " length=" + ((int)length);
        }
    };

    /** Local variables, indexed by register. */
    // 通过LocalVar[]数组模拟本地变量表
    LocalVar[] lvar;

    /** Add a new local variable. */
    private void addLocalVar(VarSymbol v) {
        int adr = v.adr;
        if (adr+1 >= lvar.length) {
            int newlength = lvar.length << 1;
            if (newlength <= adr) newlength = adr + 10;
            LocalVar[] new_lvar = new LocalVar[newlength];
            System.arraycopy(lvar, 0, new_lvar, 0, lvar.length);
            lvar = new_lvar;
        }
        Assert.checkNull(lvar[adr]);
        if (pendingJumps != null) resolvePending();
        // 将v封装为LocalVar对象后存储到下标为adr的本地变量表中
        lvar[adr] = new LocalVar(v);
        state.defined.excl(adr);
    }

    /** Set the current variable defined state. */
    public void setDefined(Bits newDefined) {
        if (alive && newDefined != state.defined) {
            Bits diff = state.defined.dup().xorSet(newDefined);
            for (int adr = diff.nextBit(0);
                 adr >= 0;
                 adr = diff.nextBit(adr+1)) {
                if (adr >= nextreg)
                    state.defined.excl(adr);
                else if (state.defined.isMember(adr))
                    setUndefined(adr);
                else
                    setDefined(adr);
            }
        }
    }

    /** Mark a register as being (possibly) defined. */
    public void setDefined(int adr) {
        LocalVar v = lvar[adr];
        if (v == null) {
            state.defined.excl(adr);
        } else {
            state.defined.incl(adr);
            if (cp < Character.MAX_VALUE) {
                if (v.start_pc == Character.MAX_VALUE)
                    v.start_pc = (char)cp;
            }
        }
    }

    /** Mark a register as being undefined. */
    public void setUndefined(int adr) {
        state.defined.excl(adr);
        if (adr < lvar.length &&
            lvar[adr] != null &&
            lvar[adr].start_pc != Character.MAX_VALUE) {
            LocalVar v = lvar[adr];
            char length = (char)(curPc() - v.start_pc);
            if (length > 0 && length < Character.MAX_VALUE) {
                lvar[adr] = v.dup();
                v.length = length;
                putVar(v);
            } else {
                v.start_pc = Character.MAX_VALUE;
            }
        }
    }

    /** End the scope of a variable. */
    private void endScope(int adr) {
        LocalVar v = lvar[adr];
        if (v != null) {
            lvar[adr] = null;
            if (v.start_pc != Character.MAX_VALUE) {
                char length = (char)(curPc() - v.start_pc);
                if (length < Character.MAX_VALUE) {
                    v.length = length;
                    putVar(v);
                }
            }
        }
        state.defined.excl(adr);
    }

    /** Put a live variable range into the buffer to be output to the
     *  class file.
     */
    void putVar(LocalVar var) {
        if (!varDebugInfo) return;
        if ((var.sym.flags() & Flags.SYNTHETIC) != 0) return;
        if (varBuffer == null)
            varBuffer = new LocalVar[20];
        else if (varBufferSize >= varBuffer.length) {
            LocalVar[] newVarBuffer = new LocalVar[varBufferSize*2];
            System.arraycopy(varBuffer, 0, newVarBuffer, 0, varBuffer.length);
            varBuffer = newVarBuffer;
        }
        varBuffer[varBufferSize++] = var;
    }

    /** Previously live local variables, to be put into the variable table. */
    LocalVar[] varBuffer;
    int varBufferSize;

    /** Create a new local variable address and return it.
     */
    // 第三个重载方法
    // 返回当前变量在变量表中的存储位置reg
    private int newLocal(int typecode) {
        int reg = nextreg;
        // 调用width()方法获取type所占的本地变量表的槽位数
        int w = width(typecode);
        // 更新nextreg和max_locals变量的值
        nextreg = reg + w;
        if (nextreg > max_locals) max_locals = nextreg;
        return reg;
    }

    // 第二个重载方法
    private int newLocal(Type type) {
        // 调用typecode()方法对type做了类型映射
        return newLocal(typecode(type));
    }

    // 第一个重载方法
    public int newLocal(VarSymbol v) {
        // 调用第2个newLocal()方法以获取一个本地变量表的存储位置
        int reg = v.adr = newLocal(v.erasure(types));
        // 然后通过v.adr保存这个存储位置
        addLocalVar(v);
        return reg;
    }

    /** Start a set of fresh registers.
     */
    public void newRegSegment() {
        nextreg = max_locals;
    }

    /** End scopes of all variables with registers >= first.
     */
    public void endScopes(int first) {
        int prevNextReg = nextreg;
        nextreg = first;
        for (int i = nextreg; i < prevNextReg; i++) endScope(i);
    }

/**************************************************************************
 * static tables
 *************************************************************************/

    public static String mnem(int opcode) {
        return Mneumonics.mnem[opcode];
    }

    private static class Mneumonics {
        private final static String[] mnem = new String[ByteCodeCount];
        static {
            mnem[nop] = "nop";
            mnem[aconst_null] = "aconst_null";
            mnem[iconst_m1] = "iconst_m1";
            mnem[iconst_0] = "iconst_0";
            mnem[iconst_1] = "iconst_1";
            mnem[iconst_2] = "iconst_2";
            mnem[iconst_3] = "iconst_3";
            mnem[iconst_4] = "iconst_4";
            mnem[iconst_5] = "iconst_5";
            mnem[lconst_0] = "lconst_0";
            mnem[lconst_1] = "lconst_1";
            mnem[fconst_0] = "fconst_0";
            mnem[fconst_1] = "fconst_1";
            mnem[fconst_2] = "fconst_2";
            mnem[dconst_0] = "dconst_0";
            mnem[dconst_1] = "dconst_1";
            mnem[bipush] = "bipush";
            mnem[sipush] = "sipush";
            mnem[ldc1] = "ldc1";
            mnem[ldc2] = "ldc2";
            mnem[ldc2w] = "ldc2w";
            mnem[iload] = "iload";
            mnem[lload] = "lload";
            mnem[fload] = "fload";
            mnem[dload] = "dload";
            mnem[aload] = "aload";
            mnem[iload_0] = "iload_0";
            mnem[lload_0] = "lload_0";
            mnem[fload_0] = "fload_0";
            mnem[dload_0] = "dload_0";
            mnem[aload_0] = "aload_0";
            mnem[iload_1] = "iload_1";
            mnem[lload_1] = "lload_1";
            mnem[fload_1] = "fload_1";
            mnem[dload_1] = "dload_1";
            mnem[aload_1] = "aload_1";
            mnem[iload_2] = "iload_2";
            mnem[lload_2] = "lload_2";
            mnem[fload_2] = "fload_2";
            mnem[dload_2] = "dload_2";
            mnem[aload_2] = "aload_2";
            mnem[iload_3] = "iload_3";
            mnem[lload_3] = "lload_3";
            mnem[fload_3] = "fload_3";
            mnem[dload_3] = "dload_3";
            mnem[aload_3] = "aload_3";
            mnem[iaload] = "iaload";
            mnem[laload] = "laload";
            mnem[faload] = "faload";
            mnem[daload] = "daload";
            mnem[aaload] = "aaload";
            mnem[baload] = "baload";
            mnem[caload] = "caload";
            mnem[saload] = "saload";
            mnem[istore] = "istore";
            mnem[lstore] = "lstore";
            mnem[fstore] = "fstore";
            mnem[dstore] = "dstore";
            mnem[astore] = "astore";
            mnem[istore_0] = "istore_0";
            mnem[lstore_0] = "lstore_0";
            mnem[fstore_0] = "fstore_0";
            mnem[dstore_0] = "dstore_0";
            mnem[astore_0] = "astore_0";
            mnem[istore_1] = "istore_1";
            mnem[lstore_1] = "lstore_1";
            mnem[fstore_1] = "fstore_1";
            mnem[dstore_1] = "dstore_1";
            mnem[astore_1] = "astore_1";
            mnem[istore_2] = "istore_2";
            mnem[lstore_2] = "lstore_2";
            mnem[fstore_2] = "fstore_2";
            mnem[dstore_2] = "dstore_2";
            mnem[astore_2] = "astore_2";
            mnem[istore_3] = "istore_3";
            mnem[lstore_3] = "lstore_3";
            mnem[fstore_3] = "fstore_3";
            mnem[dstore_3] = "dstore_3";
            mnem[astore_3] = "astore_3";
            mnem[iastore] = "iastore";
            mnem[lastore] = "lastore";
            mnem[fastore] = "fastore";
            mnem[dastore] = "dastore";
            mnem[aastore] = "aastore";
            mnem[bastore] = "bastore";
            mnem[castore] = "castore";
            mnem[sastore] = "sastore";
            mnem[pop] = "pop";
            mnem[pop2] = "pop2";
            mnem[dup] = "dup";
            mnem[dup_x1] = "dup_x1";
            mnem[dup_x2] = "dup_x2";
            mnem[dup2] = "dup2";
            mnem[dup2_x1] = "dup2_x1";
            mnem[dup2_x2] = "dup2_x2";
            mnem[swap] = "swap";
            mnem[iadd] = "iadd";
            mnem[ladd] = "ladd";
            mnem[fadd] = "fadd";
            mnem[dadd] = "dadd";
            mnem[isub] = "isub";
            mnem[lsub] = "lsub";
            mnem[fsub] = "fsub";
            mnem[dsub] = "dsub";
            mnem[imul] = "imul";
            mnem[lmul] = "lmul";
            mnem[fmul] = "fmul";
            mnem[dmul] = "dmul";
            mnem[idiv] = "idiv";
            mnem[ldiv] = "ldiv";
            mnem[fdiv] = "fdiv";
            mnem[ddiv] = "ddiv";
            mnem[imod] = "imod";
            mnem[lmod] = "lmod";
            mnem[fmod] = "fmod";
            mnem[dmod] = "dmod";
            mnem[ineg] = "ineg";
            mnem[lneg] = "lneg";
            mnem[fneg] = "fneg";
            mnem[dneg] = "dneg";
            mnem[ishl] = "ishl";
            mnem[lshl] = "lshl";
            mnem[ishr] = "ishr";
            mnem[lshr] = "lshr";
            mnem[iushr] = "iushr";
            mnem[lushr] = "lushr";
            mnem[iand] = "iand";
            mnem[land] = "land";
            mnem[ior] = "ior";
            mnem[lor] = "lor";
            mnem[ixor] = "ixor";
            mnem[lxor] = "lxor";
            mnem[iinc] = "iinc";
            mnem[i2l] = "i2l";
            mnem[i2f] = "i2f";
            mnem[i2d] = "i2d";
            mnem[l2i] = "l2i";
            mnem[l2f] = "l2f";
            mnem[l2d] = "l2d";
            mnem[f2i] = "f2i";
            mnem[f2l] = "f2l";
            mnem[f2d] = "f2d";
            mnem[d2i] = "d2i";
            mnem[d2l] = "d2l";
            mnem[d2f] = "d2f";
            mnem[int2byte] = "int2byte";
            mnem[int2char] = "int2char";
            mnem[int2short] = "int2short";
            mnem[lcmp] = "lcmp";
            mnem[fcmpl] = "fcmpl";
            mnem[fcmpg] = "fcmpg";
            mnem[dcmpl] = "dcmpl";
            mnem[dcmpg] = "dcmpg";
            mnem[ifeq] = "ifeq";
            mnem[ifne] = "ifne";
            mnem[iflt] = "iflt";
            mnem[ifge] = "ifge";
            mnem[ifgt] = "ifgt";
            mnem[ifle] = "ifle";
            mnem[if_icmpeq] = "if_icmpeq";
            mnem[if_icmpne] = "if_icmpne";
            mnem[if_icmplt] = "if_icmplt";
            mnem[if_icmpge] = "if_icmpge";
            mnem[if_icmpgt] = "if_icmpgt";
            mnem[if_icmple] = "if_icmple";
            mnem[if_acmpeq] = "if_acmpeq";
            mnem[if_acmpne] = "if_acmpne";
            mnem[goto_] = "goto_";
            mnem[jsr] = "jsr";
            mnem[ret] = "ret";
            mnem[tableswitch] = "tableswitch";
            mnem[lookupswitch] = "lookupswitch";
            mnem[ireturn] = "ireturn";
            mnem[lreturn] = "lreturn";
            mnem[freturn] = "freturn";
            mnem[dreturn] = "dreturn";
            mnem[areturn] = "areturn";
            mnem[return_] = "return_";
            mnem[getstatic] = "getstatic";
            mnem[putstatic] = "putstatic";
            mnem[getfield] = "getfield";
            mnem[putfield] = "putfield";
            mnem[invokevirtual] = "invokevirtual";
            mnem[invokespecial] = "invokespecial";
            mnem[invokestatic] = "invokestatic";
            mnem[invokeinterface] = "invokeinterface";
            mnem[invokedynamic] = "invokedynamic";
            mnem[new_] = "new_";
            mnem[newarray] = "newarray";
            mnem[anewarray] = "anewarray";
            mnem[arraylength] = "arraylength";
            mnem[athrow] = "athrow";
            mnem[checkcast] = "checkcast";
            mnem[instanceof_] = "instanceof_";
            mnem[monitorenter] = "monitorenter";
            mnem[monitorexit] = "monitorexit";
            mnem[wide] = "wide";
            mnem[multianewarray] = "multianewarray";
            mnem[if_acmp_null] = "if_acmp_null";
            mnem[if_acmp_nonnull] = "if_acmp_nonnull";
            mnem[goto_w] = "goto_w";
            mnem[jsr_w] = "jsr_w";
            mnem[breakpoint] = "breakpoint";
        }
    }
}
