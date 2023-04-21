# JavacFileManager类

Javac主要通过JavacFileManager类中提供的方法对相关的文件进行操作

例如：

```java
package chapter2; 
import java.util.List; 
public class TestJFM{
	List<String> l; 
}
```

Javac会查找java.util包路径下的List.class文件进行加载，根据java.util包路径只能得到查找文件的相对路径。而要加载一个文件必须要确定其绝对路径，这时候就可以遍历PLATFORM\_CLASS\_PATH、 SOURCE\_PATH及CLASS\_PATH中的所有路径了，然后与相对路径拼接为一个绝对路径，有了这个绝对路径后就可以判断哪个绝对路径下有List.class文件了。 JavacFileManager类为这样的查找需求提供了一个list\(\)方法，这个方法的实现如下：

```java
来源：com.sun.tools.javac.file.JavacFileManager
    public Iterable<JavaFileObject> list(Location location,
                                         String packageName,
                                         Set<JavaFileObject.Kind> kinds,
                                         boolean recurse)
        throws IOException
    {
        Iterable<? extends File> path = getLocation(location);
        if (path == null)
            return List.nil();
        RelativeDirectory subdirectory = RelativeDirectory.forPackage(packageName);
        ListBuffer<JavaFileObject> results = new ListBuffer<JavaFileObject>();

        for (File directory : path)
            listContainer(directory, subdirectory, kinds, recurse, results);
        return results.toList();
    }
```

参数location在前面已经详细介绍过；参数packageName表示要查找这个包下的相关文件；参数kinds指定要查找哪些类型的文件，这是一个Set集合，其中的元素是Kind类型的。Kind类是一个枚举类，具体定义如下：

```java
来源：javax.tools.JavaFileObject.Kind 
enum Kind {
	SOURCE(".java"), 
	CLASS(".class"), 
	HTML(".html"), 
	OTHER("");
};
```

一般都是查找以.java结尾的Java源文件或者以.class结尾的Class文件。

list\(\)方法首先调用getLocation\(\)方法以获取到相关location下的所有File对象，这些File对象为查找指明了绝对路径，然后循环所有的File对象并调用listContainer\(\)方法继续进行查找，对符合条件的所有文件都会添加到results列表中。list\(\)方法中调用的getLocation\(\)方法的实现代码如下：

```java
    public Iterable<? extends File> getLocation(Location location) {
        nullCheck(location);
        paths.lazy();
        if (location == CLASS_OUTPUT) {
            return (getClassOutDir() == null ? null : List.of(getClassOutDir()));
        } else if (location == SOURCE_OUTPUT) {
            return (getSourceOutDir() == null ? null : List.of(getSourceOutDir()));
        } else
            return paths.getPathForLocation(location);
    }
```

当location的值为CLASS\_OUTPUT或SOURCE\_OUTPUT时，则会调用getClassOuterDir\(\)方法或getSourceOutDir\(\)方法，其实就是获取\-d命令或\-s命令指定的输出路径，然后创建一个File对象返回。当location为其他值时，调用Paths对象paths的getPathForLocation\(\)方法，这个方法的实现如下：

```java
来源：com.sun.tools.javac.file.Paths
Path getPathForLocation(Location location) { 
	Path path = pathsForLocation.get(location);
	...
	return pathsForLocation.get(location);
}
```

直接从pathsForLocation集合中取值即可，返回的File对象是具体的压缩包，其中pathsForLocation集合的填充过程在前面已详细介绍过，这里不再介绍。

list\(\)方法中调用的listContainer\(\)方法的实现代码如下：

