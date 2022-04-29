package book.chapter9;

// 将类型变量T2转换为T1时，由于T2的上界为T1，相当于T2是T1的子类，调用isSubtype()方法返回true。
class Test27<T1, T2 extends T1> {
    T2 a = null;
    T1 b = (T1) a;
}