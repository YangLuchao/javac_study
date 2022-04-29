package book.chapter9;

import java.util.Vector;

public class Test16 {
    /*
    当调用isAssignable()方法判断参数化类型Vector<String>是否可以赋值给Vector<? extends Object>类型时，
    会间接调用到当前的visitClassType()方法，进而调用containsTypeRecursive()方法来判断，
    传递的参数s为Vector<? extends Object>，sup为Vector<String>。
    这样，在containsType()方法中会判断s的实际类型参数的类型“? extends Object”是否包含String类型。
    containsTypeRecursive()方法最终返回true，表示“? extends Object”类型包含String类型。
    isAssignable()方法最终也会返回true，实例的初始化表达式正确。
     */
    Vector<? extends Object> vec = new Vector<String>();
}
