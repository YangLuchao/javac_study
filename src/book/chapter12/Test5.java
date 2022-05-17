package book.chapter12;

import java.io.Serializable;

/*
将参数p的值赋值给变量x，由于x为含有通配符类型的ClassType对象，
所以首先需要调用capture()方法获取捕获转换后的类型，
然后才能判断捕获类型是否能正确赋值给Test<?>，赋值表达式正确。
 */
public class Test5<T extends Serializable> {
    public void test(Test5<? extends Number> p) {
        Test5<?> x = p;
    }
}
