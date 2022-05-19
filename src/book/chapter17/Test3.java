package book.chapter17;

public class Test3 {
    public void test(int num) {
        switch (num) {
            case 0:
            case 2:
            case 3:
                num = -1;
        }
    }
    /*
    lo为0，hi为3，由于分支中的整数不连续，因此添加了一个label为2的case分支，
    最终switch语句生成的字节码指令如下
0: iconst_0
1: istore_1
2: iload_1
3: tableswitch { // 0 to 3
       0: 32
       1: 34
       2: 32
       3: 32
       default: 34
}
32: iconst_m1
33: istore_1
34: return

新增label为2的case分支的跳转地址与默认分支的跳转地址一样，符合switch语句的执行语义。


     */
}
