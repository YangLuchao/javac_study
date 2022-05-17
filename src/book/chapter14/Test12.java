package book.chapter14;

/*
if语句的条件判断表达式是一个二元表达式，表达式的左侧为res，右侧为(a=1)==1，
没有else分支，这里着重分析tree.thenpart中的语句int b=a时的变量赋值状态。
visitBinary()方法执行前，inits=10，uninits=01，第1个位表示res变量的状态，第2个位表示a变量的状态。
在visitBinary()方法中，当执行完case分支为JCTree.AND的scanCond(tree.lhs)语句后，4个条件状态变量的值如下
initsWhenTrue=10
uninitsWhenTrue=01
initsWhenFalse=10
uninitsWhenTrue=01
最终求得的inits=10、uninits=01。
在visitBinary()方法中，当执行完case分支为JCTree.AND的scanCond(tree.rhs)语句后，4个条件状态变量的值如下
initsWhenTrue=11
uninitsWhenTrue=01
initsWhenFalse=11
uninitsWhenTrue=01
在visitIf()方法中，在分析tree.thenpart之前，会将inits与uninits初始化为initsWhenTrue和uninitsWhenTrue，所以inits=11、uninits=01
在执行实例14-12的初始化表达式int b=a时，变量a是明确初始化的变量，可出现在初始化表达式的右侧，因此实例14-12能够正常编译。
 */
public class Test12 {
    public void test(boolean res) {
        int a;
        if (res && (a = 1) == 1) {
            int b = a;
        }
    }
}
