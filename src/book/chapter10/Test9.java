package book.chapter10;

public class Test9 {
}
interface IA9 {
  public int get();
}
abstract class CA9 {
  public abstract void get();
}
/*
Javac会调用Check类中的checkCompatibleSupertypes()方法对这样的情况进行检查，这个方法的调用链如下：
Attr.visitClassDef()->Attr.attrClass()->
Attr.attrClassBody()->Check.checkCompatibleSupertypes()
 */
// abstract class CB9 extends CA9 implements IA9 { }