package book.chapter8;

import javax.tools.ToolProvider;
import java.io.IOException;

public class RunNameCheckProcessor {
    public static void main(String args[]) throws IOException {
        javax.tools.JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        int results = compiler.run(null, null, null, new String[]{
                "-processor", "book.chapter8.NameCheckProcessor",
                "-processorpath", "/Users/yangluchao/Documents/GitHub/javac_study/src/book/chapter8",
                "-d", "/Users/yangluchao/Documents/GitHub/javac_study/save/ylcComplieTest",
                "/Users/yangluchao/Documents/GitHub/javac_study/src/book/chapter8/TEST_2.java"
        });
        System.out.println("Result code: " + results);
    }
}
