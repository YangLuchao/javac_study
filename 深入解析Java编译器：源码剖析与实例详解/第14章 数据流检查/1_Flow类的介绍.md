# Flow类的介绍

Flow类通过继承TreeScanner类并选择性覆写visitXxx\(\)方法来完成具体的数据流检查。这个类中的入口方法是analyzeTree\(\)，每一个顶层类都会调用这个方法初始化一些重要的变量，然后调用scan\(\)方法扫描语法树的各个语法节点进行数据流检查。 

本节将简单介绍一下Flow类中的变量赋值检查、语句活跃性分析及异常检查，后续将结合具体的判断、循环等结构进行分析。 

### 14.1.1　语句的活跃性分析 

语句的活跃性是指这个语句是否有可能被执行，或者说语句是否可达。在Flow类中定义了一个重要的变量alive用来表示语句的活跃性，具体定义如下： 

---

```java
来源：com.sun.tools.javac.comp.Flow 
private boolean alive;  
```

---

在analyzeTree\(\)入口方法中将这个变量的值初始化为true，后续可能会通过visitXxx\(\)方法将此值更新为false。在分析当前语句时，如果alive的值为false时，表示当前语句不可达，举个例子如下： 

【实例14\-1】

---

```java
public void test(){
    return;
    System.out.println("unreachable statement");// 报错，无法访问的语句
}
```

---

对test\(\)方法内的语句进行活跃性分析，在分析打印语句时，alive的值为false，表示打印语句不可达，实例报编译错误，错误摘要为“无法访问的语句”。一般在分析break、continue、return或throw等语句时会将alive的值更新为false。另外还有一些特殊的情况下也会将alive的值更新为false，举个例子如下： 

【实例14\-2】

---

```java
public void test() {
    while (true) { }
    System.out.println("unreachable statement");// 报错，无法访问的语句
}
```

---

while循环中没有break等语句跳出循环，所以while语句后续的打印语句不可达，在分析打印语句时，alive的值为false，实例报编译错误，错误摘要为“无法访问的语句”。再举个例子如下： 

【实例14\-3】

---

```java
public void test(boolean res) throws Exception {
    if (res) {
        throw new Exception();
    }
    System.out.println("reachable statement");
}
```

---

当if语句的条件判断表达式res的结果为false时，打印语句可达，所以在分析打印语句时，alive的值为true。如果将如上的实例更改为如下形式： 

【实例14\-4】

---

```java
public void test(boolean res) throws Exception {
    if (res) {
        throw new Exception();
    } else {
        throw new Exception();
    }
    System.out.println("unreachable statement");// 报错，无法访问的语句
}
```

---

在分析打印语句时，alive的值为false，因为无论if语句的条件判断表达式res的值为true还是false，打印语句都不可达。 

Flow类提供了scanDef\(\)、scanStat\(\)、scanExpr\(\)与scanCond\(\)方法分别用来遍历定义、表达式、语句和条件表达式。scanDef\(\)方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Flow
void scanDef(JCTree tree) {
    scanStat(tree);
    if (tree != null && tree.getTag() == JCTree.BLOCK && !alive) {
        log.error(_,"initializer.must.be.able.to.complete.normally");
    }
}
```

---

scanDef\(\)方法通常用来遍历匿名块，调用scanStat\(\)方法对匿名块进行扫描，如果tree为匿名块并且alive的值为false时，Javac将报错，举个例子如下： 

【实例14\-5】

---

```java
class Test {
    {  
      throw new RuntimeException();// 报错，初始化程序必须能够正常完成  
    }
}
```

---

匿名块中不能抛出异常，实例14\-5将报错，报错摘要为“初始化程序必须能够正常完成”。 

下面介绍scanStat\(\)方法的实现，代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Flow
void scanStat(JCTree tree) {
    if (!alive && tree != null) {
        log.error(_, "unreachable.stmt");
        if (tree.getTag() != JCTree.SKIP)  
            alive = true;
    }
    scan(tree);
}
```

---

当alive的值为false并且当前还有要执行的语句时会报错，如果当前要执行的语句为非JCSkip时，会将alive的值更新为true，这是一种错误恢复机制，以扫描更多的语句，在一次编译过程中发现更多的错误；如果当前要执行的语句为JCSkip时，不会将alive的值更新为true，因为这种语句没有执行的逻辑，直接忽略即可。 

