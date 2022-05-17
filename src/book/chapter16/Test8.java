package book.chapter16;

// 如果一个类没有明确声明构造方法，则Javac会添加一个默认构造方法
public class Test8 {
//    public <init>(){
//        super();
//    }
    /*
    为默认的构造方法生成的字节码指令如下：

0: aload_0
1: invokespecial #1 // Method java/lang/Object."<init>":()V
4: return
“super()”语句的语法树结构如图16-7所示。

     */
}
