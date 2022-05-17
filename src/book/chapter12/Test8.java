package book.chapter12;

/*
a与b变量的初始化部分都是JCFieldAccess树节点，
其中selected是名称为Test的JCIdent树节点，而Test引用的符号一定为TYP。
 */
public class Test8 {
    Object a = Test8.class;
    Object b = Test8.this.a;
}
