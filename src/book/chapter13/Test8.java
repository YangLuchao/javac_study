package book.chapter13;

import java.util.List;

/*
说明最小上界及求最小上界的必要性
 */
public class Test8 {
    public <T extends List<? extends Number>> void md(T a, T b) {
    }

    /*
    在调用泛型方法md()时，传递的实际参数类型为List<Integer>和List<Number>，它们都会作为推断类型变量T的具体类型的依据
    如果只根据List<Integer>与List<Number>类型来推导的话，T符合条件的类型非常多，如List、Object等都可以，
    但是将List或Object作为推断出来的类型可能并不满足要求，
    这些类型并不在类型边界List<? extends Number>范围之内，所以需要求出最小上界。
    实际上，List<? extends Number>就是最终求得的最小上界
    这是两个类的共同父类中最精确的类型，这里暂不考虑List<? super Integer>这种含有下界通配符的类型
     */
    public void test(List<Integer> x, List<Number> y) {
        md(x, y);
    }
}
