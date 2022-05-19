package book.chapter18;

interface IA3<T>{ }
class Parent3<T>{ }
public class Test3<A,B extends IA3<String>,C extends Parent3 & IA3> { }
/*
签名被描述为字符串存放到了常量池中，
由于类、方法或字段都有可能含有泛型相关的信息，
因而可以在需要时通过类、方法或者字段的属性表中含有的Signature属性，去常量池中找到对应的签名文本字符串。
1.类签名
<A:Ljava/lang/Object;B::Lbook.chapter18/IA3<Ljava/lang/String;>;C:Lbook.chapter18/Parent3;:Lbook.chapter18/IA3;>Ljava/lang/Object;
其中，在Test上声明的类型变量A的签名为“A:Ljava/lang/Object;”；
类型变量B的签名为“B::Lchapter18/IA<Ljava/lang/String;>;”；
类型变量C的签名为“C:Lchapter18/Parent;:Lchapter18/IA;”；
Test类的父类签名为“Ljava/lang/Object;”
 */