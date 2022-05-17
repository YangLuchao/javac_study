package book.chapter12;

/*
对Integer类型的变量a进行自增操作，在查找运算符的OperatorSymbol对象时，
由于OperatorSymbol对象在创建时只接收基本类型的参数，
所以在允许类型拆箱转换时查找到名称为“++”、参数类型为int、返回类型为int的OperatorSymbol对象。
由于“a++”表达式的类型为Integer，所以应该取tree.arg.type，而不是OperatorSymbol对象的返回类型int
 */
public class Test1 {
    public void test() {
        Integer a = 1;
        a++;
    }
}
