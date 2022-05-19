package book.chapter18;

public class Test7 {
    public void test() {
        class Local<X> {
            Local<String> a;
        }
    }
    /*
    对于本地类Local中声明的变量a来说，其类型的签名如下
        Lbook.chapter18/Test7.1Local<Ljava/lang/String;>;
    调用ct.getEnclosingType()方法得到Local<String>的封闭类为Test<T>，
    因此需要擦除泛型相关的信息，最终封闭类的签名为“Lbook.chapter18/Test”，
    然后调用c.flatname.subName()方法得到1Local，最后计算实际类型参数的签名
     */
}
