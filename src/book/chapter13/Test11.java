package book.chapter13;

public class Test11 {
}

class Node11<T> {
    public T data;

    public void setData(T data) {
        this.data = data;
    }
}

// 如果声明的方法中含有类型变量时，处理会复杂一些，因为方法有覆写的特性，泛型擦除后可能导致无法实现覆写特性，
// 所以当一个类型继承或者实现一个参数化类型或者接口时，可能需要通过添加桥方法来保持覆写特性
class MyNode11 extends Node11<Integer> {
    public void setData(Integer data) {
    }
}

/*
泛型擦除形式如下
同一个包中定义了Node与MyNode类，MyNode类继承了参数化类型Node<Integer>，
其中MyNode类中的setData()方法覆写了Node类中的setData()方法
 */
class Node11_1 {
    public Object data;

    public void setData(Object data) {
        this.data = data;
    }
}

class MyNode11_1 extends Node11_1 {
    public void setData(Integer data) {
    }

    /*synthetic*/
    public void setData(Object x0) {// 合成的桥方法
        this.setData((Integer) x0);
    }
}