```java
    private void listContainer(File container,
                             RelativeDirectory subdirectory,
                               Set<JavaFileObject.Kind> fileKinds,
                               boolean recurse,
                               ListBuffer<JavaFileObject> resultList) {
        Archive archive = archives.get(container);
        if (archive == null) {
            // 如果container为目录，就调用listDirectory()方法进行处理
            if  (fsInfo.isDirectory(container)) {
              // 获取目录中满足要求的文件并追加到resultList列表中
                listDirectory(container,
                              subdirectory,
                              fileKinds,
                              recurse,
                              resultList);
                return;
            }

            // Not a directory; either a file or non-existant, create the archive
            try {
              // container是压缩包
                archive = openArchive(container);
            } catch (IOException ex) {
                log.error("error.reading.file",
                          container, getMessage(ex));
                return;
            }
        }
      // 获取压缩包中满足要求的文件并追加到resultList列表中
        listArchive(archive,
                    subdirectory,
                    fileKinds,
                    recurse,
                    resultList);
    }
```

archives是JavacFileManager类中定义的一个成员变量，具体的定义如下：

```java
来源：com.sun.tools.javac.file.JavacFileManager
Map<File, Archive> archives = new HashMap<File,Archive>();
```

这个变量主要用来缓存已经被加载过的压缩包，如果根据参数container从archives中获取的值为null，则表示可能是一个目录或者没有被加载过的压缩包。如果container表示的是一个目录，则调用listDirectory\(\)方法进行处理，处理完成后直接返回；如果container是一个压缩包，则调用openArchive\(\)方法加载这个压缩包，获取到archive后继续调用listArchive\(\)方法进行处理。

首先来看listContainer\(\)方法中调用的listDirectory\(\)方法的实现，代码如下：

```java
来源：com.sun.tools.javac.file.JavacFileManager    
private void listDirectory(File directory,
                               RelativeDirectory subdirectory,
                               Set<JavaFileObject.Kind> fileKinds,
                               boolean recurse,
                               ListBuffer<JavaFileObject> resultList) {
      // 拼接为绝对路径
        File d = subdirectory.getFile(directory);
        if (!caseMapCheck(d, subdirectory))
            return;

        File[] files = d.listFiles();
        if (files == null)
            return;

        if (sortFiles != null)
            Arrays.sort(files, sortFiles);

        for (File f: files) {
            String fname = f.getName();
            if (f.isDirectory()) {
              // 对目录的处理逻辑
                if (recurse && SourceVersion.isIdentifier(fname)) {
                  // 递归
                    listDirectory(directory,
                                  new RelativeDirectory(subdirectory, fname),
                                  fileKinds,
                                  recurse,
                                  resultList);
                }
            } else {
              // 对文件的处理逻辑
                if (isValidFile(fname, fileKinds)) {
                    JavaFileObject fe =
                        new RegularFileObject(this, fname, new File(d, fname));
                    resultList.append(fe);
                }
            }
        }
```

调用subdirectory.getFile\(\)方法将directory与subdirectory拼接成一个绝对路径，然后查找这个路径下所有的文件及目录并循环处理。在对目录进行处理时，当recurse的值为true并且fname是一个合法的文件名时，则递归调用listDirectory\(\)方法进行处理，不过这次方法参数subdirectory的值变为了：

```java
new RelativeDirectory(subdirectory, fname)
```

也就是将当前目录拼接到了搜索路径的后面，作为新的搜索路径继续进行搜索。一般，Javac在查找某个路径下的文件时是不会进行递归查找的，也就是说recurse的值为false。在对文件进行处理时，调用isValidFile\(\)方法判断文件类型是否满足要查找的类型，这个类型由参数fileKinds指定，如果符合要求则创建JavaFileObject对象并将这个对象加入到resultList列表中。

接着看listContainer\(\)方法中调用的openArchive\(\)方法，这个方法会调用另外一个重载的openArchive\(\)方法对压缩包进行处理。重载方法在实现过程中，会使用第2.1节介绍ZipFileIndexArchive、ZipArchive和SymbolArchive类来读取压缩包内容，不过默认是通过ZipFileIndexArchive类来读取的，因此这里笔者省略了通过ZipArchive与SymbolArchive类来读取压缩包的代码。

重载的openArchive\(\)方法的实现代码如下：

