package book.c;

import java.util.Vector;

public class Test11 {
    /*
    Javac判断类型为Vector<? extends Number>的变量a的值是否可以赋值给类型为Vector<? extends Object>的变量b。
    首先对Vector<? extends Number>类型进行捕获转换，
    得到Vector<capture of ? extends Number>，
    然后调用containsType()方法判断? extends Object是否包含capture of ? extends Number。
    具体就是调用匿名类中的visitWildcardType()方法，
    其中t的参数就是? extends Object，而s为capture of ? extends Number，
    由于t是上界通配符，所以只需要判断s的上界是否为t的上界的子类即可。
    调用upperBound()方法获取s的上界为Number，而t的上界为Object，Number是Object的子类，
    visitWildcardType()方法返回true
     */
    public void test(Vector<? extends Number> a) {
        Vector<? extends Object> b = a;
    }
}
