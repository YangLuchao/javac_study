package book.chapter9;
interface IA21 {}
class CA21 {}
/*
将类型变量T转换为CA类时，可获取类型变量上界CA&IA，这是一个组合类型
由于组合类型的父类CA与实现接口IA都可以转换为CA类，因而类型变量T可以强制转换为CA类
com.sun.tools.javac.code.Types.TypeRelation#visitClassType
在visitClassType()方法的实现中，当t是ClassType对象，而s为ClassType或ArrayType对象时，
也就是当t为接口或类，而s可能是类、接口或者数组类型，对t与s进行泛型擦除后判断它们的父子关系。
1:如果isSubtype()方法返回true就表示有父子关系，能够进行强制类型转换
2:如果两个类型进行泛型擦除后没有父子关系，也就是当upcast值为false时，则需要继续进行判断。
    当t或者s为接口时会调用sideCast()方法或者sideCastFinal()方法进行判断，否则会返回false。
        也就是必须要保证t或s中有一个为接口，因为如果都为类并且这两个类没有父子关系，
        则这两个类不可能有任何的类型交集，因为任何类型不可能同时继承两个类
 */
public class Test21<T extends CA21 & IA21> {
    public void test(T a) {
        CA21 b = (CA21) a;
    }
}

