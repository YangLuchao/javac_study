[toc]

# Javac编译过程

![转变过程](https://raw.githubusercontent.com/YangLuchao/javac_study/main/src/book/chapter1/%E8%BD%AC%E5%8F%98%E8%BF%87%E7%A8%8B.png)

# 入口

```java
来源：com.sun.tools.javac.main.Main#compile(java.lang.String[])
  public int compile(String[] args) {
        // 创建上下文
        Context context = new Context();
        // 上下文中放入JavacFileManager
        JavacFileManager.preRegister(context); 
  			// 执行编译
        int result = compile(args, context);
        if (fileManager instanceof JavacFileManager) {
            ((JavacFileManager)fileManager).close();
        }
        return result;
    }
```



# java源代码

在符号表填充阶段会对.java文件和.class文件进行查找和加载

com.sun.tools.javac.jvm.ClassReader#fillIn方法会调用

com.sun.tools.javac.file.JavacFileManager#list方法

```java
来源：com.sun.tools.javac.file.JavacFileManager#list
  // loaction 是.java或者.class文件要查找的地址
  // packageName 是要查找文件的包名
  // kinds 指明为是.java文件还是.class文件
    public Iterable<JavaFileObject> list(Location location,
                                         String packageName,
                                         Set<JavaFileObject.Kind> kinds,
                                         boolean recurse)
        throws IOException
    {
  			// 文件迭代器
        Iterable<? extends File> path = getLocation(location);
        if (path == null)
            return List.nil();
        RelativeDirectory subdirectory = RelativeDirectory.forPackage(packageName);
        ListBuffer<JavaFileObject> results = new ListBuffer<JavaFileObject>();

        for (File directory : path)
          	// 如果directory是目录，将目录下满足要求的文件追加到results列表中
          	// 如果directory是压缩包，最终创建ZipFileIndexArchive对象
          	// 压缩包中满足要求的文件并追加到results列表中
            listContainer(directory, subdirectory, kinds, recurse, results);
        return results.toList();  
}
```

# 词法分析 -> token流

com.sun.tools.javac.main.JavaCompiler#compile方法会间接调用com.sun.tools.javac.file.RegularFileObject#getCharContent方法，将java源文件转换了字符流。

获取字符流后，间接调用com.sun.tools.javac.main.JavaCompiler#parse方法，在while循环中将字符流转化为Name流映射为token流

[com.sun.tools.javac.parser.Scanner](https://github.com/YangLuchao/javac_study/blob/main/src/com/sun/tools/javac/parser/Scanner.java "Scanner")

```java
来源：com.sun.tools.javac.parser.Scanner#nextToken
public void nextToken() {
  	...
      while (true) {
                pos = bp;
                // switch语句会根据首个出现的字符来判断可能生成的Token对象
                switch (ch) { 
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
                }
      }
    ...
}
```

# 语法分析 -> 抽象语法树

转换为Token流后，调用com.sun.tools.javac.tree.TreeMaker#TopLevel方法new 一个编译单元

```java
来源：com.sun.tools.javac.tree.TreeMaker#TopLevel
    public JCCompilationUnit TopLevel(List<JCAnnotation> packageAnnotations,
    JCExpression pid,
    List<JCTree> defs) {
        ...
        JCCompilationUnit tree = new JCCompilationUnit(packageAnnotations, pid, defs, null, null, null, null);
        ...
        return tree;
    }
```

调用com.sun.tools.javac.comp.Enter#complete方法完成对符号的输入

```java
来源：com.sun.tools.javac.comp.Enter#complete
    public void complete(List<JCCompilationUnit> trees, ClassSymbol c) {
        ...
        try {
          	// 将当前编译单元下所有的非本地类的类符号输入到对应owner类的符号表中
            classEnter(trees, null);

            if  (memberEnter.completionEnabled) {
                while (uncompleted.nonEmpty()) {
                    ClassSymbol clazz = uncompleted.next();
                    if (c == null || c == clazz || prevUncompleted == null)
                        // 完成类符号对象或包符号对象中的符号输入
                        clazz.complete();
                    ...
                }
                for (JCCompilationUnit tree : trees) {
                    if (tree.starImportScope.elems == null) {
                        ...
                        Env<AttrContext> topEnv = topLevelEnv(tree);
                        // 符号输入的第二阶段,类中的方法和成员变量进行符号输入
                        memberEnter.memberEnter(tree, topEnv);
                      	...
                    }
                }
            }
        } finally {
            ...
        }
    }
```

完成符号输入后，抽象语法树就构建完成

_**注意：在构建抽象语法树之后会处理插入式注解器，比如对lombok下相关注解器对java源代码的注解进行处理，生成对应的Java代码。生成代码后会重复符号输入的过程，并且返回新的JavaCompiler对象。**_

# 语义分析 -> 标注语法树

在com.sun.tools.javac.main.JavaCompiler#compile2中进行语义分析

```java
来源：com.sun.tools.javac.main.JavaCompiler#compile2
    private void compile2() {
       ...
         generate(desugar(flow(attribute(todo))));
       ...
    }
```

## Attr

com.sun.tools.javac.comp.Attr完成语义分析

-   引用消除
-   类型检查
-   常量折叠

## Flow

com.sun.tools.javac.comp.Flow完成语法检查

-   变量赋值检查
-   语句活跃性分析
-   异常检查

## Lower

com.sun.tools.javac.comp.Lower完成解语法糖

-   可变长参数
-   自动拆装箱
-   条件编译
-   增强for循环
-   选择表达式
-   枚举类
-   泛型擦除
-   内部类

# 代码生成 -> 字节码

-   字节码指令生成
-   .class文件生成