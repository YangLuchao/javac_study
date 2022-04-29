package book.chapter10;

public class Test2 {
}

/*
在分析Sub<T2>的父类Parent<T2>时，
由于Parent<T2>是参数化类型，在查找T2类型变量的引用时就能准确找到Sub类中声明的类型变量T2了。
 */
class Parent2<T1>{ }
class Sub2<T2> extends Parent2<T2> { }
