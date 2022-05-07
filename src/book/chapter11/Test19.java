package book.chapter11;

/*
javac按顺序从Test类中查找匹配的md()方法，这个类中定义的3个方法都符合这次的方法调用。
在某次调用mostSpecific()方法时，传递的m1参数表示md(Number a,Integer b)方法，
m2参数表示md(Integer a,Number b)方法。
由于两个方法调用signatureMoreSpecific()方法比较后返回的m1 SignatureMoreSpecific与m2 SignatureMoreSpecific都为false，所以最终会返回一个ambiguityError对象，
这样当再次查找匹配方法时，m1参数代表md(Integer a,Integer b)方法，
此时的m1方法就会比之前的两个方法e.sym与e.sym2都精确，
mostSpecific()方法最后返回的err1与err2都是方法m1。
err1与err2相等时直接返回m1。除此之外，当m2.kind值为AMBIGUOUS时都会返回ambiguityError对象。
 */
public class Test19 {
    public void md(Integer a, Integer b) {
    } // 第1个方法

    public void md(Number a, Integer b) {
    } // 第2个方法

    public void md(Integer a, Number b) {
    } // 第3个方法

    public void test(Integer x, Integer y) {
        md(x, y); // 调用第1个方法
    }
}
