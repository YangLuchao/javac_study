package book.chapter14;

class FirstExc extends Exception {
}

class SecondExc extends Exception {
}

/*
在try语句的body体内抛出了FirstExc与SecondExc异常，
虽然catch语句对FirstExc异常进行了捕获，对未捕获的SecondExc异常在方法上也进行了声明，
但是catch语句的body体内又对FirstExc异常进行了重抛，所以方法上仍然需要声明FirstExc异常，
编译报错，报错摘要为“未报告的异常错误FirstExc；必须对其进行捕获或声明以便抛出”
 */
public class Test8 {
    public void test(String exc) throws SecondExc {
        try {
            if (exc.equals("FirstExc")) {
                throw new FirstExc();
            } else {
                throw new SecondExc();
            }
        } catch (FirstExc e) {
            // 报错，未报告的异常错误FirstExc; 必须对其进行捕获或声明以便抛出
//            throw e;
        }
    }
}
