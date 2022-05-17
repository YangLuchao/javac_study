package book.chapter15;

public class Test26 {
}

/*
访问自有变量：方法上传递的形式参数，方法内定义的非编译时常量，这些变量统称为自由变量
要想对本地变量解语法糖，至少需要经过如下几个步骤:
    1:收集被本地类引用的自由变量。
    2:在本地类中合成相关的成员变量并更新原有构造方法，在构造方法中初始化这些成员变量。
    3:更新本地类中引用自由变量的方式，替换为引用合成的成员变量。
    4:更新对本地类中构造方法的调用，主要是在创建本地类的对象时，为构造方法传递实际参数。
 */
class Outer26 {

    public void md(final int a) {
        final Integer b = new Integer(1);
        final int c = 1;
        class Local {
            int x1 = a;
            Integer x2 = b;
            int x3 = c;
        }
        Local l = new Local();
    }
}

/*
解语法糖
 */
class Outer26_1 {
    public void md(final int a) {
        final Integer b = new Integer(1);
        final int c = 1;
        Outer$1Local26_1 l = new Outer$1Local26_1(this, a, b);
    }
}

class Outer$1Local26_1 {
    /*synthetic*/ final Outer26_1 this$0;
    /*synthetic*/ final Integer val$b;
    /*synthetic*/ final int val;
    // 本地类Local解语法糖后变为Outer$1Local，由于Local类引用了自由变量a与b，
    // 所以会在Outer$1Local类中合成对应的成员变量val与val$b，然后通过构造方法初始化，
    // 初始化后的val$b与val就可以替换本地类中对自由变量a与b的引用了。
    Outer$1Local26_1(/*synthetic*/ final Outer26_1 this$0, /*synthetic*/ final int val, /*synthetic*/ final Integer val$b) {
        // 在调用的构造方法中，第一个参数必须为Outer对象this$0，这是因为Outer$1Local实例的存在依赖于Outer实例，
        // 通过这样的方式可以保证在创建本地类实例时外部类实例已经存在。
        this.this$0 = this$0;
        this.val = val;
        this.val$b = val$b;
//        super();
    }

//    int x1 = val;
//    Integer x2 = val$b;
//    int x3 = 1;
}