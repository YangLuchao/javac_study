package book.chapter10;

public class Test31 {
}

/*
调用checkOverrideClashes()方法检查CB类中的getVal()方法时，
参数site为CB类，sym是CB类中的getVal()方法对应的符号。
在循环检查时，当s1为IA接口中的getVal()方法、s2为CA类中的getVal()方法时，由于s1不等于s2，
但是泛型擦除后的s1等于s2并且s2没有覆写s1，所以Javac将报错，报错摘要为“名称冲突：
CB中的getVal(Number)覆盖的方法的疑符与另一个方法的相同，但两者均不覆盖对方”。
 */
interface IA31<T1> {
    public void getVal(T1 a);
}

class CA31<T2> {
    public void getVal(T2 b) {
    }
}

//abstract class CB31 extends CA31<Number> implements IA31<Integer> {
//    // 报错，名称冲突: CB 中的 getVal(Number) 覆盖的方法的疑符与另一个方法相同
//    // 但两者均不覆盖对方
//    public void getVal(Number c) {
//    }
//}