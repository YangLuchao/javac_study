# Class文件的结构

Class文件中存储了两种数据类型：无符号数和表。表是用来描述有层次关系的复合结构的数据，而无符号数可以用来标识一个具体结构的类型，或者还可以表示数量及属性长度等。本章在后续的描述中通常会以u1、u2、u4和u8来分别表示1个字节、2个字节、4个字节和8个字节的无符号数。在Javac的ByteBuffer类中提供了写入基本类型的常用方法，如appendByte\(\)方法，这个方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.util.ByteBuffer
public void appendByte(int b) {
    ...
    elems[length++] = (byte)b;
}
```

---

向字节数组elems中写入一个字节的内容，length保存着数组中写入的字节数量。elems与length变量的定义如下： 

---

```java
来源：com.sun.tools.javac.util.ByteBuffer 
public byte[] elems; 
public int length;  
```

---

在ByteBuffer类的构造方法中初始化elems。ByteBuffer对象表示一个具体的缓冲，主要通过elems来保存缓冲的内容，Javac在向Class文件中写入字节码时不会一个字节一个字节地写，而是先写入ByteBuffer缓冲中，然后一次性写入Class文件来提高写入的效率。 

再介绍一个ByteBuffer类中的appendChar\(\)方法，这个方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.util.ByteBuffer
public void appendChar(int x) {
    ...
    elems[length  ] = (byte)((x >>  8) & 0xFF);
    elems[length+1] = (byte)((x      ) & 0xFF);
    length = length + 2;
}
```

---

可以看到，在写入一个占用2个字节的无符号整数时，可按照高位在前的方式写入，并且写入的每个字节都要和0xFF做与运算，主要是为了防止符号扩展，严格保持字节存储时的样子。 

下面详细介绍一下Class文件的结构，如表18\-1所示。 

表18\-1　Class文件的结构 

![image](https://cdn.staticaly.com/gh/YangLuchao/img_host@master/20230418/image.2rj4dovb7js0.webp)

其中，“类型”列中以“\_info”结尾的类型都表示表类型，而“名称”列的方括号中描述了当前这个结构的数量，如果没有方括号，默认的数量为1。 

表18\-1列出了Class文件中可能出现的一些类型，同时也规定了这些不同类型存储时的顺序。当读取或者写入Class文件时，需要严格按照表18\-1中规定的顺序进行操作。 

在JavaCompiler类的compile2\(\)方法中调用generate\(\)方法，该方法会根据已有信息生成Class文件，该方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.main.JavaCompiler
public void generate(Queue<Pair<Env<AttrContext>, JCClassDecl>> queue, _) {
    for (Pair<Env<AttrContext>, JCClassDecl> x: queue) {
        Env<AttrContext> env = x.fst;
        JCClassDecl cdef = x.snd;
        JavaFileObject file;
        file = genCode(env, cdef);
    }
}
```

---

调用genCode\(\)方法处理queue队列中保存的数据，这个方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.main.JavaCompiler
JavaFileObject genCode(Env<AttrContext> env, JCClassDecl cdef) throws
IOException {
     if (gen.genClass(env, cdef) )
         return writer.writeClass(cdef.sym);
}
```

---

调用gen.genClass\(\)方法会初始化Gen类中定义的一些变量，该方法返回true表示初始化成功，然后调用ClassWriter对象writer的writeClass\(\)方法向Class文件写入字节码内容。writeClass\(\)方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.ClassWriter
public JavaFileObject writeClass(ClassSymbol c) throws IOException, 
PoolOverflow, StringOverflow{
    JavaFileObject outFile = fileManager.getJavaFileForOutput(CLASS_OUTPUT,
                                  c.flatname.toString(),JavaFileObject.Kind.
CLASS,c.sourcefile);
    OutputStream out = outFile.openOutputStream();
    writeClassFile(out, c);
    out.close();
    return outFile;
}
```

---

调用JavacFileManager对象fileManager的getJavaFileForOutput\(\)方法以获取字节码输出文件outFile，文件的名称通过调用c.flatname.toString\(\)方法获取，文件的路径通过CLASS\_OUTPUT指定。 

Javac使用ClassWriter类向Class文件写入字节码内容，另外还可以使用ClassReader类读取Class文件中的字节码内容。读取与写入是一个相反的过程，只要了解了Class文件的基本结构后，按照格式严格读取即可。由于篇幅有限，这里不再介绍ClassReader类读取Class文件的实现过程。 
