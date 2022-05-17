package book.chapter16;
// <init>表示实例构造方法，所有关于实例变量的初始化表达式和非静态块都可以重构到这个方法中
public class Test1 {
    final int a = md();
    final int b = 1;
    int c = 2;

    {
        int d = 3;
    }

    public int md() {
        return 0;
    }
}
// 实例经过normalizeDefs()方法重构后相当于变为了如下形
// 可以看到，对于运行期才能得出值的变量进行了重构，
// 在<init>构造方法中初始化这些变量，同时会将非静态匿名块重构到<init>构造方法中
class Test1_1 {
//    final int a;
//    final int b;
//    int c;
//    public <init>(){
//        super();
//        a = md();
//        b = 1;
//        c = 2;
//        {
//            int d = 3;
//        }
//    }

    public int md() {
        return 0;
    }
}