package book.chapter17;

public class Test10 {
    /*
    lookupswitch指令根据键值在跳转表中寻找配对的分支并跳转
    如图
    这是一条变长指令并且要求所有的操作数都以4字节对齐，因此紧跟在lookupswitch指令之后可能会有0～3个字节作为空白填充，而后面的default、npairs等都用4字节来表示
    从当前方法开始（第一条字节码指令）计算的地址，即紧随空白填充的是一系列32位有符号整数值，包括默认跳转地址default、匹配坐标的数量npairs及npairs组匹配坐标。
    其中，npairs的值应当大于或等于0，每一组匹配坐标都包含了一个整数值match及一个有符号32位偏移量offset。
    上述所有的32位有符号数值都是通过以下方式计算得到：
        (byte1<<24)|(byte2<<24)|(byte3<<24)|byte4

    ableswitch指令是一条变长指令并且要求所有的操作数都以4字节对齐，因此紧跟在lookupswitch指令之后可能会有0～3个字节作为空白填充
    而后面的default、lowbyte、highbyte等用4字节来表示，从当前方法开始（第一条字节码指令）计算的地址
    即紧随空白填充的是一系列32位有符号整数值，包括默认跳转地址default、高位值high及低位值low
    在此之后是high-low+1个有符号32位偏移offset。
    上述所有的32位有符号数值都是通过以下方式计算得到：
        (byte1<<24)|(byte2<<24)|(byte3<<24)|byte4
     */
}
