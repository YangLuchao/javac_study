# Try语句与throw语句的分析

对异常进行分析时，涉及throw语句和try语句的处理。对try语句进行数据流检查时，会进行异常检查、变量赋值检查及语句活跃性分析，下面详细介绍。

### 14.4.1　抛出异常 

程序通过throw语句抛出异常，在Flow类的visitThrow\(\)方法中对throw语句进行处理，这个方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Flow
public void visitThrow(JCThrow tree) {
    scanExpr(tree.expr);
    Symbol sym = TreeInfo.symbol(tree.expr);
    if ( sym != null &&
        sym.kind == VAR &&
        (sym.flags() & (FINAL | EFFECTIVELY_FINAL)) != 0 &&
        preciseRethrowTypes.get(sym) != null &&
        allowImprovedRethrowAnalysis) {// 允许使用增强型throws声明
        for (Type t : preciseRethrowTypes.get(sym)) {
            markThrown(tree, t); // 记录抛出的异常
        }
    }else {
        markThrown(tree, tree.expr.type); // 记录抛出的异常
    }
    markDead(); 
}
```

---

visitThrow\(\)方法首先调用TreeInfo.symbol\(\)方法获取tree.expr所引用的符号，这个方法在前面介绍过。如果是JCIdent或JCFieldAccess对象，直接获取对象中sym变量保存的值即可，如果为JCTypeApply对象，递归调用symbol\(\)方法获取对象的clazz变量对应的符号。 

方法中调用的markThrown\(\)方法可以记录抛出的异常类型，调用的markDead\(\)方法会将alive变量的值设置为false，表示后续的语句不可达。 

visitThrow\(\)方法支持了JDK 1.7版本中新增的增强型throws声明，举例如下： 

【实例14\-20】

---

```java
package chapter14;
class FirstExc extends Exception { }
class SecondExc extends Exception { }
public class Test{
    public void rethrowExceptionSE7(String exc) throws FirstExc, SecondExc {
        try {
            if (exc.equals("FirstExc")) {
                throw new FirstExc();
            } else {¬
                throw new SecondExc();
            }
        } catch (Exception e) {
            throw e;
        }
    }
}
```

---

以上实例在catch语句块内抛出Exception类型的异常，而在方法上声明抛出的异常类型为FirstExc与SecondExc。因为try语句的body体内只可能抛出这两种受检查的异常，所以这种语法叫增强型throws声明。在JDK 1.7之前，方法上声明抛出的异常类型只能为Exception或者Exception的父类。可以通过allowImprovedRethrowAnalysis变量来控制是否使用增强型throws声明语法，当前版本的Javac默认allowImprovedRethrowAnalysis变量的值为true，也就是默认使用增强型throws声明。 

在visitThrow\(\)方法中，if语句的条件判断条件中有含有如下判断表达式： 

---

```java
preciseRethrowTypes.get(sym) != null 
```

---

这是对重抛语句throw e之前的代码分析时得出的e可能的异常类型，将可能抛出的受检查异常和运行时异常记录到preciseRethrowTypes集合中，这个变量在Flow类中的定义如下： 

---

```java
来源：com.sun.tools.javac.comp.Flow 
HashMap<Symbol, List<Type>> preciseRethrowTypes;  
```

---

其中，key为Symbol对象，对应的就是catch语句中定义的形式参数，而value为List\<Type\>列表，其中保存着所有可能重抛的异常类型。对于实例14\-20来说，try语句的body体中可能抛出Error以RuntimeException、FirstExc和SecondExc类型的异常，但是对于catch语句来说，只能捕获FirstExc和SecondExc类型的异常，所以List\<Type\>列表中只保存这两种类型的异常。如果更改实例14\-20的rethrowExceptionSE7\(\)方法为如下形式： 

---

```java
public void rethrowExceptionSE7(String exc) throws FirstExc, SecondExc {
    try {
         if (exc.equals("FirstExc")) {
             throw new FirstExc();
         } else {
             throw new SecondExc();
         }
     } catch (FirstExc e) {
         e.printStackTrace();
     } catch (SecondExc e){
         throw e;
     }
}
```

---

通过throw e语句重抛异常时，可能抛出的异常类型就只剩下SecondExc了，因为之前的catch语句会对FirstExc类型的异常进行捕获处理。 

调用markThrown\(\)方法记录可能抛出的异常，实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Flow
void markThrown(JCTree tree, Type exc) {
    if (!chk.isUnchecked(_, exc)) { // 受检查异常
        if (!chk.isHandled(exc, caught)) // 没有被处理的异常
            pendingExits.append(new PendingExit(tree, exc));
        thrown = chk.incl(exc, thrown);
    }
}
```

