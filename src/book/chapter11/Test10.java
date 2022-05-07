package book.chapter11;

/*
Javac会简单地根据名称被使用的上下文得出期望的符号类型，但是有时候却不能充分理解上下文
在对var变量进行初始化时，代码编写者的本意是引用类a中定义的常量b进行初始化，
但是在确定a.b的引用时，调用findIdent()方法传递的参数kind的值为PCK|TYP|VAL，
由于findIdent()方法会优先将a当作变量查找，所以实例将报编译错误
 */
public class Test10 {
    int a = 1;

    class a {
        static final int b = 1;
    }

    public void test() {
        // int var = a.b; // 报错，无法取消引用int
    }
}
