package book.chapter10;

import java.io.Serializable;

public class Test18 {
}

/*
当site为CB类时，父类为CA，那么md(String a)方法在父类CA下不会发生改变，
也就是当t1为CB类而s1为md(String a)方法时，
调用types.memberType(t1,s1)方法得到的st1与s1.type是一样的；
md(T a)方法在父类CA下会变为md(Object a)方法，
md(Object a)方法与md(T a)方法不一样，checkCompatibleConcretes()方法会继续进行检查。
 */
class CA18<T> {
    public void md(String a) {
    }

    public void md(T a) {
    }
}

class CB18 extends CA18 {
}