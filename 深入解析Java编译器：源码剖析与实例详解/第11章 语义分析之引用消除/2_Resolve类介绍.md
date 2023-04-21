# Resolve类介绍

## 11.1　Resolve类介绍 

Resolve类实现了对类型、变量与方法的引用消解，**引用消解就是找到正确的指向**，举个例子如下： 

【实例11\-1】

---

```java
package chapter11;
class Parent {
    int a = 1;
}
class Sub extends Parent {
    int a = 2;
    public void md() {
        int a =3;
        int b = a; // 使用局部变量a的值进行初始化
    }
}
```

---

在md\(\)方法中定义变量b时使用a变量的值进行初始化，但是从a使用的当前上下文环境出发可访问到的名称为a的变量有多个，如局部变量、成员变量与父类中各定义了一个名称为a的变量，所以需要通过符号来确定唯一的引用。 

Resolve类提供了许多方法用来确定被引用的类型、变量和方法，不过在具体的查找过程中可能会出现各种错误，例如无法找到被引用的符号，找到多个符号（引用歧义）等。为了更好地进行符号查找，在Resolve类中定义了一些内部类，这些内部类表示符号查找过程中的错误，涉及的主要类的继承体系如图11\-1所示。 

![image](https://cdn.staticaly.com/gh/YangLuchao/img_host@master/20230418/image.6jvnfdb9ldc0.webp)

图11\-1　表示错误的类的继承体系 

可以看到所有类的父类是Symbol，其中ResolveError与InvalidSymbolError都是抽象类，它们都有一些具体的子类，这些子类表示不同的符号查找错误，对应的kind的取值已经在Kinds类中预先进行了定义，具体如下： 

---

```java
来源：com.sun.tools.javac.code.Kinds
public static final int ERRONEOUS = 1 << 6;
public static final int AMBIGUOUS    = ERRONEOUS+1; // ambiguous reference
public static final int HIDDEN       = ERRONEOUS+2; // hidden method or field
public static final int STATICERR    = ERRONEOUS+3; // nonstatic member from
static
public static final int ABSENT_VAR   = ERRONEOUS+4; // missing variable
public static final int WRONG_MTHS   = ERRONEOUS+5; // methods with wrong
arguments
public static final int WRONG_MTH    = ERRONEOUS+6; // one method with wrong
public static final int ABSENT_MTH   = ERRONEOUS+7; // missing method
public static final int ABSENT_TYP   = ERRONEOUS+8; // missing type
```

---

如表11\-1所示为表示错误的类及可能的kind值的对应关系。 

表11\-1　类与Kinds类中定义的常量值的对应关系 

![image](https://cdn.staticaly.com/gh/YangLuchao/img_host@master/20230418/image.4clkmrlt1b40.webp)

在查找类型、变量或者方法的引用时都可能得到AmbiguityError、AccessError、StaticError与SymbolNotFoundError对象，在查找方法时可能得到InaplicableSymbolsError与InapplicableSymbolError对象，下面举几个例子。 

【实例11\-2】

---

```java
package chapter11;
interface IA {
    int a = 1;
}
interface IB {
    int a = 2;
}
class CA implements IA, IB {
    int b = a;// 报错，对a的引用不明确, IA中的变量 a和IB中的变量 a都匹配
}
```

---

实例11\-2将报错，报错摘要为“对a的引用不明确，IA中的变量a和IB中的变量a都匹配”。调用Resolve类中相关的方法查找a的引用时会返回AmbiguitError对象。 

【实例11\-3】

---

```java
package chapter11;
class CA {
    private int a = 1;
}
class CB extends CA {
    int b = a; // 报错，a可以在CA中访问private
}
```

---

实例11\-3将报错，报错摘要为“a可以在CA中访问private”。在查找a的具体引用时，判断父类CA中定义的变量a没有权限获取，返回AccessError对象。 

【实例11\-4】

---

```java
class Test{
    int a = 1;
    static{
        int b = a; // 报错，无法从静态上下文中引用非静态变量a
    }
}
```

---

实例11\-4将报错，报错摘要为“无法从静态上下文中引用非静态变量a”，调用Resolve类的相关方法将返回StaticError对象。如果删除定义变量a的语句，Resolve类的相关方法将返回SymbolNotFoundError对象，表示无法找到对应的符号。 

继承ResolveError类的子类的kind值都会比之前介绍的继承Symbol的子类如VarSymbol、MethodSymbol、PackageSymbol等的kind值要小，可以简单认为kind的值越小查找到的符号越精确。例如SymbolNotFoundError类的kind值要大于AmbiguityError类的kind值，所以AmbiguityError类型更精确，Javac会报符号引用歧义相关的错误。 

在查找符号引用的过程中会频繁调用Symbol对象的exits\(\)方法，这个方法可以判断对应的符号是否含有相关的定义。Symbol类中的exits\(\)方法默认返回true，PackageSymbol类会根据包下对应的目录或类是否存在进行判断，如果包下存在目录或类，exits\(\)方法将返回true，ClassSymbol、VarSymbol等调用exits\(\)方法都会返回true。ResolveError类会覆写exits\(\)方法默认返回false，这样ResolveError的两个子类symbolNotFoundError与InapplicableSymbolsError会返回false。InvalidSymbolError会覆写exits\(\)方法，所以InvalidSymbolError及相关子类InapplicableSymbolError、AmbiguityError及StaticError会返回true，而AccessError会返回false。由于AccessError代表没有权限获取符号，所以这个定义应该存在的，不过这里覆写为false是为了让程序变得更“聪明”，下一节将会详细介绍。 
