package book.chapter10;

/*
MemberClassA继承MemberClassB时不会报错，
但是LocalClassA继承LocalClassB时会报错，因为LocalClassB是本地类并且定义在LocalClassA之后，
所以如果使用在块内的定义，则定义必须在使用之前。
在分析LocalClassA时，baseEnv()方法会将本地类输入baseScope中，
而LocalClassB因为还没有输入到env.outer.info.scope中，
所以最终的baseScope中不含有LocalClassB，这样在分析LocalClassA的父类LocalClassB时，
由于找不到名称为LocalClassB的符号而报错，报错摘要为“找不到符号”。
 */
class Test1 {
    class MemberClassA extends MemberClassB {
    }

    class MemberClassB {
    }

    public void test() {

//        test class LocalClassA extends LocalClassB {
//        }// 报错，找不到符号

        class LocalClassB {
        }
    }
}