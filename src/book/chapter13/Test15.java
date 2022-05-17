package book.chapter13;

public class Test15 {
}

class Node15<T> {
    public T data;

    public void setData(T data) {
        this.data = data;
    }
}

/*
当method为Node类中定义的setData(T data)方法时，与擦除后的类型setData(Object data)类型不相同，所以需要在dest为MyNode中添加桥方法
 */
class MyNode15 extends Node15<Integer> {
    public void setData(Integer data) {
    }
}