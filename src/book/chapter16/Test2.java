package book.chapter16;

// <clinit>表示类构造方法，所有关于类变量的初始化部分和静态块都将重构到<clinit>方法中。如果一个类没有类变量与静态块，那么Javac将不会产生<clinit>方法
public class Test2 {
    final static int a = md();
    final static int b = 1;
    static int c = 2;

    static {
        int d = 3;
    }

    public static int md() {
        return 0;
    }
}

// 实例经过normalizeDefs()方法重构后相当于变为了如下形式
// 对于编译期能得出值的常量并不会发生任何变化，而对于其他的静态成员来说会在<clinit>方法中完成初始化。
class Test2_1 {
//    final static int a;
//    final static int b = 1;
//    static int c;
//    static void <clinit>(){
//        a = md();
//        c = 2;
//        static {
//            int d = 3;
//        }
//    }
//
//    public static int md() {
//        return 0;
//    }
}