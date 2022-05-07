package book.chapter11;

public class Test5 {
}

class CA5 {
    private class MemberClass {
    }
}

class CB5 extends CA5 {
//    MemberClass a; // 报错，CA.MemberClass可以在CA中访问private
}