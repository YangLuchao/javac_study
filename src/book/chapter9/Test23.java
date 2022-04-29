package book.chapter9;

import java.io.Serializable;

interface IA23<T> {
}

interface IB23<T1> extends IA23<T1> {
}

class CA23<T2> implements IA23<T2> {
}

/*
将变量a的值赋值给变量b时会发生捕获转换，经过捕获转换后a的类型变为了CA<capture of ? extends Serializable>（capture of ? extends Serializable是? extends Serializable的捕获类型）
由于擦除后的CA类与IB接口没有父子关系，
因而在将CA<capture of ? extends Serializable>强制转换为IB<? extends Cloneable>时，isCastable()方法会调用sideCast()方法进行判断
在sideCast()方法中对from与to的参数调整后，from为CA<capture of ? extends Serializable>类，而to为IB<? extends Cloneable>接口
from与to有个共同的泛型擦除后类型相同的父类IA
(
t1,t2在：com.sun.tools.javac.code.Types.sideCast
)
因此t1为IA<capture of ? extends java.io.Serializable>接口，t2为IA<? extends Cloneable>。
调用disjointTypes()方法判断t1的实际类型参数capture of ? extends Serializable是否与t2的实际类型参数? extends Cloneable有类型交集，
如果没有类型交集，sideCast()方法将返回false，那么两个类型不能进行强制类型转换
对于实例9-23来说，sideCast()方法返回true，实例能够正常编译
 */
public class Test23 {
    public void test(CA23<? extends Serializable> a) {
        IB23<? extends Cloneable> b = (IB23<? extends Cloneable>) a;
    }

    public static void main(String[] args) {
        Test23 test23 = new Test23();
        test23.test(new CA23<Serializable>());
    }
}