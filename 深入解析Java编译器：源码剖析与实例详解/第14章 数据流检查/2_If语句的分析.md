# If语句的分析

### 14.2.1　if语句

if语句是程序中大量使用的流程控制结构，通过Flow类的visitIf\(\)方法进行数据流检查，主要检查if语句的活跃性及变量赋值的情况。在分析if语句的条件判断表达式时，除了用到inits与uinits状态变量外，还需要用到另外4个成员变量来辅助进行变量赋值检查，这4个变量在Flow类中的定义如下： 

---

```java
来源：com.sun.tools.javac.comp.Flow 
Bits initsWhenTrue; 
Bits initsWhenFalse; 
Bits uninitsWhenTrue; 
Bits uninitsWhenFalse;  
```

---

4个变量可以分为两组，一组是当条件判断表达式的结果为true时，用来保存变量赋值状态的initsWhenTrue与uninitsWhenTrue变量；另外一组是当条件判断表达式的结果为false时，用来保存变量赋值状态的initsWhenFalse与uninitsWhenFalse变量。4个变量可以保存执行完条件判断表达式时的各变量赋值状态，我们将这4个变量称为条件状态变量。 

在分析if语句时除了使用inits与uninits状态变量外，还需要使用条件状态变量，具体就是在分析if语句的body体内的语句时使用initsWhenTrue与uninitsWhenTrue，分析else语句的body体内的语句时使用initsWhenFalse与uninitsWhenFalse。 

