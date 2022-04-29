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

package com.sun.tools.javac.parser;

import com.sun.tools.javac.code.Source;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.util.*;

import java.nio.CharBuffer;

import static com.sun.tools.javac.parser.Token.*;
import static com.sun.tools.javac.util.LayoutCharacters.*;

/**
 * The lexical analyzer maps an input stream consisting of
 * ASCII characters and Unicode escapes into a token sequence.
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public class Scanner implements Lexer {

    /**
     * Are surrogates supported?
     */
    final static boolean surrogatesSupported = surrogatesSupported();

    /* Output variables; set by nextToken():
     */
    private static final boolean hexFloatsWork = hexFloatsWork();
    private static boolean scannerDebug = false;
    /**
     * The log to be used for error reporting.
     */
    private final Log log;
    /**
     * The name table.
     */
    private final Names names;
    /**
     * The keyword table.
     */
    private final Keywords keywords;
    /**
     * Has a @deprecated been encountered in last doc comment?
     * this needs to be reset by client.
     */
    protected boolean deprecatedFlag = false;
    /**
     * The token, set by nextToken().
     */
    // 调用nextToken()方法生成的Token对象会赋值给一个名称为token的成员变量
    private Token token;
    /**
     * Allow hex floating-point literals.
     */
    private boolean allowHexFloats;
    /**
     * Allow binary literals.
     */
    private boolean allowBinaryLiterals;
    /**
     * Allow underscores in literals.
     */
    private boolean allowUnderscoresInLiterals;
    /**
     * The source language setting.
     */
    private Source source;
    /**
     * The token's position, 0-based offset from beginning of text.
     */
    private int pos;
    /**
     * Character position just after the last character of the token.
     */
    private int endPos;
    /**
     * The last character position of the previous token.
     */
    private int prevEndPos;
    /**
     * The position where a lexical error occurred;
     */
    private int errPos = Position.NOPOS;
    /**
     * The name of an identifier or token:
     */
    private Name name;
    /**
     * the radix of a numeric literal token.
     */
    private int radix;
    /**
     * A character buffer for literals.
     */
    // sbuf数组按顺序暂存读入的字符
    private char[] sbuf = new char[128];
    // sp指示了sbuf中下一个可用的位置
    // 每次调用nextToken()方法，sp就会被初始化为0
    private int sp;
    /**
     * The input buffer, index of next chacter to be read,
     * index of one past last character in buffer.
     */
    // 保存了从Java源文件中读入的所有字符,最后一个数组元素的值为EOI，EOI其实就是一个值为0x1A的常量，表示已经没有可读取的字符
    private char[] buf;
    // bp保存了buf数组中当前要处理的字符的位置，初始化时将bp设置为-1
    private int bp;
    // buflen保存了buf数组中可读字符的数量，或者说指向了buf数组中可读取字符的最大下标，不包括下标值为buflen的元素
    private int buflen;
    private int eofPos;
    /**
     * The current character.
     */
    private char ch;
    /**
     * The buffer index of the last converted unicode character
     */
    private int unicodeConversionBp = -1;

    /**
     * Common code for constructors.
     */
    private Scanner(ScannerFactory fac) {
        log = fac.log;
        names = fac.names;
        keywords = fac.keywords;
        source = fac.source;
        allowBinaryLiterals = source.allowBinaryLiterals();
        allowHexFloats = source.allowHexFloats();
        allowUnderscoresInLiterals = source.allowUnderscoresInLiterals();
    }

    /**
     * Create a scanner from the input buffer.  buffer must implement
     * array() and compact(), and remaining() must be less than limit().
     */
    protected Scanner(ScannerFactory fac, CharBuffer buffer) {
        // JavacFileManager.toArray(buffer)：将buffer转化为数组
        this(fac, JavacFileManager.toArray(buffer), buffer.limit());
    }

    /**
     * Create a scanner from the input array.  This method might
     * modify the array.  To avoid copying the input array, ensure
     * that {@code inputLength < input.length} or
     * {@code input[input.length -1]} is a white space character.
     *
     * @param fac         the factory which created this Scanner
     * @param input       the input, might be modified
     * @param inputLength the size of the input.
     *                    Must be positive and less than or equal to input.length.
     */
    protected Scanner(ScannerFactory fac, char[] input, int inputLength) {
        this(fac);
        eofPos = inputLength;
        if (inputLength == input.length) {
            if (input.length > 0 && Character.isWhitespace(input[input.length - 1])) {
                inputLength--;
            } else {
                char[] newInput = new char[inputLength + 1];
                System.arraycopy(input, 0, newInput, 0, input.length);
                input = newInput;
            }
        }
        buf = input;
        buflen = inputLength;
        buf[buflen] = EOI;
        bp = -1;
        // 在处理开始时，通常会调用scanChar()方法将bp值更新为下一个要处理字符的下标位置
        // bp加1，要处理的ch致为buf的第一个字符
        scanChar();
    }

    private static boolean hexFloatsWork() {
        try {
            Float.valueOf("0x1.0p1");
            return true;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private static boolean surrogatesSupported() {
        try {
            Character.isHighSurrogate('a');
            return true;
        } catch (NoSuchMethodError ex) {
            return false;
        }
    }

    /**
     * Report an error at the given position using the provided arguments.
     */
    private void lexError(int pos, String key, Object... args) {
        log.error(pos, key, args);
        token = ERROR;
        errPos = pos;
    }

    /**
     * Report an error at the current token position using the provided
     * arguments.
     */
    private void lexError(String key, Object... args) {
        lexError(pos, key, args);
    }

    /**
     * Convert an ASCII digit from its base (8, 10, or 16)
     * to its value.
     */
    private int digit(int base) {
        char c = ch;
        int result = Character.digit(c, base);
        if (result >= 0 && c > 0x7f) {
            lexError(pos + 1, "illegal.nonascii.digit");
            ch = "0123456789abcdef".charAt(result);
        }
        return result;
    }

    /**
     * Convert unicode escape; bp points to initial '\' character
     * (Spec 3.3).
     */
    private void convertUnicode() {
        if (ch == '\\' && unicodeConversionBp != bp) {
            bp++;
            ch = buf[bp];
            if (ch == 'u') {
                do {
                    bp++;
                    ch = buf[bp];
                } while (ch == 'u');
                int limit = bp + 3;
                if (limit < buflen) {
                    int d = digit(16);
                    int code = d;
                    while (bp < limit && d >= 0) {
                        bp++;
                        ch = buf[bp];
                        d = digit(16);
                        code = (code << 4) + d;
                    }
                    if (d >= 0) {
                        ch = (char) code;
                        unicodeConversionBp = bp;
                        return;
                    }
                }
                lexError(bp, "illegal.unicode.esc");
            } else {
                bp--;
                ch = '\\';
            }
        }
    }

    /**
     * Read next character.
     */
    // bp值加1，将ch更新为buf数组中保存的下一个待处理的字符
    private void scanChar() {
        ch = buf[++bp];
        if (ch == '\\') {
            convertUnicode();
        }
    }

    /**
     * Read next character in comment, skipping over double '\' characters.
     */
    private void scanCommentChar() {
        scanChar();
        if (ch == '\\') {
            if (buf[bp + 1] == '\\' && unicodeConversionBp != bp) {
                bp++;
            } else {
                convertUnicode();
            }
        }
    }

    /**
     * Append a character to sbuf.
     */
    // sbuf扩容
    private void putChar(char ch) {
        if (sp == sbuf.length) {
            char[] newsbuf = new char[sbuf.length * 2];
            System.arraycopy(sbuf, 0, newsbuf, 0, sbuf.length);
            sbuf = newsbuf;
        }
        sbuf[sp++] = ch;
    }

    /**
     * Read next character in character or string literal and copy into sbuf.
     */
    // 读取下一个字符或string文字，并且复制进sbuf
    // 处理字符常量
    private void scanLitChar() {
        if (ch == '\\') { // 处理转义字符
            if (buf[bp + 1] == '\\' && unicodeConversionBp != bp) {
                bp++;
                putChar('\\');
                scanChar();
            } else {
                scanChar();
                switch (ch) {
                    case '0':
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                        char leadch = ch;
                        int oct = digit(8);
                        scanChar();
                        if ('0' <= ch && ch <= '7') {
                            // 将八进制表示的数转换为十进制表示，然后强制转换为char类型后调用putChar()方法
                            oct = oct * 8 + digit(8);
                            scanChar();
                            if (leadch <= '3' && '0' <= ch && ch <= '7') {
                                oct = oct * 8 + digit(8);
                                scanChar();
                            }
                        }
                        putChar((char) oct);
                        break;
                    case 'b':
                        putChar('\b');
                        scanChar();
                        break;
                    case 't':
                        putChar('\t');
                        scanChar();
                        break;
                    case 'n':
                        putChar('\n');
                        scanChar();
                        break;
                    case 'f':
                        putChar('\f');
                        scanChar();
                        break;
                    case 'r':
                        putChar('\r');
                        scanChar();
                        break;
                    case '\'':
                        putChar('\'');
                        scanChar();
                        break;
                    case '\"':
                        putChar('\"');
                        scanChar();
                        break;
                    case '\\':
                        putChar('\\');
                        scanChar();
                        break;
                    default:
                        lexError(bp, "illegal.esc.char");
                }
            }
        } else if (bp != buflen) {// 处理非转义字符
            putChar(ch);
            scanChar();
        }
    }

    private void scanDigits(int digitRadix) {
        char saveCh;
        int savePos;
        do {
            if (ch != '_') {
                putChar(ch);
            } else {
                if (!allowUnderscoresInLiterals) {
                    lexError("unsupported.underscore.lit", source.name);
                    allowUnderscoresInLiterals = true;
                }
            }
            saveCh = ch;
            savePos = bp;
            scanChar();
        } while (digit(digitRadix) >= 0 || ch == '_');
        if (saveCh == '_') {
            lexError(savePos, "illegal.underscore");
        }
    }

    /**
     * Read fractional part of hexadecimal floating point number.
     */
    private void scanHexExponentAndSuffix() {
        if (ch == 'p' || ch == 'P') {
            putChar(ch);
            scanChar();
            skipIllegalUnderscores();
            if (ch == '+' || ch == '-') {
                putChar(ch);
                scanChar();
            }
            skipIllegalUnderscores();
            if ('0' <= ch && ch <= '9') {
                scanDigits(10);
                if (!allowHexFloats) {
                    lexError("unsupported.fp.lit", source.name);
                    allowHexFloats = true;
                } else if (!hexFloatsWork) {
                    lexError("unsupported.cross.fp.lit");
                }
            } else {
                lexError("malformed.fp.lit");
            }
        } else {
            lexError("malformed.fp.lit");
        }
        if (ch == 'f' || ch == 'F') {
            putChar(ch);
            scanChar();
            token = FLOATLITERAL;
        } else {
            if (ch == 'd' || ch == 'D') {
                putChar(ch);
                scanChar();
            }
            token = DOUBLELITERAL;
        }
    }

    /**
     * Read fractional part of floating point number.
     */
    private void scanFraction() {
        skipIllegalUnderscores();
        if ('0' <= ch && ch <= '9') {
            scanDigits(10);
        }
        int sp1 = sp;
        if (ch == 'e' || ch == 'E') {
            putChar(ch);
            scanChar();
            skipIllegalUnderscores();
            if (ch == '+' || ch == '-') {
                putChar(ch);
                scanChar();
            }
            skipIllegalUnderscores();
            if ('0' <= ch && ch <= '9') {
                scanDigits(10);
                return;
            }
            lexError("malformed.fp.lit");
            sp = sp1;
        }
    }

    /**
     * Read fractional part and 'd' or 'f' suffix of floating point number.
     */
    private void scanFractionAndSuffix() {
        radix = 10;
        scanFraction();
        if (ch == 'f' || ch == 'F') {
            putChar(ch);
            scanChar();
            token = FLOATLITERAL;
        } else {
            if (ch == 'd' || ch == 'D') {
                putChar(ch);
                scanChar();
            }
            token = DOUBLELITERAL;
        }
    }

    /**
     * Read fractional part and 'd' or 'f' suffix of floating point number.
     */
    private void scanHexFractionAndSuffix(boolean seendigit) {
        radix = 16;
        Assert.check(ch == '.');
        putChar(ch);
        scanChar();
        skipIllegalUnderscores();
        if (digit(16) >= 0) {
            seendigit = true;
            scanDigits(16);
        }
        if (!seendigit) {
            lexError("invalid.hex.number");
        } else {
            scanHexExponentAndSuffix();
        }
    }

    private void skipIllegalUnderscores() {
        if (ch == '_') {
            lexError(bp, "illegal.underscore");
            while (ch == '_') {
                scanChar();
            }
        }
    }

    /**
     * Read a number.
     *
     * @param radix The radix of the number; one of 2, j8, 10, 16.
     */
    private void scanNumber(int radix) {
        this.radix = radix;
        // for octal, allow base-10 digit in case it's a float literal
        int digitRadix = (radix == 8 ? 10 : radix);
        boolean seendigit = false;
        if (digit(digitRadix) >= 0) {
            seendigit = true;
            scanDigits(digitRadix);
        }
        if (radix == 16 && ch == '.') {
            scanHexFractionAndSuffix(seendigit);
        } else if (seendigit && radix == 16 && (ch == 'p' || ch == 'P')) {
            scanHexExponentAndSuffix();
        } else if (digitRadix == 10 && ch == '.') {
            putChar(ch);
            scanChar();
            scanFractionAndSuffix();
        } else if (digitRadix == 10 &&
                (ch == 'e' || ch == 'E' ||
                        ch == 'f' || ch == 'F' ||
                        ch == 'd' || ch == 'D')) {
            scanFractionAndSuffix();
        } else {
            if (ch == 'l' || ch == 'L') {
                scanChar();
                token = LONGLITERAL;
            } else {
                token = INTLITERAL;
            }
        }
    }

    /**
     * Read an identifier.
     */
    // 将所有组成标识符的字符从buf数组中读取出来按顺序存储到sbuf数组中，
    // 最后作为参数调用names.fromChars()方法获取NameImpl对象方法
    // 对字母、数字、下划线、美元符号及一些控制字符不做任何处理，
    // 直接通过break跳出switch语句后重新执行do-while循环，
    // 然后将这些字符存储到sbuf数组中，这都是标识符的一部分
    private void scanIdent() {
        boolean isJavaIdentifierPart;
        char high;
        do {
            // sbuf数组不能存储更多字符，调用putChar()方法进行扩容
            if (sp == sbuf.length) {
                putChar(ch);
            } else {
                sbuf[sp++] = ch;
            }
            // optimization, was: putChar(ch);

            scanChar();
            switch (ch) {
                case 'A':
                case 'B':
                case 'C':
                case 'D':
                case 'E':
                case 'F':
                case 'G':
                case 'H':
                case 'I':
                case 'J':
                case 'K':
                case 'L':
                case 'M':
                case 'N':
                case 'O':
                case 'P':
                case 'Q':
                case 'R':
                case 'S':
                case 'T':
                case 'U':
                case 'V':
                case 'W':
                case 'X':
                case 'Y':
                case 'Z':
                case 'a':
                case 'b':
                case 'c':
                case 'd':
                case 'e':
                case 'f':
                case 'g':
                case 'h':
                case 'i':
                case 'j':
                case 'k':
                case 'l':
                case 'm':
                case 'n':
                case 'o':
                case 'p':
                case 'q':
                case 'r':
                case 's':
                case 't':
                case 'u':
                case 'v':
                case 'w':
                case 'x':
                case 'y':
                case 'z':
                case '$':
                case '_':
                case '0':
                case '1':
                case '2':
                case '3':
                case '4':
                case '5':
                case '6':
                case '7':
                case '8':
                case '9':
                case '\u0000':
                case '\u0001':
                case '\u0002':
                case '\u0003':
                case '\u0004':
                case '\u0005':
                case '\u0006':
                case '\u0007':
                case '\u0008':
                case '\u000E':
                case '\u000F':
                case '\u0010':
                case '\u0011':
                case '\u0012':
                case '\u0013':
                case '\u0014':
                case '\u0015':
                case '\u0016':
                case '\u0017':
                case '\u0018':
                case '\u0019':
                case '\u001B':
                case '\u007F':
                    break;
                case '\u001A': // EOI is also a legal identifier part
                    if (bp >= buflen) {// 已经没有待处理的字符了
                        name = names.fromChars(sbuf, 0, sp);
                        token = keywords.key(name);
                        return;
                    }
                    break;
                default:
                    if (ch < '\u0080') {
                        // ch是ASCII编码中的一个字符
                        // 所有合法的ASCII字符已经在上面的case分支中进行了处理
                        isJavaIdentifierPart = false;
                    } else {
                        // 获取高代理项
                        high = scanSurrogates();
                        if (high != 0) {
                            if (sp == sbuf.length) {
                                putChar(high);
                            } else {
                                sbuf[sp++] = high;
                            }
                            // 方法会判断通过高代理项和低代理项表示的字符是否为合法
                            // 标识符的首字符
                            isJavaIdentifierPart = Character.isJavaIdentifierPart(
                                    Character.toCodePoint(high, ch));
                        } else {
                            isJavaIdentifierPart = Character.isJavaIdentifierPart(ch);
                        }
                    }
                    // 生成Name和Token对象
                    if (!isJavaIdentifierPart) {
                        // 生成Name对象
                        name = names.fromChars(sbuf, 0, sp);
                        // 转化为Token对象
                        token = keywords.key(name);
                        return;
                    }
            }
        } while (true);
    }

    /**
     * Scan surrogate pairs.  If 'ch' is a high surrogate and
     * the next character is a low surrogate, then put the low
     * surrogate in 'ch', and return the high surrogate.
     * otherwise, just return 0.
     */
    private char scanSurrogates() {
        if (surrogatesSupported && Character.isHighSurrogate(ch)) {
            char high = ch;

            scanChar();

            if (Character.isLowSurrogate(ch)) {
                return high;
            }

            ch = high;
        }

        return 0;
    }

    /**
     * Return true if ch can be part of an operator.
     */
    // 判断是否为标识符号或标识符号的一部分
    private boolean isSpecial(char ch) {
        switch (ch) {
            case '!':
            case '%':
            case '&':
            case '*':
            case '?':
            case '+':
            case '-':
            case ':':
            case '<':
            case '=':
            case '>':
            case '^':
            case '|':
            case '~':
            case '@':
                return true;
            default:
                return false;
        }
    }

    /**
     * Read longest possible sequence of special characters and convert
     * to token.
     */
    // 扫描出完整的标识符号
    private void scanOperator() {
        while (true) {
            putChar(ch);
            Name newname = names.fromChars(sbuf, 0, sp);
            if (keywords.key(newname) == IDENTIFIER) {
                sp--;
                break;
            }
            name = newname;
            token = keywords.key(newname);
            scanChar();
            if (!isSpecial(ch)) {
                break;
            }
        }
    }

    /**
     * Scan a documention comment; determine if a deprecated tag is present.
     * Called once the initial /, * have been skipped, positioned at the second *
     * (which is treated as the beginning of the first line).
     * Stops positioned at the closing '/'.
     */
    @SuppressWarnings("fallthrough")
    private void scanDocComment() {
        boolean deprecatedPrefix = false;

        forEachLine:
        while (bp < buflen) {

            // Skip optional WhiteSpace at beginning of line
            while (bp < buflen && (ch == ' ' || ch == '\t' || ch == FF)) {
                scanCommentChar();
            }

            // Skip optional consecutive Stars
            while (bp < buflen && ch == '*') {
                scanCommentChar();
                if (ch == '/') {
                    return;
                }
            }

            // Skip optional WhiteSpace after Stars
            while (bp < buflen && (ch == ' ' || ch == '\t' || ch == FF)) {
                scanCommentChar();
            }

            deprecatedPrefix = false;
            // At beginning of line in the JavaDoc sense.
            if (bp < buflen && ch == '@' && !deprecatedFlag) {
                scanCommentChar();
                if (bp < buflen && ch == 'd') {
                    scanCommentChar();
                    if (bp < buflen && ch == 'e') {
                        scanCommentChar();
                        if (bp < buflen && ch == 'p') {
                            scanCommentChar();
                            if (bp < buflen && ch == 'r') {
                                scanCommentChar();
                                if (bp < buflen && ch == 'e') {
                                    scanCommentChar();
                                    if (bp < buflen && ch == 'c') {
                                        scanCommentChar();
                                        if (bp < buflen && ch == 'a') {
                                            scanCommentChar();
                                            if (bp < buflen && ch == 't') {
                                                scanCommentChar();
                                                if (bp < buflen && ch == 'e') {
                                                    scanCommentChar();
                                                    if (bp < buflen && ch == 'd') {
                                                        deprecatedPrefix = true;
                                                        scanCommentChar();
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (deprecatedPrefix && bp < buflen) {
                if (Character.isWhitespace(ch)) {
                    deprecatedFlag = true;
                } else if (ch == '*') {
                    scanCommentChar();
                    if (ch == '/') {
                        deprecatedFlag = true;
                        return;
                    }
                }
            }

            // Skip rest of line
            while (bp < buflen) {
                switch (ch) {
                    case '*':
                        scanCommentChar();
                        if (ch == '/') {
                            return;
                        }
                        break;
                    case CR: // (Spec 3.4)
                        scanCommentChar();
                        if (ch != LF) {
                            continue forEachLine;
                        }
                        /* fall through to LF case */
                    case LF: // (Spec 3.4)
                        scanCommentChar();
                        continue forEachLine;
                    default:
                        scanCommentChar();
                }
            } // rest of line
        } // forEachLine
        return;
    }

    /**
     * The value of a literal token, recorded as a string.
     * For integers, leading 0x and 'l' suffixes are suppressed.
     */
    @Override
    public String stringVal() {
        return new String(sbuf, 0, sp);
    }

    /**
     * Read token.
     */
    @Override
    public void nextToken() {

        try {
            prevEndPos = endPos;
            sp = 0;

            while (true) {
                pos = bp;
                // switch语句会根据首个出现的字符来判断可能生成的Token对象
                switch (ch) { // switch语句所有的处理分支可大概分为以下8类
                    /*
			        1、特殊字符的处理
			        2、标识符的处理
			        3、数字的处理
			        4、分隔符的处理
			        5、斜线作为首字符的处理
			        6、单引号作为首字符的处理
			        7、双引号作为首字符的处理
			        8、默认的处理
                     */
                    // -------------------------------特殊字符的处理
                    // 这些字符都不会生成具体的Token对象，在当前的词法分析阶段调用scanChar()方法直接摒弃这些字符
                    case ' ': // (Spec 3.6) 空格
                    case '\t': // (Spec 3.6) 水平制表符
                    case FF: // (Spec 3.6) 换行换页符
                        // 将空格、水平制表符与换页符当作空白字符来处理
                        do {
                            scanChar();
                        } while (ch == ' ' || ch == '\t' || ch == FF);
                        endPos = bp;
                        processWhiteSpace();
                        break;
                    case LF: // (Spec 3.4) 换行符
                        scanChar();
                        endPos = bp;
                        processLineTerminator();
                        break;
                    case CR: // (Spec 3.4) 回车
                        scanChar();
                        if (ch == LF) { // 换行
                            scanChar();
                        }
                        endPos = bp;
                        processLineTerminator();
                        break;
                    // ----------------------------------特殊字符的处理
                    // ----------------------------------标识符的处理
                    case 'A':
                    case 'B':
                    case 'C':
                    case 'D':
                    case 'E':
                    case 'F':
                    case 'G':
                    case 'H':
                    case 'I':
                    case 'J':
                    case 'K':
                    case 'L':
                    case 'M':
                    case 'N':
                    case 'O':
                    case 'P':
                    case 'Q':
                    case 'R':
                    case 'S':
                    case 'T':
                    case 'U':
                    case 'V':
                    case 'W':
                    case 'X':
                    case 'Y':
                    case 'Z':
                    case 'a':
                    case 'b':
                    case 'c':
                    case 'd':
                    case 'e':
                    case 'f':
                    case 'g':
                    case 'h':
                    case 'i':
                    case 'j':
                    case 'k':
                    case 'l':
                    case 'm':
                    case 'n':
                    case 'o':
                    case 'p':
                    case 'q':
                    case 'r':
                    case 's':
                    case 't':
                    case 'u':
                    case 'v':
                    case 'w':
                    case 'x':
                    case 'y':
                    case 'z':
                    case '$':
                    case '_':
                        // 获取标识符
                        scanIdent();
                        return;
                    // ----------------------------------标识符的处理
                    // ----------------------------------数组处理
                    case '0':
                        scanChar();
                        // 处理十六进制表示的整数或者浮点数
                        // 0x或0X开头时，按十六进制数字处理
                        if (ch == 'x' || ch == 'X') {
                            scanChar();
                            skipIllegalUnderscores();
                            if (ch == '.') {
                                scanHexFractionAndSuffix(false);
                                // 处理十六进制中的小数及后缀部分
                            } else if (digit(16) < 0) {
                                lexError("invalid.hex.number");
                            } else {
                                scanNumber(16);
                            }
                            // 处理二进制表示的整数
                            // 以0b或0B开头时，按二进制数字处理
                        } else if (ch == 'b' || ch == 'B') {
                            if (!allowBinaryLiterals) {
                                lexError("unsupported.binary.lit", source.name);
                                allowBinaryLiterals = true;
                            }
                            scanChar();
                            skipIllegalUnderscores();
                            if (digit(2) < 0) {
                                lexError("invalid.binary.number");
                            } else {
                                scanNumber(2);
                            }
                        } else {
                            // 处理八进制表示的整数或浮点数
                            putChar('0');
                            if (ch == '_') {
                                int savePos = bp;
                                do {
                                    scanChar();
                                } while (ch == '_');
                                if (digit(10) < 0) {
                                    lexError(savePos, "illegal.underscore");
                                }
                            }
                            scanNumber(8);
                        }
                        return;
                    case '1':
                    case '2':
                    case '3':
                    case '4':
                    case '5':
                    case '6':
                    case '7':
                    case '8':
                    case '9':
                        scanNumber(10);
                        return;
                    case '.':
                        scanChar();
                        // 处理十进制中的小数及后缀部分
                        if ('0' <= ch && ch <= '9') {
                            putChar('.');
                            scanFractionAndSuffix();
                        } else if (ch == '.') {
                            putChar('.');
                            putChar('.');
                            scanChar();
                            if (ch == '.') {
                                scanChar();
                                putChar('.');
                                token = ELLIPSIS;
                            } else {
                                lexError("malformed.fp.lit");
                            }
                        } else {
                            // 处理分隔符
                            token = DOT;
                        }
                        return;
                    // ----------------------------------数组处理
                    // ----------------------------------分隔符处理
                    case ',':
                        scanChar();
                        token = COMMA;
                        return;
                    case ';':
                        scanChar();
                        token = SEMI;
                        return;
                    case '(':
                        scanChar();
                        token = LPAREN;
                        return;
                    case ')':
                        scanChar();
                        token = RPAREN;
                        return;
                    case '[':
                        scanChar();
                        token = LBRACKET;
                        return;
                    case ']':
                        scanChar();
                        token = RBRACKET;
                        return;
                    case '{':
                        scanChar();
                        token = LBRACE;
                        return;
                    case '}':
                        scanChar();
                        token = RBRACE;
                        return;
                    // ----------------------------------分隔符处理
                    // ----------------------------------斜杠作为首字符的处理
                    // 以斜杠“/”作为首字符的可能为注释，如单行注释、多行注释或文档注释，还可能是除法运算符“/” 或者复合赋值运算符“/=”
                    case '/':
                        scanChar();
                        // 单行注释
                        if (ch == '/') {
                            do {
                                scanCommentChar();
                            } while (ch != CR && ch != LF && bp < buflen);
                            if (bp < buflen) {
                                endPos = bp;
                                processComment(CommentStyle.LINE);
                            }
                            break;
                            // 多行注释或文档注释
                        } else if (ch == '*') {
                            scanChar();
                            CommentStyle style;
                            if (ch == '*') {
                                style = CommentStyle.JAVADOC;
                                scanDocComment();
                            } else {
                                style = CommentStyle.BLOCK;
                                while (bp < buflen) {
                                    if (ch == '*') {
                                        scanChar();
                                        if (ch == '/') {
                                            break;
                                        }
                                    } else {
                                        scanCommentChar();
                                    }
                                }
                            }
                            if (ch == '/') {
                                scanChar();
                                endPos = bp;
                                processComment(style);
                                break;
                            } else {
                                lexError("unclosed.comment");
                                return;
                            }
                            // 复合赋值运算符
                        } else if (ch == '=') {
                            name = names.slashequals;
                            token = SLASHEQ;
                            scanChar();
                            // 除法运算符
                        } else {
                            name = names.slash;
                            token = SLASH;
                        }
                        return;
                    // ----------------------------------分隔符处理
                    // ----------------------------------单引号作为首字符的处理
                    // 单引号作为首字符的只能是字符常量
                    // 在Java源代码中，单引号作为首字符通常表示字符常量。调用scanLitChar()方法扫描字符常量，最后将token直接赋值为CHARLITERAL
                    case '\'':
                        scanChar();
                        if (ch == '\'') {
                            lexError("empty.char.lit");
                        } else {
                            if (ch == CR || ch == LF) {
                                lexError(pos, "illegal.line.end.in.char.lit");
                            }
                            // 处理字符常量
                            scanLitChar();
                            if (ch == '\'') {
                                scanChar();
                                // 字符常量
                                token = CHARLITERAL;
                            } else {
                                lexError(pos, "unclosed.char.lit");
                            }
                        }
                        return;
                    // ----------------------------------单引号作为首字符的处理
                    // ----------------------------------双引号作为首字符的处理
                    // 双引号作为首字符的只能是字符串常量
                    case '\"':
                        scanChar();
                        // 当ch不为双引号、不为回车换行且有待处理字符时，调用scanLitChar()方法扫描
                        // 字符串常量
                        while (ch != '\"' && ch != CR && ch != LF && bp < buflen) {
                            scanLitChar();
                        }
                        if (ch == '\"') {
                            token = STRINGLITERAL;
                            scanChar();
                        } else {
                            lexError(pos, "unclosed.str.lit");
                        }
                        return;
                    // ----------------------------------双引号作为首字符的处理
                    // ----------------------------------默认处理分支
                    default:
                        // 首先调用isSpecial()方法判断是否可能为标识符号，如果是就调用scanOperator()方法进行处理，否则可能是标识符的首字符
                        if (isSpecial(ch)) {
                            // 调用scanOperator()方法扫描出完整的标识符号
                            scanOperator();
                        } else {
                            // 当isJavaIdentifierStart的值为true时，表示是合法标识符的首字符
                            boolean isJavaIdentifierStart;
                            if (ch < '\u0080') {
                                // all ASCII range chars already handled, above
                                // ch是ASCII编码中的一个字符
                                isJavaIdentifierStart = false;
                            } else {
                                // 获取高代理项
                                char high = scanSurrogates();
                                if (high != 0) {
                                    if (sp == sbuf.length) {
                                        putChar(high);
                                    } else {
                                        sbuf[sp++] = high;
                                    }
                                    // 方法会判断通过高代理项和低代理项表示的字符是否合法
                                    // 标识符的首字符
                                    isJavaIdentifierStart = Character.isJavaIdentifierStart(
                                            Character.toCodePoint(high, ch));
                                } else {
                                    isJavaIdentifierStart = Character.isJavaIdentifierStart(ch);
                                }
                            }
                            // 合法的标识符首字符
                            if (isJavaIdentifierStart) {
                                // 调用scanIdent()方法进行处理
                                scanIdent();
                                // 判断bp是否等于buflen，如果等于，说明当前的ch是最后一个字符，可不处理；或者判断是否为特殊的结尾字符EOI
                            } else if (bp == buflen || ch == EOI && bp + 1 == buflen) { // JLS 3.5
                                // 已经没有待处理的字符了
                                token = EOF;
                                pos = bp = eofPos;
                            } else {
                                lexError("illegal.char", String.valueOf((int) ch));
                                scanChar();
                            }
                        }
                        return;
                }
            }
        } finally {
            endPos = bp;
            if (scannerDebug) {
                System.out.println("nextToken(" + pos
                        + "," + endPos + ")=|" +
                        new String(getRawCharacters(pos, endPos))
                        + "|");
            }
        }
    }

    /**
     * Return the current token, set by nextToken().
     */
    @Override
    public Token token() {
        return token;
    }

    /**
     * Sets the current token.
     */
    @Override
    public void token(Token token) {
        this.token = token;
    }

    /**
     * Return the current token's position: a 0-based
     * offset from beginning of the raw input stream
     * (before unicode translation)
     */
    @Override
    public int pos() {
        return pos;
    }

    /**
     * Return the last character position of the current token.
     */
    @Override
    public int endPos() {
        return endPos;
    }

    /**
     * Return the last character position of the previous token.
     */
    @Override
    public int prevEndPos() {
        return prevEndPos;
    }

    /**
     * Return the position where a lexical error occurred;
     */
    @Override
    public int errPos() {
        return errPos;
    }

    /**
     * Set the position where a lexical error occurred;
     */
    @Override
    public void errPos(int pos) {
        errPos = pos;
    }

    /**
     * Return the name of an identifier or token for the current token.
     */
    @Override
    public Name name() {
        return name;
    }

    /**
     * Return the radix of a numeric literal token.
     */
    @Override
    public int radix() {
        return radix;
    }

    /**
     * Has a @deprecated been encountered in last doc comment?
     * This needs to be reset by client with resetDeprecatedFlag.
     */
    @Override
    public boolean deprecatedFlag() {
        return deprecatedFlag;
    }

    @Override
    public void resetDeprecatedFlag() {
        deprecatedFlag = false;
    }

    /**
     * Returns the documentation string of the current token.
     */
    @Override
    public String docComment() {
        return null;
    }

    /**
     * Returns a copy of the input buffer, up to its inputLength.
     * Unicode escape sequences are not translated.
     */
    @Override
    public char[] getRawCharacters() {
        char[] chars = new char[buflen];
        System.arraycopy(buf, 0, chars, 0, buflen);
        return chars;
    }

    /**
     * Returns a copy of a character array subset of the input buffer.
     * The returned array begins at the <code>beginIndex</code> and
     * extends to the character at index <code>endIndex - 1</code>.
     * Thus the length of the substring is <code>endIndex-beginIndex</code>.
     * This behavior is like
     * <code>String.substring(beginIndex, endIndex)</code>.
     * Unicode escape sequences are not translated.
     *
     * @param beginIndex the beginning index, inclusive.
     * @param endIndex   the ending index, exclusive.
     * @throws IndexOutOfBounds if either offset is outside of the
     *                          array bounds
     */
    @Override
    public char[] getRawCharacters(int beginIndex, int endIndex) {
        int length = endIndex - beginIndex;
        char[] chars = new char[length];
        System.arraycopy(buf, beginIndex, chars, 0, length);
        return chars;
    }

    /**
     * Called when a complete comment has been scanned. pos and endPos
     * will mark the comment boundary.
     */
    protected void processComment(CommentStyle style) {
        if (scannerDebug) {
            System.out.println("processComment(" + pos
                    + "," + endPos + "," + style + ")=|"
                    + new String(getRawCharacters(pos, endPos))
                    + "|");
        }
    }

    /**
     * Called when a complete whitespace run has been scanned. pos and endPos
     * will mark the whitespace boundary.
     */
    protected void processWhiteSpace() {
        if (scannerDebug) {
            System.out.println("processWhitespace(" + pos
                    + "," + endPos + ")=|" +
                    new String(getRawCharacters(pos, endPos))
                    + "|");
        }
    }

    /**
     * Called when a line terminator has been processed.
     */
    protected void processLineTerminator() {
        if (scannerDebug) {
            System.out.println("processTerminator(" + pos
                    + "," + endPos + ")=|" +
                    new String(getRawCharacters(pos, endPos))
                    + "|");
        }
    }

    /**
     * Build a map for translating between line numbers and
     * positions in the input.
     *
     * @return a LineMap
     */
    @Override
    public Position.LineMap getLineMap() {
        return Position.makeLineMap(buf, buflen, false);
    }

    public enum CommentStyle {
        LINE,
        BLOCK,
        JAVADOC,
    }

}
