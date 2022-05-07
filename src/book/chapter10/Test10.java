package book.chapter10;

public class Test10 {
}
/*
类CA实现的接口IA<String>是个参数化类型，
所以会调用checkCompatibleAbstracts()方法对IA<String>类型中的方法进行检查。
在checkCompatibleSupertypes()方法中调用checkCompatibleAbstracts()方法时传递了两个相同的参数l.head，
也就是对同一个类型中定义的方法进行兼容性检查。
实例10-10会报错，报错摘要为“类型IA<String>和IA<String>不兼容；
两者都定义了md(java.lang.String)，但却带有不相关的返回类型”。
 */
interface IA10<T> {
    void md(String a);

    int md(T a);
}

//abstract class CA implements IA10<String> {}
