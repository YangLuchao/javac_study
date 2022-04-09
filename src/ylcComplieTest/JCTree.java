package ylcComplieTest;

abstract class JCTree {
    public abstract void accept(JCTreeVisitor v);
}

abstract class JCExpression extends JCTree {
}

class JCUnary extends JCExpression {
    @Override
    public void accept(JCTreeVisitor visitor) {
        visitor.visitUnary(this);
    }
}

class JCBinary extends JCExpression {
    @Override
    public void accept(JCTreeVisitor visitor) {
        visitor.visitBinary(this);
    }
}

class JCAssign extends JCExpression {
    @Override
    public void accept(JCTreeVisitor visitor) {
        visitor.visitJCAssign(this);
    }
}

abstract class JCTreeVisitor {
    public void visitTree(JCTree that) {
        // Assert.error();
    }

    public void visitJCAssign(JCAssign that) {
        visitTree(that);
    }

    public void visitAssign(JCAssign that) {
        visitTree(that);
    }

    public void visitUnary(JCUnary that) {
        visitTree(that);
    }

    public void visitBinary(JCBinary that) {
        visitTree(that);
    }
}

class Attr extends JCTreeVisitor {
    @Override
    public void visitAssign(JCAssign tree) {
        // 对赋值表达式进行标注
    }

    @Override
    public void visitUnary(JCUnary that) {
        // 对一元表达式进行标注
    }

    @Override
    public void visitBinary(JCBinary that) {
        // 对二元表达式进行标注
    }
}