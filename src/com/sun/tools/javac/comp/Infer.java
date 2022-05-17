/*
 * Copyright (c) 1999, 2011, Oracle and/or its affiliates. All rights reserved.
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

package com.sun.tools.javac.comp;

import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCTypeCast;
import com.sun.tools.javac.tree.TreeInfo;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.List;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.code.Type.ForAll.ConstraintKind;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.util.JCDiagnostic;

import static com.sun.tools.javac.code.TypeTags.*;

/** Helper class for type parameter inference, used by the attribution phase.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
public class Infer {
    protected static final Context.Key<Infer> inferKey =
        new Context.Key<Infer>();

    /** A value for prototypes that admit any type, including polymorphic ones. */
    // 允许任何类型（包括泛型）的原型的值。
    public static final Type anyPoly = new Type(NONE, null);

    Symtab syms;
    Types types;
    Check chk;
    Resolve rs;
    JCDiagnostic.Factory diags;

    public static Infer instance(Context context) {
        Infer instance = context.get(inferKey);
        if (instance == null)
            instance = new Infer(context);
        return instance;
    }

    protected Infer(Context context) {
        context.put(inferKey, this);
        syms = Symtab.instance(context);
        types = Types.instance(context);
        rs = Resolve.instance(context);
        chk = Check.instance(context);
        diags = JCDiagnostic.Factory.instance(context);
        ambiguousNoInstanceException =
            new NoInstanceException(true, diags);
        unambiguousNoInstanceException =
            new NoInstanceException(false, diags);
        invalidInstanceException =
            new InvalidInstanceException(diags);

    }

    public static class InferenceException extends Resolve.InapplicableMethodException {
        private static final long serialVersionUID = 0;

        InferenceException(JCDiagnostic.Factory diags) {
            super(diags);
        }
    }

    public static class NoInstanceException extends InferenceException {
        private static final long serialVersionUID = 1;

        boolean isAmbiguous; // exist several incomparable best instances?

        NoInstanceException(boolean isAmbiguous, JCDiagnostic.Factory diags) {
            super(diags);
            this.isAmbiguous = isAmbiguous;
        }
    }

    public static class InvalidInstanceException extends InferenceException {
        private static final long serialVersionUID = 2;

        InvalidInstanceException(JCDiagnostic.Factory diags) {
            super(diags);
        }
    }

    private final NoInstanceException ambiguousNoInstanceException;
    private final NoInstanceException unambiguousNoInstanceException;
    private final InvalidInstanceException invalidInstanceException;

/***************************************************************************
 * Auxiliary type values and classes
 ***************************************************************************/

    /** A mapping that turns type variables into undetermined type variables.
     */
    Mapping fromTypeVarFun = new Mapping("fromTypeVarFun") {
            public Type apply(Type t) {
                if (t.tag == TYPEVAR) return new UndetVar(t);
                else return t.map(this);
            }
        };

    /** A mapping that returns its type argument with every UndetVar replaced
     *  by its `inst' field. Throws a NoInstanceException
     *  if this not possible because an `inst' field is null.
     *  Note: mutually referring undertvars will be left uninstantiated
     *  (that is, they will be replaced by the underlying type-variable).
     */

    Mapping getInstFun = new Mapping("getInstFun") {
            public Type apply(Type t) {
                switch (t.tag) {
                    case UNKNOWN:
                        throw ambiguousNoInstanceException
                            .setMessage("undetermined.type");
                    case UNDETVAR:
                        UndetVar that = (UndetVar) t;
                        if (that.inst == null)
                            throw ambiguousNoInstanceException
                                .setMessage("type.variable.has.undetermined.type",
                                            that.qtype);
                        return isConstraintCyclic(that) ?
                            that.qtype :
                            apply(that.inst);
                        default:
                            return t.map(this);
                }
            }

            private boolean isConstraintCyclic(UndetVar uv) {
                Types.UnaryVisitor<Boolean> constraintScanner =
                        new Types.UnaryVisitor<Boolean>() {

                    List<Type> seen = List.nil();

                    Boolean visit(List<Type> ts) {
                        for (Type t : ts) {
                            if (visit(t)) return true;
                        }
                        return false;
                    }

                    public Boolean visitType(Type t, Void ignored) {
                        return false;
                    }

                    @Override
                    public Boolean visitClassType(ClassType t, Void ignored) {
                        if (t.isCompound()) {
                            return visit(types.supertype(t)) ||
                                    visit(types.interfaces(t));
                        } else {
                            return visit(t.getTypeArguments());
                        }
                    }
                    @Override
                    public Boolean visitWildcardType(WildcardType t, Void ignored) {
                        return visit(t.type);
                    }

                    @Override
                    public Boolean visitUndetVar(UndetVar t, Void ignored) {
                        if (seen.contains(t)) {
                            return true;
                        } else {
                            seen = seen.prepend(t);
                            return visit(t.inst);
                        }
                    }
                };
                return constraintScanner.visit(uv);
            }
        };

