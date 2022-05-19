package book.chapter17;

public class Test2 {
    /*
    for语句中含有2个contine语句并且跳转目标一致，因此对应的两个Chain对象会连接起来保存到GenContext对象的cond中；
    2个break语句的跳转目标一致，对应的两个Chain对象会链接起来保存到GenContext对象的exit中。
     */
    public void test(int[] arr) {
        for (int i = 0; i < arr.length; i++) {
            if (i == 1) {
                continue;
            }
            if (i == 2) {
                continue;
            }
            if (i == 3) {
                break;
            }
            if (i == 4) {
                break;
            }
        }
    }
}
