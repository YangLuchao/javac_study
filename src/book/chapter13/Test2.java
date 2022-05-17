package book.chapter13;

class Param<T> {
}

/*
对于参数化类型Test<Integer,Number,Param<Integer>,Integer>来说，由于没有封闭类，
所以formals和forms列表的值一样，actuals和args列表的值一样
调用types.substBounds()方法替换Test类中声明的类型变量T1、T2、T3与T4中的上界，替换后tvars_buf列表中的值按顺序为TypeVar(bound=Number)、TypeVar(bound=Object)、TypeVar(bound=Param<Integer>)和TypeVar(bound=Number)。
 */
public class Test2<T1 extends T2, T2, T3 extends Param<T4>, T4 extends Number> {
    Test2<Integer, Number, Param<Integer>, Integer> x;
}