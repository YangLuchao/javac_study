package book.chapter14;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.Reader;
class MyExc22 extends RuntimeException{}

/*
当visitTry()方法分析第2个try语句并且已经运行完第一部分代码实现时，caughtPrev列表中有MyExc类，
caught列表中有MyExc与IOException类，thrownPrev列表为空。
当开始处理第2个try语句之前，当前能够捕获到的异常保存在caughtPrev列表中，这个列表中只含有第1个try语句能够捕获的MyExc类。
在分析第2个try语句的body体时，如果已经运行完visitTry()方法的第一部分代码，则能够捕获异常的caught列表中含有的MyExc与IOException类，thrownPrev列表为空
visitTry()方法中调用的chk.incl()方法将之前能够捕获处理的所有异常，加上内层能够捕获处理的异常保存到caught列表中，这样当内层try语句body体中抛出异常时，
就可以从caught列表中检查这个异常是否能够被捕获处理，实际上markThrown()方法也是这么做的。
 */
public class Test22 {
    public void test(Reader file) {
        // 第1个try语句
        try {
            // 第2个try语句
            try (BufferedReader br = new BufferedReader(file);) {
                throw new MyExc22();
            } catch (IOException e) {
                e.printStackTrace();
            }
        } catch (MyExc22 e) {
            e.printStackTrace();
        }
    }
}
