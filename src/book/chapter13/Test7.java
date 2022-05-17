package book.chapter13;

public class Test7<T> {
    public void md() {
    }

    /*
    调用非泛型方法时指定了实际类型参数的类型为String，Javac默认不做任何操作，也就是直接忽略传递的实际类型参数，实例正常编译。
     */
    public void test(Test7 p) {
        // 调用md()方法不需要传递实际类型参数，但是如果传递了实际类型参数也不会报错
        p.<String>md();
    }
}
