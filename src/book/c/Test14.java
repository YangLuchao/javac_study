package book.c;

/*
在分析局部变量o的初始化表达式p.t时，
由于p是裸类型Test且t变量就定义在Test类中，
所以调用asOuterSuper()方法得到的base仍然为裸类型Test。
owner.type为Test<T>，调用owner.type的allparams()方法获取所有的形式类型参数ownerParams，
这个列表中只含有一个类型变量T。
baseParams列表中保存着实际类型参数，这里为空。
最终调用erasure()方法计算sym.type的泛型擦除后的类型，
即对类型变量T进行泛型擦除后的类型为Object，所以t的值只能赋值给Object类型的变量o
 */
public class Test14<T> {
    T t;
    public void test(Test14 p) {
        Object o = p.t;
    }
}
