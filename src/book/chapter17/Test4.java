package book.chapter17;

public class Test4 {
    public void test(Exception e) throws Exception {
        throw e;
    }
    /*
方法test()生成的字节码指令如下：
0: aload_1 
1: athrow     
     */
}
