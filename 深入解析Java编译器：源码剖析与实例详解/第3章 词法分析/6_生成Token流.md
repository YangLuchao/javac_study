# 生成Token流

`对象` `科技` `方法` `字符`

在第3.2节介绍过，将文件读取到CharBuffer对象中后会调用重载的parse\(\)方法，这个方法的实现代码如下：

```java
来源：com.sun.tools.javac.main.JavaCompiler
protected JCCompilationUnit parse(JavaFileObject filename, CharSequence content) {
	JCCompilationUnit tree = make.TopLevel(List. <JCTree.JCAnnotation>nil(), null, List.<JCTree>nil()); 
	if (content != null) {
		Parser parser = parserFactory.newParser(content, _,_,_);
		tree = parser.parseCompilationUnit(); 
	}
	tree.sourcefile = filename; 
	return tree;
}
```

parse\(\)方法通过content获取到了Parser对象parser，然后又调用了parser.parseCompilationUnit\(\)方法，这个方法会根据content创建一颗抽象语法树。Javac并没有先将字符流完全转换为Token流，然后再在Token流的基础上组建抽象语法树，而是每读取一部分Token对象后就按照JLS中的文法生成抽象语法树节点，也就是说边读取Token对象边组建抽象语法树。每调用一次Scanner类中的nextToken\(\)方法，就可以获取下一个Token对象。调用nextToken\(\)方法首先需要通过工厂类ScannerFactory获取Scanner对象，在ParserFactory类的newParser\(\)方法中有如下调用语句：

```java
来源：com.sun.tools.javac.parser.ParserFactory 
Lexer lexer = scannerFactory.newScanner(input, _);
```

其中，input就是parse\(\)方法在调用newParser\(\)方法时传递的content参数。直接调用ScannerFactory对象scannerFactory的工厂方法newScanner\(\)获取Scanner对象，newScanner\(\)方法的实现代码如下：

```java
来源：com.sun.tools.javac.parser.ScannerFactory 
public Scanner newScanner(CharSequence input, _) {
	CharBuffer buf = (CharBuffer) input; 
	return new Scanner(this, buf);
}
```

在newScanner\(\)方法中直接创建一个Scanner对象，调用的构造方法如下：

```java
来源：com.sun.tools.javac.parser.Scanner
protected Scanner(ScannerFactory fac, CharBuffer buffer) { 
	this(fac, JavacFileManager.toArray(buffer),
	buffer.limit()); 
}
```

调用JavacFileManager.toArray\(\)方法将buffer转换为字符数组，然后作为参数调用另外一个重载的构造方法，代码如下：

```java
来源：com.sun.tools.javac.parser.Scanner
    protected Scanner(ScannerFactory fac, char[] input, int inputLength) {
    	...
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
        scanChar();
   }
```

buf、buflen与bp成员变量在Scanner类中的定义如下：

```java
来源：com.sun.tools.javac.parser.Scanner
private char[] buf;
private int buflen;
private int bp;
```

buf数组保存了从Java源文件中读入的所有字符，最后一个数组元素的值为EOI，EOI其实就是一个值为0x1A的常量，表示已经没有可读取的字符；buflen保存了buf数组中可读字符的数量，或者说指向了buf数组中可读取字符的最大下标，不包括下标值为buflen的元素；bp保存了buf数组中当前要处理的字符的位置，初始化时将bp设置为\-1，在处理开始时，通常会调用scanChar\(\)方法将bp值更新为下一个要处理字符的下标位置。scanChar\(\)方法的实现代码如下：

```java
来源：com.sun.tools.javac.parser.Scanner 
private void scanChar() {
	ch = buf[++bp]; 
}
```

可以看到，bp变为了0，而ch是Scanner类中声明的一个成员变量，保存着当前待处理的字符。

在3.3.1节介绍过Token对象，同时也能看出哪些字符可以组合为一个合法Token对象的name值。在Javac的语法分析过程中会多次调用nextToken\(\)方法，将字符流转换为一个个Token对象。每次调用方法时都会读取若干个字符，通过在switch语句中判断首个读入的字符，然后在switch语句的各个分支中处理以这个字符开头的可能形成的Token对象。下面是nextToken\(\)方法的一个大概实现：

