# 可寻址实体Item

在Gen类的入口方法genClass\(\)中调用genDef\(\)方法来遍历类中定义的成员，这个方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
public void genDef(JCTree tree, Env<GenContext> env) {
    Env<GenContext> prevEnv = this.env;
    this.env = env;
    tree.accept(this);
    this.env = prevEnv;
}
```

---

相关变量的定义如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
Env<GenContext> env;
Type pt;
Item result;
```

---

除了genDef\(\)方法，还可以调用genStat\(\)和genStats\(\)方法遍历语句，调用genArgs\(\)方法遍历参数，调用genExpr\(\)方法遍历表达式，**最后处理的结果result的类型为Item**。**Item及相关的子类代表可寻址的实体**，它们都以静态内部类的形式定义在com.sun.tools.javac.jvm.Items类中。Items类提供了许多操作Item的方法。 

Item抽象类的定义代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Items.Item
abstract class Item {
    int typecode;
    Item(int typecode) {
        this.typecode = typecode;
    }
}
```

---

typecode保存了类型，值已经在ByteCodes类中预先进行了定义，在16.2.1节已介绍过，为了阅读方便，这里再次给出定义： 

---

```java
来源：com.sun.tools.javac.jvm.ByteCodes
int INTcode         = 0, // int code
    LONGcode        = 1, // long code
    FLOATcode       = 2, // float code
    DOUBLEcode      = 3, // double code
    OBJECTcode      = 4, // object code
    BYTEcode        = 5, // byte code
    CHARcode        = 6, // char code
    SHORTcode       = 7, // short code
    VOIDcode        = 8, // void code
    TypeCodeCount   = 9; // type code count
```

---

在Items类中定义了一个stackItem变量如下： 

---

```java
来源：com.sun.tools.javac.jvm.Items 
private final Item[] stackItem = new Item[TypeCodeCount];  
```

---

在Items类的构造方法中初始化stackItem数组，相关的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Items
for (int i = 0; i < VOIDcode; i++)
    stackItem[i] = new StackItem(i);
voidItem = new Item(VOIDcode) {
        public String toString() { 
            return "void"; 
        }
};
stackItem[VOIDcode] = voidItem;
```

---

stackItem数组中存储的是StackItem或者Item匿名类对象，不同的对象由typecode来区分。例如，当typecode值为0时是INTcode，代表一个整数类型的实体。 

Item类中还定义了6个重要的方法，代表对这些实体进行哪些具体的操作。下面简单介绍一下这6个方法。 

1. load\(\)方法：将当前的实体加载到操作数栈中。 
2. store\(\)方法：将操作数栈栈顶项存储到这个实体中。 
3. invoke\(\)方法：调用由这个实体所代表的方法。 
4. duplicate\(\)方法：复制栈顶项。 
5. drop\(\)方法：丢弃当前的实体。 
6. stash\(\)方法：复制栈顶项，插入到当前实体的下面。 

调用Item类的load\(\)、store\(\)与invoke\(\)方法将抛出AssertionError类型的异常，调用Item类的duplicate\(\)与drop\(\)方法将不做任何操作。Item类的stash\(\)方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Items.Item
void stash(int toscode) {
    stackItem[toscode].duplicate();
}
```

---

传入的toscode表示当前操作数栈顶的数据类型，调用duplicate\(\)方法复制一个。 

Item的子类将根据需要覆写以上的方法，其中的CondItem代表一个有条件或无条件跳转，这个类对于条件判断表达式的处理比较重要，将在17.2节详细介绍。下面详细介绍除CondItem类外剩余的Item子类的定义。 

### 16.4.1　LocalItem类 

每个LocalItem对象代表一个本地变量，LocalItem类的定义如下： 

---

```java
来源：com.sun.tools.javac.jvm.Items.LocalItem
class LocalItem extends Item {
    int reg;
    Type type;
    LocalItem(Type type, int reg) {
        super(Code.typecode(type));
        Assert.check(reg >= 0);
        this.type = type;
        this.reg = reg;
    }
    Item load() {
        if (reg <= 3)
            code.emitop0(iload_0 + Code.truncate(typecode) * 4 + reg);
        else
            code.emitop1w(iload + Code.truncate(typecode), reg);
        return stackItem[typecode];
    }
    void store() {
        if (reg <= 3)
            code.emitop0(istore_0 + Code.truncate(typecode) * 4 + reg);
        else
            code.emitop1w(istore + Code.truncate(typecode), reg);
    }
    ...
}
```

---

由于本地变量一般存储到本地变量表中，因而load\(\)与store\(\)方法生成的指令也都与本地变量表相关。其中，reg指明了当前变量存储在本地变量表中的位置，而type指明了本地变量的类型，在构造方法中通过调用Code.typecode\(\)方法初始化Item类中的typecode变量。 

load\(\)方法将本地变量表中reg指定位置的数据压入栈顶，如果指定的索引值reg小于等于3，那么可直接使用本身带有操作数的一些指令来完成，其中code.truncate\(typecode\)\*4用来辅助选择具体的指令；如果指定的索引值reg大于3，则使用指定操作数的指令，code.truncate\(typecode\)同样用来辅助选择具体的指令。 

store\(\)方法的实现与load\(\)方法类似，这里不再介绍。 

LocalItem类中还提供了一个独有的方法incr\(\)，实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Items.LocalItem
void incr(int x) {
     if (typecode == INTcode && x >= -32768 && x <= 32767) {
          code.emitop1w(iinc, reg, x);
     } else {
          load();
          if (x >= 0) {
              makeImmediateItem(syms.intType, x).load();
              code.emitop0(iadd);
          } else {
              makeImmediateItem(syms.intType, -x).load();
              code.emitop0(isub);
          }
          makeStackItem(syms.intType).coerce(typecode);
          store();
     }
}
```

