# 初识Javac

Javac讲Java源代码变成字节码的过程会涉及词法分析、语法分析、语义分析和代码生成等阶段
[com.zip](./com.zip)

![image](https://github.com/YangLuchao/img_host/raw/master/20230418/image.3oa4o1gdmh20.webp)

## 词法分析

**经过Javac的词法分析阶段后转换为Token流**

[javax.zip](./javax.zip)

每个小方格表示一个具体的Token，其中，箭头（即\-\>）左边的部分为源代码字符串，右边就是对应的Token名称。从图中可以看到，词法分析过程将Java源代码按照Java关键字、自定义标识符和符号等顺序分解为了可识别的Token流，对于空格与换行符等不会生成对应的Token，它们只是作为划分Token的重要依据。

![image](https://github.com/YangLuchao/img_host/raw/master/20230418/image.3z0spyekb6w0.webp)

## 语法分析

将进行词法分析后形成的Token流中的Token组合成遵循Java语法规范的语法节点，形成一颗基本的抽象语法树，如图所示

图中的方格代表抽象语法树节点，而方法中的名称就是Javac对此抽象语法节点的具体实现类，连接线上的名称表示节点属性；其中的两个JCVariableDecl语法树节点，代表两个变量a与b的声明及初始化；vartype属性表示变量声明的类型，而init属性表示对此变量的初始化。
![image](https://github.com/YangLuchao/img_host/raw/master/20230418/image.1lk3cm38oerk.webp)

javac将具象的token流转换为抽象的语法树，其中每个处理节点都是javac中具象的类：

com.sun.tools.javac.tree.JCTree.JCCompilationUnit

com.sun.tools.javac.tree.JCTree.JCCase

com.sun.tools.javac.tree.JCTree.JCVariableDecl

com.sun.tools.javac.tree.JCTree.JCPrimitiveTypeTree

com.sun.tools.javac.tree.JCTree.JCIdent

## 语义分析

语义分析过程最为复杂，该过程涉及的细节众多，除了对代码编写者写出的源代码根据JLS规范进行严格的检查外，还必须为后面的代码生成阶段准备各种数据，如符号表、标注抽象语法树节点的符号及类型等

## 代码生成

将标注语法树转化成字节码，并将字节码写入Class文件。通过命令javap \-verbose TestJavac来查看Class文件的相关内容
