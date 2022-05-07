package book.chapter10;

public class Test17 {
}

/*
上实例将报错，报错摘要为“CA<String>中的方法md(T)和CA<String>中的方法md(String)是使用相同的签名继承的”
 */
class CA17<T> {
    public void md(String a) {
    }

    public void md(T t) {
    }
}

//class CB17 extends CA17<String> {}
