/*
 * Copyright (c) 2002, 2010, Oracle and/or its affiliates. All rights reserved.
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

import com.sun.tools.javac.util.Context;
import com.sun.tools.javac.util.Name;
import com.sun.tools.javac.util.Names;

import static com.sun.tools.javac.parser.Token.IDENTIFIER;

/**
 * Map from Name to Token and Token to String.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
// 将Name对象映射为Token对象
public class Keywords {
    public static final Context.Key<Keywords> keywordsKey =
            new Context.Key<Keywords>();
    private final Names names;
    /**
     * Keyword array. Maps name indices to Token.
     */
    // 建立NameImpl对象到Token对象的映射关系
    private final Token[] key;
    /**
     * The number of the last entered keyword.
     */
    // 所有Name对象中的index的最大值
    private int maxKey = 0;
    /**
     * The names of all tokens.
     */
    // tokenName数组保存了Token对象到Name对象的映射，
    // 准确说是tokenName数组的下标为各个Token对象在Token枚举类中定义的序数（序数从0开始），而对应下标处的值为对应的Name对象
    /*
    最终tokenName数组的值:
    Name[0]=null // EOF
    Name[1]=null // ERROR
    Name[2]=null // IDENTIFIER
    Name[3]=NameImpl("abstract")// ABSTRACT，对应着表示“abstract” 的NameImpl对象
    ...
    Name[109]=NameImpl("@") // MONKEYS_AT，对应着表示“@”的NameImpl对象
    Name[110]=null // CUSTOM
     */
    private Name[] tokenName = new Name[Token.values().length];

    protected Keywords(Context context) {
        context.put(keywordsKey, this);
        names = Names.instance(context);

        // 循环所有的Token对象
        for (Token t : Token.values()) {
            // name值不为空
            if (t.name != null) {
                // 建立Token对象到Name对象的映射
                enterKeyword(t.name, t);
            } else {
                // 如果name值为空，将tokenName数组中调用t.ordinal()方法获取的下标处的值设置为null
                tokenName[t.ordinal()] = null;
            }
        }

        key = new Token[maxKey + 1];
        for (int i = 0; i <= maxKey; i++) {
            key[i] = IDENTIFIER;
        }
        for (Token t : Token.values()) {
            if (t.name != null) {
                // .getIndex()这个值作为key数组的下标，值是Token对象
                key[tokenName[t.ordinal()].getIndex()] = t;
            }
        }
    }

    public static Keywords instance(Context context) {
        Keywords instance = context.get(keywordsKey);
        if (instance == null) {
            instance = new Keywords(context);
        }
        return instance;
    }

    // 通过Name对象回去Token对象
    public Token key(Name name) {
        return (name.getIndex() > maxKey) ? IDENTIFIER : key[name.getIndex()];
    }

    private void enterKeyword(String s, Token token) {
        Name n = names.fromString(s);
        tokenName[token.ordinal()] = n;
        if (n.getIndex() > maxKey) {
            maxKey = n.getIndex();
        }
    }
}
