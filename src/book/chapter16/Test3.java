package book.chapter16;

public class Test3 {
    // 在md()方法内声明了一个局部变量a并初始化为常量值1，md()方法最后返回a
    public int md() {
        int a = 1;
        ++a;
        return a;
    }
    /*
    字节码指令
    0: iconst_1
    1: istore_1     // 调用LocalItem类的store()方法将栈顶的整数1存储到本地变量表1的位置
    2: iinc 1, 1    // 调用LocalItem的incr()方法为变量a加1
    5: iload_1      // 调用LocalItem类的load()方法将变量a加载到栈中
    6: ireturn
     */
}
/*
“int a=1”语句对应的语法树如图 语句的语法树1 所示
首先调用Gen类的visitVarDef()方法处理“int a=1”语句:

来源：com.sun.tools.javac.jvm.Gen
VarSymbol v = tree.sym;
genExpr(tree.init, v.erasure(types)).load();
items.makeLocalItem(v).store();

调用genExpr()方法处理tree.init，最终会返回一个ImmediateItem对象，则表示常量值1，
调用这个对象的load()方法会将这个常量加载到操作数栈中并生成对应的指令iconst_1
调用items.makeLocalItem()方法创建一个表示a变量的LocalItem对象，然后调用此对象的store()方法，
这个方法会将操作数栈中的常量值存储到本地变量表的指定位置，这个位置代表的就是变量a



++a:
“++a”语句对应的语法树结构如图 语句的语法树2 所示。
首先会执行visitExec()方法处理JCExpressionStatement树节点
visitExec()方法首先将后缀自增与自减的语句更改为前置自增与自减，这样可以简化处理，同时也是等价变换
调用genExpr()方法处理JCUnary(++a)，期望类型直接从标注语法树中获取即可，
visitExec()方法最终会调用visitUnary()方法处理JCUnary(++a)树节点。
调用genExpr()方法处理a，od为LocalItem(type=int; reg=1)，调用od.duplicate()最终会调用Item类中的duplicate()方法，
这个方法是个空实现，不做任何操作，最终还会调用od的incr()方法以生成iinc指令

return a:
调用Gen类的visitReturn()方法

调用genExpr()方法处理JCIdent(a)节点，则会返回LocalItem(type=int; reg=1)；
调用load()方法将局部变量表中指定索引位置1的数加载到操作数栈中，会生成iload_1指令，
LocalItem类的load()方法最终会返回一个StackItem对象，调用此对象的load()方法就是返回自身。
之所以再次调用，是因为像LocalItem这样的对象，其load()方法表示的含义并不是加载数据到操作数栈中。
visitReturn()方法最后根据pt的不同选择生成具体的ireturn指令，表示返回一个整数类型的值


 */
