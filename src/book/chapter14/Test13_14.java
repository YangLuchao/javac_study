package book.chapter14;

public class Test13_14 {
    public void test() {
        int a;
        for (int i = 0; i < 2; i++) {
//            int b = a; // 报错，可能尚未初始化变量a
        }
    }

    public void test1() {
        int a;
        for (int i = 0; i < 2; i++) {
            a = 1;
        }
    }
}
