package book.chapter14;

/*
test()方法中有2层for语句，对于第2个for语句来说，流程从tree.body转到tree.step有2条可能执行的路径，
另外一条路径并不能跳转到第2个for语句的tree.step部分。
由于2条可能执行的路径上都初始化了变量a，所以执行tree.step中的i+=a表达式时不报错。
在调用visitContinue()方法时，跳转到第1个for语句的pendingExit对象会保存在pendingExits列表中，等待第1个for语句调用resolveContinues()方法进行处理。
如果代码进行了状态变量的合并，将result的值设置为true并返回，最终会在visitForLoop()方法中根据所有的可执行路径共同决定tree.step是否可达。
 */
public class Test18 {
    public void test(int n) {
        // 第1个for语句
        L:
        for (; ; ) {
            int a;
            // 第2个for语句
            for (int i = 0; i < n; i += a) {
                if (i == 1) {
                    a = 1;
                    continue;
                } else if (i == 2) {
                    continue L;
                }
                a = 2;
            }
        }
    }
}
