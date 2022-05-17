package book.chapter13;

interface INode12<T> {
    void setData(T t);
}

public class Test12 {
}

/*
当bridge为null时可能需要桥方法
当sym为INode接口中定义的setData()方法时，
在MyNode类中没有参数类型为Object的桥方法，所以bridge为null
 */
class MyNode12 implements INode12<Integer> {
    public void setData(Integer data) {
    }
}