scanExpr\(\)与scanCond\(\)方法的实现也相对简单，这里不再介绍。 

### 14.1.2　变量赋值检查 

局部变量在使用前必须进行显式初始化，而声明在类型中的成员变量，Java虚拟机会默认初始化为对应的0值。但是有一种特殊情况就是，final修饰的成员变量必须显式初始化，可以在定义变量时也可以在构造方法中进行初始化。不难理解，如果Java虚拟机将这样的变量也初始化为0值不会有多大意义，因为final修饰的变量只能初始化一次。 

在进行变量赋值检查时，首先要将需要进行变量赋值检查的成员变量与局部变量存储起来，与存储相关的变量的定义如下： 

---

```java
来源：com.sun.tools.javac.comp.Flow 
VarSymbol[] vars; 
int firstadr; 
int nextadr;  
```

---

其中，vars数组保存程序中已经声明的变量，firstadr保存相关作用域内声明的第一个变量的位置，而nextadr保存vars中下一个可用的存储位置。由于保存的是数组下标，所以这个值通常是从0开始递增的。在analyzeTree\(\)方法中初始化了以上定义的3个变量，代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Flow
if (vars == null)
    vars = new VarSymbol[32];
else
    for (int i=0; i<vars.length; i++)
        vars[i] = null;
firstadr = 0;
nextadr = 0;
```

---

在向vars数组中添加变量时，首先要调用trackable\(\)方法来判断有没有必要对变量进行赋值检查，如果trackable\(\)方法返回true，表示需要检查变量的赋值状态。trackable\(\)方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Flow
boolean trackable(VarSymbol sym) {
    return  sym.owner.kind == MTH || 
            sym.flags() & (FINAL | HASINIT | PARAMETER)) == FINAL;
}
```

---

trackable\(\)方法中的局部变量或形式参数都需要检查，表示这些变量的VarSymbol对象的owner都是方法。 

由final修饰的未被显式初始化的非形式参数需要检查，因为需要显式初始化后才能使用，或者不能重复进行初始化。举个例子如下： 

【实例14\-6】

---

```java
class Test {
    final int a;
    int b; // 不需要进行赋值检查，因为有默认的零值
    public void test() {
        final int c;
        int d;
        int e = 1;
    }
}
```

---

实例14\-6中的a、c、d与e变量在调用trackable\(\)方法后返回true，表示需要对这些变量进行赋值检查，调用newVar\(\)方法将这4个变量对应的VarSymbol对象添加到vars数组中，newVar\(\)方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Flow
void newVar(VarSymbol sym) {
    if (nextadr == vars.length) {// 扩容操作
        VarSymbol[] newvars = new VarSymbol[nextadr * 2];
        System.arraycopy(vars, 0, newvars, 0, nextadr);
        vars = newvars;
    }
    sym.adr = nextadr;
    vars[nextadr] = sym; // 将需要进行赋值检查的变量保存到vars数组中
    inits.excl(nextadr);
    uninits.incl(nextadr);
    nextadr++;
}
```

---

当nextadr的值等于vars数组的大小时进行扩容，因为vars数组已经没有剩余的存储空间了，将vars数组容量扩大一倍后，调用System.arraycopy\(\)方法将原数组内容复制到新数组并更新vars。 

将需要进行赋值检查的sym保存到vars数组中，为了能找到vars数组中保存的sym，将保存sym的数组下标nextadr的值保存到Symbol类中定义的adr变量中。 

当保存了需要进行赋值检查的变量后，就可以在数据流检查过程对变量进行赋值检查了。与赋值检查相关的变量有两个，在Flow类中的定义如下： 

---

```java
来源：com.sun.tools.javac.comp.Flow 
Bits inits; 
Bits uninits;  
```

---

这两个变量都是Flow类中定义的成员变量，后面在描述中将这两个变量称为状态变量。inits表示变量是否明确初始化，uninits表示变量是否明确非初始化。由于程序中可能需要同时对多个变量赋值状态进行跟踪，所以将inits与unints变量声明为com.sun.tools.javac.util.Bits类型。Bits类可以进行位操作，也就是说两个状态变量可以通过某个相同位置上的位来共同表示某个变量初始化的情况，下面来看Bits类的定义： 

---

```java
来源：com.sun.tools.javac.util.Bits
public class Bits {
    private final static int wordlen = 32;
    private final static int wordshift = 5;
    private final static int wordmask = wordlen - 1;
    private int[] bits;
    public Bits() {
        this(new int[1]);
    }
    public Bits(int[] bits) {
        this.bits = bits;
    }
    ...
}
```

---

bits数组用来保存位的相关信息，一般在构造方法中初始化为大小为1的int数组。由于一个int类型只有32位，所以如果要跟踪的变量的数量大于32时就需要更多的int类型的数来表示，这些数都按顺序存储到bits数组中。wordlen、wordshift与wordmask都是常量，为Bits中相关方法的实现提供必要的信息。 

下面介绍Bits类中定义的几个常用的方法。 

incl\(\)方法实现了存储的功能，方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.util.Bits
public void incl(int x) {
    Assert.check(x >= 0);
    sizeTo((x >>> wordshift) + 1);
    bits[x >>> wordshift] = bits[x >>> wordshift] | (1 << (x & wordmask));
}
```

