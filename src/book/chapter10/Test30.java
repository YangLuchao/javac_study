package book.chapter10;

public class Test30 {
}

/*
两个类中定义的getVal()方法之间没有覆写的关系，
但是对CB类中定义的getVal()方法进行泛型擦除后，
签名与CA类中定义的getVal()方法一样，所以Javac将报错，
报错摘要为“名称冲突：CB中的<T>getVal(T)和CA中的getVal(Number)具有相同疑符，但两者均不覆盖对方”
 */
class CA30 {
    public void getVal(Number n) {
    }
}

/*
调用checkOverrideClashes()方法检查CB类中的getVal(T t)方法，
参数site为CB类，sym为CB类中的getVal(T t)方法对应的符号。
在循环检查时，当s1为CA类中的getVal(Number n)方法，s2为CB类中的getVal(T t)方法时，
由于s2不为s1的子签名，但是泛型擦除后的s1等于s2并且s2没有覆写s1，
所以Javac将报错，报错摘要为“名称冲突：CB中的<T>getVal(T)和CA中的getVal(Number)具有相同疑符，
但两者均不覆盖对方”。
 */
class CB30 extends CA30 {
    // 报错，CB中的<T>getVal(T)和CA中的getVal(Number)具有相同疑符
    // 但两者均不覆盖对方
//    public <T extends Number> void getVal(T t) {
//    }
}