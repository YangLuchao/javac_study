package book.chapter10;

public class Test14 {
}

interface IA14 {
    Number md();
}
/*
CA类中的md()方法实现了接口IA中定义的md()方法，虽然返回类型不同，
但是Javac允许覆写的方法的返回类型是被覆写方法返回类型的子类型，
这就是返回类型的协变，通过调用types.covariantReturnType()方法来判断
 */
class CA14 implements IA14 {
    @Override
    public Integer md() {
        return null;
    }
}