---

首先通过\(x\>\>\>wordshift\)\+1计算存储x需要的数组大小，即需要多少个整数的位，例如要存储48，也就是将第48上的位设置为1，这时候计算出来的值为2，表示需要用两个整数来存储。调用sizeTo\(\)方法判断，如果bits数组小于2，就会扩容。x\>\>\>wordshift计算x保存到数组中的哪个整数的位中，bits\[x\>\>\>wordshift\]|\(1\<\<\(x&wordmask\)\)将之前存储的相关信息与当前的信息取或，保证之前保存的相关信息不丢失。sizeTo\(\)方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.util.Bits
private void sizeTo(int len) {
     if (bits.length < len) {
         int[] newbits = new int[len];
         System.arraycopy(bits, 0, newbits, 0, bits.length);
         bits = newbits;
     }
}
```

---

在创建Bits对象时通常会在构造方法中将bits初始化为大小为1的数组，所以如果存储48，将会扩容为大小为2的数组。 

通过isMember\(\)方法判断某个位是否为1，方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.util.Bits
public boolean isMember(int x) {
    return 0 <= x && 
            x < (bits.length << wordshift) &&
            (bits[x >>> wordshift] & (1 << (x & wordmask))) != 0;
}
```

---

由于bits数组有一定大小，所以如果bits数组大小为2，则2个整数最多有64个可用位，查询参数x不能大于64，判断条件x\<\(bits.length\<\<wordshift\)就是保证查询参数不能超出当前可用位的数量。通过bits\[x\>\>\>wordshif\]取出相关的整数后与对应的位执行与操作，如果不为0，则说明相应位为1，x是当前Bits对象的成员。 

