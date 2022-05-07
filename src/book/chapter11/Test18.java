package book.chapter11;

/*
在调用md()方法时传递的实际参数类型为Integer，
Test类中定义的两个md()方法都匹配，所以会调用mostSpecific()方法比较两个方法。
假设传递的m1参数表示md(Number a)方法，m2参数表示md(Integer a)方法，
那么调用signatureMoreSpecific()方法后m1 SignatureMoreSpecific的值将为false，
m2 SignatureMoreSpecific值将为true，最后返回m2，md(Integer a)方法更精确
 */
public class Test18 {
    public void md(Number a) {
    } // 第1个方法

    public void md(Integer a) {
    } // 第2个方法

    public void test(Integer p) {
        md(p);// 调用第2个方法
    }
}