```java
来源：com.sun.tools.javac.parser.Scanner 
public void nextToken() {
	sp = 0;
	while (true) {
		switch (ch) { // switch语句所有的处理分支可大概分为以下8类
          1、特殊字符的处理
          2、标识符的处理
          3、数字的处理
          4、分隔符的处理
          5、斜线作为首字符的处理
          6、单引号作为首字符的处理
          7、双引号作为首字符的处理
          8、默认的处理 
		}
	}
}
```

switch语句会根据首个出现的字符来判断可能生成的Token对象，后面会对每一类逻辑的实现进行详细介绍。nextToken\(\)方法在获取Token对象的过程中会涉及两个成员变量，这两个变量在Scanner类中的定义如下：

```java
来源：com.sun.tools.javac.parser.Scanner 
private char[] sbuf = new char[128]; 
private int sp;
```

某个Token对象的name由多个字符组成，例如“/=”由2个字符组成，所以sbuf数组按顺序暂存读入的字符，而sp指示了sbuf中下一个可用的位置。每调用一次nextToken\(\)方法，sp就会被初始化为0，这样就可以重复利用sbuf暂存读入的若干个字符了。

调用nextToken\(\)方法生成的Token对象会赋值给一个名称为token的成员变量，token变量的定义如下：

```java
来源：com.sun.tools.javac.parser.Scanner 
private Token token;
```

## 特殊字符的处理

特殊字符包括换行符、空格及水平制表符等，具体的实现代码如下：

```java
来源：com.sun.tools.javac.parser.Scanner 
public void nextToken() {
	sp = 0;
	while (true) { 
		switch (ch) {
			case ' ': // 空格
			case '\t': // 水平制表符号
			case FF: // 换行、换页符 
				do {
						scanChar();
					} while (ch == ' ' || ch == '\t' || ch == FF); 
				break;
			case LF: // 换行符 
				scanChar(); 
				break;
			case CR: // 回车 
				scanChar();
				if (ch == LF) { // 换行 
					scanChar();
				}
				break;
			...
			}
		}
	}
```

将空格、水平制表符与换页符当作空白字符来处理，而将换行与回车或者回车换行当作行结束符来处理。这些字符都不会生成具体的Token对象，在当前的词法分析阶段调用scanChar\(\)方法直接摒弃这些字符，scanChar\(\)方法的实现代码如下：

```java
来源：com.sun.tools.javac.parser.Scanner 
private void scanChar() {
	ch = buf[++bp]; 
}
```

将bp的值加1，将ch更新为buf数组中保存的下一个待处理的字符。

## 标识符的处理

对代码编写者自定义的包名、类名、变量名等进行处理，具体的实现代码如下：

```java
来源：com.sun.tools.javac.parser.Scanner
public void nextToken() {
sp = 0;
while (true) { 
	switch (ch) {
		...
		case 'A': case 'B': case 'C': case 'D': case 'E':
		case 'F': case 'G': case 'H': case 'I': case 'J':
		case 'K': case 'L': case 'M': case 'N': case 'O':
		case 'P': case 'Q': case 'R': case 'S': case 'T':
		case 'U': case 'V': case 'W': case 'X': case 'Y':
		case 'Z':
		case 'a': case 'b': case 'c': case 'd': case 'e':
		case 'f': case 'g': case 'h': case 'i': case 'j':
		case 'k': case 'l': case 'm': case 'n': case 'o':
		case 'p': case 'q': case 'r': case 's': case 't':
		case 'u': case 'v': case 'w': case 'x': case 'y':
		case 'z':
		case '$': case '_': 
			scanIdent(); 
			return;
		... 
		}
	}
}
```

Java语言的标识符由字母、数字、下划线“\_”和美元符号“$”组成，第一个字符不能是数字，所以首个字符只可能是大小写字母、下划线与美元符号了。调用scanIdent\(\)方法得到标识符，scanIdent\(\)方法的实现代码如下：

