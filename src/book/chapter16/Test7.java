package book.chapter16;

public class Test7 {
    int a = 1;

    public void md() {
        a = a + 1;
    }
    /*
在md()方法中对实例变量a执行加1操作，生成的字节码指令如下:
0: aload_0          // 加载 this
1: aload_0          // 加载 this
2: getfield   #2    // Field a:I
5: iconst_1         // 加载1
6: iadd             // 相加
7: putfield   #2    // Field a:I 放入类
10: return
“a=a+1”语句的语法树结构如图16-6所示。
     */
}
