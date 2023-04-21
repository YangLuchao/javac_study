# Name映射为Token

将Name对象映射为Token对象的类是Keywords，这个类的构造方法如下：

```java
来源：com.sun.tools.javac.parser.Keywords
    protected Keywords(Context context) {
		...
        for (Token t : Token.values()) {
            if (t.name != null)
                enterKeyword(t.name, t);
            else
                tokenName[t.ordinal()] = null;
        }

        key = new Token[maxKey+1];
        for (int i = 0; i <= maxKey; i++) key[i] = IDENTIFIER;
        for (Token t : Token.values()) {
            if (t.name != null)
                key[tokenName[t.ordinal()].getIndex()] = t;
        }
    }
```

循环所有的Token对象，如果name值不为空则调用enterKeyword\(\)方法建立Token对象到Name对象的映射；如果name值为空，将tokenName数组中调用t.ordinal\(\)方法获取的下标处的值设置为null。其中tokenName数组的定义如下：

```java
来源：com.sun.tools.javac.parser.Keywords
private Name[] tokenName = new Name[Token.values().length];
```

tokenName数组保存了Token对象到Name对象的映射，准确说是tokenName数组的下标为各个Token对象在Token枚举类中定义的序数（序数从0开始），而对应下标处的值为对应的Name对象。enterKeyword\(\)方法的实现代码如下：

```java
来源：com.sun.tools.javac.parser.Keywords 
private void enterKeyword(String s, Token token) {
	Name n = names.fromString(s); 
	tokenName[token.ordinal()] = n; 
	if (n.getIndex() > maxKey)
		maxKey = n.getIndex(); 
}
```

其中，maxKey是一个定义在Keywords类中类型为int的成员变量，这个变量保存了所有Name对象中的index的最大值。

最终tokenName数组的值如下：

```java
Name[0]=null // EOF
Name[1]=null // ERROR
Name[2]=null // IDENTIFIER
Name[3]=NameImpl("abstract")// ABSTRACT，对应着表示“abstract” 的NameImpl对象
...
Name[109]=NameImpl("@") // MONKEYS_AT，对应着表示“@”的NameImpl对象
Name[110]=null // CUSTOM
```

可以看到，有name值的Token对象都建立了到NameImpl对象的映射，不过词法分析过程一般的需求是通过具体的NameImpl对象查找对应的Token对象，所以还需要建立NameImpl对象到Token对象的映射关系，这个关系由数组key来保存。key的定义如下：

```java
来源：com.sun.tools.javac.parser.Keywords 
private final Token[] key;
```

查看keywords\(\)构造方法可知，首先通过tokenName\[t.ordinal\(\)\]表达式获取到具体的NameImpl对象，然后调用getIndex\(\)方法获取index值，这个值在前面讲到过，是字符数组在bytes数组中存储的起始位置。用这个值作为key数组的下标，值是Token对象。在调用enterKeyword\(\)方法时已经使用maxKey保存了index的最大值，所以在Keywords\(\)构造方法中初始化的数组大小为maxKey\+1，这样数组key才能有足够的容量容纳index作为下标进行检索。由于index不连续，所以数组key中没有Token对象对应的下标都初始化为IDENTIFIER。

假设现在从字符流中读取到一串字符串 

abstract，首先转换为Name对象，接着调用此Name对象的getIndex\(\)方法获取index值，将这个值作为key数组下标获取对应的Token对象，如调用key\(\)方法来获取，这个方法的实现如下：

```java
来源：com.sun.tools.javac.parser.Keywords 
public Token key(Name name) {
	return (name.getIndex() > maxKey) ? IDENTIFIER : key[name.getIndex()];
}
```

这个Key\(\)方法还根据maxKey值来判断是否为标识符，假如某个字符串为自定义标识符时会返回IDENTIFIER。