---

incr\(\)方法直接或间接对本地变量表中存储的数值进行增减操作，如果指定的操作数大小在32768~32767范围内，直接使用iinc指令即可，否则需要借助操作数栈来完成。首先将当前LocalItem对象代表的本地变量加载到操作数栈中，然后将另外一个操作数加载到栈中，最后使用iadd或isub指令进行加减操作，完成之后调用store\(\)方法将栈顶的值更新到本地变量表中。举个例子如下： 

【实例16\-3】

---

```java
public int md(){
    int a = 1;
    ++a;
    return a;
}
```

---

在md\(\)方法内声明了一个局部变量a并初始化为常量值1，md\(\)方法最后返回a。这个方法的字节码指令如下： 

---

```java
0: iconst_1
1: istore_1// 调用LocalItem类的store()方法将栈顶的整数1存储到本地变量表1的位置
2: iinc 1, 1// 调用LocalItem的incr()方法为变量a加1
5: iload_1 // 调用LocalItem类的load()方法将变量a加载到栈中
6: ireturn
```

---

Javac根据语法树将方法内的语句翻译为字节码指令，下面将详细介绍翻译的过程。 

“int a=1”语句对应的语法树如图16\-1所示。 

首先调用Gen类的visitVarDef\(\)方法处理“int a=1”语句，相关的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
VarSymbol v = tree.sym;
genExpr(tree.init, v.erasure(types)).load();
items.makeLocalItem(v).store();
```

---

代码中，调用genExpr\(\)方法处理tree.init，最终会返回一个ImmediateItem对象，则表示常量值1，调用这个对象的load\(\)方法会将这个常量加载到操作数栈中并生成对应的指令iconst\_1。调用items.makeLocalItem\(\)方法创建一个表示a变量的LocalItem对象，然后调用此对象的store\(\)方法，这个方法会将操作数栈中的常量值存储到本地变量表的指定位置，这个位置代表的就是变量a，生成相应的指令istore\_1。 

“\+\+a”语句对应的语法树结构如图16\-2所示。 

![image](https://github.com/YangLuchao/img_host/raw/master/20230418/image.5z81tk9kmpk0.webp)

图16\-1　语句的语法树1 

![image](https://github.com/YangLuchao/img_host/raw/master/20230418/image.4skem5d4icq0.webp)

图16\-2　语句的语法树2 

首先会执行visitExec\(\)方法处理JCExpressionStatement树节点，实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
public void visitExec(JCExpressionStatement tree) {
    // Optimize x++ to ++x and x-- to --x.
    JCExpression e = tree.expr;
    switch (e.getTag()) {
        case JCTree.POSTINC:
            ((JCUnary) e).setTag(JCTree.PREINC);
            break;
        case JCTree.POSTDEC:
            ((JCUnary) e).setTag(JCTree.PREDEC);
            break;
    }
    genExpr(tree.expr, tree.expr.type).drop();
}
```

---

visitExec\(\)方法首先将后缀自增与自减的语句更改为前置自增与自减，这样可以简化处理，同时也是等价变换。调用genExpr\(\)方法处理JCUnary\(\+\+a\)，期望类型直接从标注语法树中获取即可，visitExec\(\)方法最终会调用visitUnary\(\)方法处理JCUnary\(\+\+a\)树节点。visitUnary\(\)方法相关的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
OperatorSymbol operator = (OperatorSymbol)tree.operator;
Item od = genExpr(tree.arg, operator.type.getParameterTypes().head);
switch (tree.getTag()) {
case JCTree.PREINC: case JCTree.PREDEC:
        od.duplicate();
        if (od instanceof LocalItem && (operator.opcode == iadd || operator.opcode == isub)) {
            ((LocalItem)od).incr(tree.getTag() == JCTree.PREINC ? 1 : -1);
            result = od;
        }
        break;
}
```

---

代码中，调用genExpr\(\)方法处理a，od为LocalItem\(type=int; reg=1\)，调用od.duplicate\(\)最终会调用Item类中的duplicate\(\)方法，这个方法是个空实现，不做任何操作，最终还会调用od的incr\(\)方法以生成iinc指令。 

对于“return a”语句来说，具体的语法树结构如图16\-3所示。 

![image](https://github.com/YangLuchao/img_host/raw/master/20230418/image.3ru8bsyvdxk0.webp)

图16\-3　语句的语法树3 

首先调用Gen类的visitReturn\(\)方法，相关实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
Item r = genExpr(tree.expr, pt).load();
r.load();
code.emitop0(ireturn + Code.truncate(Code.typecode(pt)));
```

---

