/*
 * Copyright (c) 1999, 2005, Oracle and/or its affiliates. All rights reserved.
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


/** Bytecode instruction codes, as well as typecodes used as
 *  instruction modifiers.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 *//*
大部分的运算符根据操作数类型的不同会对应不同的指令，
指令已经在com.sun.tools.javac.jvm.ByteCodes类中预先进行了定义，
大部分的指令都严格与Java虚拟机中的指令相对应，少一部分的指令在Java虚拟机中并没有对应的指令，需要后期做相应的处理
 */
public interface ByteCodes {
    /** Byte code instruction codes.
     */
    int illegal         = -1,
    // 什么都不做
        nop             = 0,
    // 将null推送至栈顶
        aconst_null     = 1,
    // 将int型-1推送至栈顶
        iconst_m1       = 2,
    // 将int型0推送至栈顶
        iconst_0        = 3,
    // 将int型1推送至栈顶
        iconst_1        = 4,
    // 将int型2推送至栈顶
        iconst_2        = 5,
    // 将int型3推送至栈顶
        iconst_3        = 6,
    // 将int型4推送至栈顶
        iconst_4        = 7,
    // 将int型5推送至栈顶
        iconst_5        = 8,
    // 将long型0推送至栈顶
        lconst_0        = 9,
    // 将long型1推送至栈顶
        lconst_1        = 10,
    // 将float型0推送至栈顶
        fconst_0        = 11,
    // 将float型1推送至栈顶
        fconst_1        = 12,
    // 将float型2推送至栈顶
        fconst_2        = 13,
    // 将double型0推送至栈顶
        dconst_0        = 14,
    // 将double型1推送至栈顶
        dconst_1        = 15,
    // 将单字节的常量值(-128~127)推送至栈顶
        bipush          = 16,
    // 将一个短整型常量(-32768~32767)推送至栈顶
        sipush          = 17,
    // 将int,float或String型常量值从常量池中推送至栈顶
        ldc1            = 18,
    // 将int,float或String型常量值从常量池中推送至栈顶(宽索引)
        ldc2            = 19,
    // 将long或double型常量值从常量池中推送至栈顶(宽索引)
        ldc2w           = 20,
    // 将指定的int型本地变量推送至栈顶
        iload           = 21,
    // 将指定的long型本地变量推送至栈顶
        lload           = 22,
    // 将指定的float型本地变量推送至栈顶
        fload           = 23,
    // 将指定的double型本地变量推送至栈顶
        dload           = 24,
    // 将指定的引用类型本地变量推送至栈顶
        aload           = 25,
    // 将第一个int型本地变量推送至栈顶
        iload_0         = 26,
    // 将第二个int型本地变量推送至栈顶
        iload_1         = 27,
    // 将第三个int型本地变量推送至栈顶
        iload_2         = 28,
    // 将第四个int型本地变量推送至栈顶
        iload_3         = 29,
    // 将第一个long型本地变量推送至栈顶
        lload_0         = 30,
    // 将第二个long型本地变量推送至栈顶
        lload_1         = 31,
    // 	将第三个long型本地变量推送至栈顶
        lload_2         = 32,
    // 	将第四个long型本地变量推送至栈顶
        lload_3         = 33,
    // 将第一个float型本地变量推送至栈顶
        fload_0         = 34,
    // 将第二个float型本地变量推送至栈顶
        fload_1         = 35,
    // 将第三个float型本地变量推送至栈顶
        fload_2         = 36,
    // 将第四个float型本地变量推送至栈顶
        fload_3         = 37,
    // 将第一个double型本地变量推送至栈顶
        dload_0         = 38,
    // 将第二个double型本地变量推送至栈顶
        dload_1         = 39,
    // 将第三个double型本地变量推送至栈顶
        dload_2         = 40,
    // 将第四个double型本地变量推送至栈顶
        dload_3         = 41,
    // 将第一个引用类型本地变量推送至栈顶
        aload_0         = 42,
    // 	将第二个引用类型本地变量推送至栈顶
        aload_1         = 43,
    // 将第三个引用类型本地变量推送至栈顶
        aload_2         = 44,
    // 将第四个引用类型本地变量推送至栈顶
        aload_3         = 45,
    // 将int型数组指定索引的值推送至栈顶
        iaload          = 46,
    // 将long型数组指定索引的值推送至栈顶
        laload          = 47,
    // 	将float型数组指定索引的值推送至栈顶
        faload          = 48,
    // 	将double型数组指定索引的值推送至栈顶
        daload          = 49,
    // 将引用类型数组指定索引的值推送至栈顶
        aaload          = 50,
    // 将boolean或byte型数组指定索引的值推送至栈顶
        baload          = 51,
    // 将char型数组指定索引的值推送至栈顶
        caload          = 52,
    // 将short型数组指定索引的值推送至栈顶
        saload          = 53,
    // 将栈顶int型数值存入指定本地变量
        istore          = 54,
    // 将栈顶long型数值存入指定本地变量
        lstore          = 55,
    // 将栈顶float型数值存入指定本地变量
        fstore          = 56,
    // 	将栈顶double型数值存入指定本地变量
        dstore          = 57,
    // 	将栈顶引用类型数值存入指定本地变量
        astore          = 58,
    // 	将栈顶int型数值存入第一个本地变量
        istore_0        = 59,
    // 	将栈顶int型数值存入第二个本地变量
        istore_1        = 60,
    // 将栈顶int型数值存入第三个本地变量
        istore_2        = 61,
    // 将栈顶int型数值存入第四个本地变量
        istore_3        = 62,
    // 将栈顶long型数值存入第一个本地变量
        lstore_0        = 63,
    // 将栈顶long型数值存入第二个本地变量
        lstore_1        = 64,
    // 将栈顶long型数值存入第三个本地变量
        lstore_2        = 65,
    // 将栈顶long型数值存入第四个本地变量
        lstore_3        = 66,
    // 将栈顶float型数值存入第一个本地变量
        fstore_0        = 67,
    // 将栈顶float型数值存入第二个本地变量
        fstore_1        = 68,
    // 将栈顶float型数值存入第三个本地变量
        fstore_2        = 69,
    // 将栈顶float型数值存入第四个本地变量
        fstore_3        = 70,
    // 将栈顶double型数值存入第一个本地变量
        dstore_0        = 71,
    // 将栈顶double型数值存入第二个本地变量
        dstore_1        = 72,
    // 将栈顶double型数值存入第三个本地变量
        dstore_2        = 73,
    // 将栈顶double型数值存入第四个本地变量
        dstore_3        = 74,
    // 将栈顶引用型数值存入第一个本地变量
        astore_0        = 75,
    // 将栈顶引用型数值存入第二个本地变量
        astore_1        = 76,
    // 将栈顶引用型数值存入第三个本地变量
        astore_2        = 77,
    // 将栈顶引用型数值存入第四个本地变量
        astore_3        = 78,
    // 将栈顶int型数值存入指定数组的指定索引位置
        iastore         = 79,
    // 将栈顶long型数值存入指定数组的指定索引位置
        lastore         = 80,
    // 将栈顶float型数值存入指定数组的指定索引位置
        fastore         = 81,
    // 将栈顶double型数值存入指定数组的指定索引位置
        dastore         = 82,
    // 将栈顶引用型数值存入指定数组的指定索引位置
        aastore         = 83,
    // 将栈顶boolean或byte型数值存入指定数组的指定索引位置
        bastore         = 84,
    // 将栈顶char型数值存入指定数组的指定索引位置
        castore         = 85,
    // 将栈顶short型数值存入指定数组的指定索引位置
        sastore         = 86,
    // 将栈顶数值弹出(数值不能是long或double类型的)
        pop             = 87,
    // 将栈顶的一个(非long或double类型)或两个数值
    // (非long或double的其他类型)弹出
        pop2            = 88,
    // 复制栈顶数值并将复制值压入栈顶
        dup             = 89,
    // 复制栈顶数值并将两个复制值压入栈顶
        dup_x1          = 90,
    // 复制栈顶数值并将三个(或两个)复制值压入栈顶
        dup_x2          = 91,
    // 复制栈顶一个(long或double类型)或两个
    // (非long或double的其他类型)数值并将复制值压入栈顶
        dup2            = 92,
    // dup_x1指令的双倍版本
        dup2_x1         = 93,
    // 	dup_x2指令的双倍版本
        dup2_x2         = 94,
    // 将栈顶最顶端的两个数值互换(数值不能是long或double类型)
        swap            = 95,
    // 将栈顶两int型数值相加并将结果压入栈顶
        iadd            = 96,
    // 将栈顶两long型数值相加并将结果压入栈顶
        ladd            = 97,
    // 将栈顶两float型数值相加并将结果压入栈顶
        fadd            = 98,
    // 将栈顶两double型数值相加并将结果压入栈顶
        dadd            = 99,
    // 将栈顶两int型数值相减并将结果压入栈顶
        isub            = 100,
    // 将栈顶两long型数值相减并将结果压入栈顶
        lsub            = 101,
    // 将栈顶两float型数值相减并将结果压入栈顶
        fsub            = 102,
    // 将栈顶两double型数值相减并将结果压入栈顶
        dsub            = 103,
    // 将栈顶两int型数值相乘并将结果压入栈顶
        imul            = 104,
    // 将栈顶两long型数值相乘并将结果压入栈顶
        lmul            = 105,
    // 将栈顶两float型数值相乘并将结果压入栈顶
        fmul            = 106,
    // 将栈顶两double型数值相乘并将结果压入栈顶
        dmul            = 107,
    // 将栈顶两int型数值相除并将结果压入栈顶
        idiv            = 108,
    // 将栈顶两long型数值相除并将结果压入栈顶
        ldiv            = 109,
    // 将栈顶两float型数值相除并将结果压入栈顶
        fdiv            = 110,
    // 将栈顶两double型数值相除并将结果压入栈顶
        ddiv            = 111,
    // 将栈顶两int型数值作取模运算并将结果压入栈顶
        imod            = 112,
    // 将栈顶两long型数值作取模运算并将结果压入栈顶
        lmod            = 113,
    // 将栈顶两float型数值作取模运算并将结果压入栈顶
        fmod            = 114,
    // 将栈顶两double型数值作取模运算并将结果压入栈顶
        dmod            = 115,
    // 将栈顶int型数值取负并将结果压入栈顶
        ineg            = 116,
    // 将栈顶long型数值取负并将结果压入栈顶
        lneg            = 117,
    // 将栈顶float型数值取负并将结果压入栈顶
        fneg            = 118,
    // 将栈顶double型数值取负并将结果压入栈顶
        dneg            = 119,
    // 将int型数值左移指定位数并将结果压入栈顶
        ishl            = 120,
    // 将long型数值左移指定位数并将结果压入栈顶
        lshl            = 121,
    // 将int型数值右(带符号)移指定位数并将结果压入栈顶
        ishr            = 122,
    // 将long型数值右(带符号)移指定位数并将结果压入栈顶
        lshr            = 123,
    // 将int型数值右(无符号)移指定位数并将结果压入栈顶
        iushr           = 124,
    // 将long型数值右(无符号)移指定位数并将结果压入栈顶
        lushr           = 125,
    // 将栈顶两int型数值"按位与"并将结果压入栈顶
        iand            = 126,
    // 将栈顶两long型数值"按位与"并将结果压入栈顶
        land            = 127,
    // 将栈顶两int型数值"按位或"并将结果压入栈顶
        ior             = 128,
    // 将栈顶两long型数值"按位或"并将结果压入栈顶
        lor             = 129,
    // 将栈顶两int型数值"按位异或"并将结果压入栈顶
        ixor            = 130,
    // 将栈顶两long型数值"按位异或"并将结果压入栈顶
        lxor            = 131,
    // 将指定int型变量增加指定值(如i++, i–, i+=2等)
        iinc            = 132,
    // 将栈顶int型数值强制转换为long型数值并将结果压入栈顶
        i2l             = 133,
    // 将栈顶int型数值强制转换为float型数值并将结果压入栈顶
        i2f             = 134,
    // 将栈顶int型数值强制转换为double型数值并将结果压入栈顶
        i2d             = 135,
    // 将栈顶long型数值强制转换为int型数值并将结果压入栈顶
        l2i             = 136,
    // 将栈顶long型数值强制转换为float型数值并将结果压入栈顶
        l2f             = 137,
    // 将栈顶long型数值强制转换为double型数值并将结果压入栈顶
        l2d             = 138,
    // 将栈顶float型数值强制转换为int型数值并将结果压入栈顶
        f2i             = 139,
    // 将栈顶float型数值强制转换为long型数值并将结果压入栈顶
        f2l             = 140,
    // 将栈顶float型数值强制转换为double型数值并将结果压入栈顶
        f2d             = 141,
    // 将栈顶double型数值强制转换为int型数值并将结果压入栈顶
        d2i             = 142,
    // 将栈顶double型数值强制转换为long型数值并将结果压入栈顶
        d2l             = 143,
    // 将栈顶double型数值强制转换为float型数值并将结果压入栈顶
        d2f             = 144,
    // 将栈顶int型数值强制转换为byte型数值并将结果压入栈顶
        int2byte        = 145,
    // 将栈顶int型数值强制转换为char型数值并将结果压入栈顶
        int2char        = 146,
    // 将栈顶int型数值强制转换为short型数值并将结果压入栈顶
        int2short       = 147,
    // 比较栈顶两long型数值大小, 并将结果(1, 0或-1)压入栈顶
        lcmp            = 148,
    // 比较栈顶两float型数值大小, 并将结果(1, 0或-1)压入栈顶;
    // 当其中一个数值为NaN时, 将-1压入栈顶
        fcmpl           = 149,
    // 比较栈顶两float型数值大小, 并将结果(1, 0或-1)压入栈顶;
    // 当其中一个数值为NaN时, 将1压入栈顶
        fcmpg           = 150,
    // 比较栈顶两double型数值大小, 并将结果(1, 0或-1)压入栈顶;
    // 当其中一个数值为NaN时, 将-1压入栈顶
        dcmpl           = 151,
    // 比较栈顶两double型数值大小, 并将结果(1, 0或-1)压入栈顶;
    // 当其中一个数值为NaN时, 将1压入栈顶
        dcmpg           = 152,
    // 逻辑相反的一对指令的编码相邻，如ifeq和ifne的编码是153和154，
    // 并且第一个指令的编码为奇数
    // 调用negate()方法可以获取与自身逻辑相反的指令编码
    // 当栈顶int型数值等于0时跳转
        ifeq            = 153,
    // 当栈顶int型数值不等于0时跳转
        ifne            = 154,
    // 当栈顶int型数值小于0时跳转
        iflt            = 155,
    // 当栈顶int型数值大于等于0时跳转
        ifge            = 156,
    // 当栈顶int型数值大于0时跳转
        ifgt            = 157,
    // 当栈顶int型数值小于等于0时跳转
        ifle            = 158,
    // 比较两个值是，后一个值在栈顶，前一个值在后一个值后面
    // 比较栈顶两int型数值大小, 当结果等于0时跳转
        if_icmpeq       = 159,
    // 比较栈顶两int型数值大小, 当结果不等于0时跳转
        if_icmpne       = 160,
    // 比较栈顶两int型数值大小, 当结果小于0时跳转
        if_icmplt       = 161,
    // 比较栈顶两int型数值大小, 当结果大于等于0时跳转
        if_icmpge       = 162,
    // 比较栈顶两int型数值大小, 当结果大于0时跳转
        if_icmpgt       = 163,
    // 比较栈顶两int型数值大小, 当结果小于等于0时跳转
        if_icmple       = 164,
    // 比较栈顶两引用型数值, 当结果相等时跳转
        if_acmpeq       = 165,
    // 比较栈顶两引用型数值, 当结果不相等时跳转
        if_acmpne       = 166,
    // 无条件跳转
        goto_           = 167,
    // 	跳转至指定的16位offset位置, 并将jsr的下一条指令地址压入栈顶
        jsr             = 168,
    // 返回至本地变量指定的index的指令位置(一般与jsr或jsr_w联合使用)
        ret             = 169,
    // 用于switch条件跳转, case值连续(可变长度指令)
    // tableswitch指令根据键值在跳转表中寻找配对的分支并跳转
    // 例17-10
        tableswitch     = 170,
    // 	用于switch条件跳转, case值不连续(可变长度指令)
    //  lookupswitch指令根据键值在跳转表中寻找配对的分支并跳转
    //  例17-10
        lookupswitch    = 171,
    // 从当前方法返回int
        ireturn         = 172,
    // 从当前方法返回int
        lreturn         = 173,
    // 从当前方法返回float
        freturn         = 174,
    // 从当前方法返回double
        dreturn         = 175,
    // 从当前方法返回对象引用
        areturn         = 176,
    // 从当前方法返回对象引用
        return_         = 177,
    // 获取指定类的静态域, 并将其压入栈顶
        getstatic       = 178,
    // 	为指定类的静态域赋值
        putstatic       = 179,
    // 获取指定类的实例域, 并将其压入栈顶
        getfield        = 180,
    // 	为指定类的实例域赋值
        putfield        = 181,
    // 调用实例方法
        invokevirtual   = 182,
    // 调用超类构建方法, 实例初始化方法, 私有方法
        invokespecial   = 183,
    // 调用静态方法
        invokestatic    = 184,
    // 调用接口方法
        invokeinterface = 185,
    // 调用动态方法
        invokedynamic   = 186,
    // 创建一个对象, 并将其引用引用值压入栈顶
        new_            = 187,
    // 创建一个指定的原始类型(如int, float, char等)的数组,
    // 并将其引用值压入栈顶
        newarray        = 188,
    // 创建一个引用型(如类, 接口, 数组)的数组, 并将其引用值压入栈顶
        anewarray       = 189,
    // 获取数组的长度值并压入栈顶
        arraylength     = 190,
    // 将栈顶的异常抛出
        athrow          = 191,
    // 检验类型转换, 检验未通过将抛出 ClassCastException
        checkcast       = 192,
    // 检验对象是否是指定类的实际, 如果是将1压入栈顶, 否则将0压入栈顶
        instanceof_     = 193,
    // 获得对象的锁, 用于同步方法或同步块
        monitorenter    = 194,
    // 释放对象的锁, 用于同步方法或同步块
        monitorexit     = 195,
    // 扩展本地变量的宽度
        wide            = 196,
    // 创建指定类型和指定维度的多维数组(执行该指令时,
    // 操作栈中必须包含各维度的长度值), 并将其引用压入栈顶
        multianewarray  = 197,
    // 为null时跳转
        if_acmp_null    = 198,
    // 不为null时跳转
        if_acmp_nonnull = 199,
    // 无条件跳转(宽索引)
        goto_w          = 200,
    // 跳转至指定的32位offset位置, 并将jsr_w的下一条指令地址压入栈顶
        jsr_w           = 201,
    //
        breakpoint      = 202,
    //
        ByteCodeCount   = 203;

