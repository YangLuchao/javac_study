package book.chapter10;

public class Test16 {
}
/*
在IA与IB中，方法md()的参数类型相同，
因为对IA与IB接口中声明的类型变量T进行泛型擦除后都为Object类型，
但是当site等于CA类时，IA<String>中的md()与IB<Number>中的md()方法并不兼容，
所以Javac将报错，报错摘要为“名称冲突：IB中的md(T2#1)和IA中的md(T1#2)具有相同擦除，
但两者均不覆盖对方”。
 */
interface IA16<T1> {
    void md(T1 a);
}

interface IB16<T2> {
    void md(T2 a);
}

//abstract class CA implements IA16<String>, IB16<Number> {}