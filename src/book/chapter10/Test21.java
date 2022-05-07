package book.chapter10;

import java.io.Serializable;
import java.util.List;

public class Test21 {
}
/*
在比较CB类中的md()方法与CA类中的md()方法时，hasSameArgs()方法返回false，
returnTypeSubstitutable()方法会接着判断List<Serializable>与泛型擦除后的类型List<T1>是否兼容
covariantReturnType()方法最后返回true，表示兼容，所以CB类中的md()方法覆写了CA类中的md()方法。
 */
abstract class CA21 {
    public abstract <T1 extends Serializable> List<T1> md();
}

class CB21 extends CA21 {
    @Override
    public List<Serializable> md() {
        return null;
    }
}