package book.chapter15;

public class Test14 {
}

/*
内部类访问私有变量
 */
class Outer14 {
    private int a = 1;

    class Inner {
        int b = a;
    }
}

/*
解语法糖后
Javac对内部类Inner解语法糖后做了一些特殊处理
在Outer$Inner中访问外部类的私有变量a更改为通过调用Outer.access$000()方法访问，
后面我们将方法名以“access$”字符串开头的方法都称为获取方法
其实不光是私有成员变量，对于私有方法及某些特殊情况下由protected修饰的成员，都需要通过获取方法来访问。
 */
//class Outer {
//    /*synthetic*/
//    static int access$000(Outer x0) {
//        return x0.a;
//    }
//
//    private int a = 1;
//}
//
//class Outer$Inner {
//    /*synthetic*/ final Outer this$0;
//
//    Outer$Inner(/*synthetic*/ final Outer this$0) {
//        this.this$0 = this$0;
//        super();
//    }
//
//    int b = Outer.access$000(this$0);
//}