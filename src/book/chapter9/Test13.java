package book.chapter9;

/*
t变量声明的类型Test并不是裸类型，因为在定义Test时并没有声明任何类型参数。isRaw()方法中的this等于tsym.type，因此方法直接返回false
 */
public class Test13 {
     Test13 t;
}
