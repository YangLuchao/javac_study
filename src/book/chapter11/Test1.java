package book.chapter11;

public class Test1 {
}

/*
在md()方法中定义变量b时使用a变量的值进行初始化，
但是从a使用的当前上下文环境出发可访问到的名称为a的变量有多个，
如局部变量、成员变量与父类中各定义了一个名称为a的变量
所以需要通过符号来确定唯一的引用
 */
class Parent {
    int a = 1;
}

class Sub extends Parent14 {
    int a = 2;

    public void md() {
        int a = 3;
        int b = a; // 使用局部变量a的值进行初始化
    }
}