```java
来源：com.sun.tools.javac.file.JavacFileManager
private Archive openArchive(File zipFileName, _) throws IOException {
        File origZipFileName = zipFileName;
        // 根据rt.jar包的绝对路径找到ct.sym包的绝对路径
        if (!ignoreSymbolFile && paths.isDefaultBootClassPathRtJar(zipFileName)) {
            File file = zipFileName.getParentFile().getParentFile(); // ${java.home}
            if (new File(file.getName()).equals(new File("jre")))
                file = file.getParentFile();
            // file == ${jdk.home}
            for (String name : symbolFileLocation)
                file = new File(file, name);
            // file == ${jdk.home}/lib/ct.sym
            if (file.exists())
                zipFileName = file;
        }

        Archive archive;
        ...
        ZipFile zdir = null;
		zdir = new ZipFile(zipFileName);
		if (origZipFileName == zipFileName) {// 读取的是非rt.jar包中的内容
		archive = new       ZipFileIndexArchive(this,zipFileIndexCache.getZip 
		FileIndex(zipFileName,null,_,_,_));
	} else { // 读取的是rt.jar包中的内容
		archive = new ZipFileIndexArchive(this, 
		zipFileIndexCache.getZipFileIndex(zipFileName,
		symbolFilePrefix, _,_,_)); 
	}
	...
	archives.put(origZipFileName, archive); 
	return archive;
}
```

openArchive\(\)方法首先对ct.sym这个特殊的压缩包做处理，代码的实现逻辑并不直观，主要就是根据rt.jar包的绝对路径找到ct.sym包的绝对路径，其中ignoreSymbolFile是boolean类型的变量，默认值为false；调用paths.isDefaultBootClassPathRtJar\(\) 方法，判断zipFileName是否为代表JAVA\_HOME\\jre\\lib\\路径下的rt.jar包，如果是就返回true，然后继续对rt.jar包进行处理。执行完代码后就找到了JAVA\_HOME的路径，然后拼接symbolFileLocation数组中保存的值。symbolFileLocation数组的定义如下：

```java
来源：com.sun.tools.javac.file.JavacFileManager
private static final String[] symbolFileLocation = { "lib", "ct.sym" };
```

例如笔者本机rt.jar包的绝对路径为C:\\Program Files\\Java\\jdk1.7.0\_79\\jre\\lib\\rt.jar，则最终zipFileName是ct.sym包，这个包的路径为C:\\Program Files\\Java\\jdk1.7.0\_79\\lib\\ct.sym。

openArchive\(\)方法接着根据不同的配置参数及压缩包选择性创建不同的Archive对象。当origZipFileName与zipFileName相等时，表示读取的是非rt.jar包，创建ZipFileIndexArchive对象；否则也会创建ZipFileIndexArchive对象，只是调用zipFileIndex Cache.getZipFileIndex\(\)方法时会给第2个参数传递symbolFilePrefix。symbolFilePrefix成员变量的定义如下：

```java
来源：com.sun.tools.javac.file.JavacFileManager
private static final RelativeDirectory symbolFilePrefix = new RelativeDirectory("META-INF/sym/rt.jar/");
```

在创建RelativeDirectory对象时传递了一个表示路径的字符串参数“META\-INF/sym/rt.jar/”，这个值在讲解ct.sym包时讲过，相关的类及包都放到了ct.sym包的META\-INF/sym/rt.jar/路径下了。zipFileIndexCache是ZipFileIndexCache类型的，这个类的定义如下：

```java
来源：com.sun.tools.javac.file.ZipFileIndexCache
public class ZipFileIndexCache {

    private final Map<File, ZipFileIndex> map =
            new HashMap<File, ZipFileIndex>();

    public synchronized ZipFileIndex getZipFileIndex(File zipFile,
            RelativeDirectory symbolFilePrefix,
            _, _, _) throws IOException {
        ZipFileIndex zi = getExistingZipIndex(zipFile);
        // 如果ZipFileIndex对象不存在或者文件内容已经更新，则创建一个新的ZipFileIndex对象
        if (zi == null || (zi != null && zipFile.lastModified() != zi.zipFileLastModified)) {
            zi = new ZipFileIndex(zipFile, symbolFilePrefix, writeIndex,
                    useCache, cacheLocation);
            map.put(zipFile, zi);
        }
        return zi;
    }

    public synchronized ZipFileIndex getExistingZipIndex(File zipFile) {
        return map.get(zipFile);
    }
}
```

