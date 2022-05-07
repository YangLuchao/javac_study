package book.c;

/*
当分析b变量的初始化表示式p.a时，
由于p的类型为Test<String>，
调用memberType()方法计算Test<String>类型下a变量的类型，
计算后的类型为String，所以a变量的值可以赋值给String类型的变量b
 */
class Test13<T> {
    T a = null;
    public void test(Test13<String> p) {
        //
        String b = p.a;
    }
}