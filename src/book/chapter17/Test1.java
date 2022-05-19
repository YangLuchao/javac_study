package book.chapter17;

public class Test1 {
    public void test(int i, int j) {
        while (i == j) {
            i = 1;
        }
    }
    /*
    方法的字节码指令如下:

0: iload_1
1: iload_2
2: if_icmpne   10// 当且仅当i!=j时跳转
5: iconst_1
6: istore_1
7: goto     0
10: return

当while语句的条件判断表达式的值为true时，判断i是否等于j时最先选取的指令是if_icmpeq
Javac会调用negate()方法取与if_icmpeq逻辑相反的指令if_icmpne，
也就是条件判断表达式的值为false时要跳到while语句之后继续执行。
     */
}
