package book.chapter13;

public class Test16 {
}

interface INode16 {
    void getVal(String t);
}

class Node16<T> {
    public void getVal(T t) {
    }
}
/*
在分析MyNode类时，method为INode接口中定义的getValue()方法，
而impl为Node类中定义的getVal()方法，这个方法在泛型擦除前后类型不相同，所以需要添加桥方法。
 */
class MyNode16 extends Node16<String> implements INode16 {
}