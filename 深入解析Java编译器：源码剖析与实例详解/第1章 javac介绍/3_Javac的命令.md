# Javac的命令

Javac提供了一些命令，用于支持Java源文件的编译。如果安装且配置了Java的PATH路径，可在Windows的命令行窗口中输入java \-help命令查看，或者直接查看Javac源码中的枚举类

**com.sun.tools.javac.main.OptionName**，其中列举了所有当前Javac版本所支持的命令。

 G\("\-g"\),

    G\_NONE\("\-g:none"\),

    G\_CUSTOM\("\-g:"\),

    XLINT\("\-Xlint"\),

    XLINT\_CUSTOM\("\-Xlint:"\),

    DIAGS\("\-XDdiags="\),

    NOWARN\("\-nowarn"\),

    VERBOSE\("\-verbose"\),

    DEPRECATION\("\-deprecation"\),

    CLASSPATH\("\-classpath"\),

    CP\("\-cp"\),

    SOURCEPATH\("\-sourcepath"\),

    BOOTCLASSPATH\("\-bootclasspath"\),

    XBOOTCLASSPATH\_PREPEND\("\-Xbootclasspath/p:"\),

    XBOOTCLASSPATH\_APPEND\("\-Xbootclasspath/a:"\),

    XBOOTCLASSPATH\("\-Xbootclasspath:"\),

    EXTDIRS\("\-extdirs"\),

    DJAVA\_EXT\_DIRS\("\-Djava.ext.dirs="\),

    ENDORSEDDIRS\("\-endorseddirs"\),

    DJAVA\_ENDORSED\_DIRS\("\-Djava.endorsed.dirs="\),

    PROC\("\-proc:"\),

    PROCESSOR\("\-processor"\),

    PROCESSORPATH\("\-processorpath"\),

    D\("\-d"\),

    S\("\-s"\),

    IMPLICIT\("\-implicit:"\),

    ENCODING\("\-encoding"\),

    SOURCE\("\-source"\),

    TARGET\("\-target"\),

    VERSION\("\-version"\),

    FULLVERSION\("\-fullversion"\),

    HELP\("\-help"\),

    A\("\-A"\),

    X\("\-X"\),

    J\("\-J"\),

    MOREINFO\("\-moreinfo"\),

    WERROR\("\-Werror"\),

    COMPLEXINFERENCE\("\-complexinference"\),

    PROMPT\("\-prompt"\),

    DOE\("\-doe"\),

    PRINTSOURCE\("\-printsource"\),

    WARNUNCHECKED\("\-warnunchecked"\),

    XMAXERRS\("\-Xmaxerrs"\),

    XMAXWARNS\("\-Xmaxwarns"\),

    XSTDOUT\("\-Xstdout"\),

    XPKGINFO\("\-Xpkginfo:"\),

    XPRINT\("\-Xprint"\),

    XPRINTROUNDS\("\-XprintRounds"\),

    XPRINTPROCESSORINFO\("\-XprintProcessorInfo"\),

    XPREFER\("\-Xprefer:"\),

    O\("\-O"\),

    XJCOV\("\-Xjcov"\),

    XD\("\-XD"\),

    AT\("@"\),

    SOURCEFILE\("sourcefile"\);

在命令行窗口舒服javac命令式，格式如下

```shell
javac [options] [sourceFiles] [@argFiles\]
```

options是指命令行选项，sourceFiles是指一个或多个Java源文件，@argFiles是指列出选项和源文件的一个或者多个文件

ylcComplieTest.TestCompiler