    /** Virtual instruction codes; used for constant folding.
     */
    int string_add      = 256,  // string +
        bool_not        = 257,  // boolean !
        bool_and        = 258,  // boolean &&
        bool_or         = 259;  // boolean ||

    /** Virtual opcodes; used for shifts with long shiftcount
     */
    int ishll           = 270,  // int shift left with long count
        lshll           = 271,  // long shift left with long count
        ishrl           = 272,  // int shift right with long count
        lshrl           = 273,  // long shift right with long count
        iushrl          = 274,  // int unsigned shift right with long count
        lushrl          = 275;  // long unsigned shift right with long count

    /** Virtual opcode for null reference checks
     */
    int nullchk         = 276;  // return operand if non-null,
                                // otherwise throw NullPointerException.

    /** Virtual opcode for disallowed operations.
     */
    int error           = 277;

    /** All conditional jumps come in pairs. To streamline the
     *  treatment of jumps, we also introduce a negation of an
     *  unconditional jump. That opcode happens to be jsr.
     */
    int dontgoto        = jsr;

    /** Shift and mask constants for shifting prefix instructions.
     *  a pair of instruction codes such as LCMP ; IFEQ is encoded
     *  in Symtab as (LCMP << preShift) + IFEQ.
     */
    int preShift        = 9;
    int preMask         = (1 << preShift) - 1;

    /** Type codes.
     */
    int INTcode         = 0,
        LONGcode        = 1,
        FLOATcode       = 2,
        DOUBLEcode      = 3,
        OBJECTcode      = 4,
        BYTEcode        = 5,
        CHARcode        = 6,
        SHORTcode       = 7,
        VOIDcode        = 8,
        // 最后一个变量TypeCodeCount表示Java虚拟机支持的类型共有9个
        TypeCodeCount   = 9;

    static final String[] typecodeNames = {
        "int",
        "long",
        "float",
        "double",
        "object",
        "byte",
        "char",
        "short",
        "void",
        "oops"
    };
}
