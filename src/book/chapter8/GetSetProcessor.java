package book.chapter8;

import com.sun.source.tree.Tree;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.util.Set;

/**
 * 自定义get处理器
 */
@SupportedAnnotationTypes({ "book.chapter8.Getter" })// 注解类全路径
@SupportedSourceVersion(SourceVersion.RELEASE_7)     // 支持的jdk版本
public class GetSetProcessor extends AbstractProcessor {
  // 用来报告错误、警告或其他提示信息
  private Messager messager;
  // java语法树上下文
  private JavacTrees trees;
  // Java语法树构造器
  private TreeMaker treeMaker;
  // 可以用来创建新的符号
  private Names names;
  
  // 初始化，准备环境和工具类
  @Override
  public synchronized void init(ProcessingEnvironment pe) {
    super.init(pe);
    messager = pe.getMessager();
    // 初始化一颗树
    this.trees = JavacTrees.instance(processingEnv);
    // 拿到当前环境的上下文
    Context context = ((JavacProcessingEnvironment) pe).getContext();
    // 初始化一个语法树构造器
    this.treeMaker = TreeMaker.instance(context);
    // 初始化一个符号构造
    this.names = Names.instance(context);
  }
    
  // 处理
  @Override
  public boolean process(Set<? extends TypeElement> set, RoundEnvironment re) {
    // 获取getter注解关联的类
    Set<? extends Element> elems = re.getElementsAnnotatedWith(Getter.class);
    // new一个树翻译器
    TreeTranslator tt = new TreeTranslator() {
      @Override
      public void visitClassDef(JCTree.JCClassDecl jcClassDecl) {
        List<JCTree.JCVariableDecl> jcVariableDeclList = List.nil();
        // 将类中定义的成员变量保存到jcVariableDeclList列表中
        for (JCTree tree : jcClassDecl.defs) {
          if (tree.getKind().equals(Tree.Kind.VARIABLE)) {
            JCTree.JCVariableDecl jcVariableDecl = (JCTree.JCVariableDecl) tree;
            jcVariableDeclList = jcVariableDeclList.append(jcVariableDecl);
          }
        }
        // 处理每一个成员变量
        for (JCTree.JCVariableDecl jcVariableDecl : jcVariableDeclList) {
          messager.printMessage(Diagnostic.Kind.NOTE, jcVariableDecl.getName() + " has been processed");
          // 调用makeGetterMethodDecl()方法为成员变量创建getXxx()方法
          // 对应的语法树并追加到jcClassDecl.defs中
          jcClassDecl.defs = jcClassDecl.defs.prepend(makeGetterMethodDecl(jcVariableDecl));
        }
        super.visitClassDef(jcClassDecl);
      }
    };
    // 将上面的翻译器放到每个元素中
    for (Element element : elems) {
      JCTree jcTree = trees.getTree(element);
      jcTree.accept(tt);
    }
    return true;
  }
  // 为成员变量创建getXxx()方法对应的语法树
  private JCTree.JCMethodDecl makeGetterMethodDecl(JCTree.JCVariableDecl jcVariableDecl) {
    // 描述列表
    ListBuffer<JCTree.JCStatement> statements = new ListBuffer<>();
    // this关键字描述
    JCTree.JCIdent jci = treeMaker.Ident(names.fromString("this"));
    // 变量描述，放入this关键字
    JCTree.JCFieldAccess jcf = treeMaker.Select(jci, jcVariableDecl.getName());
    // return 描述，放入变量描述
    JCTree.JCReturn jcr = treeMaker.Return(jcf);
    // 放入到描述列表
    statements.append(jcr);
    // 方法体位空
    JCTree.JCBlock body = treeMaker.Block(0, statements.toList());
    // 方法定义
    return treeMaker.MethodDef(
        // 访问等级
        treeMaker.Modifiers(Flags.PUBLIC),
        // 方法名
        getNewMethodName(jcVariableDecl.getName()),
        // 返回值类型
        jcVariableDecl.vartype,
        // 参数类型列表为空
        List.<JCTree.JCTypeParameter>nil(),
        // 参数描述列表为空
        List.<JCTree.JCVariableDecl>nil(),
        // 参数表达式为空(比如...)
        List.<JCTree.JCExpression>nil(),
        // 方法体
        body,
        null);
  }
  // 获取getXxx的方法名
  private Name getNewMethodName(Name name) {
    String s = name.toString();
    String mn = "get" + s.substring(0, 1).toUpperCase() + s.substring(1,name.length());
    return names.fromString(mn);
  }
}