# switch语句

switch语句与if语句都是重要的分支选择结构，不过switch语句的字节码指令在生成时相对复杂，在Gen类中的visitSwitch\(\)方法中生成switch语句的字节码指令。switch语句在生成指令的过程中会涉及两个非常重要的Java虚拟机指令lookupswitch与tableswitch，下面分别介绍。 

**1．lookupswitch指令**

lookupswitch指令根据键值在跳转表中寻找配对的分支并跳转，具体的格式如图17\-1所示。 

这是一条变长指令并且要求所有的操作数都以4字节对齐，因此紧跟在lookupswitch指令之后可能会有0～3个字节作为空白填充，而后面的default、npairs等都用4字节来表示，从当前方法开始（第一条字节码指令）计算的地址，即紧随空白填充的是一系列32位有符号整数值，包括默认跳转地址default、匹配坐标的数量npairs及npairs组匹配坐标。其中，npairs的值应当大于或等于0，每一组匹配坐标都包含了一个整数值match及一个有符号32位偏移量offset。上述所有的32位有符号数值都是通过以下方式计算得到： 

---

```
(byte1<<24)|(byte2<<24)|(byte3<<24)|byte4 
```

---

**2．tableswitch指令**

tableswitch指令根据键值在跳转表中寻找配对的分支并跳转，具体的格式如图17\-2所示。 

