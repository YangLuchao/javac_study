package book.chapter11;

/*
在对局部变量var进行初始化时引用了a，调用findIdent()方法确定名称为a的引用，
传递的参数kind的值为VAL，因为根据a使用的上下文可知，
这是个变量或常量（包和类型不可能以简单名称的方式出现在赋值表达式的右侧），
方法最后确定名称a是对变量a的引用
 */
public class Test8 {
    int a = 1;

    public void test() {
        int var = a; // 引用成员变量a的值进行初始化
    }

    class a {
    }
}
