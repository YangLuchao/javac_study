# JCFieldAccess树节点的标注

JCFieldAccess树节点的标注在Attr类的visitSelect\(\)方法中完成，JCFieldAccess树节点可能引用的是类型、方法或变量相关的符号，举个例子如下： 

【实例12\-7】

---

```java
package chapter12;
public class Test {
    public int a = 1;
    public void md() { }
    public void test() {
        chapter12.Test t = new Test();
        int x = t.a;
        t.md();
    }
}
```

---

定义局部变量t的语法树节点为JCVariableDecl，其中的vartype是一个JCFieldAccess树节点，引用的是类Test；x的初始化部分是个JCFieldAccess树节点，引用的是成员变量a；方法调用表达式md\(\)是一个JCMethodInvocation树节点，其中的meth为JCFieldAccess树节点，引用的是方法md\(\)。 

调用Attr类的visitSelect\(\)方法标注JCFieldAccess树节点，实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Attr
public void visitSelect(JCFieldAccess tree) {
    int skind = 0;
    if (tree.name == names._this || tree.name == names._super || tree.name
== names._class) {
        skind = TYP;
    } else {
        if ((pkind & PCK) != 0)
            skind = skind | PCK;
        if ((pkind & TYP) != 0) 
            skind = skind | TYP | PCK;
        if ((pkind & (VAL | MTH)) != 0) 
            skind = skind | VAL | TYP;
    }
    // 标注tree.selected树节点并返回引用的符号
    Type site = attribTree(tree.selected, env, skind, Infer.anyPoly);
    // 根据tree.selected引用的符号和类型确定tree引用的符号和类型
    Symbol sitesym = TreeInfo.symbol(tree.selected);
    Symbol sym = selectSym(tree, sitesym, site, env, pt, pkind);
    tree.sym = sym;
}
```

---

visitSelect\(\)方法首先计算skind值，这是处理tree.selected时的符号期望。如果当前JCFieldAccess树节点的名称为this、super或者class时，对tree.selected的符号期望是TYP，因为这几个名称之前的限定符只能是TYP，举个例子如下： 

【实例12\-8】

---

```java
class Test {
    Object a = Test.class;
    Object b = Test.this.a;
}
```

---

a与b变量的初始化部分都是JCFieldAccess树节点，其中selected是名称为Test的JCIdent树节点，而Test引用的符号一定为TYP。 

如果对tree的符号期望是PCK，那么对tree.selected的符号期望只能是PCK，因为包名之前的限定符只能为包名；如果对tree的符号期望是TYP，那么对tree.selected的符号期望可以为TYP或PCK，也就是类型之前的限定符可以是类型或者包名；如果对tree的符号期望是VAL或MTH，那么对tree.selected的符号期望可以是VAL或者TYP，因为方法或变量前的限定符可以为变量或类型。 

对于JCFieldAccess树节点来说，首先需要调用attribTree\(\)方法标注tree.selected子节点，同时传递符号期望skind与类型期望Infer.anyPoly，举个例子如下： 

【实例12\-9】

---

```java
package compile;
import java.util.Random;
public interface Music {
    Random[] wizards = new Random[4];
}
```

---

在chapter12包中定义一个类Test，代码如下： 

【实例12\-9】（续）

---

```java
package chapter12;
public class Test{
    static int n = compile.Music.wizards.length;
}
```

---

现在对变量n的初始化表达式进行标注，初始化表达式对应的语法树如图12\-1所示。 

![image](https://cdn.staticaly.com/gh/YangLuchao/img_host@master/20230418/image.5hxgnn9kx040.webp)

图12\-1　初始化表达式的语法树 

调用Attr类的visitVarDef\(\)方法对变量n的JCVariableDecl树节点进行标注时，会调用attribExpr\(\)方法对变量的初始化表达式进行标注，代码如下： 

---

```
来源：com.sun.tools.javac.comp.Attr
VarSymbol v = tree.sym;
attribExpr(tree.init, _, v.type);
```

---

对于实例12\-9来说，v.type表示int类型的Type对象，这是对tree.init的类型期望。由于tree.init为JCFieldAccess树节点，所以调用Attr类的visitSelect\(\)方法进行标注。如表12\-3中给出了标注变量n的初始化表达式的各个项所调用的方法，以及对各个项的符号和类型期望。 

表12\-3　标注初始化表达式 

![image](https://cdn.staticaly.com/gh/YangLuchao/img_host@master/20230418/image.5w1c8o58shc0.webp)

表12\-3中“方法名称”一列从上到下表示按顺序调用Attr类的visitSelect\(\)或visitIdent\(\)方法，对变量初始化表达式中的各个项进行标注。可以看到，最后调用visitIdent\(\)方法标注JCIdent\(name=compile\)，而JCIdent\(name=compile\)也是首先完成标注的树节点，之后才会依次完成对JCFieldAccess\(name=Music\)、JCFieldAccess\(name=wizards\)与JCFieldAccess\(name=length\)的标注，每次完成一项的标注后，就会返回实际的类型。对于visitSelect\(\)方法来说，通过局部变量site接收到tree.selected的实际类型后，调用TreeInfo.symbol\(\)方法获取符号，实现代码如下： 

---

```java
来源：com.sun.tools.javac.tree.TreeInfo
public static Symbol symbol(JCTree tree) {
    tree = skipParens(tree);
    switch (tree.getTag()) {
    case JCTree.IDENT:
        return ((JCIdent) tree).sym;
    case JCTree.SELECT:
        return ((JCFieldAccess) tree).sym;
    case JCTree.TYPEAPPLY:
        return symbol(((JCTypeApply) tree).clazz);
    default:
        return null;
    }
}
```

---

由于tree一定是标注过的语法树，所以直接从语法树中的相关变量中获取符号即可，当tree为JCTypeApply对象时，递归调用symbol\(\)方法获取clazz对应的符号即可。 

在visitSelect\(\)方法中得到sitesym与site后就可以调用selectSym\(\)方法确定当前tree引用的符号。例如如果当前visitSelect\(\)方法标注的是JCFieldAccess\(name=Music\)，那么会在包compile下查找一个符号期望为pkind，类型期望为pt的sym。 

selectSym\(\)方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Attr
private Symbol selectSym(JCFieldAccess tree,Symbol location,Type site,
Env<AttrContext> env,
                         Type pt,int pkind) {
    Name name = tree.name;
    switch (site.tag) {
    case PACKAGE: // 限定符为包类型
        return rs.findIdentInPackage(env, site.tsym, name, pkind);
    case ARRAY: // 限定符为数组
    case CLASS: // 限定符为类或接口
        if (pt.tag == METHOD || pt.tag == FORALL) {
            return rs.resolveQualifiedMethod(
                _, env, location, site, name, pt.getParameterTypes(), pt.get
TypeArguments());
        } else if (name == names._this || name == names._super) {
            return rs.resolveSelf(pos, env, site.tsym, name);
        } else if (name == names._class) {
            // 当name为class时，可以确定限定符为类型
            Type t = syms.classType;
            List<Type> typeargs = allowGenerics ? List.of(types.erasure
(site)) : List.<Type>nil();
            t = new ClassType(t.getEnclosingType(), typeargs, t.tsym);
            return new VarSymbol( STATIC | PUBLIC | FINAL, names._class, t,
site.tsym);
        } else {
            Symbol sym = rs.findIdentInType(env, site, name, pkind);
            return sym;
        }
    case TYPEVAR: // 限定符为类型变量
        Symbol sym = selectSym(tree, location, capture(site.getUpperBound()),
env, pt, pkind);
        return sym;
    default:
        // 当限定符为基本类型时，只允许name为class
        if (name == names._class) {
            // 当name为class时，可以确定限定符为类型
            Type t = syms.classType;
            Type arg = types.boxedClass(site).type;
            t = new ClassType(t.getEnclosingType(), List.of(arg), t.tsym);
            return new VarSymbol(STATIC | PUBLIC | FINAL, names._class, t,
site.tsym);
        } 
    }
}
```