/***************************************************************************
 * Mini/Maximization of UndetVars
 ***************************************************************************/

    /** Instantiate undetermined type variable to its minimal upper bound.
     *  Throw a NoInstanceException if this not possible.
     *  将未确定类型变量实例化为其最小上限。如果这不可能，则抛出 NoInstanceException
     */
    void maximizeInst(UndetVar that, Warner warn) throws NoInstanceException {
        List<Type> hibounds = Type.filter(that.hibounds, errorFilter);
        // that.inst为空时表示还没有推断出具体的类型
        if (that.inst == null) {
            if (hibounds.isEmpty())
                // 列表为空，推断出来的类型就是Object
                that.inst = syms.objectType;
            else if (hibounds.tail.isEmpty())
                // 列表中只有一个元素，则这个元素就是推断出来的类型
                that.inst = hibounds.head;
            else
                // 有两个或更多元素，调用glb()方法求列表中所有类型的最大下界
                that.inst = types.glb(hibounds);
        }
        if (that.inst == null ||
            that.inst.isErroneous())
            throw ambiguousNoInstanceException
                .setMessage("no.unique.maximal.instance.exists",
                            that.qtype, hibounds);
    }
    //where
        private boolean isSubClass(Type t, final List<Type> ts) {
            t = t.baseType();
            if (t.tag == TYPEVAR) {
                List<Type> bounds = types.getBounds((TypeVar)t);
                for (Type s : ts) {
                    if (!types.isSameType(t, s.baseType())) {
                        for (Type bound : bounds) {
                            if (!isSubClass(bound, List.of(s.baseType())))
                                return false;
                        }
                    }
                }
            } else {
                for (Type s : ts) {
                    if (!t.tsym.isSubClass(s.baseType().tsym, types))
                        return false;
                }
            }
            return true;
        }

    private Filter<Type> errorFilter = new Filter<Type>() {
        @Override
        public boolean accepts(Type t) {
            return !t.isErroneous();
        }
    };

    /** Instantiate undetermined type variable to the lob of all its lower bounds.
     *  Throw a NoInstanceException if this not possible.
     */
    // minimizeInst()方法推断具体的类型
    // 将未确定类型变量实例化为其所有下限的 lub。如果这不可能，则抛出 NoInstanceException。
    void minimizeInst(UndetVar that, Warner warn) throws NoInstanceException {
        // Type.filter()方法过滤that.lobounds列表中所有的错误类型，得到lobounds列表，
        List<Type> lobounds = Type.filter(that.lobounds, errorFilter);
        // Type.filter()方法根据lobounds列表中的值推断出具体类型并保存到inst变量中
        if (that.inst == null) {
            if (lobounds.isEmpty())
                // 当lobounds列表为空时推断出的具体类型为Object
                that.inst = syms.botType;
            else if (lobounds.tail.isEmpty())
                // 当lobounds列表中有一个值并且为引用类型时，将这个类型当作推断出的具体类型
                that.inst = lobounds.head.isPrimitive() ? syms.errType : lobounds.head;
            else {
                // 当lobounds列表中含有至少两个值时，调用types.lub()方法计算类型的最小上界，计算出的最小上界就是推断出的具体类型
                // 例13-8
                that.inst = types.lub(lobounds);
            }
            if (that.inst == null || that.inst.tag == ERROR)
                    throw ambiguousNoInstanceException
                        .setMessage("no.unique.minimal.instance.exists",
                                    that.qtype, lobounds);
            // VGJ: sort of inlined maximizeInst() below.  Adding
            // bounds can cause lobounds that are above hibounds.
            List<Type> hibounds = Type.filter(that.hibounds, errorFilter);
            if (hibounds.isEmpty())
                return;
            Type hb = null;
            if (hibounds.tail.isEmpty())
                hb = hibounds.head;
            else for (List<Type> bs = hibounds;
                      bs.nonEmpty() && hb == null;
                      bs = bs.tail) {
                if (isSubClass(bs.head, hibounds))
                    hb = types.fromUnknownFun.apply(bs.head);
            }
            if (hb == null ||
                !types.isSubtypeUnchecked(hb, hibounds, warn) ||
                !types.isSubtypeUnchecked(that.inst, hb, warn))
                throw ambiguousNoInstanceException;
        }
    }

