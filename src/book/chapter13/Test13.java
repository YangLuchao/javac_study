package book.chapter13;

public class Test13 {
}

class Node13<T> {
    public T data;

    public void setData(T data) {
        this.data = data;
    }
}

/*
当bridge等于method时可能需要桥方法
当sym为Node类中定义的setData()方法时，
查找到的bridge与method都为Node类中的setData()方法，所以bridge等于method。
 */
class MyNode13 extends Node13<Integer> {
    public void setData(Integer data) {
    }
}