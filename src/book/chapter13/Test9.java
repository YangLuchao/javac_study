package book.chapter13;

class Collection<T> {
}

/*
钻石语法
钻石语法时需要进行类型推断，推断过程分为两个独立的步骤
    1：参考创建对象表达式中为构造方法传递的实际参数类型。
    2：如果一些需要推断的类型变量不能根据构造方法中传递的实际参数类型推断得出，那么Javac还将结合new表达式的上下文信息来推断。
        在当前JDK 1.7版本的Javac中，类型推断只会结合赋值表达式左侧声明的类型进行推断

当使用钻石语法时，通过new关键字创建泛型对象时会调用构造方法，而构造方法也是一种特殊的方法，所以钻石语法的类型推断类似于前面讲过的调用非构造方法时的类型推断，
 */
public class Test9 {
    // 对于a变量来说，Collection类中定义的类型变量T被明确指定为String类型
    Collection<String> a = new Collection<String>();
    // 对于b变量来说，创建泛型对象时使用了钻石语法，也就是没有明确指定T的类型，需要对类型变量T所代表的具体类型进行推断
    Collection<String> b = new Collection<>();
    // 对于c变量来说，创建对象时使用了Collection的裸类型，所以泛型相关信息被忽略
    Collection<String> c = new Collection();
}
