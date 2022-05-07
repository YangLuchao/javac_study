package book.chapter10;

import java.util.List;

public class Test20 {
}
abstract class CA20 {
  public abstract <T1> List<T1> md(String a);
}
class CB20 extends CA20 {
  @Override
  public <T2> List<T2> md(String t) {
    return null;
  }
}