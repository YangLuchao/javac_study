package book.chapter11;

public class Test6 {
}

interface IA6 {
    class MemberClass6 {
    }
}

class CA6 {
    class MemberClass6 {
    }
}

class CB6 extends CA6 implements IA6 {
    // 报错，对MemberClass的引用不明确
    // CA中的类 chapter11.CA.MemberClass和IA中的类 chapter11.IA.MemberClass都匹配
//    MemberClass6 mc;
}