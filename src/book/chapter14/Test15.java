package book.chapter14;

public class Test15 {
    public void test() {
        final int a;
        // 由于在for语句中对final变量执行了赋值操作，所以uninits中变量a对应的位的状态值为0，表示不是明确非赋值状态
        // 当第2次执行do-while循环时，再次给final变量a赋值就会报错，
        // 所以不能在tree.cond、tree.body和tree.step中对tree.cond之前定义出的final变量进行赋值操作，因为它们都有可能被执行多次。
        for (int i = 0; i < 10; i++) {
//            a = 1;// 报错，可能在 loop 中分配了变量b
        }
    }
}
