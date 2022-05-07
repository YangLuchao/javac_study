package book.chapter11;

import static book.chapter11.Parent14.a;

/*
对Test类中变量b的初始化表达式来说，通过Sub类访问了Parent类中定义的静态变量a，
但是直接通过Parent类不能访问静态变量a，因为没有public修饰的Parent类在chapter11包下访问不到。
为了能访问到静态变量a，需要在对语法树节点JCIdent(name=a)进行符号标注时，
将VarSymbol(name=a)对象的owner变量的值替换为ClassSymbol(name=Sub)对象
如果从当前编译单元的namedImportScope中没有查找到合适的符号，
会从当前编译单元的starImportScope中查找，同样在某些情况下需要替换sym的owner值
 */
public class Test14_2 {
    int b = a;
}