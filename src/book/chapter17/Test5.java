package book.chapter17;


class FirstExc5 extends Exception {
}

class SecondExc5 extends Exception {
}

/*
通过try语句来捕获抛出的异常，因此在生成try语句的字节码指令时还会生成异常表（ExceptionTable），通过异常表来处理异常
 */
public class Test5 {
    void tryItOut() throws FirstExc5, SecondExc5 {
    }

    void handleFirstExc(Object o) {
    }

    void handleSecondExc(Object o) {
    }

    void wrapItUp() {
    }

    public void catchExc() {
        try {
            tryItOut();
        } catch (FirstExc5 e) {
            handleFirstExc(e);
        } catch (SecondExc5 e) {
            handleSecondExc(e);
        } finally {
            wrapItUp();
        }
    }
    /*
catchExc()方法生成的字节码指令及异常表的详细信息如下：
0: aload_0
1: invokevirtual #2         // Method tryItOut:()V
4: aload_0
5: invokevirtual #3         // Method wrapItUp:()V
8: goto     44
11: astore_1
12: aload_0
13: aload_1
14: invokevirtual #5        // Method handleFirstExc:(Ljava/lang/Object;)V
17: aload_0
18: invokevirtual #3        // Method wrapItUp:()V
21: goto     44
24: astore_1
25: aload_0
26: aload_1
27: invokevirtual #7        // Method handleSecondExc:(Ljava/lang/Object;)V
30: aload_0
31: invokevirtual #3        // Method wrapItUp:()V
34: goto     44
37: astore_2
38: aload_0
39: invokevirtual #3        // Method wrapItUp:()V
42: aload_2
43: athrow
44: return
Exception table:
from  to    target  type
  0   4     11      Class chapter17/FirstExc
  0   4     24      Class chapter17/SecondExc
  0   4     37      any
  11  17    37      any
  24  30    37      any
  37  38    37      any

  生成finally语句的字节码指令的编号范围为37~44，其中包括保存栈顶异常到本地变量表，
  执行完finally语句的字节码指令后抛出异常。在执行各个catch语句时也可能抛出异常，
  而这里异常最终都会存储到栈顶，等待finally进行重抛。
     */
}
