package book.chapter11;

/*
在对变量b的初始化表达式chapter11.Test.a进行标注时，
首先调用resolveIdent()方法查找chapter11的符号引用，
resolveIdent()方法会调用当前的findIdent()方法，传递的参数kind的值为PCK|TYP|VAL，
表示chapter11可能为包名、类型名或者变量名
 */
public class Test7 {
    static int a = 1;
    int b = book.chapter11.Test7.a;
}
