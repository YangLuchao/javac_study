package book.chapter15;

import java.io.*;

public class Test12 {
    public void testTryWithResources(FileReader a, FileOutputStream b) throws
            IOException {
        try (
                BufferedReader br = new BufferedReader(a);
                PrintStream ps = new PrintStream(b)
        ) {
            System.out.println(br.readLine());
        }
    }

    /**
     * 解语法糖
     *
     * @param a
     * @param b
     * @throws IOException
     */
    public void testTryWithResources1(FileReader a, FileOutputStream b) throws
            IOException {
        final BufferedReader br = new BufferedReader(a);
        /*synthetic*/
        Throwable primaryException0$ = null;
        try (PrintStream ps = new PrintStream(b)) {
            System.out.println(br.readLine());
        } catch (/*synthetic*/ final Throwable t$) {
            primaryException0$ = t$;
            throw t$;
        } finally {
            if (br != null)
                if (primaryException0$ != null)
                    try {
                        br.close();
                    } catch (Throwable x2) {
                        primaryException0$.addSuppressed(x2);
                    }
                else br.close();
        }
    }

    /*
    递归解语法糖
    多个资源变量按照声明的先后顺序形成嵌套结构。
     */
    public void testTryWithResources2(FileReader a, FileOutputStream b) throws
            IOException {
        final BufferedReader br = new BufferedReader(a);
        /*synthetic*/
        Throwable primaryException0$ = null;
        try {
            final PrintStream ps = new PrintStream(b);
            /*synthetic*/
            Throwable primaryException1$ = null;
            try {
                System.out.println(br.readLine());
            } catch (/*synthetic*/ final Throwable t$) {
                primaryException1$ = t$;
                throw t$;
            } finally {
                if (ps != null)
                    if (primaryException1$ != null)
                        try {
                            ps.close();
                        } catch (Throwable x2) {
                            primaryException1$.addSuppressed(x2);
                        }
                    else ps.close();
            }
        } catch (/*synthetic*/ final Throwable t$) {
            primaryException0$ = t$;
            throw t$;
        } finally {
            if (br != null)
                if (primaryException0$ != null)
                    try {
                        br.close();
                    } catch (Throwable x2) {
                        primaryException0$.addSuppressed(x2);
                    }
                else br.close();
        }
    }

}
