# Attr类介绍

抽象语法树的标注在Attr类中完成，这个类继承了JCTree.Visitor抽象类并覆写了大部分的visitXxx\(\)方法。在标注类型过程中，类型的查找不像符号一样可以通过符号表查找，但可以通过先确定唯一的符号引用后获取类型，然后对符号及类型进行验证，验证通过后才会将符号及类型保存到相应的语法树节点上。标注完成后的抽象语法树称为标注语法树，标注语法树将为后续编译阶段提供必要的符号及类型信息。 

调用attribTree\(\)方法遍历语法树，实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Attr
Type attribTree(JCTree tree, Env<AttrContext> env, int pkind, Type pt,
String errKey) {
    Env<AttrContext> prevEnv = this.env;
    int prevPkind = this.pkind;
    Type prevPt = this.pt;
    String prevErrKey = this.errKey;
    try {
        this.env = env;
        this.pkind = pkind;
        this.pt = pt;
        this.errKey = errKey;
        tree.accept(this);
        if (tree == breakTree)
            throw new BreakAttr(env);
        return result;
    } finally {
        this.env = prevEnv;
        this.pkind = prevPkind;
        this.pt = prevPt;
        this.errKey = prevErrKey;
    }
}
```

---

attribTree\(\)方法的实现类似于Enter及MemberEnter类中classEnter\(\)方法与memberEnter\(\)方法的实现，attribTree\(\)方法同样需要通过局部变量来保存相关的成员变量的值，相关的成员变量的说明如表12\-2所示。 

表12\-2　成员变量的说明 

![image](https://cdn.staticaly.com/gh/YangLuchao/img_host@master/20230418/image.3x4nsab5aqo0.webp)

其中，pkind与pt就是根据当前表达式语法树所处的上下文环境得出的对符号及类型的期望。如果已经得出实际的符号及类型，就会调用check\(\)方法来验证实际的符号和类型是否与期望的符号和类型兼容。check\(\)方法的实现代码如下： 

---

```java
来源：com.sun.tools.javac.comp.Attr
Type check(JCTree tree, Type owntype, int ownkind, int pkind, Type pt) {
    if ( pt.tag != METHOD && pt.tag != FORALL) {
        if ((ownkind & ~pkind) == 0) { // 验证符号的兼容性
            owntype = chk.checkType(_, owntype, pt, errKey);
 // 验证类型的兼容性
        } else {
            log.error(_, "unexpected.type",_,_);
            owntype = types.createErrorType(owntype);
        }
    }
    tree.type = owntype; // 为语法树标注类型
    return owntype;
}
```

---

参数owntype与ownkind分别表示实际的符号和类型，参数pt与pkind表示期望的符号和类型。当期望的类型不是与方法相关的类型时，将会对符号及类型进行兼容性检查。首先检查实际的符号是否为期望符号的一种，如果是，就会调用Check类的checkType\(\)方法继续对类型的兼容性进行检查，最后将owntype保存到树节点的type变量上，算是完成了对语法树类型的标注。 

checkType\(\)方法对于参数found为非ForAll对象的处理逻辑如下： 

---

```java
来源：com.sun.tools.javac.comp.Check
Type checkType(_, Type found, Type req, String errKey) {
    if (req.tag == NONE)
        return found;
    if (types.isAssignable(found, req, _))
        return found;
    return typeError(_, diags.fragment(errKey), found, req);
}
```

---

当req.tag的值为NONE时，表示对类型没有任何的期望，直接返回实际类型即可；当req.tag的值不为NONE时，调用types.isAssignable\(\)方法判断类型的兼容性，这个方法在第9章已经详细介绍过，这里不再介绍。如果isAssignable\(\)方法返回false，表示类型不兼容，调用typeError\(\)方法创建一个ErrorType对象并返回。 

在Attr类中，调用attribTree\(\)方法的主要有attribType\(\)与attribExpr\(\)方法，这两个方法及相关的应用将在后面进行详细介绍。 
