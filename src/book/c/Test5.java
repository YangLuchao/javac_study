package book.c;

interface IA{ }
class CA { }
public class Test5 {
    public <T1 extends T2,T2 extends CA&IA> void test(){ }
}