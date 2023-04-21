# JCIdent树节点的标注

JCIdent树节点可能引用的是类型、方法或变量相关的符号，在Attr类的visitIdent\(\)方法中完成标注。举个例子如下： 

【实例12\-2】

---

```java
class Test {
    public int a = 1;
    public void md() { }
    public void test() {
        int b = a;
        Test c = new Test();
        md();
    }
}
```

---

变量b的初始化部分是一个名称为a的JCIdent树节点，引用了成员变量a；变量c声明的是一个名称为Test的JCIdent树节点，引用了Test类；方法调用表达式md\(\)是一个JCMethodInvocation树节点，其中的meth是一个名称为md的JCIdent树节点，引用了方法md\(\)。 

调用visitIdent\(\)方法标注JCIdent树节点，首先进行符号的标注，主要实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Attr
public void visitIdent(JCIdent tree) {
    Symbol sym;
    if (pt.tag == METHOD || pt.tag == FORALL) {// 查找方法
        sym = rs.resolveMethod(_, env, tree.name, pt.getParameterTypes(),
pt.getTypeArguments());
    } else if (tree.sym != null && tree.sym.kind != VAR) {  // 处理导入声明
        sym = tree.sym;
    } else { // 查找类型或变量
        sym = rs.resolveIdent(_, env, tree.name, pkind);
    }
    tree.sym = sym;
    result = checkId(tree, env1.enclClass.sym.type, sym, env, pkind, pt, _);
}
```

---

对于符号查找来说，如果期望的类型是方法，则调用Resolve类的resolveMethod\(\)方法查找；如果期望的类型是变量，则调用resolveIdent\(\)方法查找。这两个方法在第11章详细介绍过，这里不再进行介绍。当tree.sym不为空且tree.sym.kind不等于VAR时主要用来处理导入声明，举个例子如下： 

【实例12\-3】

---

```java
import java.util.ArrayList; 
```

---

以上代码表示包名java的JCIdent树节点中的sym变量在符号输入阶段已经被赋值为PackageSymbol对象，所以直接取tree.sym值即可。 

标注了符号就需要标注类型了，调用checkId\(\)方法会计算类型，然后在checkId\(\)方法中调用check\(\)方法进行类型标注。由于sym可能是类型、方法或变量，所以checkId\(\)方法也根据符号的不同执行了不同的处理逻辑，下面分情况讨论。 

**1．sym是类型**

当sym.kind值为TYP时，表示sym是一个类型，checkId\(\)方法通过sym获取实现类型，主要实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Attr 
Type owntype;  
owntype = sym.type;  
```

---

直接获取sym.type值即可。 

**2．sym是变量**

当sym.kind值为VAR时，表示sym是一个变量，checkId\(\)方法通过sym获取实现类型，主要实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Attr
VarSymbol v = (VarSymbol)sym;
owntype = (sym.owner.kind == TYP && sym.name != names._this && sym.name !=
names._super)
          ? types.memberType(site, sym)
           : sym.type;
...
if (pkind == VAL) {
    owntype = capture(owntype); 
}
```

---

当sym是成员变量并且不是this或者super这两个隐式的变量时，调用types.memberType\(\)方法计算sym在site下的类型，否则直接取sym.type的值，举个例子如下： 

【实例12\-4】

---

```java
class Test<T> {
    T t = null;
    public void test(Test<String> p) {
        String x = p.t;
    }
}
```

---

在初始化局部变量x时，当site为Test\<String\>类型时，调用types.memberType\(\)方法得到成员变量t的类型为String，所以能够正确赋值给String类型的变量x。再举个例子如下： 

【实例12\-5】

---

```java
class Test<T extends Serializable> {
    public void test(T t) {
        Serializable x = t;
    }
}
```

---

代码中，将参数t的值赋值给变量x，如果sym表示t变量的符号，则直接获取sym.type得到t的类型为TypeVar。这个类型的上界为Serializable，所以赋值表达式正确。 

如果期望的符号是VAL时，还会调用capture\(\)方法对owntype进行捕获转换，举个例子如下： 

【实例12\-6】

---

```java
class Test<T extends Serializable> {
    public void test(Test<? extends Number> p) {
        Test<?> x = p;
    }
}
```

---

将参数p的值赋值给变量x，由于x为含有通配符类型的ClassType对象，所以首先需要调用capture\(\)方法获取捕获转换后的类型，然后才能判断捕获类型是否能正确赋值给Test\<?\>，赋值表达式正确。 

**3．sym是方法**

当sym.kind的值为MTH时，表示sym是一个方法，checkId\(\)方法通过sym获取实际的类型，主要实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Attr
JCMethodInvocation app = (JCMethodInvocation)env.tree;
owntype = checkMethod(site, sym, env, app.args,
                      pt.getParameterTypes(), pt.getTypeArguments(),
                      env.info.varArgs);
```

---

调用checkMethod\(\)方法计算owntype变量的值，checkMethod\(\)方法的实现代码如下： 

---

```java
public Type checkMethod(Type site,Symbol sym,Env<AttrContext> env,
                        final List<JCExpression> argtrees,List<Type> argtypes,
List<Type> typeargtypes,
                        boolean useVarargs) {
    Type owntype = rs.instantiate(env,site,sym,argtypes,typeargtypes, true,
useVarargs,_);
return owntype;
}
```

---

调用Resolve类中的instantiate\(\)方法得到具体的类型，instantiate\(\)方法在第11章介绍过。对于非泛型的方法来说，获取的是sym在site类型中的方法类型。泛型方法的计算比较复杂，在下一章将详细介绍。 
