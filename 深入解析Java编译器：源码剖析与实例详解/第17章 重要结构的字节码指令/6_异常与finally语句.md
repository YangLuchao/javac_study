# 异常与finally语句

### 17.6.1　异常的抛出

在Java源代码中可以使用throw关键字抛出异常，而在生成字节码指令时使用athrow指令抛出异常，举个例子如下： 

【实例17\-4】

---

```java
public void test(Exception e) throws Exception{
    throw e;
}
```

---

方法test\(\)生成的字节码指令如下： 

---

```java
0: aload_1 
1: athrow  
```

---

在visitThrow\(\)方法中生成抛出异常的字节码指令，方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
public void visitThrow(JCThrow tree) {
    genExpr(tree.expr, tree.expr.type).load();
    code.emitop0(athrow);
}
```

---

调用genExpr\(\)方法获取Item对象，对于以上实例来说，获取到的是表示本地变量e的LocalItem对象，这个对象的reg值为1，表示变量存储到了本地变量表索引为1的位置，调用这个对象的load\(\)方法生成aload\_1指令并将异常类型压入操作数栈顶，这样就可以调用code.emitop0\(\)方法生成athrow指令并抛出栈顶保存的异常类型了。 

### 17.6.2　异常的捕获与finally语句 

通过try语句来捕获抛出的异常，因此在生成try语句的字节码指令时还会生成异常表（ExceptionTable），通过异常表来处理异常，举个例子如下： 

【实例17\-5】

---

```java
package chapter17;
class FirstExc extends Exception { }
class SecondExc extends Exception { }
public class Test {
    void tryItOut() throws FirstExc, SecondExc { }
    void handleFirstExc(Object o) { }
    void handleSecondExc(Object o) { }
    void wrapItUp() { }
    public void catchExc() {
        try {
            tryItOut();
        } catch (FirstExc e) {
            handleFirstExc(e);
        } catch (SecondExc e) {
            handleSecondExc(e);
        } finally {
            wrapItUp();
        }
    }
}
```

---

catchExc\(\)方法生成的字节码指令及异常表的详细信息如下： 

---

```java
0: aload_0
1: invokevirtual #2         // Method tryItOut:()V
4: aload_0
5: invokevirtual #3         // Method wrapItUp:()V
8: goto          44
11: astore_1
12: aload_0
13: aload_1
14: invokevirtual #5         // Method handleFirstExc:(Ljava/lang/Object;)V
17: aload_0
18: invokevirtual #3         // Method wrapItUp:()V
21: goto          44
24: astore_1
25: aload_0
26: aload_1
27: invokevirtual #7 // Method handleSecondExc:(Ljava/lang/Object;)V
30: aload_0
31: invokevirtual #3          // Method wrapItUp:()V
34: goto          44
37: astore_2
38: aload_0
39: invokevirtual #3           // Method wrapItUp:()V
42: aload_2
43: athrow
44: return
Exception table:
from    to  target type
    0     4    11   Class chapter17/FirstExc
    0     4    24   Class chapter17/SecondExc
    0     4    37   any
   11    17    37   any
   24    30    37   any
   37    38    37   any
```

---

需要说明的是，在早期的JDK版本中，Javac通过jsr与ret指令实现finally语句，而在当前JDK 1.7版本的Javac中，通过在每个分支之后添加冗余代码的形式来实现finally语句，以上指令编号在4~11、17~24及30~37之间（包括起始编号但不包括结束编号）的所有指令都是冗余的字节码指令。 

visitTry\(\)方法为try语句生成字节码指令，这个方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
public void visitTry(final JCTry tree) {
    final Env<GenContext> tryEnv = env.dup(tree, new GenContext());
    final Env<GenContext> oldEnv = env;
    tryEnv.info.finalize = new GenFinalizer() {
        void gen() {
            Assert.check(tryEnv.info.gaps.length() % 2 == 0);
            tryEnv.info.gaps.append(code.curPc());
            genLast();
        }
        void genLast() {
            if (tree.finalizer != null)
                genStat(tree.finalizer, oldEnv, _);
        }
        boolean hasFinalizer() {
            return tree.finalizer != null;
        }
    };
    tryEnv.info.gaps = new ListBuffer<Integer>();
    genTry(tree.body, tree.catchers, tryEnv);
}
```

