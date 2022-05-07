package book.c;

/*
在判断CB类中的md()方法是否覆写CA类中的md()方法时，
会检查两个方法的形式参数的类型MyColl<Number>是否与MyColl<? super Number>相等。
由于MyColl<? super Number>中的通配符类型? super Number的上界与下界都为Number，
所以可取的实际类型参数只能为Number，最终的类型相当于MyColl<Number>，
调用的visitClassType()方法返回true，表示相等，方法被正确地覆写
 */
public class Test8 {
}
class MyColl8<T extends Number> { }
class CA8 {
    public void md(MyColl8<? super Number> t) { }
}
class CB8 extends CA8 {
//     public void md(MyColl8<Number> n) { }
}
