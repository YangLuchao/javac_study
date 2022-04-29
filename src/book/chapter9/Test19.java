package book.chapter9;

public class Test19{}

interface IA19<T>{ }
class T19 implements IA19<String> { }
/*
如果S为类，则泛型擦写后的两个类型必须有父子关系，如果没有父子关系，
则T或者继承T实现的子类都不能转换为S，因为不可能有一个子类同时继承T和S
同一个接口IA的不同参数化类型IA<String>与IA<Integer>分别被T与S所实现，
因为IA<String>与IA<Integer>是两个完全不同的类型，所以T不能强制转换为S
 */
//class S extends T19 implements IA19<Integer>{ }