excl\(\)方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.util.Bits
public void excl(int x) {
    Assert.check(x >= 0);
    sizeTo((x >>> wordshift) + 1);
    bits[x >>> wordshift] = bits[x >>> wordshift] & ~(1 << (x & wordmask));
}
```

---

excl\(\)方法可以将第x位上的数设置为0，具体就是通过bits\[x\>\>\>wordshift\]取出对应的整数，然后与~\(1 \<\< \(x & wordmask\)\)做与操作，这样这个整数对应的位就会变为0。 

Bits类中还定义了许多常用的方法，这里进行简单的列举如下： 

* dup\(\)方法：复制一份当前的Bits对象并返回。 
* inclRange\(int start,int limit\)方法：将第start位到第start\+limit位的所有位都设置为1，包括第start位，不包括第start\+limit位。 
* excludeFrom\(int start\)方法：将从第start位开始到最后一位的所有位都设置为0，包括最后一位。 
* andSet\(Bits xs\)方法：将当前的Bits对象与传入的xs做与操作，返回操作后的结果。 
* orSet\(Bits xs\)方法：将当前的Bits对象与传入的xs做或操作，返回操作后的结果。 
* diffSet\(Bits xs\)方法：操作当前的Bits对象，如果与传入的xs对应位上的值相同，将当前Bits对象对应位置为0，否则保持不变，如当前的Bits对象为001，与110操作后的结果为001。 
* nextBit\(int x\)方法：从第x位开始查找下一个为1的位，返回这个位的位置，如果不存在，返回\-1。 

Javac在实现过程中，经常会使用nextBit\(\)方法遍历所有值为1的位，例如： 

---

```java
for (int i = bits.nextBit(0); i>=0; i = bits.nextBit(i+1))    ...  
```

---

其中，bits为Bits对象，从第0位开始遍历所有为1的位，如果i为\-1，则结束循环。 

之前介绍了两个状态变量inits与uninits，下面使用这两个状态变量对变量赋值状态进行检查。在实例14\-6中，a、c、d与e变量需要进行赋值状态检查。a变量会保存到vars数组下标为0的位置，所以inits与uninits中第0个位表示的是a变量的状态，inits中第0个位的值为0，表示变量没有明确初始化，所以不能使用a变量的值，只有为1时才可以取a变量的值，如变量可以出现在赋值表达式的右侧；uninits中第0个位的值为1，表示变量明确未初始化，所以如果对应的变量有final修饰，可以对a变量进行初始化。在处理完test\(\)方法最后一条声明变量e的语句后，与变量赋值检查相关变量的值如图14\-1所示。 

![image](https://cdn.staticaly.com/gh/YangLuchao/img_host@master/20230418/image.14dxapvrg05.webp)

图14\-1　与变量赋值检查相关变量的值 

由final修饰的成员变量的特殊性在于，如果在定义时没有显式初始化，那么必须在构造方法或者初始化块内显式初始化，所以实例14\-6将报编译错误，报错摘要为“可能尚未初始化变量a”。 

在实例14\-6中，在方法test\(\)的最后添加一条赋值语句如下： 

---

```java
c = 1； 
```

---

对变量c进行了初始化，Javac在处理这样的赋值语句时会调用Flow类中的visitAssign\(\)方法，这个方法会间接调用letInit\(\)方法。letInit\(\)方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Flow
void letInit(_, VarSymbol sym) {
    if (sym.adr >= firstadr && trackable(sym)) {
        if ((sym.flags() & FINAL) != 0) {
            if ((sym.flags() & PARAMETER) != 0) {
 //对catch语句中声明的形式参数进行赋值操作
                if ((sym.flags() & UNION) != 0) { //multi-catch parameter
                    log.error(_, "multicatch.parameter.may.not.be.assigned",
sym);
                }else {
                    log.error(_, "final.parameter.may.not.be.assigned",sym);
                }
            } else if (!uninits.isMember(sym.adr)) {
 // 对没有明确非初始化的final变量进行初始化
                log.error(_,loopPassTwo ? "var.might.be.assigned.in.loop" :
"var.might.already.be.assigned",
                          sym);
            } else if (!inits.isMember(sym.adr)) {
 // 当变量没有明确初始化时，更新uninits与inits的值 
                uninits.excl(sym.adr);
                uninitsTry.excl(sym.adr);
            } else {
                uninits.excl(sym.adr);
            }
        }
        inits.incl(sym.adr);
    } else if ((sym.flags() & FINAL) != 0) {// 多次对final变量进行初始化
        log.error(_, "var.might.already.be.assigned", sym);
    }
}
```

---

letInit\(\)方法的实现有些复杂，尤其是对final变量进行了很多检查，因为final变量如果没有明确初始化或者多次初始化都会引起错误。对final变量的具体检查如下： 

1. 对形式参数的检查，不能对catch语句中声明的形式参数进行赋值操作。 
2. 不能对没有明确非初始化的final变量进行初始化，也就是调用letInit\(\)方法可能会导致final变量重复初始化。 
3. 当final变量不是形式参数并且明确未初始化时，此时调用inits.isMember\(\)方法将返回false，表明这个变量能够进行初始化，将uninits与uninitsTry中相应的位的状态设置为0，将inits中相应的位的状态设置为1，uninitsTry辅助进行try语句中变量的赋值状态检查，在后面将会介绍。 
4. 最后对不可达的final变量也进行了初始化，将uninits中相应的位设置为0，这样下次如果重复初始化就会报错，这是对程序错误的一种兼容处理。 

