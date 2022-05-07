package book.c;

/*
这次ownerParams列表中含有类型变量T，而baseParams列表中含有Integer类型，
所以调用subst()方法替换sym.type，即类型变量T都替换为Integer，
也就是p.t的类型为Integer，所以t的值可以赋值给Integer类型的变量o
 */
public class Test15<T> {
    T t;
    public void test(Test15<Integer> p) {
        Integer o = p.t;
    }
}
