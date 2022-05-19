package book.chapter18;

public class Test22 {
    /*
    类访问标识符
    当前类的访问标识access_flags用2个字节来表示，2个字节共有16个位，每个位都可以代表一个标志，16个标志位中的8个代表了这个类的一些性质，其余的8个未用到的标志位一律置为0
    如图Test22
    具有相同含义的标志值与Flags类中预定义的常量名称对应的常量值基本一致，因此可以直接使用Symbol对象的flags_field变量中保存的值
    com.sun.tools.javac.code.Flags
    需要注意的是，在Flags类中常量值0x0020表示的是SYNCHRONIZED，而对于类来说，flags_field中含有的应该是ACC_SUPER，而ACC_SUPER又不能在Java源代码中显式标注，因此Javac在写入时会给每个类添加ACC_SUPER。
    writeClassFile()方法对标志处理相关的实现代码:
    com.sun.tools.javac.jvm.ClassWriter
     */
}
