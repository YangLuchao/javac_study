package book.chapter9;

interface IA24<T> {
}

interface IB24<T1> extends IA24<T1> {
}

class CA24<T2> implements IA24<T2> {
}

public class Test24 {
    interface MyInterface {
    }

    class MyClass {
    }


    /*
    将CA<MyInterface>类强制转换为IB<MyClass>接口，
    由于非final修饰的CA类与IB接口不存在父子关系，因而调用sideCast()方法判断是否能进行强制类型转换。
    将CA<MyInterface>类强制转换为IB<MyClass>接口时，
    会调用visitType()方法判断MyInterface与MyClass的关系，由于是两个具体的类型，
    因而最终会调用notSoftSubtype()方法判断，该方法判断t不等于s并且t与s没有父子关系，
    所以方法将返回true，表示两个类型互斥。显然对于具体类型MyInterface与MyClass来说，
    只有在相等时才不互斥，所以实例报错，报错摘要为“不可转换的类型”。
     */
    public void test(CA24<MyInterface> a) {
//        IB24<MyClass> b = (IB24<MyClass>) a; // 报错，不可转换的类型
    }

    /*
    调用visitType()方法判断capture of ? extends MyInterface与MyClass的关系。
    由于表示capture of ? extends MyInterface的CapturedType对象的tag值为TYPEVAR，
    因而在notSoftSubtype()方法中会判断MyInterface接口是否能通过强制类型转换为MyClass类，
    notSoftSubtype()方法返回true，t与s互斥。因为MyClass为具体的类型，
    所以最终的实际类型参数只能为MyClass，而“? extends MyInterface”中不包含MyClass，
    所以实例报错，报错摘要为“不可转换的类型”。
    */
    public void test25(CA24<? extends MyInterface> a) {
//        IB24<MyClass> b = (IB24<MyClass>) a;// 报错，不可转换的类型
    }

    /*
    调用visitWildcardType()方法判断capture of ? extends MyInterface
    与“? extends MyClass”的关系，
    由于表示capture of ? extends MyInterface的CapturedType对象的tag值不为WILDCARD，
    因而会调用notSoftSubtypeRecursive()方法判断capture of ? extends MyInterface与“? extends MyClass”的上界MyClass的关系。
    在调用notSoftSubtype()方法时，由于CapturedType对象的tag值为TYPEVAR，因而调用isCastable()方法判断MyInterface是否能通过强制类型转换转为MyClass，
    isCastable()方法将返回true，表示两个实际类型参数不互斥，
    因为任何一个继承MyClass类、实现MyInterface接口的子类都可以作为实际类型参数。
     */
    public void test26(CA24<? extends MyInterface> a) {
//        IB24<? extends MyClass> b = (IB24<? extends MyClass>) a;
    }
}