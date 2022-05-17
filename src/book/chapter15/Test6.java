package book.chapter15;

public class Test6 {
    public void test(Integer[] array) {
        for (Integer i : array) {
            System.out.println(i);
        }
    }

    /*
    增强for循环解语法糖
     */
    public void test1(Integer[] array) {
        for (Integer len$ = array.length, i$ = 0; i$ < len$; ++i$) {
            Integer i = array[i$];
            {
                System.out.println(i);
            }
        }
    }
}
