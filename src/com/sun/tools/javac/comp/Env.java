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

package com.sun.tools.javac.comp;

import com.sun.tools.javac.tree.*;
import java.util.Iterator;
import java.util.NoSuchElementException;

/** A class for environments, instances of which are passed as
 *  arguments to tree visitors.  Environments refer to important ancestors
 *  of the subtree that's currently visited, such as the enclosing method,
 *  the enclosing class, or the enclosing toplevel node. They also contain
 *  a generic component, represented as a type parameter, to carry further
 *  information specific to individual passes.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
/*
Javac在实现过程中，为了能够体现出作用域的嵌套，以及为后序语句及表达式的分析提供更全面的上下文信息，
一般会在任意树节点的分析过程中伴随有Env、AttrContext与Scope对象。
Env对象可以保存当前树节点的关于抽象语法树的上下文信息
 */
public class Env<A> implements Iterable<Env<A>> {

    /** The next enclosing environment.
     */
    // 通过next与outer形成各个Env对象的嵌套
    // next指向了父节点所对应的Env对象
    public Env<A> next;

    /** The environment enclosing the current class.
     */
    // 通过next与outer形成各个Env对象的嵌套
    // outer指向当前节点所归属的JCClassDecl类型节点的父节点(当前节点的Env属于哪个class对象的Env)
    // outer变量最主要的作用就是结合AttrContext类中的staticLevel对静态环境进行判断
    public Env<A> outer;

    /** The tree with which this environment is associated.
     */
    // 当前节点的父节点
    // 因此对于树中的任何节点来说，当分析子节点时就需要创建父节点的Env对象
    public JCTree tree;

    /** The enclosing toplevel tree.
     */
    // 当前节点所属的编译单元(当前节点属于哪个编译单元，哪个Java文件)
    public JCTree.JCCompilationUnit toplevel;

    /** The next enclosing class definition.
     */
    // 当前节点所属的分析JCClassDecl类型的节点(当前节点属于那个类)
    public JCTree.JCClassDecl enclClass;

    /** The next enclosing method definition.
     */
    // 当前节点所属的JCMethodDecl类型的节点(当前节点属于那个方法)
    public JCTree.JCMethodDecl enclMethod;

    /** A generic field for further information.
     */
    // 通过Env对象的info变量来保存AttrContext对象，AttrContext对象中保存一些特殊的信息
    public A info;

    /** Is this an environment for evaluating a base clause?
     */
    // 说明这个Env对象是分析当前类型的父类、接口、类型的注解及类型声明的类型变量使用的上下文环境
    // 在分析其他的树节点时baseClause值为false
    public boolean baseClause = false;

    /** Create an outermost environment for a given (toplevel)tree,
     *  with a given info field.
     */
    public Env(JCTree tree, A info) {
        this.next = null;
        this.outer = null;
        this.tree = tree;
        this.toplevel = null;
        this.enclClass = null;
        this.enclMethod = null;
        this.info = info;
    }

    /** Duplicate this environment, updating with given tree and info,
     *  and copying all other fields.
     */
    public Env<A> dup(JCTree tree, A info) {
        return dupto(new Env<A>(tree, info));
    }

    /** Duplicate this environment into a given Environment,
     *  using its tree and info, and copying all other fields.
     */
    public Env<A> dupto(Env<A> that) {
        that.next = this;
        that.outer = this.outer;
        that.toplevel = this.toplevel;
        that.enclClass = this.enclClass;
        that.enclMethod = this.enclMethod;
        return that;
    }

    /** Duplicate this environment, updating with given tree,
     *  and copying all other fields.
     */
    public Env<A> dup(JCTree tree) {
        return dup(tree, this.info);
    }

    /** Return closest enclosing environment which points to a tree with given tag.
     */
    // 获取环境形成的上下文环境
    public Env<A> enclosing(int tag) {
        Env<A> env1 = this;
        while (env1 != null && env1.tree.getTag() != tag)
            env1 = env1.next;
        return env1;
    }

    public String toString() {
        return "Env[" + info + (outer == null ? "" : ",outer=" + outer) + "]";
    }

    public Iterator<Env<A>> iterator() {
        return new Iterator<Env<A>>() {
            Env<A> next = Env.this;
            public boolean hasNext() {
                return next.outer != null;
            }
            public Env<A> next() {
                if (hasNext()) {
                    Env<A> current = next;
                    next = current.outer;
                    return current;
                }
                throw new NoSuchElementException();

            }
            public void remove() {
                throw new UnsupportedOperationException();
            }
        };
    }
}
