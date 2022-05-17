package book.chapter15;

/*
内部类访问私有构造方法
 */
public class Test15 {
}

/*
经过Javac编译后生成了3个Class文件，
分别为Outer.class、Outer$Inner.class和Outer$1.class。
Outer.class是Outer类生成的Class文件，
Outer$Inner.class是Inner生成的Class文件，
Outer$1.class是为了避免调用私有构造方法产生冲突而由Javac生成的一个空实现的类
 */
class Outer15 {
    private Outer15() {
    }

    class Inner {
        public void md() {
            Outer15 o = new Outer15();
        }
    }
}

/*
编译后
在Outer$Inner类中调用了一个合成的、形式参数类型为Outer$1的非私有构造方法，
这个构造方法又调用了Outer类的私有构造方法，内部类通过这样的方式访问了外部类的私有构造方法
之所以要合成一个参数类型为Outer$1的构造方法，是为了避免与现有构造方法冲突，
Outer$1类型是Javac在编译时使用的类型，不可能被代码编写者使用。
 */
//class Outer$Inner {
//    public void md() {
//        Outer o = new Outer(null);
//    }
//}
//
///*synthetic*/ class Outer$1 {
//}
//
//class Outer {
//    /*synthetic*/ Outer(book.chapter15.Outer$1 x0) {
//        this();
//    }
//
//    private Outer() {
//        super();
//    }
//}