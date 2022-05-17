package book.chapter13;

class Param3<T> {
}

/*
在处理Test类中声明的类型变量T1时，
会创建一个新的类型变量，因为T1的上界为T2，
而为T2传递的实际类型参数的类型为Param<T1>类，
这个Param<T1>类中的T1会在substBound()方法中被替换为新的类型变量，
而参数化类型Test<Param<T1>,Param<T1>>中的第一个实际类型参数Param<T1>也将被替换为新的类型变量，
最终的目的是为了使用同一个对象表示同一个类型变量，这样有利于后续的类型比较
 */
public class Test3<T1 extends T2, T2> {
    Test3<Param3<T1>, Param3<T1>> x;
}