```java
来源：com.sun.tools.javac.parser.Scanner 
private void scanIdent() {
	boolean isJavaIdentifierPart; 
	char high;
	do {
		if (sp == sbuf.length)// sbuf数组不能存储更多字符，调用putChar()方法进行扩容 
			putChar(ch);
		else
			sbuf[sp++] = ch; 
		scanChar();
		switch (ch) {
			case 'A': case 'B': case 'C': case 'D': case 'E':
			case 'F': case 'G': case 'H': case 'I': case 'J':
            case 'K': case 'L': case 'M': case 'N': case 'O':
			case 'P': case 'Q': case 'R': case 'S': case 'T':
			case 'U': case 'V': case 'W': case 'X': case 'Y':
			case 'Z':
			case 'a': case 'b': case 'c': case 'd': case 'e':
			case 'f': case 'g': case 'h': case 'i': case 'j':
			case 'k': case 'l': case 'm': case 'n': case 'o':
			case 'p': case 'q': case 'r': case 's': case 't':
			case 'u': case 'v': case 'w': case 'x': case 'y':
			case 'z':
			case '$': case '_':
			case '0': case '1': case '2': case '3': case '4':
			case '5': case '6': case '7': case '8': case '9':
			case '\u0000': case '\u0001': case '\u0002': case '\u0003':
			case '\u0004': case '\u0005': case '\u0006': case '\u0007':
			case '\u0008': case '\u000E': case '\u000F': case '\u0010':
			case '\u0011': case '\u0012': case '\u0013': case '\u0014':
			case '\u0015': case '\u0016': case '\u0017':
			case '\u0018': case '\u0019': case '\u001B':
			case '\u007F':
				break;
			case '\u001A': // EOI is also a legal identifierpart
				if (bp >= buflen) { // 已经没有待处理的字符 
					name = names.fromChars(sbuf, 0, sp); 
					token = keywords.key(name);
					return; 
				}
				break; 
			default:
				if (ch < '\u0080') { // ch是ASCII编码中的一个字符 
					isJavaIdentifierPart = false;
				// 所有合法的ASCII字符已经在上面的case分支中进行了处理
				} else {
					high = scanSurrogates(); // 获取高代理项 
					if (high != 0) {
						if (sp == sbuf.length) { 
							putChar(high);
						} else {
							sbuf[sp++] = high; 
						}
						// 方法会判断通过高代理项和低代理项表示的字符是否为合法
						// 标识符的首字符
						isJavaIdentifierPart =Character.isJavaIdentifierPart (Character.toCodePoint(high, ch));
					} else {
						isJavaIdentifierPart =Character.isJavaIdentifierPart(ch);
					} 
				}
			if (!isJavaIdentifierPart) {
            	name = names.fromChars(sbuf, 0, sp); 
				token = keywords.key(name);
				return;
			}
		} 
	} while (true);
}
```

其中的sbuf就是一个默认大小为128的字符数组，临时用来存储从buf数组中读出来的若干个字符。scanIdent\(\)方法将所有组成标识符的字符从buf数组中读取出来按顺序存储到sbuf数组中，最后作为参数调用names.fromChars\(\)方法获取NameImpl对象方法对字母、数字、下划线、美元符号及一些控制字符不做任何处理，直接通过break跳出switch语句后重新执行do\-while循环，然后将这些字符存储到sbuf数组中，这都是标识符的一部分。控制字符也可以作为合法标识符的一部分，不过排除了分隔符、换页符等。例如，声明一个Object变量，代码如下：

```java
A a\u0000 = null;
```

a\\u0000也是合法标识符的一部分。当读取到了文件末尾字符时，不再继续读取，通过sbuf查找对应的Token对象。在之前初始化buf时做了预处理，将最后一个结尾的字符赋值为EOI，也就是'\\u001A'。

默认分支中，当ch小于'\\u0080'时表示这个字符已经不是合法标识符的一部分了，因为默认分支之前的分支已经处理了所有为合法标识符首字符的情况，如果再出现ASCII编码中的字符就能确定不是合法标识符的一部分了。isJavaIdentifierPart被赋值为false，这样sbuf数组中存储的字符就会形成一个Token对象。

## 数字处理

数字的处理包括对整数和浮点数的处理，具体的实现代码如下：

