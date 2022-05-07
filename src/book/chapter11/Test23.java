package book.chapter11;

public class Test23 {
}

/*
在CB类中定义的静态方法md()隐藏了CA类中定义的静态方法，
所以mostSpecific()方法最终会返回CB类中的md()方法。
在mostSpecific()方法中，如果一个为抽象方法而另外一个为非抽象方法，则返回非抽象方法
 */
class CA23 {
    public static void md() {
    }
}

class CB23 extends CA23 {
    public static void md() {
    }

    public void test() {
        md(); // 调用CB类中的md()方法
    }
}