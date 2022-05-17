package book.chapter15;

public class Test9 {
    public void testStringSwitch(String fruit) {
        switch (fruit) {
            case "banana":
            case "apple":
                System.out.println("banana or orange");
                break;
            case "orange":
                System.out.println("orange");
                break;
            default:
                System.out.println("default");
                break;
        }
    }

    /*
    解语法糖
     */
    public void testStringSwitch1(String fruit) {
        /*synthetic*/ final String s99$ = (fruit);
        /*synthetic*/ int tmp99$ = -1;
        switch (s99$.hashCode()) {
            case -1396355227:
                if (s99$.equals("banana")) tmp99$ = 0;
                break;
            case 93029210:
                if (s99$.equals("apple")) tmp99$ = 1;
                break;
            case -1008851410:
                if (s99$.equals("orange")) tmp99$ = 2;
                break;
        }
        switch (tmp99$) {
            case 0:
            case 1:
                System.out.println("banana or orange");
                break;
            case 2:
                System.out.println("orange");
                break;
            default:
                System.out.println("default");
                break;
        }
    }
}
