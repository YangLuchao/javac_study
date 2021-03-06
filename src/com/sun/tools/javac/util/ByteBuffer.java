/*
 * Copyright (c) 1999, 2008, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.util;

import java.io.*;

/** A byte buffer is a flexible array which grows when elements are
 *  appended. There are also methods to append names to byte buffers
 *  and to convert byte buffers to names.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
// Class文件中存储了两种数据类型：无符号数和表。
// 表是用来描述有层次关系的复合结构的数据，而无符号数可以用来标识一个具体结构的类型，或者还可以表示数量及属性长度等
// ByteBuffer对象表示一个具体的缓冲，主要通过elems来保存缓冲的内容，Javac在向Class文件中写入字节码时不会一个字节一个字节地写，
// 而是先写入ByteBuffer缓冲中，然后一次性写入Class文件来提高写入的效率。
public class ByteBuffer {

    /** An array holding the bytes in this buffer; can be grown.
     */
    // 向字节数组elems中写入一个字节的内容
    public byte[] elems;

    /** The current number of defined bytes in this buffer.
     */
    // length保存着数组中写入的字节数量
    public int length;

    /** Create a new byte buffer.
     */
    public ByteBuffer() {
        this(64);
    }

    /** Create a new byte buffer with an initial elements array
     *  of given size.
     */
    public ByteBuffer(int initialSize) {
        // 在ByteBuffer类的构造方法中初始化elems
        elems = new byte[initialSize];
        length = 0;
    }

    private void copy(int size) {
        byte[] newelems = new byte[size];
        System.arraycopy(elems, 0, newelems, 0, elems.length);
        elems = newelems;
    }

    /** Append byte to this buffer.
     */
    // 写入基本类型的常用方法
    public void appendByte(int b) {
        if (length >= elems.length)
            copy(elems.length * 2);
        elems[length++] = (byte)b;
    }

    /** Append `len' bytes from byte array,
     *  starting at given `start' offset.
     */
    public void appendBytes(byte[] bs, int start, int len) {
        while (length + len > elems.length)
            copy(elems.length * 2);
        System.arraycopy(bs, start, elems, length, len);
        length += len;
    }

    /** Append all bytes from given byte array.
     */
    public void appendBytes(byte[] bs) {
        appendBytes(bs, 0, bs.length);
    }

    /** Append a character as a two byte number.
     */
    // 在写入一个占用2个字节的无符号整数时，可按照高位在前的方式写入，
    // 并且写入的每个字节都要和0xFF做与运算，主要是为了防止符号扩展，严格保持字节存储时的样子。
    public void appendChar(int x) {
        while (length + 1 >= elems.length)
            copy(elems.length * 2);
        elems[length  ] = (byte)((x >>  8) & 0xFF);
        elems[length+1] = (byte)((x      ) & 0xFF);
        length = length + 2;
    }

    /** Append an integer as a four byte number.
     */
    public void appendInt(int x) {
        while (length + 3 >= elems.length) copy(elems.length * 2);
        elems[length  ] = (byte)((x >> 24) & 0xFF);
        elems[length+1] = (byte)((x >> 16) & 0xFF);
        elems[length+2] = (byte)((x >>  8) & 0xFF);
        elems[length+3] = (byte)((x      ) & 0xFF);
        length = length + 4;
    }

    /** Append a long as an eight byte number.
     */
    public void appendLong(long x) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(8);
        DataOutputStream bufout = new DataOutputStream(buffer);
        try {
            bufout.writeLong(x);
            appendBytes(buffer.toByteArray(), 0, 8);
        } catch (IOException e) {
            throw new AssertionError("write");
        }
    }

    /** Append a float as a four byte number.
     */
    public void appendFloat(float x) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(4);
        DataOutputStream bufout = new DataOutputStream(buffer);
        try {
            bufout.writeFloat(x);
            appendBytes(buffer.toByteArray(), 0, 4);
        } catch (IOException e) {
            throw new AssertionError("write");
        }
    }

    /** Append a double as a eight byte number.
     */
    public void appendDouble(double x) {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream(8);
        DataOutputStream bufout = new DataOutputStream(buffer);
        try {
            bufout.writeDouble(x);
            appendBytes(buffer.toByteArray(), 0, 8);
        } catch (IOException e) {
            throw new AssertionError("write");
        }
    }

    /** Append a name.
     */
    public void appendName(Name name) {
        appendBytes(name.getByteArray(), name.getByteOffset(), name.getByteLength());
    }

    /** Reset to zero length.
     */
    public void reset() {
        length = 0;
    }

    /** Convert contents to name.
     */
    public Name toName(Names names) {
        return names.fromUtf(elems, 0, length);
    }
}
