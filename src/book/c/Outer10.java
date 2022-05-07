package book.c;

/*
在判断CB类中的md()方法是否覆写了CA类中的md()方法时，
会比较两个方法的形式参数类型Outer<String>.Inner<Integer>，
这两个类型相同，所以CB类中的md()方法覆写了CA类中的md()方法
 */
public class Outer10<T1> {
    class Inner10<T2>{ }
    class CA10 {
        public void md(Outer10<String>.Inner10<Integer> p) { }
    }
    class CB10 extends CA10 {
        public void md(Outer10<String>.Inner10<Integer> p) { }
    }
}