```java
来源：com.sun.tools.javac.parser.Scanner 
public void nextToken() {
            prevEndPos = endPos;
            sp = 0;

            while (true) {
                pos = bp;
                switch (ch) {
                ...
                case '0':
                    scanChar();
                    if (ch == 'x' || ch == 'X') { // 处理十六进制表示的整数或浮点数
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
                case '1': case '2': case '3': case '4':
                case '5': case '6': case '7': case '8': case '9':
                    scanNumber(10);
                    return;
                case '.':
                    scanChar();
                    // 处理十进制中的小数部分
                    if ('0' <= ch && ch <= '9') {
                        putChar('.');
                      // 处理十进制中的小数及后缀部分
                        scanFractionAndSuffix();
                    } else if (ch == '.') {
                        putChar('.'); putChar('.');
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
                    ...
		}
    }
}
```

当以0x或0X开头时，按十六进制数字处理；当以0b或0B开头时，按二进制数字处理；当以0开头时，按八进制数字处理，对于八进制来说，0与数字中间还允许出现任意的下划线；当以数字1到9中的任何一个数字开头，则按十进制数字处理；如果以点“.”开头，可能是小数，也可能是方法中的可变参数的表示方式，或者只是一个单纯的分隔符。首先来看整数相关的文法如下：

IntegerLiteral: 

DecimalIntegerLiteral 

HexIntegerLiteral 

OctalIntegerLiteral 

BinaryIntegerLiteral

DecimalIntegerLiteral:

DecimalNumeral IntegerTypeSuffixopt 

HexIntegerLiteral:

HexNumeral IntegerTypeSuffixopt 

OctalIntegerLiteral:

OctalNumeral IntegerTypeSuffixopt 

BinaryIntegerLiteral:

BinaryNumeral IntegerTypeSuffixopt 

IntegerTypeSuffix: one of lL

由文法可知，整数可以使用二进制、八进制、十进制和十六进制来表示，具体数字的写法可以由DecimalNumeral、HexNumeral、OctalNumeral与BinaryNumeral文法描述，感兴趣的读者可查阅JLS了解。接着看浮点数相关的文法如下：

FloatingPointLiteral: 

DecimalFloatingPointLiteral

HexadecimalFloatingPointLiteral

由文法可知，浮点数可以使用十进制与十六进制 来表示，具体数字的写法可以由DecimalFloatingPointLiteral与HexadecimalFloatingPointLiteral文法描述，感兴趣的读者可查阅JLS了解。

前面的代码中对十六进制、二进制、八进制与十进制都调用了scanNumber\(\)方法，并传递了参数16、2、8与10代表对对应进制的处理。由于scanNumber\(\)方法的处理细节比较多，为了节省篇幅，这里不做介绍，读者可熟悉了相关文法后自行阅读Javac源代码了解方法的具体实现。

对以点“.”开头的字符，如果后面跟随数字，则调用scanFractionAndSuffix\(\)方法处理小数及后缀部分；如果后面跟着点“.”字符，表示变长参数；其他情况下按分隔符处理。

## 分隔符

分隔符的处理逻辑相对简单，具体的实现代码如下：

```java
来源：com.sun.tools.javac.parser.Scanner 
public void nextToken() {
	sp = 0;
	while (true) { 
		switch (ch) {
		...
		case ',':
			scanChar(); token = COMMA; return; 
		case ';':
			scanChar(); token = SEMI; return; 
		case '(':
			scanChar(); token = LPAREN; return; 
		case ')':
			scanChar(); token = RPAREN; return; 
		case '[':
			scanChar(); token = LBRACKET; return; 
		case ']':
			scanChar(); token = RBRACKET; return; 
		case '{':
			scanChar(); token = LBRACE; return; 
		case '}':
			scanChar(); token = RBRACE; return;
		... 
		}
	} 
}
```

在遇到分隔符后调用scanChar\(\)方法，将ch更新为下一个待处理的字符，然后赋值token后方法直接返回。

## 斜杠作为首字符的处理

以斜杠“/”作为首字符的可能为注释，如单行注释、多行注释或文档注释，还可能是除法运算符“/” 或者复合赋值运算符“/=”，具体的实现代码如下：

```java
来源：com.sun.tools.javac.parser.Scanner 
public void nextToken() {
	sp = 0;
	while (true) { 
		switch (ch) {
		...
		case '/': 
			scanChar();
			if (ch == '/') {// 单行注释
				...
			} else if (ch == '*') { // 多行注释或文档注释
				...
			} else if (ch == '=') { // 复合赋值运算符“/=” 
				name = names.slashequals;
				token = SLASHEQ; 
				scanChar();
			} else { // 除法运算符“/” 
				name = names.slash; 
				token = SLASH;
			} 
			return;
			... 
		}
	} 
}
```

