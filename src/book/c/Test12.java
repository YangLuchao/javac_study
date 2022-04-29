package book.c;

import java.util.Vector;

public class Test12 {
    /*
    Javac方法判断类型为Vector<? super Integer>的变量a的值是否
    可以赋值给类型为Vector<? super Integer>的变量b，
    首先对Vector<? super Integer>类型进行捕获转换，
    得到Vector<capture of ? super Integer>，
    然后调用containsType()方法判断? super Integer是否包含capture of ? super Number。
    具体就是调用匿名类中的visitWildcardType()方法，
    其中t的参数就是? super Integer，而s为capture of ? super Number，
    由于t是下界界通配符，所以只需要判断t的下界是否为s的下界的子类型即可，
    调用lowerBound()方法获取s的下界为Number，
    而t的下界为Integer，Integer是Number的子类，visitWildcardType()方法返回true。
     */
    public void test(Vector<? super Number> a) {
        Vector<? super Integer> b = a;
    }
}
