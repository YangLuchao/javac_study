package book.chapter10;

class Test7 {
    class CA7 {
    } // 第1个类

    {
        class CA7 {
        } // 第2个类
        CA7 a; // CA引用第2个类
    }

    public void test(CA7 b) { // CA引用第1个类
        class CA7 {
        } // 第3个类
        CA7 c;// CA引用第3个类
    }
}