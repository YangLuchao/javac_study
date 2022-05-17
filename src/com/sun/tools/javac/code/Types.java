/*
 * Copyright (c) 2003, 2011, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.sun.tools.javac.code;

import java.lang.ref.SoftReference;
import java.util.*;

import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.List;

import com.sun.tools.javac.jvm.ClassReader;
import com.sun.tools.javac.code.Attribute.RetentionPolicy;
import com.sun.tools.javac.code.Lint.LintCategory;
import com.sun.tools.javac.comp.Check;

import static com.sun.tools.javac.code.Scope.*;
import static com.sun.tools.javac.code.Type.*;
import static com.sun.tools.javac.code.TypeTags.*;
import static com.sun.tools.javac.code.Symbol.*;
import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.BoundKind.*;
import static com.sun.tools.javac.util.ListBuffer.lb;

/**
 * Utility class containing various operations on types.
 *
 * <p>Unless other names are more illustrative, the following naming
 * conventions should be observed in this file:
 *
 * <dl>
 * <dt>t</dt>
 * <dd>If the first argument to an operation is a type, it should be named t.</dd>
 * <dt>s</dt>
 * <dd>Similarly, if the second argument to an operation is a type, it should be named s.</dd>
 * <dt>ts</dt>
 * <dd>If an operations takes a list of types, the first should be named ts.</dd>
 * <dt>ss</dt>
 * <dd>A second list of types should be named ss.</dd>
 * </dl>
 *
 * <p><b>This is NOT part of any supported API.
 * If you write code that depends on this, you do so at your own risk.
 * This code and its internal interfaces are subject to change or
 * deletion without notice.</b>
 */
public class Types {
    protected static final Context.Key<Types> typesKey =
        new Context.Key<Types>();

    final Symtab syms;
    final JavacMessages messages;
    final Names names;
    final boolean allowBoxing;
    // 是否支持协变 在JDK 1.5及之后的版本中，这个值都为true
    final boolean allowCovariantReturns;
    final boolean allowObjectToPrimitiveCast;
    final ClassReader reader;
    final Check chk;
    List<Warner> warnStack = List.nil();
    final Name capturedName;

    // <editor-fold defaultstate="collapsed" desc="Instantiating">
    public static Types instance(Context context) {
        Types instance = context.get(typesKey);
        if (instance == null)
            instance = new Types(context);
        return instance;
    }

