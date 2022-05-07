package book.chapter10;

public class Test28 {
}

/*
隐藏（hidding）只针对成员变量、成员方法与成员类型
 */
class Parent28 {
    public static void md() {
    }
}

class Sub28 extends Parent28 {
    public static void md() {
    }

    public void test() {
        md();
    }
}