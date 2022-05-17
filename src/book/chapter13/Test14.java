package book.chapter13;

import java.io.Serializable;

/*
当sym为IA接口中定义的md()方法，origin为CB类时，
最终的meth为IA接口中定义的md()方法，bridge为CA类中定义的md()方法，
impl为CB类中定义的md()方法。定义bridge的类CA并不是定义impl的类CB的子类，
所以需要在origin也就是CB类中添加桥方法，
 */
public class Test14 {
}

interface IA14<T extends Serializable> {
    T md();
}

class CA14<T extends Number> implements IA14<Serializable> {
    public T md() {
        return null;
    }
}

class CB14 extends CA14<Number> {
    public Integer md() {
        return null;
    }
    /*
    添加的桥方法如下
     */
//    /*synthetic*/ public Number getFoo() {
//  return this.md();
//    }
//    /*synthetic*/ public Serializable getFoo() {
//  return this.md();
//    }
}