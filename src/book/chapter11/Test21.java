package book.chapter11;

/*
如果to对应为第1个md()方法，而from对应第2个md()方法，
当第1个方法的形式参数个数少于第2个方法时，在adjustVarargs()方法中向args列表中追加2个类型，
因为第2个方法除去varargsTypeFrom后有两个参数。
将第1个方法的a参数的类型追加到Integer中，当追加第2个参数时，由于这个参数是varargsTypeTo，
所以调用types.elemtype()方法得到变长参数的元素类型Integer，
最后再追加一个变长参数元素类型Integer，所以第1个方法在经过形式参数调整后相当于变为了如下形式：
public void md(Integer a, Integer b, Integer c) { }
第1个方法在经过形式参数调整后相当于变为了如下形式如下：
public void md(Integer a, Integer b, Number c) { }
在signatureMoreSpecific()方法中将此方法的3个形式参数类型作为调用第2个方法时传递的实际参数类型。
对于实例11-21来说，实际参数类型兼容形式参数类型，所以第1个方法比第2个方法更精确，
方法调用表达式md(x,y)将调用第1个方法
 */
public class Test21 {
    public void md(Integer a, Integer... b) {
    } // 第1个方法

    public void md(Integer a, Integer b, Number... c) {
    } // 第2个方法

    public void test(Integer x, Integer y) {
        md(x, y); // 调用第1个方法
    }
}