visitIf\(\)方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Flow
public void visitIf(JCIf tree) {
    scanCond(tree.cond);
    Bits initsBeforeElse = initsWhenFalse;
    Bits uninitsBeforeElse = uninitsWhenFalse;
    inits = initsWhenTrue;
    uninits = uninitsWhenTrue;
    scanStat(tree.thenpart);
    if (tree.elsepart != null) { // if语句有else分支
        boolean aliveAfterThen = alive;
        alive = true;
        Bits initsAfterThen = inits.dup();
        Bits uninitsAfterThen = uninits.dup();
        inits = initsBeforeElse;
        uninits = uninitsBeforeElse;
        scanStat(tree.elsepart);
        inits.andSet(initsAfterThen);
        uninits.andSet(uninitsAfterThen);
        alive = alive | aliveAfterThen;
    } else { // if语句没有else分支
        inits.andSet(initsBeforeElse);
        uninits.andSet(uninitsBeforeElse);
        alive = true;
    }
}
```

---

在调用scanCond\(\)方法处理条件判断表达式tree.cond时，会初始化4个条件状态变量。 

在调用scanStat\(\)方法分析if分支tree.thenpart之前，将initsWhenTrue与unintsWhenTrue的值赋值给inits与unints，这样分析if分支语句时，使用inits与uninits就相当于使用initsWhenTrue与uninitsWhenTrue变量的值。由于在执行if分支语句的过程中，initsWhenFalse与unintsWhenFalse的值有可能被修改，如if分支语句中又有条件判断表达式需要调用scanCond\(\)方法进行处理，所以要通过方法的局部变量initsBeforeElse与uninitsBeforeElse来保存。 

如果当前分析的if语句有else分支tree.elsepart，则通过initsAfterThen与uninitsAfterThen保存处理tree.thenpart后的状态变量的值，然后将之前保存的initsWhenFalse（通过initsBeforeElse暂时保存）与unintsWhenFalse（通过uninitsBeforeElse暂时保存）的值赋值给inits与unints，这样分析else分支语句时，使用inits与uninits就相当于使用initsWhenFalse与unintsWhenFalse变量的值。完成else分支elsepart处理后，调用andSet\(\)方法将inits与unints变量的值分别与initsAfterThen与uninitsAfterThen变量的值进行与运算，求得的inits与uninits就是分析if语句之后的变量使用的状态变量。 

如果当前分析的if语句没有else分支tree.elsepart，则调用andSet\(\)方法将inits与unints变量的值分别与initsWhenFalse（通过initsBeforeElse暂时保存）与unintsWhenFalse（通过uninitsBeforeElse暂时保存）变量的值进行与运算，求得的inits与uninits就是分析if语句之后的变量使用的状态变量。 

再来看语句的活跃性。活跃性可以理解为语句是否可达，或者语句是否有可能被执行。当if语句有else分支时，最后通过alive=alive|aliveAfterThen来计算alive值，也就是if分支与else分支两条可能的执行路径中，只要有一条是活跃的，最终if语句之后的语句就是活跃的。在没有else分支的情况下，alive被设置为true，因为else分支不存在，所以if语句后续的语句都有可能被执行。举个例子如下： 

【实例14\-9】

---

```java
public void test(boolean res) {
    int a;
    if (res) {
        a = 1;
    } else {
        a = 2;
    }
    int b = a;
}
```

---

状态变量或条件状态变量的第1个位代表的是res变量的赋值状态，第2位代表的是a变量的赋值状态，在调用scanCond\(\)方法分析完if语句的条件判断表达式tree.cond之后，4个条件状态变量的值如下： 

---

```java
initsWhenTrue = 10
uninitsWhenTrue = 01
initsWhenFalse = 10
uninitsWhenFalse = 01
```

---

分析完if语句后求得inits=11、uninits=01。在分析int b=a语句时，a变量已经被明确初始化，因为inits的第2个位上的值为1，表示a变量已经被初始化，所以可以使用a变量的值初始化b变量。当a为非final变量时，不参考uninits的第2位的值，因为uninits中的值表示的是明确非赋值状态，主要用于final变量的检查，对于非final变量时，重复赋值并不会产生编译错误。 

如果注释掉else分支中a=2的赋值语句，分析完if语句后求得inits=10、uninits=01。在执行int b=a语句时，a变量没有被明确初始化，因为inits的第2个位上的值为0，所以Javac将报错，报错摘要为“可能尚未初始化变量a”。再举个例子如下： 

【实例14\-10】

---

```java
public void test(boolean res) {
    final int a;
    if (res) {
        a = 1;
    }
    a = 2;// 报错，可能已分配变量a
}
```

---

调用scanCond\(\)方法分析完if语句的条件判断表达式tree.cond之后，4个条件状态变量的值如下： 

---

```java
initsWhenTrue = 10
uninitsWhenTrue = 01
initsWhenFalse = 10
uninitsWhenFalse = 01
```

---

最后求得inits=10、uninits=00。在执行a=2赋值语句时，由于a是final变量，所以要判断变量是否重复赋值，a所对应的uninits中的第2个位的值为0，表示并不是明确非赋值状态，所以Javac报错，报错摘要为“可能已分配变量a”。inits中的第2位的值为0，表示不是明确赋值状态，所以不能取a变量的值。 

下面介绍visitIf\(\)方法中调用的scanCond\(\)方法的实现，代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Flow
void scanCond(JCTree tree) {
    if (tree.type.isFalse()) {// 条件判断表达式的结果为布尔常量false
        if (inits == null) 
            merge();
        initsWhenTrue = inits.dup();
        initsWhenTrue.inclRange(firstadr, nextadr);
        uninitsWhenTrue = uninits.dup();
        uninitsWhenTrue.inclRange(firstadr, nextadr);
        initsWhenFalse = inits;
        uninitsWhenFalse = uninits;
    } else if (tree.type.isTrue()) {// 条件判断表达式的结果为布尔常量true
        if (inits == null) 
            merge();
        initsWhenFalse = inits.dup();
        initsWhenFalse.inclRange(firstadr, nextadr);
        uninitsWhenFalse = uninits.dup();
        uninitsWhenFalse.inclRange(firstadr, nextadr);
        initsWhenTrue = inits;
        uninitsWhenTrue = uninits;
    } else {
        scan(tree);
        if (inits != null)
            split(tree.type != syms.unknownType);
    }
    if (tree.type != syms.unknownType)
        inits = uninits = null;
}
```

---

当调用tree.type.isFalse\(\)方法返回true时，表示条件判断表达式的结果是布尔常量false，对于if语句来说，if分支下的语句将永远得不到执行，最终的变量赋值情况要看else分支下语句的执行情况。条件判断表达式的结果为布尔常量true时的处理逻辑与为false时的处理逻辑类似，这里不再介绍。举个例子如下： 

【实例14\-11】

---

```java
public void test() {
    int a;
    if (false) {
        // nothing
    } else {
        a = 2;
    }
    int b = a;
}
```

---

实例14\-11能够正常编译。由于if语句的条件判断表达式的结果为布尔常量false，所以调用scanCond\(\)方法分析完if语句的条件判断表达式tree.cond之后，4个条件状态变量的值如下： 

