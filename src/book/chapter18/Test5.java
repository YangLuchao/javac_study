package book.chapter18;

import java.util.List;

public class Test5 {
    List<? extends Number> a ;
    List<? super Integer> b ;
    List<?> c ;
}
/*
字段类型签名
字段类型签名可以将字段、参数或局部变量的类型编译成对应的签名信息
个字段对应的签名字符串分别如下：
    Ljava/util/List<+Ljava/lang/Number;>;
    Ljava/util/List<-Ljava/lang/Integer;>;
    Ljava/util/List<*>;
 */
