package book.chapter14;

class FirstExc21 extends Exception {
}

class FirstSubExc21 extends FirstExc21 {
}

class SecondExc21 extends Exception {
}

/*
不同位置对应的thrown与caught列表 如图所示
if语句的条件判断表达式调用chk.isUnchecked()方法确保要处理的是受检查的异常，
调用chk.isHandled()方法确保异常没有被处理过，这里所说的处理，是指异常被捕获或者在方法上声明抛出。
 */
public class Test21 {
    public void rethrowExceptionSE7(String exc) throws FirstExc21 {
        // 第1个try语句
        // 位置1
        try {
            // 第2个try语句
            try {
                if (exc.equals("FirstExc21")) {
                    throw new FirstExc21();
                }
                if (exc.equals("SecondExc21")) {
                    throw new SecondExc21();
                }
                // 位置2
            } catch (FirstExc21 e) {
                if (exc.equals("FirstExc21")) {
                    throw new FirstSubExc21();
                }
                // 位置3
            }
        } catch (SecondExc21 e) {
            // 位置4
        }
    }
}
