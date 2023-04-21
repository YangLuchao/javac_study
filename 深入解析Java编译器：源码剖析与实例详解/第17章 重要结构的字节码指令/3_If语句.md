# If语句

Gen类中的visitIf\(\)方法为if语句生成字节码指令，该方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Gen
public void visitIf(JCIf tree) {
    int limit = code.nextreg;
    Chain thenExit = null;
    CondItem c = genCond(TreeInfo.skipParens(tree.cond),_);
    Chain elseChain = c.jumpFalse();
     // 要分析的if语句的条件判断表达式的结果不为常量值false
    if (!c.isFalse()) {
        code.resolve(c.trueJumps);
        genStat(tree.thenpart, env, _);
        thenExit = code.branch(goto_);
    }
    if (elseChain != null) {
        code.resolve(elseChain);
        if (tree.elsepart != null)
            genStat(tree.elsepart, env,_);
    }
    code.resolve(thenExit);
    code.endScopes(limit);
}
```

---

调用genCond\(\)方法获取到CondItem对象后调用jumpFalse\(\)方法，表示当条件判断表达式的值为false时需要进行地址回填。 

当条件判断表达式的值不为常量值false时，可调用genStat\(\)方法生成if语句body体中的字节码指令。但在此之前还需要调用code.resolve\(\)方法填写c.trueJumps中所有需要回填的地址，然后调用code.branch\(\)方法创建一个无条件跳转分支thenExit，这样当if语句块没有else分支时下一条指令就是thenExit的跳转目标；如果有else分支时，则跳转目标为else分支生成的字节码指令后的下一个指令地址。 

当tree.elsepart不为空时，同样调用genStat\(\)方法生成else语句body体中的字节码指令，不过在此之前还需要调用code.resolve\(\)方法回填要跳转到else语句body体中的elseChain，这样在生成else语句body体中的第一条字节码指令时就会调用resolve\(Chain chain,int target\)方法回填地址。 

Code类中对另外一个重载的resolve\(Chain chain\)方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.jvm.Code
public void resolve(Chain chain) {
    pendingJumps = mergeChains(chain, pendingJumps);
}
```

---

可以看到，只是调用了mergeChains\(\)方法将chain与pendingJumps合并后再次赋值给成员变量pendingJumps。当pendingJumps有值时，在生成下一条指令时就会对pendingJumps中所有的Chain对象进行地址回填，因而pendingJumps一旦被赋值，就确定下一个指令的地址就是所有pendingJumps中保存的Chain对象的回填地址。 

另外，visitIf\(\)方法还在开始执行时通过局部变量limit保存了code.nextreg的值，然后执行完成后调用code.endScopes\(\)方法将执行if语句而保存到本地变量表中的数据清除，因为已经离开了if语句的作用域范围，这些数据是无效的状态。 