正常情况下，sym.adr都大于等于firstadr的值，如果小于firstadr并且sym是final变量的话，Javac将报错，举个例子如下： 

【实例14\-7】

---

```java
class Test {
    final int x;
    public Test(int d) { // 第1个构造方法
        x = 1;
    }
    public Test() { // 第2个构造方法
        this(2);
        x = 2; // 报错，可能已分配变量x
    }
}
```

---

可以看到两个构造方法都对变量x进行了初始化，但是第2个构造方法首先会调用第1个构造方法对x进行初始化，如果在当前的构造方法中再次初始化时就会报错。在处理第2个构造方法时会调用visitMethodDef\(\)方法，这个方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Flow
boolean isInitialConstructor = TreeInfo.isInitialConstructor(tree);
if (!isInitialConstructor)
    firstadr = nextadr;
```

---

其中，TreeInfo.isInitialConstructor\(\)方法判断构造方法中的第一个语句是否为this\(...\)这样的形式，也就是是否调用了其他构造方法，如果是则返回false。对于实例14\-7的第2个构造方法来说，最终会将nextadr的值赋值给firstadr，这样当前构造方法就不能再次初始化x变量了，因为当前的构造方法初始化变量的有效范围要大于等于firstadr，由于x的adr为0，而firstadr为1，再次调用letInit\(\)方法初始化x变量时，实例14\-7将报错，报错摘要为“可能已分配变量x”。可以看出，第1个构造方法中初始化成员变量后，第2个构造方法中就可以直接使用，如果第2个构造方法中没有this\(2\)语句，那么也需要对final变量进行初始化。 

向实例14\-6中的test\(\)方法的最后添加一条赋值语句如下： 

---

```java
c = d; // 报错，可能尚未初始化变量d 
```

---

实例将报错，报错摘要为“可能尚未初始化变量d”。由于d出现在赋值表达式的右侧，所以d变量必须有值，通过VarSymbol对象的adr可知d被存储到了vars数组下标为2的位置，而inits中第2个位的值为0，表示没有明确初始化，所以报错。对d变量的赋值状态检查，最终会调用visitIdent\(\)方法，该方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Flow
public void visitIdent(JCIdent tree) {
    if (tree.sym.kind == VAR) {
        checkInit(_, (VarSymbol)tree.sym);
        ...
    }
}
```

---

如果是变量则调用checkInit\(\)方法进行检查，这个方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Flow
void checkInit(_, VarSymbol sym) {
    if ( (sym.adr >= firstadr || sym.owner.kind != TYP) &&
        trackable(sym) &&
        !inits.isMember(sym.adr)) {
        log.error(_, "var.might.not.have.been.initialized",sym);
        inits.incl(sym.adr);
    }
}
```

---

实例14\-6中的变量d满足if语句的条件判断表达式，所以会报错，将inits中代表变量d的位的值设置为1，表示明确初始化，这是一种错误恢复机制，能够让Javac在一次编译过程中发现更多的错误。 

### 14.1.3　异常检查 

异常检查主要检查方法中抛出的异常有没有被捕获或者在方法上声明抛出，举个例子如下： 

【实例14\-8】

---

```java
package chapter14;
class FirstExc extends Exception { }
class SecondExc extends Exception { }
public class Test {
    public void test(String exc) throws SecondExc {
        try {
            if (exc.equals("FirstExc")) {
                throw new FirstExc();
            } else {
                throw new SecondExc();
            }
        } catch (FirstExc e) {
            // 报错，未报告的异常错误FirstExc; 必须对其进行捕获或声明以便抛出
            throw e; 
        }
    }
}
```

---

在try语句的body体内抛出了FirstExc与SecondExc异常，虽然catch语句对FirstExc异常进行了捕获，对未捕获的SecondExc异常在方法上也进行了声明，但是catch语句的body体内又对FirstExc异常进行了重抛，所以方法上仍然需要声明FirstExc异常，编译报错，报错摘要为“未报告的异常错误FirstExc；必须对其进行捕获或声明以便抛出”。 

进行异常检查的逻辑主要在visitTry\(\)方法中实现，但是visitTry\(\)方法在进行异常检查的同时，还需要进行变量赋值检查和语法的活跃性分析，所以代码实现起来比较复杂，在本章的第14.4节将详细介绍。 
