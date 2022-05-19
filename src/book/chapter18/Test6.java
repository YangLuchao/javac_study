package book.chapter18;

import java.io.Serializable;
import java.util.List;

public class Test6 {
    public <A, B extends Serializable> void test(A a, List<B> b) {
    }
    /*
    方法test()的签名字符串如下:
        <A:Ljava/lang/Object;B::Ljava/io/Serializable;>(TA;Ljava/util/List<TB;>;)V
    其中，尖括号内为类型变量的签名，圆括号内为参数类型的签名，最后的V为方法的返回类型。
     */
}
