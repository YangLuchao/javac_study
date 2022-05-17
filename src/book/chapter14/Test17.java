package book.chapter14;

public class Test17 {
    /*
    for语句的tree.cond不为空，在处理tree.cond之后tree.body之前，将init与uninits分别初始化为initsWhenTrue与initsWhenFalse
    在执行tree.step之前，tree.body中有两条可能的执行路径到达tree.step，所以在处理tree.step之前需要合并这两条路径的变量状态。
    对于执行continue语句的这条路径来说，变量状态被存储到了pendingExits中，另外一条可能执行的路径的变量状态保存在inits与unints中
    由于a变量在两条可能执行的路径上都进行了初始化，所以在tree.step中可以使用a变量的值。
     */
    public void test(int n) {
        int a;
        for (int i = 0; i < n; i += a) {
            if (i == 1) {
                a = 1;
                continue;
            }
            a = 2;
        }
    }
}
