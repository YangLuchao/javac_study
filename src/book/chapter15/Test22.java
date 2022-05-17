package book.chapter15;

public class Test22 {
}

class Outer22 {
    // 对私有变量a来说，使用的方式不同会生成不同的获取方法。
    // 对于获取方法名称的后两个字符来说，就是在accessSymbol()方法中调用accessCode()方法获取到的acode，
    // 然后通过acode / 10 + acode % 10来得到获取方法名称的后两位字符。
    private int a = 1;

    class Inner22 {
        public void md() {
            int b = a; // deref
            a = 2; // assign
            int c;
            c = ++a; // preinc
            c = --a; // predec
            c = a++; // postinc
            c = a--; // postdec
            a += 1; // first assignment op
        }
    }
}

/*
解语法糖后
 */
class Outer22_1 {

    /*synthetic*/
    static int access$012(Outer22_1 x0, int x1) {
        return x0.a += x1;
    }

    /*synthetic*/
    static int access$010(Outer22_1 x0) {
        return x0.a--;
    }

    /*synthetic*/
    static int access$008(Outer22_1 x0) {
        return x0.a++;
    }

    /*synthetic*/
    static int access$006(Outer22_1 x0) {
        return --x0.a;
    }

    /*synthetic*/
    static int access$004(Outer22_1 x0) {
        return ++x0.a;
    }

    /*synthetic*/
    static int access$002(Outer22_1 x0, int x1) {
        return x0.a = x1;
    }

    /*synthetic*/
    static int access$000(Outer22_1 x0) {
        return x0.a;
    }

    private int a = 1;
}

class Outer$Inner22_1 {
    /*synthetic*/ final Outer22_1 this$0;

    Outer$Inner22_1(/*synthetic*/ final Outer22_1 this$0) {
        this.this$0 = this$0;
//        super();
    }

    public void md() {
        int b = Outer22_1.access$000(this$0);
        Outer22_1.access$002(this$0, 2);
        b = Outer22_1.access$004(this$0);
        b = Outer22_1.access$006(this$0);
        b = Outer22_1.access$008(this$0);
        b = Outer22_1.access$010(this$0);
        Outer22_1.access$012(this$0, 1);
    }
}