package book.chapter13;

import java.io.Serializable;

/*
对于第1个调用方法的语句来说，site表示Test<String>类型，m表示md()方法，调用memberType()方法后，
md()方法中的T类型会被替换为String类型，所以可以传递String类型的参数；
对于第2个调用方法的语句来说，site表示Test类型，m表示md()方法，调用memberType()方法后，
md()方法中的T类型会被替换为类型变量T声明时的上界Serializable，同样可以传递String类型的参数
 */
public class Test6<T extends Serializable> {
    public void md(T t) {
    }

    public void test(Test6<String> p1, Test6 p2) {
        p1.md("param"); // 第1个调用方法的语句
        p2.md("param"); // 第2个调用方法的语句
    }
}