代码中，调用genExpr\(\)方法处理JCIdent\(a\)节点，则会返回LocalItem\(type=int; reg=1\)；调用load\(\)方法将局部变量表中指定索引位置1的数加载到操作数栈中，会生成iload\_1指令，LocalItem类的load\(\)方法最终会返回一个StackItem对象，调用此对象的load\(\)方法就是返回自身。之所以再次调用，是因为像LocalItem这样的对象，其load\(\)方法表示的含义并不是加载数据到操作数栈中。visitReturn\(\)方法最后根据pt的不同选择生成具体的ireturn指令，表示返回一个整数类型的值。 

### 16.4.2　ImmediateItem类 

每个ImmediateItem对象代表一个常量，ImmediateItem类的定义如下： 

---

```java
来源：com.sun.tools.javac.jvm.Items.ImmediateItem
class ImmediateItem extends Item {
    Object value;
    ImmediateItem(Type type, Object value) {
        super(Code.typecode(type));
        this.value = value;
    }
    Item load() {
        switch (typecode) {
        case INTcode: case BYTEcode: case SHORTcode: case CHARcode:
            int ival = ((Number)value).intValue();
            if (-1 <= ival && ival <= 5)
                code.emitop0(iconst_0 + ival);
            else if (Byte.MIN_VALUE <= ival && ival <= Byte.MAX_VALUE)
                code.emitop1(bipush, ival);
            else if (Short.MIN_VALUE <= ival && ival <= Short.MAX_VALUE)
                code.emitop2(sipush, ival);
            else
                ldc();
            break;
        case LONGcode:
            long lval = ((Number)value).longValue();
            if (lval == 0 || lval == 1)
                code.emitop0(lconst_0 + (int)lval);
            else
                ldc();
            break;
        case FLOATcode:
            float fval = ((Number)value).floatValue();
            if (isPosZero(fval) || fval == 1.0 || fval == 2.0)
                code.emitop0(fconst_0 + (int)fval);
            else {
                ldc();
            }
            break;
        case DOUBLEcode:
            double dval = ((Number)value).doubleValue();
            if (isPosZero(dval) || dval == 1.0)
                code.emitop0(dconst_0 + (int)dval);
            else
                ldc();
            break;
        case OBJECTcode:
            ldc();
            break;
        }
        return stackItem[typecode];
    }
    /** Return true iff float number is positive 0. */
    private boolean isPosZero(float x) {
        return x == 0.0f && 1.0f / x > 0.0f;
    }
    /** Return true iff double number is positive 0.*/
    private boolean isPosZero(double x) {
        return x == 0.0d && 1.0d / x > 0.0d;
    }
}
```

---

ImmediateItem类中的value保存了常量值，类中只覆写了load\(\)方法，当typecode为int、byte、short与char类型并且操作数又不大时会选择iconst\_X（X为大于等于\-1小于等于5的整数）指令、bipush与sipush指令；当操作数过大时则会调用ldc\(\)方法进行处理。当typecode为long、float或double类型时，操作与前面的类似，这里不再过多介绍。 

ldc\(\)方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Items.ImmediateItem
private void ldc() {
    int idx = pool.put(value);
    if (typecode == LONGcode || typecode == DOUBLEcode) {
        code.emitop2(ldc2w, idx);
    } else if (idx <= 255) {
        code.emitop1(ldc1, idx);
    } else {
        code.emitop2(ldc2, idx);
    }
}
```

---

将大数放入常量池中，然后使用ldc2w、ldc1或者ldc2指令将常量值推送到栈顶。举个例子如下： 

【实例16\-4】

---

```java
public void md(){
    int i = 1;
    int j = 100;
    int k = 100000;
       
    double a = 1;
    double b = 100;
    double c = 100000;
}
```

---

局部变量的初始化表达式都为常量值，因此使用ImmediateItem对象表示，调用这个对象的load\(\)方法加载这些常量值到操作数栈时会选取不同的指令，最终md\(\)方法的字节码指令如下： 

---

```java
0: iconst_1  // 调用ImmediateItem类的load()方法加载整数1
1: istore_1
2: bipush        100 // 调用ImmediateItem类的load()方法加载整数100
4: istore_2
5: ldc           #2 // 调用ImmediateItem类的load()方法加载整数100000
7: istore_3
8: dconst_1  // 调用ImmediateItem类的load()方法加载浮点数1
9: dstore         4
11: ldc2_w       #3// // 调用ImmediateItem类的load()方法加载浮点数100.0d
14: dstore        6
16: ldc2_w       #5// // 调用ImmediateItem类的load()方法加载浮点数100000.0d
19: dstore        8
21: return
```

---

当加载整数1时使用iconst\_1指令，加载整数100时使用sipush指令，加载整数100 000时使用ldc指令。 

当加载双精度浮点类型的1时使用dconst\_1指令，加载双精度浮点类型的100和100 000时使用ldc2\_w指令。 

### 16.4.3　StackItem类 

每个StackItem对象代表一个操作数栈中的数据，对于Javac来说，这个数据就是一个类型。StackItem类的定义如下： 

---

```java
来源：com.sun.tools.javac.jvm.Items.StackItem
class StackItem extends Item {
    StackItem(int typecode) {
        super(typecode);
    }
    Item load() {
        return this;
    }
    void duplicate() {
        code.emitop0(width() == 2 ? dup2 : dup);
    }
    void drop() {
        code.emitop0(width() == 2 ? pop2 : pop);
    }
    void stash(int toscode) {
        code.emitop0((width() == 2 ? dup_x2 : dup_x1) + 3 * (Code.width
(toscode) - 1));
    }
}
```

---

StackItem类的duplicate\(\)方法会生成dup2或dup指令，调用code.emitop0\(\)方法在生成指令的同时还要复制操作数栈顶的内容。 

StackItem类的drop\(\)方法会生成pop2或pop指令，调用emitop0\(\)方法同样会根据生成的指令对栈中的数据进行弹出操作。 

StackItem类的stash\(\)方法会生成dup\_x1、dup2\_x1或dup\_x2、dup2\_x2指令。同样通过调用emitop0\(\)方法完成栈中数据的操作，具体指令的选取是由当前实体所代表的数据类型的宽度与要复制的栈顶数据类型的宽度共同决定的。例如，如果当前实体所代表的数据类型的宽度为1，则只能选取dup\_x1或dup2\_x1指令，如果此时代表栈顶要复制数据的类型toscode的宽度为1，则选取dup\_x1类型，否则选取dup2\_x1类型。 

emitop0\(\)方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Items.StackItem
public void emitop0(int op) {
    emitop(op);
    switch (op) {
    case dup:
       state.push(state.stack[state.stacksize-1]);
       break;
    case dup2:
       if (state.stack[state.stacksize-1] != null) {
          Type value1 = state.pop1();
          Type value2 = state.pop1();
          state.push(value2);
          state.push(value1);
          state.push(value2);
          state.push(value1);
       } else {
          Type value = state.pop2();
          state.push(value);
          state.push(value);
       }
       break;
}
```

