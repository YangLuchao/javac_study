package book.chapter13;

class Collections {
    // 泛型方法
    public static <T> void copy(T a) {
    }
}
/*
泛型方法是指那些至少有一个形式类型参数声明的方法。
有时候在调用泛型方法时，需要知道形式类型参数的具体类型，
但并不需要每次调用泛型方法时都明确指明类型参数的类型，
Javac等编译器能够根据上下文信息推断出实际类型参数的类型
 */
public class Test4 {
    /*
    Collections类中定义的copy()方法是泛型方法，
    对于第1个调用copy()方法的语句来说，类型变量T被明确指定为String类型，所以copy()方法中类型变量T就是String类型；
    对于第2个调用copy()方法的语句来说，调用会产生编译错误，因为方法调用不支持类似于创建泛型对象时的钻石语法；
    对于第3个调用copy()方法的语句来说，将结合调用上下文对T的具体类型进行推断，最终推断出的类型为String。
     */
    public void test(String p) {
        Collections.<String>copy(p);// 第1个调用copy()方法的语句
//        Collections.<>copy(p); // 第2个调用copy()方法的语句，将报编译错误
        Collections.copy(p); // 第3个调用copy()方法的语句，需要进行类型推断
    }
}