代码中对单行注释、多行注释和文档注释进行处理，不过以斜杠“/”开头的字符还可能是运算符或运算符的一部分。由于注释并不能影响程序运行的行为，所以这里省略了对注释的处理逻辑，读者可自行阅读Javac源代表了解相关的实现。

## 单引号作为首字符的处理

单引号作为首字符的只能是字符常量，其他情况下会报编译错误。具体的实现代码如下：

```java
来源：com.sun.tools.javac.parser.Scanner 
public void nextToken() {
	sp = 0;
	while (true) { 
		switch (ch) {
		...
		case '\'':
			scanChar();
			scanLitChar();
			if (ch == '\'') { 
				scanChar();
				token = CHARLITERAL; 
			}
			return;
		... 
		}
	} 
}
```

在Java源代码中，单引号作为首字符通常表示字符常量。调用scanLitChar\(\)方法扫描字符常量，最后将token直接赋值为CHARLITERAL。scanLitChar\(\)方法的实现代码如下：

```java
    private void scanLitChar() {
        if (ch == '\\') { // 处理转义字符
            if (buf[bp+1] == '\\' && unicodeConversionBp != bp) {
                bp++;
                putChar('\\');
                scanChar();
            } else {
                scanChar();
                switch (ch) {
                case '0': case '1': case '2': case '3':
                case '4': case '5': case '6': case '7':
                    char leadch = ch;
                    int oct = digit(8);
                    scanChar();
                    if ('0' <= ch && ch <= '7') {
                        oct = oct * 8 + digit(8);
                        scanChar();
                        if (leadch <= '3' && '0' <= ch && ch <= '7') {
                            oct = oct * 8 + digit(8);
                            scanChar();
                        }
                    }
                    putChar((char)oct);
                    break;
                case 'b':
                    putChar('\b'); scanChar(); break;
                case 't':
                    putChar('\t'); scanChar(); break;
                case 'n':
                    putChar('\n'); scanChar(); break;
                case 'f':
                    putChar('\f'); scanChar(); break;
                case 'r':
                    putChar('\r'); scanChar(); break;
                case '\'':
                    putChar('\''); scanChar(); break;
                case '\"':
                    putChar('\"'); scanChar(); break;
                case '\\':
                    putChar('\\'); scanChar(); break;
                default:
                    lexError(bp, "illegal.esc.char");
                }
            }
        } else if (bp != buflen) { // 处理非转义字符
            putChar(ch); 
          	scanChar();
        }
    }
```

scanListChar\(\)方法主要对转义字符进行处理， 转义字符相关的文法如下：

EscapeSequence:

\\ b    /\* \\u0008: backspace BS \*/

\\ t    /\* \\u0009: horizontal tab HT \*/

\\ n    /\* \\u000a: linefeed LF \*/

\\ f    /\* \\u000c: form feed FF \*/

\\ r    /\* \\u000d: carriage return CR \*/

\\ "    /\* \\u0022: double quote " \*/

\\ '    /\* \\u0027: single quote ' \*/

\\ \\    /\* \\u005c: backslash \\ \*/

OctalEscape   /\* \\u0000 to \\u00ff: from octal value \*/ 

OctalEscape:

\\ OctalDigit

\\ OctalDigit OctalDigit

\\ ZeroToThree OctalDigit OctalDigit 

OctalDigit: one of

0 1 2 3 4 5 6 7 

ZeroToThree: one of

0 1 2 3

在处理八进制转义字符时，会调用digit\(\)方法将八进制表示的数转换为十进制表示，然后强制转换为char类型后调用putChar\(\)方法。调用的digit\(\)方法的实现代码如下：

```java
private int digit(int base) { 
	char c = ch;
	int result = Character.digit(c, base);
	...
	return result; 
}
```

base指定c是二进制、八进制、十进制还是十六进制数中的字符，digit\(\)方法最终会将c转换为十进制表示的整数并返回，例如十六进制的a代表10，则方法最终会返回10。

## 双引号作为首字符的处理

双引号作为首字符的只能是字符串常量，具体代码如下：