---

调用emitop\(\)方法将对应指令的编码保存到code字节数组中，然后操作栈中的内容。对于dup指令来说，复制栈顶内容后压入栈顶，对于dup2指令来说，可能复制的是long或double这样占两个槽位的类型，也可能是复制只占一个槽位的两个类型，因此emitop\(\)方法在实现时分情况进行了处理。 

### 16.4.4　AssignItem类 

每个AssignItem对象代表一个赋值表达式左侧的表达式，AssignItem类的定义如下： 

---

```java
来源：com.sun.tools.javac.jvm.Items.AssignItem
class AssignItem extends Item {
    Item lhs;
    AssignItem(Item lhs) {
       super(lhs.typecode);
       this.lhs = lhs;
    }
    void drop() {
        lhs.store();
    }
    Item load() {
        lhs.stash(typecode);
        lhs.store();
        return stackItem[typecode];
    }
}
```

---

lhs代表赋值表达式左侧的可寻址实体，而覆写的load\(\)与drop\(\)方法是最常用的方法。举个例子如下： 

【实例16\-5】

---

```java
public void md() {
    int i, j;
    j = i = 1;
}
```

---

md\(\)方法的字节码指令如下： 

---

```java
0: iconst_1
1: dup
2: istore_1
3: istore_2
4: return
```

---

赋值表达式“j=i=1”等价于表达式“j=\(i=1\)”，相当于包含了两个赋值表达式，具体的语法树结构如图16\-4所示。 

