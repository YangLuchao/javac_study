package book.chapter12;

/*
对变量n的初始化表达式进行标注，初始化表达式对应的语法树如图:初始化表达式的语法树.png所示
调用Attr类的visitVarDef()方法对变量n的JCVariableDecl树节点进行标注时，会调用attribExpr()方法对变量的初始化表达式进行标注
v.type表示int类型的Type对象，这是对tree.init的类型期望。
由于tree.init为JCFieldAccess树节点，所以调用Attr类的visitSelect()方法进行标注。
如图：Test9_1标注初始化表达式.png 给出了标注变量n的初始化表达式的各个项所调用的方法，以及对各个项的符号和类型期望
可以看到，最后调用visitIdent()方法标注JCIdent(name=compile)，
而JCIdent(name=compile)也是首先完成标注的树节点，
之后才会依次完成对JCFieldAccess(name=Music)、
JCFieldAccess(name=wizards)与JCFieldAccess(name=length)的标注，
每次完成一项的标注后，就会返回实际的类型
对于visitSelect()方法来说，通过局部变量site接收到tree.selected的实际类型后，调用TreeInfo.symbol()方法获取符号
 */
public class Test9_1 {
    static int n = book.chapter12.Music.wizards.length;
}
/*
调用Attr类的visitVarDef()方法对变量n的JCVariableDecl树节点进行标注时，会调用attribExpr()方法对变量的初始化表达式进行标注
来源：com.sun.tools.javac.comp.Attr
VarSymbol v = tree.sym;
attribExpr(tree.init, _, v.type);
对于实例12-9来说，v.type表示int类型的Type对象，这是对tree.init的类型期望。
由于tree.init为JCFieldAccess树节点，所以调用Attr类的visitSelect()方法进行标注。
如表12-3中给出了标注变量n的初始化表达式的各个项所调用的方法，以及对各个项的符号和类型期望
表12-3中“方法名称”一列从上到下表示按顺序调用Attr类的visitSelect()或visitIdent()方法，对变量初始化表达式中的各个项进行标注
可以看到，最后调用visitIdent()方法标注JCIdent(name=compile)，
而JCIdent(name=compile)也是首先完成标注的树节点，
之后才会依次完成对JCFieldAccess(name=Music)、
JCFieldAccess(name=wizards)与JCFieldAccess(name=length)的标注，
每次完成一项的标注后，就会返回实际的类型。
对于visitSelect()方法来说，通过局部变量site接收到tree.selected的实际类型后，
调用TreeInfo.symbol()方法获取符号
 */