package book.chapter10;

/*
checkTransparentClass()方法针对如上情况进行了检查，
因为相同作用域内的唯一性检查已经由checkUniqueClassName()方法完成，
所以方法从s.next这个作用域开始检查，也就是从定义当前类型作用域的上一个作用域开始查找。
当前方法对每一个找到的符号都要判断所属的符号是变量还是方法，保证查找到的是一个本地类。
 */
class Test8 {
    public void test() {
        class CA8 {
        }
        {
//            class CA8 {
//            } // 报错，已在方法 test()中定义了类 CA
        }
    }
}