---

首先调用env.dup\(\)方法来获取Env对象tryEnv，Env对象中的info变量保存的是GenContext对象，这个对象可以辅助生成try语句的字节码指令。GenContext类中定义了两个重要的变量： 

---

```java
GenFinalizer finalize = null; 
ListBuffer<Integer> gaps = null;  
```

---

其中，gaps中保存了finally语句生成的冗余指令的范围。对于实例17\-5来说，gaps列表中按顺序保存的值为4、11、17、24、30与37，也就是指令编号在4~11、17~24、30~37之间（包括起始编号但不包括结束编号）的所有指令都是冗余的字节码指令。 

finalize是GenFinalizer类型的变量，GenFinalizer类的定义如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen.GenFinalizer
abstract class GenFinalizer {
    boolean hasFinalizer() { 
        return true; 
    }
    abstract void gen();
    abstract void genLast();
}
```

---

GenFinalizer类中定义了3个方法，在visitTry\(\)方法中以匿名类的形式覆写了这3个方法。调用hasFinalizer\(\)方法判断try语句是否含有finally语句，如果含有finally语句，hasFinalizer\(\)方法将返回true；调用gen\(\)方法可以记录生成finally语句对应的字节码指令的相关信息。对于实例17\-5来说，当需要生成finally语句对应的冗余的字节码指令时，调用gen\(\)方法会获取当前的变量cp的值并追加到gaps列表中，这个值就是4、17或30；调用genLast\(\)方法生成冗余的字节码指令。 

在visitTry\(\)方法中，初始化了tryEnv.info.finalizer后就可以调用genTry\(\)方法生成try语句的字节码指令了。genTry\(\)方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
int startpc = code.curPc();
genStat(body, env, _);
int endpc = code.curPc();
```

---

startpc与endpc记录了try语句body体生成的字节码指令的范围，对于实例17\-5来说，这两个值分别为0和4。 

接着在genTry\(\)方法中为try语句body体生成的字节码指令生成冗余的字节码指令，代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
List<Integer> gaps = env.info.gaps.toList();
genFinalizer(env);
Chain exitChain = code.branch(goto_);
endFinalizerGap(env);
```

---

调用env.info.gaps的toList\(\)方法初始化gaps，由于toList\(\)方法会重新创建一个列表，因此如果往gaps中追加值时不会影响env.inof.gaps列表中的值。在实例17\-5中，gaps与env.info.gaps值都为空。 

调用genFinalizer\(\)方法记录冗余指令的起始位置，调用endFinalizerGap\(\)方法生成冗余指令并记录冗余指令的结束位置，genFinalizer\(\)方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
void genFinalizer(Env<GenContext> env) {
    if (code.isAlive() && env.info.finalize != null)
        env.info.finalize.gen();
}
```

---

调用env.info.finalize的gen\(\)方法其实就是调用在visitTry\(\)方法中创建的GenFinalizer匿名类中覆写的方法，覆写的gen\(\)方法可以记录冗余指令的起始位置及生成冗余指令。需要注意的是，当code.isAlive\(\)方法返回true时才会做这个操作，也就是代码可达，如果try语句的body体中最后是return语句，则调用code.isAlive\(\)方法将返回false，需要执行其他的逻辑生成冗余指令。 

