package book.chapter11;

public class Test22 {
}

/*
通过方法调用表达式md(p,p)调用的CA<Integer,Integer>类中的两个md()方法都匹配，
而且两个方法一样精确，所以Javac将报错，报错摘要为“对md的引用不明确，
CA中的方法md(T2,T2,T2...)和CA中的方法md(T1,T1...)都匹配”。
在mostSpecific()方法中，如果其中有一个为桥方法，则返回非桥方法，
桥方法是由Javac生成的，所以代码编写者不能通过程序调用
 */
class CA22<T1, T2 extends Number> {
    public void md(T2 a, T2 b, T2... c) {
    } // 第1个方法

    public void md(T1 a, T1... c) {
    } // 第2个方法
}

class CB22<T3> extends CA22<Integer, Integer> {
    public void test(Integer p) {
        // 报错，对md的引用不明确, CA中的方法 md(T2,T2,T2...)和CA中的方法md(T1, T1...)都匹配
        // md(p, p);
    }
}