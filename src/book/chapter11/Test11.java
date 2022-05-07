package book.chapter11;

public class Test11 {
}

/*
在分析成员变量b的初始化表达式a.b时，
调用findIdent()方法确定名称为a的符号引用，
传递的kind参数的值为PCK|TYP|VAL，
首先找到了CA类中定义的变量a，但是这是私有变量，调用findVar()方法返回AccessError对象。
由于这个对象的exits()方法会返回false，
所以会继续调用findType()方法查找符号引用，这时候找到了名称为a的类，程序正常编译。
 */
class CA11 {
    private int a = 1;
}

class CB11 extends CA11 {
    class a {
        static final int b = 0;
    }

    int b = a.b; // a引用的是成员类a
}