---

selectSym\(\)方法根据限定符类型site的不同计算当前JCFieldAccess树节点引用的符号。下面根据site.tag值的不同分情况讨论。 

**1．值为PACKAGE时**

当site.tag值为PACKAGE时，表示限定符为包类型，调用Resolve类的findIdentInPackage\(\)方法从指定的包符号site.tsym内查找名称为name的类或包。findIdentInPackage\(\)方法在第11章已经详细介绍过，这里不再介绍。 

**2．值为ARRAY或CLASS时**

当site.tag值为ARRAY时，表示限定符为数组，当site.tag值为CLASS时，表示限定符为类或接口。当类型期望为方法类型时，调用Resolve类中的resolveQualifiedMethod\(\)方法进行查找。resolveQualifiedMethod\(\)方法可以在有限定符的情况下查找引用的具体方法，在第11章详细介绍过。当JCFieldAccess树节点的name值为this或super时，调用resolveSelf\(\)方法获取引用的符号，实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Resolve
Symbol resolveSelf(_,Env<AttrContext> env,TypeSymbol c,Name name) {
    Env<AttrContext> env1 = env;
    while (env1.outer != null) {
        if (env1.enclClass.sym == c) {
            Symbol sym = env1.info.scope.lookup(name).sym;
            if (sym != null) {
                return sym;
            }
        }
        env1 = env1.outer;
    }
    log.error(_, "not.encl.class", c);
    return syms.errSymbol;
}
```

---

在第7章介绍符号输入的第二阶段时介绍过complete\(Symbol sym\)方法，这个方法中处理类型时，会向类型对应的本地作用域中输入thisSym与superSym符号，所以resolveSelf\(\)方法可以通过符号表查找this或super关键字引用的符号。 

当name为class时要进行特殊处理，如分析Integer.class时，Integer是类，最终Integer.class返回的是一个VarSymbol对象，名称为class，所以可以将class看作是Integer类中定义的一个变量，不过这个变量的类型为java.lang.Class\<Integer\>，或者如int\[\].class，返回VarSymbol对象，其类型为java.lang.Class\<int\[\]\>。 

对除以上情况外，其他情况调用Resolve类的findIdentInType\(\)方法查找符号引用，这个方法在第11章介绍过，这里不再介绍。 

**3．值为TYPEVAR时**

当site.tag值为TYPEVAR时，表示限定符为类型变量，首先获取类型变量的上界，这个上界类型中可能含有通配符类型，所以调用capture\(\)方法进行捕获转换，然后递归调用selectSym\(\)方法得到sym。 

**4．默认处理**

默认逻辑中只处理了基本类型的情况，对于除以上处理的类型和基本类型外，其他类型表示程序有错误，如site.tag值为ERROR与WILDCARD时，表示程序已经出错。 

当site为基本类型时，只能是形如int.class这样的形式，而且实现与之前介绍的处理Integer.class或int\[\].class的实现类似，不过基本类型会封装为对应的引用类型。 

对于实例12\-9来说，当调用visitSelect\(\)方法标注JCFieldAccess\(name=Music\)时，sitesym为PackageSymbol\(name=compile\)，site为PackageType\(tsym.name=compile\)。调用findIdentInPackage\(\)方法会返回ClassSymbol\(name=Music\)，这就是当前树节点引用的实际符号，调用checkId\(\)方法得到ClassType\(tsym.name=Music\)，这就是当前树节点引用的实际类型，ClassType\(tsym.name=Music\)会继续返回给visitSelect\(\)方法进行处理。如表12\-4所示，简单给出了初始化表达式标注的完整过程。 

表12\-4　初始化表达式的标注过程 

![image](https://cdn.staticaly.com/gh/YangLuchao/img_host@master/20230418/image.3kkm1rgyiwo0.webp)

![image](https://cdn.staticaly.com/gh/YangLuchao/img_host@master/20230418/image.6lgk9knmxr80.webp)

表12\-4从上到下按顺序标注了初始化表达式的各个项，同时给出了期望的符号和类型与最终查找到的实际符号和类型。 
