package book.chapter11;

import java.io.Serializable;

/*
CA<Integer>类中的md()方法与IA<Integer>类中的md()方法相比，
形式参数类型相同，但是返回类型Number比Serializable更精确，
所以会为CA<T>类中定义的md()方法创建新的方法，
这个新方法的异常类型变为了MySubExc，
所以如果不在test()方法中对MySubExc异常类型进行捕获或抛出，
Javac将报错，报错摘要为“未报告的异常错误MySubExc；必须对其进行捕获或声明以便抛出”。
 */
interface IA26<T> {
    Serializable md(T a) throws MySubExc26;
}

public class Test26 {
}

class MyExc26 extends Exception {
}

class MySubExc26 extends MyExc26 {
}

abstract class CA26<T> {
    public abstract Number md(T a) throws MyExc26;
}

//abstract class CB extends CA26<Integer> implements IA26<Integer> {
//    public void test() throws MyExc26 {
//        md(1);
//    }
//
//    @Override
//    // 报错 'book.chapter11.CB' 中的 'md(Integer)'
//    // 与 'book.chapter11.IA' 中的 'md(T)' 冲突；
//    // 重写的方法未抛出 'book.chapter11.MyExc26'
//    public Number md(Integer a) throws MyExc26 {
//        return null;
//    }
//}