---

```java
initsWhenTrue = 0
uninitsWhenTrue = 1
initsWhenFalse = 1
uninitsWhenFalse = 0
```

---

执行完if语句后4个条件状态变量的值如下： 

---

```java
initsWhenTrue = 1
uninitsWhenTrue = 1
initsWhenFalse = 0
uninitsWhenFalse = 1
```

---

最后求得inits=1、uninitsWhenFalse=1，可以看到a变量被明确初始化，所以a可以出现在赋值表达式的右侧。在求inits时，因为if分支永远不可能执行，所以inits的值取决于initsWhenFalse，在调用scanStat\(\)方法分析完tree.elsepart后，initsWhenFalse的值会被更新为1，所以inits最终的值也为1。 

条件判断表达式可能是基本表达式，也可能是一元、二元或三元表达式，后面将会详细介绍一元表达式、二元表达式或三元表达式作为条件判断表达式时的变量赋值检查。如果inits不为null，调用split\(\)方法为4个条件状态变量设置初始值，一般情况下也会将inits与unints设置为null。 

### 14.2.2　一元表达式与if语句 

只有当一元表达式的结果为布尔类型时才可以作为if语句的条件判断表达式，所以只需要调用visitUnary\(\)方法处理含有非运算符的一元表达式即可。visitUnary\(\)方法对含有非运算符的一元表达式的处理如下： 

---

```java

来源：com.sun.tools.javac.comp.Flow
public void visitUnary(JCUnary tree) {
    switch (tree.getTag()) {
    case JCTree.NOT:// 含有非运算符的一元表达式
        scanCond(tree.arg);
        Bits t = initsWhenFalse;
        initsWhenFalse = initsWhenTrue;
        initsWhenTrue = t;
        t = uninitsWhenFalse;
        uninitsWhenFalse = uninitsWhenTrue;
        uninitsWhenTrue = t;
        break;
        ...
    }
}
```

---

在调用scanCond\(\)方法分析tree.arg后得到4个条件状态变量，将initsWhenFalse与initsWhenTrue的值互换，将unintsWhenFalse与uninitsWhenTrue的值互换，体现了一元表达式中非运算符的语义。 

### 14.2.3　二元表达式与if语句 