    protected Types(Context context) {
        context.put(typesKey, this);
        syms = Symtab.instance(context);
        names = Names.instance(context);
        Source source = Source.instance(context);
        allowBoxing = source.allowBoxing();
        allowCovariantReturns = source.allowCovariantReturns();
        allowObjectToPrimitiveCast = source.allowObjectToPrimitiveCast();
        reader = ClassReader.instance(context);
        chk = Check.instance(context);
        capturedName = names.fromString("<captured wildcard>");
        messages = JavacMessages.instance(context);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="upperBound">
    /**
     * The "rvalue conversion".<br>
     * The upper bound of most types is the type
     * itself.  Wildcards, on the other hand have upper
     * and lower bounds.
     * @param t a type
     * @return the upper bound of the given type
     */
    // 当类型为WildcardType时，调用U()与L()方法分别求上界与下界，当不能确定具体的类型时，
    // 可以调用upperBound()方法与lowerBound()方法求上界与下界
    public Type upperBound(Type t) {
        return upperBound.visit(t);
    }
    // where
        // 只有当t为WildcardType或CapturedType对象时，才可能存在上界
        private final MapVisitor<Void> upperBound = new MapVisitor<Void>() {
            // 当t为下界或无界通配符时，上界可能为Object或形式类型参数中声明的上界，
            // 否则t为上界通配符，调用visit()方法求t.type的上界
            @Override
            public Type visitWildcardType(WildcardType t, Void ignored) {
                if (t.isSuperBound())
                    return t.bound == null ? syms.objectType : t.bound.bound;
                else
                    return visit(t.type);
            }

            @Override
            public Type visitCapturedType(CapturedType t, Void ignored) {
                return visit(t.bound);
            }
        };
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="lowerBound">
    /**
     * The "lvalue conversion".<br>
     * The lower bound of most types is the type
     * itself.  Wildcards, on the other hand have upper
     * and lower bounds.
     * @param t a type
     * @return the lower bound of the given type
     */
    // 当类型为WildcardType时，调用U()与L()方法分别求上界与下界，当不能确定具体的类型时，
    // 可以调用upperBound()方法与lowerBound()方法求上界与下界
    public Type lowerBound(Type t) {
        return lowerBound.visit(t);
    }
    // where
        // 只有当t为WildcardType或CapturedType对象时，才可能存在下界
        private final MapVisitor<Void> lowerBound = new MapVisitor<Void>() {
            // 当t为上界或无界通配符时，下界为null，否则t为下界通配符，调用visit()方法求t.type的下界
            @Override
            public Type visitWildcardType(WildcardType t, Void ignored) {
                return t.isExtendsBound() ? syms.botType : visit(t.type);
            }

            @Override
            public Type visitCapturedType(CapturedType t, Void ignored) {
                return visit(t.getLowerBound());
            }
        };
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="isUnbounded">
    /**
     * Checks that all the arguments to a class are unbounded
     * wildcards or something else that doesn't make any restrictions
     * on the arguments. If a class isUnbounded, a raw super- or
     * subclass can be cast to it without a warning.
     * @param t a type
     * @return true iff the given type is unbounded or raw
     */
    public boolean isUnbounded(Type t) {
        return isUnbounded.visit(t);
    }
    // where
        private final UnaryVisitor<Boolean> isUnbounded = new UnaryVisitor<Boolean>() {

            public Boolean visitType(Type t, Void ignored) {
                return true;
            }

            @Override
            public Boolean visitClassType(ClassType t, Void ignored) {
                List<Type> parms = t.tsym.type.allparams();
                List<Type> args = t.allparams();
                while (parms.nonEmpty()) {
                    WildcardType unb = new WildcardType(syms.objectType,
                                                        BoundKind.UNBOUND,
                                                        syms.boundClass,
                                                        (TypeVar)parms.head);
                    if (!containsType(args.head, unb))
                        return false;
                    parms = parms.tail;
                    args = args.tail;
                }
                return true;
            }
        };
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="asSub">
    /**
     * Return the least specific subtype of t that starts with symbol
     * sym.  If none exists, return null.  The least specific subtype
     * is determined as follows:
     *
     * <p>If there is exactly one parameterized instance of sym that is a
     * subtype of t, that parameterized instance is returned.<br>
     * Otherwise, if the plain type or raw type `sym' is a subtype of
     * type t, the type `sym' itself is returned.  Otherwise, null is
     * returned.
     */
    public Type asSub(Type t, Symbol sym) {
        return asSub.visit(t, sym);
    }
    // where
        private final SimpleVisitor<Type,Symbol> asSub = new SimpleVisitor<Type,Symbol>() {

            public Type visitType(Type t, Symbol sym) {
                return null;
            }

            @Override
            public Type visitClassType(ClassType t, Symbol sym) {
                if (t.tsym == sym)
                    return t;
                Type base = asSuper(sym.type, t.tsym);
                if (base == null)
                    return null;
                ListBuffer<Type> from = new ListBuffer<Type>();
                ListBuffer<Type> to = new ListBuffer<Type>();
                try {
                    adapt(base, t, from, to);
                } catch (AdaptFailure ex) {
                    return null;
                }
                Type res = subst(sym.type, from.toList(), to.toList());
                if (!isSubtype(res, t))
                    return null;
                ListBuffer<Type> openVars = new ListBuffer<Type>();
                for (List<Type> l = sym.type.allparams();
                     l.nonEmpty(); l = l.tail)
                    if (res.contains(l.head) && !t.contains(l.head))
                        openVars.append(l.head);
                if (openVars.nonEmpty()) {
                    if (t.isRaw()) {
                        // The subtype of a raw type is raw
                        res = erasure(res);
                    } else {
                        // Unbound type arguments default to ?
                        List<Type> opens = openVars.toList();
                        ListBuffer<Type> qs = new ListBuffer<Type>();
                        for (List<Type> iter = opens; iter.nonEmpty(); iter = iter.tail) {
                            qs.append(new WildcardType(syms.objectType, BoundKind.UNBOUND, syms.boundClass, (TypeVar) iter.head));
                        }
                        res = subst(res, opens, qs.toList());
                    }
                }
                return res;
            }

            @Override
            public Type visitErrorType(ErrorType t, Symbol sym) {
                return t;
            }
        };
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="isConvertible">
    /**
     * Is t a subtype of or convertiable via boxing/unboxing
     * convertions to s?
     */
    // 拆裝箱后并进行转换
    // 对类型装箱转换与类型拆箱转换进行了支持
    public boolean isConvertible(Type t, Type s, Warner warn) {
        boolean tPrimitive = t.isPrimitive();
        boolean sPrimitive = s.isPrimitive();
        // t与s同时为基本类型或引用类型
        if (tPrimitive == sPrimitive) {
            checkUnsafeVarargsConversion(t, s, warn);
            // 当t与s同时为基本类型或引用类型时，需要调用isSubtypeUncheck()方法进行判断
            // 判断类型相同，就可以转换
            return isSubtypeUnchecked(t, s, warn);
        }
        // 当代码执行到这里时，t与s一个为基本类型，一个为引用类型
        // 当allowBoxing为true时，则表示允许使用类型拆箱转换与类型装箱转换
        if (!allowBoxing)
            return false;
        // 说明t与s中一个为基本类型，另外一个为引用类型，需要进行类型拆箱转换与类型装箱转换。
        // 如果t为基本类型就对t进行类型装箱转换，然后调用isSubtype()方法继续判断；
        // 如果t为引用类型，就对t进行类型拆箱转换，然后调用isSubtype()方法继续判断
        // 类型不同，则拆箱或装箱后可以转换
        return tPrimitive
            ? isSubtype(boxedClass(t).type, s)
            : isSubtype(unboxedType(t), s);
    }
    //where
    private void checkUnsafeVarargsConversion(Type t, Type s, Warner warn) {
        if (t.tag != ARRAY || isReifiable(t)) return;
        ArrayType from = (ArrayType)t;
        boolean shouldWarn = false;
        switch (s.tag) {
            case ARRAY:
                ArrayType to = (ArrayType)s;
                shouldWarn = from.isVarargs() &&
                        !to.isVarargs() &&
                        !isReifiable(from);
                break;
            case CLASS:
                shouldWarn = from.isVarargs() &&
                        isSubtype(from, s);
                break;
        }
        if (shouldWarn) {
            warn.warn(LintCategory.VARARGS);
        }
    }

    /**
     * Is t a subtype of or convertiable via boxing/unboxing
     * convertions to s?
     */
    public boolean isConvertible(Type t, Type s) {
        return isConvertible(t, s, Warner.noWarnings);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="isSubtype">
    /**
     * Is t an unchecked subtype of s?
     */
    public boolean isSubtypeUnchecked(Type t, Type s) {
        return isSubtypeUnchecked(t, s, Warner.noWarnings);
    }
    /**
     * Is t an unchecked subtype of s?
     */
    /*
    isSubtype()方法与isSubtypeUncheck()方法的主要区别就是，
    isSubtypeUnchecked()方法还支持了非检查转换。
        class Parent<T>{ }
        class Sub<T> extends Parent<String>{
            Parent<String> p = new Sub();// 警告，未经检查的转换
        }
     */
    // 判断t是否能转换为s，对非检查转换进行了支持
    // 调用isSubtypeUnchecked()方法的前提是t与s同时为引用类型或同时为基本类型
    public boolean isSubtypeUnchecked(Type t, Type s, Warner warn) {
        // 当t与s为数组类型并且组成数组的元素类型为基本类型时，则这两个基本类型必须相同，
        // 否则递归调用isSubtypeUnchecked()方法将继续对组成数组元素的类型进行判断。
        if (t.tag == ARRAY && s.tag == ARRAY) {
            if (((ArrayType)t).elemtype.tag <= lastBaseTag) {
                return isSameType(elemtype(t), elemtype(s));
            } else {
                ArrayType from = (ArrayType)t;
                ArrayType to = (ArrayType)s;
                if (from.isVarargs() &&
                        !to.isVarargs() &&
                        !isReifiable(from)) {
                    warn.warn(LintCategory.VARARGS);
                }
                return isSubtypeUnchecked(elemtype(t), elemtype(s), warn);
            }
        } else if (isSubtype(t, s)) {
            // 当t与s不同时为数组类型时，则调用isSubtype()方法进行判断
            return true;
        }
        else if (t.tag == TYPEVAR) {
            // 当t为类型变量时，则调用isSubtypeUnchecked()方法判断t的上界类型与s的关系
            return isSubtypeUnchecked(t.getUpperBound(), s, warn);
        }
        else if (s.tag == UNDETVAR) {
            UndetVar uv = (UndetVar)s;
            if (uv.inst != null)
                // 当s为需要推断的类型并且已经推断出具体的类型un.inst时，
                // 则调用isSubtypeUnchecked()方法判断t与uv.inst的关系
                return isSubtypeUnchecked(t, uv.inst, warn);
        }
        else if (!s.isRaw()) {
            // 当s不是裸类型时可能会发生非检查转换，
            // 首先调用asSuper()方法查找t的父类，这个父类的tsym等于s.tsym
            Type t2 = asSuper(t, s.tsym);
            if (t2 != null && t2.isRaw()) {
                // 当s为运行时类型时，不会给出警告
                if (isReifiable(s))
                    warn.silentWarn(LintCategory.UNCHECKED);
                else
                    warn.warn(LintCategory.UNCHECKED);
                return true;
            }
        }
        return false;
    }

    /**
     * Is t a subtype of s?<br>
     * (not defined for Method and ForAll types)
     */
    // 在泛型推断或对实际类型参数进行边界检查时也会调用isSubtype()方法
    final public boolean isSubtype(Type t, Type s) {
        return isSubtype(t, s, true);
    }
    final public boolean isSubtypeNoCapture(Type t, Type s) {
        return isSubtype(t, s, false);
    }


    // isAssignable()方法对常量进行了支持；
    // isConvertible()方法对类型装箱转换与类型拆箱转换进行了支持；
    // isSubtypeUnchecked()方法主要对非检查转换进行了支持，
    // 那么在isSubtypeUnchecked()方法中
    // 调用的isSubtype()方法就需要重点支持还没有支持的具体类型转换:
    // 1:同一性转换；
    // 2:基本类型宽化转换；
    // 3:引用类型宽化转换。
    public boolean isSubtype(Type t, Type s, boolean capture) {
        /* 传递的参数capture的值为true，表示需要对t进行类型捕获
            List<? extends Object> a = new ArrayList<String>();
            Object b = a;
            将类型为List<? extends Object>的变量a的值赋值给变量b时，
            则对List<? extends Object>类型中含有的通配符类型进行类型捕获，
            这样才能参与具体的类型转换
         */
        // 当t与s相同时则直接返回true，这也是对同一性转换的支持
        if (t == s)
            return true;

        // 当s是UndetVar对象，所以tag值为TypeTags.UNDETVAR
        // 大于firstPartialTag的值，调用isSuperType()方法进行处理
        if (s.tag >= firstPartialTag)
            return isSuperType(s, t);

        if (s.isCompound()) {
            for (Type s2 : interfaces(s).prepend(supertype(s))) {
                if (!isSubtype(t, s2, capture))
                    return false;
            }
            return true;
        }

        Type lower = lowerBound(s);
        if (s != lower)
            return isSubtype(capture ? capture(t) : t, lower, false);
        // 判断转换
        return isSubtype.visit(capture ? capture(t) : t, s);
    }
    // where
    // 判断是否可以赋值转换
        private TypeRelation isSubtype = new TypeRelation()
        {

            public Boolean visitType(Type t, Type s) {
                switch (t.tag) {
                    // 当t为byte或char类型时，t是s的子类有以下两种情况
                case BYTE: case CHAR:
                    // 1:t与s是相同的类型，也就是tag的值相同
                    // 2：s是基本类型，t的tag值加2后小于等于s的tag值。
                        // tag的取值在TypeTags类中预先进行了定义，
                        // 其中，BYTE的值为1、CHAR为2、SHORT为3、INT为4。
                        // 因为byte不能直接转换为char，所以t的tag值加2排除了byte转换为char这种情况。
                    return (t.tag == s.tag ||
                              t.tag + 2 <= s.tag && s.tag <= DOUBLE);
                case SHORT: case INT: case LONG: case FLOAT: case DOUBLE:
                    // 当t为除byte、char与boolean外的基本类型时，要求s不能为基本类型并且t的tag值要小于等于s的tag值
                    return t.tag <= s.tag && s.tag <= DOUBLE;
                case BOOLEAN: case VOID:
                    // 当t为boolean或void类型时两个类型要相等
                    return t.tag == s.tag;
                case TYPEVAR:
                    // 当t为类型变量时，则调用isSubtypeNoCapture()方法来判断t的上界类型是否为s的子类
                    return isSubtypeNoCapture(t.getUpperBound(), s);
                    // 当t为null时，s为null或引用类型都可以
                case BOT:
                    return
                        s.tag == BOT || s.tag == CLASS ||
                        s.tag == ARRAY || s.tag == TYPEVAR;
                    // 其他情况下t不会为s的子类
                case WILDCARD: //we shouldn't be here - avoids crash (see 7034495)
                case NONE:
                    return false;
                default:
                    throw new AssertionError("isSubtype " + t.tag);
                }
            }

            private Set<TypePair> cache = new HashSet<TypePair>();

            // 例：9-16
            private boolean containsTypeRecursive(Type t, Type s) {
                TypePair pair = new TypePair(t, s);
                if (cache.add(pair)) {
                    try {
                        // 调用containsType()方法判断t的类型参数是否包含s的类型参数
                        return containsType(t.getTypeArguments(),
                                            s.getTypeArguments());
                    } finally {
                        cache.remove(pair);
                    }
                } else {
                    // 调用containsType()方法判断t的类型参数是否包含s的类型参数
                    return containsType(t.getTypeArguments(),
                                        rewriteSupers(s).getTypeArguments());
                }
            }

            private Type rewriteSupers(Type t) {
                if (!t.isParameterized())
                    return t;
                ListBuffer<Type> from = lb();
                ListBuffer<Type> to = lb();
                adaptSelf(t, from, to);
                if (from.isEmpty())
                    return t;
                ListBuffer<Type> rewrite = lb();
                boolean changed = false;
                for (Type orig : to.toList()) {
                    Type s = rewriteSupers(orig);
                    if (s.isSuperBound() && !s.isExtendsBound()) {
                        s = new WildcardType(syms.objectType,
                                             BoundKind.UNBOUND,
                                             syms.boundClass);
                        changed = true;
                    } else if (s != orig) {
                        s = new WildcardType(upperBound(s),
                                             BoundKind.EXTENDS,
                                             syms.boundClass);
                        changed = true;
                    }
                    rewrite.append(s);
                }
                if (changed)
                    return subst(t.tsym.type, from.toList(), rewrite.toList());
                else
                    return t;
            }

            @Override
            public Boolean visitClassType(ClassType t, Type s) {
                // 查找t的父类
                Type sup = asSuper(t, s.tsym);
                // 父类不为空
                return sup != null
                    // 判断父类的类型和s的关系
                    && sup.tsym == s.tsym
                    // 判断参数化类型 和 封闭类型
                    // 在进行类型参数判断时，如果s不是参数化类型，则类型转换肯定能成功；
                    // 如果s为参数化类型时则需要调用containsTypeRecursive()方法进行判断
                    && (!s.isParameterized() || containsTypeRecursive(s, sup))
                    // 进行封闭类型的判断时，需要调用isSubtypeNoCapture()方法来判断sup的封闭类型是否为s的封闭类型的子类即可
                    && isSubtypeNoCapture(sup.getEnclosingType(),
                                          s.getEnclosingType());
            }

            @Override
            public Boolean visitArrayType(ArrayType t, Type s) {
                // 当s也为数组类型时，
                // 当前方法的判断逻辑与isSubtypeUnchecked()方法中针对数组类型的判断逻辑相同
                if (s.tag == ARRAY) {
                    if (t.elemtype.tag <= lastBaseTag)
                        return isSameType(t.elemtype, elemtype(s));
                    else
                        return isSubtypeNoCapture(t.elemtype, elemtype(s));
                }
                // 当s不为数组类型时，只有为Object、Cloneable或Serializable类型时，
                // visitArrayType()方法才会返回true，
                // 因为数组的超类型除数组外就只有Object、Cloneable与Serializable
                if (s.tag == CLASS) {
                    Name sname = s.tsym.getQualifiedName();
                    return sname == names.java_lang_Object
                        || sname == names.java_lang_Cloneable
                        || sname == names.java_io_Serializable;
                }

                return false;
            }

            @Override
            // 判断t是否是s的子类
            public Boolean visitUndetVar(UndetVar t, Type s) {
                if (t == s || t.qtype == s || s.tag == ERROR || s.tag == UNKNOWN)
                    // t与s相等或者t.qtype与s相等，则直接返回true
                    return true;

                if (t.inst != null)
                    // t.inst不为空，表示推断出了具体的类型，调用types.isSubtypeNoCapture()方法判断t.inst与s的关系
                    return isSubtypeNoCapture(t.inst, s); // TODO: ", warn"?
                // t.inst为空，则往t的hibounds中追加s的值，然后返回true，表示t是s的子类
                t.hibounds = t.hibounds.prepend(s);
                return true;
            }

            @Override
            public Boolean visitErrorType(ErrorType t, Type s) {
                return true;
            }
        };

    /**
     * Is t a subtype of every type in given list `ts'?<br>
     * (not defined for Method and ForAll types)<br>
     * Allows unchecked conversions.
     */
    public boolean isSubtypeUnchecked(Type t, List<Type> ts, Warner warn) {
        for (List<Type> l = ts; l.nonEmpty(); l = l.tail)
            if (!isSubtypeUnchecked(t, l.head, warn))
                return false;
        return true;
    }

    /**
     * Are corresponding elements of ts subtypes of ss?  If lists are
     * of different length, return false.
     */
    public boolean isSubtypes(List<Type> ts, List<Type> ss) {
        while (ts.tail != null && ss.tail != null
               /*inlined: ts.nonEmpty() && ss.nonEmpty()*/ &&
               isSubtype(ts.head, ss.head)) {
            ts = ts.tail;
            ss = ss.tail;
        }
        return ts.tail == null && ss.tail == null;
        /*inlined: ts.isEmpty() && ss.isEmpty();*/
    }

    /**
     * Are corresponding elements of ts subtypes of ss, allowing
     * unchecked conversions?  If lists are of different length,
     * return false.
     **/
    public boolean isSubtypesUnchecked(List<Type> ts, List<Type> ss, Warner warn) {
        while (ts.tail != null && ss.tail != null
               /*inlined: ts.nonEmpty() && ss.nonEmpty()*/ &&
               isSubtypeUnchecked(ts.head, ss.head, warn)) {
            ts = ts.tail;
            ss = ss.tail;
        }
        return ts.tail == null && ss.tail == null;
        /*inlined: ts.isEmpty() && ss.isEmpty();*/
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="isSuperType">
    /**
     * Is t a supertype of s?
     */
    // t是否是s的超类
    public boolean isSuperType(Type t, Type s) {
        switch (t.tag) {
        case ERROR:
            return true;
        case UNDETVAR: {
            UndetVar undet = (UndetVar)t;
            // 当两个类型相等、需要推断的类型变量undet.qtype与s相等或者s为null时
            if (t == s ||
                undet.qtype == s ||
                s.tag == ERROR ||
                s.tag == BOT)
                // isSuperType()方法返回true，表示t是s的父类
                return true;
            if (undet.inst != null)
                // 当推断出UndetVar对象中的类型变量qtype的具体类型时会保存到inst变量中
                return isSubtype(s, undet.inst);
            // 当还没有推断出具体类型时，会将s当作UndeVar对象的一个下界填充到lobounds列表中
            undet.lobounds = undet.lobounds.prepend(s);
            return true;
        }
        default:
            return isSubtype(s, t);
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="isSameType">
    /**
     * Are corresponding elements of the lists the same type?  If
     * lists are of different length, return false.
     */
    public boolean isSameTypes(List<Type> ts, List<Type> ss) {
        while (ts.tail != null && ss.tail != null
               /*inlined: ts.nonEmpty() && ss.nonEmpty()*/ &&
               isSameType(ts.head, ss.head)) {
            ts = ts.tail;
            ss = ss.tail;
        }
        return ts.tail == null && ss.tail == null;
        /*inlined: ts.isEmpty() && ss.isEmpty();*/
    }

    /**
     * Is t the same type as s?
     */
    // 判断两个类型是否相等
    public boolean isSameType(Type t, Type s) {
        return isSameType.visit(t, s);
    }
    // where
        private TypeRelation isSameType = new TypeRelation() {

            public Boolean visitType(Type t, Type s) {
                if (t == s)
                    // 当t与s是同一个类型时，直接返回true
                    return true;

                if (s.tag >= firstPartialTag)
                    // 当s的类型为UndetVar时，调换t与s参数的位置后继续调用visit()方法进行判断
                    return visit(s, t);

                switch (t.tag) {
                case BYTE: case CHAR: case SHORT: case INT: case LONG: case FLOAT:
                case DOUBLE: case BOOLEAN: case VOID: case BOT: case NONE:
                    // 当t为基本类型、void、null与none类型时，两个类型必须是同一个类型才会相等
                    return t.tag == s.tag;
                case TYPEVAR: {
                    if (s.tag == TYPEVAR) {
                        // 如果t与s都是类型变量，那么tsym变量的值必须相同，并且还要调用visit()方法判断两个类型变量的上界
                        return t.tsym == s.tsym &&
                                visit(t.getUpperBound(), s.getUpperBound());
                    }
                    else {
                        //special case for s == ? super X, where upper(s) = u
                        //check that u == t, where u has been set by Type.withTypeVar
                        return s.isSuperBound() &&
                                !s.isExtendsBound() &&
                                visit(t, upperBound(s));
                    }
                }
                default:
                    throw new AssertionError("isSameType " + t.tag);
                }
            }

            @Override
            // 当s为UndetVar对象时，调换t与s参数的位置后继续调用visit()方法进行判断，
            // 调换参数对于判断两个类型是否相等来说等价，
            // 最终会访问visitUndetVar()方法，
            // 这个方法比较UndetVar对象与WildcardType对象后会返回false，
            // 因为WildcardType对象不能代表具体的类型，而UndetVar对象代表一个具体的类型
            public Boolean visitWildcardType(WildcardType t, Type s) {
                if (s.tag >= firstPartialTag)
                    return visit(s, t);
                else
                    return false;
            }

            @Override
            public Boolean visitClassType(ClassType t, Type s) {
                // 当t与s为同一个类型时直接返回true
                if (t == s)
                    return true;

                // 当s的类型为UndetVar时，调换t与s参数的位置后继续调用visit()方法进行判断，
                // 也就是调用visitUndetVar()方法进行判断
                // 例c-8
                if (s.tag >= firstPartialTag)
                    return visit(s, t);

                // 当s为下界通配符时，只有当t与s的上界及t与s的下界都相同时，类型才可能相同。
                if (s.isSuperBound() && !s.isExtendsBound())
                    return visit(t, upperBound(s)) && visit(t, lowerBound(s));

                // 当t与s都是组合类型时，如果两个类型相同，则父类和所有实现接口必须相同
                if (t.isCompound() && s.isCompound()) {
                    if (!visit(supertype(t), supertype(s)))
                        return false;
                    // 通过集合set来提高比较效率
                    HashSet<SingletonType> set = new HashSet<SingletonType>();
                    // 在遍历第一个组合类型的所有接口时将接口封装为SingletonType对象
                    for (Type x : interfaces(t))
                        set.add(new SingletonType(x));
                    // 然后在遍历第2个组合类型时也将所有接口封装为SingletonType对象并从set集合中移除
                    for (Type x : interfaces(s)) {
                        // 如果移除失败，表示第一个组合类型没有对应的SingletonType对象，直接返回false
                        if (!set.remove(new SingletonType(x)))
                            return false;
                    }
                    // 最后还需要判断set是否为空
                    return (set.isEmpty());
                }
                // 当两个类型的tsym相同、封闭类型相同、实际的类型参数类型也相同时，则这两个ClassType对象相同
                // 例c-10
                return t.tsym == s.tsym
                    && visit(t.getEnclosingType(), s.getEnclosingType())
                        // 比较实际类型参数的类型
                    && containsTypeEquivalent(t.getTypeArguments(), s.getTypeArguments());
            }

            @Override
            public Boolean visitArrayType(ArrayType t, Type s) {
                // 当t与s是同一个类型时，visitArrayType()方法直接返回true
                if (t == s)
                    return true;

                if (s.tag >= firstPartialTag)
                    // 当s的类型为UndetVar时，调换t与s参数的位置后继续调用visit()方法进行判断
                    return visit(s, t);

                // 当t与s都为数组类型时，调用containsTypeEquivalent()方法判断组成两个数组的元素类型是否相同
                return s.tag == ARRAY
                    && containsTypeEquivalent(t.elemtype, elemtype(s));
            }

            @Override
            public Boolean visitMethodType(MethodType t, Type s) {
                // 当t为MethodType类型时，如果s为MethodType或ForAll类型并且它们的形式参数的类型和返回类型相同时，返回true
                return hasSameArgs(t, s) && visit(t.getReturnType(), s.getReturnType());
            }

            @Override
            public Boolean visitPackageType(PackageType t, Type s) {
                // Javac中表示相同包名使用同一个PackageSymbol对象表示，
                // 而PackageSymbol对象相同时PackageType对象也相同，
                // 因为在创建PackageSymbol对象时就创建了对应的PackageType对象，
                // 直接使用“==”即可
                return t == s;
            }

            @Override
            public Boolean visitForAll(ForAll t, Type s) {
                // 当t为ForAll类型时，s也必须为ForAll类型，否则方法直接返回false
                if (s.tag != FORALL)
                    return false;

                ForAll forAll = (ForAll)s;
                // 首先调用hasSameBounds()方法比较声明的类型参数，主要比较类型参数的数量及上界
                return hasSameBounds(t, forAll)
                        // 调用subst()方法将forAll.qtype中使用到的自身的类型变量forAll.tvars全部替换为t类型声明的类型变量，
                        // 因为两个方法中声明的类型变量如果等价，并没有用同一个对象来表示，
                        // 所以需要替换forAll.qtype中的返回类型、形式参数类型及抛出的异常类型，以便更好地比较两个类型
                    && visit(t.qtype, subst(forAll.qtype, forAll.tvars, t.tvars));
            }

            @Override
            public Boolean visitUndetVar(UndetVar t, Type s) {
                // 当s为WildcardType对象时，由于UndetVar对象表示具体的类型，
                // 而WildcardType对象不表示具体的类型，所以两个类型无法进行比较，直接返回false
                if (s.tag == WILDCARD)
                    return false;
                // 当t与s相同或者t.qtype与s相同时，返回true，其中qtype变量中保存的就是要进行推断的类型变量，如果类型变量一样，推断出来的最终类型肯定也相同
                if (t == s || t.qtype == s || s.tag == ERROR || s.tag == UNKNOWN)
                    return true;

                if (t.inst != null)
                    // 当t.inst不为空时，表示已经推断出了具体的类型，调用visit()方法判断t.inst的值是否与s相等
                    return visit(t.inst, s);

                // 如果t.inst的值为空，直接将s赋值给t.inst，
                // 也就是假设t推断出来的具体类型为s，那么需要检查这个推断的类型是否满足条件
                t.inst = fromUnknownFun.apply(s);
                // 判断上界
                for (List<Type> l = t.lobounds; l.nonEmpty(); l = l.tail) {
                    if (!isSubtype(l.head, t.inst))
                        return false;
                }
                // 判断下界
                for (List<Type> l = t.hibounds; l.nonEmpty(); l = l.tail) {
                    if (!isSubtype(t.inst, l.head))
                        return false;
                }
                return true;
            }

            @Override
            public Boolean visitErrorType(ErrorType t, Type s) {
                return true;
            }
        };
    // </editor-fold>

    /**
     * A mapping that turns all unknown types in this type to fresh
     * unknown variables.
     */
    public Mapping fromUnknownFun = new Mapping("fromUnknownFun") {
            public Type apply(Type t) {
                if (t.tag == UNKNOWN) return new UndetVar(t);
                else return t.map(this);
            }
        };
    // </editor-fold>

    // 判断s是否包含t
    public boolean containedBy(Type t, Type s) {
        switch (t.tag) {
            // 当t为待推断对象
        case UNDETVAR:
            // 当s为通配符对象
            if (s.tag == WILDCARD) {
                // 为了让t被s所包含，可能会向UndetVar对象的hibounds或lobounds中添加边界限定条件
                UndetVar undetvar = (UndetVar)t;
                WildcardType wt = (WildcardType)s;
                switch(wt.kind) {
                    case UNBOUND:
                    case EXTENDS: {
                        // 当wt为无界或上界通配符时，获取上界bound，
                        Type bound = upperBound(s);
                        // 然后检查undetvar中所有的下界是否为上界bound的子类
                        for (Type t2 : undetvar.lobounds) {
                            if (!isSubtype(t2, bound))
                                // 如果存在某个下界不是上界bound的子类，那么不存在包含的关系，直接返回false，
                                return false;
                        }
                        // 否则将s的上界作为undetvar的上界添加到hibounds列表中；
                        undetvar.hibounds = undetvar.hibounds.prepend(bound);
                        break;
                    }
                    case SUPER: {
                        // 当wt为下界通配符时获取下界bound，
                        Type bound = lowerBound(s);
                        // 然后检查这个下界bound是否为undetvar所有上界的子类
                        for (Type t2 : undetvar.hibounds) {
                            if (!isSubtype(bound, t2))
                                // 如果存在某个下界不是上界bound的子类，那么不存在包含的关系，直接返回false，
                                return false;
                        }
                        // 否则将s的下界添加到UndetVar对象的lobounds中
                        undetvar.lobounds = undetvar.lobounds.prepend(bound);
                        break;
                    }
                }
                return true;
            } else {
                // s为非通配符对象，调用isSameType()方法来判断，
                // 表示两个类型必须相等才能有包含的关系
                return isSameType(t, s);
            }
        case ERROR:
            return true;
        default:
            // 当t不为UndetVar对象时调用containsType()方法继续判断
            return containsType(s, t);
        }
    }

    // 判断一个类型是否包含另外一个类型
    boolean containsType(List<Type> ts, List<Type> ss) {
        while (ts.nonEmpty() && ss.nonEmpty()
               && containsType(ts.head, ss.head)) {
            ts = ts.tail;
            ss = ss.tail;
        }
        return ts.isEmpty() && ss.isEmpty();
    }

    /**
     * Check if t contains s.
     *
     * <p>T contains S if:
     *
     * <p>{@code L(T) <: L(S) && U(S) <: U(T)}
     *
     * <p>This relation is only used by ClassType.isSubtype(), that
     * is,
     *
     * <p>{@code C<S> <: C<T> if T contains S.}
     *
     * <p>Because of F-bounds, this relation can lead to infinite
     * recursion.  Thus we must somehow break that recursion.  Notice
     * that containsType() is only called from ClassType.isSubtype().
     * Since the arguments have already been checked against their
     * bounds, we know:
     *
     * <p>{@code U(S) <: U(T) if T is "super" bound (U(T) *is* the bound)}
     *
     * <p>{@code L(T) <: L(S) if T is "extends" bound (L(T) is bottom)}
     *
     * @param t a type
     * @param s a type
     */
    // 判断一个类型是否包含另外一个类型
    // 判断t是否包含s
    public boolean containsType(Type t, Type s) {
        return containsType.visit(t, s);
    }
    // where
        private TypeRelation containsType = new TypeRelation() {

            // 类型为WildcardType时，可以调用U()方法求下界
            private Type U(Type t) {
                while (t.tag == WILDCARD) {
                    WildcardType w = (WildcardType)t;
                    // 当t为无界通配符类型或下界通配符类型时，调用isSuperBound()方法返回true，
                    if (w.isSuperBound())
                        // 这个通配符类型的上界如果没有指定，则默认为Object，
                        // 否则通过w.bound.bound获取形式类型参数声明时的上界
                        return w.bound == null ? syms.objectType : w.bound.bound;
                    else
                        // 当w为上界通配符时直接获取上界通配符的上界w.type
                        t = w.type;
                }
                return t;
            }

            // 类型为WildcardType时，可以调用L()方法求上界
            private Type L(Type t) {
                while (t.tag == WILDCARD) {
                    WildcardType w = (WildcardType)t;
                    // 当t为无界通配符类型或上界通配符类型时，调用isExtendsBound()方法返回true
                    if (w.isExtendsBound())
                        // 这个通配符类型的下界为null
                        return syms.botType;
                    else
                        // 否则取上界通配符声明的上界w.type
                        t = w.type;
                }
                return t;
            }
            // 当t为非WildcardType(不是通配符类型)或UndetVar(是待推断类型)类型时会调用visitType()方法进行判断
            @Override
            public Boolean visitType(Type t, Type s) {
                // 当s为UndetVar(待推断对象)对象时，判断t是否包含s
                if (s.tag >= firstPartialTag)
                    // 判断t是否包含s
                    return containedBy(s, t);
                else
                    return isSameType(t, s);
            }

            @Override
            // 判断t是否包含s
            public Boolean visitWildcardType(WildcardType t, Type s) {
                // 当t为WildcardType对象且s为UndetVar对象时，
                // 调用containedBy()方法进行判断，否则有3种情况会让t包含s
                if (s.tag >= firstPartialTag)
                    return containedBy(s, t);
                else {
                    // 1:t与s都是通配符类型并且相同
                    return isSameWildcard(t, s)
                        // isCaptureOf()方法返回true
                        || isCaptureOf(s, t)
                        || (
                            // 当t有上界时，s的上界必须是t的上界的子类型；
                            (t.isExtendsBound() || isSubtypeNoCapture(L(t), lowerBound(s)))
                                        &&
                             // 当t有下界时，t的下界必须是s的下界的子类型
                            (t.isSuperBound() || isSubtypeNoCapture(upperBound(s), U(t))));
                }
                // t是WildcardType类型，所以可以调用L()与U()方法求下界与上界
                // s不能确定具体的类型，所以调用lowerBound()与upperBound()方法求下界与上界
                // 例c-11/c-12
            }

            @Override
            // 判断t是否包含s
            public Boolean visitUndetVar(UndetVar t, Type s) {
                // 当t为UndetVar(是待推断对象)对象且s不为WildcardType(非通配符对象)对象时，
                // 调用isSameType()方法进行判断
                if (s.tag != WILDCARD)
                    return isSameType(t, s);
                else
                    // 当t为UndetVar对象且s为WildcardType对象时，由于UndetVar对象代表一个具体的类型，
                    // 而WildcardType对象不代表一个具体的类型，所以t肯定不包含s，方法直接返回false
                    return false;
            }

            @Override
            public Boolean visitErrorType(ErrorType t, Type s) {
                return true;
            }
        };

    // 在调用isCaptureOf()方法之前已经调用过isSameWildcard()方法，所以当s为WildcardType时两个类型不相等
    // 当前方法仅对s为CapturedType对象进行判断，
    // 也就是调用isSameWildcard()方法比较t与CapturedType对象s的wildcard
    public boolean isCaptureOf(Type s, WildcardType t) {
        if (s.tag != TYPEVAR || !((TypeVar)s).isCaptured())
            return false;
        return isSameWildcard(t, ((CapturedType)s).wildcard);
    }

    // 当t与s都为通配符类型并且kind与type的值相同时，isSameWildcard()方法将返回true
    public boolean isSameWildcard(WildcardType t, Type s) {
        if (s.tag != WILDCARD)
            return false;
        WildcardType w = (WildcardType)s;
        return w.kind == t.kind && w.type == t.type;
    }

    // 比较两个类型列表里的所有类型是否全部相等
    public boolean containsTypeEquivalent(List<Type> ts, List<Type> ss) {
        while (ts.nonEmpty() && ss.nonEmpty()
               && containsTypeEquivalent(ts.head, ss.head)) {
            ts = ts.tail;
            ss = ss.tail;
        }
        return ts.isEmpty() && ss.isEmpty();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="isCastable">
    public boolean isCastable(Type t, Type s) {
        return isCastable(t, s, Warner.noWarnings);
    }

    /**
     * Is t is castable to s?<br>
     * s is assumed to be an erased type.<br>
     * (not defined for Method and ForAll types).
     */
    // 当需要进行强制类型转换时，那么可以调用Types类中的isCastable()方法进行判断
    public boolean isCastable(Type t, Type s, Warner warn) {
        // 当t与s相同时则直接返回，这也是对同一性转换的支持
        if (t == s)
            return true;

        if (t.isPrimitive() != s.isPrimitive())
            // t与s一个为基本类型，一个为引用类型
            // 会发生类型装箱转换与类型拆箱转换后进行判断
            return allowBoxing && (
                    isConvertible(t, s, warn)
                    || (allowObjectToPrimitiveCast &&
                        s.isPrimitive() &&
                        isSubtype(boxedClass(s).type, t)));
        if (warn != warnStack.head) {
            try {
                warnStack = warnStack.prepend(warn);
                checkUnsafeVarargsConversion(t, s, warn);
                return isCastable.visit(t,s);
            } finally {
                warnStack = warnStack.tail;
            }
        } else {
            // t与s同时为基本类型或引用类型
            return isCastable.visit(t,s);
        }
    }
    // where
        // 判断强制类型转换，返回值都是Boolean类型，表示是否能够进行强制转换
        private TypeRelation isCastable = new TypeRelation() {

            public Boolean visitType(Type t, Type s) {
                if (s.tag == ERROR)
                    return true;

                switch (t.tag) {
                case BYTE: case CHAR: case SHORT: case INT: case LONG: case FLOAT:
                case DOUBLE:
                    // 当t为除了boolean类型之外的基本类型时，s也必须是除了boolean类型之外的基本类型
                    return s.tag <= DOUBLE;
                case BOOLEAN:
                    // 当t为boolean类型时，s也必须为boolean类型；
                    return s.tag == BOOLEAN;
                case VOID:
                    // 当t为void类型时返回false，表示void类型不能强制转换为任何类型；
                    return false;
                case BOT:
                    // 当t为null时，调用isSubtype()方法进行判断，此时只要求s为引用类型即可。
                    return isSubtype(t, s);
                default:
                    throw new AssertionError();
                }
            }

            @Override
            public Boolean visitWildcardType(WildcardType t, Type s) {
                return isCastable(upperBound(t), s, warnStack.head);
            }

            @Override
            // 参数t为原类型，而s为目标转换类型。假设t的类型为T，而s的类型为S，
            // 当T为类或者接口时都会调用这个方法
            public Boolean visitClassType(ClassType t, Type s) {
                if (s.tag == ERROR || s.tag == BOT)
                    return true;
                // 当s为类型变量时，判断t是否能够强制转换为s的上界
                if (s.tag == TYPEVAR) {
                    // 如果S是一个类型变量，将S替换为类型变量上界，如果上界仍然是类型变量，
                    // 则继续查找这个类型变量上界，直到找到一个非类型变量的类型为止
                    if (isCastable(t, s.getUpperBound(), Warner.noWarnings)) {
                        warnStack.head.warn(LintCategory.UNCHECKED);
                        return true;
                    } else {
                        return false;
                    }
                }
                // 当t或s为组合类型时，组合类型的父类和接口必须能够强制转换为另外一个类型
                // 当T为组合类型时，必须要求T的父类及实现的接口都能转换为目标类型S，否则不能进行强制类型转换
                if (t.isCompound()) {
                    Warner oldWarner = warnStack.head;
                    warnStack.head = Warner.noWarnings;
                    if (!visit(supertype(t), s))
                        return false;
                    for (Type intf : interfaces(t)) {
                        if (!visit(intf, s))
                            return false;
                    }
                    if (warnStack.head.hasLint(LintCategory.UNCHECKED))
                        oldWarner.warn(LintCategory.UNCHECKED);
                    return true;
                }

                // 当S为组合类型时是相同的情况，
                // visitClassType()方法会调换t与s参数的位置，然后继续调用isCompound()方法进行判断
                if (s.isCompound()) {
                    // call recursively to reuse the above code
                    return visitClassType((ClassType)s, t);
                }
                // t为接口或类，s为接口、类或数组
                // 当T为接口时
                //（1）如果S是一个非final修饰的类型，那么要将接口T转换为非final修饰的类型。这与将一个非final的类转换为接口是相同的情况，也就是不允许T和S有不同的参数化父类型，这个父类型在擦写后是同一个类型。除此之外，其他转换都是被允许的。
                //（2）如果S是一个final修饰类型，因为接口不能由final修饰，所以S只能是个类。由于S是由final修饰的类，因而S必须直接或间接实现T接口，否则两个类型不能进行强制类型转换，因为S已经没有子类可以继承S类实现T接口了。
                //（3）当S是数组类型时，那么T一定是Serializable或Cloneable接口，否则两个类型不能进行强制类型转换。
                if (s.tag == CLASS || s.tag == ARRAY) {
                    boolean upcast;
                    if ((upcast = isSubtype(erasure(t), erasure(s)))
                        || isSubtype(erasure(s), erasure(t))) {
                        if (!upcast && s.tag == ARRAY) {
                            // 如果S是一个数组类型，那么T一定是Object类
                            if (!isReifiable(s))
                                warnStack.head.warn(LintCategory.UNCHECKED);
                            return true;
                        } else if (s.isRaw()) {
                            return true;
                        } else if (t.isRaw()) {
                            if (!isUnbounded(s))
                                warnStack.head.warn(LintCategory.UNCHECKED);
                            return true;
                        }
                        // Assume |a| <: |b|
                        final Type a = upcast ? t : s;
                        final Type b = upcast ? s : t;
                        final boolean HIGH = true;
                        final boolean LOW = false;
                        final boolean DONT_REWRITE_TYPEVARS = false;
                        Type aHigh = rewriteQuantifiers(a, HIGH, DONT_REWRITE_TYPEVARS);
                        Type aLow  = rewriteQuantifiers(a, LOW,  DONT_REWRITE_TYPEVARS);
                        Type bHigh = rewriteQuantifiers(b, HIGH, DONT_REWRITE_TYPEVARS);
                        Type bLow  = rewriteQuantifiers(b, LOW,  DONT_REWRITE_TYPEVARS);
                        Type lowSub = asSub(bLow, aLow.tsym);
                        Type highSub = (lowSub == null) ? null : asSub(bHigh, aHigh.tsym);
                        if (highSub == null) {
                            final boolean REWRITE_TYPEVARS = true;
                            aHigh = rewriteQuantifiers(a, HIGH, REWRITE_TYPEVARS);
                            aLow  = rewriteQuantifiers(a, LOW,  REWRITE_TYPEVARS);
                            bHigh = rewriteQuantifiers(b, HIGH, REWRITE_TYPEVARS);
                            bLow  = rewriteQuantifiers(b, LOW,  REWRITE_TYPEVARS);
                            lowSub = asSub(bLow, aLow.tsym);
                            highSub = (lowSub == null) ? null : asSub(bHigh, aHigh.tsym);
                        }
                        if (highSub != null) {
                            if (!(a.tsym == highSub.tsym && a.tsym == lowSub.tsym)) {
                                Assert.error(a.tsym + " != " + highSub.tsym + " != " + lowSub.tsym);
                            }
                            if (!disjointTypes(aHigh.allparams(), highSub.allparams())
                                && !disjointTypes(aHigh.allparams(), lowSub.allparams())
                                && !disjointTypes(aLow.allparams(), highSub.allparams())
                                && !disjointTypes(aLow.allparams(), lowSub.allparams())) {
                                if (upcast ? giveWarning(a, b) :
                                    giveWarning(b, a))
                                    warnStack.head.warn(LintCategory.UNCHECKED);
                                return true;
                            }
                        }
                        if (isReifiable(s))
                            return isSubtypeUnchecked(a, b);
                        else
                            return isSubtypeUnchecked(a, b, warnStack.head);
                    }

                    // 当代码执行到这里时，t与s的泛型擦除后的类型不会有父子关系
                    if (s.tag == CLASS) {
                        if ((s.tsym.flags() & INTERFACE) != 0) {
                            return ((t.tsym.flags() & FINAL) == 0)
                                    // 调用sideCast()方法表示t与s一定是非final修饰的类型，因此不能转换的情况只有同时实现了不同的参数化父类型，这个父类型在擦写后是同一个类型
                                ? sideCast(t, s, warnStack.head)
                                    // 调用sideCastFinal()方法表示t是final修饰的类型而s是接口，或者t是接口而s是final修饰的类型
                                    // 例9-22
                                : sideCastFinal(t, s, warnStack.head);
                        } else if ((t.tsym.flags() & INTERFACE) != 0) {
                            return ((s.tsym.flags() & FINAL) == 0)
                                ? sideCast(t, s, warnStack.head)
                                : sideCastFinal(t, s, warnStack.head);
                        } else {
                            // unrelated class types
                            return false;
                        }
                    }
                }
                return false;
            }

            @Override
            // 假设参数t的类型为T，而s的类型为S时，
            // 调用visitArrayType()方法处理当T为数组类型时的情况时，可根据S的不同，分情况处理，
            public Boolean visitArrayType(ArrayType t, Type s) {
                switch (s.tag) {
                case ERROR:
                case BOT:
                    return true;
                case TYPEVAR:
                    // 如果S是一个类型变量，这时候S类型变量的上界必须为Object、Serializable或Cloneable
                    // S通过强制类型转换能够转换为T类型变量的上界，否则Javac将报编译错误
                    //
                    if (isCastable(s, t, Warner.noWarnings)) {
                        warnStack.head.warn(LintCategory.UNCHECKED);
                        return true;
                    } else {
                        return false;
                    }
                case CLASS:
                    // 如果S是一个类，那么S必须是Object；如果S是接口，那么S必须是Serializable或者Cloneable。
                    // 两者之间有父子关系，直接调用isSubtype()方法判断即可。
                    return isSubtype(t, s);
                case ARRAY:
                    // 如果S是一个数组，那么调用elemtype()方法得到组成数组的元素类型
                    if (elemtype(t).tag <= lastBaseTag ||
                            elemtype(s).tag <= lastBaseTag) {
                        // 如果有一个为基本类型，那么另外一个也必须为基本类型，而且两者必须相等
                        return elemtype(t).tag == elemtype(s).tag;
                    } else {
                        // 如果都是引用类型，那么组成数组T的元素类型必须能够通过强制类型转换转换为组成数组S的元素类型，因此继续调用visit()方法来判断
                        return visit(elemtype(t), elemtype(s));
                    }
                default:
                    return false;
                }
            }

            @Override
            // 当t与s同时为类型变量时，如果t为s的子类或t的上界能够强制转换为s时，则方法将返回true。
            public Boolean visitTypeVar(TypeVar t, Type s) {
                switch (s.tag) {
                case ERROR:
                case BOT:
                    return true;
                case TYPEVAR:
                    if (isSubtype(t, s)) {
                        return true;
                    } else if (isCastable(t.bound, s, Warner.noWarnings)) {
                        warnStack.head.warn(LintCategory.UNCHECKED);
                        return true;
                    } else {
                        return false;
                    }
                default:
                    return isCastable(t.bound, s, warnStack.head);
                }
            }

            @Override
            public Boolean visitErrorType(ErrorType t, Type s) {
                return true;
            }
        };
    // </editor-fold>

    // 判断ts列表中和ss列表中是否有类型互斥，有返回true
    public boolean disjointTypes(List<Type> ts, List<Type> ss) {
        // 当两个类型的实际类型参数列表ts与ss中有互斥类型时，方法将返回true
        while (ts.tail != null && ss.tail != null) {
            if (disjointType(ts.head, ss.head)) return true;
            ts = ts.tail;
            ss = ss.tail;
        }
        return false;
    }

    /**
     * Two types or wildcards are considered disjoint if it can be
     * proven that no type can be contained in both. It is
     * conservative in that it is allowed to say that two types are
     * not disjoint, even though they actually are.
     *
     * The type C<X> is castable to C<Y> exactly if X and Y are not
     * disjoint.
     */
    // 调用disjointType()方法判断列表ts与ss中对应位置的类型是否有类型交集，
    // 如果没有，则将返回true
    public boolean disjointType(Type t, Type s) {
        return disjointType.visit(t, s);
    }
    // where
        private TypeRelation disjointType = new TypeRelation() {

            private Set<TypePair> cache = new HashSet<TypePair>();

            @Override
            public Boolean visitType(Type t, Type s) {
                if (s.tag == WILDCARD)
                    return visit(s, t);
                else
                    return notSoftSubtypeRecursive(t, s) || notSoftSubtypeRecursive(s, t);
            }

            private boolean isCastableRecursive(Type t, Type s) {
                TypePair pair = new TypePair(t, s);
                if (cache.add(pair)) {
                    try {
                        return Types.this.isCastable(t, s);
                    } finally {
                        cache.remove(pair);
                    }
                } else {
                    return true;
                }
            }

            // 判断两种类型是否有交集，是否互斥
            // 参数t与s都不为通配符类型
            private boolean notSoftSubtypeRecursive(Type t, Type s) {
                TypePair pair = new TypePair(t, s);
                if (cache.add(pair)) {
                    try {
                        return Types.this.notSoftSubtype(t, s);
                    } finally {
                        cache.remove(pair);
                    }
                } else {
                    return false;
                }
            }

            @Override
            // 当t或s中的任何一个类型为通配符类型时，都会调用visitWildcardType()方法进行判断
            // 在visitWildcardType()方法中调用notSoftSubtype()方法时，
            // 如果类型中有代表下界的类型，那么t会被指定为下界，或者t与s都为上界。
            // 无论是上界还是下界，t与s可能为类型变量、类或接口。
            public Boolean visitWildcardType(WildcardType t, Type s) {
                if (t.isUnbound())
                    return false;

                if (s.tag != WILDCARD) {
                    if (t.isExtendsBound())
                        // 判断s与t的上界的关系
                        return notSoftSubtypeRecursive(s, t.type);
                    else
                        // 判断t的下界与s的关系
                        return notSoftSubtypeRecursive(t.type, s);
                }

                if (s.isUnbound())
                    return false;
                // 代码执行到这里，t与s肯定都为通配符类型，而且都不是无界通配符类型
                if (t.isExtendsBound()) {
                    if (s.isExtendsBound())
                        // t与s都有上界
                        return !isCastableRecursive(t.type, upperBound(s));
                    else if (s.isSuperBound())
                        // t有上界而s有下界
                        return notSoftSubtypeRecursive(lowerBound(s), t.type);
                } else if (t.isSuperBound()) {
                    // t为下界而s有上界
                    if (s.isExtendsBound())
                        // 当t与s都为下界通配时，一定有类型交集，至少有Object类
                        return notSoftSubtypeRecursive(t.type, upperBound(s));
                }
                return false;
            }
        };
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="lowerBoundArgtypes">
    /**
     * Returns the lower bounds of the formals of a method.
     */
    public List<Type> lowerBoundArgtypes(Type t) {
        return map(t.getParameterTypes(), lowerBoundMapping);
    }
    private final Mapping lowerBoundMapping = new Mapping("lowerBound") {
            public Type apply(Type t) {
                return lowerBound(t);
            }
        };
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="notSoftSubtype">
    /**
     * This relation answers the question: is impossible that
     * something of type `t' can be a subtype of `s'? This is
     * different from the question "is `t' not a subtype of `s'?"
     * when type variables are involved: Integer is not a subtype of T
     * where <T extends Number> but it is not true that Integer cannot
     * possibly be a subtype of T.
     */
    // 判断两种类型是否有交集，是否互斥
    // 不互斥返回false
    // 互斥返回true
    public boolean notSoftSubtype(Type t, Type s) {
        // 如果类型中有代表下界的类型，那么t会被指定为下界，
        // 或者t与s都为上界。无论是上界还是下界，t与s可能为类型变量、类或接口。
        if (t == s)
            return false;
        if (t.tag == TYPEVAR) {
            // 当t.tag值为TYPEVAR时，t可能为TypeVar对象或CapturedType对象
            TypeVar tv = (TypeVar) t;
            // 调用isCastable()方法判断t的上界是否可以转换为s的上界（当s也有上界时，取上界，否则就是s本身）
            // 当isCastable()方法返回true时，表示t与s有类型交集，notSoftSubtype()方法返回false，两个类型不互斥
            return !isCastable(tv.bound,
                               // relaxBound()方法以获取类型变量的上界
                               relaxBound(s),
                               Warner.noWarnings);
        }
        if (s.tag != WILDCARD)
            // relaxBound()方法以获取类型变量的上界
            s = upperBound(s);
                // relaxBound()方法以获取类型变量的上界
        return !isSubtype(t, relaxBound(s));
    }

    private Type relaxBound(Type t) {
        // 当类型变量的上界仍然为类型变量时，继承获取类型变量上界，直到找到一个非类型类型的类型为止。
        if (t.tag == TYPEVAR) {
            while (t.tag == TYPEVAR)
                t = t.getUpperBound();
            t = rewriteQuantifiers(t, true, true);
        }
        return t;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="isReifiable">
    // 运行时类型是运行时存在的类型，通过调用isReifiable()方法判断
    public boolean isReifiable(Type t) {
        return isReifiable.visit(t);
    }
    // where
    // 运行时类型判断
        private UnaryVisitor<Boolean> isReifiable = new UnaryVisitor<Boolean>() {

            public Boolean visitType(Type t, Void ignored) {
                return true;
            }

            @Override
            // 当t为类或接口时
            public Boolean visitClassType(ClassType t, Void ignored) {
                // 如果t是组合类型或参数化类型，则将会返回false
                if (t.isCompound())
                    return false;
                else {
                    // 当参数化类型中的实际类型参数都为无界通配符时，这个类型仍然是运行时类型
                    if (!t.isParameterized())
                        return true;

                    for (Type param : t.allparams()) {
                        if (!param.isUnbound())
                            return false;
                    }
                    return true;
                }
            }

            @Override
            // 当t为数组类型时，visitArrayType()方法会继续判断数组元素的类型
            public Boolean visitArrayType(ArrayType t, Void ignored) {
                return visit(t.elemtype);
            }

            @Override
            // 当t为类型变量时，visitTypeVar()方法直接返回false，表示不是运行时类型
            public Boolean visitTypeVar(TypeVar t, Void ignored) {
                return false;
            }
        };
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Array Utils">
    public boolean isArray(Type t) {
        while (t.tag == WILDCARD)
            t = upperBound(t);
        return t.tag == ARRAY;
    }

    /**
     * The element type of an array.
     */
    public Type elemtype(Type t) {
        switch (t.tag) {
        case WILDCARD:
            return elemtype(upperBound(t));
        case ARRAY:
            return ((ArrayType)t).elemtype;
        case FORALL:
            return elemtype(((ForAll)t).qtype);
        case ERROR:
            return t;
        default:
            return null;
        }
    }

    public Type elemtypeOrType(Type t) {
        Type elemtype = elemtype(t);
        return elemtype != null ?
            elemtype :
            t;
    }

    /**
     * Mapping to take element type of an arraytype
     */
    private Mapping elemTypeFun = new Mapping ("elemTypeFun") {
        public Type apply(Type t) { return elemtype(t); }
    };

    /**
     * The number of dimensions of an array type.
     */
    public int dimensions(Type t) {
        int result = 0;
        while (t.tag == ARRAY) {
            result++;
            t = elemtype(t);
        }
        return result;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="asSuper">
    /**
     * Return the (most specific) base type of t that starts with the
     * given symbol.  If none exists, return null.
     *
     * @param t a type
     * @param sym a symbol
     */
    // 查找某个类型或某个类型的父类和实现接口
    // 传入符号或类型，查找父类型
    public Type asSuper(Type t, Symbol sym) {
        return asSuper.visit(t, sym);
    }
    // where
    // 查找某个类型或某个类型的父类和实现接口
    // 查找出sym引用的类型
        private SimpleVisitor<Type,Symbol> asSuper = new SimpleVisitor<Type,Symbol>() {

            public Type visitType(Type t, Symbol sym) {
                return null;
            }

            @Override
            public Type visitClassType(ClassType t, Symbol sym) {
                // t就是要查找的类型
                if (t.tsym == sym)
                    return t;
                // 查找父类
                Type st = supertype(t);
                // 父类为类或者类型变量 递归asSuper
                if (st.tag == CLASS || st.tag == TYPEVAR || st.tag == ERROR) {
                    // 递归查找
                    Type x = asSuper(st, sym);
                    // 不为空时返回
                    if (x != null)
                        return x;
                }
                // 查找接口
                if ((sym.flags() & INTERFACE) != 0) {
                    // 查找实现的接口
                    for (List<Type> l = interfaces(t); l.nonEmpty(); l = l.tail) {
                        // 递归查找
                        Type x = asSuper(l.head, sym);
                        // 找到返回
                        if (x != null)
                            return x;
                    }
                }
                return null;
            }

            @Override
            public Type visitArrayType(ArrayType t, Symbol sym) {
                // 任何ArrayType对象的tsym都为Symtab类中预定义的ClassSymbol(name=Array)，
                // 所以只能通过判断t是否为sym.type的子类型来确定
                return isSubtype(t, sym.type) ? sym.type : null;
            }

            @Override
            public Type visitTypeVar(TypeVar t, Symbol sym) {
                // 如果t.tsym等于sym，则t就是要查找的类型
                if (t.tsym == sym)
                    return t;
                else
                    // 传入上界，递归查找
                    return asSuper(t.bound, sym);
            }

            @Override
            public Type visitErrorType(ErrorType t, Symbol sym) {
                return t;
            }
        };

    /**
     * Return the base type of t or any of its outer types that starts
     * with the given symbol.  If none exists, return null.
     *
     * @param t a type
     * @param sym a symbol
     */
    // asOuterSuper()方法与asSuper()方法相比，
    // 不但会查找类型及类型的父类和实现接口，同时还会查找它的封闭类
    public Type asOuterSuper(Type t, Symbol sym) {
        switch (t.tag) {
            // 当t为类或接口时
        case CLASS:
            do {
                // 调用asSuper()方法查找t或t的父类或实现接口，如果找到就直接返回
                Type s = asSuper(t, sym);
                if (s != null)
                    return s;
                // 调用t.getEnclosingType()方法获取封闭类型后继续查找
                t = t.getEnclosingType();
            } while (t.tag == CLASS);
            return null;
            // 当t为数组或类型变量时，不存在查找封闭类型的情况
        case ARRAY:
            return isSubtype(t, sym.type) ? sym.type : null;
        case TYPEVAR:
            return asSuper(t, sym);
        case ERROR:
            return t;
        default:
            return null;
        }
    }

    /**
     * Return the base type of t or any of its enclosing types that
     * starts with the given symbol.  If none exists, return null.
     *
     * @param t a type
     * @param sym a symbol
     */
    public Type asEnclosingSuper(Type t, Symbol sym) {
        switch (t.tag) {
        case CLASS:
            do {
                Type s = asSuper(t, sym);
                if (s != null) return s;
                Type outer = t.getEnclosingType();
                t = (outer.tag == CLASS) ? outer :
                    (t.tsym.owner.enclClass() != null) ? t.tsym.owner.enclClass().type :
                    Type.noType;
            } while (t.tag == CLASS);
            return null;
        case ARRAY:
            return isSubtype(t, sym.type) ? sym.type : null;
        case TYPEVAR:
            return asSuper(t, sym);
        case ERROR:
            return t;
        default:
            return null;
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="memberType">
    /**
     * The type of given symbol, seen as a member of t.
     *
     * @param t a type
     * @param sym a symbol
     */
    // 获取某个给定类型下具体的成员类型，主要还是针对泛型进行操作
    // 传入符号，返回类型
    // 例c-13
    public Type memberType(Type t, Symbol sym) {
        // 当sym有static修饰时，返回sym.type，
        return (sym.flags() & STATIC) != 0
                // 因为有static修饰的成员的具体类型与t所代表的实例类型无关
            ? sym.type
            : memberType.visit(t, sym);
        }
    // where
        private SimpleVisitor<Type,Symbol> memberType = new SimpleVisitor<Type,Symbol>() {

            //当t不为WildcardType、TypeVar与ClassType对象时调用visitType()方法，这个方法直接返回sym.type
            @Override
            public Type visitType(Type t, Symbol sym) {
                return sym.type;
            }

            // 当t为WildcardType对象时调用visitWildcardType()方法
            @Override
            public Type visitWildcardType(WildcardType t, Symbol sym) {
                // 调用memberType()方法求sym在t的上界类型下的具体类型
                return memberType(upperBound(t), sym);
            }

            @Override
            public Type visitClassType(ClassType t, Symbol sym) {
                Symbol owner = sym.owner;
                long flags = sym.flags();
                // 判断sym不能有static修饰，因为有static修饰后就与t所代表的参数化类型或裸类型无关了
                // 同时也要保证定义sym的类型被定义为了泛型类型
                if (((flags & STATIC) == 0) && owner.type.isParameterized()) {
                    // 这样sym.type才有可能使用了类型中声明的类型变量，需要将这些类型变量替换为实际的类型
                    // 调用asOuterSuper()方法查找base，这个类型的tsym为owner
                    // 由于继承的原因，t可能并不是定义sym的类型，所以要从t开始查找到定义sym的类型，这个类型可能是参数化类型或裸类型，最后找到base
                    // 例c-14/c-15
                    Type base = asOuterSuper(t, owner);
                    base = t.isCompound() ? capture(base) : base;
                    if (base != null) {
                        List<Type> ownerParams = owner.type.allparams();
                        List<Type> baseParams = base.allparams();
                        if (ownerParams.nonEmpty()) {
                            if (baseParams.isEmpty()) {
                                // then base is a raw type
                                return erasure(sym.type);
                            } else {
                                return subst(sym.type, ownerParams, baseParams);
                            }
                        }
                    }
                }
                return sym.type;
            }

            // 当t为TypeVar对象时调用visitTypeVar()方法，
            @Override
            public Type visitTypeVar(TypeVar t, Symbol sym) {
                // 这个方法会继续调用memberType()方法求sym在t的上界类型下的具体类型
                return memberType(t.bound, sym);
            }

            @Override
            public Type visitErrorType(ErrorType t, Symbol sym) {
                return t;
            }
        };
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="isAssignable">
    public boolean isAssignable(Type t, Type s) {
        return isAssignable(t, s, Warner.noWarnings);
    }

    /**
     * Is t assignable to s?<br>
     * Equivalent to subtype except for constant values and raw
     * types.<br>
     * (not defined for Method and ForAll types)
     */
    // 当转换后的类型为裸类型时，还可能发生非检查转换
    // isAssignable()方法判断t是否可以转换为s。
    // 如果t有对应的常量值，则根据目标类型s来判断常量值是否在s所表示的范围内
    public boolean isAssignable(Type t, Type s, Warner warn) {
        if (t.tag == ERROR)
            return true;
        // 对整数类型的编译常量进行处理
        if (t.tag <= INT && t.constValue() != null) {
            int value = ((Number)t.constValue()).intValue();
            switch (s.tag) {
            case BYTE:
                if (Byte.MIN_VALUE <= value && value <= Byte.MAX_VALUE)
                    return true;
                break;
            case CHAR:
                if (Character.MIN_VALUE <= value && value <= Character.MAX_VALUE)
                    return true;
                break;
            case SHORT:
                if (Short.MIN_VALUE <= value && value <= Short.MAX_VALUE)
                    return true;
                break;
            case INT:
                return true;
            case CLASS:
                switch (unboxedType(s).tag) {
                case BYTE:
                case CHAR:
                case SHORT:
                    return isAssignable(t, unboxedType(s), warn);
                }
                break;
            }
        }
        // 当t类型没有对应的常量值时
        return isConvertible(t, s, warn);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="erasure">
    /**
     * The erasure of t {@code |t|} -- the type that results when all
     * type parameters in t are deleted.
     */
    // 泛型擦除
    public Type erasure(Type t) {
        return erasure(t, false);
    }
    //where
    private Type erasure(Type t, boolean recurse) {
        if (t.tag <= lastBaseTag)
            return t; /* fast special case */
        else
            return erasure.visit(t, recurse);
        }
    // where
        private SimpleVisitor<Type, Boolean> erasure = new SimpleVisitor<Type, Boolean>() {
            public Type visitType(Type t, Boolean recurse) {
                if (t.tag <= lastBaseTag)
                    return t; /*fast special case*/
                else
                    return t.map(recurse ? erasureRecFun : erasureFun);
            }

            @Override
            public Type visitWildcardType(WildcardType t, Boolean recurse) {
                return erasure(upperBound(t), recurse);
            }

            @Override
            public Type visitClassType(ClassType t, Boolean recurse) {
                Type erased = t.tsym.erasure(Types.this);
                if (recurse) {
                    erased = new ErasedClassType(erased.getEnclosingType(),erased.tsym);
                }
                return erased;
            }

            @Override
            public Type visitTypeVar(TypeVar t, Boolean recurse) {
                return erasure(t.bound, recurse);
            }

            @Override
            public Type visitErrorType(ErrorType t, Boolean recurse) {
                return t;
            }
        };

    private Mapping erasureFun = new Mapping ("erasure") {
            public Type apply(Type t) { return erasure(t); }
        };

    private Mapping erasureRecFun = new Mapping ("erasureRecursive") {
        public Type apply(Type t) { return erasureRecursive(t); }
    };

    public List<Type> erasure(List<Type> ts) {
        return Type.map(ts, erasureFun);
    }

    public Type erasureRecursive(Type t) {
        return erasure(t, true);
    }

    public List<Type> erasureRecursive(List<Type> ts) {
        return Type.map(ts, erasureRecFun);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="makeCompoundType">
    /**
     * Make a compound type from non-empty list of types
     *
     * @param bounds            the types from which the compound type is formed
     * @param supertype         is objectType if all bounds are interfaces,
     *                          null otherwise.
     */
    // 创建组合类型
    // 创建一个ClassSymbol对象，获取ClassType对象后初始化supertype_field与interfaces_field变量的值，
    // 其实就相当于创建了一个空实现的类，然后指定这个类的父类和实现接口
    public Type makeCompoundType(List<Type> bounds,
                                 Type supertype) {
        ClassSymbol bc =
            new ClassSymbol(ABSTRACT|PUBLIC|SYNTHETIC|COMPOUND|ACYCLIC,
                            Type.moreInfo
                                ? names.fromString(bounds.toString())
                                : names.empty,
                            syms.noSymbol);
        if (bounds.head.tag == TYPEVAR)
            // error condition, recover
                bc.erasure_field = syms.objectType;
            else
                bc.erasure_field = erasure(bounds.head);
            bc.members_field = new Scope(bc);
        ClassType bt = (ClassType)bc.type;
        bt.allparams_field = List.nil();
        if (supertype != null) {
            bt.supertype_field = supertype;
            bt.interfaces_field = bounds;
        } else {
            bt.supertype_field = bounds.head;
            bt.interfaces_field = bounds.tail;
        }
        Assert.check(bt.supertype_field.tsym.completer != null
                || !bt.supertype_field.isInterface(),
            bt.supertype_field);
        return bt;
    }

    /**
     * Same as {@link #makeCompoundType(List,Type)}, except that the
     * second parameter is computed directly. Note that this might
     * cause a symbol completion.  Hence, this version of
     * makeCompoundType may not be called during a classfile read.
     */
    public Type makeCompoundType(List<Type> bounds) {
        Type supertype = (bounds.head.tsym.flags() & INTERFACE) != 0 ?
            supertype(bounds.head) : null;
        return makeCompoundType(bounds, supertype);
    }

    /**
     * A convenience wrapper for {@link #makeCompoundType(List)}; the
     * arguments are converted to a list and passed to the other
     * method.  Note that this might cause a symbol completion.
     * Hence, this version of makeCompoundType may not be called
     * during a classfile read.
     */
    public Type makeCompoundType(Type bound1, Type bound2) {
        return makeCompoundType(List.of(bound1, bound2));
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="supertype">
    // 求某个类型的父类
    public Type supertype(Type t) {
        return supertype.visit(t);
    }
    // where
        private UnaryVisitor<Type> supertype = new UnaryVisitor<Type>() {

            public Type visitType(Type t, Void ignored) {
                // 表示除类和接口、类型变量及数组外，其他类型的父类为null
                return null;
            }

            @Override
            // 在visitClassType()方法中，
            // 当t.supertype_field值为空时会计算父类，然后赋值给t.supertype_field变量保存，
            // 这样下次如果再次求这个类型的父类时就不用重复进行计算了
            public Type visitClassType(ClassType t, Void ignored) {
                if (t.supertype_field == null) {
                    // 获取父类
                    Type supertype = ((ClassSymbol)t.tsym).getSuperclass();
                    // 接口没有父类，但是为了处理的方便，Javac默认接口的父类为Object
                    if (t.isInterface())
                        // 默认接口的父类为Object
                        // 接口在定义时就会赋值supertype_field变量为Object类
                        supertype = ((ClassType)t.tsym.type).supertype_field;
                    if (t.supertype_field == null) {
                        List<Type> actuals = classBound(t).allparams();
                        List<Type> formals = t.tsym.type.allparams();
                        if (t.hasErasedSupertypes()) {
                            // t是裸类型
                            // 调用erasureRecursive()方法擦除supertype中的泛型信息
                            t.supertype_field = erasureRecursive(supertype);
                        } else if (formals.nonEmpty()) {
                            // t的定义类型有类型参数声明
                            // 当t的定义类型有形式类型参数的声明时，formals列表将不为空
                            // 调用types.subst()方法将supertype中含有formals列表中含有的所有类型替换为actuals列表中对应位置上的类型
                            t.supertype_field = subst(supertype, formals, actuals);
                        }
                        else {
                            // 没有泛型的情况，直接返回父类型
                            t.supertype_field = supertype;
                        }
                    }
                }
                return t.supertype_field;
            }

            /**
             * The supertype is always a class type. If the type
             * variable's bounds start with a class type, this is also
             * the supertype.  Otherwise, the supertype is
             * java.lang.Object.
             */
            @Override
            // 例：/Users/yangluchao/Documents/GitHub/javac_study/src/book/c/Test5.java
            public Type visitTypeVar(TypeVar t, Void ignored) {
                // 当类型变量的上界为类型变量或者类型变量的上界既不为组合类型也不为接口时，
                // 直接取t.bound返回，否则调用supertype()方法继续求t.bound的父类。
                if (t.bound.tag == TYPEVAR ||
                    (!t.bound.isCompound() && !t.bound.isInterface())) {
                    return t.bound;
                } else {
                    return supertype(t.bound);
                }
            }

            @Override
            public Type visitArrayType(ArrayType t, Void ignored) {
                // 当数组类型t的组成元素为基本类型或Object类时
                if (t.elemtype.isPrimitive() || isSameType(t.elemtype, syms.objectType))
                    // 父类为调用arraySuperType()方法返回的类型，
                    return arraySuperType();
                else
                    // 否则调用supertype()方法求t.elemtype的父类，然后创建一个新的数组类型
                    return new ArrayType(supertype(t.elemtype), t.tsym);
            }

            @Override
            public Type visitErrorType(ErrorType t, Void ignored) {
                return t;
            }
        };
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="interfaces">
    /**
     * Return the interfaces implemented by this class.
     */
    public List<Type> interfaces(Type t) {
        return interfaces.visit(t);
    }
    // where
        private UnaryVisitor<List<Type>> interfaces = new UnaryVisitor<List<Type>>() {

            public List<Type> visitType(Type t, Void ignored) {
                return List.nil();
            }

            @Override
            public List<Type> visitClassType(ClassType t, Void ignored) {
                // 第1次判断t.interfaces_field为空
                if (t.interfaces_field == null) {
                    List<Type> interfaces = ((ClassSymbol)t.tsym).getInterfaces();
                    // 第2次判断t.interfaces_field为空
                    if (t.interfaces_field == null) {
                        Assert.check(t != t.tsym.type, t);
                        List<Type> actuals = t.allparams();
                        List<Type> formals = t.tsym.type.allparams();
                        if (t.hasErasedSupertypes()) {
                            t.interfaces_field = erasureRecursive(interfaces);
                        } else if (formals.nonEmpty()) {
                            t.interfaces_field =
                                upperBounds(subst(interfaces, formals, actuals));
                        }
                        else {
                            t.interfaces_field = interfaces;
                        }
                    }
                }
                return t.interfaces_field;
            }

            @Override
            public List<Type> visitTypeVar(TypeVar t, Void ignored) {
                // 当t的上界为组合类型时，调用interfaces()方法继续求组合类型的实现接口
                if (t.bound.isCompound())
                    return interfaces(t.bound);
                // 当t的上界为接口时，返回仅含有这个接口的列表，否则没有实现接口，返回空列表
                if (t.bound.isInterface())
                    return List.of(t.bound);

                return List.nil();
            }
        };
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="isDerivedRaw">
    Map<Type,Boolean> isDerivedRawCache = new HashMap<Type,Boolean>();

    public boolean isDerivedRaw(Type t) {
        Boolean result = isDerivedRawCache.get(t);
        if (result == null) {
            result = isDerivedRawInternal(t);
            isDerivedRawCache.put(t, result);
        }
        return result;
    }

    public boolean isDerivedRawInternal(Type t) {
        if (t.isErroneous())
            return false;
        return
            t.isRaw() ||
            supertype(t) != null && isDerivedRaw(supertype(t)) ||
            isDerivedRaw(interfaces(t));
    }

    public boolean isDerivedRaw(List<Type> ts) {
        List<Type> l = ts;
        while (l.nonEmpty() && !isDerivedRaw(l.head)) l = l.tail;
        return l.nonEmpty();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="setBounds">
    /**
     * Set the bounds field of the given type variable to reflect a
     * (possibly multiple) list of bounds.
     * @param t                 a type variable
     * @param bounds            the bounds, must be nonempty
     * @param supertype         is objectType if all bounds are interfaces,
     *                          null otherwise.
     */
    public void setBounds(TypeVar t, List<Type> bounds, Type supertype) {
        if (bounds.tail.isEmpty())
            t.bound = bounds.head;
        else
            t.bound = makeCompoundType(bounds, supertype);
        t.rank_field = -1;
    }

    /**
     * Same as {@link #setBounds(Type.TypeVar,List,Type)}, except that
     * third parameter is computed directly, as follows: if all
     * all bounds are interface types, the computed supertype is Object,
     * otherwise the supertype is simply left null (in this case, the supertype
     * is assumed to be the head of the bound list passed as second argument).
     * Note that this check might cause a symbol completion. Hence, this version of
     * setBounds may not be called during a classfile read.
     */
    public void setBounds(TypeVar t, List<Type> bounds) {
        Type supertype = (bounds.head.tsym.flags() & INTERFACE) != 0 ?
            syms.objectType : null;
        setBounds(t, bounds, supertype);
        t.rank_field = -1;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="getBounds">
    /**
     * Return list of bounds of the given type variable.
     */
    public List<Type> getBounds(TypeVar t) {
        if (t.bound.isErroneous() || !t.bound.isCompound())
            return List.of(t.bound);
        else if ((erasure(t).tsym.flags() & INTERFACE) == 0)
            return interfaces(t).prepend(supertype(t));
        else
            // No superclass was given in bounds.
            // In this case, supertype is Object, erasure is first interface.
            return interfaces(t);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="classBound">
    /**
     * If the given type is a (possibly selected) type variable,
     * return the bounding class of this type, otherwise return the
     * type itself.
     */
    public Type classBound(Type t) {
        return classBound.visit(t);
    }
    // where
        private UnaryVisitor<Type> classBound = new UnaryVisitor<Type>() {

            public Type visitType(Type t, Void ignored) {
                return t;
            }

            @Override
            public Type visitClassType(ClassType t, Void ignored) {
                Type outer1 = classBound(t.getEnclosingType());
                if (outer1 != t.getEnclosingType())
                    return new ClassType(outer1, t.getTypeArguments(), t.tsym);
                else
                    return t;
            }

            @Override
            public Type visitTypeVar(TypeVar t, Void ignored) {
                return classBound(supertype(t));
            }

            @Override
            public Type visitErrorType(ErrorType t, Void ignored) {
                return t;
            }
        };
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="sub signature / override equivalence">
    /**
     * Returns true iff the first signature is a <em>sub
     * signature</em> of the other.  This is <b>not</b> an equivalence
     * relation.
     *
     * @jls section 8.4.2.
     * @see #overrideEquivalent(Type t, Type s)
     * @param t first signature (possibly raw).
     * @param s second signature (could be subjected to erasure).
     * @return true if t is a sub signature of s.
     */
    public boolean isSubSignature(Type t, Type s) {
        return isSubSignature(t, s, true);
    }

    public boolean isSubSignature(Type t, Type s, boolean strict) {
        return hasSameArgs(t, s, strict) || hasSameArgs(t, erasure(s), strict);
    }

    /**
     * Returns true iff these signatures are related by <em>override
     * equivalence</em>.  This is the natural extension of
     * isSubSignature to an equivalence relation.
     *
     * @jls section 8.4.2.
     * @see #isSubSignature(Type t, Type s)
     * @param t a signature (possible raw, could be subjected to
     * erasure).
     * @param s a signature (possible raw, could be subjected to
     * erasure).
     * @return true if either argument is a sub signature of the other.
     */
    // 返回true，表示一个方法对另外一个方法进行了覆写
    // 当t与s所代表的方法的形式参数相同，
    // 或者对t或者s调用erasure()方法进行泛型擦除后方法的形式参数相同时，
    // overrideEquivalent()方法将返回true，表示一个方法对另外一个方法进行了覆写。
    public boolean overrideEquivalent(Type t, Type s) {
        return hasSameArgs(t, s) ||
            hasSameArgs(t, erasure(s)) || hasSameArgs(erasure(t), s);
    }

    // <editor-fold defaultstate="collapsed" desc="Determining method implementation in given site">
    class ImplementationCache {

        // key为MethodSymbol类型，保存被覆写的方法
        // value为SoftReference<Map<TypeSymbol,Entry>>类型，保存覆写对应key方法的方法
        // 具体就是从TypeSymbol开始查找时找到的覆写方法，这个覆写方法是Entry对象，Entry对象可以简单看作是对MethodSymbol对象的封装
        private WeakHashMap<MethodSymbol, SoftReference<Map<TypeSymbol, Entry>>> _map =
                new WeakHashMap<MethodSymbol, SoftReference<Map<TypeSymbol, Entry>>>();

        class Entry {
            final MethodSymbol cachedImpl;
            final Filter<Symbol> implFilter;
            final boolean checkResult;
            final int prevMark;

            public Entry(MethodSymbol cachedImpl,
                    Filter<Symbol> scopeFilter,
                    boolean checkResult,
                    int prevMark) {
                this.cachedImpl = cachedImpl;
                this.implFilter = scopeFilter;
                this.checkResult = checkResult;
                this.prevMark = prevMark;
            }

            boolean matches(Filter<Symbol> scopeFilter, boolean checkResult, int mark) {
                return this.implFilter == scopeFilter &&
                        this.checkResult == checkResult &&
                        this.prevMark == mark;
            }
        }

        MethodSymbol get(MethodSymbol ms, TypeSymbol origin, boolean checkResult, Filter<Symbol> implFilter) {
            SoftReference<Map<TypeSymbol, Entry>> ref_cache = _map.get(ms);
            Map<TypeSymbol, Entry> cache = ref_cache != null ? ref_cache.get() : null;
            if (cache == null) {
                cache = new HashMap<TypeSymbol, Entry>();
                _map.put(ms, new SoftReference<Map<TypeSymbol, Entry>>(cache));
            }
            Entry e = cache.get(origin);
            CompoundScope members = membersClosure(origin.type, true);
            if (e == null ||
                    !e.matches(implFilter, checkResult, members.getMark())) {
                MethodSymbol impl = implementationInternal(ms, origin, checkResult, implFilter);
                cache.put(origin, new Entry(impl, implFilter, checkResult, members.getMark()));
                return impl;
            }
            else {
                return e.cachedImpl;
            }
        }

        private MethodSymbol implementationInternal(MethodSymbol ms, TypeSymbol origin, boolean checkResult, Filter<Symbol> implFilter) {
            for (Type t = origin.type; t.tag == CLASS || t.tag == TYPEVAR; t = supertype(t)) {
                while (t.tag == TYPEVAR)
                    t = t.getUpperBound();
                TypeSymbol c = t.tsym;
                for (Scope.Entry e = c.members().lookup(ms.name, implFilter);
                     e.scope != null;
                     e = e.next(implFilter)) {
                    if (e.sym != null &&
                             e.sym.overrides(ms, origin, Types.this, checkResult))
                        return (MethodSymbol)e.sym;
                }
            }
            return null;
        }
    }

    private ImplementationCache implCache = new ImplementationCache();

    public MethodSymbol implementation(MethodSymbol ms, TypeSymbol origin, boolean checkResult, Filter<Symbol> implFilter) {
        return implCache.get(ms, origin, checkResult, implFilter);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="compute transitive closure of all members in given site">
    class MembersClosureCache extends SimpleVisitor<CompoundScope, Boolean> {

        private WeakHashMap<TypeSymbol, Entry> _map =
                new WeakHashMap<TypeSymbol, Entry>();

        class Entry {
            final boolean skipInterfaces;
            final CompoundScope compoundScope;

            public Entry(boolean skipInterfaces, CompoundScope compoundScope) {
                this.skipInterfaces = skipInterfaces;
                this.compoundScope = compoundScope;
            }

            boolean matches(boolean skipInterfaces) {
                return this.skipInterfaces == skipInterfaces;
            }
        }

        /** members closure visitor methods **/

        public CompoundScope visitType(Type t, Boolean skipInterface) {
            return null;
        }

        @Override
        public CompoundScope visitClassType(ClassType t, Boolean skipInterface) {
            ClassSymbol csym = (ClassSymbol)t.tsym;
            Entry e = _map.get(csym);
            // 当没有查找到缓存的结果或缓存的结果不符合要求时，重新获取CompoundScope对象
            if (e == null || !e.matches(skipInterface)) {
                CompoundScope membersClosure = new CompoundScope(csym);
                if (!skipInterface) {
                    for (Type i : interfaces(t)) {
                        membersClosure.addSubScope(visit(i, skipInterface));
                    }
                }
                membersClosure.addSubScope(visit(supertype(t), skipInterface));
                membersClosure.addSubScope(csym.members());
                e = new Entry(skipInterface, membersClosure);
                _map.put(csym, e);
            }
            return e.compoundScope;
        }

        @Override
        public CompoundScope visitTypeVar(TypeVar t, Boolean skipInterface) {
            // 处理类型变量的上界
            return visit(t.getUpperBound(), skipInterface);
        }
    }

    private MembersClosureCache membersCache = new MembersClosureCache();

    // skipInterface 跳过接口中的方法
    public CompoundScope membersClosure(Type site, boolean skipInterface) {
        return membersCache.visit(site, skipInterface);
    }
    // </editor-fold>

    /**
     * Does t have the same arguments as s?  It is assumed that both
     * types are (possibly polymorphic) method types.  Monomorphic
     * method types "have the same arguments", if their argument lists
     * are equal.  Polymorphic method types "have the same arguments",
     * if they have the same arguments after renaming all type
     * variables of one to corresponding type variables in the other,
     * where correspondence is by position in the type parameter list.
     * t 和 s 有相同的论据吗？假定这两种类型都是（可能是多态的）方法类型。
     * 如果参数列表相等，单态方法类型“具有相同的参数”。多态方法类型“具有相同的参数”，
     * 如果在将一个的所有类型变量重命名为另一个中的相应类型变量后它们具有相同的参数，
     * 其中对应关系是在类型参数列表中的位置。
     */
    // hasSameArgs()方法可以对两个方法的形式参数类型进行比较
    public boolean hasSameArgs(Type t, Type s) {
        return hasSameArgs(t, s, true);
    }

    // hasSameArgs()方法的第3个参数可能是hasSameArgs_strict或者hasSameArgs_nonstrict
    public boolean hasSameArgs(Type t, Type s, boolean strict) {
        return hasSameArgs(t, s, strict ? hasSameArgs_strict : hasSameArgs_nonstrict);
    }

    private boolean hasSameArgs(Type t, Type s, TypeRelation hasSameArgs) {
        return hasSameArgs.visit(t, s);
    }
    // where
        private class HasSameArgs extends TypeRelation {

            // 对于hasSameArgs_strict变量来说，strict的值为true，
            // 而对于hasSameArgs_nonstrict变量来说，strict的值为false
            boolean strict;

            public HasSameArgs(boolean strict) {
                this.strict = strict;
            }

            public Boolean visitType(Type t, Type s) {
                throw new AssertionError();
            }

            @Override
            public Boolean visitMethodType(MethodType t, Type s) {
                return s.tag == METHOD
                    && containsTypeEquivalent(t.argtypes, s.getParameterTypes());
            }

            @Override
            // 逻辑和com.sun.tools.javac.code.Types.TypeRelation#visitForAll基本相同
            public Boolean visitForAll(ForAll t, Type s) {
                if (s.tag != FORALL)
                    return strict ? false : visitMethodType(t.asMethodType(), s);

                ForAll forAll = (ForAll)s;
                return hasSameBounds(t, forAll)
                    && visit(t.qtype, subst(forAll.qtype, forAll.tvars, t.tvars));
            }

            @Override
            public Boolean visitErrorType(ErrorType t, Type s) {
                return false;
            }
        };

        TypeRelation hasSameArgs_strict = new HasSameArgs(true);
        TypeRelation hasSameArgs_nonstrict = new HasSameArgs(false);

    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="subst">
    public List<Type> subst(List<Type> ts,
                            List<Type> from,
                            List<Type> to) {
        return new Subst(from, to).subst(ts);
    }

    /**
     * Substitute all occurrences of a type in `from' with the
     * corresponding type in `to' in 't'. Match lists `from' and `to'
     * from the right: If lists have different length, discard leading
     * elements of the longer list.
     * 用 't' 中的 'to' 中的相应类型替换所有出现在 'from' 中的类型。
     * 从右侧匹配列表 `from' 和 `to'：如果列表的长度不同，则丢弃较长列表的前导元素。
     */
    // 将t中含有form列表中含有的所有类型替换为to列表中对应位置上的类型
    public Type subst(Type t, List<Type> from, List<Type> to) {
        return new Subst(from, to).subst(t);
    }

    private class Subst extends UnaryVisitor<Type> {
        List<Type> from;
        List<Type> to;

        public Subst(List<Type> from, List<Type> to) {
            int fromLength = from.length();
            int toLength = to.length();
            while (fromLength > toLength) {
                fromLength--;
                from = from.tail;
            }
            while (fromLength < toLength) {
                toLength--;
                to = to.tail;
            }
            this.from = from;
            this.to = to;
        }

        Type subst(Type t) {
            if (from.tail == null)
                return t;
            else
                return visit(t);
            }

        List<Type> subst(List<Type> ts) {
            if (from.tail == null)
                return ts;
            boolean wild = false;
            if (ts.nonEmpty() && from.nonEmpty()) {
                Type head1 = subst(ts.head);
                List<Type> tail1 = subst(ts.tail);
                if (head1 != ts.head || tail1 != ts.tail)
                    return tail1.prepend(head1);
            }
            return ts;
        }

        public Type visitType(Type t, Void ignored) {
            return t;
        }

        @Override
        public Type visitMethodType(MethodType t, Void ignored) {
            List<Type> argtypes = subst(t.argtypes);
            Type restype = subst(t.restype);
            List<Type> thrown = subst(t.thrown);
            if (argtypes == t.argtypes &&
                restype == t.restype &&
                thrown == t.thrown)
                return t;
            else
                return new MethodType(argtypes, restype, thrown, t.tsym);
        }

        @Override
        public Type visitTypeVar(TypeVar t, Void ignored) {
            for (List<Type> from = this.from, to = this.to;
                 from.nonEmpty();
                 from = from.tail, to = to.tail) {
                if (t == from.head) {
                    return to.head.withTypeVar(t);
                }
            }
            return t;
        }

        @Override
        public Type visitClassType(ClassType t, Void ignored) {
            if (!t.isCompound()) {
                List<Type> typarams = t.getTypeArguments();
                List<Type> typarams1 = subst(typarams);
                Type outer = t.getEnclosingType();
                Type outer1 = subst(outer);
                if (typarams1 == typarams && outer1 == outer)
                    return t;
                else
                    return new ClassType(outer1, typarams1, t.tsym);
            } else {
                Type st = subst(supertype(t));
                List<Type> is = upperBounds(subst(interfaces(t)));
                if (st == supertype(t) && is == interfaces(t))
                    return t;
                else
                    return makeCompoundType(is.prepend(st));
            }
        }

        @Override
        public Type visitWildcardType(WildcardType t, Void ignored) {
            Type bound = t.type;
            if (t.kind != BoundKind.UNBOUND)
                bound = subst(bound);
            if (bound == t.type) {
                return t;
            } else {
                if (t.isExtendsBound() && bound.isExtendsBound())
                    bound = upperBound(bound);
                return new WildcardType(bound, t.kind, syms.boundClass, t.bound);
            }
        }

        @Override
        public Type visitArrayType(ArrayType t, Void ignored) {
            Type elemtype = subst(t.elemtype);
            if (elemtype == t.elemtype)
                return t;
            else
                return new ArrayType(upperBound(elemtype), t.tsym);
        }

        @Override
        public Type visitForAll(ForAll t, Void ignored) {
            if (Type.containsAny(to, t.tvars)) {
                //perform alpha-renaming of free-variables in 't'
                //if 'to' types contain variables that are free in 't'
                List<Type> freevars = newInstances(t.tvars);
                t = new ForAll(freevars,
                        Types.this.subst(t.qtype, t.tvars, freevars));
            }
            List<Type> tvars1 = substBounds(t.tvars, from, to);
            Type qtype1 = subst(t.qtype);
            if (tvars1 == t.tvars && qtype1 == t.qtype) {
                return t;
            } else if (tvars1 == t.tvars) {
                return new ForAll(tvars1, qtype1);
            } else {
                return new ForAll(tvars1, Types.this.subst(qtype1, t.tvars, tvars1));
            }
        }

        @Override
        public Type visitErrorType(ErrorType t, Void ignored) {
            return t;
        }
    }

    public List<Type> substBounds(List<Type> tvars,
                                  List<Type> from,
                                  List<Type> to) {
        if (tvars.isEmpty())
            return tvars;
        ListBuffer<Type> newBoundsBuf = lb();
        boolean changed = false;
        // calculate new bounds
        for (Type t : tvars) {
            TypeVar tv = (TypeVar) t;
            Type bound = subst(tv.bound, from, to);
            if (bound != tv.bound)
                changed = true;
            newBoundsBuf.append(bound);
        }
        if (!changed)
            return tvars;
        ListBuffer<Type> newTvars = lb();
        // create new type variables without bounds
        for (Type t : tvars) {
            newTvars.append(new TypeVar(t.tsym, null, syms.botType));
        }
        // the new bounds should use the new type variables in place
        // of the old
        List<Type> newBounds = newBoundsBuf.toList();
        from = tvars;
        to = newTvars.toList();
        for (; !newBounds.isEmpty(); newBounds = newBounds.tail) {
            newBounds.head = subst(newBounds.head, from, to);
        }
        newBounds = newBoundsBuf.toList();
        // set the bounds of new type variables to the new bounds
        for (Type t : newTvars.toList()) {
            TypeVar tv = (TypeVar) t;
            tv.bound = newBounds.head;
            newBounds = newBounds.tail;
        }
        return newTvars.toList();
    }

    // TypeVar为泛型对象，将t替换为上界类型
    // 例13-3
    public TypeVar substBound(TypeVar t, List<Type> from, List<Type> to) {
        Type bound1 = subst(t.bound, from, to);
        if (bound1 == t.bound)
            // 上界相同，返回t
            return t;
        else {
            // 不同，创建新的泛型
            TypeVar tv = new TypeVar(t.tsym, null, syms.botType);
            // 设置泛型的上界
            tv.bound = subst(bound1, List.<Type>of(t), List.<Type>of(tv));
            // 返回
            return tv;
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="hasSameBounds">
    /**
     * Does t have the same bounds for quantified variables as s?
     */
    // 判断两个ForAll类型的上界是否相同
    boolean hasSameBounds(ForAll t, ForAll s) {
        List<Type> l1 = t.tvars;
        List<Type> l2 = s.tvars;
        while (l1.nonEmpty() && l2.nonEmpty() &&
                // isSameType()方法比较类型变量的上界
               isSameType(l1.head.getUpperBound(),
                          subst(l2.head.getUpperBound(),
                                s.tvars,
                                t.tvars))) {
            l1 = l1.tail;
            l2 = l2.tail;
        }
        // 判断声明的类型变量的数量是否相等
        return l1.isEmpty() && l2.isEmpty();
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="newInstances">
    /** Create new vector of type variables from list of variables
     *  changing all recursive bounds from old to new list.
     */
    public List<Type> newInstances(List<Type> tvars) {
        List<Type> tvars1 = Type.map(tvars, newInstanceFun);
        for (List<Type> l = tvars1; l.nonEmpty(); l = l.tail) {
            TypeVar tv = (TypeVar) l.head;
            tv.bound = subst(tv.bound, tvars, tvars1);
        }
        return tvars1;
    }
    static private Mapping newInstanceFun = new Mapping("newInstanceFun") {
            public Type apply(Type t) { return new TypeVar(t.tsym, t.getUpperBound(), t.getLowerBound()); }
        };
    // </editor-fold>

    public Type createMethodTypeWithParameters(Type original, List<Type> newParams) {
        return original.accept(methodWithParameters, newParams);
    }
    // where
        private final MapVisitor<List<Type>> methodWithParameters = new MapVisitor<List<Type>>() {
            public Type visitType(Type t, List<Type> newParams) {
                throw new IllegalArgumentException("Not a method type: " + t);
            }
            public Type visitMethodType(MethodType t, List<Type> newParams) {
                return new MethodType(newParams, t.restype, t.thrown, t.tsym);
            }
            public Type visitForAll(ForAll t, List<Type> newParams) {
                return new ForAll(t.tvars, t.qtype.accept(this, newParams));
            }
        };

    public Type createMethodTypeWithThrown(Type original, List<Type> newThrown) {
        return original.accept(methodWithThrown, newThrown);
    }
    // where
        private final MapVisitor<List<Type>> methodWithThrown = new MapVisitor<List<Type>>() {
            public Type visitType(Type t, List<Type> newThrown) {
                throw new IllegalArgumentException("Not a method type: " + t);
            }
            public Type visitMethodType(MethodType t, List<Type> newThrown) {
                return new MethodType(t.argtypes, t.restype, newThrown, t.tsym);
            }
            public Type visitForAll(ForAll t, List<Type> newThrown) {
                return new ForAll(t.tvars, t.qtype.accept(this, newThrown));
            }
        };

    public Type createMethodTypeWithReturn(Type original, Type newReturn) {
        return original.accept(methodWithReturn, newReturn);
    }
    // where
        private final MapVisitor<Type> methodWithReturn = new MapVisitor<Type>() {
            public Type visitType(Type t, Type newReturn) {
                throw new IllegalArgumentException("Not a method type: " + t);
            }
            public Type visitMethodType(MethodType t, Type newReturn) {
                return new MethodType(t.argtypes, newReturn, t.thrown, t.tsym);
            }
            public Type visitForAll(ForAll t, Type newReturn) {
                return new ForAll(t.tvars, t.qtype.accept(this, newReturn));
            }
        };

    // <editor-fold defaultstate="collapsed" desc="createErrorType">
    public Type createErrorType(Type originalType) {
        return new ErrorType(originalType, syms.errSymbol);
    }

    public Type createErrorType(ClassSymbol c, Type originalType) {
        return new ErrorType(c, originalType);
    }

    public Type createErrorType(Name name, TypeSymbol container, Type originalType) {
        return new ErrorType(name, container, originalType);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="rank">
    /**
     * The rank of a class is the length of the longest path between
     * the class and java.lang.Object in the class inheritance
     * graph. Undefined for all but reference types.
     */
    // 类的秩是类继承图中类与 java.lang.Object 之间的最长路径的长度。
    // 未定义除引用类型之外的所有类型。
    // 计算继承体系中的最长继承路径
    public int rank(Type t) {
        switch(t.tag) {
        case CLASS: {
            ClassType cls = (ClassType)t;
            // 当cls.rank_field的值小于0时，表示没有计算过这个变量的值，需要执行计算
            if (cls.rank_field < 0) {
                Name fullname = cls.tsym.getQualifiedName();
                if (fullname == names.java_lang_Object)
                    cls.rank_field = 0;
                else {
                    // 取继承体系中最长继承路径对应的值
                    int r = rank(supertype(cls));
                    for (List<Type> l = interfaces(cls);
                         l.nonEmpty();
                         l = l.tail) {
                        if (rank(l.head) > r)
                            r = rank(l.head);
                    }
                    cls.rank_field = r + 1;
                }
            }
            return cls.rank_field;
        }
        case TYPEVAR: {
            TypeVar tvar = (TypeVar)t;
            // 当cls.rank_field的值小于0时，表示没有计算过这个变量的值，需要执行计算
            if (tvar.rank_field < 0) {
                int r = rank(supertype(tvar));
                for (List<Type> l = interfaces(tvar);
                     l.nonEmpty();
                     l = l.tail) {
                    // 取继承体系中最长继承路径对应的值
                    if (rank(l.head) > r)
                        r = rank(l.head);
                }
                tvar.rank_field = r + 1;
            }
            return tvar.rank_field;
        }
        case ERROR:
            return 0;
        default:
            throw new AssertionError();
        }
    }
    // </editor-fold>

    /**
     * Helper method for generating a string representation of a given type
     * accordingly to a given locale
     */
    public String toString(Type t, Locale locale) {
        return Printer.createStandardPrinter(messages).visit(t, locale);
    }

    /**
     * Helper method for generating a string representation of a given type
     * accordingly to a given locale
     */
    public String toString(Symbol t, Locale locale) {
        return Printer.createStandardPrinter(messages).visit(t, locale);
    }

    // <editor-fold defaultstate="collapsed" desc="toString">
    /**
     * This toString is slightly more descriptive than the one on Type.
     *
     * @deprecated Types.toString(Type t, Locale l) provides better support
     * for localization
     */
    @Deprecated
    public String toString(Type t) {
        if (t.tag == FORALL) {
            ForAll forAll = (ForAll)t;
            return typaramsString(forAll.tvars) + forAll.qtype;
        }
        return "" + t;
    }
    // where
        private String typaramsString(List<Type> tvars) {
            StringBuilder s = new StringBuilder();
            s.append('<');
            boolean first = true;
            for (Type t : tvars) {
                if (!first) s.append(", ");
                first = false;
                appendTyparamString(((TypeVar)t), s);
            }
            s.append('>');
            return s.toString();
        }
        private void appendTyparamString(TypeVar t, StringBuilder buf) {
            buf.append(t);
            if (t.bound == null ||
                t.bound.tsym.getQualifiedName() == names.java_lang_Object)
                return;
            buf.append(" extends "); // Java syntax; no need for i18n
            Type bound = t.bound;
            if (!bound.isCompound()) {
                buf.append(bound);
            } else if ((erasure(t).tsym.flags() & INTERFACE) == 0) {
                buf.append(supertype(t));
                for (Type intf : interfaces(t)) {
                    buf.append('&');
                    buf.append(intf);
                }
            } else {
                // No superclass was given in bounds.
                // In this case, supertype is Object, erasure is first interface.
                boolean first = true;
                for (Type intf : interfaces(t)) {
                    if (!first) buf.append('&');
                    first = false;
                    buf.append(intf);
                }
            }
        }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Determining least upper bounds of types">
    /**
     * A cache for closures.
     *
     * <p>A closure is a list of all the supertypes and interfaces of
     * a class or interface type, ordered by ClassSymbol.precedes
     * (that is, subclasses come first, arbitrary but fixed
     * otherwise).
     */
    // 保存了类到调用closure()方法得到的列表的对应关系，避免对同一个类型的超类进行多次计算
    private Map<Type,List<Type>> closureCache = new HashMap<Type,List<Type>>();

    /**
     * Returns the closure of a class or interface type.
     */
    // 返回类或者接口类型
    // closure()方法通过调用supertype()方法查找直接父类，
    // 通过interfaces()方法查找当前类实现的所有的接口，
    // 然后递归调用closure()方法来完成所有的父类及接口查找
    public List<Type> closure(Type t) {
        List<Type> cl = closureCache.get(t);
        if (cl == null) {
            // 查找父类型
            Type st = supertype(t);
            if (!t.isCompound()) {
                // t不是组合类型
                if (st.tag == CLASS) {
                    cl = insert(closure(st), t);
                } else if (st.tag == TYPEVAR) {
                    cl = closure(st).prepend(t);
                } else {
                    // 当st.tag的值不为CLASS或TYPEVAR时，则可能为Object，
                    // 因为Object的父类为Type.noType，其tag值为NONE
                    cl = List.of(t);
                }
            } else {
                // t是组合类型
                // 如果t是组合类型时，由于组合类型并不是一个真实存在的类，因而不会将t保存到cl列表中，
                // 直接调用closure()从父类查找即可
                cl = closure(supertype(t));
            }
            // 查找接口
            for (List<Type> l = interfaces(t); l.nonEmpty(); l = l.tail)
                cl = union(cl, closure(l.head));
            closureCache.put(t, cl);
        }
        return cl;
    }

    /**
     * Insert a type in a closure
     */
    // 当st.tag的值为TYPEVAR时，将t追加到closure(st)方法返回列表的头部，
    // insert()方法不仅仅是将t插入到cl列表中，还会调用t.tsym的precedes()方法判断优先级。
    // 优先级越高，越靠近cl列表的头部位置，因此最终cl列表中的元素都是按照优先级从高到低进行排序的
    public List<Type> insert(List<Type> cl, Type t) {
        // 按优先级大小将t插入cl列表中
        if (cl.isEmpty() || t.tsym.precedes(cl.head.tsym, this)) {
            return cl.prepend(t);
        } else if (cl.head.tsym.precedes(t.tsym, this)) {
            return insert(cl.tail, t).prepend(cl.head);
        } else {
            return cl;
        }
    }

    /**
     * Form the union of two closures
     */
    // 会根据类型的优先级来合并两个列表cl1与cl2，最后返回合并后的列表
    public List<Type> union(List<Type> cl1, List<Type> cl2) {
        if (cl1.isEmpty()) {
            return cl2;
        } else if (cl2.isEmpty()) {
            return cl1;
        } else if (cl1.head.tsym.precedes(cl2.head.tsym, this)) {
            // cl1.head.tsym的优先级高
            return union(cl1.tail, cl2).prepend(cl1.head);
        } else if (cl2.head.tsym.precedes(cl1.head.tsym, this)) {
            // cl2.head.tsym的优先级高
            return union(cl1, cl2.tail).prepend(cl2.head);
        } else {
            // cl2.head.tsym与cl2.head.tsym优先级相同
            return union(cl1.tail, cl2.tail).prepend(cl1.head);
        }
    }

    /**
     * Intersect two closures
     */
    public List<Type> intersect(List<Type> cl1, List<Type> cl2) {
        // 参数列表cl1与cl2中的元素已经按优先级从高到低进行了排序，所以可以通过比较优先级快速判断两个类型的tsym是否相同。
        if (cl1 == cl2)
            return cl1;
        if (cl1.isEmpty() || cl2.isEmpty())
            return List.nil();
        // 调用TypeSymbol类中的precedes()方法比较两个类型的优先级
        // 返回false时，tsym一定不是同一个，舍弃优先级大的那个，然后对两个列表继续递归调用intersect()方法进行计算。
        if (cl1.head.tsym.precedes(cl2.head.tsym, this))
            return intersect(cl1.tail, cl2);
        if (cl2.head.tsym.precedes(cl1.head.tsym, this))
            return intersect(cl1, cl2.tail);
        if (isSameType(cl1.head, cl2.head))
            // 两个类型相等
            return intersect(cl1.tail, cl2.tail).prepend(cl1.head);
        if (cl1.head.tsym == cl2.head.tsym &&
            cl1.head.tag == CLASS && cl2.head.tag == CLASS) {
            // cl1与cl2列表中的类型可能有参数化的类型，所以需要对参数化类型也支持取交集
            if (cl1.head.isParameterized() && cl2.head.isParameterized()) {
                // 当从cl1与cl2列表中取出的类型都是参数化类型时，
                // 调用merge()方法求两个类型的交集
                Type merge = merge(cl1.head,cl2.head);
                return intersect(cl1.tail, cl2.tail).prepend(merge);
            }
            // 当两个类型中有一个是裸类型时，其擦写后的类型就是两个类型的交集
            if (cl1.head.isRaw() || cl2.head.isRaw())
                return intersect(cl1.tail, cl2.tail).prepend(erasure(cl1.head));
        }
        return intersect(cl1.tail, cl2.tail);
    }
    // where
        class TypePair {
            final Type t1;
            final Type t2;
            TypePair(Type t1, Type t2) {
                this.t1 = t1;
                this.t2 = t2;
            }
            @Override
            public int hashCode() {
                return 127 * Types.hashCode(t1) + Types.hashCode(t2);
            }
            @Override
            public boolean equals(Object obj) {
                if (!(obj instanceof TypePair))
                    return false;
                TypePair typePair = (TypePair)obj;
                return isSameType(t1, typePair.t1)
                    && isSameType(t2, typePair.t2);
            }
        }
        Set<TypePair> mergeCache = new HashSet<TypePair>();
        // 求两个类型的交集，
        private Type merge(Type c1, Type c2) {
            ClassType class1 = (ClassType) c1;
            // c1的参数化类型
            List<Type> act1 = class1.getTypeArguments();
            ClassType class2 = (ClassType) c2;
            // c2的参数化类型
            List<Type> act2 = class2.getTypeArguments();
            ListBuffer<Type> merged = new ListBuffer<Type>();
            List<Type> typarams = class1.tsym.type.getTypeArguments();

            // c1、c都不为空
            while (act1.nonEmpty() && act2.nonEmpty() && typarams.nonEmpty()) {
                if (containsType(act1.head, act2.head)) {
                    // act1包含act2
                    merged.append(act1.head);
                } else if (containsType(act2.head, act1.head)) {
                    // act2包含act1
                    merged.append(act2.head);
                } else {
                    // 没有包含关系
                    // 如：List<Integer>与List<Number>两个类型的最小上界时，调用merge()方法传递的List<Integer>与List<Number>类型并没有相互包含的关系
                    TypePair pair = new TypePair(c1, c2);
                    Type m;
                    // 会创建一个参数化类型。这个类型的实际类型参数的类型是个通配符类型
                    if (mergeCache.add(pair)) {
                        // 这个通配符类型要包含Integer与Number两个类型
                        // 具体就是先求两个实际类型参数类型的上界，然后递归调用lub()方法计算两个上界的最小上界
                        // 如Integer与Number的上界分别为Integer与Number，调用lub()方法求上界的最小上界时得到Number类型，
                        // 所以最终实际类型参数的类型为通配符类型? extends Number
                        m = new WildcardType(lub(upperBound(act1.head),
                                                 upperBound(act2.head)),
                                             BoundKind.EXTENDS,
                                             syms.boundClass);
                        mergeCache.remove(pair);
                    } else {
                        m = new WildcardType(syms.objectType,
                                             BoundKind.UNBOUND,
                                             syms.boundClass);
                    }
                    merged.append(m.withTypeVar(typarams.head));
                }
                act1 = act1.tail;
                act2 = act2.tail;
                typarams = typarams.tail;
            }
            Assert.check(act1.isEmpty() && act2.isEmpty() && typarams.isEmpty());
            return new ClassType(class1.getEnclosingType(), merged.toList(), class1.tsym);
        }

    /**
     * Return the minimum type of a closure, a compound type if no
     * unique minimum exists.
     * 返回闭包的最小类型，如果不存在唯一最小值，则返回复合类型。
     */
    // 返回最小上界
    private Type compoundMin(List<Type> cl) {
        if (cl.isEmpty())
            // 当cl为空时，最小上界为Object
            return syms.objectType;
        // cl不为空时调用closureMin()方法求最小的候选集compound
        List<Type> compound = closureMin(cl);
        if (compound.isEmpty())
            return null;
        else if (compound.tail.isEmpty())
            // 当compound中只有一个元素时，返回这个元素
            return compound.head;
        else
            // 当compound中有多于一个的元素时，调用makeCompoundType()方法创建一个组合类型
            return makeCompoundType(compound);
    }

    /**
     * Return the minimum types of a closure, suitable for computing
     * compoundMin or glb.
     */
    // cl列表中的元素是按优先级从高到低排好序
    // 一般类型变量的优先级较高，子类的优先级次之，因此列表中类型变量会先出现。
    // 如果两个类型有父子关系，则子类一定比父类的位置靠前
    // 返回cl的最小类型
    private List<Type> closureMin(List<Type> cl) {
        ListBuffer<Type> classes = lb();
        ListBuffer<Type> interfaces = lb();
        while (!cl.isEmpty()) {
            Type current = cl.head;
            if (current.isInterface())
                interfaces.append(current);
            else
                classes.append(current);
            ListBuffer<Type> candidates = lb();
            for (Type t : cl.tail) {
                if (!isSubtypeNoCapture(current, t))
                    candidates.append(t);
            }
            cl = candidates.toList();
        }
        return classes.appendList(interfaces).toList();
    }

    /**
     * Return the least upper bound of pair of types.  if the lub does
     * not exist return null.
     */
    public Type lub(Type t1, Type t2) {
        return lub(List.of(t1, t2));
    }

    /**
     * Return the least upper bound (lub) of set of types.  If the lub
     * does not exist return the type of null (bottom).
     */
    // 返回ts的最小上界
    // ts列表中保存的类型可能是类和接口、数组或者类型变量，
    // 所以在lub()方法中分情况求类型的最小上界，
    public Type lub(List<Type> ts) {
        final int ARRAY_BOUND = 1;
        final int CLASS_BOUND = 2;
        int boundkind = 0;
        // 计算ts的组成情况，分为只有数组的情况、只有类和接口的情况或者既有数组也有类和接口的情况
        for (Type t : ts) {
            switch (t.tag) {
            case CLASS:
                boundkind |= CLASS_BOUND;
                break;
            case ARRAY:
                boundkind |= ARRAY_BOUND;
                break;
            case  TYPEVAR:
                do {
                    t = t.getUpperBound();
                } while (t.tag == TYPEVAR);
                if (t.tag == ARRAY) {
                    boundkind |= ARRAY_BOUND;
                } else {
                    boundkind |= CLASS_BOUND;
                }
                break;
            default:
                if (t.isPrimitive())
                    return syms.errType;
            }
        }

        switch (boundkind) {
        case 0:
            return syms.botType;

        case ARRAY_BOUND:
            // calculate lub(A[], B[])
            // 求lub(A[], B[])
            // Type.map()方法将ts列表中所有的数组类型替换为对应的组成元素的类型
            List<Type> elements = Type.map(ts, elemTypeFun);
            for (Type t : elements) {
                if (t.isPrimitive()) {
                    // 如果有一个组成元素的类型是基本类型
                    Type first = ts.head;
                    for (Type s : ts.tail) {
                        if (!isSameType(first, s)) {
                            // 其他类型中至少有一个类型不和这个基本类型相同
                            // lub(int[], B[]) is Cloneable & Serializable
                            // lub(int[], B[]) 为Cloneable & Serializable
                            // ts列表中所有数组的最小上界只能是组合类型Cloneable & Serializable
                            return arraySuperType();
                        }
                    }
                    // all the array types are the same, return one
                    // lub(int[], int[]) is int[]
                    // 所有的数组类型相同，返回第一个数组类型即可
                    return first;
                }
            }
            // lub(A[], B[]) is lub(A, B)[]
            // 求lub(A[], B[])就是求lub(A, B)[]
            // 如果组成元素的类型都是非基本类型时，调用lub()方法求组成元素类型的最小上界，
            // 然后创建一个新的数组类型，这个类型就是求得的最小上界
            return new ArrayType(lub(elements), syms.arrayClass);

        case CLASS_BOUND: // ts列表中只含有类和接口
            // calculate lub(A, B)
            while (ts.head.tag != CLASS && ts.head.tag != TYPEVAR)
                ts = ts.tail;
            Assert.check(!ts.isEmpty());
            // 第1步：求所有擦除泛型的超类并做交集
            // 首先计算ts列表中每个元素的父类集合
            // 例如List<Integer>与List<Number>，调用erasedSupertypes()方法获取到List<Integer>的所有擦除泛型的超类为{List、Collection、Iterable、Object}，List<Number>与List<Integer>擦除泛型后类型相同，所以超类也相同。两个列表调用Types类中的intersect()方法做交集后得到的列表仍然为{List、Collection、Iterable、Object}。
            List<Type> cl = erasedSupertypes(ts.head);
            for (Type t : ts.tail) {
                if (t.tag == CLASS || t.tag == TYPEVAR)
                    // 调用Types类中的intersect()方法求两个类的交集，其实就是求共同的超类
                    cl = intersect(cl, erasedSupertypes(t));
            }
            // 第2步：求最小的候选集
            List<Type> mec = closureMin(cl);
            // 第3步：求lci(Inv(G))
            List<Type> candidates = List.nil();
            for (Type erasedSupertype : mec) {
                // Inv(mec)表示对于列表mec中的每个元素，查找在ts列表中所有对应的参数化类型

                List<Type> lci = List.of(asSuper(ts.head, erasedSupertype.tsym));
                for (Type t : ts) {
                    // 对lci列表做lci(the least containing invocation)运算
                    lci = intersect(lci, List.of(asSuper(t, erasedSupertype.tsym)));
                }
                candidates = candidates.appendList(lci);
            }
            //step 4 - let MEC be { G1, G2 ... Gn }, then we have that
            //lub = lci(Inv(G1)) & lci(Inv(G2)) & ... & lci(Inv(Gn))
            // 第4步：求最小上界¬
            return compoundMin(candidates);

        default:
            // calculate lub(A, B[])
            // 求lub(A, B[])
            // 当ts列表中的元素既有数组也有类和接口时，将数组类型替换为组合类型Object & Serializable & Cloneable
            List<Type> classes = List.of(arraySuperType());
            for (Type t : ts) {
                if (t.tag != ARRAY) // Filter out any arrays
                    classes = classes.prepend(t);
            }
            // lub(A, B[]) is lub(A, arraySuperType)
            // 求lub(A, B[])就是求lub(A, arraySuperType)
            // 调用lub()方法求这个组合类型与其他类型的最小上界
            return lub(classes);
        }
    }
    // where
        List<Type> erasedSupertypes(Type t) {
            ListBuffer<Type> buf = lb();
            // closure()方法获取t的超类型，然后循环处理各个类型
            for (Type sup : closure(t)) {
                if (sup.tag == TYPEVAR) {
                    // 如果类型为类型变量，直接追加到buf列表中
                    buf.append(sup);
                } else {
                    // 否则调用erasure()方法将泛型擦除后的类型追加到buf列表中。
                    buf.append(erasure(sup));
                }
            }
            // 需要注意的是，调用closure()方法返回的列表中的元素是按照优先级从高到低排好序的，所以最终的buf列表中的元素也是按照优先级排好序，这样在lub()方法中调用intersect()方法时就会利用排序规则快速获取两个列表中类型的交集。
            return buf.toList();
        }

        private Type arraySuperType = null;
        // 调用makeCompoundType()方法创建一个组合类型，
        // 这个组合类型的父类为Object并且实现了接口Serializable与Cloneable，
        // 不过这个组合类型并不是真实存在，
        // 所以对应的ClassSymbol对象中的flags_field中含有SYNTHETIC与COMPOUND标识
        private Type arraySuperType() {
            // initialized lazily to avoid problems during compiler startup
            if (arraySuperType == null) {
                synchronized (this) {
                    if (arraySuperType == null) {
                        // JLS 10.8: all arrays implement Cloneable and Serializable.
                        arraySuperType = makeCompoundType(List.of(syms.serializableType,
                                                                  syms.cloneableType),
                                                          syms.objectType);
                    }
                }
            }
            return arraySuperType;
        }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Greatest lower bound">
    public Type glb(List<Type> ts) {
        Type t1 = ts.head;
        for (Type t2 : ts.tail) {
            if (t1.isErroneous())
                return t1;
            t1 = glb(t1, t2);
        }
        return t1;
    }
    //where
    // glb()方法可以求两个类型的最大下界（Greasted Lower Bound）
    public Type glb(Type t, Type s) {
        if (s == null)
            return t;
        // 调用glb()方法的前提是t与s都必须为引用类型
        else if (t.isPrimitive() || s.isPrimitive())
            return syms.errType;
        // 如果t和s有父子关系，则返回子类即可
        else if (isSubtypeNoCapture(t, s))
            return t;
        // 如果t和s有父子关系，则返回子类即可
        else if (isSubtypeNoCapture(s, t))
            return s;

        // 根据类型的优先级来合并两个列表cl1与cl2，最后返回合并后的列表
        List<Type> closure = union(closure(t), closure(s));
        // 调用closureMin()方法计算bounds的值
        List<Type> bounds = closureMin(closure);

        // bounds列表中没有元素
        if (bounds.isEmpty()) {
            return syms.objectType;
        } else if (bounds.tail.isEmpty()) {
            // bounds列表中只有一个元素
            return bounds.head;
        } else {
            // bounds列表中至少有两个元素
            int classCount = 0;
            for (Type bound : bounds)
                if (!bound.isInterface())
                    classCount++;
            if (classCount > 1)
                return createErrorType(t);
        }
        // 当bounds列表中的值多于一个时，则调用makeCompoundType()方法创建一个组合类型
        return makeCompoundType(bounds);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="hashCode">
    /**
     * Compute a hash code on a type.
     */
    public static int hashCode(Type t) {
        return hashCode.visit(t);
    }
    // where
        private static final UnaryVisitor<Integer> hashCode = new UnaryVisitor<Integer>() {

            public Integer visitType(Type t, Void ignored) {
                return t.tag;
            }

            @Override
            public Integer visitClassType(ClassType t, Void ignored) {
                int result = visit(t.getEnclosingType());
                result *= 127;
                result += t.tsym.flatName().hashCode();
                for (Type s : t.getTypeArguments()) {
                    result *= 127;
                    result += visit(s);
                }
                return result;
            }

            @Override
            public Integer visitWildcardType(WildcardType t, Void ignored) {
                int result = t.kind.hashCode();
                if (t.type != null) {
                    result *= 127;
                    result += visit(t.type);
                }
                return result;
            }

            @Override
            public Integer visitArrayType(ArrayType t, Void ignored) {
                return visit(t.elemtype) + 12;
            }

            @Override
            public Integer visitTypeVar(TypeVar t, Void ignored) {
                return System.identityHashCode(t.tsym);
            }

            @Override
            public Integer visitUndetVar(UndetVar t, Void ignored) {
                return System.identityHashCode(t);
            }

            @Override
            public Integer visitErrorType(ErrorType t, Void ignored) {
                return 0;
            }
        };
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Return-Type-Substitutable">
    /**
     * Does t have a result that is a subtype of the result type of s,
     * suitable for covariant returns?  It is assumed that both types
     * are (possibly polymorphic) method types.  Monomorphic method
     * types are handled in the obvious way.  Polymorphic method types
     * require renaming all type variables of one to corresponding
     * type variables in the other, where correspondence is by
     * position in the type parameter list. */
    public boolean resultSubtype(Type t, Type s, Warner warner) {
        List<Type> tvars = t.getTypeArguments();
        List<Type> svars = s.getTypeArguments();
        Type tres = t.getReturnType();
        Type sres = subst(s.getReturnType(), svars, tvars);
        // 调用covariantReturnType()方法判断参数t与s这两个方法的返回类型
        return covariantReturnType(tres, sres, warner);
    }

    /**
     * Return-Type-Substitutable.
     * @jls section 8.4.5
     */
    public boolean returnTypeSubstitutable(Type r1, Type r2) {
        // 调用hasSameArgs()方法比较两个方法的形式参数类型，
        // 需要注意的是，如果是一个由ForAll对象表示的泛型方法和一个MethodType对象表示的非泛型方法进行比较时，即使形式参数相同，hasSameArgs()方法仍然会返回false
        // 例10-21
        if (hasSameArgs(r1, r2))
            return resultSubtype(r1, r2, Warner.noWarnings);
        else
            // 如果相同就会继续调用resultSubtype()方法比较方法的返回类型
            return covariantReturnType(r1.getReturnType(),
                                       erasure(r2.getReturnType()),
                                       Warner.noWarnings);
    }

    public boolean returnTypeSubstitutable(Type r1,
                                           Type r2, Type r2res,
                                           Warner warner) {
        if (isSameType(r1.getReturnType(), r2res))
            return true;
        if (r1.getReturnType().isPrimitive() || r2res.isPrimitive())
            return false;


        if (hasSameArgs(r1, r2))
            return covariantReturnType(r1.getReturnType(), r2res, warner);
        if (!allowCovariantReturns)
            return false;
        if (isSubtypeUnchecked(r1.getReturnType(), r2res, warner))
            return true;
        if (!isSubtype(r1.getReturnType(), erasure(r2res)))
            return false;
        warner.warn(LintCategory.UNCHECKED);
        return true;
    }

    /**
     * Is t an appropriate return type in an overrider for a
     * method that returns s?
     */
    // 对于返回 s 的方法，重写器中的返回类型是否符合
    // 以下两种情况下types.covariantReturnType()方法将返回true
    // 1:当两个类型相同时，那么即使是基本类型也返回true
    // 2:当两个返回类型都是引用类型并且t可赋值给s时方法将返回true
    public boolean covariantReturnType(Type t, Type s, Warner warner) {
        return
            isSameType(t, s) ||
            allowCovariantReturns &&
            !t.isPrimitive() &&
            !s.isPrimitive() &&
            isAssignable(t, s, warner);
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Box/unbox support">
    /**
     * Return the class that boxes the given primitive.
     */
    public ClassSymbol boxedClass(Type t) {
        return reader.enterClass(syms.boxedName[t.tag]);
    }

    /**
     * Return the boxed type if 't' is primitive, otherwise return 't' itself.
     */
    // 对t进行装箱转换，types.boxedTypeOrType
    public Type boxedTypeOrType(Type t) {
        return t.isPrimitive() ?
            boxedClass(t).type :
            t;
    }

    /**
     * Return the primitive type corresponding to a boxed type.
     */
    public Type unboxedType(Type t) {
        if (allowBoxing) {
            for (int i=0; i<syms.boxedName.length; i++) {
                Name box = syms.boxedName[i];
                if (box != null &&
                    asSuper(t, reader.enterClass(box)) != null)
                    return syms.typeOfTag[i];
            }
        }
        return Type.noType;
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Capture conversion">
    /*
     * JLS 5.1.10 Capture Conversion:
     *
     * Let G name a generic type declaration with n formal type
     * parameters A1 ... An with corresponding bounds U1 ... Un. There
     * exists a capture conversion from G<T1 ... Tn> to G<S1 ... Sn>,
     * where, for 1 <= i <= n:
     *
     * + If Ti is a wildcard type argument (4.5.1) of the form ? then
     *   Si is a fresh type variable whose upper bound is
     *   Ui[A1 := S1, ..., An := Sn] and whose lower bound is the null
     *   type.
     *
     * + If Ti is a wildcard type argument of the form ? extends Bi,
     *   then Si is a fresh type variable whose upper bound is
     *   glb(Bi, Ui[A1 := S1, ..., An := Sn]) and whose lower bound is
     *   the null type, where glb(V1,... ,Vm) is V1 & ... & Vm. It is
     *   a compile-time error if for any two classes (not interfaces)
     *   Vi and Vj,Vi is not a subclass of Vj or vice versa.
     *
     * + If Ti is a wildcard type argument of the form ? super Bi,
     *   then Si is a fresh type variable whose upper bound is
     *   Ui[A1 := S1, ..., An := Sn] and whose lower bound is Bi.
     *
     * + Otherwise, Si = Ti.
     *
     * Capture conversion on any type other than a parameterized type
     * (4.5) acts as an identity conversion (5.1.1). Capture
     * conversions never require a special action at run time and
     * therefore never throw an exception at run time.
     *
     * Capture conversion is not applied recursively.
     */
    /**
     * Capture conversion as specified by the JLS.
     */

    public List<Type> capture(List<Type> ts) {
        List<Type> buf = List.nil();
        for (Type t : ts) {
            buf = buf.prepend(capture(t));
        }
        return buf.reverse();
    }
    // 类型转换，类型捕获
    // capture()方法会对所有的通配符类型进行类型捕获
    public Type capture(Type t) {
        if (t.tag != CLASS)
            return t;
        if (t.getEnclosingType() != Type.noType) {
            Type capturedEncl = capture(t.getEnclosingType());
            if (capturedEncl != t.getEnclosingType()) {
                Type type1 = memberType(capturedEncl, t.tsym);
                t = subst(type1, t.tsym.type.getTypeArguments(), t.getTypeArguments());
            }
        }
        ClassType cls = (ClassType)t;
        // 只针对参数化类型进行捕获，如果cls为裸类型或不是参数化类型时，则直接返回
        if (cls.isRaw() || !cls.isParameterized())
            return cls;
        // G中声明了类型参数
        ClassType G = (ClassType)cls.asElement().asType();
        // 形式类型参数的类型列表
        // A列表中保存着所有声明类型参数的类型：class G<A1 extends U1>{}
        List<Type> A = G.getTypeArguments();
        // 实际类型参数的类型列表
        // T列表中保存着所有的实际类型参数的类型：G<? extends B1> a = new G<>();
        List<Type> T = cls.getTypeArguments();
        // 经过捕获转换后的类型列表
        List<Type> S = freshTypeVariables(T);

        List<Type> currentA = A;
        List<Type> currentT = T;
        List<Type> currentS = S;
        boolean captured = false;
        while (!currentA.isEmpty() &&
               !currentT.isEmpty() &&
               !currentS.isEmpty()) {
            // 当currentS.head不等于currentT.head时，也就是WildcardType对象被封装为CapturedType对象，需要进行类型捕获
            if (currentS.head != currentT.head) {
                captured = true;
                WildcardType Ti = (WildcardType)currentT.head;
                Type Ui = currentA.head.getUpperBound();
                CapturedType Si = (CapturedType)currentS.head;
                if (Ui == null)
                    Ui = syms.objectType;
                switch (Ti.kind) {
                    // 当实际类型参数为无界通配符时，需要计算捕获类型上界与下界
                case UNBOUND:
                    // subst()方法将上界中含有的类型变量全部替换为捕获类型Si
                    Si.bound = subst(Ui, A, S);
                    // 而下界为null，表示无下界
                    Si.lower = syms.botType;
                    break;
                    // 当实际类型参数为上界通配符时，需要计算捕获类型上界与下界
                case EXTENDS:
                    // 调用glb()方法计算两个上界类型的最大下界并作为Si的上界
                    Si.bound = glb(Ti.getExtendsBound(), subst(Ui, A, S));
                    // 下界为null，表示无下界
                    Si.lower = syms.botType;
                    break;
                    // 当实际类型参数为下界通配符时，需要计算捕获类型上界与下界
                case SUPER:
                    // 上界为类型中声明类型参数时的上界，
                    Si.bound = subst(Ui, A, S);
                    // 而下界就是实际传递的类型参数下界，也就是下界通配符下界。
                    Si.lower = Ti.getSuperBound();
                    break;
                }
                if (Si.bound == Si.lower)
                    currentS.head = Si.bound;
            }
            currentA = currentA.tail;
            currentT = currentT.tail;
            currentS = currentS.tail;
        }
        if (!currentA.isEmpty() || !currentT.isEmpty() || !currentS.isEmpty())
            return erasure(t); // some "rare" type involved

        if (captured)
            return new ClassType(cls.getEnclosingType(), S, cls.tsym);
        else
            return t;
    }
    // where
    // 捕获并转换后类型参数
    // 参数types是实际类型参数的类型：G<? extends B1> a = new G<>();
        public List<Type> freshTypeVariables(List<Type> types) {
            ListBuffer<Type> result = lb();
            for (Type t : types) {
                // 当t为通配符类型时，需要进行捕获转换
                if (t.tag == WILDCARD) {
                    Type bound = ((WildcardType)t).getExtendsBound();
                    if (bound == null)
                        bound = syms.objectType;
                    // 将每个WildcardType对象封装为CapturedType对象并按顺序保存到result列表中
                    // 而且两个列表中相同位置的元素有对应关系
                    result.append(new CapturedType(capturedName,
                                                   syms.noSymbol,
                                                   bound,
                                                   syms.botType,
                                                   (WildcardType)t));
                } else {
                    result.append(t);
                }
            }
            return result.toList();
        }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Internal utility methods">
    private List<Type> upperBounds(List<Type> ss) {
        if (ss.isEmpty()) return ss;
        Type head = upperBound(ss.head);
        List<Type> tail = upperBounds(ss.tail);
        if (head != ss.head || tail != ss.tail)
            return tail.prepend(head);
        else
            return ss;
    }

    // from类型转换为to类型，
    // 它们是非最终的不相关。
    // 此方法尝试通过公共超接口将类型参数从 to 传输到 from 来拒绝强制转换。
    // sideCast()方法首先对from与to参数进行调整，调整后from肯定为非final修饰的类或接口，而to肯定为接口
    private boolean sideCast(Type from, Type to, Warner warn) {
        boolean reverse = false;
        Type target = to;
        // // 当to不为接口时，调整from与to参数的值
        if ((to.tsym.flags() & INTERFACE) == 0) {
            Assert.check((from.tsym.flags() & INTERFACE) != 0);
            reverse = true;
            to = from;
            from = target;
        }
        // from为非final修饰的类或接口，而to为接口
        // 对from进行泛型擦除后，调用superClosure()方法查找与to的所有共同父类
        List<Type> commonSupers = superClosure(to, erasure(from));
        boolean giveWarning = commonSupers.isEmpty();
        // 查找from与to的所有父类和接口的共同参数化类型并判断
        while (commonSupers.nonEmpty()) {
            // 查找from与to的所有参数化类型
            Type t1 = asSuper(from, commonSupers.head.tsym);
            // // 也可以通过调用asSuper(to, commonSupers.head.tsym)方法得到t2
            Type t2 = commonSupers.head;
            // 如果t1与t2都为参数化类型，判断实际类型参数是否互斥
            // 这个类型在擦写后是同一个类型，找到的参数化类型为t1与t2
            if (disjointTypes(t1.getTypeArguments(), t2.getTypeArguments()))
                return false;
            giveWarning = giveWarning || (reverse ? giveWarning(t2, t1) : giveWarning(t1, t2));
            commonSupers = commonSupers.tail;
        }
        if (giveWarning && !isReifiable(reverse ? from : to))
            warn.warn(LintCategory.UNCHECKED);
        if (!allowCovariantReturns)
            // reject if there is a common method signature with
            // incompatible return types.
            chk.checkCompatibleAbstracts(warn.pos(), from, to);
        return true;
    }

    // from类型转换为to类型
    private boolean sideCastFinal(Type from, Type to, Warner warn) {
        boolean reverse = false;
        Type target = to;
        // 当to不为接口时，调整from与to参数的值
        if ((to.tsym.flags() & INTERFACE) == 0) {
            Assert.check((from.tsym.flags() & INTERFACE) != 0);
            reverse = true;
            to = from;
            from = target;
        }
        Assert.check((from.tsym.flags() & FINAL) != 0);
        Type t1 = asSuper(from, to.tsym);
        if (t1 == null) return false;
        Type t2 = to;
        if (disjointTypes(t1.getTypeArguments(), t2.getTypeArguments()))
            return false;
        if (!allowCovariantReturns)
            // reject if there is a common method signature with
            // incompatible return types.
            chk.checkCompatibleAbstracts(warn.pos(), from, to);
        if (!isReifiable(target) &&
            (reverse ? giveWarning(t2, t1) : giveWarning(t1, t2)))
            warn.warn(LintCategory.UNCHECKED);
        return true;
    }

    private boolean giveWarning(Type from, Type to) {
        Type subFrom = asSub(from, to.tsym);
        return to.isParameterized() &&
                (!(isUnbounded(to) ||
                isSubtype(from, to) ||
                ((subFrom != null) && containsType(to.allparams(), subFrom.allparams()))));
    }

    private List<Type> superClosure(Type t, Type s) {
        List<Type> cl = List.nil();
        // t为接口，因此只需要循环检查所有的实现接口即可
        // 参数t为接口而s为泛型擦除后的类型，调用interfaces()方法查找t的所有实现接口后，判断这些接口与s的关系
        for (List<Type> l = interfaces(t); l.nonEmpty(); l = l.tail) {
            if (isSubtype(s, erasure(l.head))) {
                // 当s是泛型擦除后的接口的子类时，则调用insert()方法添加到cl列表中
                cl = insert(cl, l.head);
            } else {
                // 否则递归调用superClosure()方法继续查找，调用union()方法将找到后的列表合并到cl列表中
                cl = union(cl, superClosure(l.head, s));
            }
        }
        return cl;
    }

    // 比较两个类型是否相同
    private boolean containsTypeEquivalent(Type t, Type s) {
        return
                // 调用isSameType()方法判断两个类型是否相同
            isSameType(t, s) ||
                    // 如果不想听，判断两个类型是否彼此包含
            containsType(t, s) && containsType(s, t);
    }

    // <editor-fold defaultstate="collapsed" desc="adapt">
    /**
     * Adapt a type by computing a substitution which maps a source
     * type to a target type.
     *
     * @param source    the source type
     * @param target    the target type
     * @param from      the type variables of the computed substitution
     * @param to        the types of the computed substitution.
     */
    public void adapt(Type source,
                       Type target,
                       ListBuffer<Type> from,
                       ListBuffer<Type> to) throws AdaptFailure {
        new Adapter(from, to).adapt(source, target);
    }

    class Adapter extends SimpleVisitor<Void, Type> {

        ListBuffer<Type> from;
        ListBuffer<Type> to;
        Map<Symbol,Type> mapping;

        Adapter(ListBuffer<Type> from, ListBuffer<Type> to) {
            this.from = from;
            this.to = to;
            mapping = new HashMap<Symbol,Type>();
        }

        public void adapt(Type source, Type target) throws AdaptFailure {
            visit(source, target);
            List<Type> fromList = from.toList();
            List<Type> toList = to.toList();
            while (!fromList.isEmpty()) {
                Type val = mapping.get(fromList.head.tsym);
                if (toList.head != val)
                    toList.head = val;
                fromList = fromList.tail;
                toList = toList.tail;
            }
        }

        @Override
        public Void visitClassType(ClassType source, Type target) throws AdaptFailure {
            if (target.tag == CLASS)
                adaptRecursive(source.allparams(), target.allparams());
            return null;
        }

        @Override
        public Void visitArrayType(ArrayType source, Type target) throws AdaptFailure {
            if (target.tag == ARRAY)
                adaptRecursive(elemtype(source), elemtype(target));
            return null;
        }

        @Override
        public Void visitWildcardType(WildcardType source, Type target) throws AdaptFailure {
            if (source.isExtendsBound())
                adaptRecursive(upperBound(source), upperBound(target));
            else if (source.isSuperBound())
                adaptRecursive(lowerBound(source), lowerBound(target));
            return null;
        }

        @Override
        public Void visitTypeVar(TypeVar source, Type target) throws AdaptFailure {
            // Check to see if there is
            // already a mapping for $source$, in which case
            // the old mapping will be merged with the new
            Type val = mapping.get(source.tsym);
            if (val != null) {
                if (val.isSuperBound() && target.isSuperBound()) {
                    val = isSubtype(lowerBound(val), lowerBound(target))
                        ? target : val;
                } else if (val.isExtendsBound() && target.isExtendsBound()) {
                    val = isSubtype(upperBound(val), upperBound(target))
                        ? val : target;
                } else if (!isSameType(val, target)) {
                    throw new AdaptFailure();
                }
            } else {
                val = target;
                from.append(source);
                to.append(target);
            }
            mapping.put(source.tsym, val);
            return null;
        }

        @Override
        public Void visitType(Type source, Type target) {
            return null;
        }

        private Set<TypePair> cache = new HashSet<TypePair>();

        private void adaptRecursive(Type source, Type target) {
            TypePair pair = new TypePair(source, target);
            if (cache.add(pair)) {
                try {
                    visit(source, target);
                } finally {
                    cache.remove(pair);
                }
            }
        }

        private void adaptRecursive(List<Type> source, List<Type> target) {
            if (source.length() == target.length()) {
                while (source.nonEmpty()) {
                    adaptRecursive(source.head, target.head);
                    source = source.tail;
                    target = target.tail;
                }
            }
        }
    }

    public static class AdaptFailure extends RuntimeException {
        static final long serialVersionUID = -7490231548272701566L;
    }

    private void adaptSelf(Type t,
                           ListBuffer<Type> from,
                           ListBuffer<Type> to) {
        try {
            //if (t.tsym.type != t)
                adapt(t.tsym.type, t, from, to);
        } catch (AdaptFailure ex) {
            // Adapt should never fail calculating a mapping from
            // t.tsym.type to t as there can be no merge problem.
            throw new AssertionError(ex);
        }
    }
    // </editor-fold>

    /**
     * Rewrite all type variables (universal quantifiers) in the given
     * type to wildcards (existential quantifiers).  This is used to
     * determine if a cast is allowed.  For example, if high is true
     * and {@code T <: Number}, then {@code List<T>} is rewritten to
     * {@code List<?  extends Number>}.  Since {@code List<Integer> <:
     * List<? extends Number>} a {@code List<T>} can be cast to {@code
     * List<Integer>} with a warning.
     * @param t a type
     * @param high if true return an upper bound; otherwise a lower
     * bound
     * @param rewriteTypeVars only rewrite captured wildcards if false;
     * otherwise rewrite all type variables
     * @return the type rewritten with wildcards (existential
     * quantifiers) only
     */
    private Type rewriteQuantifiers(Type t, boolean high, boolean rewriteTypeVars) {
        return new Rewriter(high, rewriteTypeVars).visit(t);
    }

    class Rewriter extends UnaryVisitor<Type> {

        boolean high;
        boolean rewriteTypeVars;

        Rewriter(boolean high, boolean rewriteTypeVars) {
            this.high = high;
            this.rewriteTypeVars = rewriteTypeVars;
        }

        @Override
        public Type visitClassType(ClassType t, Void s) {
            ListBuffer<Type> rewritten = new ListBuffer<Type>();
            boolean changed = false;
            for (Type arg : t.allparams()) {
                Type bound = visit(arg);
                if (arg != bound) {
                    changed = true;
                }
                rewritten.append(bound);
            }
            if (changed)
                return subst(t.tsym.type,
                        t.tsym.type.allparams(),
                        rewritten.toList());
            else
                return t;
        }

        public Type visitType(Type t, Void s) {
            return high ? upperBound(t) : lowerBound(t);
        }

        @Override
        public Type visitCapturedType(CapturedType t, Void s) {
            Type bound = visitWildcardType(t.wildcard, null);
            return (bound.contains(t)) ?
                    erasure(bound) :
                    bound;
        }

        @Override
        public Type visitTypeVar(TypeVar t, Void s) {
            if (rewriteTypeVars) {
                Type bound = high ?
                    (t.bound.contains(t) ?
                        erasure(t.bound) :
                        visit(t.bound)) :
                    syms.botType;
                return rewriteAsWildcardType(bound, t);
            }
            else
                return t;
        }

        @Override
        public Type visitWildcardType(WildcardType t, Void s) {
            Type bound = high ? t.getExtendsBound() :
                                t.getSuperBound();
            if (bound == null)
            bound = high ? syms.objectType : syms.botType;
            return rewriteAsWildcardType(visit(bound), t.bound);
        }

        private Type rewriteAsWildcardType(Type bound, TypeVar formal) {
            return high ?
                makeExtendsWildcard(B(bound), formal) :
                makeSuperWildcard(B(bound), formal);
        }

        Type B(Type t) {
            while (t.tag == WILDCARD) {
                WildcardType w = (WildcardType)t;
                t = high ?
                    w.getExtendsBound() :
                    w.getSuperBound();
                if (t == null) {
                    t = high ? syms.objectType : syms.botType;
                }
            }
            return t;
        }
    }


    /**
     * Create a wildcard with the given upper (extends) bound; create
     * an unbounded wildcard if bound is Object.
     *
     * @param bound the upper bound
     * @param formal the formal type parameter that will be
     * substituted by the wildcard
     */
    private WildcardType makeExtendsWildcard(Type bound, TypeVar formal) {
        if (bound == syms.objectType) {
            return new WildcardType(syms.objectType,
                                    BoundKind.UNBOUND,
                                    syms.boundClass,
                                    formal);
        } else {
            return new WildcardType(bound,
                                    BoundKind.EXTENDS,
                                    syms.boundClass,
                                    formal);
        }
    }

    /**
     * Create a wildcard with the given lower (super) bound; create an
     * unbounded wildcard if bound is bottom (type of {@code null}).
     *
     * @param bound the lower bound
     * @param formal the formal type parameter that will be
     * substituted by the wildcard
     */
    private WildcardType makeSuperWildcard(Type bound, TypeVar formal) {
        if (bound.tag == BOT) {
            return new WildcardType(syms.objectType,
                                    BoundKind.UNBOUND,
                                    syms.boundClass,
                                    formal);
        } else {
            return new WildcardType(bound,
                                    BoundKind.SUPER,
                                    syms.boundClass,
                                    formal);
        }
    }

    /**
     * A wrapper for a type that allows use in sets.
     */
    class SingletonType {
        final Type t;
        SingletonType(Type t) {
            this.t = t;
        }
        public int hashCode() {
            // hashCode()方法在实现时调用了Types.hashCode()方法，
            // Types.hashCode()方法可以保证相同的两个类型返回的哈希值一定相同
            return Types.hashCode(t);
        }
        public boolean equals(Object obj) {
            // 而equals()方法对两个SingletonType对象进行比较，
            // 最终还会调用isSameType()方法进行比较，也就是比较组合类型的实现接口。
            // 例c-9
            return (obj instanceof SingletonType) &&
                isSameType(t, ((SingletonType)obj).t);
        }
        public String toString() {
            return t.toString();
        }
    }
    // </editor-fold>

    // <editor-fold defaultstate="collapsed" desc="Visitors">
    /**
     * A default visitor for types.  All visitor methods except
     * visitType are implemented by delegating to visitType.  Concrete
     * subclasses must provide an implementation of visitType and can
     * override other methods as needed.
     *
     * @param <R> the return type of the operation implemented by this
     * visitor; use Void if no return type is needed.
     * @param <S> the type of the second argument (the first being the
     * type itself) of the operation implemented by this visitor; use
     * Void if a second argument is not needed.
     */
    public static abstract class DefaultTypeVisitor<R,S> implements Type.Visitor<R,S> {
        final public R visit(Type t, S s)               { return t.accept(this, s); }
        public R visitClassType(ClassType t, S s)       { return visitType(t, s); }
        public R visitWildcardType(WildcardType t, S s) { return visitType(t, s); }
        public R visitArrayType(ArrayType t, S s)       { return visitType(t, s); }
        public R visitMethodType(MethodType t, S s)     { return visitType(t, s); }
        public R visitPackageType(PackageType t, S s)   { return visitType(t, s); }
        public R visitTypeVar(TypeVar t, S s)           { return visitType(t, s); }
        public R visitCapturedType(CapturedType t, S s) { return visitType(t, s); }
        public R visitForAll(ForAll t, S s)             { return visitType(t, s); }
        public R visitUndetVar(UndetVar t, S s)         { return visitType(t, s); }
        public R visitErrorType(ErrorType t, S s)       { return visitType(t, s); }
    }

    /**
     * A default visitor for symbols.  All visitor methods except
     * visitSymbol are implemented by delegating to visitSymbol.  Concrete
     * subclasses must provide an implementation of visitSymbol and can
     * override other methods as needed.
     *
     * @param <R> the return type of the operation implemented by this
     * visitor; use Void if no return type is needed.
     * @param <S> the type of the second argument (the first being the
     * symbol itself) of the operation implemented by this visitor; use
     * Void if a second argument is not needed.
     */
    public static abstract class DefaultSymbolVisitor<R,S> implements Symbol.Visitor<R,S> {
        final public R visit(Symbol s, S arg)                   { return s.accept(this, arg); }
        public R visitClassSymbol(ClassSymbol s, S arg)         { return visitSymbol(s, arg); }
        public R visitMethodSymbol(MethodSymbol s, S arg)       { return visitSymbol(s, arg); }
        public R visitOperatorSymbol(OperatorSymbol s, S arg)   { return visitSymbol(s, arg); }
        public R visitPackageSymbol(PackageSymbol s, S arg)     { return visitSymbol(s, arg); }
        public R visitTypeSymbol(TypeSymbol s, S arg)           { return visitSymbol(s, arg); }
        public R visitVarSymbol(VarSymbol s, S arg)             { return visitSymbol(s, arg); }
    }

    /**
     * A <em>simple</em> visitor for types.  This visitor is simple as
     * captured wildcards, for-all types (generic methods), and
     * undetermined type variables (part of inference) are hidden.
     * Captured wildcards are hidden by treating them as type
     * variables and the rest are hidden by visiting their qtypes.
     *
     * @param <R> the return type of the operation implemented by this
     * visitor; use Void if no return type is needed.
     * @param <S> the type of the second argument (the first being the
     * type itself) of the operation implemented by this visitor; use
     * Void if a second argument is not needed.
     */
    public static abstract class SimpleVisitor<R,S> extends DefaultTypeVisitor<R,S> {
        @Override
        public R visitCapturedType(CapturedType t, S s) {
            return visitTypeVar(t, s);
        }
        @Override
        public R visitForAll(ForAll t, S s) {
            return visit(t.qtype, s);
        }
        @Override
        public R visitUndetVar(UndetVar t, S s) {
            return visit(t.qtype, s);
        }
    }

    /**
     * A plain relation on types.  That is a 2-ary function on the
     * form Type&nbsp;&times;&nbsp;Type&nbsp;&rarr;&nbsp;Boolean.
     * <!-- In plain text: Type x Type -> Boolean -->
     */
    public static abstract class TypeRelation extends SimpleVisitor<Boolean,Type> {}

    /**
     * A convenience visitor for implementing operations that only
     * require one argument (the type itself), that is, unary
     * operations.
     *
     * @param <R> the return type of the operation implemented by this
     * visitor; use Void if no return type is needed.
     */
    public static abstract class UnaryVisitor<R> extends SimpleVisitor<R,Void> {
        final public R visit(Type t) { return t.accept(this, null); }
    }

    /**
     * A visitor for implementing a mapping from types to types.  The
     * default behavior of this class is to implement the identity
     * mapping (mapping a type to itself).  This can be overridden in
     * subclasses.
     *
     * @param <S> the type of the second argument (the first being the
     * type itself) of this mapping; use Void if a second argument is
     * not needed.
     */
    public static class MapVisitor<S> extends DefaultTypeVisitor<Type,S> {
        final public Type visit(Type t) { return t.accept(this, null); }
        public Type visitType(Type t, S s) { return t; }
    }
    // </editor-fold>


    // <editor-fold defaultstate="collapsed" desc="Annotation support">

    public RetentionPolicy getRetention(Attribute.Compound a) {
        RetentionPolicy vis = RetentionPolicy.CLASS; // the default
        Attribute.Compound c = a.type.tsym.attribute(syms.retentionType.tsym);
        if (c != null) {
            Attribute value = c.member(names.value);
            if (value != null && value instanceof Attribute.Enum) {
                Name levelName = ((Attribute.Enum)value).value.name;
                if (levelName == names.SOURCE) vis = RetentionPolicy.SOURCE;
                else if (levelName == names.CLASS) vis = RetentionPolicy.CLASS;
                else if (levelName == names.RUNTIME) vis = RetentionPolicy.RUNTIME;
                else ;// /* fail soft */ throw new AssertionError(levelName);
            }
        }
        return vis;
    }
    // </editor-fold>
}