![image](https://cdn.staticaly.com/gh/YangLuchao/img_host@master/20230418/image.7fw2n71t0s00.webp)

图17\-1　lookupswitch指令结构 

![image](https://cdn.staticaly.com/gh/YangLuchao/img_host@master/20230418/image.2hdpze7yv040.webp)

图17\-2　tableswitch指令结构 

tableswitch指令是一条变长指令并且要求所有的操作数都以4字节对齐，因此紧跟在lookupswitch指令之后可能会有0～3个字节作为空白填充，而后面的default、lowbyte、highbyte等用4字节来表示，从当前方法开始（第一条字节码指令）计算的地址，即紧随空白填充的是一系列32位有符号整数值，包括默认跳转地址default、高位值high及低位值low，在此之后是high\-low\+1个有符号32位偏移offset。上述所有的32位有符号数值都是通过以下方式计算得到： 

---

```java
(byte1<<24)|(byte2<<24)|(byte3<<24)|byte4 
```

---

介绍完lookupswitch指令与tableswitch指令后，接着看字节码指令的生成。Gen类的visitSwitch\(\)方法生成switch语句的字节码指令，该方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
public void visitSwitch(JCSwitch tree) {
    int limit = code.nextreg;
    Assert.check(tree.selector.type.tag != CLASS);
    Item sel = genExpr(tree.selector, syms.intType);
    List<JCCase> cases = tree.cases;
    if (cases.isEmpty()) {
        sel.load().drop(); 
    } else {
        sel.load();
        Env<GenContext> switchEnv = env.dup(tree, new GenContext());
        switchEnv.info.isSwitch = true;
        ...
    }
    code.endScopes(limit); 
}
```

---

在解语法糖阶段已经将tree.selector表达式的类型都转换为了int类型，因此在调用genExpr\(\)方法处理tree.selector时，给出了期望的类型为syms.intType。 

当cases分支为空时处理非常简单，可直接调用sel.load\(\)方法加载Item对象sel，因为没有分支使用，所以调用drop\(\)方法抛弃；当switch语句中有分支时，首先要进行指令选择，也就是要选择lookupswitch指令还是tableswitch指令，visitSwitch\(\)方法中选择指令的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
int lo = Integer.MAX_VALUE;  // 保存label的最小值
int hi = Integer.MIN_VALUE;  // 保存label的最大值
int nlabels = 0;               // 保存在label的数量
int[] labels = new int[cases.length()];  
int defaultIndex = -1;    
// 更新lo、hi、nlabels与defaultIndex变量的值
List<JCCase> l = cases;
for (int i = 0; i < labels.length; i++) {
    if (l.head.pat != null) {
        int val = ((Number)l.head.pat.type.constValue()).intValue();
        labels[i] = val;
        if (val < lo) 
            lo = val;
        if (hi < val) 
            hi = val;
        nlabels++;
    } else {
        Assert.check(defaultIndex == -1);
        defaultIndex = i;
    }
    l = l.tail;
}
// 通过粗略计算使用lookupswitch指令与tableswitch指令的时间与空间消耗来选择指令
long table_space_cost = 4 + ((long) hi - lo + 1);  // words
long table_time_cost = 3;  // comparisons
long lookup_space_cost = 3 + 2 * (long) nlabels;
long lookup_time_cost = nlabels;
int opcode = nlabels > 0 && 
            table_space_cost + 3 * table_time_cost <=lookup_space_cost + 3
* lookup_time_cost
              ? tableswitch : lookupswitch;
```

---

代码中首先声明了局部变量lo与hi，然后计算出所有label中的最大值并保存到hi中，计算出所有label中的最小值并保存到lo中，计算过程非常简单，循环比较然后更新lo与hi的值即可。 

得到lo与hi的值之后会利用这两个值粗略计算一下使用lookupswitch指令与tableswitch指令的时间与空间消耗，在hi和lo的差值不大且label数偏多的情况下，会选择tableswitch指令；当差值很大而label数不多的情况下，会选择lookupswitch指令。 

确定了选取的指令并且知道case分支数量后，其实指令的基本布局已经确定了，也就是知道这条指令各部分所占的字节数以及由几部分组成，只是还没有生成各case分支所含语句中的指令，因此不知道跳转地址，暂时初始化为默认值\-1，随后进行地址回填。visitSwitch\(\)方法中的相关实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
int startpc = code.curPc();    
code.emitop0(opcode);
code.align(4);
int tableBase = code.curPc();  // 保存跳转表开始的位置
// 在生成lookupswitch指令时，保存对应分支到跳转的目标地址的偏移量
int[] offsets = null;        
code.emit4(-1);                // 为默认的跳转地址预留空间
if (opcode == tableswitch) { // 使用tableswitch指令
    code.emit4(lo);           
    code.emit4(hi);           
    for (long i = lo; i <= hi; i++) { // 为跳转表预留空间
        code.emit4(-1);
    }
} else { // 使用lookupswitch指令
    code.emit4(nlabels);    
    for (int i = 0; i < nlabels; i++) { // 为跳转表预留空间
        code.emit4(-1);
        code.emit4(-1); 
    }
    offsets = new int[labels.length];
}
```

---

严格按照指令的格式进行数据填充即可，不知道跳转地址时初始化为默认值\-1。对于tableswitch指令来说，为lo到hi之间的所有整数都执行了code.emit4\(\)方法，也就是这之间的任何整数都有一个跳转地址，举个例子如下： 

【实例17\-3】

---

```java
public void test(int num) {
    switch (num) {
    case 0:
    case 2:
    case 3:
        num = -1;
    }
}
```

---

lo为0，hi为3，由于分支中的整数不连续，因此添加了一个label为2的case分支，最终switch语句生成的字节码指令如下： 

---

```java
0: iconst_0
1: istore_1
2: iload_1
3: tableswitch { // 0 to 3
              0: 32
              1: 34
              2: 32
              3: 32
              default: 34
}
32: iconst_m1
33: istore_1
34: return 
```

---

新增label为2的case分支的跳转地址与默认分支的跳转地址一样，符合switch语句的执行语义。 

对于lookupswitch指令来说，首先输入分支数量nlabels，接下来就是预留nlabels组匹配坐标。最后还初始化了一个offsets数组，这个数组会保存对应分支到跳转的目标地址的偏移量，以便后续进行地址回填。 

在visitSwitch\(\)方法中关于地址回填的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
code.markDead();
l = cases;
// 循环各个case分支并生成字节码指令，同时回填部分跳转地址
for (int i = 0; i < labels.length; i++) {
    JCCase c = l.head;
    l = l.tail;
    int pc = code.entryPoint(_);
    // Insert offset directly into code or else into the offsets table.
    if (i != defaultIndex) {
        if (opcode == tableswitch) {
            code.put4(tableBase + 4 * (labels[i] - lo + 3),pc - startpc);
        } else { // lookupswitch
            offsets[i] = pc - startpc;
        }
    } else {
        code.put4(tableBase, pc - startpc);
    }
    // 生成case分支所含的语句的字节码指定
    genStats(c.stats, switchEnv, _);
}
// 处理所有的break语句
code.resolve(switchEnv.info.exit);
// 如果还没有设置默认分支的偏移地址时，设置默认分支的偏移地址
if (code.get4(tableBase) == -1) {
    code.put4(tableBase, code.entryPoint(_) - startpc);
}
// 继续进行地址回填
if (opcode == tableswitch) { // 对tableswitch指令进行地址回填
    // Let any unfilled slots point to the default case.
    int defaultOffset = code.get4(tableBase);
    for (long i = lo; i <= hi; i++) {
        int t = (int)(tableBase + 4 * (i - lo + 3));
        if (code.get4(t) == -1)
            code.put4(t, defaultOffset);
    }
} else { // 对lookupswitch指令进行地址回填
    // Sort non-default offsets and copy into lookup table.
    if (defaultIndex >= 0)
        for (int i = defaultIndex; i < labels.length - 1; i++) {
            labels[i] = labels[i+1];
            offsets[i] = offsets[i+1];
        }
    if (nlabels > 0)
        qsort2(labels, offsets, 0, nlabels - 1);
    for (int i = 0; i < nlabels; i++) {
        int caseidx = tableBase + 8 * (i + 1);
        code.put4(caseidx, labels[i]);
        code.put4(caseidx + 4, offsets[i]);
    }
}
```

---

循环case分支，然后调用genStats\(\)方法生成分支中语句的指令，在处理每一个分支之前回填地址。 

对于tableswitch指令来说，对没有填充的虚拟case分支设置跳转地址，这个地址就是默认分支的跳转地址。 

对于lookupswitch指令来说，在循环生成各个分支所含语句的字节码指令时，将地址偏移量暂时保存到offsets数组中，随后根据offsets数组中保存的对应关系进行地址回填。loopupswitch中会对所有case分支生成的匹配坐标按照分支中的数值进行排序，以方便使用二分查找来加快查找对应case分支的效率。qsort2\(\)方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
static void qsort2(int[] keys, int[] values, int lo, int hi) {
     int i = lo;
     int j = hi;
     int pivot = keys[(i+j)/2];
     do {
         while (keys[i] < pivot) 
             i++;
         while (pivot < keys[j]) 
             j--;
         if (i <= j) {
             int temp1 = keys[i];
             keys[i] = keys[j];
             keys[j] = temp1;
             int temp2 = values[i];
             values[i] = values[j];
             values[j] = temp2;
             i++;
             j--;
         }
     } while (i <= j);
     if (lo < j) 
         qsort2(keys, values, lo, j);
     if (i < hi) 
         qsort2(keys, values, i, hi);
}
```

---

可以看出，以上代码是典型的二分查找逻辑，这里不再详细介绍。 
