package book.chapter11;

/*
通过方法调用表达式md(x,y)调用方法md()时，由于Test类中定义的两个md()方法都匹配，
所以需要比较两个md()方法的签名。在不允许类型装箱转换与类型拆箱转换的条件下，
signatureMoreSpecific()方法会返回false，
所以在mostSpecific()方法中会返回AmbiguityError类型的错误
 */
public class Test20 {
    public void md(int a, Integer b) {
    } // 第1个方法

    public void md(Integer a, int b) {
    } // 第2个方法

    public void test(Integer x, Integer y) {
        // 报错，对md的引用不明确, Test中的方法 md(int,Integer)和Test中的方法
        // md(Integer,int)都匹配
        // md(x, y);
    }
}
