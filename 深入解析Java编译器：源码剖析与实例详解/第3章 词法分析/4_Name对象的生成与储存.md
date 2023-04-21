# Name对象的生成与储存

Javac会将Java源代码中用到的字符串封装为com.sun.tools.javac.util.Name对象，例如Java中的标识符号、保留关键字等，并且相同的字符串用同一个Name对象表示，这样在判断Name对象是否相等时就可以直接通过恒等运算符“==”来判断了。

多个Name对象通过Table类中的数组来存储，Name与Table相关类的继承关系如图3\-5所示。
![image](https://cdn.staticaly.com/gh/YangLuchao/img_host@master/20230418/image.31ldchivtuk0.webp)

Name类的实现类为NameImpl，定义在SharedNameTable类中，而Table类定义在Name类中，主要的实现类为SharedNameTable。

在Token类中定义的所有Token对象中，除去没有name的Token对象，每个Token对象的name都可以用一个NameImpl对象来表示，所有的NameImpl对象全部存储到了SharedNameTable类的hashes数组中。首先认识一下Table接口的主要实现类SharedNameTable，这个类中定义了如下重要的成员变量：

```java
来源：com.sun.tools.javac.util.SharedNameTable 
private NameImpl[] hashes;
public byte[] bytes;  // 字节数组
```

hashes是一个NameImpl类型的数组，通过计算NameImpl对象的哈希值将其存储到hashes数组的特定位置，如果出现冲突，就使用NameImpl类中定义的next变量将冲突的对象链接成单链表的形式。bytes数组将统一存储所有的NameImpl对象中需要存储的多个字符（注意字节数组无法直接存储字符，后面还会提到，这里暂且这么表述）。例如，某个NameImpl对象表示复合赋值运算符“/=”，需要按顺序存储两个字符'/'和'='，那么bytes数组将存储所有字符转换为字节的内容，然后通过起始位置的偏移index和字节所占的长度length来指定具体的存放位置。关于NameImpl类的next、index与length成员变量的定义如下：

```java
来源：com.sun.tools.javac.util.SharedNameTable.NameImpl 
NameImpl next;
int index; 
int length;
```

可以通过调用SharedNameTable类中的fromChars\(\)方法将字符数组映射为Name对象，这个方法的实现代码如下：

```java
    public Name fromChars(char[] cs, int start, int len) {
        int nc = this.nc;
        byte[] bytes = this.bytes;
      // 扩容操作
        while (nc + len * 3 >= bytes.length) {
            //          System.err.println("doubling name buffer of length " + names.length + " to fit " + len + " chars");//DEBUG
            byte[] newnames = new byte[bytes.length * 2];
            System.arraycopy(bytes, 0, newnames, 0, bytes.length);
            bytes = this.bytes = newnames;
        }
      // 计算字符数组要存储到字节数组时所需要占用的字节长度
        int nbytes = Convert.chars2utf(cs, start, bytes, nc, len) - nc;
      // 计算哈希值
        int h = hashValue(bytes, nc, nbytes) & hashMask;
        NameImpl n = hashes[h];
      // 如果产生冲突，使用next讲冲突袁术链接起来
        while (n != null &&
                (n.getByteLength() != nbytes ||
                !equals(bytes, n.index, bytes, nc, nbytes))) {
            n = n.next;
        }
      // 创建新的NameImpl对象
        if (n == null) {
            n = new NameImpl(this);
            n.index = nc;
            n.length = nbytes;
            n.next = hashes[h];
            hashes[h] = n;
            this.nc = nc + nbytes;
            if (nbytes == 0) {
                this.nc++;
            }
        }
        return n;
    }
```

参数cs一般是字符串调用toCharArray\(\)方法转换来的字符数组，例如“/=”字符串转换为含有两个字符'/'和'='的字符数组；参数start与length表示从cs的start下标开始取length个字符进行处理。一个典型的调用fromChars\(\)方法的fromString\(\)方法的实现如下：

```java
来源：com.sun.tools.javac.util.Name.Table 
public Name fromString(String s) {
	return fromChars(cs.toCharArray(), 0, cs.length); 
}
```

fromChars\(\)方法在实现时涉及几个成员变量，具体的定义如下：

```java
private NameImpl[] hashes; 
private int hashMask; 
public byte[] bytes; 
private int nc = 0;
```

其中，hashes用来保存多个NameImpl对象，多个NameImpl对象使用哈希存储，在计算哈希值时会使用hashMask来辅助计算，bytes存储了字符数组转为字节数组的具体内容，nc保存了bytes数组中下一个可用的位置，初始值为0，其他3个变量通常会在构造方法中初始化，代码如下：

```java
public SharedNameTable(Names names) {
	this(names, 0x8000, 0x20000); 
}
public SharedNameTable(Names names, int hashSize, int  nameSize) {
	super(names);
	hashMask = hashSize - 1;
	hashes = new NameImpl[hashSize]; 
	bytes = new byte[nameSize];
}
```

一般都是调用第1个构造方法得到SharedNameTable对象，然后在第2个构造方法中对各个变量进行初始化。

fromChars\(\)方法调用Convert.chars2utf\(\)方法将传入的字符数组cs转换为字节数组bytes.chars2utf\(\)方法的实现如下：

```java
来源：com.sun.tools.javac.util.Convert    
public static int chars2utf(char[] src, int sindex,
                                byte[] dst, int dindex,
                                int len) {
        int j = dindex;
        int limit = sindex + len;
  		// 循环处理src数组中的每个字符
        for (int i = sindex; i < limit; i++) {
            char ch = src[i];
            if (1 <= ch && ch <= 0x7F) { // 字符使用单字节表示
                dst[j++] = (byte)ch;
            } else if (ch <= 0x7FF) {// 字符使用双字节表示
                dst[j++] = (byte)(0xC0 | (ch >> 6));
                dst[j++] = (byte)(0x80 | (ch & 0x3F));
            } else { // 字符使用三字节表示
                dst[j++] = (byte)(0xE0 | (ch >> 12));
                dst[j++] = (byte)(0x80 | ((ch >> 6) & 0x3F));
                dst[j++] = (byte)(0x80 | (ch & 0x3F));
            }
        }
        return j;
    }
```

src数组保存的字符都是UTF\-8编码，所以一个字符可能会转换为多个字节存储。最后返回dst的下一个可用位置j，这样在fromChars\(\)方法中就可以根据返回值计算出存储字符数组cs所需要占用的字节个数nbytes了。

在fromChars\(\)方法中获取到nbytes后调用hashValue\(\)方法，返回的哈希值与hashMask做与“&”运算，这样就可以得到存储在hashes数组中的槽位值。hashValue\(\)方法的实现代码如下：

```java
来源：com.sun.tools.javac.util.Name.Table
protected static int hashValue(byte bytes[], int offset, int length) {
	int h = 0;
	int off = offset;
	for (int i = 0; i < length; i++) { 
		h = (h << 5) - h + bytes[off++];
	}
	return h; 
}
```

这个方法针对相同的bytes数组计算出相同的哈希值，保证了相同的字节数组得到相同的槽位值，同时也能通过计算的哈希值从hashes数组中获取保存的值。

在fromChars\(\)方法中，如果根据哈希值进行存储时，对应槽位上的值不为空并且与当前要保存的内容不同，则使用单链表来解决冲突；如果获取到的值为空，则创建NameImpl对象并保存index与length的值。所以fromChars\(\)方法兼有存储和查找的功能。

假如有个NameImpl对象为“/=”，具体的存储原理如图3\-6所示。
![image](https://cdn.staticaly.com/gh/YangLuchao/img_host@master/20230418/image.6qxl7vqgnw80.webp)

有了fromChars\(\)方法后，Javac就可以用Name对象来表示特定的字符数组或者说字符串了。假设词法分析认定连续读入的'/'与'='字符序列应该是一个Token对象，那么就需要通过这串字符序列或者说存储这个字符序列的字符数组找到对应的Token对象，在查找具体的Token对象之前，调用fromChars\(\)方法将字符数组转换为Name对象，然后通过Name对象查找具体的Token对象。完成Name对象到Token对象映射的类是com.sun.tools.javac.parser.Keywords。