endFinalizerGap\(\)方法的实现如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
void endFinalizerGap(Env<GenContext> env) {
    if (env.info.gaps != null && env.info.gaps.length() % 2 == 1)
        env.info.gaps.append(code.curPc());
}
```

---

当env.info.gaps列表不为空并且列表中元素的数量为奇数时，才会向列表末尾追加冗余指令的结束位置，判断列表中元素的数量为奇数主要是为了保证记录起始位置，这样才会记录结束位置。例如上面的代码中，code.isAlive\(\)为false时不会记录起始位置，那么也就没有必要记录结束位置了。 

现在生成了try语句body体的字节码指令并且也记录了冗余指令的起始与结束位置，下面生成各个catch语句的字节码指令，在genTry\(\)方法中的相关实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
if (startpc != endpc) 
  for (List<JCCatch> l = catchers; l.nonEmpty(); l = l.tail) {
      code.entryPoint(_, l.head.param.sym.type);
      genCatch(l.head, env, startpc, endpc, gaps);
      genFinalizer(env);
      if (hasFinalizer || l.tail.nonEmpty()) {
          exitChain = Code.mergeChains(exitChain,code.branch(goto_));
      }
      endFinalizerGap(env);
  }
```

---

genTry\(\)方法首先判断startpc不等于endpc，这样可以保证try语句body体有字节码指令生成，因为有执行的字节码指令才可能抛出异常进入catch语句。 

循环遍历catch语句，调用code.entryPoint\(\)方法向操作数栈压入抛出的异常类型，这也是模拟Java虚拟机运行时的情况。当try语句的body体中抛出异常时，Java虚拟机会将对应的异常压入栈顶，随后调用genCatch\(\)方法生成catch语句的body体的字节码指令。 

在调用genFinalizer\(\)方法之后endFinalizerGap\(\)方法之前，如果有finally语句或有后续catch语句的话，那么生成的字节码指令要无条件跳转到目标位置，也就是当前try语句之后的第一条指令位置，这个地址要回填，因此使用exitChain链接起来。 

genCatch\(\)方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
void genCatch(JCCatch tree,Env<GenContext> env,int startpc, int endpc,
List<Integer> gaps) {
    if (startpc != endpc) {
        List<JCExpression> subClauses = TreeInfo.isMultiCatch(tree) ?
                ((JCTypeUnion)tree.param.vartype).alternatives :
                List.of(tree.param.vartype);
        ...
        // 如果try语句的body体有字节码指令生成，则向异常表插入异常记录
        if (startpc < endpc) {
            for (JCExpression subCatch : subClauses) {
                int catchType = makeRef(_, subCatch.type);
                registerCatch(_,startpc, endpc, code.curPc(),catchType);
            }
        }
        VarSymbol exparam = tree.param.sym;
        int limit = code.nextreg;
        int exlocal = code.newLocal(exparam);
        // 如果try语句的body体中抛出异常，则异常会被压入栈顶，将异常存储到本地变量表中
        items.makeLocalItem(exparam).store();
        genStat(tree.body, env, _);
        code.endScopes(limit); 
    }
}
```

---

genCatch\(\)方法还是首先判断startpc不等于endpc，这样可以保证try语句body体有字节码指令生成，然后调用registerCatch\(\)方法向异常表中添加异常处理记录，如果catch语句中声明了N个异常捕获类型，则循环向异常表中添加N条异常处理记录。 

在genCatch\(\)方法中，生成了所有的异常处理记录后，接着为catch语句生成字节码指令，然后生成冗余代码及记录冗余代码的开始与结束位置。 

register\(\)方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
void registerCatch(_,int startpc, int endpc,int handler_pc, int catch_type) {
    if (startpc != endpc) {
        char startpc1 = (char)startpc;
        char endpc1 = (char)endpc;
        char handler_pc1 = (char)handler_pc;
        if (startpc1 == startpc && endpc1 == endpc && handler_pc1 ==
 handler_pc) {
            code.addCatch(startpc1, endpc1, handler_pc1,(char)catch_type);
        } else {
            log.error(_, "limit.code.too.large.for.try.stmt");
            nerrs++;
        }
    }
}
```

---

