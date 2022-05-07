package book.chapter11;

/*
var变量的类型被声明为a，所以调用findIdent()方法时，
传递的参数kind的值为TYP，方法最后确定名称a是对成员类a的引用。
 */
public class Test9 {
    int a = 1;

    class a {
    }

    public void test() {
        a var; // a引用的是成员类a
    }
}
