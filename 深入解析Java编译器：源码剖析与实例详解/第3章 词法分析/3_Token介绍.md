# Token介绍

在介绍如何将CharBuffer对象中的内容转换为Token流之前，需要认识一下Javac是如何定义Token的。通过Token枚举常量来定义Token的类型。首先来看Token类的定义，代码如下：

```java
来源：com.sun.tools.javac.parser.Token 
public enum Token implements Formattable {
	...
	Token() { 
		this(null);
	}
	Token(String name) { 
		this.name = name;
	}
	public final String name;
	... 
}
```

枚举类中有个String类型的变量name，如果name不为空，那么就表示将name所保存的字符串定义为一个特定的Token对象（指的就是Token常量）。这些Token对象大概分为4类，下面分别介绍。

## 标识符号

与标识符相关的Token对象如下：

```java
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
```

将Java语言中的运算符及分隔符等定义为了特定的Token对象，其中，Token.ELLIPSIS是为了支持JDK 7版本中新增的变长参数的语法。

## Java保留关键字

与Java保留关键字相关的Token对象如下：

```java
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
```

以上对Java语言中所有的保留关键字定义了对应的Token对象，包括没有使用的const与goto保留关键字。

## 标识符

标识符被定义为Token.IDENTIFIER，这个Token对象没有name值，用来泛指用户自定义的类名、包名、变量包、方法名等。

## 字面量

与字面量相关的Token对象如下：

```java
INTLITERAL, 
LONGLITERAL, 
FLOATLITERAL, 
DOUBLELITERAL, 
CHARLITERAL, 
STRINGLITERAL, 
TRUE("true"), 
FALSE("false"), 
NULL("null"),
```

除了基本类型的字面量外，还有String类型的字面量。另外，null通常用来初始化引用类型。

## 特殊类型

特殊类型有ERROR与EOF。当词法分析不能将读取到的一组字符映射为除ERROR之外的任何一种Token对象时，会将读取到下一个分隔符之前的所有字符映射为ERROR。当读取到文件的末尾时生成一个EOF，作为Token流的结束标记。
