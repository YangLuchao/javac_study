package book.chapter9;
public class Test20{}
interface IA20<T>{}
/*
T虽然没有直接实现S接口，但是T是非final修饰的类，所以可能有类继承了T并实现了S接口。
如果T是final修饰的类，那么T必须实现S接口，因为此时的T没有子类
 */
class T implements IA20<String> {}
interface S extends IA20<Integer> {}
// class A extends T implements S{}