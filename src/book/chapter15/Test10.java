package book.chapter15;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class Test10 {
    public void testTryWithResources(FileReader f) throws IOException {
        try (BufferedReader br = new BufferedReader(f);) {
            System.out.println(br.readLine());
        }
    }

    /*
    解语法糖
     */
    public void testTryWithResources1(FileReader f) throws IOException {
        final BufferedReader br = new BufferedReader(f);
        /*synthetic*/
        Throwable primaryException0$ = null;
        try {
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
    如果try语句不为基本的try-with-resources形式时，
    如至少含有一个catch语句或finally语句时称为扩展try-with-resources
     */
    public void testTryWithResources() {
        try (BufferedReader br = new BufferedReader(new FileReader("AutoClose Test.java"));) {
            System.out.println(br.readLine());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /*
    解语法糖
     */
    public void testTryWithResources1() {
        try {
            final BufferedReader br = new BufferedReader(new FileReader("AutoClose Test.java"));
            /*synthetic*/
            Throwable primaryException0$ = null;
            try {
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
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
