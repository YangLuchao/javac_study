package book.chapter14;

/*
由于if语句的条件判断表达式的结果为布尔常量false，
所以调用scanCond()方法分析完if语句的条件判断表达式tree.cond之后，
4个条件状态变量的值如下
initsWhenTrue = 0
uninitsWhenTrue = 1
initsWhenFalse = 1
uninitsWhenFalse = 0
执行完if语句后4个条件状态变量的值如下：
initsWhenTrue = 1
uninitsWhenTrue = 1
initsWhenFalse = 0
uninitsWhenFalse = 1
最后求得inits=1、uninitsWhenFalse=1，可以看到a变量被明确初始化，所以a可以出现在赋值表达式的右侧
 */
public class Test11 {
    public void test() {
        int a;
        if (false) {
            // nothing
        } else {
            a = 2;
        }
        int b = a;
    }
}
