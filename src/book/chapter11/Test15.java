package book.chapter11;

/*
1:调用resolveMethod()方法查找方法调用表达式md(p)调用的具体方法，
    传递的name参数的值为md，argtypes列表中含有一个ClassType对象，表示的是Integer类型，
    而typeargtypes列表为空。resolveMethod()方法最后返回一个表示md()方法的MethodSymbol对象。
2:查找方法调用表达式Test.<String>md(p)调用的方法时会调用resolveQualifiedMethod()方法，
    传递的name参数的值为md，argtypes列表中含有一个ClassType对象，表示的是Integer类型，
    而typeargtypes列表也含有一个ClassType对象，表示的是String类型，
    location是表示Test类的ClassSymbol对象，site是表示Test类的ClassType对象。
    resolveQualifiedMethod()方法最后返回一个表示md()方法的MethodSymbol对象。
 */
public class Test15 {
    public static <T> void md(Integer a) {
    }

    public void test(Integer p) {
        md(p);
        Test15.<String>md(p);
    }
}
