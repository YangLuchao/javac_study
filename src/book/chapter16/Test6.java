package book.chapter16;

public class Test6 {
    static int a = 1;

    public void md() {
        a = a + 1;
    }

    /*
0: getstatic  #2    // 调用StaticItem类的load()方法将变量a的值压入到操作数栈顶
3: iconst_1         // 将本地第一个变量压入栈顶
4: iadd             // 栈顶执行自增操作
5: putstatic  #2    // 调用StaticItem类的store()方法设置变量a的值
8: return

“a=a+1”语句的树结构如图16-5所示。

     */

}
