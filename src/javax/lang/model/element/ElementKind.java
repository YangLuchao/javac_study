/*
 * Copyright (c) 2005, 2010, Oracle and/or its affiliates. All rights reserved.
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

package javax.lang.model.element;

/**
 * The {@code kind} of an element.
 *
 * <p>Note that it is possible additional element kinds will be added
 * to accommodate new, currently unknown, language structures added to
 * future versions of the Java&trade; programming language.
 *
 * @author Joseph D. Darcy
 * @author Scott Seligman
 * @author Peter von der Ah&eacute;
 * @see Element
 * @since 1.6
 */
// 元素的类型
public enum ElementKind {

    /** A package. */
    // 包
    PACKAGE,

    // Declared types
    /** An enum type. */
    // 枚举
    ENUM,
    /** A class not described by a more specific kind (like {@code ENUM}). */
    // 类
    CLASS,
    /** An annotation type. */
    // 注解
    ANNOTATION_TYPE,
    /**
     * An interface not described by a more specific kind (like
     * {@code ANNOTATION_TYPE}).
     */
    // 接口
    INTERFACE,

    // Variables
    /** An enum constant. */
    // 枚举常量
    ENUM_CONSTANT,
    /**
     * A field not described by a more specific kind (like
     * {@code ENUM_CONSTANT}).
     */
    // 成员变量
    FIELD,
    /** A parameter of a method or constructor. */
    // 形参
    PARAMETER,
    /** A local variable. */
    // 本地变量
    LOCAL_VARIABLE,
    /** A parameter of an exception handler. */
    // 异常参数
    EXCEPTION_PARAMETER,

    // Executables
    /** A method. */
    // 方法
    METHOD,
    /** A constructor. */
    // 构造器
    CONSTRUCTOR,
    /** A static initializer. */
    // clinit
    STATIC_INIT,
    /** An instance initializer. */
    // init
    INSTANCE_INIT,

    /** A type parameter. */
    // 类型变量(泛型)
    TYPE_PARAMETER,

    /**
     * An implementation-reserved element.  This is not the element
     * you are looking for.
     */
    OTHER,

    /**
     * A resource variable.
     * @since 1.7
     */
    RESOURCE_VARIABLE;


    /**
     * Returns {@code true} if this is a kind of class:
     * either {@code CLASS} or {@code ENUM}.
     *
     * @return {@code true} if this is a kind of class
     */
    public boolean isClass() {
        return this == CLASS || this == ENUM;
    }

    /**
     * Returns {@code true} if this is a kind of interface:
     * either {@code INTERFACE} or {@code ANNOTATION_TYPE}.
     *
     * @return {@code true} if this is a kind of interface
     */
    public boolean isInterface() {
        return this == INTERFACE || this == ANNOTATION_TYPE;
    }

    /**
     * Returns {@code true} if this is a kind of field:
     * either {@code FIELD} or {@code ENUM_CONSTANT}.
     *
     * @return {@code true} if this is a kind of field
     */
    public boolean isField() {
        return this == FIELD || this == ENUM_CONSTANT;
    }
}
