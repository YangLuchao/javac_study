package book.chapter9;

/*
当一些运算符对操作数应用二元数字提升时，每个操作数对应的值必须能够通过如下两个步骤转换为一个数字类型
1：如果任何一个操作数是引用类型，那么要引用类型拆箱转换
2：使用如下规则来应用基本类型宽化转换：
    1.当其中任何一个操作数的类型为double时，则另外一个操作数类型也转换为double；
    2.当其中任何一个操作数的类型为float时，则另外一个操作数的类型也转换为float；
    3.当其中任何一个操作数的类型为long时，则另外一个操作数的类型也转换为long；
    4.两个操作数都转换为int类型。
二元数字提升作用在一些特定运算符的操作数上，这些运算符如下：
（1）乘法运算符“*”、除法运算符“/”或者取模运算符“%”；
（2）加法运算符“+”或减法运算符“-”；
（3）使用比较运算符比较数字的运算符，包括“<”、“<=”、“>”或者“>=”；
（4）使用比较运算符比较数字的运算符“==”或者“!=”；
（5）使用位运算符操作数字的运算符，包括“&”、“^”或者“|”；
（6）在特定情况下的三元运算符“?:”。
 */
class Test29 {
    public void test() {
        int i = 0;
        float f = 1.0f;
        double d = 2.0;
        // 首先将int*float提升为float*float
        // 然后将float==double提升为double==double
        if (i * f == d) ;

        // 将char&byte提升为int&int
        byte b = 0x1f;
        char c = 'G';
        int control = c & b;

        // 将int:float提升为float:float
        f = (b == 0) ? i : 4.0f;
    }
}