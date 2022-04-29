package book.chapter8;


import javax.annotation.processing.Messager;
import javax.lang.model.element.*;
import javax.lang.model.util.ElementScanner7;
import javax.tools.Diagnostic;
import java.util.EnumSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 对程序名称规范进行检查的编译器插件，如果程序命名不合规范，则会输出一个编译器警告信息
 */
public class NameCheckScanner extends ElementScanner7<Void, Void> {
    private final Messager messager;

    public NameCheckScanner(Messager messager) {
        this.messager = messager;
    }

    public void checkName(Element e, String regEx, String info) {
        String name = e.getSimpleName().toString();
        Pattern pattern = Pattern.compile(regEx);
        Matcher matcher = pattern.matcher(name);
        if (!matcher.matches()) {
            messager.printMessage(Diagnostic.Kind.WARNING, System.out.format(info, name).toString(), e);
        }
    }

    // 检查方法命名是否合法
    @Override
    public Void visitExecutable(ExecutableElement e, Void p) {
        if (e.getKind() == ElementKind.METHOD) {
            checkName(e, "[a-z][A-Za-z0-9]{0,}", "方法名%s不符合符合驼式命名法， 首字母小写\n");
        }
        super.visitExecutable(e, p);
        return null;
    }

    // 检查变量命名是否合法，如果变量是枚举或常量，则按大写命名检查，否则按照驼式命名法规则检查
    @Override
    public Void visitVariable(VariableElement e, Void p) {
        if (e.getKind() == ElementKind.ENUM_CONSTANT ||
                e.getConstantValue() != null ||
                isConstantVar(e))
            checkName(e, "[A-Z][A-Z_]{0,}", "常量%s不符合要求全部大写字母或下划 线构成，并且第一个字符不能是下划线\n");
        else
            checkName(e, "[a-z][A-Za-z0-9]{0,}", "变量名%s不符合符合驼式命名法， 首字母小写\n");
        return null;
    }

    // 判断一个变量是否是常量
    private boolean isConstantVar(VariableElement e) {
        if (e.getEnclosingElement().getKind() == ElementKind.INTERFACE)
            return true;
        else if (e.getKind() == ElementKind.FIELD
                && e.getModifiers().containsAll(EnumSet.of(Modifier.PUBLIC, Modifier.STATIC, Modifier.FINAL)))
            return true;
        else {
            return false;
        }
    }

    // 检查类型的命名是否合法
    @Override
    public Void visitType(TypeElement e, Void p) {
        scan(e.getTypeParameters(), p);
        checkName(e, "[A-Z][A-Za-z0-9]{0,}", "类名%s不符合驼式命名法\n");
        super.visitType(e, p);
        return null;
    }
}