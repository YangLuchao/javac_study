package book.chapter12;

/*
在初始化局部变量x时，当site为Test<String>类型时，
调用types.memberType()方法得到成员变量t的类型为String，
所以能够正确赋值给String类型的变量x
 */
public class Test4<T> {
    T t = null;

    public void test(Test4<String> p) {
        String x = p.t;
    }
}
