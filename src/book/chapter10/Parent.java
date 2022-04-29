package book.chapter10;
class Parent {
    public static void md() { }
}
class Sub extends Parent2 {
    public static void md() { }
    public void test() {
        md();
    }
}