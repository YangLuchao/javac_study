package book.chapter9;

interface IA22 {
}

interface IB22 extends IA22 {
}

final class CA22 implements IA22 {
}
/*

 */
public class Test22 {
    public void test(IB22 a) {
        Object o = (CA23) a; // 运行时报错，不可转换的类型
    }

    public static void main(String[] args) {
        Test22 test22 = new Test22();
        test22.test(new IB22() {});
    }
}