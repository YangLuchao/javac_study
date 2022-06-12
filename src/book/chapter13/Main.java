package book.chapter13;

import javax.tools.ToolProvider;

public class Main {
    public static void main(String[] args) {
        test10();
    }

    private static void test(String path, String d){
        javax.tools.JavaCompiler compiler =
                ToolProvider.getSystemJavaCompiler();
        int result = compiler.run(null, null, null,
                new String[]{
                        "-d", d,
                        path
                }
        );
        System.out.println("Result code: " + result);
    }

    private static void test10(){
        test("/Users/yangluchao/Documents/GitHub/javac_study/src/book/chapter13/Test10.java",
                "/Users/yangluchao/Documents/GitHub/javac_study/src/book/chapter13/out/test10");
    }

    private static void test11(){
        test("/Users/yangluchao/Documents/GitHub/javac_study/src/book/chapter13/Test11.java",
                "/Users/yangluchao/Documents/GitHub/javac_study/src/book/chapter13/out/test11");
    }
}
