package book.chapter15;

import java.util.List;

public class Test7 {
    public void test(List<String> list) {
        for (String str : list) {
            System.out.println(str);
        }
    }

    /**
     * 容器类型的增强for循环解语法糖
     * @param list
     */
    public void test1(List<String> list) {
        for (java.util.Iterator i$ = list.iterator(); i$.hasNext(); ) {
            String str = (String) i$.next();
            {
                System.out.println(str);
            }
        }
    }
}
