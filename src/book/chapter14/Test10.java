package book.chapter14;

/*
调用scanCond()方法分析完if语句的条件判断表达式tree.cond之后，4个条件状态变量的值如下
initsWhenTrue = 10
uninitsWhenTrue = 01
initsWhenFalse = 10
uninitsWhenFalse = 01
最后求得inits=10、uninits=00。在执行a=2赋值语句时，
由于a是final变量，所以要判断变量是否重复赋值，a所对应的uninits中的第2个位的值为0，
表示并不是明确非赋值状态，所以Javac报错，报错摘要为“可能已分配变量a”。
inits中的第2位的值为0，表示不是明确赋值状态，所以不能取a变量的值
 */
public class Test10 {
    public void test(boolean res) {
        final int a;
        if (res) {
            a = 1;
        }
//        a = 2;// 报错，可能已分配变量a
    }
}
