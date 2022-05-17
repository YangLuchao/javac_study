package book.chapter15;

public class Test17 {
}

/*
refSuper&&!needsPrivateAccess(sym)表达式的值为true
当成员访问的限制符为“C.super”形式时，refSuper的值为true，其中C代表类型名称。
 */
class Parent17 {
    int a = 1;
}

/*
其中Sub.super.a这样的引用形式需要添加获取方法，refSuper的值为true并且不是获取的私有变量

 */
class Sub17 extends Parent17 {
    class Inner17 {
        public void md() {
            int b = Sub17.super.a;
        }
    }
}