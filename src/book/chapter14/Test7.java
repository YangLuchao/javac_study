package book.chapter14;

/*
可以看到两个构造方法都对变量x进行了初始化，但是第2个构造方法首先会调用第1个构造方法对x进行初始化，
如果在当前的构造方法中再次初始化时就会报错
 */
public class Test7 {
    final int x;

    public Test7(int d) { // 第1个构造方法
        x = 1;
    }

    public Test7() { // 第2个构造方法
        this(2);
//        x = 2; // 报错，可能已分配变量x
    }
}
