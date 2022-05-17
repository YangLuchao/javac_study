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

package com.sun.tools.javac.jvm;

import java.util.*;

import com.sun.tools.javac.code.Symbol.*;

/** An internal structure that corresponds to the constant pool of a classfile.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
// Pool类代表常量池，可以存储常量池相关的信息，为后续Class字节码文件中常量池的生成提供必要的数据
public class Pool {

    public static final int MAX_ENTRIES = 0xFFFF;
    public static final int MAX_STRING_LENGTH = 0xFFFF;

    /** Index of next constant to be entered.
     */
    // pp指向pool数组中下一个可用的位置
    // pp也可以表示pool数组中存储数据的数量
    int pp;

    /** The initial pool buffer.
     */
    // 用于存储常量池中不同类型的数据对象
    Object[] pool;

    /** A hashtable containing all constants in the pool.
     */
    // indices保存了pool数组中所有对象到这个数组下标的映射
    // 在字节码指令生成过程中，需要频繁查找某个对象在常量池中的下标，因此为了提高查找效率，使用了Map结构来保存映射关系。
    Map<Object,Integer> indices;

    /** Construct a pool with given number of elements and element array.
     */
    public Pool(int pp, Object[] pool) {
        // 一般初始化为1，也就是数组的可用下标从1开始，0不存储任何数据，
        // 这样主要是为了做到与Class中常量池的规定一致，即索引值为0的位置代表不引用任何值
        this.pp = pp;
        this.pool = pool;
        this.indices = new HashMap<Object,Integer>(pool.length);
        for (int i = 1; i < pp; i++) {
            if (pool[i] != null) indices.put(pool[i], i);
        }
    }

    /** Construct an empty pool.
     */
    public Pool() {
        this(1, new Object[64]);
    }

    /** Return the number of entries in the constant pool.
     */
    public int numEntries() {
        return pp;
    }

    /** Remove everything from this pool.
     */
    public void reset() {
        pp = 1;
        indices.clear();
    }

    /** Double pool buffer in size.
     */
    // 常量池扩容
    private void doublePool() {
        Object[] newpool = new Object[pool.length * 2];
        System.arraycopy(pool, 0, newpool, 0, pool.length);
        pool = newpool;
    }

    /** Place an object in the pool, unless it is already there.
     *  If object is a symbol also enter its owner unless the owner is a
     *  package.  Return the object's index in the pool.
     */
    // put()方法向常量池中放入某个对象并返回这个对象在常量池中存储的索引
    public int put(Object value) {
        // 如果在常量池中存储的是MethodSymbol或VarSymbol对象，还需要分别封装为Method对象与Variable对象，
        // 因为两个对象要作为key存储到Map<Object,Integer>对象indices中，所以要重新覆写hashCode()与equals()方法
        if (value instanceof MethodSymbol)
            value = new Method((MethodSymbol)value);
        else if (value instanceof VarSymbol)
            value = new Variable((VarSymbol)value);
        Integer index = indices.get(value);
        // 判断value是否已经存在于常量池中，如果index为null则表示不存在
        if (index == null) {
            index = pp;
            // 向indices及pool数组中存储value
            indices.put(value, index);
            if (pp == pool.length)
                doublePool();
            pool[pp++] = value;
            if (value instanceof Long || value instanceof Double) {
                if (pp == pool.length)
                    doublePool();
                pool[pp++] = null;
            }
        }
        return index.intValue();
    }

    /** Return the given object's index in the pool,
     *  or -1 if object is not in there.
     */
    // get()方法可以获取常量池中某个对象的常量池索引
    public int get(Object o) {
        Integer n = indices.get(o);
        // 如果常量池中没有存储这个对象，将会返回-1。
        return n == null ? -1 : n.intValue();
    }

    static class Method extends DelegatedSymbol {
        MethodSymbol m;
        Method(MethodSymbol m) {
            super(m);
            this.m = m;
        }
        public boolean equals(Object other) {
            if (!(other instanceof Method))
                return false;
            MethodSymbol o = ((Method)other).m;
            // 两个Method对象在比较时需要比较name、owner及type，其中，name与owner直接使用等号比较即可
            // Name类的实现机制，如果方法名称相同，一定是同一个Name对象，可使用等号来提高比较效率
            // 同一个类型定义一定会使用同一个符号来表示，因此如果方法定义在同一个类型中，owner也一定是同一个对象，直接使用等号比较即可
            return
                o.name == m.name &&
                o.owner == m.owner &&
                o.type.equals(m.type);
        }
        public int hashCode() {
            return
                m.name.hashCode() * 33 +
                m.owner.hashCode() * 9 +
                m.type.hashCode();
        }
    }

    static class Variable extends DelegatedSymbol {
        VarSymbol v;
        Variable(VarSymbol v) {
            super(v);
            this.v = v;
        }
        public boolean equals(Object other) {
            if (!(other instanceof Variable)) return false;
            VarSymbol o = ((Variable)other).v;
            return
                o.name == v.name &&
                o.owner == v.owner &&
                o.type.equals(v.type);
        }
        public int hashCode() {
            return
                v.name.hashCode() * 33 +
                v.owner.hashCode() * 9 +
                v.type.hashCode();
        }
    }
}
