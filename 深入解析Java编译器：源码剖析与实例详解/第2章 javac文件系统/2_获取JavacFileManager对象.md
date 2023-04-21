# 获取JavacFileManager对象

在每次进行Java源代码编译时，Javac都会生成一个特定的上下文对象com.sun.tools.javac.util.Context，一些重要类在此上下文中都有唯一的对象，也就是常说的单例。Context类中定义了一个重要的成员变量ht，具体定义如下：

```java
来源：com.sun.tools.javac.util.Context
private Map<Key<?>,Object> ht = new HashMap<Key<?>,Object>();
```

key的类型为Key\<?\>，这个类型定义在Context类中，是一个空实现，具体定义如下：

```java
来源：com.sun.tools.javac.util.Context 
public static class Key<T> { }
```

而value存储的是T类型的对象，也可能是Factory\<T\>类型的工厂对象。其中，Factory\<T\>也定义在Context类中，具体定义如下：

```java
来源：com.sun.tools.javac.util.Context.Factory 
public static interface Factory<T> {
	T make(Context c); 
};
```

其中声明的make\(\)方法就是创建T类型的对象时调用的工厂方法。Context类中提供了两个put\(\)方法，可以将T类型的对象或者Factory\<T\>类型的对象存储到ht中。这两个put\(\)方法的实现如下：

```java
来源：com.sun.tools.javac.util.Context.Factory 
public <T> void put(Key<T> key, Factory<T> fac) {
Object old = ht.put(key, fac);
	...
	ft.put(key, fac); 
}
public <T> void put(Key<T> key, T data) {
	...
	Object old = ht.put(key, data);
	... 
}
```

在获取对象时调用Context类的get\(\)方法即可，get\(\)方法的实现如下：

```java
来源：com.sun.tools.javac.util.Context.Factory 
public <T> T get(Key<T> key) {
	Object o = ht.get(key);
	if (o instanceof Factory<?>) { 
		Factory<?> fac = (Factory<?>)o; 
		o = fac.make(this);
		...
		Assert.check(ht.get(key) == o); 
		}
	return Context.<T>uncheckedCast(o); 
}
```

get\(\)方法根据参数key从ht中获取value值o，然后判断值是否为工厂对象。如果是，就调用工厂方法获取T类型的对象，最后调用uncheckedCast\(\)方法将o的类型强制转换为T类型。下面来看如何获取一个JavacFileManager对象。在com.sun.tools.javac.main.Main类中的compile\(\)方法中有如下代码实现：

```java
来源：com.sun.tools.javac.main.Main 
public int compile(String[] args) {
	Context context = new Context(); 
	JavacFileManager.preRegister(context);
	... 
}
```

首先创建一个Context对象，然后调用JavacFileManager类的preRegister\(\)方法，该方法的实现如下：

```java
来源：com.sun.tools.javac.file.JavacFileManager 
public static void preRegister(Context context) {
	context.put(JavaFileManager.class, new Context.Factory<JavaFileManager>() {
	public JavaFileManager make(Context c) { 
		return new JavacFileManager(c, true, null);
		} 
	});
}
```

以上代码在上下文对象context中放入一个创建JavacFileManager对象的工厂类对象，当需要JavacFileManager对象时可以通过如下方式获取：

```java
JavaFileManager fileManager = context.get(JavaFileManager.class);
```
