package book.chapter14;

class FirstExc20 extends Exception {
}

class SecondExc20 extends Exception {
}

public class Test20 {
    public void rethrowExceptionSE7(String exc) throws FirstExc, SecondExc {
        try {
            if (exc.equals("FirstExc")) {
                throw new FirstExc();
            } else {
                throw new SecondExc();
            }
        } catch (Exception e) {
            // 因为try语句的body体内只可能抛出这两种受检查的异常，所以这种语法叫增强型throws声明
            throw e;
        }
    }
}