map一般通过调用getZipFileIndex\(\)方法进行填充，这个变量保存了Java的文件对象到Javac内部文件对象的映射关系。getZipFileIndex\(\)方法首先调用getExistingZipIndex\(\)方法判断map中是否存在对应的ZipFileIndex对象，如果不存在或者文件内容已经更新，则创建一个新的ZipFileIndex对象并存储到map中。ZipFileIndex类在前面已经详细介绍过，创建ZipFileIndex对象时会调用checkIndex\(\)方法，根据传入的zipFile参数填充ZipFileIndex对象的entries数组，也就是把zipFile这个压缩包的目录和文件都读取出来保存到entries数组中。

最后在openArchive\(\)方法中，将创建出来的archive按对应关系存储到archives集合中并返回此对象，这样在listContainer\(\)方法中调用openArchive\(\)方法最终得到了一个ZipFileIndexArchive对象。接着调用listArchive\(\)方法进行处理，具体就是从这个压缩包中找到满足要求的文件并添加到resultList列表中。listArchive\(\)方法的实现代码如下：

```java
来源：com.sun.tools.javac.file.JavacFileManager
    private void listArchive(Archive archive,
                               RelativeDirectory subdirectory,
                               Set<JavaFileObject.Kind> fileKinds,
                               boolean recurse,
                               ListBuffer<JavaFileObject> resultList) {
        // 获取subdirectory路径下的所有文件并追加到resultList列表中
        List<String> files = archive.getFiles(subdirectory);
        if (files != null) {
            for (; !files.isEmpty(); files = files.tail) {
                String file = files.head;
                if (isValidFile(file, fileKinds)) {
                    resultList.append(archive.getFileObject(subdirectory, file));
                }
            }
        }
        // 获取subdirectory及subdirectory目录的所有直接或间接子目录下的文件
        // 并追加到resultList列表中
        if (recurse) {
            for (RelativeDirectory s: archive.getSubdirectories()) {
                if (subdirectory.contains(s)) {
                    listArchive(archive, s, fileKinds, false, resultList);
                }
            }
        }
	}
```

调用archive.getFiles\(\)方法。对于ZipFileIndexArchive类型来说，会间接调用zfIndex的getFiles\(\)方法，这些方法的实现在第2.1节中详细介绍过，为了读者阅读方便，这里再次给出ZipFileIndex类中的getFiles\(\)方法的实现，代码如下： 

---

```java
来源：com.sun.tools.javac.file.ZipFileIndex
public synchronized com.sun.tools.javac.util.List<String> getFiles
(RelativeDirectory path) {
        checkIndex();// 调用checkIndex()方法确保压缩包内容已经被读取并且是最新的
        DirectoryEntry de = directories.get(path);
        com.sun.tools.javac.util.List<String> ret = de == null ? null : de.
getFiles();
        if (ret == null) {
            return com.sun.tools.javac.util.List.<String>nil();
        }
        return ret;
}
```

---

ZipFile\(\)方法首先调用checkIndex\(\)方法，由于在创建ZipFileIndex对象时已经调用过checkIndex\(\)方法并且读取了压缩包内容，因而这里一般不会再重复读取，除非之前读取的内容已经不是最新的内容才会再次读取。从directories集合中获取具体的目录，如果de不为空，则表示有相应的目录存在，调用de.getFiles\(\)方法获取目录下的所有文件名称并返回；如果de为空则返回一个空的列表。 

listArchive\(\)方法遍历了files列表，取出满足fileKinds参数格式要求的文件，然后调用archive的getFileObject\(\)方法获取JavaFileObject对象并添加到resultList列表中。

当archive为ZipFileIndexArchive类型时，关于ZipFileIndexArchive类的getFileObject\(\)方法的实现在第2.1节中也详细介绍过，这里不再介绍。 
