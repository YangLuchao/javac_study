package book.chapter14;

/*
a、c、d与e变量在调用trackable()方法后返回true，表示需要对这些变量进行赋值检查
调用newVar()方法将这4个变量对应的VarSymbol对象添加到vars数组中
 */
public class Test6 {
    // 由final修饰的成员变量的特殊性在于，如果在定义时没有显式初始化，
    // 那么必须在构造方法或者初始化块内显式初始化，所以实例14-6将报编译错误，
    // 报错摘要为“可能尚未初始化变量a”。
//    final int a;
    int b; // 不需要进行赋值检查，因为有默认的零值

    public void test() {
        final int c;
        int d;
        int e = 1;
        // 对变量c进行了初始化，Javac在处理这样的赋值语句时会调用Flow类中的visitAssign()方法，这个方法会间接调用letInit()方法
        c=1;
        // 由于d出现在赋值表达式的右侧，所以d变量必须有值，通过VarSymbol对象的adr可知d被存储到了vars数组下标为2的位置，而inits中第2个位的值为0，表示没有明确初始化，所以报错
//        c=d;// 报错，可能尚未初始化变量d
    }
}
/*
两个状态变量inits与uninits，下面使用这两个状态变量对变量赋值状态进行检查
a变量会保存到vars数组下标为0的位置，所以inits与uninits中第0个位表示的是a变量的状态，
inits中第0个位的值为0，表示变量没有明确初始化,所以不能使用a变量的值，只有为1时才可以取a变量的值，如变量可以出现在赋值表达式的右侧；
uninits中第0个位的值为1，表示变量明确未初始化，所以如果对应的变量有final修饰，可以对a变量进行初始化。
在处理完test()方法最后一条声明变量e的语句后，与变量赋值检查相关变量的值如图14-1所示
 */
