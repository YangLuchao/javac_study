# class文件结构

![class文件结构](https://raw.githubusercontent.com/YangLuchao/javac_study/main/src/book/chapter18/Test20Class%E6%96%87%E4%BB%B6%E7%9A%84%E7%BB%93%E6%9E%84.png)

u1、u2、u4和u8来分别表示1个字节、2个字节、4个字节和8个字节的无符号数，以“_info”结尾的类型都表示表类型。读取与写入class文件都是根据该表有严格的顺序

## 魔数及版本号

每个Class文件的头4个字节被称为魔数（Magic Number），它的唯一作用是确定这个文件是否为一个能被虚拟机接受的Class文件。紧接着魔数的4个字节存储的是Class文件的版本号：第5和第6个字节是次版本号（Minor Version），第7和第8个字节是主版本号（Major Version）。

魔数及版本号的定义

```java
	魔数的定义，来源：com.sun.tools.javac.jvm.ClassFile
  	public final static int JAVA_MAGIC = 0xCAFEBABE;
	版本号的定，来源：com.sun.tools.javac.jvm.Target
    // 主版本号
    public final int majorVersion; 
		// 次版本号
    public final int minorVersion;
    private Target(_, int majorVersion, int minorVersion) {
        ...
        this.majorVersion = majorVersion;
        this.minorVersion = minorVersion;
    }
    
```

魔数及版本号的使用

```java
	来源：com.sun.tools.javac.jvm.ClassWriter
  public void writeClassFile(OutputStream out, ClassSymbol c)
        throws IOException, PoolOverflow, StringOverflow {
  		...
	      // poolbuf是常量池缓冲区对象  
      	poolbuf.appendInt(JAVA_MAGIC);
        // 次版本号
        poolbuf.appendChar(target.minorVersion);
        // 主版本号
        poolbuf.appendChar(target.majorVersion);
 			...
  }
```

## 常量池

```java
来源：com.sun.tools.javac.jvm.ClassWriter#writePool
void writePool(Pool pool) throws PoolOverflow, StringOverflow {
  ...
  while (i < pool.pp) {
    ...
    // 往常量池中追加各种信息
    ...
  }
  // 要在Class文件的第8个和第9个字节中写入常量池项的数量,pool.pp保存了常量池项的数量
  putChar(poolbuf, poolCountIdx, pool.pp);
}
```

