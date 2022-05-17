package book.chapter15;

public class Test19 {
}

class Outer19 {
    // 如果sym是静态成员，base可以是类型名称或具体的实例
    private static final int c = 3;
    // 如果sym没有static修饰，在调用获取方法时需要追加base参数到实际参数列表头部
    private final int a = 1;
    private final int b = 2;

    class Inner19 {
        int x = a;
        // 如果sym是实例成员，base就是具体的实例，要访问base实例中的sym成员变量，需要向获取方法传递参数
        // 例如，对于new Outer().b表达式来说，将new Outer()作为参数传递给获取方法access$100()。
        // 由于new Outer().b是JCFieldAccess类型的节点，所以直接取selected的值作为base即可
        // 计算的receiver为JCFieldAccess(Outer.access$100)，最后access()方法返回JCMethodInvocation对象。
        int y = new Outer19().b;
        // 如果sym是静态成员，base可以是类型名称或具体的实例。
        // 例如对于new Outer().c表达式来说，base直接取JCFieldAccess节点的selected值即可，
        // 计算的receiver为JCFieldAccess(new Outer().access$200)，
        // 最后access()方法返回JCMethodInvocation对象。
        int z = new Outer19().c;
    }
}
/*
解语法糖后
 */
//class Outer19_1 {
//
//          /*synthetic*/ static int access$200() {
//    return c;
//  }
//
//          /*synthetic*/ static int access$100(Outer19_1 x0) {
//    return x0.b;
//  }
//
//          /*synthetic*/ static int access$000(Outer19_1 x0) {
//    return x0.a;
//  }
//  private int a = 1;
//  private int b = 2;
//  private static int c = 3;
//}
//class Outer$Inner19_1 {
//  /*synthetic*/ final Outer19_1 this$0;
//              Outer$Inner19_1(/*synthetic*/ final Outer19_1 this$0) {
//    this.this$0 = this$0;
//    super();
//    }
//  int x = Outer19_1.access$000(this$0);
//  int y = Outer19_1.access$100(new Outer19_1());
//  int z = new Outer19_1().access$200();
//}