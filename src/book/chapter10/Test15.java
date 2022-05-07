package book.chapter10;

public class Test15 {
}

/*
com.sun.tools.javac.comp.Check.checkCommonOverriderIn
当s1与s2分别对应IA接口中的md()方法和IB接口中的md()方法时，这两个方法可以共存，
因为最终的CA类中的md()方法覆写了接口中的两个方法，提供了更精确的返回值类型
 */
interface MyInterface1_15 {
}

interface MyInterface2_15 {
}

class MyClass15 implements MyInterface1_15, MyInterface2_15 {
}

interface IA15 {
    MyInterface1_15 md(String a);
}

interface IB15 {
    MyInterface2_15 md(String a);
}

abstract class CA15 {
    abstract MyClass15 md(String a);
}

abstract class CB extends CA15 implements IB15, IA15 {
}