![image](https://github.com/YangLuchao/img_host/raw/master/20230418/image.40vr4bavie40.webp)

图16\-4　语句的语法树4 

在Gen类的visitExec\(\)方法中处理JCExpressionStatement树节点，这个方法有如下调用语句： 

---

```java
来源：com.sun.tools.javac.jvm.Gen 
genExpr(tree.expr, tree.expr.type).drop();  
```

---

调用genExpr\(\)方法处理JCAssign\(j=i=1\)语法树节点，期望的类型是tree.expr.type，这个方法最终会返回一个AssignItem对象，调用这个对象的drop\(\)方法表示不使用这个赋值表达式的值。需要注意的是，赋值表达式最终也会产生一个值，比如调用一个形式参数类型为int的md\(\)方法，可以使用如下的方法调用表达式： 

---

```java
md(i=1) 
```

---

将赋值表达式的值作为方法的参数传递。 

visitAssign\(\)方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
public void visitAssign(JCAssign tree) {
    Item l = genExpr(tree.lhs, tree.lhs.type);
    genExpr(tree.rhs, tree.lhs.type).load();
    result = items.makeAssignItem(l);
}
```

---

对于JCAssign\(i=1\)树节点来说，i是一个LocalItem对象，调用genExpr\(\)方法处理tree.rhs会返回一个ImmediateItem对象，调用这个对象的load\(\)方法将1加载到栈中（生成iconst\_1指令），该方法最后返回一个AssignItem对象。 

对于JCAssign\(j=i=1\)树节点来说，l是一个LocalItem对象，当调用genExpr\(\)方法处理tree.rhs时同样会调用visitAssign\(\)方法处理，该方法会获取到处理JCAssign\(i=1\)树节点时的AssignItem对象。当调用AssignItem对象的load\(\)方法时，由于lhs为LocalItem对象，因而会调用Item类的stash\(\)方法将栈中的常量1复制一份（生成dup指令），然后调用LocalItem类的store\(\)方法将新复制出来的常量1保存到局部变量表指定的位置（生成istore\_1指令），这个位置就是变量i的位置。 

在visitExec\(\)方法中调用drop\(\)方法最终会调用LocalItem类中的drop\(\)方法，该方法将这个变量存储到本地变量表中（生成istore\_2指令）。 

调用visitIdent\(\)方法处理JCIdent\(j\)或JCIdent\(i\)树节点，该方法相关的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
Symbol sym = tree.sym;
if (sym.kind == VAR && sym.owner.kind == MTH) {
    result = items.makeLocalItem((VarSymbol)sym);
}
```

---

由于tree.sym在标注阶段被标注为VarSymbol对象，因而sym.kind值为VAR，最终会返回一个LocalItem对象，这就是AssignItem对象的lhs变量中保存的实体。 

调用visitLiteral\(\)方法处理JCLiteral\(1\)树节点，该方法相关的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen 
result = items.makeImmediateItem(tree.type, tree.value);  
```

---

返回一个ImmediateItem对象，然后会在visitAssign\(\)方法中调用这个对象的load\(\)方法将常量值加载到栈中。 

### 16.4.5　StaticItem类 

每个StaticItem对象代表一个静态变量或者静态方法，StaticItem类的定义如下： 

---

```java
来源：com.sun.tools.javac.jvm.Items.StaticItem
class StaticItem extends Item {
    Symbol member;
    StaticItem(Symbol member) {
        super(Code.typecode(member.erasure(types)));
        this.member = member;
    }
    Item load() {
        code.emitop2(getstatic, pool.put(member));
        return stackItem[typecode];
    }
    void store() {
        code.emitop2(putstatic, pool.put(member));
    }
    Item invoke() {
        MethodType mtype = (MethodType)member.erasure(types);
        int rescode = Code.typecode(mtype.restype);
        code.emitInvokestatic(pool.put(member), mtype);
        return stackItem[rescode];
    }
}
```

---

类中的member变量保存了具体的变量或方法的符号，如果member保存的是静态变量，则可以调用load\(\)或store\(\)方法来完成加载或存储操作；如果member保存的是静态方法，则可以调用invoke\(\)方法执行静态方法。 

对于load\(\)方法来说，首先调用pool.put\(\)方法将member存储到常量池中并返回常量池索引，然后调用code.emitop2\(\)方法生成getstatic指令，这个指令在运行前不需要栈中的数据，但是运行后将在栈内生成一个typecode类型的数据，load\(\)方法返回的stackItem \[typecode\]，表示这个新生成的栈顶数据。emitop2\(\)方法相关的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Code
public void emitop2(int op, int od) {
    emitop(op);
    emit2(od);
    switch (op) {
    case getstatic:
        state.push(((Symbol)(pool.pool[od])).erasure(types));
        break;
    }
}
```

---

当指令为getstatic时会向栈中压入一个擦除泛型后的类型，表示运行getstatic指令后产生了一个此类型的数据。 

StaticItem类中的store\(\)方法生成putstatic指令，这个指令会消耗栈顶的一个数据，用来设置对象字段的值。运行putstatic指令不会产生新的类型数据，因此不需要后续的操作，store\(\)方法无返回值。 

如果member是静态方法，则可以调用invoke\(\)方法以生成方法调用相关的指令。首先调用pool.put\(\)方法将member存储到常量池中并返回常量池索引，然后调用code.emitInvokestatic\(\)方法生成方法调用指令，这个方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Code
public void emitInvokestatic(int meth, Type mtype) {
    int argsize = width(mtype.getParameterTypes());
    emitop(invokestatic);
    emit2(meth);
    state.pop(argsize);
    state.push(mtype.getReturnType());
}
```

---

调用emitop\(\)与emit2\(\)方法生成invokestatic指令及操作数，然后从栈中弹出方法调用的实际参数，运行invokestatic指令会产生一个新的数据，其类型就是调用方法的返回类型，因此向栈中压入一个方法返回类型，同时在invoke\(\)方法中返回一个stackItem\[rescode\]代表这个新产生的栈顶数据。举个例子如下： 

【实例16\-6】

---

```java
class Test {
    static int a = 1;
    public void md() {
        a = a + 1;
    }
}
```

---

在md\(\)方法中对类变量a执行加1操作，生成的字节码指令如下： 

---

```java
0: getstatic     #2// 调用StaticItem类的load()方法将变量a的值压入到操作数栈顶 
3: iconst_1
4: iadd
5: putstatic     #2  // 调用StaticItem类的store()方法设置变量a的值
8: return
```

---

“a=a\+1”语句的树结构如图16\-5所示。 

![image](https://github.com/YangLuchao/img_host/raw/master/20230418/image.6jtacqu8pq00.webp)

图16\-5　语句的语法树5 

在Gen类的visitExec\(\)方法中处理JCExpressionStatement树节点，这个方法有如下调用语句： 

---

```java
genExpr(tree.expr, tree.expr.type).drop(); 
```

---

调用genExpr\(\)方法处理JCAssign\(a=a\+1\)树节点，其中的lhs是StaticItem对象，调用drop\(\)方法会生成putstatic指令，该指令将设置静态变量a的值为操作数栈顶的值。调用的visitAssign\(\)方法前面介绍过，这里再次给出相关的实现如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
public void visitAssign(JCAssign tree) {
    Item l = genExpr(tree.lhs, tree.lhs.type);
    genExpr(tree.rhs, tree.lhs.type).load();
    result = items.makeAssignItem(l);
}
```

---

调用genExpr\(\)方法处理JCIdent\(a\)树节点并返回StaticItem对象。当调用genExpr\(\)方法处理JCBinary\(a\+1\)树节点时，则会调用visitBinary\(\)方法，这个方法会返回一个StackItem对象，表示栈中a\+1执行后会产生一个int类型的数据。调用StackItem对象的load\(\)方法不做任何操作，因为所代表的数据已经在操作数栈中，最后将l作为参数调用items.makeAssignItem\(\)方法创建一个AssignItem对象并赋值给result。 

调用visitIdent\(\)方法处理JCIdent\(a\)树节点，相关的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
Symbol sym = tree.sym;
if ((sym.flags() & STATIC) != 0) {
    result = items.makeStaticItem(sym);
}
```

---

对静态变量和静态方法创建一个StaticItem对象并返回。 

调用visitBinary\(\)方法处理JCBnary\(a\+1\)树节点，相关的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
OperatorSymbol operator = (OperatorSymbol)tree.operator;
Item od = genExpr(tree.lhs, operator.type.getParameterTypes().head);
od.load();
result = completeBinop(_, tree.rhs, operator);
```

---

调用genExpr\(\)方法处理JCIdent\(a\)树节点并返回StaticItem对象，调用这个对象的load\(\)方法生成getstatic指令，则表示获取变量a的值并压入到操作数栈顶。调用completeBinop\(\)方法处理JCLiteral\(1\)树节点，实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
Item completeBinop(_, JCTree rhs, OperatorSymbol operator) {
    MethodType optype = (MethodType)operator.type;
    int opcode = operator.opcode;
    Type rtype = operator.erasure(types).getParameterTypes().tail.head;
    genExpr(rhs, rtype).load();
    code.emitop0(opcode);
    return items.makeStackItem(optype.restype);
}
```

---

completeBinop\(\)方法调用genExpr\(\)方法处理JCLiteral\(1\)并返回ImmediateItem对象，调用这个对象的load\(\)方法生成iconst\_1指令，将常量值1压入操作数栈顶；调用code.emitop0\(\)方法生成iadd指令，最后创建一个StatckItem对象，类型为operator方法的返回类型。 

调用visitLiteral\(\)方法处理JCLiteral\(1\)树节点，相关的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen 
result = items.makeImmediateItem(tree.type, tree.value);  
```

---

创建一个ImmediateItem对象并赋值给result。 

### 16.4.6　MemberItem类 

每个MemberItem对象代表一个实例变量或者实例方法，MemberItem类的定义如下： 

---

```java
来源：com.sun.tools.javac.jvm.Items.MemberItem
class MemberItem extends Item {
    Symbol member;
    boolean nonvirtual;
    MemberItem(Symbol member, boolean nonvirtual) {
        super(Code.typecode(member.erasure(types)));
        this.member = member;
        this.nonvirtual = nonvirtual;
    }
    Item load() {
        code.emitop2(getfield, pool.put(member));
        return stackItem[typecode];
    }
    void store() {
        code.emitop2(putfield, pool.put(member));
    }
    Item invoke() {
        MethodType mtype = (MethodType)member.externalType(types);
        int rescode = Code.typecode(mtype.restype);
        if ((member.owner.flags() & Flags.INTERFACE) != 0) {
            code.emitInvokeinterface(pool.put(member), mtype);
        } else if (nonvirtual) {
            code.emitInvokespecial(pool.put(member), mtype);
        } else {
            code.emitInvokevirtual(pool.put(member), mtype);
        }
        return stackItem[rescode];
    }
}
```

---

load\(\)与store\(\)方法的实现与StaticItem类中的load\(\)与store\(\)方法的实现类似，invoke\(\)方法的实现相对复杂一些，如果当前对象表示的是接口中定义的方法，则生成invokeinterface指令；如果nonvirtual为true，则生成invokespecial指令；其他情况下生成invokevirtual指令。在以下情况下，nonvirtual的值为true，则表示使用invokespecial指令调用当前方法： 

* 调用构造方法，也就是调用名称为\<init\>的方法； 
* 由private修饰的私有方法； 
* 通过super关键字调用父类方法。 

通过MemberItem类可以辅助生成使用实例成员的表达式的字节码指令，举个例子如下： 

【实例16\-7】

---

```java
class Test{
    int a = 1;
    public void md(){
      a = a+1;
    }
}
```

---

在md\(\)方法中对实例变量a执行加1操作，生成的字节码指令如下： 

---

```java
0: aload_0
1: aload_0
2: getfield      #2     // Field a:I
5: iconst_1
6: iadd
7: putfield      #2     // Field a:I
10: return
```

---

“a=a\+1”语句的语法树结构如图16\-6所示。 

与实例16\-6操作静态变量a不同，操作实例变量a需要指定对象，也就是要确定操作的是哪个对象的实例变量a。 

![image](https://github.com/YangLuchao/img_host/raw/master/20230418/image.4tnleo4g20a0.webp)

图16\-6　语句的语法树6 

在Gen类的visitExec\(\)方法中处理JCExpressionStatement的树节点，这个方法有如下调用语句： 

---

```java
来源：com.sun.tools.javac.jvm.Gen 
genExpr(tree.expr, tree.expr.type).drop();  
```

---

调用genExpr\(\)方法处理JCAssign\(a=a\+1\)树节点，其中的lhs是MemberItem对象，调用drop\(\)方法会生成putfield指令，则表示设置静态变量a为操作数栈顶的值。 

调用visitAssign\(\)方法处理JCAssign\(a=a\+1\)树节点，相关的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
public void visitAssign(JCAssign tree) {
    Item l = genExpr(tree.lhs, tree.lhs.type);
    genExpr(tree.rhs, tree.lhs.type).load();
    result = items.makeAssignItem(l);
}
```

---

当调用genExpr\(\)方法处理JCIdent\(a\)树节点时则返回MemberItem对象；当调用genExpr\(\)方法处理JCBinary\(a\+1\)树节点时，会调用visitBinary\(\)方法，该方法返回一个StackItem对象，表示栈中a\+1执行后会产生一个int类型的数值；调用load\(\)方法不做任何操作，因为值已经在操作数栈中了，最后将l作为参数调用items.makeAssignItem\(\)方法创建一个AssignItem对象并赋值给result。 

调用visitIdent\(\)方法处理JCIdent\(a\)树节点，相关的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
items.makeThisItem().load();
sym = binaryQualifier(sym, env.enclClass.type);
result = items.makeMemberItem(sym, (sym.flags() & PRIVATE) != 0);
```

---

创建一个SelfItem对象并调用load\(\)方法，该方法会生成aload\_0指令，表示将当前的实例压入栈内，然后创建一个MemberItem对象并赋值给result。 

调用visitBinary\(\)方法处理JCBnary\(a\+1\)树节点，相关的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
OperatorSymbol operator = (OperatorSymbol)tree.operator;
Item od = genExpr(tree.lhs, operator.type.getParameterTypes().head);
od.load();
result = completeBinop(_, tree.rhs, operator);
```

---

当调用genExpr\(\)方法处理JCIdent\(a\)树节点时则会调用visitIdent\(\)方法处理，visitIdent\(\)方法会生成一个aload\_0指令并且返回一个MemberItem对象。调用load\(\)方法生成getfield指令，表示获取实例变量a的值并压入到操作数栈顶。completeBinaop\(\)方法对JCLiteral\(1\)的处理与实例16\-6的处理逻辑类似，生成iconst\_1与iadd指令并返回一个StackItem对象，代表栈中产生了一个int类型的数据。 

### 16.4.7　SelfItem类 

SelfItem代表Java中的关键字this或super，SelfItem类的定义如下： 

---

```java
来源：com.sun.tools.javac.jvm.Items.SelfItem
class SelfItem extends Item {
    boolean isSuper;
    SelfItem(boolean isSuper) {
        super(OBJECTcode);
        this.isSuper = isSuper;
    }
    Item load() {
        code.emitop0(aload_0);
        return stackItem[typecode];
    }
}
```

---

当isSuper值为true时则表示关键字super。 

如果一个类没有明确声明构造方法，则Javac会添加一个默认构造方法，如为Test类添加构造方法，举例如下： 

【实例16\-8】

---

```java
public <init>() {
    super();
}
```

---

为默认的构造方法生成的字节码指令如下： 

---

```java
0: aload_0
1: invokespecial #1  // Method java/lang/Object."<init>":()V
4: return
```

---

“super\(\)”语句的语法树结构如图16\-7所示。 

![image](https://github.com/YangLuchao/img_host/raw/master/20230418/image.6mb6o2xr5e80.webp)

图16\-7　语句的语法树7 

在Gen类的visitExec\(\)方法中处理JCExpressionStatement\(super\(\)\)树节点，这个方法有如下调用语句： 

---

```java
genExpr(tree.expr, tree.expr.type).drop(); 
```

---

调用genExpr\(\)方法会返回代表void的Item匿名类对象，调用这个对象的drop\(\)方法不做任何操作。 

调用visitApply\(\)方法处理JCMethodInvocation\(super\(\)\)树节点，相关的实现代码如下： 

---

```java
public void visitApply(JCMethodInvocation tree) {
    Item m = genExpr(tree.meth, methodType);
    ...
    result = m.invoke();
}
```

---

调用genExpr\(\)方法处理JCIdent\(super\)树节点，最终返回MemberItem\(member.name=Object，nonvirtual=true\)对象，调用这个对象的invoke\(\)方法生成方法调用的相关指令。 

调用visitIdent\(\)方法处理JCIdent\(super\)树节点，相关的实现代码如下： 

---

```java
Symbol sym = tree.sym;
if (tree.name == names._this || tree.name == names._super) {
    Item res = tree.name == names._this
        ? items.makeThisItem()
        : items.makeSuperItem();
    if (sym.kind == MTH) {
        res.load();
        res = items.makeMemberItem(sym, true);
    }
    result = res;
}
```

---

当关键字为this时，则调用items.makeThisItem\(\)方法创建SelfItem对象；当关键字为super时，则调用items.makeSuperItem\(\)方法创建SelfItem对象。如果sym表示方法，则调用SelfItem对象的load\(\)方法，这个方法会生成aload\_0指令。visitIdent\(\)方法最后会返回MemberItem对象。 

### 16.4.8　IndexedItem类 

每个IndexedItem对象代表一个索引表达式，IndexedItem类的定义如下： 

---

```java
来源：com.sun.tools.javac.jvm.Items.IndexedItem
class IndexedItem extends Item {
    IndexedItem(Type type) {
        super(Code.typecode(type));
    }
    Item load() {
        code.emitop0(iaload + typecode);
        return stackItem[typecode];
    }
    void store() {
        code.emitop0(iastore + typecode);
    }
    void duplicate() {
        code.emitop0(dup2);
    }
    void drop() {
        code.emitop0(pop2);
    }
    void stash(int toscode) {
        code.emitop0(dup_x2 + 3 * (Code.width(toscode) - 1));
    }
}
```

---

IndexedItem类覆写了大部分方法，不过方法的实现都比较简单，这里不再介绍。举个例子如下： 

【实例16\-9】

---

```java
public int md(int[] arr, int a) {
    return arr[a]++; 
}
```

---

md\(\)方法的字节码指令如下： 

---

```java
0: aload_1
1: iload_2
2: dup2// 调用IndexedItem类中的duplicate()方法复制栈顶1个或2个值并插入栈顶
3: iaload// 调用IndexedItem类中的load()方法从数组中加载一个int类型数据到操作数栈 
4: dup_x2// 调用IndexedItem类中的stash()方法复制操作数栈栈顶的值，并插入栈顶以下2个或3个值之后
5: iconst_1
6: iadd   
7: iastore// 调用IndexedItem类中的store()方法将操作数栈顶的数据存入数组中
8: ireturn
```

---

md\(\)方法中的“return arr\[a\]\+\+”语句对应的语法树结构如图16\-8所示。 

![image](https://github.com/YangLuchao/img_host/raw/master/20230418/image.re19hmwc1f4.webp)

图16\-8　语句的语法树8 

调用visitReturn\(\)方法处理JCReturn\(return arr\[a\]\+\+\)树节点，相关的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
if (tree.expr != null) {
    Item r = genExpr(tree.expr, pt).load();
    r.load();
    code.emitop0(ireturn + Code.truncate(Code.typecode(pt)));
} 
```

---

调用genExpr\(\)方法处理JCUnary\(arr\[a\]\+\+\)树节点，得到StackItem对象，这是表示arr\[a\]的值已经在栈内，因此调用这个对象的load\(\)方法无操作，最后会生成ireturn指令。 

调用visitUnary\(\)方法处理JCUnary\(arr\[a\]\+\+\)树节点，相关的实现代码如下： 

---

```java
OperatorSymbol operator = (OperatorSymbol)tree.operator;
Item od = genExpr(tree.arg, operator.type.getParameterTypes().head);
switch (tree.getTag()) {
...
case JCTree.POSTINC: case JCTree.POSTDEC:
    od.duplicate();
    Item res = od.load();
    od.stash(od.typecode);
    code.emitop0(one(od.typecode));
    code.emitop0(operator.opcode);
    od.store();
    result = res;
    break;
...
    }
```

---

调用genExpr\(\)方法处理JCArrayAccess\(arr\[a\]\)树节点，得到IndexedItem\(int\)对象，调用IndexedItem\(int\)对象的duplicate\(\)方法将生成dup2指令，调用IndexedItem\(int\)对象的load\(\)方法将生成iaload指令，调用IndexedItem\(int\)对象的stash\(\)方法将生成dup\_x2指令；然后调用code.emitop0\(\)方法将生成iconst\_1与iadd指令，调用od.store\(\)方法将生成iastore指令。 

调用visitIndexed\(\)方法处理JCArrayAccess\(arr\[a\]\)树节点，相关的实现代码如下： 

---

```java
public void visitIndexed(JCArrayAccess tree) {
    genExpr(tree.indexed, tree.indexed.type).load();
    genExpr(tree.index, syms.intType).load();
    result = items.makeIndexedItem(tree.type);
}
```

---

调用genExpr\(\)方法处理JCIdent\(arr\)树节点，得到LocalItem\(type=int\[\]; reg=1\)对象，调用这个对象的load\(\)方法将生成aload\_1指令并将int\[\]类型压入操作数栈顶；调用genExpr\(\)方法处理JCIdent\(a\)树节点，将得到LocalItem\(type=int; reg=2\)对象，调用这个对象的load\(\)方法将生成aload\_2指令并将int类型压入操作数栈顶。visitIndexed\(\)方法最后会创建一个IndexedItem对象并返回。 

调用visitIdent\(\)方法处理JCIdent\(arr\)或JCIdent\(a\)树节点，方法中相关的实现代码如下： 

---

```java
Symbol sym = tree.sym;
if (sym.kind == VAR && sym.owner.kind == MTH) {
    result = items.makeLocalItem((VarSymbol)sym);
}
```

---

由于arr与a都为变量，因而visitIdent\(\)方法将会创建一个LocalItem对象并赋值给result，result将作为最终的结果返回给调用者。 
