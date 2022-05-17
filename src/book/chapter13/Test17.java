package book.chapter13;

import java.io.Serializable;

interface INode17<T extends Serializable> {
    T getVal(String t);
}

public class Test17 {
}

/*
method为INode接口中定义的getVal()方法，而impl为Node类中定义的getVal()方法，
这个方法实现了接口INode中声明的getVal()方法。但是在虚拟机看来，
这两个方法有不同的签名，一个方法覆写另外一个方法时，返回类型必须严格一致，
 */
class Node17<T extends Number> implements INode17<Serializable> {
    public T getVal(String t) {
        return null;
    }
}
// 所以需要在MyNode类中添加如下桥方法
// /*synthetic*/ public Serializable getVal(String x0) {
//   return this.getVal(x0);
// }
