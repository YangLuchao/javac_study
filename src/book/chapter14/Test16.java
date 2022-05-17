package book.chapter14;

public class Test16 {
    public void test() {
        int a;
        final int b;
        for (int i = 0; i < 2; i++) {
            a = 2; // 第1个错误，可能在 loop 中分配了变量b
//            b = 1;
        }
//        int c = a;// 第2个错误，可能尚未初始化变量a
    }
}
