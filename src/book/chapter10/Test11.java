package book.chapter10;

public class Test11 {
}
/*
如果当前检查的类为CB，那么supertypes列表中包含IA和CA，
调用checkCompatibleAbstracts()方法对IA接口与CA抽象类中的方法进行兼容性检查。
实例10-11将报错，报错摘要为"类型IA和CA不兼容；两者都定义了md()，但却带有不相关的返回类型"
 */
interface IA11 {
  void md();
}
abstract class CA11 {
  public abstract int md();
}
// abstract class CB extends CA11 implements IA11 { }
