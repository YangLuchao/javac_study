package book.chapter18;

public class Test8 {
}
/*
字段x的签名如下：
Lbook.chapter18/Outer8<Ljava/lang/String;>.Inner8<Ljava/lang/Integer;>;
 */
class Outer8<T> {
    class Inner8<X> {
        Outer8<String>.Inner8<Integer> x;
    }
}