```java
来源：com.sun.tools.javac.parser.Scanner 
public void nextToken() {
	sp = 0;
	while (true) { 
		switch (ch) {
		...
		case '\"': 
			scanChar();
			// 当ch不为双引号、不为回车换行且有待处理字符时，调用scanLitChar()方法扫描 
			// 字符串常量
			while (ch != '\"' && ch != CR && ch != LF && bp < buflen)
				scanLitChar();
				if (ch == '\"') {
				token = STRINGLITERAL; 
				scanChar();
			} 
			return;
		... 
		}
	} 
}
```

在Java源代码中，双引号作为首字符通常就是字符串常量。当ch不为双引号、不为回车换行且有待处理的字符时，循环调用scanLitChar\(\)方法扫描两个双引号之间的所有组成字符串的字符，最后token直接赋值为STRINGLITERAL。

## 默认的处理

除了之前介绍的7类以特定字符开头的处理外，剩下的字符全部都使用默认分支中的逻辑处理，例如一些运算符的首字符，以汉字开头的标识符等，具体的实现代码如下：

```java
    public void nextToken() {
            prevEndPos = endPos;
            sp = 0;
            while (true) {
                pos = bp;
                switch (ch) { // ch是标识符号或标识符号首字符
				...
                default:
                    if (isSpecial(ch)) {
                        scanOperator();
                    } else {
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
                            scanIdent();
                        } else if (bp == buflen || ch == EOI && bp+1 == buflen) { // JLS 3.5
                          // 已经没有待处理的字符了
                            token = EOF;
                            pos = bp = eofPos;
                        } else {
                            lexError("illegal.char", String.valueOf((int)ch));
                            scanChar();
                        }
                    }
                    return;
                }
            }
    }
```

首先调用isSpecial\(\)方法判断是否可能为标识符号，如果是就调用scanOperator\(\)方法进行处理，否则可能是标识符的首字符。当isJavaIdentifierStart的值为true时，表示是合法标识符的首字符，调用scanIdent\(\)方法进行处理。当isJavaIdentifierStart的值为false时，表示也不是合法标识符的首字符。那么判断bp是否等于buflen，如果等于，说明当前的ch是最后一个字符，可不处理；或者判断是否为特殊的结尾字符EOI，之前在Scanner类的构造方法中讲到过，buf数组中待处理字符的最后一个字符会被赋值为EOI。

isSpecial\(\)方法的实现代码如下：

```java
来源：com.sun.tools.javac.parser.Scanner 
private boolean isSpecial(char ch) {
	switch (ch) {
	case '!': case '%': case '&': case '*': case '?':
	case '+': case '-': case ':': case '<': case '=':
	case '>': case '^': case '|': case '~':
	case '@':
		return true; 
	default:
		return false; 
	}
}
```

isSpecial\(\)方法判断是否为标识符号或标识符号的一部分，如果不是，该方法将返回false，在nextToken\(\)方法中就会判断是否为标识符了。isSpecial\(\)方法如果返回true，会调用scanOperator\(\)方法扫描出完整的标识符号，这个方法的实现代码如下：

```java
来源：com.sun.tools.javac.parser.Scanner 
private void scanOperator() {
	while (true) { 
	putChar(ch);
	Name newname = names.fromChars(sbuf, 0, sp); 
	name = newname;
	token = keywords.key(newname); 
	scanChar();
	if (!isSpecial(ch)) 
		break;
	}
}
```

尽可能多地扫描字符，例如连续出现'/'与'='字符时，最终“/=”作为一个运算符，而不是“/”和 “=”各作为一个运算符。如果不为运算符，在nextToken\(\)方法中判断字符ch是否小于'\\u0080'。由于ASCII编码中最大字符的编码为'\\u007F'，所以如果小于'\\u0080'，将isJavaIdentifierStart设置为false，表示不可能是标识符的一部分。因为对于小于'\\u0080'的所有合法的ASCII字符，switch语句的各个case分支已经进行了处理，包括标识符的首字符。由于标识符首字符除了大小写字母、下划线与美元符号外，还可能是汉字等字符，所以需要调用Character.isJavaIdentifierStart\(\)方法进行判断，如果方法返回true，表示是合法标识符的首字符。后续处理的逻辑与之前调用scanIdent\(\)方法对标识符的处理逻辑一样，这里不再介绍。
