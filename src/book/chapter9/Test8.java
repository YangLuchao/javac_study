package book.chapter9;

/**
 * 捕获转换（Capture Conversion）
 */
class Fruit {
}

class Apple extends Fruit {
}

class Plate<T> {
    private T item;

    public void set(T t) {
        item = t;
    }

    public T get() {
        return item;
    }
}

public class Test8 {
    public void test() {
        Plate<? extends Apple> a = new Plate<Apple>();
        Plate<? extends Fruit> b = a;
    }
}

class U1{

}
class B1 extends U1{

}

class B2 extends B1{

}
// 假设G是一个有形式类型参数声明的类型，其中只声明了一个类型参数A1，
// 它的上界为U1，那么需要对含有通配符类型T1的参数化类型G<T1>进行类型捕获。
class G<A1 extends U1> {

}
interface I1{

}
class G1 extends G implements I1{

}
class Test8_1 {
    // 如果T1是一个无界通配符?，那么S1是一个新的类型变量，它的上界是U1，下界是null；
    public void test1(){
        G<?> a = new G<>();
        G<?> b = a;
    }
    // 如果T1是一个上界通配符? extends B1，那么S1是一个新的类型变量，
    // 它的上界通过调用glb(B1,U1)方法得到，下界为null，
    // 其中，glb()方法可以计算两个类型中的最大下界。glb()方法的实现将在后面详细介绍；
    public void test2(){
        G<? extends B1> a = new G<>();
        G<?> b = a;

    }
    // 如果T1是一个下界通配符? super B2，那么S1是一个新的类型变量，该类型变量上界为U1，下界为B2
    public void test3(){

        G<? super B2> a = new G<>();
        G<?> b = a;
    }


}