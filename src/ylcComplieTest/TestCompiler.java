package ylcComplieTest;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.util.List;

public class TestCompiler {

    public static void main(String args[]) throws
            IOException {
        String path =
                "/Users/yangluchao/Documents/GitHub/javac_study/src/ylcComplieTest/TestCompiler.java";
        javax.tools.JavaCompiler compiler =
                ToolProvider.getSystemJavaCompiler();
        int result = compiler.run(null, null, null,
                "-d", "/Users/yangluchao/Documents/GitHub/javac_study/save", path);
        System.out.println("Result code: " + result);
    }

    /**
     * 中序表达式：(a + b) * c / (d - e) ^ f
     * 后序表达式：a b + c * d e - f ^ /
     */
    public int postfix(int a, int b, int c, int d, int e, int f){
        return  (a + b) * c / (d - e) ^ f;
    }

    public Integer whatIsNull(List nul) {
        nul.add(new Object());
        return null;
    }

}