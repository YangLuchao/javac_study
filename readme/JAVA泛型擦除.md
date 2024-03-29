[toc]

# Java泛型

***<u>深入理解Java虚拟机 第三版 10.3.1</u>***

>泛型的本质是参数化类型（Parameterized Type）或者参数化多态（Parametric Polymorphism）的应用，即可以将操作的数据类型指定为方法签名中的一种特殊参数，这种参数类型能够用在类、接口和方法的创建中，分别构成泛型类、泛型接口和泛型方法。泛型让程序员能够针对泛化的数据类型编写相同的算法，这极大地增强了编程语言的类型系统及抽象能力。
>
>Java选择的泛型实现方式叫作“类型擦除式泛型”（Type Erasure Generics）
>
>...
>
>Java语言中的泛型则不同，它只在程序源码中存在，在编译后的字节码文件中，全部泛型都被替换为原来的裸类型了，并且在相应的地方插入了强制转型代码
>
>...

## 直观看擦除

**泛型擦除前：**

![image-20220613124257932](https://raw.githubusercontent.com/YangLuchao/javac_study/main/readme/%E6%B3%9B%E5%9E%8B%E6%93%A6%E9%99%A4-%E5%9B%BE/%E6%B3%9B%E5%9E%8B%E6%93%A6%E9%99%A4%E5%89%8D.png)

**编译后：**

![image-20220613124447715](https://raw.githubusercontent.com/YangLuchao/javac_study/main/readme/%E6%B3%9B%E5%9E%8B%E6%93%A6%E9%99%A4-%E5%9B%BE/%E7%BC%96%E8%AF%91%E5%90%8E.png)

当前看来是没有被擦除的，这是因为在反编译是，==工具将泛型信息也同步进行了反编译==，通过字节码是可以看泛型是被擦除了

![image-20220613124708604](https://raw.githubusercontent.com/YangLuchao/javac_study/main/readme/%E6%B3%9B%E5%9E%8B%E6%93%A6%E9%99%A4-%E5%9B%BE/Test10_1%E7%9A%84%E5%AD%97%E8%8A%82%E7%A0%81%E6%88%AA%E5%9B%BE.png)

后面源码阶段也可以看单擦除的过程

# 泛型实现的相关定义

## 树节点

### **JCTypeParameter(类型参数树节点)：**

```java
    来源：com.sun.tools.javac.tree.JCTree.JCTypeParameter
    // 类型参数对象，泛型
    // 每个形式类型参数都是一个JCTypeParameter对象
    // 可以表示类型（接口或类）或者方法声明的类型参数
    public static class JCTypeParameter extends JCTree implements TypeParameterTree {
        // 保存类型参数中类型变量的名称
        public Name name;
        // bounds保存类型变量的上界，可以有多个(泛型)
        public List<JCExpression> bounds;
				...
    }
```

### **JCWildcard(通配符树节点):**

```java
    来源：com.sun.tools.javac.tree.JCTree.JCWildcard
    // 通配符类型
    public static class JCWildcard extends JCExpression implements WildcardTree {
        // 保存通配符的类型
        public TypeBoundKind kind;
        // 通配符类型上界或下界
        public JCTree inner;
				...
    }
		来源：com.sun.tools.javac.tree.JCTree.TypeBoundKind
    // 通配符的类型
    public static class TypeBoundKind extends JCTree {
        // 通配符的类型
        public BoundKind kind;
    }
来源：com.sun.tools.javac.code.BoundKind
public enum BoundKind {
    // 下界通配符
    EXTENDS("? extends "),
    // 上界通配符
    SUPER("? super "),
    // 无界通配符
    UNBOUND("?");

    private final String name;
}
```

## 类型

### **ForAll(含有泛型变量声明的方法类型,主要辅助进行类型推断)**

```java
    来源：com.sun.tools.javac.code.Type.ForAll
    public static class ForAll extends DelegatedType implements ExecutableType {
        // 具体的类型变量
        public List<Type> tvars;
        ...
		}
```

# 泛型相关抽象语法树的生成

已下面代码为例，生成抽象语法树：

```java
package book.chapter13;

import java.io.Serializable;

/*
泛型擦除前
 */
class Test10<X extends Integer, Y extends Serializable & Cloneable, Z extends X> {
    X x;
    Y y;
    Z z;
}
```

### **抽象语法树生成**

```java
来源：com.sun.tools.javac.parser.JavacParser#parseCompilationUnit
    public JCTree.JCCompilationUnit parseCompilationUnit() {
        ...
        // 出现结束符跳出循环
        while (S.token() != EOF) {
           ...
                // 解析类声明
                JCTree def = typeDeclaration(mods);
                defs.append(def);
           ...
        }
        JCTree.JCCompilationUnit toplevel = F.at(pos).TopLevel(packageAnnotations, pid, defs.toList());
        ...
        return toplevel;
    }

```

### **解析类、接口、枚举的定义**

```java
来源：com.sun.tools.javac.parser.JavacParser#classOrInterfaceOrEnumDeclaration
      JCStatement classOrInterfaceOrEnumDeclaration(JCModifiers mods, String dc) {
        // 解析类
        if (S.token() == CLASS) {
            return classDeclaration(mods, dc);
            // 解析接口
        } else if (S.token() == INTERFACE) {
            return interfaceDeclaration(mods, dc);
            // 解析枚举类
        } else if (allowEnums) {
            if (S.token() == ENUM) {
                return enumDeclaration(mods, dc);
            } else {
                ...
            }
        } else {
           ... 
        }
    }

```

### **类声明解析**

```java
来源：com.sun.tools.javac.parser.JavacParser#classDeclaration
    JCClassDecl classDeclaration(JCModifiers mods, String dc) {
        ...
        // 解析类型参数
        List<JCTypeParameter> typarams = typeParametersOpt();
				...
        // 创建类声明对象，并返回
        JCClassDecl result = toP(F.at(pos).ClassDef(
            mods, name, typarams, extending, implementing, defs));
        return result;
    }
```

### **参数化类型解析**

```java
来源：com.sun.tools.javac.parser.JavacParser#typeParametersOpt
    List<JCTypeParameter> typeParametersOpt() {
  			// LT : '<'
        if (S.token() == LT) {
            ...
            ListBuffer<JCTypeParameter> typarams = new ListBuffer<JCTypeParameter>();
          	// 跳转到下一个token
            S.nextToken();
          	// 解析类型参数
            typarams.append(typeParameter());
            // 循环对多个类型参数进行解析
            while (S.token() == COMMA) {
              	// 跳转到下一个token
                S.nextToken();
              	// 解析类型参数
                typarams.append(typeParameter());
            }
          	// GT : '>'
            accept(GT);
            return typarams.toList();
        } else {
            return List.nil();
        }
    }
来源：com.sun.tools.javac.parser.JavacParser#typeParameter
      JCTypeParameter typeParameter() {
        ...
        // 类型参数的name  
        Name name = ident();
  			// 当前类型参数的上界列表，可以有多个上界
        ListBuffer<JCExpression> bounds = new ListBuffer<JCExpression>();
        // 类型参数中声明上界，EXTENDS：'extends',没有默认上界为Object
        if (S.token() == EXTENDS) {
          	// 切换到下一个token
          	S.nextToken();
          	// 添加到上界列表
            bounds.append(parseType());
         		// AMP:'&',可以有多个上界，用&符间隔
            while (S.token() == AMP) {
                S.nextToken();
              	// 添加到上界列表
                bounds.append(parseType());
            }
        }
  			// 组装并返回当前类型参数的抽象树节点
        return toP(F.at(pos).TypeParameter(name, bounds.toList()));
    }
来源：com.sun.tools.javac.parser.JavacParser#parseType
      public JCExpression parseType() {
  			// 解析赋值表达式，TYPE表示当前赋值表达式返回期望为一个类型
        return term(TYPE);
    }
```

### **抽象语法树**

![抽象语法树](https://raw.githubusercontent.com/YangLuchao/javac_study/main/readme/%E6%B3%9B%E5%9E%8B%E6%93%A6%E9%99%A4-%E5%9B%BE/%E6%B3%9B%E5%9E%8B%E7%9B%B8%E5%85%B3%E6%8A%BD%E8%B1%A1%E8%AF%AD%E6%B3%95%E6%A0%91.png)

# 泛型擦除

Java的泛型本质上是语法糖的一种，所以在解语法糖中对泛型信息进行擦除

```java
来源：com.sun.tools.javac.main.JavaCompiler#desugar
	protected void desugar() {
      // 泛型擦除，擦除后返回新的树节点
      env.tree = transTypes.translateTopLevelClass(env.tree, localMake);
	}


```

调用translateTopLevelClass()方法会调用TransTypes类的translate()方法。TransTypes类中有一系列重载的translate()方法，通过translate()方法遍历整个语法树。

**泛型擦除前的树节点**

![image-20220613151630665](https://raw.githubusercontent.com/YangLuchao/javac_study/main/readme/%E6%B3%9B%E5%9E%8B%E6%93%A6%E9%99%A4-%E5%9B%BE/Test10%E6%B3%9B%E5%9E%8B%E6%93%A6%E9%99%A4%E5%89%8D1.png)

## 类的泛型擦除

```java
来源：com.sun.tools.javac.comp.TransTypes#translateClass
void translateClass(ClassSymbol c) {
        ...
            try {
              	// 类型定义
                JCClassDecl tree = (JCClassDecl) env.tree;
              	// 类型参数列表置为空
                tree.typarams = List.nil();
              	// 访问class树节点的内容
                super.visitClassDef(tree);
                ...
                // 树节点类型进行擦除
                tree.type = erasure(tree.type);
            } finally {
               ...
            }
  			...
    }
```

泛型擦除抽象语法树

![image-20220613152114362](https://raw.githubusercontent.com/YangLuchao/javac_study/main/readme/%E6%B3%9B%E5%9E%8B%E6%93%A6%E9%99%A4-%E5%9B%BE/Test10%E7%B1%BB%E5%9E%8B%E6%B3%9B%E5%9E%8B%E6%93%A6%E9%99%A4%E5%90%8E.png)

## 变量的泛型擦除

```java
来源：com.sun.tools.javac.tree.TreeTranslator#visitClassDef
    public void visitClassDef(JCClassDecl tree) {
        tree.mods = translate(tree.mods);
        tree.typarams = translateTypeParams(tree.typarams);
        tree.extending = translate(tree.extending);
        tree.implementing = translate(tree.implementing);
  			// 翻译class节点定义子节点
        tree.defs = translate(tree.defs);
        result = tree;
    }
来源：com.sun.tools.javac.comp.TransTypes#visitVarDef
    public void visitVarDef(JCVariableDecl tree) {
  			// 变量类型翻译
        tree.vartype = translate(tree.vartype, null);
  			// 初始值翻译
        tree.init = translate(tree.init, tree.sym.erasure(types));
        // 类型擦除
        tree.type = erasure(tree.type);
        result = tree;
    }
来源：com.sun.tools.javac.code.Types#erasure(com.sun.tools.javac.code.Type, boolean)
// 对泛型擦除的方法都会间接调用到
      private Type erasure(Type t, boolean recurse) {
        if (t.tag <= lastBaseTag)
            return t; /* fast special case */
        else
          	// erasure.visit返回值解释擦除后的类型
            return erasure.visit(t, recurse);
        }

```

泛型擦除后的抽象语法树

![image-20220613154104691](https://raw.githubusercontent.com/YangLuchao/javac_study/main/readme/%E6%B3%9B%E5%9E%8B%E6%93%A6%E9%99%A4-%E5%9B%BE/Test10%E5%8F%98%E9%87%8F%E6%B3%9B%E5%9E%8B%E6%93%A6%E9%99%A4%E5%90%8E%E7%9A%84%E6%8A%BD%E8%B1%A1%E8%AF%AD%E6%B3%95%E6%A0%91.png)

# 类型推断