只有当二元表达式的结果为布尔类型时才能作为if语句中的条件判断表达式，但只需要讨论含有或运算符和与运算符的二元表达式即可，因为这两个运算符有“短路”的功能，会影响条件判断表达式的执行，从而可能影响变量的赋值状态，所以在visitBinary\(\)方法中会重点处理含有这两个运算符的二元表达式。visitBinary\(\)方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Flow
public void visitBinary(JCBinary tree) {
    switch (tree.getTag()) {
    case JCTree.AND: // 含有与运算符的二元表达式
        scanCond(tree.lhs); 
        Bits initsWhenFalseLeft = initsWhenFalse;
        Bits uninitsWhenFalseLeft = uninitsWhenFalse;
        inits = initsWhenTrue;
        uninits = uninitsWhenTrue;
        scanCond(tree.rhs); 
        initsWhenFalse.andSet(initsWhenFalseLeft);
        uninitsWhenFalse.andSet(uninitsWhenFalseLeft);
        break;
    case JCTree.OR:// 含有或运算符的二元表达式
        scanCond(tree.lhs);
        Bits initsWhenTrueLeft = initsWhenTrue;
        Bits uninitsWhenTrueLeft = uninitsWhenTrue;
        inits = initsWhenFalse;
        uninits = uninitsWhenFalse;
        scanCond(tree.rhs);
        initsWhenTrue.andSet(initsWhenTrueLeft);
        uninitsWhenTrue.andSet(uninitsWhenTrueLeft);
        break;
    default:
        scanExpr(tree.lhs);
        scanExpr(tree.rhs);    
    }
}
```

---

含有或运算符和与运算符的二元表达式具有短路功能，逻辑的处理需要和它们的语义保持一致。 

对于与运算符来说，如果tree.lhs的值为false，则不会继续执行tree.rhs。首先调用scanCond\(\)方法分析tree.lhs，得到4个条件状态变量，如果要分析tree.rhs，则tree.lhs的值一定为true，那么就需要将initsWhenTrue与uninitsWhenTrue的值赋值给inits与unints，然后调用scanCond\(\)方法分析tree.rhs，最终inits与unints变量保存的就是二元表达式结果为true时变量的赋值状态。对tree.lhs的值为false与tree.rhs的值为false时的状态变量进行与运算后，得到二元表达式的结果为false时的变量赋值状态。 

对于或运算符来说，当tree.lhs的值为false时才会分析tree.rhs，在调用scanCond\(\)方法分析tree.rhs之前，需要将inits与unints初始化为initsWhenFalse与uninitsWhenFalse，最终的inits与unints变量保存的就是二元表达式结果为false时的变量赋值状态，对tree.lhs的值为true与tree.rhs的值为true时的状态变量进行与运算后，得到二元表达式的结果为true时的变量赋值状态。举个例子如下： 

【实例14\-12】

---

```java
public void test(boolean res) {
    int a;
    if (res && (a = 1) == 1) {
        int b = a;
    }
}
```

---

if语句的条件判断表达式是一个二元表达式，表达式的左侧为res，右侧为\(a=1\)==1，没有else分支，这里着重分析tree.thenpart中的语句int b=a时的变量赋值状态。 

visitBinary\(\)方法执行前，inits=10，uninits=01，第1个位表示res变量的状态，第2个位表示a变量的状态。 

在visitBinary\(\)方法中，当执行完case分支为JCTree.AND的scanCond\(tree.lhs\)语句后，4个条件状态变量的值如下： 

---

```java
initsWhenTrue=10
uninitsWhenTrue=01
initsWhenFalse=10
uninitsWhenTrue=01
```

---

最终求得的inits=10、uninits=01。 

在visitBinary\(\)方法中，当执行完case分支为JCTree.AND的scanCond\(tree.rhs\)语句后，4个条件状态变量的值如下： 

---

```java
initsWhenTrue=11
uninitsWhenTrue=01
initsWhenFalse=11
uninitsWhenTrue=01
```

---

在visitIf\(\)方法中，在分析tree.thenpart之前，会将inits与uninits初始化为initsWhenTrue和uninitsWhenTrue，所以inits=11、uninits=01。在执行实例14\-12的初始化表达式int b=a时，变量a是明确初始化的变量，可出现在初始化表达式的右侧，因此实例14\-12能够正常编译。 

### 14.2.4　三元表达式与if语句 

只有当三元表达式JCConditional的truepart与falsepart为布尔类型时，才能作为if语句的条件判断表达式。visitConditional\(\)方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Flow
public void visitConditional(JCConditional tree) {
    scanCond(tree.cond);
    Bits initsBeforeElse = initsWhenFalse;
    Bits uninitsBeforeElse = uninitsWhenFalse;
    inits = initsWhenTrue;
    uninits = uninitsWhenTrue;
    if (tree.truepart.type.tag == BOOLEAN && tree.falsepart.type.tag ==
BOOLEAN) {
        scanCond(tree.truepart);
        Bits initsAfterThenWhenTrue = initsWhenTrue.dup();
        Bits initsAfterThenWhenFalse = initsWhenFalse.dup();
        Bits uninitsAfterThenWhenTrue = uninitsWhenTrue.dup();
        Bits uninitsAfterThenWhenFalse = uninitsWhenFalse.dup();
        inits = initsBeforeElse;
        uninits = uninitsBeforeElse;
        scanCond(tree.falsepart);
        initsWhenTrue.andSet(initsAfterThenWhenTrue);
        initsWhenFalse.andSet(initsAfterThenWhenFalse);
        uninitsWhenTrue.andSet(uninitsAfterThenWhenTrue);
        uninitsWhenFalse.andSet(uninitsAfterThenWhenFalse);
    }
    ...
}
```

---

当三元表达式中的tree.truepart与tree.falsepart的结果为布尔类型时，调用scanCond\(\)方法分析tree.cond后得到4个条件状态变量。在分析tree.truepart之前，将inits与uninits变量初始化为initsWhenTrue与uninitsWhenTrue变量的值；在分析tree.falsepart之前，将inits与uninits变量初始化为initsBeforeElse与uninitsBeforeElse变量的值，也就是初始化为initsWhenFalse与uninitsWhenFalse变量的值。 

在分析tree.truepart与tree.falsepart时，调用2次scanCond\(\)方法会产生2组共8个条件状态变量，两两进行与运算后就可以得到最终作为条件判断表达式的4个条件状态变量的值了。 
