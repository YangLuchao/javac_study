# Gen类介绍

Gen类继承了JCTree.Visitor类并覆写了visitXxx\(\)方法，可以根据标注语法树生成对应的字节码指令，为第18章Class文件的生成提供必要的信息。 

调用JavaCompiler类的compile\(\)方法会间接调用到Gen类的genClass\(\)方法，每个类都会调用一次genClass\(\)方法，因为每个类都可以定义自己的方法，需要为这些方法生成对应的字节码指令。genClass\(\)方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
public boolean genClass(Env<AttrContext> env, JCClassDecl cdef) {
        ClassSymbol c = cdef.sym;
        cdef.defs = normalizeDefs(cdef.defs, c);
        Env<GenContext> localEnv = new Env<GenContext>(cdef, new GenContext());
        localEnv.toplevel = env.toplevel;
        localEnv.enclClass = cdef;
        for (List<JCTree> l = cdef.defs; l.nonEmpty(); l = l.tail) {
            genDef(l.head, localEnv);
        }
}
```

---

以上代码中，调用normalizeDefs\(\)方法对一些语法树结构进行调整，主要是对匿名块和成员变量的初始化表达式进行调整。 

创建localEnv环境，Env对象localEnv的info变量的类型为GenContext，这个类中定义了一些变量和方法，用来辅助进行字节码指令的生成，后面将详细介绍。 

调用genDef\(\)方法对cdef中定义的每个成员进行处理，如果成员为方法就会为方法生成字节码指令。Gen类只会为方法生成字节码指令，但是一个类中的成员变量与块中也会含有需要生成字节码指令的表达式或语句，Javac会将这些具体的语法树结构重构到\<init\>方法与\<cinit\>方法中。 

\<init\>表示实例构造方法，所有关于实例变量的初始化表达式和非静态块都可以重构到这个方法中，举个例子如下： 

【实例16\-1】

---

```java
package chapter16;
public class Test {
    final int a = md();
    final int b = 1; 
    int c = 2;
    {
        int d = 3;
    }

    public int md(){
        return 0;
    }
}
```

---

实例经过normalizeDefs\(\)方法重构后相当于变为了如下形式： 

---

```java
package chapter16;
public class Test {
    final int a;
    final int b; 
    int c;
    public <init>() {
        super();
        a = md();
        b = 1;
        c = 2;
        {
            int d = 3;
        }
    }
    public int md(){
        return 0;
    }
}
```

---

可以看到，对于运行期才能得出值的变量进行了重构，在\<init\>构造方法中初始化这些变量，同时会将非静态匿名块重构到\<init\>构造方法中。 

\<clinit\>表示类构造方法，所有关于类变量的初始化部分和静态块都将重构到\<clinit\>方法中。如果一个类没有类变量与静态块，那么Javac将不会产生\<clinit\>方法，举个例子如下： 

【实例16\-2】

---

```java
package chapter16;
public class Test {
    final static int a = md();
    final static int b = 1; 
    static int c = 2;
    static{
        int d = 3;
    }
    public static int md(){
        return 0;
    }
}
```

---

实例16\-2经过调用normalizeDefs\(\)方法重构后相当于变为了如下形式： 

---

```java
package chapter16;
public class Test {
    final static int a;
    final static int b = 1; 
    static int c;
    static void <clinit>() {
        a = md();
        c = 2;
        static {
            int d = 3;
        }
    }
    public static int md(){
        return 0;
    }
}
```

---

可以看到，对于编译期能得出值的常量并不会发生任何变化，而对于其他的静态成员来说会在\<clinit\>方法中完成初始化。 

normalizeDefs\(\)方法对匿名块重构的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
JCBlock block = (JCBlock)def;
if ((block.flags & STATIC) != 0)
    clinitCode.append(block);
else
    initCode.append(block);
```

---

将非静态匿名块追加到\<init\>方法，将静态匿名块追加到\<clinit\>方法中，这样块的花括号可以形成一个作用域，能更好地避免块之间及块与方法中相关定义的冲突，也能更好地完成初始化工作。 

normalizeDefs\(\)方法对变量重构的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
JCVariableDecl vdef = (JCVariableDecl) def;
VarSymbol sym = vdef.sym;
checkDimension(_, sym.type);
if (vdef.init != null) {
    if ((sym.flags() & STATIC) == 0) {
        JCStatement init = make.Assignment(sym, vdef.init);
        initCode.append(init);
    } else if (sym.getConstValue() == null) {
        JCStatement init = make.Assignment(sym, vdef.init);
        clinitCode.append(init);
    } else {
        checkStringConstant(_, sym.getConstValue());
    }
}
```

---

如果对变量进行了初始化（vdef.init不为空），则对于实例变量来说，创建JCAssign树节点后追加到\<init\>方法中，而对于静态变量来说，如果在编译期不能确定具体的值，同样会创建JCAssign树节点并追加到\<clinit\>方法中。 

经过normalizeDefs\(\)方法重构后，只需要为方法生成字节码指令就可以满足一切需求。调整完成后还需要循环构造方法，并调用normalizeMethod\(\)方法进行处理。 

---

```java
来源：com.sun.tools.javac.jvm.Gen
if (initCode.length() != 0) {
    List<JCStatement> inits = initCode.toList();
    for (JCTree t : methodDefs) {
        normalizeMethod((JCMethodDecl)t, inits);
    }
}
```

---

如果initCode列表中有值，则需要调用normalizeMethod\(\)方法继续进行处理，也就是将初始化语句及非静态匿名块插入到构造方法中。normalizeMethod\(\)方法的实现代码如下： 

---

```java

来源：com.sun.tools.javac.jvm.Gen
void normalizeMethod(JCMethodDecl md, List<JCStatement> initCode) {
    if (md.name == names.init && TreeInfo.isInitialConstructor(md)) {
        List<JCStatement> stats = md.body.stats;
        ListBuffer<JCStatement> newstats = new ListBuffer<JCStatement>();
        if (stats.nonEmpty()) {
            while (TreeInfo.isSyntheticInit(stats.head)) {
                newstats.append(stats.head);
                stats = stats.tail;
            }
            newstats.append(stats.head);
            stats = stats.tail;
            while (stats.nonEmpty() && TreeInfo.isSyntheticInit(stats.head)) {
                newstats.append(stats.head);
                stats = stats.tail;
            }
            // 将初始化语句及非静态匿名块插入到构造方法中
            newstats.appendList(initCode);
            while (stats.nonEmpty()) {
                newstats.append(stats.head);
                stats = stats.tail;
            }
        }
        md.body.stats = newstats.toList();
    }
}
```

---

如果一个类中有多个构造方法，则需要将initCode列表中的内容追加到\<init\>方法中，不对首个形式为this\(...\)语句的构造方法追加内容，也就是如果有this\(...\)形式的语句，则调用TreeInfo类的isInitialConstructor\(\)方法将返回false，主要还是保证在创建对应类的对象时，能够执行initCode列表中的语句而且只执行一次。需要注意的是，在向newstats列表中追加语句时需要严格保证语句的顺序。 