---

markThrown\(\)方法会对受检查且没有被处理过的异常进行记录，具体就是创建一个PendingExit对象并追加到pendingExits列表中，在介绍异常捕获时还会介绍pendingExits列表，这里暂不过多介绍。如果exc是受检查异常，则调用chk.incl\(\)方法保存到thrown列表中。incl\(\)方法在第11章介绍过，它会首先从thrown列表中移除exc和exc的所有子类，然后将exc追加到thrown列表中。 

markThrown\(\)方法中使用到了两个成员变量caught与thrown，这两个变量在Flow类中的定义如下： 

---

```java
来源：com.sun.tools.javac.comp.Flow 
List<Type> thrown; 
List<Type> caught;  
```

---

thrown列表保存了可能抛出的异常，而caught列表保存了可以捕获的或者在方法上声明抛出的异常，举个例子如下： 

【实例14\-21】

---

```java
package chapter14;
class FirstExc extends Exception { }
class FirstSubExc extends FirstExc { }
class SecondExc extends Exception { }
public class Test {
    public void rethrowExceptionSE7(String exc) throws FirstExc {
        // 第1个try语句
        // 位置1
        try {
            // 第2个try语句
            try {
                if (exc.equals("FirstExc")) {
                    throw new FirstExc();
                }
                if (exc.equals("SecondExc")) {
                    throw new SecondExc();
                }
                // 位置2
            } catch (FirstExc e) {
                if (exc.equals("FirstExc")) {
                    throw new FirstSubExc();
                }
                // 位置3
            }
        } catch (SecondExc e) {
            // 位置4
        }
    }
}
```

---

如表14\-1所示为实例14\-21在位置1到位置4时thrown与caught列表中保存的值。 

表14\-1　不同位置对应的thrown与caught列表 

