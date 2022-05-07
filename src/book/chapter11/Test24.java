package book.chapter11;

public class Test24 {
}
// 两个同时为非抽象方法
class CA24<T1, T2 extends Number> {
    public void md(T2 a) {
    }

    public void md(T1 a) {
    }
}

/*
在CA<Integer,Integer>中定义了两个相同的方法md()，所以Javac报错摘要为“对md的引用不明确，
CA中的方法md(T2)和CA中的方法md(T1)都匹配”
 */
//class CB24 extends CA24<Integer, Integer> {
//    public void test(Integer p) {
//        // 报错，对md的引用不明确, CA中的方法 md(T2)和CA中的方法 md(T1)都匹配
//        md(p);
//    }
//}
