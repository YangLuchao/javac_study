package book.chapter11;

public class Test25 {
}
/*
当使用裸类型CA时，擦写T1为Object，T2为Number，两个方法的参数类型不一样，
在不引用md()方法时不会出错。只有在通过方法调用表达式md(p)调用方法时，
Javac才会报错，报错摘要为“对md的引用不明确，CA中的方法md(T2)和CA中的方法md(T1)都匹配”。
 */
abstract class CA25<T1, T2 extends Number> {
    public abstract void md(T2 a);

    public abstract void md(T1 a);
}

abstract class CB25 extends CA25<Integer, Integer> {
    public void test(Integer p) {
        // 报错，对md的引用不明确, CA中的方法 md(T2)和CA中的方法 md(T1)都匹配
//        md(p);
    }
}