/***************************************************************************
 * Exported Methods
 ***************************************************************************/

    /** Try to instantiate expression type `that' to given type `to'.
     *  If a maximal instantiation exists which makes this type
     *  a subtype of type `to', return the instantiated type.
     *  If no instantiation exists, or if several incomparable
     *  best instantiations exist throw a NoInstanceException.
     *  尝试将表达式类型“that”实例化为给定类型“to”。
     *  如果存在使该类型成为“to”类型的子类型的最大实例化，则返回实例化类型。
     *  如果不存在实例化，或者存在多个无与伦比的最佳实例化，则抛出 NoInstanceException。
     */
    public Type instantiateExpr(ForAll that,// that就是之前讲到的UninferredReturnType对象
                                Type to, // 而to为目标转换类型，假设这个类型为T
                                Warner warn) throws InferenceException {
        // 调用Type.map()方法将that.tvars列表中的元素重新封装为UndetVar对象，
        // 需要注意的是that.tvars就是instantiateMethod()方法中restvars列表，
        // 代表剩下的待推断的类型变量列表。
        List<Type> undetvars = Type.map(that.tvars, fromTypeVarFun);
        for (List<Type> l = undetvars; l.nonEmpty(); l = l.tail) {
            UndetVar uv = (UndetVar) l.head;
            TypeVar tv = (TypeVar)uv.qtype;
            ListBuffer<Type> hibounds = new ListBuffer<Type>();
            // that.getConstraints()方法会间接调用UninferredMethodType匿名类对象的getConstraints()方法
            for (Type t : that.getConstraints(tv, ConstraintKind.EXTENDS)) {
                hibounds.append(types.subst(t, that.tvars, undetvars));
            }

            List<Type> inst = that.getConstraints(tv, ConstraintKind.EQUAL);
            if (inst.nonEmpty() && inst.head.tag != BOT) {
                uv.inst = inst.head;
            }
            uv.hibounds = hibounds.toList();
        }
        // types.subst()方法将被代理的方法含有的that.tvars类型变量全部替换为对应undetvars列表中的类型后得到qtype
        Type qtype1 = types.subst(that.qtype, that.tvars, undetvars);
        // 调用types.isSubtype()方法判断qtype1是否为types.boxedTypeOrType(to)或to的子类，如果不是将报编译报错
        if (!types.isSubtype(qtype1,
                // 调用的boxedTypeOrType()方法对to进行类型装箱转换，如果不为基本类型则直接返回类型本身
                qtype1.tag == UNDETVAR ? types.boxedTypeOrType(to) : to)) {
            throw unambiguousNoInstanceException
                .setMessage("infer.no.conforming.instance.exists",
                            that.tvars, that.qtype, to);
        }

        // 将类型推断信息保存到待推断类型变量对应的UndetVar对象的hibounds与inst中之后，
        // 在instantiateExpr()方法中调用maximizeInst()方法进行类型推断
        for (List<Type> l = undetvars; l.nonEmpty(); l = l.tail)
            maximizeInst((UndetVar) l.head, warn);

        // check bounds
        // 推断出具体类型后就可以进行类型验证
        // Type.map()方法处理undetvars，一般情况下都是获取每个UndetVar对象的inst值，所以targs列表中保存的是具体推断出的类型
        List<Type> targs = Type.map(undetvars, getInstFun);
        if (Type.containsAny(targs, that.tvars)) {
            //replace uninferred type-vars
            targs = types.subst(targs,
                    that.tvars,
                    instaniateAsUninferredVars(undetvars, that.tvars));
        }
        // 调用UninferredReturnType类的inst()方法获取方法的返回类型，这样就可以调用checkType()方法判断方法的返回类型是否可以转换为目标类型了
        // 由于已经推断出了具体类型，所以对UninferredMethodType与UninferredReturnType这两个代理对象的实际代理类型qtype进行更新
        return chk.checkType(warn.pos(), that.inst(targs, types), to);
    }
    //where
    private List<Type> instaniateAsUninferredVars(List<Type> undetvars, List<Type> tvars) {
        ListBuffer<Type> new_targs = ListBuffer.lb();
        //step 1 - create syntethic captured vars
        for (Type t : undetvars) {
            UndetVar uv = (UndetVar)t;
            Type newArg = new CapturedType(t.tsym.name, t.tsym, uv.inst, syms.botType, null);
            new_targs = new_targs.append(newArg);
        }
        //step 2 - replace synthetic vars in their bounds
        for (Type t : new_targs.toList()) {
            CapturedType ct = (CapturedType)t;
            ct.bound = types.subst(ct.bound, tvars, new_targs.toList());
            WildcardType wt = new WildcardType(ct.bound, BoundKind.EXTENDS, syms.boundClass);
            ct.wildcard = wt;
        }
        return new_targs.toList();
    }

    /** Instantiate method type `mt' by finding instantiations of
     *  `tvars' so that method can be applied to `argtypes'.
     */
    // 通过查找“tvars”的实例来实例化方法类型“mt”，以便可以将方法应用于“argtypes”。
    public Type instantiateMethod(final Env<AttrContext> env,
                                  List<Type> tvars,
                                  MethodType mt,
                                  final Symbol msym,
                                  final List<Type> argtypes,
                                  final boolean allowBoxing,
                                  final boolean useVarargs,
                                  final Warner warn) throws InferenceException {
        // 调用types.map()方法为每个需要推断的类型变量建立对应的UndetVar对象
        // UndetVar对象的qtype保存了需要推断的类型变量
        List<Type> undetvars = Type.map(tvars, fromTypeVarFun);
        List<Type> formals = mt.argtypes;
        final List<Type> capturedArgs = types.capture(argtypes);
        List<Type> actuals = capturedArgs;
        List<Type> actualsNoCapture = argtypes;
        Type varargsFormal = useVarargs ? formals.last() : null;
        if (varargsFormal == null &&
                actuals.size() != formals.size()) {
            throw unambiguousNoInstanceException
                .setMessage("infer.arg.length.mismatch");
        }
        // 通过第1阶段与第2阶段查找方法
        while (actuals.nonEmpty() && formals.head != varargsFormal) {
            Type formal = formals.head;
            Type actual = actuals.head.baseType();
            Type actualNoCapture = actualsNoCapture.head.baseType();
            if (actual.tag == FORALL)
                actual = instantiateArg((ForAll)actual, formal, tvars, warn);
            Type undetFormal = types.subst(formal, tvars, undetvars);
            // 调用types.isConvertible()或isSubtypeUnchecked()方法检查实际参数的类型是否与形式参数的类型兼容，其实就是通过第一阶段、第二阶段或第三阶段查找匹配的方法，
            // 无论调用哪一个方法，最终都会调用types.isSubtype()方法。
            boolean works = allowBoxing
                ? types.isConvertible(actual, undetFormal, warn)
                : types.isSubtypeUnchecked(actual, undetFormal, warn);
            if (!works) {
                throw unambiguousNoInstanceException
                    .setMessage("infer.no.conforming.assignment.exists",
                                tvars, actualNoCapture, formal);
            }
            formals = formals.tail;
            actuals = actuals.tail;
            actualsNoCapture = actualsNoCapture.tail;
        }

        if (formals.head != varargsFormal) // not enough args
            throw unambiguousNoInstanceException.setMessage("infer.arg.length.mismatch");

        // 通过第3阶段查找方法
        if (useVarargs) {
            Type elemType = types.elemtype(varargsFormal);
            Type elemUndet = types.subst(elemType, tvars, undetvars);
            while (actuals.nonEmpty()) {
                Type actual = actuals.head.baseType();
                Type actualNoCapture = actualsNoCapture.head.baseType();
                if (actual.tag == FORALL)
                    actual = instantiateArg((ForAll)actual, elemType, tvars, warn);
                boolean works = types.isConvertible(actual, elemUndet, warn);
                if (!works) {
                    throw unambiguousNoInstanceException
                        .setMessage("infer.no.conforming.assignment.exists",
                                    tvars, actualNoCapture, elemType);
                }
                actuals = actuals.tail;
                actualsNoCapture = actualsNoCapture.tail;
            }
        }

        // minimizeInst()方法推断具体的类型
        // 当完成对UndetVar对象的lobounds列表的填充后，
        // 就可以根据lobounds列表中的值推断UndetVar对象中qtype的具体类型了
        for (Type t : undetvars)
            minimizeInst((UndetVar) t, warn);

        /** Type variables instantiated to bottom */
        ListBuffer<Type> restvars = new ListBuffer<Type>();

        /** Undet vars instantiated to bottom */
        final ListBuffer<Type> restundet = new ListBuffer<Type>();

        /** Instantiated types or TypeVars if under-constrained */
        ListBuffer<Type> insttypes = new ListBuffer<Type>();

        /** Instantiated types or UndetVars if under-constrained */
        ListBuffer<Type> undettypes = new ListBuffer<Type>();

        for (Type t : undetvars) {
            UndetVar uv = (UndetVar)t;
            if (uv.inst.tag == BOT) {
                // 当uv.inst.tag值为BOT时，表示UndetVar对象uv还没有推断出具体的类型
                // 向4个集合中添加uv或者uv.qtype
                restvars.append(uv.qtype);
                restundet.append(uv);
                insttypes.append(uv.qtype);
                undettypes.append(uv);
                // uv.inst置为空
                uv.inst = null;
                // 这样后续还会继续结合上下文进行类型推断
            } else {
                // 向insttypes与undettypes中添加推断出来的具体类型uv.inst
                insttypes.append(uv.inst);
                undettypes.append(uv.inst);
            }
        }
        // 检查实际类型参数和推断出来的类型参数是否符合要求
        checkWithinBounds(tvars, undettypes.toList(), warn);

        // types.subst()方法将mt中含有推断出具体类型的类型变量替换为具体类型
        mt = (MethodType)types.subst(mt, tvars, insttypes.toList());
        // 当restvars列表不为空时，表示还有待推断的类型，方法返回UninferredMethodType对象
        if (!restvars.isEmpty()) {
            final List<Type> inferredTypes = insttypes.toList();
            final List<Type> all_tvars = tvars; //this is the wrong tvars
            // 如果restvars列表中还有元素，则表示还有待推断的类型变量，JDK 1.7版本的Javac还会结合赋值表达式左侧的信息进行类型推断
            // 调用instantiateMethod()方法后返回的mt可能是MethodType类型，也可能是UninferredReturnType类型
            return new UninferredMethodType(mt, restvars.toList()) {
                @Override
                List<Type> getConstraints(TypeVar tv, ConstraintKind ck) {
                    for (Type t : restundet.toList()) {
                        UndetVar uv = (UndetVar)t;
                        if (uv.qtype == tv) {
                            switch (ck) {
                                case EXTENDS: return uv.hibounds.appendList(types.subst(types.getBounds(tv), all_tvars, inferredTypes));
                                case SUPER: return uv.lobounds;
                                case EQUAL: return uv.inst != null ? List.of(uv.inst) : List.<Type>nil();
                            }
                        }
                    }
                    return List.nil();
                }
                @Override
                void check(List<Type> inferred, Types types) throws NoInstanceException {
                    // 检查实际的参数类型是否与推断出的形式参数类型兼容
                    checkArgumentsAcceptable(env, capturedArgs, getParameterTypes(), allowBoxing, useVarargs, warn);
                    // 检查推断出的类型是否在声明的类型变量的上界范围之内
                    checkWithinBounds(all_tvars,
                           types.subst(inferredTypes, tvars, inferred), warn);
                    if (useVarargs) {
                        chk.checkVararg(env.tree.pos(), getParameterTypes(), msym);
                    }
            }};
        }
        else {
            // 如果已经没有待推断的类型变量，则restvars列表为空，调用isEmpty()方法将返回true
            // check that actuals conform to inferred formals
            // 检查实际参数的类型是否与形式参数的类型兼容
            checkArgumentsAcceptable(env, capturedArgs, mt.getParameterTypes(), allowBoxing, useVarargs, warn);
            // return instantiated version of method type
            // 返回方法类型的实例化版本
            return mt;
        }
    }
    //where

        /**
         * A delegated type representing a partially uninferred method type.
         * The return type of a partially uninferred method type is a ForAll
         * type - when the return type is instantiated (see Infer.instantiateExpr)
         * the underlying method type is also updated.
         * 表示部分未推断的方法类型的委托类型。部分未推断的方法类型的返回类型是 ForAll 类型 -
         * 当返回类型被实例化时（请参阅 Infer.instantiateExpr），基础方法类型也会更新
         */
        // UninferredMethodType对象表示含有待推断类型变量的方法，
        static abstract class UninferredMethodType extends DelegatedType {

            // tvars保存了待推断的类型变量
            final List<Type> tvars;

            public UninferredMethodType(MethodType mtype, List<Type> tvars) {
                // tagMETHOD, 待推断的对象是methodType
                super(METHOD, new MethodType(mtype.argtypes, null, mtype.thrown, mtype.tsym));
                this.tvars = tvars;
                // 调用asMethodType()方法一般会获取到这个新的MethodType对象，然后restype被更新为一个新创建的UninferredReturnType对象
                asMethodType().restype = new UninferredReturnType(tvars, mtype.restype);
            }

            @Override
            public MethodType asMethodType() {
                return qtype.asMethodType();
            }

            @Override
            public Type map(Mapping f) {
                return qtype.map(f);
            }

            void instantiateReturnType(Type restype, List<Type> inferred, Types types) throws NoInstanceException {
                // 创建一个新的MethodType对象并赋值给qtype，新的MethodType对象的形式参数类型、返回类型及异常抛出类型都进行了类型替换
                qtype = new MethodType(types.subst(getParameterTypes(), tvars, inferred),
                                       restype,
                                       types.subst(UninferredMethodType.this.getThrownTypes(), tvars, inferred),
                                       UninferredMethodType.this.qtype.tsym);
                // 最后调用check()方法检查推断出来的类型是否满足要求
                check(inferred, types);
            }

            abstract void check(List<Type> inferred, Types types) throws NoInstanceException;

            abstract List<Type> getConstraints(TypeVar tv, ConstraintKind ck);

            class UninferredReturnType extends ForAll {
                public UninferredReturnType(List<Type> tvars, Type restype) {
                    // 初始化ForAll类中定义的tvars变量与DelegatedType类中定义的qtype变量
                    // tvars中保存了待推断的类型变量，而qtype保存了mtype.restype，也就是方法的返回类型
                    super(tvars, restype);
                }
                @Override
                public Type inst(List<Type> actuals, Types types) {
                    // 调用父类的inst()方法更新UninferredReturnType对象
                    // UninferredReturnType类的直接父类为ForAll
                    Type newRestype = super.inst(actuals, types);
                    // 接着在inst()方法中调用instantiateReturnType()方法更新UninferredMethodType对象
                    instantiateReturnType(newRestype, actuals, types);
                    return newRestype;
                }
                @Override
                public List<Type> getConstraints(TypeVar tv, ConstraintKind ck) {
                    return UninferredMethodType.this.getConstraints(tv, ck);
                }
            }
        }

        private void checkArgumentsAcceptable(Env<AttrContext> env, List<Type> actuals, List<Type> formals,
                boolean allowBoxing, boolean useVarargs, Warner warn) {
            try {
                rs.checkRawArgumentsAcceptable(env, actuals, formals,
                       allowBoxing, useVarargs, warn);
            }
            catch (Resolve.InapplicableMethodException ex) {
                // inferred method is not applicable
                throw invalidInstanceException.setMessage(ex.getDiagnostic());
            }
        }

    /** Try to instantiate argument type `that' to given type `to'.
     *  If this fails, try to insantiate `that' to `to' where
     *  every occurrence of a type variable in `tvars' is replaced
     *  by an unknown type.
     */
    private Type instantiateArg(ForAll that,
                                Type to,
                                List<Type> tvars,
                                Warner warn) throws InferenceException {
        List<Type> targs;
        try {
            return instantiateExpr(that, to, warn);
        } catch (NoInstanceException ex) {
            Type to1 = to;
            for (List<Type> l = tvars; l.nonEmpty(); l = l.tail)
                to1 = types.subst(to1, List.of(l.head), List.of(syms.unknownType));
            return instantiateExpr(that, to1, warn);
        }
    }

    /** check that type parameters are within their bounds.
     */
    // 检查类型参数是否在其范围内
    // 如果没有推断出具体类型，checkWithinBounds()方法不进行边界检查，
    // 对于推断出来的类型，checkWithinBounds()方法会检查类型是否在类型变量声明的上界内
    void checkWithinBounds(List<Type> tvars,
                                   List<Type> arguments,
                                   Warner warn)
        throws InvalidInstanceException {
        // tvars参数列表保存着所有待推断的类型变量
        // arguments参数列表中还可能含有没有推断出具体类型的UndetVar对象
        for (List<Type> tvs = tvars, args = arguments;
             tvs.nonEmpty();
             tvs = tvs.tail, args = args.tail) {
            if (args.head instanceof UndetVar ||
                    tvars.head.getUpperBound().isErroneous())
                continue;
            // types.getBounds()方法获取类型变量的上界
            // 由于上界的类型也可能含有类型变量甚至就是类型变量，而这些类型变量可能已经推断出具体的类型，所以也需要调用types.subst()方法将上界中含有已经推断出具体类型的类型变量替换为具体的类型
            List<Type> bounds = types.subst(types.getBounds((TypeVar)tvs.head), tvars, arguments);
            // 最后调用types.isSubtypeUnchecked()方法判断实际推断出的具体类型是否在上界内，如果不在上界内，checkWithinBounds()方法将抛出InvalidInstanceException异常，从而终止编译流程
            if (!types.isSubtypeUnchecked(args.head, bounds, warn))
                throw invalidInstanceException
                    .setMessage("inferred.do.not.conform.to.bounds",
                                args.head, bounds);
        }
    }

    /**
     * Compute a synthetic method type corresponding to the requested polymorphic
     * method signature. The target return type is computed from the immediately
     * enclosing scope surrounding the polymorphic-signature call.
     */
    Type instantiatePolymorphicSignatureInstance(Env<AttrContext> env, Type site,
                                            Name name,
                                            MethodSymbol spMethod,  // sig. poly. method or null if none
                                            List<Type> argtypes) {
        final Type restype;

        //The return type for a polymorphic signature call is computed from
        //the enclosing tree E, as follows: if E is a cast, then use the
        //target type of the cast expression as a return type; if E is an
        //expression statement, the return type is 'void' - otherwise the
        //return type is simply 'Object'. A correctness check ensures that
        //env.next refers to the lexically enclosing environment in which
        //the polymorphic signature call environment is nested.

        switch (env.next.tree.getTag()) {
            case JCTree.TYPECAST:
                JCTypeCast castTree = (JCTypeCast)env.next.tree;
                restype = (TreeInfo.skipParens(castTree.expr) == env.tree) ?
                    castTree.clazz.type :
                    syms.objectType;
                break;
            case JCTree.EXEC:
                JCTree.JCExpressionStatement execTree =
                        (JCTree.JCExpressionStatement)env.next.tree;
                restype = (TreeInfo.skipParens(execTree.expr) == env.tree) ?
                    syms.voidType :
                    syms.objectType;
                break;
            default:
                restype = syms.objectType;
        }

        List<Type> paramtypes = Type.map(argtypes, implicitArgType);
        List<Type> exType = spMethod != null ?
            spMethod.getThrownTypes() :
            List.of(syms.throwableType); // make it throw all exceptions

        MethodType mtype = new MethodType(paramtypes,
                                          restype,
                                          exType,
                                          syms.methodClass);
        return mtype;
    }
    //where
        Mapping implicitArgType = new Mapping ("implicitArgType") {
                public Type apply(Type t) {
                    t = types.erasure(t);
                    if (t.tag == BOT)
                        // nulls type as the marker type Null (which has no instances)
                        // infer as java.lang.Void for now
                        t = types.boxedClass(syms.voidType).type;
                    return t;
                }
        };
    }
