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

package com.sun.tools.javac.util;

/** A class for extensible, mutable bit sets.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
// 位操作的类
public class Bits {

    // 由于一个int类型只有32位，所以如果要跟踪的变量的数量大于32时就需要更多的int类型的数来表示，这些数都按顺序存储到bits数组中
    private final static int wordlen = 32;
    private final static int wordshift = 5;
    private final static int wordmask = wordlen - 1;

    // bits数组用来保存位的相关信息
    // 一般在构造方法中初始化为大小为1的int数组
    private int[] bits;

    /** Construct an initially empty set.
     */
    public Bits() {
        this(new int[1]);
    }

    /** Construct a set consisting initially of given bit vector.
     */
    public Bits(int[] bits) {
        this.bits = bits;
    }

    /** Construct a set consisting initially of given range.
     */
    public Bits(int start, int limit) {
        this();
        inclRange(start, limit);
    }

    // bits的长度小于len扩容操作
    // 在创建Bits对象时通常会在构造方法中将bits初始化为大小为1的数组，所以如果存储48，将会扩容为大小为2的数组
    private void sizeTo(int len) {
        if (bits.length < len) {
            int[] newbits = new int[len];
            System.arraycopy(bits, 0, newbits, 0, bits.length);
            bits = newbits;
        }
    }

    /** This set = {}.
     */
    public void clear() {
        for (int i = 0; i < bits.length; i++) bits[i] = 0;
    }

    /** Return a copy of this set.
     */
    // 复制一份当前的Bits对象并返回
    public Bits dup() {
        int[] newbits = new int[bits.length];
        System.arraycopy(bits, 0, newbits, 0, bits.length);
        return new Bits(newbits);
    }

    /** Include x in this set.
     */
    // 将x放入bits中
    public void incl(int x) {
        Assert.check(x >= 0);
        // 通过(x>>>wordshift)+1计算存储x需要的数组大小，即需要多少个整数的位
        // 例如要存储48，也就是将第48上的位设置为1，这时候计算出来的值为2，表示需要用两个整数来存储
        sizeTo((x >>> wordshift) + 1);
        // x>>>wordshift计算x保存到数组中的哪个整数的位中
        // bits[x>>>wordshift]|(1<<(x&wordmask))将之前存储的相关信息与当前的信息取或，保证之前保存的相关信息不丢失
        bits[x >>> wordshift] = bits[x >>> wordshift] |
            (1 << (x & wordmask));
    }


    /** Include [start..limit) in this set.
     */
    // 将第start位到第start+limit位的所有位都设置为1，包括第start位，不包括第start+limit位。
    public void inclRange(int start, int limit) {
        sizeTo((limit >>> wordshift) + 1);
        for (int x = start; x < limit; x++)
            bits[x >>> wordshift] = bits[x >>> wordshift] |
                (1 << (x & wordmask));
    }

    /** Exclude [start...end] from this set.
     */
    // 将从第start位开始到最后一位的所有位都设置为0，包括最后一位
    public void excludeFrom(int start) {
        Bits temp = new Bits();
        temp.sizeTo(bits.length);
        temp.inclRange(0, start);
        andSet(temp);
    }

    /** Exclude x from this set.
     */
    // 将x在bits中排出
    // 将第x位上的数设置为0
    public void excl(int x) {
        Assert.check(x >= 0);
        sizeTo((x >>> wordshift) + 1);
        bits[x >>> wordshift] = bits[x >>> wordshift] &
            ~(1 << (x & wordmask));
    }

    /** Is x an element of this set?
     */
    // 判断bits是否包含该元素
    // 由于bits数组有一定大小，所以如果bits数组大小为2，则2个整数最多有64个可用位，查询参数x不能大于64，判断条件x<(bits.length<<wordshift)就是保证查询参数不能超出当前可用位的数量。通过bits[x>>>wordshif]取出相关的整数后与对应的位执行与操作，如果不为0，则说明相应位为1，x是当前Bits对象的成员
    public boolean isMember(int x) {
        return
            0 <= x && x < (bits.length << wordshift) &&
            (bits[x >>> wordshift] & (1 << (x & wordmask))) != 0;
    }

    /** this set = this set & xs.
     */
    public Bits andSet(Bits xs) {
        sizeTo(xs.bits.length);
        for (int i = 0; i < xs.bits.length; i++)
            bits[i] = bits[i] & xs.bits[i];
        return this;
    }

    /** this set = this set | xs.
     */
    // 将当前的Bits对象与传入的xs做或操作，返回操作后的结果。
    public Bits orSet(Bits xs) {
        sizeTo(xs.bits.length);
        for (int i = 0; i < xs.bits.length; i++)
            bits[i] = bits[i] | xs.bits[i];
        return this;
    }

    /** this set = this set \ xs.
     */
    // 操作当前的Bits对象，如果与传入的xs对应位上的值相同，将当前Bits对象对应位置为0，否则保持不变，
    // 如当前的Bits对象为001，与110操作后的结果为001
    public Bits diffSet(Bits xs) {
        for (int i = 0; i < bits.length; i++) {
            if (i < xs.bits.length) {
                bits[i] = bits[i] & ~xs.bits[i];
            }
        }
        return this;
    }

    /** this set = this set ^ xs.
     */
    public Bits xorSet(Bits xs) {
        sizeTo(xs.bits.length);
        for (int i = 0; i < xs.bits.length; i++)
            bits[i] = bits[i] ^ xs.bits[i];
        return this;
    }

    /** Count trailing zero bits in an int. Algorithm from "Hacker's
     *  Delight" by Henry S. Warren Jr. (figure 5-13)
     */
    private static int trailingZeroBits(int x) {
        Assert.check(wordlen == 32);
        if (x == 0) return 32;
        int n = 1;
        if ((x & 0xffff) == 0) { n += 16; x >>>= 16; }
        if ((x & 0x00ff) == 0) { n +=  8; x >>>=  8; }
        if ((x & 0x000f) == 0) { n +=  4; x >>>=  4; }
        if ((x & 0x0003) == 0) { n +=  2; x >>>=  2; }
        return n - (x&1);
    }

    /** Return the index of the least bit position >= x that is set.
     *  If none are set, returns -1.  This provides a nice way to iterate
     *  over the members of a bit set:
     *  <pre>
     *  for (int i = bits.nextBit(0); i>=0; i = bits.nextBit(i+1)) ...
     *  </pre>
     */
    // 从第x位开始查找下一个为1的位，返回这个位的位置，如果不存在，返回-1。
    // Javac在实现过程中，经常会使用nextBit()方法遍历所有值为1的位，例如
    // for (int i = bits.nextBit(0); i>=0; i = bits.nextBit(i+1)) ...
    // bits为Bits对象，从第0位开始遍历所有为1的位，如果i为-1，则结束循环
    public int nextBit(int x) {
        int windex = x >>> wordshift;
        if (windex >= bits.length) return -1;
        int word = bits[windex] & ~((1 << (x & wordmask))-1);
        while (true) {
            if (word != 0)
                return (windex << wordshift) + trailingZeroBits(word);
            windex++;
            if (windex >= bits.length) return -1;
            word = bits[windex];
        }
    }

    /** a string representation of this set.
     */
    public String toString() {
        char[] digits = new char[bits.length * wordlen];
        for (int i = 0; i < bits.length * wordlen; i++)
            digits[i] = isMember(i) ? '1' : '0';
        return new String(digits);
    }

    /** Test Bits.nextBit(int). */
    public static void main(String[] args) {
        java.util.Random r = new java.util.Random();
        Bits bits = new Bits();
        int dupCount = 0;
        for (int i=0; i<125; i++) {
            int k;
            do {
                k = r.nextInt(250);
            } while (bits.isMember(k));
            System.out.println("adding " + k);
            bits.incl(k);
        }
        int count = 0;
        for (int i = bits.nextBit(0); i >= 0; i = bits.nextBit(i+1)) {
            System.out.println("found " + i);
            count ++;
        }
        if (count != 125) throw new Error();
    }
}
