package book.chapter9;

interface IA {
}

class CA {
}

/*
makeCompoundType(List<Type> bounds, Type supertype) 例子
在将a变量的值赋值给b变量时会发生捕获转换，要对Test<? extends CA>类型中的实际类型参数进行捕获
调用glb()方法计算IA接口与CA类的最大下界。
由于CA为类，因此调用makeCompoundType()方法返回一个父类为CA、实现接口为IA、名称为空字符串的类
 */
public class Test9<T extends IA19> {
    Test9<? extends CA21> a = new Test9();
    Test9<?> b = a;
}