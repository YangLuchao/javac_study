package book.chapter15;

public class Test16 {
}

/*
由于本地类Local中有个私有构造方法，所以如果单独作为一个类时，
在md()方法中创建Local对象将无法访问私有构造方法。
这里会将private修饰符去掉，这样就能正常访问Local类的私有构造方法了。
 */
class Outer16 {
    public void md() {
        class Local16 {
            private Local16() {
            }
        }
        new Local16();
    }
}