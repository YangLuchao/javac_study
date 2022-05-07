package book.c;

import java.io.Serializable;
import java.util.AbstractList;

/*
Java在判断CB类中的md()方法是否正确覆写了CA类中的md()方法时，
会比较参数类型MyColl<X>与MyColl<Y>，也就是比较实际类型参数X与Y是否相同。
由于实际类型参数都为类型变量，所以最终比较的是两个类型变量的上界，
也就是组合类型AbstractList&Serializable，visitClassType()方法将返回true
 */
public class Test9 {
}
class MyColl<T> { }
class CA9 {
    public <X extends AbstractList & Serializable> void md(MyColl<X> p) { }
}
class CB9 extends CA9 {
    public <Y extends AbstractList & Serializable> void md(MyColl<Y> p) { }
}
