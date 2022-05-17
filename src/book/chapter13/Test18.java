package book.chapter13;

public class Test18 {
}

class Node18<T> {
    public void getVal(T t) {
    }
}
/*
当method与impl都为Node类中的getVal()方法时，isBridgeNeeded()方法将返回true，
那么在addBridgeIfNeeded()方法中就会调用addBridge()方法添加桥方法
 */
class MyNode extends Node18<String> {
}