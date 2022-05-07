package book.chapter10;

/*
同名的变量a并不冲突，在局部变量a的作用域内引用的变量a就是局部变量，也就是局部变量a遮蔽（shadowing）了成员变量a。
 */
public class Test25 {
    int a = 1;

    public void test() {
        int a = 2;
        int b = a;
    }
}
