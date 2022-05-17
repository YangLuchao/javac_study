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

import com.sun.tools.javac.util.*;
import com.sun.tools.javac.code.*;

/** Contains information specific to the attribute and enter
 *  passes, to be used in place of the generic field in environments.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
// 通过Env对象的info变量来保存AttrContext对象，AttrContext对象中保存一些特殊的信息
public class AttrContext {

    /** The scope of local symbols.
     */
    // AttrContext类中定义的scope是Scope类型，
    // 由于Env对象有着对应语法树的层次结构，
    // 因而通过info保存的AttrContext对象与通过AttrContext对象的scope保存的Scope对象也都具有这种层次结构，
    // 这种设计在Javac后序编译的各个阶段可以灵活操作具体的上下文环境
    Scope scope = null;

    /** The number of enclosing `static' modifiers.
     */
    // staticLevel与Env类的outer辅助判断当前是否为静态环境。
    // 由于静态环境无法引用非静态环境的实例成员，因此经在查找具体的符号引用时，需要对这一语法规则进行判断
    // 默认为非静态为0，静态为1
    int staticLevel = 0;

    /** Is this an environment for a this(...) or super(...) call?
     */
    boolean isSelfCall = false;

    /** Are we evaluating the selector of a `super' or type name?
     */
    boolean selectSuper = false;

    /** Are arguments to current function applications boxed into an array for varargs?
     */
    boolean varArgs = false;

    /** A list of type variables that are all-quantifed in current context.
     */
    // 在当前上下文中全部量化的类型变量列表
    List<Type> tvars = List.nil();

    /** A record of the lint/SuppressWarnings currently in effect
     */
    Lint lint;

    /** The variable whose initializer is being attributed
     * useful for detecting self-references in variable initializers
     */
    Symbol enclVar = null;

    /** Duplicate this context, replacing scope field and copying all others.
     */
    AttrContext dup(Scope scope) {
        AttrContext info = new AttrContext();
        info.scope = scope;
        info.staticLevel = staticLevel;
        info.isSelfCall = isSelfCall;
        info.selectSuper = selectSuper;
        info.varArgs = varArgs;
        info.tvars = tvars;
        info.lint = lint;
        info.enclVar = enclVar;
        return info;
    }

    /** Duplicate this context, copying all fields.
     */
    AttrContext dup() {
        return dup(scope);
    }

    public Iterable<Symbol> getLocalElements() {
        if (scope == null)
            return List.nil();
        return scope.getElements();
    }

    public String toString() {
        return "AttrContext[" + scope.toString() + "]";
    }
}
