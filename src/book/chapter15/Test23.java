package book.chapter15;

public class Test23 {
}

class Outer23 {
    private String a = "helloworld";
    private long b = 1;

    class Inner23 {
        public void md() {
            a += 1;
            b += 1;
        }
    }
}

// 解语法糖后
class Outer$Inner23_1 {
    /*synthetic*/ final Outer23_1 this$0;

    Outer$Inner23_1(/*synthetic*/ final Outer23_1 this$0) {
        this.this$0 = this$0;
//    super();
    }

    public void md() {
        Outer23_1.access$084(this$0, String.valueOf(1));
        Outer23_1.access$114(this$0, 1);
    }
}

class Outer23_1 {
    /*synthetic*/
    static long access$114(Outer23_1 x0, long x1) {
        return x0.b += x1;
    }

    /*synthetic*/
    static String access$084(Outer23_1 x0, Object x1) {
        return x0.a += x1;
    }

    private String a = "helloworld";
    private long b = 1;
}