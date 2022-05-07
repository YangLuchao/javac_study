package book.chapter11;

/*
当分析InnerB的父类MemberClass时，env1.tree为JCClassDecl(name=InnerB)对象，
env1.enclClass为JCClassDecl(name=InnerA)对象，此时需要判断InnerB是否含有static修饰符。
如果含有static修饰符，那么MemberClass就不能通过静态环境引用，因为InnerB是静态类，
当继承的父类为MemberClass时，MemberClass是一个非静态类，需要Test实例的存在，
而此时可能没有Test实例，所以实例将报错，报错摘要为“此处不允许使用修饰符static”
 */
public class Test12 {
    class MemberClass12 {}

    class InnerA12 {
//        static class InnerB12 extends MemberClass12 {}
        // 报错，此处不允许使用修饰符static
    }
}