将startpc、endpc与handler\_pc进行强制类型转换转为char类型后，通过恒等式“==”来判断它们是否与强制类型转换之前的值相等。由于Java的char类型用两个字节来表示，而int类型用4个字节表示，因此如果转换之后的数超过了char类型能表示的整数范围，那么当前版本的Javac会报错“try语句的代码过长”，否则调用Code类的addCatch\(\)方法向catcheInfo列表中追加一条异常处理记录。catchInfo变量的定义及addCatch\(\)方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
public ListBuffer<char[]> catchInfo = new ListBuffer<char[]>();
public void addCatch( char startPc, char endPc, char handlerPc, char 
catchType) {
    catchInfo.append(new char[]{startPc, endPc, handlerPc, catchType});
}
```

---

addCatch\(\)方法的实现非常简单，向catchInfo列表中追加一个相关记录信息的字符数组即可。 

现在try语句的body体与各个catch语句都处理完成了，下面处理finally语句。在genTry\(\)方法中的相关实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
boolean hasFinalizer = env.info.finalize != null && env.info.finalize.
hasFinalizer();
if (hasFinalizer) {
    code.newRegSegment(); // 将nextreg的值设置为max_locals来避免冲突
    int catchallpc = code.entryPoint(_, syms.throwableType);
    int startseg = startpc;
    // 为try语句body体中抛出而各个catch语句无法捕获的异常加上执行catch语句时可能抛出的异常
    // 生成异常记录
    while (env.info.gaps.nonEmpty()) {
        int endseg = env.info.gaps.next().intValue();
        registerCatch(_, startseg, endseg,catchallpc, 0);
        startseg = env.info.gaps.next().intValue();
    }
    // 将try语句body体或catch语句抛出的异常保存到本地变量表中，在生成完finally语句的字节码指令后
    // 将异常加载到操作数栈顶并使用athrow指令抛出
    Item excVar = makeTemp(syms.throwableType);
    excVar.store();
    genFinalizer(env);
    excVar.load();
    registerCatch(_, startseg,env.info.gaps.next().intValue(),catchallpc, 0);
    code.emitop0(athrow);
    code.markDead();
}
// 回填break语句的跳转地址
code.resolve(exitChain);
```

---

如果try语句含有finally语句，则生成finally语句的字节码指令，如实例17\-5，生成finally语句的字节码指令的编号范围为37~44，其中包括保存栈顶异常到本地变量表，执行完finally语句的字节码指令后抛出异常。在执行各个catch语句时也可能抛出异常，而这里异常最终都会存储到栈顶，等待finally进行重抛。 

当try语句的body体发生异常时，将异常压入操作数栈顶，调用code.entryPoint\(\)方法压入异常类型后获取到catchallpc，然后向异常表中继续插入try语句的body体中抛出的而各个catch语句无法捕获的异常加上执行catch语句时可能抛出的异常（这些异常不一定要使用throw关键字显式进行抛出）。对于实例17\-5来说，catchallpc的值为37，最终会向异常表中添加第3条、第4条与第5条异常记录，为了方便阅读，这里再次给出实例17\-5的异常表信息如下： 

---

```java
Exception table:
  from    to  	target  	type
    0     4    	11   		Class chapter17/FirstExc
    0     4    	24   		Class chapter17/SecondExc
    0     4    	37   		any
   11    17    	37   		any
   24    30    	37   		any
   37    38    	37   		any
```

---

现在env.info.gaps列表中记录的各个冗余指令的范围分别为4~11、17~14和30~37，而需要进行异常捕获的指令，也就是插入的异常记录的范围分别为0~4、11~17、24~30和37~38。不难看出，冗余指令的起始位置就是异常捕获的结束位置，而异常捕获的起始位置就是冗余代码的结束位置，try语句的body体生成的字节码指令的起始位置startpc为0。 

无论是try语句的body体还是各个catch语句，抛出的异常都会压入操作数栈顶，在调用genFinalizer\(\)方法生成finally语句的字节码指令之前，将异常类型保存到本地变量表中，在生成finally语句的字节码指令之后，加载到操作数栈顶，这样就可以重抛这个异常了。需要注意的是，在将异常类型保存到本地变量表时也需要生成一条异常记录，但是指令编号为38和39如果有异常抛出时，程序会提前结束，其他的异常会被抑制。 

最后通过调用code.resolve\(\)方法将需要回填的exitChain赋值给pendingJumps，这样在try语句后的第一条指令输入时，为break等语句对应的字节码指令进行地址回填。 
