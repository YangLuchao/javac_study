package book.chapter12;

/*
定义局部变量t的语法树节点为JCVariableDecl，
其中的vartype是一个JCFieldAccess树节点，引用的是类Test；
x的初始化部分是个JCFieldAccess树节点，引用的是成员变量a；
方法调用表达式md()是一个JCMethodInvocation树节点，其中的meth为JCFieldAccess树节点，引用的是方法md()。
 */
public class Test7 {
    public int a = 1;

    public void md() {
    }

    public void test() {
        book.chapter12.Test7 t = new Test7();
        int x = t.a;
        t.md();
    }
}
