package book.chapter13;

import java.io.Serializable;

public class Test10 {
}

/*
泛型擦除前
 */
class Test10_1<X extends Integer, Y extends Serializable & Cloneable, Z extends X> {
    X x;
    Y y;
    Z z;
}

/*
泛型擦除后
X被替换为默认的上界Object
对于形如T1&T2...这样的上界，最终替换为类型T1，不管T1是类还是接口
另外，上界还可能是另一个类型变量，如Test类中Z的上界为X，此时就将Z替换为X的上界Object
 */
//class Test10_2 {
//    Object x;
//    Serializable y;
//    Object z;
//}