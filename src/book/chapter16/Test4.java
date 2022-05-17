package book.chapter16;

public class Test4 {
    public void md() {
        int i = 1;
        int j = 100;
        int k = 100000;

        double a = 1;
        double b = 100;
        double c = 100000;
    }
    /*
    局部变量的初始化表达式都为常量值，因此使用ImmediateItem对象表示，
    调用这个对象的load()方法加载这些常量值到操作数栈时会选取不同的指令，
    最终md()方法的字节码指令如下:

0: iconst_1         // 调用ImmediateItem类的load()方法加载整数1,将int型1推送至栈顶
1: istore_1         // 将栈顶int型数值存入第二个本地变量
2: bipush    100    // 调用ImmediateItem类的load()方法加载整数100,将单字节的常量值(-128~127)推送至栈顶
4: istore_2         // 将栈顶int型数值存入第三个本地变量
5: ldc     #2       // 调用ImmediateItem类的load()方法加载整数100000,com.sun.tools.javac.jvm.Items.ImmediateItem.ldc()方法，将大数放入常量池中，然后使用ldc2w、ldc1或者ldc2指令将常量值推送到栈顶
7: istore_3         // 将栈顶int型数值存入第四个本地变量
8: dconst_1         // 调用ImmediateItem类的load()方法加载浮点数1,将double型1推送至栈顶
9: dstore    4      // 将栈顶double数值存入第五个本地变量
11: ldc2_w   #3     // 调用ImmediateItem类的load()方法加载浮点数100.0d，
14: dstore    6     // 将栈顶double数值存入第六个本地变量
16: ldc2_w   #5     // 调用ImmediateItem类的load()方法加载浮点数100000.0d
19: dstore    8     //
21: return

当加载整数1时使用iconst_1指令，加载整数100时使用sipush指令，加载整数100000时使用ldc指令。
当加载双精度浮点类型的1时使用dconst_1指令，加载双精度浮点类型的100和100000时使用ldc2_w指令。
     */
}
