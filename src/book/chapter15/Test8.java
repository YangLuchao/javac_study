package book.chapter15;


public class Test8 {
    public void testEnumSwitch(Fruit8 fruit) {
        switch (fruit) {
            case APPLE:
                System.out.println("apple");
                break;
            case ORANGE:
                System.out.println("orange");
                break;
            default:
                System.out.println("unknow");
        }
    }
}
enum Fruit8 {
    APPLE, ORANGE
}
// 对Fruit8解语法糖
//enum Fruit extends Enum<Fruit>{
//    private <init>(/*synthetic*/ String $enum$name, /*synthetic*/ int
//    $enum$ordinal) {
//        super($enum$name, $enum$ordinal);
//    }
//    /*public static final*/ APPLE /* = new Fruit("APPLE", 0) */
//            /*public static final*/ ORINGE /* = new Fruit("ORINGE", 1) */
//    /*synthetic*/ private static final Fruit[] $VALUES = new Fruit[]
//            {Fruit.APPLE, Fruit.ORINGE}
//    public static Fruit[] values() {
//        return (Fruit[])$VALUES.clone();
//    }
//    public static Fruit valueOf(String name) {
//        return (Fruit)Enum.valueOf(Fruit.class, name);
//    }
//}

// 对Test解语法糖
///*synthetic*/ class Test$1 {
//    /*synthetic*/ static final int[] $SwitchMap$chapter15$Fruit = new int
//            [Fruit8.values().length];
//    static {
//        try {
//            book.chapter15.Test$1.$SwitchMap$cp$Fruit[Fruit8.APPLE.ordinal()] = 1;
//        } catch (NoSuchFieldError ex) { }
//        try {
//            book.chapter15.Test$1.$SwitchMap$cp$Fruit[Fruit8.ORANGE.ordinal()] = 2;
//        } catch (NoSuchFieldError ex) { }
//    }
//}
//switch (chapter15.Test$1.$SwitchMap$chapter15$Fruit[(fruit).ordinal()]) {
//        case 1:
//        System.out.println("apple");
//        break;
//        case 2:
//        System.out.println("orange");
//        break;
//default:
//        System.out.println("unknow");
//        }
