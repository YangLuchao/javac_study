package book.chapter15;

public class Test13 {
}

enum Fruit13 {
    APPLE, ORANGE
}
/*
解语法糖
 */
//enum Fruit extends Enum<Fruit13> {
//    private <init>(/*synthetic*/
//    String $enum$name, /*synthetic*/ int
//    $enum$ordinal)
//
//    {
//        super($enum$name, $enum$ordinal);
//    }
//
//    /*public static final*/ APPLE /* = new Fruit("APPLE", 0) */
//            /*public static final*/ ORINGE /* = new Fruit("ORINGE", 1) */
//    /*synthetic*/ private static final Fruit13[] $VALUES = new Fruit13[]
//            {Fruit13.APPLE, Fruit13.ORINGE}
//
//    public static Fruit13[] values() {
//        return (Fruit13[]) $VALUES.clone();
//    }
//
//    public static Fruit13 valueOf(String name) {
//        return (Fruit13) Enum.valueOf(Fruit13.class, name);
//    }
//}