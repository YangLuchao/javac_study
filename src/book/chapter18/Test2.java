package book.chapter18;

public class Test2 {
    /*
    描述符为：
    (JI)V
     */
    void wait1(long timeout, int nanos) {
    }

    /*
    描述符为
    (ZILjava/lang/String;II)Z
     */
    boolean regionMatches(boolean ignoreCase, int toOffset,
                          String other, int offeset, int len) {
        return false;
    }
    /*
    描述符（Descriptor）是一个描述字段或方法类型的字符串
    对于字段来说，其描述符只描述类型即可
    方法的描述符要相对复杂一些，描述符中要描述参数列表中的参数数量、参数类型、参数顺序及返回值等信息
    （1）基本数据类型
        Test26BaseType字符解释表.png
    （2）对象类型
        对象类型ObjectType中的ClassName表示一个类或接口的二进制名称的内部形式，例如chapter18.TestClass被描述为“Lchapter18/TestClass;”
     （3）数组类型
        数组类型通过前置“[”来描述，例如，chapter18.TestClass[]一维数组被描述为“[Lchapter18/TestClass;”，二维数组int[][]被描述为“[[I”。
    返回值描述符ReturnDescriptor中的V表示void类型，即方法无返回值。
     */
}
