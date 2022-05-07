package book.chapter10;

public class Test29 {
}
/*
当site为CB类，sym是CB类内定义的getVal(T t)方法时，
调用CompoundScope类内定义的getElementsByName()方法会查找到CA类中定义的getVal(Number n)方法，
而CB类内定义的getVal(T t)方法并不是getVal(Number n)方法的子签名，
所以调用types.isSubSignature()方法将返回false。
但是两个方法在泛型擦除后有相同的形式参数类型Number，所以Javac将报错，
报错摘要为“名称冲突：CB中的<T>getVal(T)和CA中的getVal(Number)具有相同疑符，但两者均不隐藏对方”
 */
class CA29 {
    public static void getVal(Number n) {
    }
}

class CB29 extends CA29 {
//    public static <T extends Number> void getVal(T t) {
//    }
}
