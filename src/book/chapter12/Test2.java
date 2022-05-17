package book.chapter12;

/*
变量b的初始化部分是一个名称为a的JCIdent树节点，引用了成员变量a；
变量c声明的是一个名称为Test的JCIdent树节点，引用了Test类；
方法调用表达式md()是一个JCMethodInvocation树节点，其中的meth是一个名称为md的JCIdent树节点，引用了方法md()
 */
public class Test2 {
    public int a = 1;

    public void md() {
    }

    public void test() {
        int b = a;
        Test2 c = new Test2();
        md();
    }
}
