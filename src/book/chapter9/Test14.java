package book.chapter9;

public class Test14{

}

class Parent<T> {
}

/*
a变量的初始化表达式的类型为Outer<String>.Inner，不为裸类型，
而b变量的初始化表达式的类型为Outer.Inner，为裸类型，因此b变量会给出警告“未经检查的转换”
 */
class Outer<T> {
    class Inner extends Parent<String> {
    }

    public void test(Outer<String>.Inner x, Outer.Inner y) {
        // x不为裸类型
        Parent<String> a = x;
        // y为裸类型
        Parent<String> b = y; // 警告，未经检查的转换
    }
}