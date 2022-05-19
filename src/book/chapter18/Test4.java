package book.chapter18;

interface IA4<T>{ }
class Parent4<T>{ }
public class Test4 extends Parent4<String> implements IA4<String> { }
/*
Test4类的签名字符串如下：
Lbook.chapter18/Parent4<Ljava/lang/String;>;Lbook.chapter18/IA4<Ljava/lang/String;>;
Test类的父类签名为“Lbook.chapter18/Parent4<Ljava/lang/String;>;”
Test类实现接口的签名为“Lbook.chapter18/IA4<Ljava/lang/String;>;”。
 */