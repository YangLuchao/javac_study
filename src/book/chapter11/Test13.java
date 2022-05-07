package book.chapter11;

/*
变量a声明的类型Inner引用的是test()方法内定义的本地类Inner，而不是Test类中定义的成员类Inner，
因为会优先从本地作用域env1.info.scope开始查找
 */
public class Test13 {
    class Inner13 {
    }

    public void test() {
        class Inner13 {
        }
        Inner13 a; // Inner引用的是本地类Inner
    }
}
