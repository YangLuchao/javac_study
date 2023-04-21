# StandardLocation类

Java中有一个重要的枚举类javax.tools.StandardLocation，其中定义了几个重要的枚举常量：

```java
来源：javax.tools.StandardLocation
public enum StandardLocation implements Location { 
	CLASS_OUTPUT,
	SOURCE_OUTPUT, 
	CLASS_PATH, 
	SOURCE_PATH,
	ANNOTATION_PROCESSOR_PATH, 
	PLATFORM_CLASS_PATH;
	... 
}
```

CLASS\_OUTPUT与SOURCE\_OUTPUT代表文件的输出路径，其中CLASS\_OUTPUT代表Class文件的输出路径，通常对应着\-d命令指定的路径，而SOURCE\_OUTPUT代表Java源文件的输出路径，通常对应着\-s指定的路径。例如，Javac将Java源文件编译为Class文件时，会将这个Class文件保存到CLASS\_OUTPUT所代表的输出路径下。

StandardLocation枚举类中定义的枚举常量将Java源文件及Class文件的搜索路径进行了归类，主要分为4大类：

* PLATFORM\_CLASS\_PATH
* SOURCE\_PATH 
* CLASS\_PATH
* ANNOTATION\_PROCESSOR\_PATH

如果在PLATFORM\_CLASS\_PATH下搜索Class文件具体会读取JAVA\_HOME/lib和JAVA\_HOME/ext路径下的JAR包，而SOURCE\_PATH与CLASS\_PATH只有在指定了\-classpath或者\-sourcepath命令时才会有用，这两者之间的关系如下。

* 当没有指定\-sourcepath命令时，在\-classpath命令指定的路径下面搜索Java源文件和Class文件。
* 当指定\-sourcepath命令时，只搜索\-classpath命令指定路径下的Class文件，忽略所有的Java源文件，而在\-sourcepath命令指定的路径下搜索Java源文件，会忽略所有的Class文件。因此一般应该避免指定\-sourcepath命令，只指定\-classpath命令来搜索依赖的Java源文件和Class文件。

下面就来看看这4个类别分别对应的具体的搜索路径。在Paths类中定义了一个类型为Map\<Location,Path\>的成员变量pathsForLocation，该变量保存了StandardLocation类中的枚举常量到具体搜索路径的映射关系。pathsForLocation的value值类型为Path，Path类是Paths类中定义的一个私有类，这个类继承了LinkedHashSet\<File\>，也就是说Path本质上是一个集合类。在适当的时候会调用Paths类中的lazy\(\)或setPathForLocation\(\)方法对pathsForLocation进行填充。

PLATOFRM\_CLASS\_PATH代表的搜索路径通过调用computeBootClassPath\(\)方法得到，该方法的实现如下：

```java
来源：com.sun.tools.javac.file.Paths
    private Path computeBootClassPath() {
        defaultBootClassPathRtJar = null;
        Path path = new Path();
		// 获取-bootclasspath指定的值
        String bootclasspathOpt = options.get(BOOTCLASSPATH);
        // 获取-endorseddirs指定的值
        String endorseddirsOpt = options.get(ENDORSEDDIRS);
        // 获取-extdirs指定的值
        String extdirsOpt = options.get(EXTDIRS);
        // 获取-Xbootclasspath/p:指定的值
        String xbootclasspathPrependOpt = options.get(XBOOTCLASSPATH_PREPEND);
        // 获取-Xbootclasspath/a:指定的值
        String xbootclasspathAppendOpt = options.get(XBOOTCLASSPATH_APPEND);
        path.addFiles(xbootclasspathPrependOpt);
		// 当endorseddirsOpt为空时，获取系统属性java.endorsed.dirs所指定的目录路径
        if (endorseddirsOpt != null)
            path.addDirectories(endorseddirsOpt);
        else
            path.addDirectories(System.getProperty("java.endorsed.dirs"), false);
// 当bootclasspathOpt为空时，获取系统属性sun.boot.class.path所指定的目录路径
        if (bootclasspathOpt != null) {
            path.addFiles(bootclasspathOpt);
        } else {
            // Standard system classes for this compiler's release.
            String files = System.getProperty("sun.boot.class.path");
            path.addFiles(files, false);
		...
        }

        path.addFiles(xbootclasspathAppendOpt);

        // 如果extdirsOpt为空时，获取系统属性java.ext.dirs所指定的目录路径
        if (extdirsOpt != null)
            path.addDirectories(extdirsOpt);
        else
            path.addDirectories(System.getProperty("java.ext.dirs"), false);
        ...
        return path;
    }
```

如果没有指定\-endorseddirs命令，则获取系统属性java.endorsed.dirs所指定的目录路径；如果没有指定\-bootclasspath命令，则获取系统属性sun.boot.class.path所指定的目录路径；如果没有指定\-extdirs命令，则获取系统属性java.ext.dirs所指定的目录路径；对于\-Xbootclasspath/p:与\-Xbootclasspath/a:命令指定的文件（通常为JAR包），直接添加到path集合中。

CLASS\_PATH代表的搜索路径通过调用computeUserClassPath\(\)方法得到，这个方法的实现如下：

```java
来源：com.sun.tools.javac.file.Paths
    private Path computeUserClassPath() {
    	// 获取-classpath指定的路径
        String cp = options.get(CLASSPATH);

        // CLASSPATH environment variable when run from `javac'.
        // cp为空时，获取系统属性env.class.path所指定的目录路径
        if (cp == null) cp = System.getProperty("env.class.path");

        // If invoked via a java VM (not the javac launcher), use the
        // platform class path
        // cp为空且系统属性application.home为空时，获取系统属性java.class.path所指定的目录路径
        if (cp == null && System.getProperty("application.home") == null)
            cp = System.getProperty("java.class.path");

        // Default to current working directory.
        // cp为空时，默认为当前的工作目录
        if (cp == null) cp = ".";

        return new Path()
            .expandJarClassPaths(true)        // Only search user jars for Class-Paths 仅在classpath下搜索用户的JAR包
            .emptyPathDefault(new File("."))  // Empty path elt ==> current directory path默认的路径为当前的工作目录
            .addFiles(cp);
    }
```

当没有指定\-classpath命令时，默认也会获取系统属性env.class.path的值；如果cp值仍然为空并且系统属性application.home值也为空时，则获取系统属性java.class.path的值；如果仍然为空则就取当前的工作路径为cp的值。

调用computeSourcePath\(\)方法计算SOURCE\_PATH，就是获取\-sourcepath命令指定的路径；调用computeAnnotationProcessorPath\(\)方法计算ANNOTATION\_PROCESSOR\_PATH，就是获取\-processorpath命令指定的路径。
