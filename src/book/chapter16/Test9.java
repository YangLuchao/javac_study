package book.chapter16;

public class Test9 {
    public int md(int[] arr, int a) {
        return arr[a]++;
    }
    /*
    md()方法的字节码指令如下：
 
0: aload_1      // 加载本地第一个变量
1: iload_2      // 加载本地第二个变量
2: dup2         // 调用IndexedItem类中的duplicate()方法复制栈顶1个或2个值并插入栈顶，复制第一第二个变量
3: iaload       // 调用IndexedItem类中的load()方法从数组中加载一个int类型数据到操作数栈 ，加载指定int类型的变量
4: dup_x2       // 调用IndexedItem类中的stash()方法复制操作数栈栈顶的值，并插入栈顶以下2个或3个值之后，
5: iconst_1     // 加载常量1
6: iadd         // 相加
7: iastore      // 调用IndexedItem类中的store()方法将操作数栈顶的数据存入数组中
8: ireturn      // 返回i

md()方法中的“return arr[a]++”语句对应的语法树结构如图16-8所示
     */
}
