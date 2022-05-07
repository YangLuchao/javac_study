package book.chapter11;

public class Test17<T> {
    public T md() {
        return null;
    }

    /*
    当分析局部变量a的初始化表达式p.md()时，由于p是Test<String>类型，
    所以在Test<String>类型下md()方法的返回类型是String。
    调用types.memberType()方法（此方法在附录C中有介绍）时，
    传递的site参数就是表示Test<String>类型的ClassType对象，
    m是表示方法md()的MethodSymbol对象，m.type为ClassType对象，
    这个对象的restype为表示类型变量T的TypeVar对象，最后返回的mt是表示方法md()的ClassType对象，
    不过这个对象的restype已经变为了表示String类型的ClassType对象
     */
    public void test(Test17<String> p) {
        String a = p.md();
    }
}
