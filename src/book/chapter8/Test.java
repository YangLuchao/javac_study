package book.chapter8;

import javax.tools.ToolProvider;

public class Test{
  public static void main(String[] args) {
    javax.tools.JavaCompiler compiler = ToolProvider.getSystemJavaCompiler(); 
    int results = compiler.run(null, null, null, new String[]{
            // -processor命令指定具体的注解处理器类
            "-processor","book.chapter8.GetSetProcessor",
            // -processorpath命令指定搜索注解处理器的路径
            "-processorpath","/Users/yangluchao/Documents/GitHub/javac_study/src/book/chapter8",
            // 运行后会根据/Users/yangluchao/Documents/GitHub/javac_study/src/book/chapter8/TestAnnotation.java
            // 在“/Users/yangluchao/Documents/GitHub/javac_study/save/ylcComplieTest”路径下
            // 生成TestAnnotation.class类
            "-d","/Users/yangluchao/Documents/GitHub/javac_study/save/ylcComplieTest",
            "/Users/yangluchao/Documents/GitHub/javac_study/src/book/chapter8/TestAnnotation.java",
    });
  }
}