package book.chapter16;

public class Test5 {
    public void md() {
        int i, j;
        j = i = 1;
    }

    /*
    md()方法的字节码指令如下：
0: iconst_1
1: dup
2: istore_1
3: istore_2
4: return

赋值表达式“j=i=1”等价于表达式“j=(i=1)”，相当于包含了两个赋值表达式，具体的语法树结构如图16-4所示。
在Gen类的visitExec()方法中处理JCExpressionStatement树节点
genExpr(tree.expr, tree.expr.type).drop();
调用genExpr()方法处理JCAssign(j=i=1)语法树节点，期望的类型是tree.expr.type，这个方法最终会返回一个AssignItem对象，调用这个对象的drop()方法表示不使用这个赋值表达式的值
对于JCAssign(i=1)树节点来说，i是一个LocalItem对象，调用genExpr()方法处理tree.rhs会返回一个ImmediateItem对象，调用这个对象的load()方法将1加载到栈中（生成iconst_1指令），该方法最后返回一个AssignItem对象
对于JCAssign(j=i=1)树节点来说，l是一个LocalItem对象，当调用genExpr()方法处理tree.rhs时同样会调用visitAssign()方法处理，该方法会获取到处理JCAssign(i=1)树节点时的AssignItem对象。当调用AssignItem对象的load()方法时，由于lhs为LocalItem对象，因而会调用Item类的stash()方法将栈中的常量1复制一份（生成dup指令），然后调用LocalItem类的store()方法将新复制出来的常量1保存到局部变量表指定的位置（生成istore_1指令），这个位置就是变量i的位置。
在visitExec()方法中调用drop()方法最终会调用LocalItem类中的drop()方法，该方法将这个变量存储到本地变量表中（生成istore_2指令）。
     */
}
