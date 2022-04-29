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

import java.lang.ref.SoftReference;

/**
 * Implementation of Name.Table that stores all names in a single shared
 * byte array, expanding it as needed. This avoids the overhead incurred
 * by using an array of bytes for each name.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
// 所有的NameImpl对象全部存储到了SharedNameTable类的hashes数组中
public class SharedNameTable extends Name.Table {
    // maintain a freelist of recently used name tables for reuse.
    private static List<SoftReference<SharedNameTable>> freelist = List.nil();
    /**
     * The shared byte array holding all encountered names.
     */
    // bytes数组将统一存储所有的NameImpl对象中需要存储的多个字符
    // 通过起始位置的偏移index和字节所占的长度length来指定具体的存放位置
    public byte[] bytes;
    /**
     * The hash table for names.
     */
    // 存储Name对象
    // 通过计算NameImpl对象的哈希值将其存储到hashes数组的特定位置
    // 如果出现冲突，就使用NameImpl类中定义的next变量将冲突的对象链接成单链表的形式
    private NameImpl[] hashes;
    /**
     * The mask to be used for hashing
     */
    // 多个NameImpl对象使用哈希存储，在计算哈希值时会使用hashMask来辅助计算
    private int hashMask;
    /**
     * The number of filled bytes in `names'.
     */
    // nc保存了bytes数组中下一个可用的位置，初始值为0
    private int nc = 0;

    /**
     * Allocator
     *
     * @param names    The main name table
     * @param hashSize the (constant) size to be used for the hash table
     *                 needs to be a power of two.
     * @param nameSize the initial size of the name table.
     */
    // 对各个变量进行初始化
    public SharedNameTable(Names names, int hashSize, int nameSize) {
        super(names);
        hashMask = hashSize - 1;
        hashes = new NameImpl[hashSize];
        bytes = new byte[nameSize];

    }

    // 构建SharedNameTable对象
    public SharedNameTable(Names names) {
        this(names, 0x8000, 0x20000);
    }

    static public synchronized SharedNameTable create(Names names) {
        while (freelist.nonEmpty()) {
            SharedNameTable t = freelist.head.get();
            freelist = freelist.tail;
            if (t != null) {
                return t;
            }
        }
        return new SharedNameTable(names);
    }

    static private synchronized void dispose(SharedNameTable t) {
        freelist = freelist.prepend(new SoftReference<SharedNameTable>(t));
    }

    @Override
    // 将字符数组映射为Name对象
    // cs一般是字符串调用toCharArray()方法转换来的字符数组
    // fromChars()方法兼有存储和查找的功能
    // 通过fromChars()方法后Javac就可以用Name对象来表示特定的字符数组或者说字符串了
    public Name fromChars(char[] cs, int start, int len) {
        // 参数start与length表示从cs的start下标开始取length个字符进行处理。
        int nc = this.nc;
        byte[] bytes = this.bytes;
        // 扩容操作
        while (nc + len * 3 >= bytes.length) {
            //          System.err.println("doubling name buffer of length " + names.length + " to fit " + len + " chars");//DEBUG
            byte[] newnames = new byte[bytes.length * 2];
            System.arraycopy(bytes, 0, newnames, 0, bytes.length);
            bytes = this.bytes = newnames;
        }
        // 计算字符数组要存储到字节数组时所需要占用的字节长度
        // 将cs追加到bytes的最后
        int nbytes = Convert.chars2utf(cs, start, bytes, nc, len) - nc;
        // 得到存储在hashes数组中的槽位值
        int h = hashValue(bytes, nc, nbytes) & hashMask;
        NameImpl n = hashes[h];
        // 如果产生冲突，使用next讲冲突元素链接起来
        // 对应槽位上的值不为空并且与当前要保存的内容不同，则使用单链表来解决冲突
        while (n != null &&
                (n.getByteLength() != nbytes ||
                        !equals(bytes, n.index, bytes, nc, nbytes))) {
            n = n.next;
        }
        // 创建新的NameImpl对象
        if (n == null) {
            n = new NameImpl(this);
            // 起始位置的偏移量
            n.index = nc;
            // 字节所占的长度
            n.length = nbytes;
            // n.next置空
            n.next = hashes[h];
            // 存在数组里
            hashes[h] = n;
            this.nc = nc + nbytes;
            if (nbytes == 0) {
                this.nc++;
            }
        }
        return n;
    }

    @Override
    public Name fromUtf(byte[] cs, int start, int len) {
        int h = hashValue(cs, start, len) & hashMask;
        NameImpl n = hashes[h];
        byte[] names = bytes;
        while (n != null &&
                (n.getByteLength() != len || !equals(names, n.index, cs, start, len))) {
            n = n.next;
        }
        if (n == null) {
            int nc = this.nc;
            while (nc + len > names.length) {
                //              System.err.println("doubling name buffer of length + " + names.length + " to fit " + len + " bytes");//DEBUG
                byte[] newnames = new byte[names.length * 2];
                System.arraycopy(names, 0, newnames, 0, names.length);
                names = bytes = newnames;
            }
            System.arraycopy(cs, start, names, nc, len);
            n = new NameImpl(this);
            n.index = nc;
            n.length = len;
            n.next = hashes[h];
            hashes[h] = n;
            this.nc = nc + len;
            if (len == 0) {
                this.nc++;
            }
        }
        return n;
    }

    @Override
    public void dispose() {
        dispose(this);
    }

    // Token类中定义的所有Token对象中，除去没有name的Token对象，
    // 每个Token对象的name都可以用一个NameImpl对象来表示
    static class NameImpl extends Name {
        /**
         * The next name occupying the same hash bucket.
         */
        NameImpl next;

        /**
         * The index where the bytes of this name are stored in the global name
         * buffer `byte'.
         */
        // 起始位置的偏移量
        int index;

        /**
         * The number of bytes in this name.
         */
        // 字节所占的长度
        int length;

        NameImpl(SharedNameTable table) {
            super(table);
        }

        @Override
        public int getIndex() {
            return index;
        }

        @Override
        public int getByteLength() {
            return length;
        }

        @Override
        public byte getByteAt(int i) {
            return getByteArray()[index + i];
        }

        @Override
        public byte[] getByteArray() {
            return ((SharedNameTable) table).bytes;
        }

        @Override
        public int getByteOffset() {
            return index;
        }

        /**
         * Return the hash value of this name.
         */
        @Override
        public int hashCode() {
            return index;
        }

        /**
         * Is this name equal to other?
         */
        @Override
        public boolean equals(Object other) {
            if (other instanceof Name) {
                return
                        table == ((Name) other).table && index == ((Name) other).getIndex();
            } else {
                return false;
            }
        }

    }

}
