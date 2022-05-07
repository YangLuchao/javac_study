package book.chapter10;
public class Test12{}
/*
如果t1为IB接口，而t2为CA接口，那么interfaces1列表中包含IA、IB与Object类型，
而interfaces2列表中只包含CA类，调用firstDirectIncompatibility()方法比较两个列表中的所有类型，
查找不兼容的方法
 */
interface IA12 { }
interface IB12 extends IA12 { }
abstract class CA12 implements IA12 { }
class CB12 extends CA12 implements IB12 { }