![image](https://github.com/YangLuchao/img_host/raw/master/20230418/image.2zk0jislgp40.webp)

if语句的条件判断表达式调用chk.isUnchecked\(\)方法确保要处理的是受检查的异常，调用chk.isHandled\(\)方法确保异常没有被处理过，这里所说的处理，是指异常被捕获或者在方法上声明抛出。 

isUnchecked\(\)方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Check
boolean isUnchecked(Type exc) {
    return
        (exc.tag == TYPEVAR) ? isUnchecked(types.supertype(exc)) :
        (exc.tag == CLASS) ? isUnchecked((ClassSymbol)exc.tsym) :
        exc.tag == BOT;
}
```

---

当exc为类型变量时，求类型变量的父类并递归调用isUnchecked\(\)方法判断是否为非检查异常；当exc为类或接口时，调用另一个重载的isUnchecked\(\)方法判断是否为非检查异常；当exc为null时，方法返回true，表示是非检查异常，剩下的其他类型都是受检查异常。 

重载的isUnchecked\(\)方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Check
boolean isUnchecked(ClassSymbol exc) {
    return
        exc.kind == ERR ||
        exc.isSubClass(syms.errorType.tsym, types) ||
        exc.isSubClass(syms.runtimeExceptionType.tsym, types);
}
```

---

当exc为错误相关的类或RuntimeException类型时，方法返回true，表示是非检查异常。 

在markThrown\(\)方法中调用的isHandled\(\)方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Check
boolean isHandled(Type exc, List<Type> handled) {
    return isUnchecked(exc) || subset(exc, handled);
}
```

---

当exc为非检查异常或是已经处理的异常的子类时，方法返回true，表示异常已经被处理。调用subset\(\)方法查看exc是否为handled列表中任何一个类型的子类型，实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Check
boolean subset(Type t, List<Type> ts) {
    for (List<Type> l = ts; l.nonEmpty(); l = l.tail)
        if (types.isSubtype(t, l.head)) 
            return true;
    return false;
}
```

---

subset\(\)方法实现非常简单，通过调用types.isSubtype\(\)方法判断即可。 

在markThrown\(\)方法中调用的incl\(\)方法的实现代码如下： 

---

```java

来源：com.sun.tools.javac.comp.Check
List<Type> incl(Type t, List<Type> ts) {
    return subset(t, ts) ? ts : excl(t, ts).prepend(t);
}
```

---

调用subset\(\)方法判断t是否为ts列表中任何一个类型的子类型，如果是就直接返回ts，否则调用excl\(\)方法排除ts列表中含有的t或t的子类型，然后将t追加到列表的头部。excl\(\)方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Check
List<Type> excl(Type t, List<Type> ts) {
    if (ts.isEmpty()) {
        return ts;
    } else {
        List<Type> ts1 = excl(t, ts.tail);
        if (types.isSubtype(ts.head, t)) 
            return ts1;
        else if (ts1 == ts.tail) 
            return ts;
        else 
            return ts1.prepend(ts.head);
    }
}
```

---

excl\(\)方法要从ts列表中排除含有的t或t的子类型，实现比较简单，不再过多解释。 

### 14.4.2　异常检查 

在visitTry\(\)方法中对try语句进行异常检查、变量赋值检查和活跃性分析，本节只介绍异常检查。为了便于叙述，分几个部分介绍visitTry\(\)方法的实现。首先介绍第一部分的实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Flow
public void visitTry(JCTry tree) {
    List<Type> caughtPrev = caught;
    List<Type> thrownPrev = thrown;
    thrown = List.nil();
    for (List<JCCatch> l = tree.catchers; l.nonEmpty(); l = l.tail) {
        List<JCExpression> subClauses = TreeInfo.isMultiCatch(l.head) ?
                ((JCTypeUnion)l.head.param.vartype).alternatives : List.of
(l.head.param.vartype);
        for (JCExpression ct : subClauses) {
            caught = chk.incl(ct.type, caught);
        }
    }
    ...
}
```

---

代码中首先通过局部变量保存caught与thrown列表的值，然后将当前try语句中含有的各个catch语句中能够捕获到的异常类型添加到caught列表中，举个例子如下： 

【实例14\-22】

---

```java
public void test(Reader file) {
    // 第1个try语句
    try {
        // 第2个try语句
        try (BufferedReader br = new BufferedReader(file);) {
            throw new MyExc();
        } catch (IOException e) {
            e.printStackTrace();
        }
    } catch (MyExc e) {
        e.printStackTrace();
    }
}
```

---

当visitTry\(\)方法分析第2个try语句并且已经运行完第一部分代码实现时，caughtPrev列表中有MyExc类，caught列表中有MyExc与IOException类，thrownPrev列表为空。 

当开始处理第2个try语句之前，当前能够捕获到的异常保存在caughtPrev列表中，这个列表中只含有第1个try语句能够捕获的MyExc类。在分析第2个try语句的body体时，如果已经运行完visitTry\(\)方法的第一部分代码，则能够捕获异常的caught列表中含有的MyExc与IOException类，thrownPrev列表为空。visitTry\(\)方法中调用的chk.incl\(\)方法将之前能够捕获处理的所有异常，加上内层能够捕获处理的异常保存到caught列表中，这样当内层try语句body体中抛出异常时，就可以从caught列表中检查这个异常是否能够被捕获处理，实际上markThrown\(\)方法也是这么做的。 

接着介绍visitTry\(\)方法中对异常检查的第二部分代码实现： 

---

```java
来源：com.sun.tools.javac.comp.Flow
ListBuffer<JCVariableDecl> resourceVarDecls = ListBuffer.lb();
ListBuffer<PendingExit> prevPendingExits = pendingExits;
pendingExits = new ListBuffer<PendingExit>();
for (JCTree resource : tree.resources) {
    List<Type> closeableSupertypes = resource.type.isCompound() ?
                   types.interfaces(resource.type).prepend(types.supertype
(resource.type)) :
                   List.of(resource.type);
    for (Type sup : closeableSupertypes) {
        if (types.asSuper(sup, syms.autoCloseableType.tsym) != null) {
        // 第1个if语句
            Symbol closeMethod = rs.resolveQualifiedMethod(tree,attrEnv,
sup,names.close,
                    List.<Type>nil(),List.<Type>nil());
            if (closeMethod.kind == MTH) { // 第2个if语句
                for (Type t : ((MethodSymbol)closeMethod).getThrownTypes()) {
                    markThrown(resource, t);
                }
            }
        }
    }
```

---

如果try语句是具体的try\-with\-resources语句，那么在自动调用close\(\)方法时可能抛出异常，所以也要将这些异常记录到throw列表中，以便后续在分析catch语句时提供必要的异常抛出类型。visitTry\(\)方法中的第1个if语句的条件判断表达式如下： 

---

```java
types.asSuper(sup, syms.autoCloseableType.tsym) != null 
```

---

调用asSuper\(\)方法查找sup或sup的父类型，这个类型的tsym等于syms.autoCloseableType.tsym，这个类型必须存在，也就是sup必须实现AutoCloseable接口，然后调用resolveQualifiedMethod\(\)方法从sup中查找close\(\)方法，将close\(\)方法中可能抛出的异常通过markThrow\(\)方法记录到throw列表中。关于resolveQualifiedMethod\(\)与markThrow\(\)方法，前面已经介绍过，这里不再介绍。 

接着在visitTry\(\)方法中处理try语句的body体，第三部分实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Flow 
scanStat(tree.body);    
```

---

在处理try语句的body体时，可能会通过throw关键字抛出异常，如果抛出受检查的异常，则会记录到throw列表中。 

接着在visitTry\(\)方法中为处理catch语句准备运行环境，第四部分实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Flow
List<Type> thrownInTry = allowImprovedCatchAnalysis ?
              chk.union(thrown, List.of(syms.runtimeExceptionType, syms.
errorType)) : thrown;
thrown = thrownPrev;
caught = caughtPrev;
List<Type> caughtInTry = List.nil();
```

---

通过局部变量thrownInTry保存thrown列表的值，这样thrownInTry列表中包含了try语句body体中抛出的受检查异常。对于JDK 1.7及之后的版本来说，还会追加Runtime Exception和Error。还原thrown与caught列表的值，因为catch语句应该使用thrownPrev与caughtPrev列表中的值进行异常分析。 

下面开始循环处理try语句含有的所有catch语句，visitTry\(\)方法第5部分的实现代码如下： 

---

```java

来源：com.sun.tools.javac.comp.Flow
for (List<JCCatch> l = tree.catchers; l.nonEmpty(); l = l.tail) {
    JCVariableDecl param = l.head.param;
    List<JCExpression> subClauses = TreeInfo.isMultiCatch(l.head) ?
            ((JCTypeUnion)l.head.param.vartype).alternatives : List.of(l.
head.param.vartype);
    List<Type> ctypes = List.nil();
    List<Type> rethrownTypes = chk.diff(thrownInTry, caughtInTry);
    for (JCExpression ct : subClauses) {
        Type exc = ct.type;
        if (exc != syms.unknownType) {
            ctypes = ctypes.append(exc);
            if (types.isSameType(exc, syms.objectType))
                continue;
            checkCaughtType(_, exc, thrownInTry, caughtInTry);
            caughtInTry = chk.incl(exc, caughtInTry);
        }
    }
    scan(param);
    preciseRethrowTypes.put(param.sym, chk.intersect(ctypes, rethrownTypes));
    scanStat(l.head.body);
    preciseRethrowTypes.remove(param.sym);
}
```

---

在visitTry\(\)方法的for循环中调用chk.diff\(\)方法计算重抛的异常，也就是说，如果在try语句body体中抛出的异常被当前分析的try语句中的catch语句捕获了，则在thrownInTry列表中移除，这样当前分析的try语句后续的catch语句就不用处理这些异常了；如果在当前catch语句的body体中重抛异常时，rethrownTypes列表中保存了这些可能被重抛的异常。diff\(\)方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Check
List<Type> diff(List<Type> ts1, List<Type> ts2) {
    List<Type> ts = ts1;
    for (List<Type> l = ts2; l.nonEmpty(); l = l.tail)
        ts = excl(l.head, ts); // 将ts列表中是l.head的子类的类型全部移除
    return ts;
}
```

---

循环调用excl\(\)方法将ts1列表中是ts2列表中类型的子类型从ts1列表中删除，excl\(\)方法前面已经介绍过，这里不再介绍。 

接着看visitTry\(\)方法的第五部分的代码实现。计算出rethrownTypes列表后，循环当前catch语句中声明捕获的异常类型列表subClauses，调用checkCaughtType\(\)方法对异常类型进行检查，checkCaughtType\(\)方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Flow
void checkCaughtType(_, Type exc, List<Type> thrownInTry, List<Type>
caughtInTry) {
    if (chk.subset(exc, caughtInTry)) {
        log.error(_, "except.already.caught", exc);
    } else if (!chk.isUnchecked(_, exc) && 
            !isExceptionOrThrowable(exc) && 
            !chk.intersects(exc, thrownInTry)) {
        log.error(_, "except.never.thrown.in.try", exc);
    }
}
```

---

checkCaughtType\(\)方法主要对以下两种情况进行了检查： 

* 对于同一个try语句含有的多个catch语句来说，在分析当前catch语句时，检查之前是否已经有catch语句捕获了exc异常，如果已经捕获，Javac将报错，报错摘要为“已捕获到异常错误”。 
* 如果不可能在try语句的body体中抛出的受检查异常也在catch语句中声明了捕获，Javac将报错，报错摘要为“在相应的try语句主体中不能抛出异常错误”。 

如果没有错误发生，在visitTry\(\)方法中调用chk.incl\(\)方法将exc添加到caughtInTry列表中，新列表将作为新的caughtInTry变量的值，这是分析下一个catch语句时使用的变量值。 

visitTry\(\)调用chk.intersect\(\)方法对ctypes与rethrownTypes列表中的类型取交集。假设ctypes列表中含有Exception类型，rethrownTypes列表中含有Exception类的子类FirstExc与SecondExc，那么chk.intersect\(\)方法最终返回含有FirstExc与SecondExc类型的列表，表示如果在当前catch语句中重抛异常，实际上抛出的是这两个具体的异常类型。 

visitTry\(\)将调用chk.intersect\(\)方法得到的值存储到preciseRethrowType集合中，调用scanStat\(\)方法处理catch语句的body体。如果异常类型进行了重抛，查看前面关于异常抛出的相关内容，重抛中的增强型throws声明语法正是借助preciseRethrowType集合来完成的。 

接着在visitTry\(\)方法中处理try语句中含有的finally语句，第六部分代码实现如下： 

---

```java
来源：com.sun.tools.javac.comp.Flow
if (tree.finalizer != null) {
    List<Type> savedThrown = thrown;
    thrown = List.nil();
    ListBuffer<PendingExit> exits = pendingExits;
    pendingExits = prevPendingExits;
    alive = true;
    scanStat(tree.finalizer);
    if (!alive) {
        thrown = chk.union(thrown, thrownPrev);
    } else {
        thrown = chk.union(thrown, chk.diff(thrownInTry, caughtInTry));
        thrown = chk.union(thrown, savedThrown);
        while (exits.nonEmpty()) {
            PendingExit exit = exits.next();
            pendingExits.append(exit);
        }
    }
} else {
    thrown = chk.union(thrown, chk.diff(thrownInTry, caughtInTry));
    ListBuffer<PendingExit> exits = pendingExits;
    pendingExits = prevPendingExits;
    while (exits.nonEmpty()) 
        pendingExits.append(exits.next());
}
```

---

如果try语句含有finally语句，调用scanStat\(\)方法处理完finally语句之后计算thrown与pendingExits列表的值。 

如果alive的值为false，最终的thrown列表中的异常为finally语句body体中抛出的异常加上try语句之前抛出的异常，这样会导致当前try语句body体及catch语句body体中抛出的异常被抑制，可能会提示“finally子句无法正常完成”。 

如果alive的值为true，thrown列表中的异常由以下三部分组成： 

* try语句（包括body体及自动调用resource中的close\(\)方法抛出的异常）可能抛出的异常而catch语句没有捕获的异常。 
* catch语句的body体中可能抛出的异常与try语句之前的语句可能抛出的异常。 
* finally语句的body体中可能抛出的异常。 

最后将exits列表中的值追到pendingExits列表中。 

如果try语句不含有finally语句，同样会计算thrown与pendingExits列表的值。 

thrown列表的异常由以下两部分组成： 

* try语句的body体中抛出的而catch语句没有捕获的受检查异常。 
* catch语句的body体可能抛出的异常thrown与在try语句之前抛出的异常。 

最后将pendingExits列表中的值加入到prevPendingExits列表中，如果pendingExits列表中有值，表示有未处理的受检查异常，在Flow类的visitClassDef\(\)方法中。当分析完所有的类成员时，会执行如下代码： 

---

```java

来源：com.sun.tools.javac.comp.Flow
for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
    if (l.head.getTag() == JCTree.METHODDEF) {
        scan(l.head);
        errorUncaught();
    }
}
```

---

当成员为方法时，调用errorUncaught\(\)方法，这个方法主要的实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Flow
void errorUncaught() {
    for (PendingExit exit = pendingExits.next();
         exit != null;
         exit = pendingExits.next()) {
         ...
         log.error(_,"unreported.exception.need.to.catch.or.throw",exit.
thrown);
    }
}
```

---

当pendingExits列表中有值时会报错。 

### 14.4.3　变量赋值状态及语句的活跃性 

在visitTry\(\)方法中处理tree.resources树节点之前会对初始化状态变量也就是在执行第14.4.2节的第二部分代码之前还有如下代码： 

---

```java
来源：com.sun.tools.javac.comp.Flow
Bits uninitsTryPrev = uninitsTry;
Bits initsTry = inits.dup();
uninitsTry = uninits.dup();
```

---

在处理try语句body体之前，会通过initsTry与uninitsTry保存inits与uninits的值。因为在处理try语句的body体时会更新inits与uninits的值，但是在分析catch语句或者finally语句的body体时，使用的仍然是initsTry与uninitsTry变量的值。 

在调用scanStat\(\)方法处理完tree.body树节点之后，在开始处理catch语句或finally语句之前，需要更新状态变量，也就是在执行14.4.2节的第5部分代码之前还有如下代码： 

---

```java
来源：com.sun.tools.javac.comp.Flow
boolean aliveEnd = alive;
uninitsTry.andSet(uninits);
Bits initsEnd = inits;
Bits uninitsEnd = uninits;
int nextadrCatch = nextadr;
```

---

处理catch语句或finally语句时使用的状态变量应该为initsTry与uninitsTry。对于uninitsTry来说，因为tree.body是一条可能的执行路径，所以最终的取值为处理tree.body之前保存的值uninitsTry与之后的值uninits取交集，举个例子如下： 

【实例14\-23】

---

```java

public void test() {
    int a;
    try {
        a = 1;
    } catch (Exception e) {
        int b = a; // 报错，可能尚未初始化变量a
    }
}
```

---

实例将报错，报错摘要为“可能尚未初始化变量a”。虽然try语句的body体初始化了变量a，但这是一条可选择的执行路径。再举个例子，如下： 

【实例14\-24】

---

```java
public void test() {
    final int a;
    try {
        a = 1;
    } catch (Exception e) {
        a = 2; // 报错，可能已分配变量a
    }
}
```

---

实例会报错，报错摘要为“可能已分配变量a”。因为在分析catch语句时使用的是try语句body体运行之前的变量uninitsTry与运行之后的变量uninits取交集的状态，所以a不是明确非初始化，因为有一条可执行的路径可能初始化了变量a。 

接下来就是在visitTry\(\)方法中分析try语句含有的各个catch语句了。以下代码对应14.4.2节中visitTry\(\)方法中的第五部分代码，只不过前面只给出了异常检查的相关代码，删除了与变量赋值及活跃性分析的相关代码。这里删除了异常检查相关的代码实现，代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Flow
for (List<JCCatch> l = tree.catchers; l.nonEmpty(); l = l.tail) {
    alive = true;
    JCVariableDecl param = l.head.param;
    inits = initsTry.dup();
    uninits = uninitsTry.dup();
    scan(param);
    inits.incl(param.sym.adr);
    uninits.excl(param.sym.adr);
    
    scanStat(l.head.body);
    initsEnd.andSet(inits);
    uninitsEnd.andSet(uninits);
    nextadr = nextadrCatch;
    aliveEnd |= alive;
}
```

---

在分析catch语句时，将inits与uninits初始化为initsTry与uninitsTry，然后调用scan\(\)方法处理catch语句中的形式参数param。处理完成后再次更新inits与uninits，因为catch语句body体中同样可以使用当前catch语句中声明的形式参数。 

处理完catch语句body体后，需要更新initsEnd与uninitsEnd，这两个状态变量是运行try语句body体后的状态变量。可以看到，最终会与各个catch语句body体运行后的状态变量取交集，最终这个initsEnd与uninitsEnd会作为分析try语句后续语句的状态变量，举个例子如下： 

【实例14\-25】

---

```java
public void test() {
    int a;
    try {
        a = 1;
    } catch (Exception e) {
        a = 2;
    }
    int b = a;
}
```

---

实例正常编译，最后a变量出现在初始化语句的右侧，表示a已经被明确初始化。因为从定义a变量到使用a变量时的两条可能执行路径都进行了明确赋值，这两条路径分别是try语句body体执行路径和catch语句body体执行路径。这里仅讨论没有finally语句的情况，如果有finally语句时，还需要进一步讨论。 

如果try语句含有多个catch语句并且当前分析的不是第一个catch语句时，由于inits与uninits中包含了上一次catch语句中定义的变量的状态，如形式参数param变量的状态，这些变量的状态在分析下一个catch语句时都失效，因为相关变量已经不在作用域范围之内，所以在处理下一个catch语句时，需要再次通过initsTry与uninitsTry还原inits与unints的值。 

当try语句中含有finally语句时，visitTry\(\)方法中的处理逻辑如下： 

---

```java
来源：com.sun.tools.javac.comp.Flow
if (tree.finalizer != null) {
    inits = initsTry.dup();
    uninits = uninitsTry.dup();
    ListBuffer<PendingExit> exits = pendingExits;
    pendingExits = prevPendingExits;
    alive = true;
    scanStat(tree.finalizer);
    if (!alive) {
       ...
    } else {
        ...
        uninits.andSet(uninitsEnd);
        while (exits.nonEmpty()) {
            PendingExit exit = exits.next();
            if (exit.inits != null) {
                exit.inits.orSet(inits);
                exit.uninits.andSet(uninits);
            }
            pendingExits.append(exit);
        }
        inits.orSet(initsEnd);
        alive = aliveEnd;
    }
}else {
    inits = initsEnd;
    uninits = uninitsEnd;
    alive = aliveEnd;
}
uninitsTry.andSet(uninitsTryPrev).andSet(uninits);
```

---

在执行finally语句之前初始化inits与uninits，当调用scanStat\(\)方法处理完tree.finalizer并且alive的值仍然为true时，更新相关的状态变量及alive的值。在更新exit.inits时与inits取并集，因为变量需要在每条可选择的执行路径上都初始化才能变为明确初始化状态；在更新exit.uninits时与uninits取交集，因为变量需要在每条可选择的执行路径上都没有初始化才能变为非明确初始化状态。在更新uninits时与uninitsEnd取交集，在更新inits时与initsEnd取并集，这是因为finally语句不是一条可选择的执行路径，而是一条必须执行的路径。举个例子如下： 

【实例14\-26】

---

```java
public void test() {
    int a;
    try {
        a = 1;
    } catch (Exception e) {
    } finally {
        a = 3;
    }
    int b = a;
}
```

---

实例编译正常，因为finally语句中的body体一定会执行，所以a会被明确初始化，可以出现在初始化语句的右侧。再举个例子如下： 

【实例14\-27】

---

```java
public void test() {
    int a;
    try {
        a = 1;
    } catch (Exception e) {
        a = 3;
    }
    int b = a;
}
```

---

try语句的body体与catch语句的body体都初始化了变量a，两条可选择的执行路径都初始化了a，所以a会被明确初始化，可以出现在初始化语句的右侧。 

当没有finally语句时直接将inits与uninits初始化为initsEnd与uninitsEnd即可。 

最后无论有没有finally语句都需要将alive的值更新为aliveEnd。也就是说，只要try语句的body体与各个catch语句这几条可选择的执行路径中有一条是活跃的，最终的aliveEnd的值就为true。 

在有finally语句并且alive的值为true的情况下，对PendingExit对象中的inits与uninits也会做处理，确定在这一条可能执行的路径上明确初始化的变量和未初始化的变量。这里当在Flow类中的visitMethodDef\(\)方法中处理完方法的body体时，实现代码如下： 

---

```java
List<PendingExit> exits = pendingExits.toList();
pendingExits = new ListBuffer<PendingExit>();
while (exits.nonEmpty()) {
    PendingExit exit = exits.head;
    exits = exits.tail;
    if (exit.thrown == null) {
        Assert.check(exit.tree.getTag() == JCTree.RETURN);
        if (isInitialConstructor) {
            inits = exit.inits;
            for (int i = firstadr; i < nextadr; i++)
                checkInit(_, vars[i]);
        }
    } 
    ...
  }
```

---

当要分析的方法中有返回语句return时，如果isInitialConstructor的值为true，也就是构造方法不以this\(...\)形式的语句开头时，要调用checkInit\(\)方法根据exit.inits检查作用域有效范围内的变量初始化情况。 
