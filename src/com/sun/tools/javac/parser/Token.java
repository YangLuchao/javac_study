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

package com.sun.tools.javac.parser;

import com.sun.tools.javac.api.Formattable;
import com.sun.tools.javac.api.Messages;

import java.util.Locale;

/**
 * An interface that defines codes for Java source tokens
 * returned from lexical analysis.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
// javac将CharBuffer对象中的内容转换为Token流
public enum Token implements Formattable {
    // -----------------------------特殊类型
    EOF,
    ERROR,
    // ------------------------------特殊类型
    // ------------------------------标识符
    // 用来泛指用户自定义的类名、包名、变量包、方法名
    IDENTIFIER,
    // ------------------------------标识符
    // ------------------------------java保留的关键字
    // 数据类型：
    BOOLEAN("boolean"),
    BYTE("byte"),
    CHAR("char"),
    SHORT("short"),
    INT("int"),
    LONG("long"),
    FLOAT("float"),
    DOUBLE("double"),
    CLASS("class"),
    INTERFACE("interface"),
    ENUM("enum"),
    // 流程控制：
    BREAK("break"),
    CONTINUE("continue"),
    FOR("for"),
    IF("if"),
    CASE("case"),
    CATCH("catch"),
    DEFAULT("default"),
    DO("do"),
    ELSE("else"),
    RETURN("return"),
    SWITCH("switch"),
    THROW("throw"),
    THROWS("throws"),
    TRY("try"),
    WHILE("while"),
    // 修饰符
    ABSTRACT("abstract"),
    NATIVE("native"),
    PRIVATE("private"),
    PROTECTED("protected"),
    PUBLIC("public"),
    STATIC("static"),
    STRICTFP("strictfp"),
    SYNCHRONIZED("synchronized"),
    TRANSIENT("transient"),
    VOID("void"),
    VOLATILE("volatile"),
    // 动作
    EXTENDS("extends"),
    FINAL("final"),
    FINALLY("finally"),
    IMPLEMENTS("implements"),
    IMPORT("import"),
    INSTANCEOF("instanceof"),
    NEW("new"),
    PACKAGE("package"),
    SUPER("super"),
    THIS("this"),
    ASSERT("assert"),
    // 保留字
    CONST("const"),
    GOTO("goto"),
    // ------------------------------java保留的关键字
    // -----------------------------字面量
    // 基本类型的字面量外，还有String类型的字面量
    // null通常用来初始化引用类型
    INTLITERAL,
    LONGLITERAL,
    FLOATLITERAL,
    DOUBLELITERAL,
    CHARLITERAL,
    STRINGLITERAL,
    TRUE("true"),
    FALSE("false"),
    NULL("null"),
    // -----------------------------字面量
    // ----------------------------标识符
    LPAREN("("),
    RPAREN(")"),
    LBRACE("{"),
    RBRACE("}"),
    LBRACKET("["),
    RBRACKET("]"),
    SEMI(";"),
    COMMA(","),
    DOT("."),
    ELLIPSIS("..."),
    EQ("="),
    GT(">"),
    LT("<"),
    BANG("!"),
    TILDE("~"),
    QUES("?"),
    COLON(":"),
    EQEQ("=="),
    LTEQ("<="),
    GTEQ(">="),
    BANGEQ("!="),
    AMPAMP("&&"),
    BARBAR("||"),
    PLUSPLUS("++"),
    SUBSUB("--"),
    PLUS("+"),
    SUB("-"),
    STAR("*"),
    SLASH("/"),
    AMP("&"),
    BAR("|"),
    CARET("^"),
    PERCENT("%"),
    LTLT("<<"),
    GTGT(">>"),
    GTGTGT(">>>"),
    PLUSEQ("+="),
    SUBEQ("-="),
    STAREQ("*="),
    SLASHEQ("/="),
    AMPEQ("&="),
    BAREQ("|="),
    CARETEQ("^="),
    PERCENTEQ("%="),
    LTLTEQ("<<="),
    GTGTEQ(">>="),
    GTGTGTEQ(">>>="),
    MONKEYS_AT("@"),
    // ----------------------------标识符
    CUSTOM;

    // 如果name不为空，那么就表示将name所保存的字符串定义为一个特定的Token对象（指的就是Token常量）
    public final String name;

    Token() {
        this(null);
    }

    Token(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        switch (this) {
            case IDENTIFIER:
                return "token.identifier";
            case CHARLITERAL:
                return "token.character";
            case STRINGLITERAL:
                return "token.string";
            case INTLITERAL:
                return "token.integer";
            case LONGLITERAL:
                return "token.long-integer";
            case FLOATLITERAL:
                return "token.float";
            case DOUBLELITERAL:
                return "token.double";
            case ERROR:
                return "token.bad-symbol";
            case EOF:
                return "token.end-of-input";
            case DOT:
            case COMMA:
            case SEMI:
            case LPAREN:
            case RPAREN:
            case LBRACKET:
            case RBRACKET:
            case LBRACE:
            case RBRACE:
                return "'" + name + "'";
            default:
                return name;
        }
    }

    @Override
    public String getKind() {
        return "Token";
    }

    @Override
    public String toString(Locale locale, Messages messages) {
        return name != null ? toString() : messages.getLocalizedString(locale, "compiler.misc." + toString());
    }
}
