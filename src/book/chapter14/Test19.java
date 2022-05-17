package book.chapter14;

/*
赋值语句a=3会报错，报错摘要为“可能已分配变量a”，
因为for语句中含有break语句的执行路径给final变量a赋了值，在执行for语句后续的赋值语句a=3时，
uninits的值为001，其中第2个状态位表示的就是a变量的赋值状态，其值为0，表示变量a不是明确非赋值状态。
 */
public class Test19 {
    public void test(int n) {
        final int a;
        for (int i = 0; i < n; i++) {
            if (i == 1) {
                a = 1;
                break;
            }
        }
//        a = 3;// 报错，可能已分配变量a
    }
}
