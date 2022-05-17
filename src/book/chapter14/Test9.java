package book.chapter14;

/*
状态变量或条件状态变量的第1个位代表的是res变量的赋值状态，第2位代表的是a变量的赋值状态
initsWhenTrue = 10
uninitsWhenTrue = 01
initsWhenFalse = 10
uninitsWhenFalse = 01
分析完if语句后求得inits=11、uninits=01
在分析int b=a语句时，a变量已经被明确初始化，因为inits的第2个位上的值为1，表示a变量已经被初始化，
所以可以使用a变量的值初始化b变量
当a为非final变量时，不参考uninits的第2位的值，因为uninits中的值表示的是明确非赋值状态，
主要用于final变量的检查，对于非final变量时，重复赋值并不会产生编译错误。
当a为非final变量时，不参考uninits的第2位的值，因为uninits中的值表示的是明确非赋值状态，
主要用于final变量的检查，对于非final变量时，重复赋值并不会产生编译错误。
例14-10
 */
public class Test9 {
    public void test(boolean res) {
        int a;
        if (res) {
            a = 1;
        } else {
            // 如果注释掉else分支中a=2的赋值语句，分析完if语句后求得inits=10、uninits=01。
            // 在执行int b=a语句时，a变量没有被明确初始化，因为inits的第2个位上的值为0，
            // 所以Javac将报错，报错摘要为“可能尚未初始化变量a”。
            a = 2;
        }
        int b = a;
    }
}
