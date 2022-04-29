package book.chapter4.example10;

import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.code.TypeTags;
import com.sun.tools.javac.file.JavacFileManager;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.util.*;

/**
 * 在语法分析过程中，如果从Token流中分析出了语法树节点的各个变量值，就可以直接调用TreeMaker类中的工厂方法创建树节点，最终根据Java源代码创建出对应的抽象语法树了
 * 手动创建抽象语法树
 */
public class TestTreeMaker {
  // name
  static Names names;
  // make
  static TreeMaker F;
  public static void main(String[] args) {
   // 初始化准备name,make
   Context context = new Context();
   // 注册fileManager
   JavacFileManager.preRegister(context);
   // 初始化make
   F = TreeMaker.instance(context);
   // 初始化name
   names = Names.instance(context);
 	 // public int a = 1;
   // 属性修饰符：JCModifiers对象
   // 修饰符：public
   JCTree.JCModifiers mods = F.Modifiers(Flags.PUBLIC);
   // 属性类型：JCPrimitiveTypeTree基本类型对象
   // TypeTags.INT：基本类型INT类型
   JCTree.JCPrimitiveTypeTree type = F.TypeIdent(TypeTags.INT);
   // 属性名
   Name name = names.fromString("a");
   // 属性值
   // JCLiteral字面量对象
   JCTree.JCLiteral init = F.Literal(TypeTags.INT, "1");
   // 构建成员变量：修饰符，name,成员变量类型，初始化的字面量
   JCTree.JCVariableDecl result = F.VarDef(mods, name, type, init);
   // 修饰符：public
   JCTree.JCModifiers mods1 = F.Modifiers(Flags.PUBLIC);
   // name
   Name name1 = names.fromString("Test");
   ListBuffer<JCTree> defs = new ListBuffer<JCTree>();
   // 放入成员变量
   defs.append(result);
   // 初始化形参列表，没有泛型参数
   List<JCTree.JCTypeParameter> typarams = List.nil();
   // 初始化接口列表，没有实现接口
   List<JCTree.JCExpression> implementing= List.nil();
   // 构建类，放入成员变量：修饰符，name，型参，继承的类，实现的接口，成员变量列表
   JCTree.JCClassDecl jcc = F.ClassDef(mods1, name1,typarams, null,implementing,defs.toList());
   // 类列表
   ListBuffer<JCTree> defsx = new ListBuffer<JCTree>();
   // 放入类
   defsx.add(jcc);
   // 初始化包注解列表列表，没有注解
   List<JCTree.JCAnnotation> packageAnnotations = List.nil();
   // 构建包
   JCTree.JCIdent ifr = F.Ident(names.fromString("chapter4"));
   JCTree.JCExpression pid = ifr;
   // 构建编译单元：包注解，包，类
   JCTree.JCCompilationUnit toplevel = F.TopLevel(packageAnnotations, pid,defsx.toList());

   System.out.println(toplevel.toString());
  }
}