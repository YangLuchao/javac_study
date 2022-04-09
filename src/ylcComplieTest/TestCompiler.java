package ylcComplieTest;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;

public class TestCompiler {

    Integer a;

    TestCompiler() {
        Class a = Void.class;
        Class b = void.class;
        Integer c = TestCompiler.this.a;
//        TestCompiler.super
    }

    TestCompiler(Integer i) {
        this(i, i);
    }

    TestCompiler(Integer i, Integer a) {
        this();
    }

    public static void main(String args[]) throws
            IOException {
        String path =
                "/Users/yangluchao/projects/JavaComplier/src/ylcComplieTest/TestCompiler.java";
        javax.tools.JavaCompiler compiler =
                ToolProvider.getSystemJavaCompiler();
        int result = compiler.run(null, null, null,
                new String[]{
                        "-d", "/Users/yangluchao/projects/JavaCompiler/save",
                        path
                }
        );
        System.out.println("Result code: " + result);
    }

    public <T extends TestCompiler & JavaCompiler> void ai(T t) {

    }
}