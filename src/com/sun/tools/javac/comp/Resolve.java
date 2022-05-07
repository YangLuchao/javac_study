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

import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;
import com.sun.tools.javac.code.*;
import com.sun.tools.javac.jvm.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.api.Formattable.LocalizedString;
import static com.sun.tools.javac.comp.Resolve.MethodResolutionPhase.*;

import com.sun.tools.javac.code.Type.*;
import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.tree.JCTree.*;

import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.*;
import static com.sun.tools.javac.code.TypeTags.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticFlag;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticType;
import javax.lang.model.element.ElementVisitor;

import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;

/** Helper class for name resolution, used mostly by the attribution phase.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
// Resolve类实现了对类型、变量与方法的引用消解，引用消解就是找到正确的指向
// 例11-1
public class Resolve {
    protected static final Context.Key<Resolve> resolveKey =
        new Context.Key<Resolve>();

    Names names;
    Log log;
    Symtab syms;
    Check chk;
    Infer infer;
    ClassReader reader;
    TreeInfo treeinfo;
    Types types;
    JCDiagnostic.Factory diags;
    public final boolean boxingEnabled; // = source.allowBoxing();
    public final boolean varargsEnabled; // = source.allowVarargs();
    public final boolean allowMethodHandles;
    private final boolean debugResolve;

    Scope polymorphicSignatureScope;

    public static Resolve instance(Context context) {
        Resolve instance = context.get(resolveKey);
        if (instance == null)
            instance = new Resolve(context);
        return instance;
    }

    protected Resolve(Context context) {
        context.put(resolveKey, this);
        syms = Symtab.instance(context);

        varNotFound = new
            SymbolNotFoundError(ABSENT_VAR);
        wrongMethod = new
            InapplicableSymbolError(syms.errSymbol);
        wrongMethods = new
            InapplicableSymbolsError(syms.errSymbol);
        methodNotFound = new
                // ABSENT_MTH是Kinds类中定义的常量，值为ERRONEOUS+7，
                // 当sym小于ERRONEOUS时不会再继续调用findFun()方法查找了，
                // 因为已经找到了适合的sym
            SymbolNotFoundError(ABSENT_MTH);
        typeNotFound = new
            SymbolNotFoundError(ABSENT_TYP);

        names = Names.instance(context);
        log = Log.instance(context);
        chk = Check.instance(context);
        infer = Infer.instance(context);
        reader = ClassReader.instance(context);
        treeinfo = TreeInfo.instance(context);
        types = Types.instance(context);
        diags = JCDiagnostic.Factory.instance(context);
        Source source = Source.instance(context);
        boxingEnabled = source.allowBoxing();
        varargsEnabled = source.allowVarargs();
        Options options = Options.instance(context);
        debugResolve = options.isSet("debugresolve");
        Target target = Target.instance(context);
        allowMethodHandles = target.hasMethodHandles();
        polymorphicSignatureScope = new Scope(syms.noSymbol);

        inapplicableMethodException = new InapplicableMethodException(diags);
    }

    /** error symbols, which are returned when resolution fails
     */
    // 变量没有找到错误
    final SymbolNotFoundError varNotFound;
    // 方法不适用错误
    final InapplicableSymbolError wrongMethod;
    // 有多个适用的方法错误
    final InapplicableSymbolsError wrongMethods;
    // 方法没有找到错误
    final SymbolNotFoundError methodNotFound;
    // 类型没有找到错误
    final SymbolNotFoundError typeNotFound;

/* ************************************************************************
 * Identifier resolution
 *************************************************************************/

    /** An environment is "static" if its static level is greater than
     *  the one of its outer environment
     */
    // 法判断是否为静态环境
    static boolean isStatic(Env<AttrContext> env) {
        return env.info.staticLevel > env.outer.info.staticLevel;
    }

    /** An environment is an "initializer" if it is a constructor or
     *  an instance initializer.
     */
    static boolean isInitializer(Env<AttrContext> env) {
        Symbol owner = env.info.scope.owner;
        return owner.isConstructor() ||
            owner.owner.kind == TYP &&
            (owner.kind == VAR ||
             owner.kind == MTH && (owner.flags() & BLOCK) != 0) &&
            (owner.flags() & STATIC) == 0;
    }

    /** Is class accessible in given evironment?
     *  @param env    The current environment.
     *  @param c      The class whose accessibility is checked.
     */
    public boolean isAccessible(Env<AttrContext> env, TypeSymbol c) {
        return isAccessible(env, c, false);
    }

    public boolean isAccessible(Env<AttrContext> env, TypeSymbol c, boolean checkInner) {
        boolean isAccessible = false;
        switch ((short)(c.flags() & AccessFlags)) {
            case PRIVATE:
                isAccessible =
                    env.enclClass.sym.outermostClass() ==
                    c.owner.outermostClass();
                break;
            case 0:
                isAccessible =
                    env.toplevel.packge == c.owner // fast special case
                    ||
                    env.toplevel.packge == c.packge()
                    ||
                    // Hack: this case is added since synthesized default constructors
                    // of anonymous classes should be allowed to access
                    // classes which would be inaccessible otherwise.
                    env.enclMethod != null &&
                    (env.enclMethod.mods.flags & ANONCONSTR) != 0;
                break;
            default: // error recovery
            case PUBLIC:
                isAccessible = true;
                break;
            case PROTECTED:
                isAccessible =
                    env.toplevel.packge == c.owner // fast special case
                    ||
                    env.toplevel.packge == c.packge()
                    ||
                    isInnerSubClass(env.enclClass.sym, c.owner);
                break;
        }
        return (checkInner == false || c.type.getEnclosingType() == Type.noType) ?
            isAccessible :
            isAccessible && isAccessible(env, c.type.getEnclosingType(), checkInner);
    }
    //where
        /** Is given class a subclass of given base class, or an inner class
         *  of a subclass?
         *  Return null if no such class exists.
         *  @param c     The class which is the subclass or is contained in it.
         *  @param base  The base class
         */
        private boolean isInnerSubClass(ClassSymbol c, Symbol base) {
            while (c != null && !c.isSubClass(base, types)) {
                c = c.owner.enclClass();
            }
            return c != null;
        }

    boolean isAccessible(Env<AttrContext> env, Type t) {
        return isAccessible(env, t, false);
    }

    boolean isAccessible(Env<AttrContext> env, Type t, boolean checkInner) {
        return (t.tag == ARRAY)
            ? isAccessible(env, types.elemtype(t))
            : isAccessible(env, t.tsym, checkInner);
    }

    /** Is symbol accessible as a member of given type in given evironment?
     *  @param env    The current environment.
     *  @param site   The type of which the tested symbol is regarded
     *                as a member.
     *  @param sym    The symbol.
     */
    // 检查访问权限
    // 在给定环境中，符号是否可以作为给定类型的成员访问
    // 在env下site能否访问到sym
    public boolean isAccessible(Env<AttrContext> env, Type site, Symbol sym) {
        return isAccessible(env, site, sym, false);
    }
    public boolean isAccessible(Env<AttrContext> env, Type site, Symbol sym, boolean checkInner) {
        if (sym.name == names.init && sym.owner != site.tsym) return false;
        switch ((short)(sym.flags() & AccessFlags)) {
        case PRIVATE:
            return
                (env.enclClass.sym == sym.owner // fast special case
                 ||
                 env.enclClass.sym.outermostClass() ==
                 sym.owner.outermostClass())
                &&
                sym.isInheritedIn(site.tsym, types);
        case 0:
            return
                (env.toplevel.packge == sym.owner.owner // fast special case
                 ||
                 env.toplevel.packge == sym.packge())
                &&
                isAccessible(env, site, checkInner)
                &&
                sym.isInheritedIn(site.tsym, types)
                &&
                notOverriddenIn(site, sym);
        case PROTECTED:
            return
                (env.toplevel.packge == sym.owner.owner // fast special case
                 ||
                 env.toplevel.packge == sym.packge()
                 ||
                 isProtectedAccessible(sym, env.enclClass.sym, site)
                 ||
                 // OK to select instance method or field from 'super' or type name
                 // (but type names should be disallowed elsewhere!)
                 env.info.selectSuper && (sym.flags() & STATIC) == 0 && sym.kind != TYP)
                &&
                isAccessible(env, site, checkInner)
                &&
                notOverriddenIn(site, sym);
        default: // this case includes erroneous combinations as well
            return isAccessible(env, site, checkInner) && notOverriddenIn(site, sym);
        }
    }
    //where
    /* `sym' is accessible only if not overridden by
     * another symbol which is a member of `site'
     * (because, if it is overridden, `sym' is not strictly
     * speaking a member of `site'). A polymorphic signature method
     * cannot be overridden (e.g. MH.invokeExact(Object[])).
     */
    private boolean notOverriddenIn(Type site, Symbol sym) {
        if (sym.kind != MTH || sym.isConstructor() || sym.isStatic())
            return true;
        else {
            Symbol s2 = ((MethodSymbol)sym).implementation(site.tsym, types, true);
            return (s2 == null || s2 == sym || sym.owner == s2.owner ||
                    s2.isPolymorphicSignatureGeneric() ||
                    !types.isSubSignature(types.memberType(site, s2), types.memberType(site, sym)));
        }
    }
    //where
        /** Is given protected symbol accessible if it is selected from given site
         *  and the selection takes place in given class?
         *  @param sym     The symbol with protected access
         *  @param c       The class where the access takes place
         *  @site          The type of the qualifier
         */
        private
        boolean isProtectedAccessible(Symbol sym, ClassSymbol c, Type site) {
            while (c != null &&
                   !(c.isSubClass(sym.owner, types) &&
                     (c.flags() & INTERFACE) == 0 &&
                     // In JLS 2e 6.6.2.1, the subclass restriction applies
                     // only to instance fields and methods -- types are excluded
                     // regardless of whether they are declared 'static' or not.
                     ((sym.flags() & STATIC) != 0 || sym.kind == TYP || site.tsym.isSubClass(c, types))))
                c = c.owner.enclClass();
            return c != null;
        }

    /** Try to instantiate the type of a method so that it fits
     *  given type arguments and argument types. If succesful, return
     *  the method's instantiated type, else return null.
     *  The instantiation will take into account an additional leading
     *  formal parameter if the method is an instance method seen as a member
     *  of un underdetermined site In this case, we treat site as an additional
     *  parameter and the parameters of the class containing the method as
     *  additional type variables that get instantiated.
     *  尝试实例化方法的类型，使其适合给定的类型参数和参数类型。
     *  如果成功，则返回方法的实例化类型，否则返回 null。
     *  如果方法是被视为未确定站点成员的实例方法，则实例化将考虑附加的前导形参。
     *  在这种情况下，我们将站点视为附加参数，将包含该方法的类的参数视为附加类型变量被实例化。
     *  @param env         The current environment
     *  @param site        The type of which the method is a member.
     *  @param m           The method symbol.
     *  @param argtypes    The invocation's given value arguments.
     *  @param typeargtypes    The invocation's given type arguments.
     *  @param allowBoxing Allow boxing conversions of arguments.
     *  @param useVarargs Box trailing arguments into an array for varargs.
     */
    Type rawInstantiate(Env<AttrContext> env,
                        Type site,
                        Symbol m,
                        List<Type> argtypes,
                        List<Type> typeargtypes,
                        boolean allowBoxing,
                        boolean useVarargs,
                        Warner warn)
        throws Infer.InferenceException {
        boolean polymorphicSignature = m.isPolymorphicSignatureGeneric() && allowMethodHandles;
        if (useVarargs && (m.flags() & VARARGS) == 0)
            throw inapplicableMethodException.setMessage();
        // 例11-17
        Type mt = types.memberType(site, m);

        // tvars is the list of formal type variables for which type arguments
        // need to inferred.
        List<Type> tvars = null;
        if (env.info.tvars != null) {
            tvars = types.newInstances(env.info.tvars);
            mt = types.subst(mt, env.info.tvars, tvars);
        }
        if (typeargtypes == null)
            typeargtypes = List.nil();
        if (mt.tag != FORALL && typeargtypes.nonEmpty()) {
            // This is not a polymorphic method, but typeargs are supplied
            // which is fine, see JLS 15.12.2.1
        } else if (mt.tag == FORALL && typeargtypes.nonEmpty()) {
            ForAll pmt = (ForAll) mt;
            if (typeargtypes.length() != pmt.tvars.length())
                throw inapplicableMethodException.setMessage("arg.length.mismatch"); // not enough args
            // Check type arguments are within bounds
            List<Type> formals = pmt.tvars;
            List<Type> actuals = typeargtypes;
            while (formals.nonEmpty() && actuals.nonEmpty()) {
                List<Type> bounds = types.subst(types.getBounds((TypeVar)formals.head),
                                                pmt.tvars, typeargtypes);
                for (; bounds.nonEmpty(); bounds = bounds.tail)
                    if (!types.isSubtypeUnchecked(actuals.head, bounds.head, warn))
                        throw inapplicableMethodException.setMessage("explicit.param.do.not.conform.to.bounds",actuals.head, bounds);
                formals = formals.tail;
                actuals = actuals.tail;
            }
            mt = types.subst(pmt.qtype, pmt.tvars, typeargtypes);
        } else if (mt.tag == FORALL) {
            ForAll pmt = (ForAll) mt;
            List<Type> tvars1 = types.newInstances(pmt.tvars);
            tvars = tvars.appendList(tvars1);
            mt = types.subst(pmt.qtype, pmt.tvars, tvars1);
        }

        // find out whether we need to go the slow route via infer
        boolean instNeeded = tvars.tail != null || /*inlined: tvars.nonEmpty()*/
                polymorphicSignature;
        for (List<Type> l = argtypes;
             l.tail != null/*inlined: l.nonEmpty()*/ && !instNeeded;
             l = l.tail) {
            if (l.head.tag == FORALL)
                instNeeded = true;
        }

        if (instNeeded)
            return polymorphicSignature ?
                infer.instantiatePolymorphicSignatureInstance(env, site, m.name, (MethodSymbol)m, argtypes) :
                infer.instantiateMethod(env,
                                    tvars,
                                    (MethodType)mt,
                                    m,
                                    argtypes,
                                    allowBoxing,
                                    useVarargs,
                                    warn);
        // 对非泛型方法进行处理
        // 对传递的实际参数的类型进行兼容性检查，如果有不匹配的类型出现，会抛出相关的异常信息
        // 检查argtypes中的实际参数类型是否能转换为mt方法的形式参数类型
        // 如果能转换，则返回m在site类型下的方法类型，否则抛出InapplicableMethodException类型的异常，表示m不是一个匹配的方法。instantiate()方法最终返回null
        checkRawArgumentsAcceptable(env, argtypes, mt.getParameterTypes(),
                                allowBoxing, useVarargs, warn);
        return mt;
    }

    /** Same but returns null instead throwing a NoInstanceException
     */
    Type instantiate(Env<AttrContext> env,
                     Type site,
                     Symbol m,
                     List<Type> argtypes,
                     List<Type> typeargtypes,
                     boolean allowBoxing,
                     boolean useVarargs,
                     Warner warn) {
        try {
            // 检查argtypes中的实际参数类型是否能转换为m方法的形式参数类型
            // 如果能转换，则返回m在site类型下的方法类型，
            // 否则抛出InapplicableMethodException类型的异常，表示m不是一个匹配的方法。
            // instantiate()方法最终返回null
            return rawInstantiate(env, site, m, argtypes, typeargtypes,
                                  allowBoxing, useVarargs, warn);
        } catch (InapplicableMethodException ex) {
            return null;
        }
    }

    /** Check if a parameter list accepts a list of args.
     */
    boolean argumentsAcceptable(Env<AttrContext> env,
                                List<Type> argtypes,
                                List<Type> formals,
                                boolean allowBoxing,
                                boolean useVarargs,
                                Warner warn) {
        try {
            checkRawArgumentsAcceptable(env, argtypes, formals, allowBoxing, useVarargs, warn);
            return true;
        } catch (InapplicableMethodException ex) {
            return false;
        }
    }

    // 对传递的实际参数的类型进行兼容性检查，如果有不匹配的类型出现，会抛出相关的异常信息
    void checkRawArgumentsAcceptable(Env<AttrContext> env,
                                List<Type> argtypes,
                                List<Type> formals,
                                boolean allowBoxing,
                                boolean useVarargs,
                                Warner warn) {
        Type varargsFormal = useVarargs ? formals.last() : null;
        if (varargsFormal == null &&
                argtypes.size() != formals.size()) {
            throw inapplicableMethodException.setMessage("arg.length.mismatch"); // not enough args
        }
        // 进行方法第1阶段与第2阶段的查找
        while (argtypes.nonEmpty() && formals.head != varargsFormal) {
            // 实际参数的类型是否可通过类型转换转为形式参数的类型
            boolean works = allowBoxing
                    // 当allowBoxing为true时，表示可能是BOX或者VARARITY查找阶段
                    // 调用types.isConvertible()方法判断argtypes.head类型是否可转为formals.head类型
                ? types.isConvertible(argtypes.head, formals.head, warn)
                    // 当allowBoxing为false时，表示不允许有类型拆箱转换与类型装箱转换，代表BASIC查找阶段
                    // 调用types.isSubtypeUnchecked()方法判断argtypes.head类型是否可转为formals.head类型
                : types.isSubtypeUnchecked(argtypes.head, formals.head, warn);
            if (!works)
                throw inapplicableMethodException.setMessage("no.conforming.assignment.exists",
                        argtypes.head,
                        formals.head);
            argtypes = argtypes.tail;
            formals = formals.tail;
        }

        if (formals.head != varargsFormal)
            throw inapplicableMethodException.setMessage("arg.length.mismatch"); // not enough args
        // 进行方法第3阶段的查找
        if (useVarargs) {
            Type elt = types.elemtype(varargsFormal);
            while (argtypes.nonEmpty()) {
                if (!types.isConvertible(argtypes.head, elt, warn))
                    throw inapplicableMethodException.setMessage("varargs.argument.mismatch",
                            argtypes.head,
                            elt);
                argtypes = argtypes.tail;
            }
            //check varargs element type accessibility
            if (!isAccessible(env, elt)) {
                Symbol location = env.enclClass.sym;
                throw inapplicableMethodException.setMessage("inaccessible.varargs.type",
                            elt,
                            Kinds.kindName(location),
                            location);
            }
        }
        return;
    }
    // where
        public static class InapplicableMethodException extends RuntimeException {
            private static final long serialVersionUID = 0;

            JCDiagnostic diagnostic;
            JCDiagnostic.Factory diags;

            InapplicableMethodException(JCDiagnostic.Factory diags) {
                this.diagnostic = null;
                this.diags = diags;
            }
            InapplicableMethodException setMessage() {
                this.diagnostic = null;
                return this;
            }
            InapplicableMethodException setMessage(String key) {
                this.diagnostic = key != null ? diags.fragment(key) : null;
                return this;
            }
            InapplicableMethodException setMessage(String key, Object... args) {
                this.diagnostic = key != null ? diags.fragment(key, args) : null;
                return this;
            }
            InapplicableMethodException setMessage(JCDiagnostic diag) {
                this.diagnostic = diag;
                return this;
            }

            public JCDiagnostic getDiagnostic() {
                return diagnostic;
            }
        }
        private final InapplicableMethodException inapplicableMethodException;

/* ***************************************************************************
 *  Symbol lookup
 *  the following naming conventions for arguments are used
 *
 *       env      is the environment where the symbol was mentioned
 *       site     is the type of which the symbol is a member
 *       name     is the symbol's name
 *                if no arguments are given
 *       argtypes are the value arguments, if we search for a method
 *
 *  If no symbol was found, a ResolveError detailing the problem is returned.
 ****************************************************************************/

    /** Find field. Synthetic fields are always skipped.
     *  @param env     The current environment.
     *  @param site    The original type from where the selection takes place.
     *  @param name    The name of the field.
     *  @param c       The class to search for the field. This is always
     *                 a superclass or implemented interface of site's class.
     */
    // 查找成员变量。合成字段总是被跳过
    Symbol findField(Env<AttrContext> env,
                     Type site,
                     Name name,
                     TypeSymbol c) {
        while (c.type.tag == TYPEVAR)
            c = c.type.getUpperBound().tsym;
        Symbol bestSoFar = varNotFound;
        Symbol sym;
        // 从c中查找成员变量
        // 从当前类c的members_field中查找成员变量
        Scope.Entry e = c.members().lookup(name);
        while (e.scope != null) {
            if (e.sym.kind == VAR && (e.sym.flags_field & SYNTHETIC) == 0) {
                //调用isAccessible()方法判断在env环境下是否能访问到该变量
                return isAccessible(env, site, e.sym)
                        // 如果能访问到就直接返回查找到的符号，否则返回AccessError对象
                    ? e.sym : new AccessError(env, site, e.sym);
            }
            e = e.next();
        }
        // 从c的父类中查找成员变量
        Type st = types.supertype(c.type);
        if (st != null && (st.tag == CLASS || st.tag == TYPEVAR)) {
            sym = findField(env, site, name, st.tsym);
            if (sym.kind < bestSoFar.kind)
                bestSoFar = sym;
        }
        // 从c的接口中查找成员变量
        for (List<Type> l = types.interfaces(c.type);
             bestSoFar.kind != AMBIGUOUS && l.nonEmpty();
             l = l.tail) {
            sym = findField(env, site, name, l.head.tsym);
            if (bestSoFar.kind < AMBIGUOUS && sym.kind < AMBIGUOUS &&
                sym.owner != bestSoFar.owner)
                // 如果找到合适的符号也没有直接返回，而是继续进行查找，
                // 这样就可以避免父类或接口中定义相同成员变量导致引用歧义，如果有歧义，返回AmbiguityErr对象
                bestSoFar = new AmbiguityError(bestSoFar, sym);
            else if (sym.kind < bestSoFar.kind)
                bestSoFar = sym;
        }
        return bestSoFar;
    }

    /** Resolve a field identifier, throw a fatal error if not found.
     *  @param pos       The position to use for error reporting.
     *  @param env       The environment current at the method invocation.
     *  @param site      The type of the qualifying expression, in which
     *                   identifier is searched.
     *  @param name      The identifier's name.
     */
    public VarSymbol resolveInternalField(DiagnosticPosition pos, Env<AttrContext> env,
                                          Type site, Name name) {
        Symbol sym = findField(env, site, name, site.tsym);
        if (sym.kind == VAR) return (VarSymbol)sym;
        else throw new FatalError(
                 diags.fragment("fatal.err.cant.locate.field",
                                name));
    }

    /** Find unqualified variable or field with given name.
     *  Synthetic fields always skipped.
     *  @param env     The current environment.
     *  @param name    The name of the variable or field.
     */
    // 查找变量
    Symbol findVar(Env<AttrContext> env, Name name) {
        Symbol bestSoFar = varNotFound;
        Symbol sym;
        Env<AttrContext> env1 = env;
        boolean staticOnly = false;
        // 从env1.info.scope 开始查找变量
        // 首先从上下文环境env开始查找
        // 通过将env1更新为env1.outer来跳出当前的类型，也就是跳出当前类型的查找逻辑
        while (env1.outer != null) {
            if (isStatic(env1))
                // 对静态环境引用非静态变量做了判断
                staticOnly = true;
            Scope.Entry e = env1.info.scope.lookup(name);
            while (e.scope != null &&
                   (e.sym.kind != VAR ||
                    (e.sym.flags_field & SYNTHETIC) != 0))
                e = e.next();
            // 没有找到局部变量，查找成员变量
            sym = (e.scope != null)
                ? e.sym
                : findField(
                    env1, env1.enclClass.sym.type, name, env1.enclClass.sym);
            if (sym.exists()) {
                if (staticOnly &&
                    sym.kind == VAR &&
                    sym.owner.kind == TYP &&
                    (sym.flags() & STATIC) == 0)
                    return new StaticError(sym);
                else
                    return sym;
            } else if (sym.kind < bestSoFar.kind) {
                bestSoFar = sym;
            }

            if ((env1.enclClass.sym.flags() & STATIC) != 0)
                staticOnly = true;
            env1 = env1.outer;
        }

        // 如果本地作用域中没有，就会调用findField()方法从类型的members_field中或者类型的父类中查找
        sym = findField(env, syms.predefClass.type, name, syms.predefClass);
        if (sym.exists())
            return sym;
        if (bestSoFar.exists())
            return bestSoFar;
        // 从 env.toplevel.namedImportScope中查找变量
        // 例11-14
        Scope.Entry e = env.toplevel.namedImportScope.lookup(name);
        for (; e.scope != null; e = e.next()) {
            sym = e.sym;
            Type origin = e.getOrigin().owner.type;
            if (sym.kind == VAR) {
                if (e.sym.owner.type != origin)
                    sym = sym.clone(e.getOrigin().owner);
                return isAccessible(env, origin, sym)
                    ? sym : new AccessError(env, origin, sym);
            }
        }

        // 从 env.toplevel.starImportScope中查找变量
        Symbol origin = null;
        e = env.toplevel.starImportScope.lookup(name);
        for (; e.scope != null; e = e.next()) {
            sym = e.sym;
            if (sym.kind != VAR)
                continue;
            // invariant: sym.kind == VAR
            if (bestSoFar.kind < AMBIGUOUS && sym.owner != bestSoFar.owner)
                return new AmbiguityError(bestSoFar, sym);
            else if (bestSoFar.kind >= VAR) {
                origin = e.getOrigin().owner;
                bestSoFar = isAccessible(env, origin.type, sym)
                    ? sym : new AccessError(env, origin.type, sym);
            }
        }
        if (bestSoFar.kind == VAR && bestSoFar.owner.type != origin.type)
            return bestSoFar.clone(origin);
        else
            return bestSoFar;
    }

    Warner noteWarner = new Warner();

    /** Select the best method for a call site among two choices.
     *  @param env              The current environment.
     *  @param site             The original type from where the
     *                          selection takes place.
     *  @param argtypes         The invocation's value arguments,
     *  @param typeargtypes     The invocation's type arguments,
     *  @param sym              Proposed new best match.
     *  @param bestSoFar        Previously found best match.
     *  @param allowBoxing Allow boxing conversions of arguments.
     *  @param useVarargs Box trailing arguments into an array for varargs.
     */
    // 从sym和bestSoFar间选择最匹配的方法
    @SuppressWarnings("fallthrough")
    Symbol selectBest(Env<AttrContext> env,
                      Type site,
                      List<Type> argtypes,
                      List<Type> typeargtypes,
                      Symbol sym,
                      Symbol bestSoFar,
                      boolean allowBoxing,
                      boolean useVarargs,
                      boolean operator) {
        if (sym.kind == ERR)
            return bestSoFar;
        if (!sym.isInheritedIn(site.tsym, types))
            return bestSoFar;
        // 检查sym方法是否合适，如果不合适，调用rawInstantiate()方法将抛出异常
        Assert.check(sym.kind < AMBIGUOUS);
        try {
            // 检查最新查找到的符号sym是否符合要求，
            // 也就是检查形式参数的类型与形式类型参数的类型是否符合要求
            // rawInstantiate()方法调用checkRawArgumentsAcceptable()方法对非泛型方法的形式参数类型进行检查，
            // 如果形式参数类型不匹配，会抛出InapplicableMethodException类型的异常
            rawInstantiate(env, site, sym, argtypes, typeargtypes,
                               allowBoxing, useVarargs, Warner.noWarnings);
        } catch (InapplicableMethodException ex) {
            switch (bestSoFar.kind) {
            case ABSENT_MTH:
                // 可能是首次调用selectBest()方法，bestSoFar被初始化为methodNotFound，
                // 最终调用wrongMethod.setWrongSym()方法返回InapplicableSymbolError对象，
                // 表示找到了一个根据方法名称查找到的不匹配的方法
                return wrongMethod.setWrongSym(sym, ex.getDiagnostic());
            case WRONG_MTH:
                // 表示在之前的查找过程中已经找到了一个不匹配的方法，这次查找的sym仍然是一个不匹配的方法，
                // 最终调用wrongMethods.addCandidate()方法返回InapplicableSymbolsError对象，
                // 表示找到了多个根据方法名称查找到的不匹配的方法
                wrongMethods.addCandidate(currentStep, wrongMethod.sym, wrongMethod.explanation);
            case WRONG_MTHS:
                // 表示在之前的查找过程中已经找到了多个不匹配的方法，这次查找的sym仍然是一个不匹配的方法，
                // 最终调用wrongMethods.addCandidate()方法返回InapplicableSymbolsError对象，
                // 表示找到了多个根据方法名称查找到的不匹配的方法。
                return wrongMethods.addCandidate(currentStep, sym, ex.getDiagnostic());
            default:
                return bestSoFar;
            }
        }
        // 执行完checkRawArgumentsAcceptable()方法时如果没有抛出InapplicableMethodException类型的异常，
        // 那么对于selectBest()方法来说，sym也是一个匹配的方法，
        // 可能会调用mostSpecific()方法查找sym与bestSoFar两个匹配方法中最精确的那个方法

        // 检查访问权限
        // 调用isAccessible()方法判断是否在env下能访问到sym
        if (!isAccessible(env, site, sym)) {
            // 访问不到
            // 如果bestSoFar.kind值为ABSENT_MTH时，表示sym是目前唯一匹配的方法，直接返回AccessError类型的错误，
            // 否则返回bestSoFar，这个bestSoFar可能是一个匹配的或不匹配的方法
            return (bestSoFar.kind == ABSENT_MTH)
                ? new AccessError(env, site, sym)
                : bestSoFar;
            }
        // 当只有一个合适的方法时直接返回，否则调用mostSpecific()方法选取一个最精确的方法
        return (bestSoFar.kind > AMBIGUOUS)
            ? sym
                // 参数allowBoxing的值为allowBoxing && operator，在查找普通方法及构造方法的MethodSymbol对象时，
                // operator的值为false，所以allowBoxing的值为false，
                // 也就是调用signatureMoreSpecific()方法比较两个方法的签名时不允许使用类型装箱转换与类型拆箱转换。
                // 当查找运算符对应的OperatorSymbol对象时，operator的值才可能为true
            : mostSpecific(sym, bestSoFar, env, site,
                           allowBoxing && operator, useVarargs);
    }

    /* Return the most specific of the two methods for a call,
     *  given that both are accessible and applicable.
     *  @param m1               A new candidate for most specific.
     *  @param m2               The previous most specific candidate.
     *  @param env              The current environment.
     *  @param site             The original type from where the selection
     *                          takes place.
     *  @param allowBoxing Allow boxing conversions of arguments.
     *  @param useVarargs Box trailing arguments into an array for varargs.
     */
    // 返回调用的两种方法中最具体的一种，前提是它们都是可访问且适用的
    Symbol mostSpecific(Symbol m1,
                        Symbol m2,
                        Env<AttrContext> env,
                        final Type site,
                        boolean allowBoxing,
                        boolean useVarargs) {
        switch (m2.kind) {
        case MTH: // 比较m1与m2签名的精确性
            // 两个方法相同，返回m1
            if (m1 == m2)
                return m1;
            // 调用signatureMoreSpecifie()方法比较m1与m2的签名
            // 例11-18
            boolean m1SignatureMoreSpecific = signatureMoreSpecific(env, site, m1, m2, allowBoxing, useVarargs);
            boolean m2SignatureMoreSpecific = signatureMoreSpecific(env, site, m2, m1, allowBoxing, useVarargs);
            // m1与m2的签名都匹配
            if (m1SignatureMoreSpecific && m2SignatureMoreSpecific) {
                // 调用types.memberType()方法得到m1与m2方法在site下的具体类型mt1与mt2
                Type mt1 = types.memberType(site, m1);
                Type mt2 = types.memberType(site, m2);
                // 判断两个方法是否相互覆写
                if (!types.overrideEquivalent(mt1, mt2))
                    // 例11-21
                    // 没有相互覆写，有歧义
                    return ambiguityError(m1, m2);

                // same signature; select (a) the non-bridge method, or
                // (b) the one that overrides the other, or (c) the concrete
                // one, or (d) merge both abstract signatures
                if ((m1.flags() & BRIDGE) != (m2.flags() & BRIDGE))
                    return ((m1.flags() & BRIDGE) != 0) ? m2 : m1;

                // 当一个方法覆写或隐藏了另外一个方法时，返回这个方法
                // 例11-23
                TypeSymbol m1Owner = (TypeSymbol)m1.owner;
                TypeSymbol m2Owner = (TypeSymbol)m2.owner;
                if (types.asSuper(m1Owner.type, m2Owner) != null &&
                    ((m1.owner.flags_field & INTERFACE) == 0 ||
                     (m2.owner.flags_field & INTERFACE) != 0) &&
                    m1.overrides(m2, m1Owner, types, false))
                    return m1;
                if (types.asSuper(m2Owner.type, m1Owner) != null &&
                    ((m2.owner.flags_field & INTERFACE) == 0 ||
                     (m1.owner.flags_field & INTERFACE) != 0) &&
                    m2.overrides(m1, m2Owner, types, false))
                    return m2;
                // 如果一个为抽象方法而另外一个为非抽象方法，则返回非抽象方法
                boolean m1Abstract = (m1.flags() & ABSTRACT) != 0;
                boolean m2Abstract = (m2.flags() & ABSTRACT) != 0;
                if (m1Abstract && !m2Abstract)
                    return m2;
                if (m2Abstract && !m1Abstract)
                    return m1;
                // 两个方法同时非抽象方法
                // 例11-24
                if (!m1Abstract && !m2Abstract)
                    return ambiguityError(m1, m2);
                // 当两个方法在泛型擦除后签名相同时，将产生引用歧义
                // 两个方法同时抽象方法
                // 调用types.isSameTypes()方法判断两个泛型擦除后的方法的形式参数类型是否相同时，可以确定此时的m1与m2都为抽象方法
                // 例11-25
                if (!types.isSameTypes(m1.erasure(types).getParameterTypes(),
                                       m2.erasure(types).getParameterTypes()))
                    return ambiguityError(m1, m2);
                // 两个方法同时为抽象方法并且相互不覆写，合并抛出的异常和返回类型
                Symbol mostSpecific;
                // 当types.returnTypeSubstitutable()方法返回true时，表示mt1比mt2的返回类型更精确
                if (types.returnTypeSubstitutable(mt1, mt2))
                    mostSpecific = m1;
                // 当types.returnTypeSubstitutable()方法返回true时，表示mt2比mt1的返回类型更精确
                else if (types.returnTypeSubstitutable(mt2, mt1))
                    mostSpecific = m2;
                else {
                    // Theoretically, this can't happen, but it is possible
                    // due to error recovery or mixing incompatible class files
                    return ambiguityError(m1, m2);
                }
                // 调用intersect()方法计算新的异常参数列表
                List<Type> allThrown = chk.intersect(mt1.getThrownTypes(), mt2.getThrownTypes());
                // 创建一个新的Type对象
                // 因为抛出的异常参数类型要取两个方法的交集
                // 例11-26
                Type newSig = types.createMethodTypeWithThrown(mostSpecific.type, allThrown);
                // 然后创建一个新的MethodSymbol对象
                MethodSymbol result = new MethodSymbol(
                        mostSpecific.flags(),
                        mostSpecific.name,
                        newSig,
                        mostSpecific.owner) {
                    @Override
                    public MethodSymbol implementation(TypeSymbol origin, Types types, boolean checkResult) {
                        if (origin == site.tsym)
                            return this;
                        else
                            return super.implementation(origin, types, checkResult);
                    }
                };
                // 返回result对象
                return result;
            }
            // m1更精确，返回m1
            if (m1SignatureMoreSpecific)
                return m1;
            // m2更精确，返回m2
            if (m2SignatureMoreSpecific)
                return m2;
            return ambiguityError(m1, m2);
        case AMBIGUOUS: // 比较m1与e.sym与e.sym2签名的精确性 例11-19
            AmbiguityError e = (AmbiguityError)m2;
            Symbol err1 = mostSpecific(m1, e.sym, env, site, allowBoxing, useVarargs);
            Symbol err2 = mostSpecific(m1, e.sym2, env, site, allowBoxing, useVarargs);
            if (err1 == err2)
                return err1;
            if (err1 == e.sym && err2 == e.sym2)
                return m2;
            if (err1 instanceof AmbiguityError &&
                err2 instanceof AmbiguityError &&
                ((AmbiguityError)err1).sym == ((AmbiguityError)err2).sym)
                return ambiguityError(m1, m2);
            else
                return ambiguityError(err1, err2);
        default:
            throw new AssertionError();
        }
    }
    //where
    // 比较重载方法的精确性
    private boolean signatureMoreSpecific(Env<AttrContext> env, Type site, Symbol m1, Symbol m2, boolean allowBoxing, boolean useVarargs) {
        // 例11-20
        noteWarner.clear();
        // m1与m2方法要比较精确性其实就是比较形式参数的类型，
        // 但是由于可能含有变长参数，所以两个方法的形式参数的数量可能并不相等
        // 首先需要调用adjustVarargs()方法调整含有变长参数方法的形式参数的数量，使得两个被比较的方法的形式参数个数相同
        // adjustVarargs()方法以m2的形式参数为标准对m1方法的形式参数进行调整，最后返回调整参数个数后的m1
        // 然后调用types.memberType()方法计算m1在site类型下的方法类型mtype1
        Type mtype1 = types.memberType(site, adjustVarargs(m1, m2, useVarargs));
        // 为了证明m1比m2方法更精确，在调用instantiate()方法时，
        // 直接将m1方法的形式参数类型当作调用m2方法时传递的实际参数类型，
        // 如果能够返回代表m2方法的类型mtype2，则mostSpecific()方法将返回true，表示m1比m2更精确
        Type mtype2 = instantiate(env, site, adjustVarargs(m2, m1, useVarargs),
                // types.lowerBoundArgtypes()对方法的形式参数类型间接调用Types类中的lowerBound()方法求下界
                types.lowerBoundArgtypes(mtype1), null,
                allowBoxing, false, noteWarner);
        return mtype2 != null &&
                !noteWarner.hasLint(Lint.LintCategory.UNCHECKED);
    }
    //where
    // 已from为标准调整to, 最后返回调整参数个数的to
    private Symbol adjustVarargs(Symbol to, Symbol from, boolean useVarargs) {
        List<Type> fromArgs = from.type.getParameterTypes();
        List<Type> toArgs = to.type.getParameterTypes();
        // 在允许变长参数并且2个方法都含有变长参数的情况下，通过向to方法中添加形式参数
        // 让两个方法的形式参数数量一致
        if (useVarargs &&
                // 当useVarargs参数值为true并且to与from都是含有变长参数的方法时，
                (from.flags() & VARARGS) != 0 &&
                (to.flags() & VARARGS) != 0) {
            // from的可变参数
            Type varargsTypeFrom = fromArgs.last();
            // to的可变参数
            Type varargsTypeTo = toArgs.last();
            ListBuffer<Type> args = ListBuffer.lb();
            // 当to方法所含的参数数量少于from方法所含的参数数量时
            if (toArgs.length() < fromArgs.length()) {
                /*
                如果我们正在检查一个可变参数方法'from'与另一个可变参数方法'to'
                （其中'to'的arity <'from'的arity）然后将'to'的签名扩展为'fit''from'的arity
                （这意味着将假形式添加到“to”直到“to”签名与“from”具有相同的数量）
                 */
                // 尽可能调整to方法的形式参数数量与from的参数数量相同
                // 例11-21
                while (fromArgs.head != varargsTypeFrom) {
                    args.append(toArgs.head == varargsTypeTo ? types.elemtype(varargsTypeTo) : toArgs.head);
                    fromArgs = fromArgs.tail;
                    toArgs = toArgs.head == varargsTypeTo ?
                        toArgs :
                        toArgs.tail;
                }
            } else {
                //形式参数列表与删除最后一个参数（数组类型）的原始列表相同
                args.appendList(toArgs.reverse().tail.reverse());
            }
            //追加可变参数元素类型作为最后的合成形式
            args.append(types.elemtype(varargsTypeTo));
            // 调用types.createMethodTypeWithParameters()方法创建新的方法类型mtype，然后创建一个MethodSymbol对象返回，
            // 不过这个方法没有变长参数，所以需要去掉to.flags_field值中的VARARGS标识。
            Type mtype = types.createMethodTypeWithParameters(to.type, args.toList());
            return new MethodSymbol(to.flags_field & ~VARARGS, to.name, mtype, to.owner);
        } else {
            return to;
        }
    }
    //where
    Symbol ambiguityError(Symbol m1, Symbol m2) {
        if (((m1.flags() | m2.flags()) & CLASH) != 0) {
            return (m1.flags() & CLASH) == 0 ? m1 : m2;
        } else {
            return new AmbiguityError(m1, m2);
        }
    }

    /** Find best qualified method matching given name, type and value
     *  arguments.
     *  @param env       The current environment.
     *  @param site      The original type from where the selection
     *                   takes place.
     *  @param name      The method's name.
     *  @param argtypes  The method's value arguments.
     *  @param typeargtypes The method's type arguments
     *  @param allowBoxing Allow boxing conversions of arguments.
     *  @param useVarargs Box trailing arguments into an array for varargs.
     */
    Symbol findMethod(Env<AttrContext> env,
                      Type site,
                      Name name,
                      List<Type> argtypes,
                      List<Type> typeargtypes,
                      boolean allowBoxing,
                      boolean useVarargs,
                      boolean operator) {
        Symbol bestSoFar = methodNotFound;
        return findMethod(env,
                          site,
                          name,
                          argtypes,
                          typeargtypes,
                          site.tsym.type,
                          true,
                          bestSoFar,
                          allowBoxing,
                          useVarargs,
                          operator,
                          new HashSet<TypeSymbol>());
    }
    // where
    private Symbol findMethod(Env<AttrContext> env,
                              Type site,
                              Name name,
                              List<Type> argtypes,
                              List<Type> typeargtypes,
                              Type intype,
                              boolean abstractok,
                              Symbol bestSoFar,
                              boolean allowBoxing,
                              boolean useVarargs,
                              boolean operator,
                              Set<TypeSymbol> seen) {
        for (Type ct = intype; ct.tag == CLASS || ct.tag == TYPEVAR; ct = types.supertype(ct)) {
            // 类型参数
            while (ct.tag == TYPEVAR)
                // 找到上界
                ct = ct.getUpperBound();
            ClassSymbol c = (ClassSymbol)ct.tsym;
            // seen主要用来避免重复查找，例如子类和父类都实现了一个共同的接口，
            // 那么只需要查找一遍即可，这样这个共同的接口及这个接口实现的一些接口就都避免了重复查找
            if (!seen.add(c)) // 避免重复查找
                return bestSoFar;
            // 当c中不存在抽象方法时，不用检查接口中的方法，因为接口中的方法都有对应的方法实现
            // 当前查找的c不是抽象类、接口或枚举类时会更新为false，因为抽象类、接口或枚举类中可以有抽象方法，所以要查找的可能就是这些抽象方法
            if ((c.flags() & (ABSTRACT | INTERFACE | ENUM)) == 0)
                abstractok = false;
            // 找到name的符号
            for (Scope.Entry e = c.members().lookup(name);
                 e.scope != null;
                 e = e.next()) {
                // e为方法
                if (e.sym.kind == MTH &&
                        // e不是合成方法
                    (e.sym.flags_field & SYNTHETIC) == 0) {
                    // 查找最匹配的方法
                    bestSoFar = selectBest(env, site, argtypes, typeargtypes,
                                           e.sym, bestSoFar,
                                           allowBoxing,
                                           useVarargs,
                                           operator);
                }
            }
            if (name == names.init)
                break;
            // 当abstractok的值为true时，要查找c的实现接口中定义的方法
            // 因为要查找的方法可能就是接口中定义的方法
            if (abstractok) {
                // 查找抽象方法
                Symbol concrete = methodNotFound;
                if ((bestSoFar.flags() & ABSTRACT) == 0)
                    concrete = bestSoFar;
                // 查找接口实现的方法
                for (List<Type> l = types.interfaces(c.type);
                     l.nonEmpty();
                     l = l.tail) {
                    bestSoFar = findMethod(env, site, name, argtypes,
                                           typeargtypes,
                                           l.head, abstractok, bestSoFar,
                                           allowBoxing, useVarargs, operator, seen);
                }
                if (concrete != bestSoFar &&
                    concrete.kind < ERR  && bestSoFar.kind < ERR &&
                    types.isSubSignature(concrete.type, bestSoFar.type))
                    bestSoFar = concrete;
            }
        }
        return bestSoFar;
    }

    /** Find unqualified method matching given name, type and value arguments.
     *  @param env       The current environment.
     *  @param name      The method's name.
     *  @param argtypes  The method's value arguments.
     *  @param typeargtypes  The method's type arguments.
     *  @param allowBoxing Allow boxing conversions of arguments.
     *  @param useVarargs Box trailing arguments into an array for varargs.
     */
    Symbol findFun(Env<AttrContext> env, Name name,
                   List<Type> argtypes, List<Type> typeargtypes,
                   boolean allowBoxing, boolean useVarargs) {
        Symbol bestSoFar = methodNotFound;
        Symbol sym;
        Env<AttrContext> env1 = env;
        boolean staticOnly = false;
        while (env1.outer != null) {
            if (isStatic(env1)) staticOnly = true;
            sym = findMethod(
                env1, env1.enclClass.sym.type, name, argtypes, typeargtypes,
                allowBoxing, useVarargs, false);
            if (sym.exists()) {
                if (staticOnly &&
                    sym.kind == MTH &&
                    sym.owner.kind == TYP &&
                    (sym.flags() & STATIC) == 0) return new StaticError(sym);
                else return sym;
            } else if (sym.kind < bestSoFar.kind) {
                bestSoFar = sym;
            }
            if ((env1.enclClass.sym.flags() & STATIC) != 0) staticOnly = true;
            env1 = env1.outer;
        }

        sym = findMethod(env, syms.predefClass.type, name, argtypes,
                         typeargtypes, allowBoxing, useVarargs, false);
        if (sym.exists())
            return sym;

        Scope.Entry e = env.toplevel.namedImportScope.lookup(name);
        for (; e.scope != null; e = e.next()) {
            sym = e.sym;
            Type origin = e.getOrigin().owner.type;
            if (sym.kind == MTH) {
                if (e.sym.owner.type != origin)
                    sym = sym.clone(e.getOrigin().owner);
                if (!isAccessible(env, origin, sym))
                    sym = new AccessError(env, origin, sym);
                bestSoFar = selectBest(env, origin,
                                       argtypes, typeargtypes,
                                       sym, bestSoFar,
                                       allowBoxing, useVarargs, false);
            }
        }
        if (bestSoFar.exists())
            return bestSoFar;

        e = env.toplevel.starImportScope.lookup(name);
        for (; e.scope != null; e = e.next()) {
            sym = e.sym;
            Type origin = e.getOrigin().owner.type;
            if (sym.kind == MTH) {
                if (e.sym.owner.type != origin)
                    sym = sym.clone(e.getOrigin().owner);
                if (!isAccessible(env, origin, sym))
                    sym = new AccessError(env, origin, sym);
                bestSoFar = selectBest(env, origin,
                                       argtypes, typeargtypes,
                                       sym, bestSoFar,
                                       allowBoxing, useVarargs, false);
            }
        }
        return bestSoFar;
    }

    /** Load toplevel or member class with given fully qualified name and
     *  verify that it is accessible.
     *  @param env       The current environment.
     *  @param name      The fully qualified name of the class to be loaded.
     */
    Symbol loadClass(Env<AttrContext> env, Name name) {
        try {
            ClassSymbol c = reader.loadClass(name);
            return isAccessible(env, c) ? c : new AccessError(c);
        } catch (ClassReader.BadClassFile err) {
            throw err;
        } catch (CompletionFailure ex) {
            return typeNotFound;
        }
    }

    /** Find qualified member type.
     *  @param env       The current environment.
     *  @param site      The original type from where the selection takes
     *                   place.
     *  @param name      The type's name.
     *  @param c         The class to search for the member type. This is
     *                   always a superclass or implemented interface of
     *                   site's class.
     */
    // 查找成员类型
    Symbol findMemberType(Env<AttrContext> env,
                          Type site,
                          Name name,
                          TypeSymbol c) {
        Symbol bestSoFar = typeNotFound;
        Symbol sym;
        // 从c中查找成员类型
        Scope.Entry e = c.members().lookup(name);

        while (e.scope != null) {
            if (e.sym.kind == TYP) {
                // 如果找到还需要调用isAccessible()方法判断在env环境下是否能访问到该类
                return isAccessible(env, site, e.sym)
                        // 如果能访问到就直接返回查找到的符号，否则返回AccessError对象
                    ? e.sym
                    : new AccessError(env, site, e.sym);
            }
            e = e.next();
        }
        // 从c的父类中查找成员类型
        Type st = types.supertype(c.type);
        if (st != null && st.tag == CLASS) {
            // 递归调用findMemberType()方法进行查找
            sym = findMemberType(env, site, name, st.tsym);
            // 如果找到合适的符号也没有直接返回
            if (sym.kind < bestSoFar.kind)
                bestSoFar = sym;
        }
        // 从c的接口中查找成员类型
        for (List<Type> l = types.interfaces(c.type);
             bestSoFar.kind != AMBIGUOUS && l.nonEmpty();
             l = l.tail) {
            sym = findMemberType(env, site, name, l.head.tsym);
            if (bestSoFar.kind < AMBIGUOUS && sym.kind < AMBIGUOUS &&
                sym.owner != bestSoFar.owner)
                // 继续进行查找，这样就可以避免父类或接口中定义相同成员类型导致引用歧义，
                // 如果有歧义，返回AmbiguityErr对象
                // 例11-5
                // 例11-6
                bestSoFar = new AmbiguityError(bestSoFar, sym);
            else if (sym.kind < bestSoFar.kind)
                bestSoFar = sym;
        }
        return bestSoFar;
    }

    /** Find a global type in given scope and load corresponding class.
     *  @param env       The current environment.
     *  @param scope     The scope in which to look for the type.
     *  @param name      The type's name.
     */
    // 在给定范围内查找类型并加载相应的类
    Symbol findGlobalType(Env<AttrContext> env, Scope scope, Name name) {
        Symbol bestSoFar = typeNotFound;
        // 搜索scope中名称为name的类型，如果e.scope不为空，也就是e不为哨兵
        for (Scope.Entry e = scope.lookup(name); e.scope != null; e = e.next()) {
            // 调用loadClass()方法确保类型被加载，也就是确保ClassSymbol对象的members_field已经填充了成员符号
            // 因为在分析导入声明或本包内的类时并没有对这个变量进行填充，
            // 只是为ClassSymbol对象的completer设置了ClassReader对象，
            // 以实现惰性填充，所以这里一般会调用ClassReader对象的complete()方法完成成员符号的加载。
            Symbol sym = loadClass(env, e.sym.flatName());
            if (bestSoFar.kind == TYP && sym.kind == TYP &&
                bestSoFar != sym)
                return new AmbiguityError(bestSoFar, sym);
            else if (sym.kind < bestSoFar.kind)
                bestSoFar = sym;
        }
        return bestSoFar;
    }

    /** Find an unqualified type symbol.
     *  @param env       The current environment.
     *  @param name      The type's name.
     */
    // 查找唯一的类型符号
    // findType()方法首先从当前名称name被使用的上下文环境env开始查找，
    // 如果本地作用域中没有，就会调用findMemberType()方法从类型的members_field中或者类型的父类中查找。
    // 由于类型可以嵌套，所以对每个封闭类型都执行这样的查找逻辑。
    // 通过将env1更新为env1.outer来跳出当前的类型，也就是跳出当前类型的查找逻辑。
    // 在第6章介绍Env类时介绍过outer变量，这个变量可以快速地跳转到封闭当前类型作用域所对应的上下文环境
    Symbol findType(Env<AttrContext> env, Name name) {
        Symbol bestSoFar = typeNotFound;
        Symbol sym;
        // staticOnly表示当前的环境是否为静态的
        // 根据AttrContext类中的staticLevel变量判断当前的环境是否为静态的，
        // 不过有时候还要结合Env类中的baseClause变量进行判断
        // 例11-12
        boolean staticOnly = false;
        // 例11-13 说明类型被使用的优先级
        // 从env1.info.scope开始查找类型
        for (Env<AttrContext> env1 = env; env1.outer != null; env1 = env1.outer) {
            if (isStatic(env1))
                staticOnly = true;
            for (Scope.Entry e = env1.info.scope.lookup(name);
                 e.scope != null;
                 e = e.next()) {
                if (e.sym.kind == TYP) {
                    if (staticOnly &&
                        e.sym.type.tag == TYPEVAR &&
                        e.sym.owner.kind == TYP)
                        return new StaticError(e.sym);
                    return e.sym;
                }
            }
            // 没有找到本地定义的类型，查找成员类型
            sym = findMemberType(env1, env1.enclClass.sym.type, name,
                                 env1.enclClass.sym);
            if (staticOnly && sym.kind == TYP &&
                sym.type.tag == CLASS &&
                sym.type.getEnclosingType().tag == CLASS &&
                env1.enclClass.sym.type.isParameterized() &&
                sym.type.getEnclosingType().isParameterized())
                return new StaticError(sym);
            else if (sym.exists())
                return sym;
            else if (sym.kind < bestSoFar.kind) bestSoFar = sym;
            // 使用baseClause的值辅助进行静态环境的判断
            JCClassDecl encl = env1.baseClause ? (JCClassDecl)env1.tree : env1.enclClass;
            if ((encl.sym.flags() & STATIC) != 0)
                staticOnly = true;
        }

        if (env.tree.getTag() != JCTree.IMPORT) {
            // 从env.toplevel.namedImportScope中查找类型
            // 这个作用域中含有通过导入声明导入的类型及当前编译单元中的顶层类
            sym = findGlobalType(env, env.toplevel.namedImportScope, name);
            if (sym.exists())
                return sym;
            else if (sym.kind < bestSoFar.kind)
                bestSoFar = sym;
            // 从env.toplevel.packge.members()中查找类型
            // 当前编译单元的packge.members_field中查找，这个作用域中包含着当前包下的所有类型
            sym = findGlobalType(env, env.toplevel.packge.members(), name);
            if (sym.exists())
                return sym;
            else if (sym.kind < bestSoFar.kind)
                bestSoFar = sym;
            // 从env.toplevel.starImportScope中查找类型
            // 查找编译单元的starImportScope，这个作用域中填充了所有通过带星号的导入声明导入的类型
            sym = findGlobalType(env, env.toplevel.starImportScope, name);
            if (sym.exists())
                return sym;
            else if (sym.kind < bestSoFar.kind)
                bestSoFar = sym;
        }

        return bestSoFar;
    }

    /** Find an unqualified identifier which matches a specified kind set.
     *  @param env       The current environment.
     *  @param name      The indentifier's name.
     *  @param kind      Indicates the possible symbol kinds
     *                   (a subset of VAL, TYP, PCK).
     */
    // 通过name查找符号
    // 参数kind值可能为PCK|TYP|VAL，
    // 所以调用findIdent()方法可能查找到变量、类型或者包的符号。
    // 由查找顺序可以看出，优先将name当作变量名来查找，其次当作类型名来查找，最后当作包名来查找
    // 例11-7/例11-8/例11-9/例11-10/例11-11
    Symbol findIdent(Env<AttrContext> env, Name name, int kind) {
        Symbol bestSoFar = typeNotFound;
        Symbol sym;

        if ((kind & VAR) != 0) { // 查找变量
            sym = findVar(env, name);
            if (sym.exists())
                return sym;
            else if (sym.kind < bestSoFar.kind)
                bestSoFar = sym;
        }

        if ((kind & TYP) != 0) { // 查找类型
            sym = findType(env, name);
            if (sym.exists())
                return sym;
            else if (sym.kind < bestSoFar.kind)
                bestSoFar = sym;
        }

        if ((kind & PCK) != 0) // 查找包
            return reader.enterPackage(name);
        else
            return bestSoFar;
    }

    /** Find an identifier in a package which matches a specified kind set.
     *  @param env       The current environment.
     *  @param name      The identifier's name.
     *  @param kind      Indicates the possible symbol kinds
     *                   (a nonempty subset of TYP, PCK).
     */
    // 在包下查找类型的具体符号
    // 从当前的env开始查找，具体就是在包符号pck下查找名称为name的符号，
    // kind值取自Kinds类中预定义的常量值，kind的值一般为TYP(类、类型变量)|PCK(包)，
    // 因为包下只可能查找到类型或包的符号
    Symbol findIdentInPackage(Env<AttrContext> env, TypeSymbol pck,
                              Name name, int kind) {
        Name fullname = TypeSymbol.formFullName(name, pck);
        // 初始化为类型没有找到错误
        Symbol bestSoFar = typeNotFound;
        PackageSymbol pack = null;
        if ((kind & PCK) != 0) { // 查找包
            pack = reader.enterPackage(fullname);
            // 调用符号的exists()方法返回true，表示找到了合适的符号
            if (pack.exists())
                return pack;
        }
        if ((kind & TYP) != 0) { // 查找类型
            Symbol sym = loadClass(env, fullname);
            // 调用符号的exists()方法返回true，表示找到了合适的符号
            if (sym.exists()) {
                if (name == sym.name)
                    return sym;
            }
            else if (sym.kind < bestSoFar.kind)
                bestSoFar = sym;
        }
        return (pack != null) ? pack : bestSoFar;
    }

    /** Find an identifier among the members of a given type `site'.
     *  @param env       The current environment.
     *  @param site      The type containing the symbol to be found.
     *  @param name      The identifier's name.
     *  @param kind      Indicates the possible symbol kinds
     *                   (a subset of VAL, TYP).
     */
    // 从类型下查找具体的符号
    // 从当前的env开始查找，具体就是在类型site下查找名称为name的符号，
    // kind的值一般为VAL|TYP，因为在类型中只可能存在变量或类，
    // 虽然也可能有方法，但是方法的引用非常容易区别，
    // 所以对于方法来说会直接调用Resolve类中的其他的方法进行查找
    // 调用findIdentInType()方法查找成员变量或成员类型
    Symbol findIdentInType(Env<AttrContext> env, Type site,
                           Name name, int kind) {
        Symbol bestSoFar = typeNotFound;
        Symbol sym;
        // 如果参数kind的值为VAL|TYP时，也就是无法根据上下文环境env确定要查找的到底是成员变量还是成员类型时，优先调用findField()方法来查找成员变量
        if ((kind & VAR) != 0) { // 查找变量
            // 查找成员变量
            sym = findField(env, site, name, site.tsym);
            if (sym.exists())
                return sym;
            else if (sym.kind < bestSoFar.kind)
                bestSoFar = sym;
        }

        if ((kind & TYP) != 0) { // 查找类型
            // 找不到时才调用findMemberType()方法查找成员类型
            sym = findMemberType(env, site, name, site.tsym);
            if (sym.exists())
                return sym;
            else if (sym.kind < bestSoFar.kind)
                bestSoFar = sym;
        }
        return bestSoFar;
    }

/* ***************************************************************************
 *  Access checking
 *  The following methods convert ResolveErrors to ErrorSymbols, issuing
 *  an error message in the process
 ****************************************************************************/

    /** If `sym' is a bad symbol: report error and return errSymbol
     *  else pass through unchanged,
     *  additional arguments duplicate what has been used in trying to find the
     *  symbol (--> flyweight pattern). This improves performance since we
     *  expect misses to happen frequently.
     *
     *  @param sym       The symbol that was found, or a ResolveError.
     *  @param pos       The position to use for error reporting.
     *  @param site      The original type from where the selection took place.
     *  @param name      The symbol's name.
     *  @param argtypes  The invocation's value arguments,
     *                   if we looked for a method.
     *  @param typeargtypes  The invocation's type arguments,
     *                   if we looked for a method.
     */
    Symbol access(Symbol sym,
                  DiagnosticPosition pos,
                  Symbol location,
                  Type site,
                  Name name,
                  boolean qualified,
                  List<Type> argtypes,
                  List<Type> typeargtypes) {
        if (sym.kind >= AMBIGUOUS) {
            ResolveError errSym = (ResolveError)sym;
            if (!site.isErroneous() &&
                !Type.isErroneous(argtypes) &&
                (typeargtypes==null || !Type.isErroneous(typeargtypes)))
                logResolveError(errSym, pos, location, site, name, argtypes, typeargtypes);
            sym = errSym.access(name, qualified ? site.tsym : syms.noSymbol);
        }
        return sym;
    }

    /** Same as original access(), but without location.
     */
    Symbol access(Symbol sym,
                  DiagnosticPosition pos,
                  Type site,
                  Name name,
                  boolean qualified,
                  List<Type> argtypes,
                  List<Type> typeargtypes) {
        return access(sym, pos, site.tsym, site, name, qualified, argtypes, typeargtypes);
    }

    /** Same as original access(), but without type arguments and arguments.
     */
    Symbol access(Symbol sym,
                  DiagnosticPosition pos,
                  Symbol location,
                  Type site,
                  Name name,
                  boolean qualified) {
        if (sym.kind >= AMBIGUOUS)
            return access(sym, pos, location, site, name, qualified, List.<Type>nil(), null);
        else
            return sym;
    }

    /** Same as original access(), but without location, type arguments and arguments.
     */
    Symbol access(Symbol sym,
                  DiagnosticPosition pos,
                  Type site,
                  Name name,
                  boolean qualified) {
        return access(sym, pos, site.tsym, site, name, qualified);
    }

    /** Check that sym is not an abstract method.
     */
    void checkNonAbstract(DiagnosticPosition pos, Symbol sym) {
        if ((sym.flags() & ABSTRACT) != 0)
            log.error(pos, "abstract.cant.be.accessed.directly",
                      kindName(sym), sym, sym.location());
    }

/* ***************************************************************************
 *  Debugging
 ****************************************************************************/

    /** print all scopes starting with scope s and proceeding outwards.
     *  used for debugging.
     */
    public void printscopes(Scope s) {
        while (s != null) {
            if (s.owner != null)
                System.err.print(s.owner + ": ");
            for (Scope.Entry e = s.elems; e != null; e = e.sibling) {
                if ((e.sym.flags() & ABSTRACT) != 0)
                    System.err.print("abstract ");
                System.err.print(e.sym + " ");
            }
            System.err.println();
            s = s.next;
        }
    }

    void printscopes(Env<AttrContext> env) {
        while (env.outer != null) {
            System.err.println("------------------------------");
            printscopes(env.info.scope);
            env = env.outer;
        }
    }

    public void printscopes(Type t) {
        while (t.tag == CLASS) {
            printscopes(t.tsym.members());
            t = types.supertype(t);
        }
    }

/* ***************************************************************************
 *  Name resolution
 *  Naming conventions are as for symbol lookup
 *  Unlike the find... methods these methods will report access errors
 ****************************************************************************/

    /** Resolve an unqualified (non-method) identifier.
     *  @param pos       The position to use for error reporting.
     *  @param env       The environment current at the identifier use.
     *  @param name      The identifier's name.
     *  @param kind      The set of admissible symbol kinds for the identifier.
     */
    // 通过名称查找可能的符号
    // kind值一般为VAL|TYP|PCK，也就是通过名称name来查找时，可能会查找到变量、类型或包的符号
    Symbol resolveIdent(DiagnosticPosition pos, Env<AttrContext> env,
                        Name name, int kind) {
        return access(
                // 查找标识
            findIdent(env, name, kind),
            pos, env.enclClass.sym.type, name, false);
    }

    /** Resolve an unqualified method identifier.
     *  @param pos       The position to use for error reporting.
     *  @param env       The environment current at the method invocation.
     *  @param name      The identifier's name.
     *  @param argtypes  The types of the invocation's value arguments.
     *  @param typeargtypes  The types of the invocation's type arguments.
     */
    // resolveMethod()方法与resolveQualifiedMethod()方法的查找逻辑类似，不过resolveMethod()方法会调用findFun()方法进行查找。findFun()方法查找方法的逻辑与findVar()方法查找变量的逻辑类似，具体的查找流程如图resolveMethod查找方法符号的流程所示
    // 通过简单名称查找匹配的方法
    // 查找普通方法（不包含构造方法、运算符等）的引用
    // 例11-15
    Symbol resolveMethod(DiagnosticPosition pos,
                         // 要查找的方法的上下文
                         Env<AttrContext> env,
                         // 要查找方法的name
                         Name name,
                         // 查找方法传递的实际参数类型列表
                         List<Type> argtypes,
                         // 查找方法传递的实际类型参数的类型列调用表(泛型)
                         List<Type> typeargtypes
                         // 要求查找方法的形式参数的类型和形式类型参数的类型要兼容argtypes与typeargtypes给出的实际类型
                         // 实参与擦除后的形参类型要兼容
    ) {
        Symbol sym = startResolution();
        List<MethodResolutionPhase> steps = methodResolutionSteps;
        while (steps.nonEmpty() &&
               steps.head.isApplicable(boxingEnabled, varargsEnabled) &&
               sym.kind >= ERRONEOUS) {
            currentStep = steps.head;
            sym = findFun(env, name, argtypes, typeargtypes,
                    steps.head.isBoxingRequired,
                    env.info.varArgs = steps.head.isVarargsRequired);
            methodResolutionCache.put(steps.head, sym);
            steps = steps.tail;
        }
        if (sym.kind >= AMBIGUOUS) {//if nothing is found return the 'first' error
            MethodResolutionPhase errPhase =
                    firstErroneousResolutionPhase();
            sym = access(methodResolutionCache.get(errPhase),
                    pos, env.enclClass.sym.type, name, false, argtypes, typeargtypes);
            env.info.varArgs = errPhase.isVarargsRequired;
        }
        return sym;
    }

    private Symbol startResolution() {
        wrongMethod.clear();
        wrongMethods.clear();
        // 方法没有找到错误
        return methodNotFound;
    }

    /** Resolve a qualified method identifier
     *  @param pos       The position to use for error reporting.
     *  @param env       The environment current at the method invocation.
     *  @param site      The type of the qualifying expression, in which
     *                   identifier is searched.
     *  @param name      The identifier's name.
     *  @param argtypes  The types of the invocation's value arguments.
     *  @param typeargtypes  The types of the invocation's type arguments.
     */
    // 通过在某个类型或符号中查找方法(方法的引用消除)
    // 查找普通方法（不包含构造方法、运算符等）的引用
    Symbol resolveQualifiedMethod(DiagnosticPosition pos,
                                  // 当前site的上下文环境
                                  Env<AttrContext> env,
                                  // 当前的类型
                                  Type site,
                                  // 当前查找方法的name
                                  Name name,
                                  // 查找方法传递的实际参数类型列表
                                  List<Type> argtypes,
                                  // 查找方法传递的实际类型参数的类型列调用表(泛型)
                                  List<Type> typeargtypes
                                  // 要求查找方法的形式参数的类型和形式类型参数的类型要兼容argtypes与typeargtypes给出的实际类型
                                  // 实参与擦除后的形参类型要兼容
    ) {
        return resolveQualifiedMethod(pos, env, site.tsym, site, name, argtypes, typeargtypes);
    }
    // 通过在某个类型或符号中查找方法(方法的引用消除)
    // 查找普通方法（不包含构造方法、运算符等）的引用
    Symbol resolveQualifiedMethod(DiagnosticPosition pos,
                                  // 当前location或site的上下文环境
                                  Env<AttrContext> env,
                                  // 当前的引用
                                  Symbol location,
                                  // 当前的类型
                                  Type site,
                                  // 要查找方法的name
                                  Name name,
                                  // 查找方法传递的实际参数类型列表
                                  List<Type> argtypes,
                                  // 查找方法传递的实际类型参数的类型列调用表(泛型)
                                  List<Type> typeargtypes
                                  // 要求查找方法的形式参数的类型和形式类型参数的类型要兼容argtypes与typeargtypes给出的实际类型
                                  // 实参与擦除后的形参类型要兼容
    ) {
        // 初始化sym
        // sym初始化为方法没有找到错误SymbolNotFoundError
        Symbol sym = startResolution();
        List<MethodResolutionPhase> steps = methodResolutionSteps;
        // 分3个阶段进行查找
        while (steps.nonEmpty() &&
               steps.head.isApplicable(boxingEnabled, varargsEnabled) &&
                // ABSENT_MTH是Kinds类中定义的常量，值为ERRONEOUS+7，当sym小于ERRONEOUS时不会再继续调用findFun()方法查找了，因为已经找到了适合的sym
                sym.kind >= ERRONEOUS) {
            currentStep = steps.head;
            // 方法查找的每个阶段都会调用findMethod()方法进行查找
            sym = findMethod(env, site, name, argtypes, typeargtypes,
                    steps.head.isBoxingRequired(),
                    env.info.varArgs = steps.head.isVarargsRequired(), false);
            methodResolutionCache.put(steps.head, sym);
            steps = steps.tail;
        }
        if (sym.kind >= AMBIGUOUS) {
            if (site.tsym.isPolymorphicSignatureGeneric()) {
                //polymorphic receiver - synthesize new method symbol
                env.info.varArgs = false;
                sym = findPolymorphicSignatureInstance(env,
                        site, name, null, argtypes);
            }
            else {
                //if nothing is found return the 'first' error
                MethodResolutionPhase errPhase =
                        firstErroneousResolutionPhase();
                sym = access(methodResolutionCache.get(errPhase),
                        pos, location, site, name, true, argtypes, typeargtypes);
                env.info.varArgs = errPhase.isVarargsRequired;
            }
        } else if (allowMethodHandles && sym.isPolymorphicSignatureGeneric()) {
            //non-instantiated polymorphic signature - synthesize new method symbol
            env.info.varArgs = false;
            sym = findPolymorphicSignatureInstance(env,
                    site, name, (MethodSymbol)sym, argtypes);
        }
        return sym;
    }

    /** Find or create an implicit method of exactly the given type (after erasure).
     *  Searches in a side table, not the main scope of the site.
     *  This emulates the lookup process required by JSR 292 in JVM.
     *  @param env       Attribution environment
     *  @param site      The original type from where the selection takes place.
     *  @param name      The method's name.
     *  @param spMethod  A template for the implicit method, or null.
     *  @param argtypes  The required argument types.
     */
    Symbol findPolymorphicSignatureInstance(Env<AttrContext> env, Type site,
                                            Name name,
                                            MethodSymbol spMethod,  // sig. poly. method or null if none
                                            List<Type> argtypes) {
        Type mtype = infer.instantiatePolymorphicSignatureInstance(env,
                site, name, spMethod, argtypes);
        long flags = ABSTRACT | HYPOTHETICAL | POLYMORPHIC_SIGNATURE |
                    (spMethod != null ?
                        spMethod.flags() & Flags.AccessFlags :
                        Flags.PUBLIC | Flags.STATIC);
        Symbol m = null;
        for (Scope.Entry e = polymorphicSignatureScope.lookup(name);
             e.scope != null;
             e = e.next()) {
            Symbol sym = e.sym;
            if (types.isSameType(mtype, sym.type) &&
                (sym.flags() & Flags.STATIC) == (flags & Flags.STATIC) &&
                types.isSameType(sym.owner.type, site)) {
               m = sym;
               break;
            }
        }
        if (m == null) {
            // create the desired method
            m = new MethodSymbol(flags, name, mtype, site.tsym);
            polymorphicSignatureScope.enter(m);
        }
        return m;
    }

    /** Resolve a qualified method identifier, throw a fatal error if not
     *  found.
     *  @param pos       The position to use for error reporting.
     *  @param env       The environment current at the method invocation.
     *  @param site      The type of the qualifying expression, in which
     *                   identifier is searched.
     *  @param name      The identifier's name.
     *  @param argtypes  The types of the invocation's value arguments.
     *  @param typeargtypes  The types of the invocation's type arguments.
     */
    public MethodSymbol resolveInternalMethod(DiagnosticPosition pos, Env<AttrContext> env,
                                        Type site, Name name,
                                        List<Type> argtypes,
                                        List<Type> typeargtypes) {
        Symbol sym = resolveQualifiedMethod(
            pos, env, site.tsym, site, name, argtypes, typeargtypes);
        if (sym.kind == MTH) return (MethodSymbol)sym;
        else throw new FatalError(
                 diags.fragment("fatal.err.cant.locate.meth",
                                name));
    }

    /** Resolve constructor.
     *  @param pos       The position to use for error reporting.
     *  @param env       The environment current at the constructor invocation.
     *  @param site      The type of class for which a constructor is searched.
     *  @param argtypes  The types of the constructor invocation's value
     *                   arguments.
     *  @param typeargtypes  The types of the constructor invocation's type
     *                   arguments.
     */
    Symbol resolveConstructor(DiagnosticPosition pos,
                              Env<AttrContext> env,
                              Type site,
                              List<Type> argtypes,
                              List<Type> typeargtypes) {
        Symbol sym = startResolution();
        List<MethodResolutionPhase> steps = methodResolutionSteps;
        while (steps.nonEmpty() &&
               steps.head.isApplicable(boxingEnabled, varargsEnabled) &&
               sym.kind >= ERRONEOUS) {
            currentStep = steps.head;
            sym = resolveConstructor(pos, env, site, argtypes, typeargtypes,
                    steps.head.isBoxingRequired(),
                    env.info.varArgs = steps.head.isVarargsRequired());
            methodResolutionCache.put(steps.head, sym);
            steps = steps.tail;
        }
        if (sym.kind >= AMBIGUOUS) {//if nothing is found return the 'first' error
            MethodResolutionPhase errPhase = firstErroneousResolutionPhase();
            sym = access(methodResolutionCache.get(errPhase),
                    pos, site, names.init, true, argtypes, typeargtypes);
            env.info.varArgs = errPhase.isVarargsRequired();
        }
        return sym;
    }

    /** Resolve constructor using diamond inference.
     *  @param pos       The position to use for error reporting.
     *  @param env       The environment current at the constructor invocation.
     *  @param site      The type of class for which a constructor is searched.
     *                   The scope of this class has been touched in attribution.
     *  @param argtypes  The types of the constructor invocation's value
     *                   arguments.
     *  @param typeargtypes  The types of the constructor invocation's type
     *                   arguments.
     */
    Symbol resolveDiamond(DiagnosticPosition pos,
                              Env<AttrContext> env,
                              Type site,
                              List<Type> argtypes,
                              List<Type> typeargtypes) {
        Symbol sym = startResolution();
        List<MethodResolutionPhase> steps = methodResolutionSteps;
        while (steps.nonEmpty() &&
               steps.head.isApplicable(boxingEnabled, varargsEnabled) &&
               sym.kind >= ERRONEOUS) {
            currentStep = steps.head;
            sym = resolveConstructor(pos, env, site, argtypes, typeargtypes,
                    steps.head.isBoxingRequired(),
                    env.info.varArgs = steps.head.isVarargsRequired());
            methodResolutionCache.put(steps.head, sym);
            steps = steps.tail;
        }
        if (sym.kind >= AMBIGUOUS) {
            final JCDiagnostic details = sym.kind == WRONG_MTH ?
                ((InapplicableSymbolError)sym).explanation :
                null;
            Symbol errSym = new ResolveError(WRONG_MTH, "diamond error") {
                @Override
                JCDiagnostic getDiagnostic(DiagnosticType dkind, DiagnosticPosition pos,
                        Symbol location, Type site, Name name, List<Type> argtypes, List<Type> typeargtypes) {
                    String key = details == null ?
                        "cant.apply.diamond" :
                        "cant.apply.diamond.1";
                    return diags.create(dkind, log.currentSource(), pos, key,
                            diags.fragment("diamond", site.tsym), details);
                }
            };
            MethodResolutionPhase errPhase = firstErroneousResolutionPhase();
            sym = access(errSym, pos, site, names.init, true, argtypes, typeargtypes);
            env.info.varArgs = errPhase.isVarargsRequired();
        }
        return sym;
    }

    /** Resolve constructor.
     *  @param pos       The position to use for error reporting.
     *  @param env       The environment current at the constructor invocation.
     *  @param site      The type of class for which a constructor is searched.
     *  @param argtypes  The types of the constructor invocation's value
     *                   arguments.
     *  @param typeargtypes  The types of the constructor invocation's type
     *                   arguments.
     *  @param allowBoxing Allow boxing and varargs conversions.
     *  @param useVarargs Box trailing arguments into an array for varargs.
     */
    Symbol resolveConstructor(DiagnosticPosition pos, Env<AttrContext> env,
                              Type site, List<Type> argtypes,
                              List<Type> typeargtypes,
                              boolean allowBoxing,
                              boolean useVarargs) {
        Symbol sym = findMethod(env, site,
                                names.init, argtypes,
                                typeargtypes, allowBoxing,
                                useVarargs, false);
        chk.checkDeprecated(pos, env.info.scope.owner, sym);
        return sym;
    }

    /** Resolve a constructor, throw a fatal error if not found.
     *  @param pos       The position to use for error reporting.
     *  @param env       The environment current at the method invocation.
     *  @param site      The type to be constructed.
     *  @param argtypes  The types of the invocation's value arguments.
     *  @param typeargtypes  The types of the invocation's type arguments.
     */
    public MethodSymbol resolveInternalConstructor(DiagnosticPosition pos, Env<AttrContext> env,
                                        Type site,
                                        List<Type> argtypes,
                                        List<Type> typeargtypes) {
        Symbol sym = resolveConstructor(
            pos, env, site, argtypes, typeargtypes);
        if (sym.kind == MTH) return (MethodSymbol)sym;
        else throw new FatalError(
                 diags.fragment("fatal.err.cant.locate.ctor", site));
    }

    /** Resolve operator.
     *  @param pos       The position to use for error reporting.
     *  @param optag     The tag of the operation tree.
     *  @param env       The environment current at the operation.
     *  @param argtypes  The types of the operands.
     */
    Symbol resolveOperator(DiagnosticPosition pos, int optag,
                           Env<AttrContext> env, List<Type> argtypes) {
        Name name = treeinfo.operatorName(optag);
        Symbol sym = findMethod(env, syms.predefClass.type, name, argtypes,
                                null, false, false, true);
        if (boxingEnabled && sym.kind >= WRONG_MTHS)
            sym = findMethod(env, syms.predefClass.type, name, argtypes,
                             null, true, false, true);
        return access(sym, pos, env.enclClass.sym.type, name,
                      false, argtypes, null);
    }

    /** Resolve operator.
     *  @param pos       The position to use for error reporting.
     *  @param optag     The tag of the operation tree.
     *  @param env       The environment current at the operation.
     *  @param arg       The type of the operand.
     */
    Symbol resolveUnaryOperator(DiagnosticPosition pos, int optag, Env<AttrContext> env, Type arg) {
        return resolveOperator(pos, optag, env, List.of(arg));
    }

    /** Resolve binary operator.
     *  @param pos       The position to use for error reporting.
     *  @param optag     The tag of the operation tree.
     *  @param env       The environment current at the operation.
     *  @param left      The types of the left operand.
     *  @param right     The types of the right operand.
     */
    Symbol resolveBinaryOperator(DiagnosticPosition pos,
                                 int optag,
                                 Env<AttrContext> env,
                                 Type left,
                                 Type right) {
        return resolveOperator(pos, optag, env, List.of(left, right));
    }

    /**
     * Resolve `c.name' where name == this or name == super.
     * @param pos           The position to use for error reporting.
     * @param env           The environment current at the expression.
     * @param c             The qualifier.
     * @param name          The identifier's name.
     */
    Symbol resolveSelf(DiagnosticPosition pos,
                       Env<AttrContext> env,
                       TypeSymbol c,
                       Name name) {
        Env<AttrContext> env1 = env;
        boolean staticOnly = false;
        while (env1.outer != null) {
            if (isStatic(env1)) staticOnly = true;
            if (env1.enclClass.sym == c) {
                Symbol sym = env1.info.scope.lookup(name).sym;
                if (sym != null) {
                    if (staticOnly) sym = new StaticError(sym);
                    return access(sym, pos, env.enclClass.sym.type,
                                  name, true);
                }
            }
            if ((env1.enclClass.sym.flags() & STATIC) != 0) staticOnly = true;
            env1 = env1.outer;
        }
        log.error(pos, "not.encl.class", c);
        return syms.errSymbol;
    }

    /**
     * Resolve `c.this' for an enclosing class c that contains the
     * named member.
     * @param pos           The position to use for error reporting.
     * @param env           The environment current at the expression.
     * @param member        The member that must be contained in the result.
     */
    Symbol resolveSelfContaining(DiagnosticPosition pos,
                                 Env<AttrContext> env,
                                 Symbol member,
                                 boolean isSuperCall) {
        Name name = names._this;
        Env<AttrContext> env1 = isSuperCall ? env.outer : env;
        boolean staticOnly = false;
        if (env1 != null) {
            while (env1 != null && env1.outer != null) {
                if (isStatic(env1)) staticOnly = true;
                if (env1.enclClass.sym.isSubClass(member.owner, types)) {
                    Symbol sym = env1.info.scope.lookup(name).sym;
                    if (sym != null) {
                        if (staticOnly) sym = new StaticError(sym);
                        return access(sym, pos, env.enclClass.sym.type,
                                      name, true);
                    }
                }
                if ((env1.enclClass.sym.flags() & STATIC) != 0)
                    staticOnly = true;
                env1 = env1.outer;
            }
        }
        log.error(pos, "encl.class.required", member);
        return syms.errSymbol;
    }

    /**
     * Resolve an appropriate implicit this instance for t's container.
     * JLS 8.8.5.1 and 15.9.2
     */
    Type resolveImplicitThis(DiagnosticPosition pos, Env<AttrContext> env, Type t) {
        return resolveImplicitThis(pos, env, t, false);
    }

    Type resolveImplicitThis(DiagnosticPosition pos, Env<AttrContext> env, Type t, boolean isSuperCall) {
        Type thisType = (((t.tsym.owner.kind & (MTH|VAR)) != 0)
                         ? resolveSelf(pos, env, t.getEnclosingType().tsym, names._this)
                         : resolveSelfContaining(pos, env, t.tsym, isSuperCall)).type;
        if (env.info.isSelfCall && thisType.tsym == env.enclClass.sym)
            log.error(pos, "cant.ref.before.ctor.called", "this");
        return thisType;
    }

/* ***************************************************************************
 *  ResolveError classes, indicating error situations when accessing symbols
 ****************************************************************************/

    public void logAccessError(Env<AttrContext> env, JCTree tree, Type type) {
        AccessError error = new AccessError(env, type.getEnclosingType(), type.tsym);
        logResolveError(error, tree.pos(), type.getEnclosingType().tsym, type.getEnclosingType(), null, null, null);
    }
    //where
    private void logResolveError(ResolveError error,
            DiagnosticPosition pos,
            Symbol location,
            Type site,
            Name name,
            List<Type> argtypes,
            List<Type> typeargtypes) {
        JCDiagnostic d = error.getDiagnostic(JCDiagnostic.DiagnosticType.ERROR,
                pos, location, site, name, argtypes, typeargtypes);
        if (d != null) {
            d.setFlag(DiagnosticFlag.RESOLVE_ERROR);
            log.report(d);
        }
    }

    private final LocalizedString noArgs = new LocalizedString("compiler.misc.no.args");

    public Object methodArguments(List<Type> argtypes) {
        return argtypes.isEmpty() ? noArgs : argtypes;
    }

    /**
     * Root class for resolution errors. Subclass of ResolveError
     * represent a different kinds of resolution error - as such they must
     * specify how they map into concrete compiler diagnostics.
     */
    private abstract class ResolveError extends Symbol {

        /** The name of the kind of error, for debugging only. */
        final String debugName;

        ResolveError(int kind, String debugName) {
            super(kind, 0, null, null, null);
            this.debugName = debugName;
        }

        @Override
        public <R, P> R accept(ElementVisitor<R, P> v, P p) {
            throw new AssertionError();
        }

        @Override
        public String toString() {
            return debugName;
        }

        @Override
        public boolean exists() {
            return false;
        }

        /**
         * Create an external representation for this erroneous symbol to be
         * used during attribution - by default this returns the symbol of a
         * brand new error type which stores the original type found
         * during resolution.
         *
         * @param name     the name used during resolution
         * @param location the location from which the symbol is accessed
         */
        protected Symbol access(Name name, TypeSymbol location) {
            return types.createErrorType(name, location, syms.errSymbol.type).tsym;
        }

        /**
         * Create a diagnostic representing this resolution error.
         *
         * @param dkind     The kind of the diagnostic to be created (e.g error).
         * @param pos       The position to be used for error reporting.
         * @param site      The original type from where the selection took place.
         * @param name      The name of the symbol to be resolved.
         * @param argtypes  The invocation's value arguments,
         *                  if we looked for a method.
         * @param typeargtypes  The invocation's type arguments,
         *                      if we looked for a method.
         */
        abstract JCDiagnostic getDiagnostic(JCDiagnostic.DiagnosticType dkind,
                DiagnosticPosition pos,
                Symbol location,
                Type site,
                Name name,
                List<Type> argtypes,
                List<Type> typeargtypes);

        /**
         * A name designates an operator if it consists
         * of a non-empty sequence of operator symbols +-~!/*%&|^<>=
         */
        boolean isOperator(Name name) {
            int i = 0;
            while (i < name.getByteLength() &&
                   "+-~!*/%&|^<>=".indexOf(name.getByteAt(i)) >= 0) i++;
            return i > 0 && i == name.getByteLength();
        }
    }

    /**
     * This class is the root class of all resolution errors caused by
     * an invalid symbol being found during resolution.
     */
    abstract class InvalidSymbolError extends ResolveError {

        /** The invalid symbol found during resolution */
        Symbol sym;

        InvalidSymbolError(int kind, Symbol sym, String debugName) {
            super(kind, debugName);
            this.sym = sym;
        }

        @Override
        public boolean exists() {
            return true;
        }

        @Override
        public String toString() {
             return super.toString() + " wrongSym=" + sym;
        }

        @Override
        public Symbol access(Name name, TypeSymbol location) {
            if (sym.kind >= AMBIGUOUS)
                return ((ResolveError)sym).access(name, location);
            else if ((sym.kind & ERRONEOUS) == 0 && (sym.kind & TYP) != 0)
                return types.createErrorType(name, location, sym.type).tsym;
            else
                return sym;
        }
    }

    /**
     * InvalidSymbolError error class indicating that a symbol matching a
     * given name does not exists in a given site.
     */
    class SymbolNotFoundError extends ResolveError {

        SymbolNotFoundError(int kind) {
            super(kind, "symbol not found error");
        }

        @Override
        JCDiagnostic getDiagnostic(JCDiagnostic.DiagnosticType dkind,
                DiagnosticPosition pos,
                Symbol location,
                Type site,
                Name name,
                List<Type> argtypes,
                List<Type> typeargtypes) {
            argtypes = argtypes == null ? List.<Type>nil() : argtypes;
            typeargtypes = typeargtypes == null ? List.<Type>nil() : typeargtypes;
            if (name == names.error)
                return null;

            if (isOperator(name)) {
                boolean isUnaryOp = argtypes.size() == 1;
                String key = argtypes.size() == 1 ?
                    "operator.cant.be.applied" :
                    "operator.cant.be.applied.1";
                Type first = argtypes.head;
                Type second = !isUnaryOp ? argtypes.tail.head : null;
                return diags.create(dkind, log.currentSource(), pos,
                        key, name, first, second);
            }
            boolean hasLocation = false;
            if (location == null) {
                location = site.tsym;
            }
            if (!location.name.isEmpty()) {
                if (location.kind == PCK && !site.tsym.exists()) {
                    return diags.create(dkind, log.currentSource(), pos,
                        "doesnt.exist", location);
                }
                hasLocation = !location.name.equals(names._this) &&
                        !location.name.equals(names._super);
            }
            boolean isConstructor = kind == ABSENT_MTH &&
                    name == names.table.names.init;
            KindName kindname = isConstructor ? KindName.CONSTRUCTOR : absentKind(kind);
            Name idname = isConstructor ? site.tsym.name : name;
            String errKey = getErrorKey(kindname, typeargtypes.nonEmpty(), hasLocation);
            if (hasLocation) {
                return diags.create(dkind, log.currentSource(), pos,
                        errKey, kindname, idname, //symbol kindname, name
                        typeargtypes, argtypes, //type parameters and arguments (if any)
                        getLocationDiag(location, site)); //location kindname, type
            }
            else {
                return diags.create(dkind, log.currentSource(), pos,
                        errKey, kindname, idname, //symbol kindname, name
                        typeargtypes, argtypes); //type parameters and arguments (if any)
            }
        }
        //where
        private String getErrorKey(KindName kindname, boolean hasTypeArgs, boolean hasLocation) {
            String key = "cant.resolve";
            String suffix = hasLocation ? ".location" : "";
            switch (kindname) {
                case METHOD:
                case CONSTRUCTOR: {
                    suffix += ".args";
                    suffix += hasTypeArgs ? ".params" : "";
                }
            }
            return key + suffix;
        }
        private JCDiagnostic getLocationDiag(Symbol location, Type site) {
            if (location.kind == VAR) {
                return diags.fragment("location.1",
                    kindName(location),
                    location,
                    location.type);
            } else {
                return diags.fragment("location",
                    typeKindName(site),
                    site,
                    null);
            }
        }
    }

    /**
     * InvalidSymbolError error class indicating that a given symbol
     * (either a method, a constructor or an operand) is not applicable
     * given an actual arguments/type argument list.
     */
    class InapplicableSymbolError extends InvalidSymbolError {

        /** An auxiliary explanation set in case of instantiation errors. */
        JCDiagnostic explanation;

        InapplicableSymbolError(Symbol sym) {
            super(WRONG_MTH, sym, "inapplicable symbol error");
        }

        /** Update sym and explanation and return this.
         */
        InapplicableSymbolError setWrongSym(Symbol sym, JCDiagnostic explanation) {
            this.sym = sym;
            if (this.sym == sym && explanation != null)
                this.explanation = explanation; //update the details
            return this;
        }

        /** Update sym and return this.
         */
        InapplicableSymbolError setWrongSym(Symbol sym) {
            this.sym = sym;
            return this;
        }

        @Override
        public String toString() {
            return super.toString() + " explanation=" + explanation;
        }

        @Override
        JCDiagnostic getDiagnostic(JCDiagnostic.DiagnosticType dkind,
                DiagnosticPosition pos,
                Symbol location,
                Type site,
                Name name,
                List<Type> argtypes,
                List<Type> typeargtypes) {
            if (name == names.error)
                return null;

            if (isOperator(name)) {
                boolean isUnaryOp = argtypes.size() == 1;
                String key = argtypes.size() == 1 ?
                    "operator.cant.be.applied" :
                    "operator.cant.be.applied.1";
                Type first = argtypes.head;
                Type second = !isUnaryOp ? argtypes.tail.head : null;
                return diags.create(dkind, log.currentSource(), pos,
                        key, name, first, second);
            }
            else {
                Symbol ws = sym.asMemberOf(site, types);
                return diags.create(dkind, log.currentSource(), pos,
                          "cant.apply.symbol" + (explanation != null ? ".1" : ""),
                          kindName(ws),
                          ws.name == names.init ? ws.owner.name : ws.name,
                          methodArguments(ws.type.getParameterTypes()),
                          methodArguments(argtypes),
                          kindName(ws.owner),
                          ws.owner.type,
                          explanation);
            }
        }

        void clear() {
            explanation = null;
        }

        @Override
        public Symbol access(Name name, TypeSymbol location) {
            return types.createErrorType(name, location, syms.errSymbol.type).tsym;
        }
    }

    /**
     * ResolveError error class indicating that a set of symbols
     * (either methods, constructors or operands) is not applicable
     * given an actual arguments/type argument list.
     */
    class InapplicableSymbolsError extends ResolveError {

        private List<Candidate> candidates = List.nil();

        InapplicableSymbolsError(Symbol sym) {
            super(WRONG_MTHS, "inapplicable symbols");
        }

        @Override
        JCDiagnostic getDiagnostic(JCDiagnostic.DiagnosticType dkind,
                DiagnosticPosition pos,
                Symbol location,
                Type site,
                Name name,
                List<Type> argtypes,
                List<Type> typeargtypes) {
            if (candidates.nonEmpty()) {
                JCDiagnostic err = diags.create(dkind,
                        log.currentSource(),
                        pos,
                        "cant.apply.symbols",
                        name == names.init ? KindName.CONSTRUCTOR : absentKind(kind),
                        getName(),
                        argtypes);
                return new JCDiagnostic.MultilineDiagnostic(err, candidateDetails(site));
            } else {
                return new SymbolNotFoundError(ABSENT_MTH).getDiagnostic(dkind, pos,
                    location, site, name, argtypes, typeargtypes);
            }
        }

        //where
        List<JCDiagnostic> candidateDetails(Type site) {
            List<JCDiagnostic> details = List.nil();
            for (Candidate c : candidates)
                details = details.prepend(c.getDiagnostic(site));
            return details.reverse();
        }

        Symbol addCandidate(MethodResolutionPhase currentStep, Symbol sym, JCDiagnostic details) {
            Candidate c = new Candidate(currentStep, sym, details);
            if (c.isValid() && !candidates.contains(c))
                candidates = candidates.append(c);
            return this;
        }

        void clear() {
            candidates = List.nil();
        }

        private Name getName() {
            Symbol sym = candidates.head.sym;
            return sym.name == names.init ?
                sym.owner.name :
                sym.name;
        }

        private class Candidate {

            final MethodResolutionPhase step;
            final Symbol sym;
            final JCDiagnostic details;

            private Candidate(MethodResolutionPhase step, Symbol sym, JCDiagnostic details) {
                this.step = step;
                this.sym = sym;
                this.details = details;
            }

            JCDiagnostic getDiagnostic(Type site) {
                return diags.fragment("inapplicable.method",
                        Kinds.kindName(sym),
                        sym.location(site, types),
                        sym.asMemberOf(site, types),
                        details);
            }

            @Override
            public boolean equals(Object o) {
                if (o instanceof Candidate) {
                    Symbol s1 = this.sym;
                    Symbol s2 = ((Candidate)o).sym;
                    if  ((s1 != s2 &&
                        (s1.overrides(s2, s1.owner.type.tsym, types, false) ||
                        (s2.overrides(s1, s2.owner.type.tsym, types, false)))) ||
                        ((s1.isConstructor() || s2.isConstructor()) && s1.owner != s2.owner))
                        return true;
                }
                return false;
            }

            boolean isValid() {
                return  (((sym.flags() & VARARGS) != 0 && step == VARARITY) ||
                          (sym.flags() & VARARGS) == 0 && step == (boxingEnabled ? BOX : BASIC));
            }
        }
    }

    /**
     * An InvalidSymbolError error class indicating that a symbol is not
     * accessible from a given site
     */
    class AccessError extends InvalidSymbolError {

        private Env<AttrContext> env;
        private Type site;

        AccessError(Symbol sym) {
            this(null, null, sym);
        }

        AccessError(Env<AttrContext> env, Type site, Symbol sym) {
            super(HIDDEN, sym, "access error");
            this.env = env;
            this.site = site;
            if (debugResolve)
                log.error("proc.messager", sym + " @ " + site + " is inaccessible.");
        }

        @Override
        public boolean exists() {
            return false;
        }

        @Override
        JCDiagnostic getDiagnostic(JCDiagnostic.DiagnosticType dkind,
                DiagnosticPosition pos,
                Symbol location,
                Type site,
                Name name,
                List<Type> argtypes,
                List<Type> typeargtypes) {
            if (sym.owner.type.tag == ERROR)
                return null;

            if (sym.name == names.init && sym.owner != site.tsym) {
                return new SymbolNotFoundError(ABSENT_MTH).getDiagnostic(dkind,
                        pos, location, site, name, argtypes, typeargtypes);
            }
            else if ((sym.flags() & PUBLIC) != 0
                || (env != null && this.site != null
                    && !isAccessible(env, this.site))) {
                return diags.create(dkind, log.currentSource(),
                        pos, "not.def.access.class.intf.cant.access",
                    sym, sym.location());
            }
            else if ((sym.flags() & (PRIVATE | PROTECTED)) != 0) {
                return diags.create(dkind, log.currentSource(),
                        pos, "report.access", sym,
                        asFlagSet(sym.flags() & (PRIVATE | PROTECTED)),
                        sym.location());
            }
            else {
                return diags.create(dkind, log.currentSource(),
                        pos, "not.def.public.cant.access", sym, sym.location());
            }
        }
    }

    /**
     * InvalidSymbolError error class indicating that an instance member
     * has erroneously been accessed from a static context.
     */
    class StaticError extends InvalidSymbolError {

        StaticError(Symbol sym) {
            super(STATICERR, sym, "static error");
        }

        @Override
        JCDiagnostic getDiagnostic(JCDiagnostic.DiagnosticType dkind,
                DiagnosticPosition pos,
                Symbol location,
                Type site,
                Name name,
                List<Type> argtypes,
                List<Type> typeargtypes) {
            Symbol errSym = ((sym.kind == TYP && sym.type.tag == CLASS)
                ? types.erasure(sym.type).tsym
                : sym);
            return diags.create(dkind, log.currentSource(), pos,
                    "non-static.cant.be.ref", kindName(sym), errSym);
        }
    }

    /**
     * InvalidSymbolError error class indicating that a pair of symbols
     * (either methods, constructors or operands) are ambiguous
     * given an actual arguments/type argument list.
     */
    // 歧义错误
    class AmbiguityError extends InvalidSymbolError {

        /** The other maximally specific symbol */
        Symbol sym2;

        AmbiguityError(Symbol sym1, Symbol sym2) {
            super(AMBIGUOUS, sym1, "ambiguity error");
            this.sym2 = sym2;
        }

        @Override
        JCDiagnostic getDiagnostic(JCDiagnostic.DiagnosticType dkind,
                DiagnosticPosition pos,
                Symbol location,
                Type site,
                Name name,
                List<Type> argtypes,
                List<Type> typeargtypes) {
            AmbiguityError pair = this;
            while (true) {
                if (pair.sym.kind == AMBIGUOUS)
                    pair = (AmbiguityError)pair.sym;
                else if (pair.sym2.kind == AMBIGUOUS)
                    pair = (AmbiguityError)pair.sym2;
                else break;
            }
            Name sname = pair.sym.name;
            if (sname == names.init) sname = pair.sym.owner.name;
            return diags.create(dkind, log.currentSource(),
                      pos, "ref.ambiguous", sname,
                      kindName(pair.sym),
                      pair.sym,
                      pair.sym.location(site, types),
                      kindName(pair.sym2),
                      pair.sym2,
                      pair.sym2.location(site, types));
        }
    }

    /*
    MethodResolutionPhase类表示方法查找的阶段，这个类的定义如下
     */
    enum MethodResolutionPhase {
        /*
        表示方法查找第一阶段
        基础查找
         */
        BASIC(false, false),
        /*
        表示方法查找第二阶段
        拆装箱
         */
        BOX(true, false),
        /*
        表示方法查找第三阶段
        泛型擦除
         */
        VARARITY(true, true);

        boolean isBoxingRequired;
        boolean isVarargsRequired;

        MethodResolutionPhase(boolean isBoxingRequired, boolean isVarargsRequired) {
           this.isBoxingRequired = isBoxingRequired;
           this.isVarargsRequired = isVarargsRequired;
        }

        public boolean isBoxingRequired() {
            return isBoxingRequired;
        }

        public boolean isVarargsRequired() {
            return isVarargsRequired;
        }

        public boolean isApplicable(boolean boxingEnabled, boolean varargsEnabled) {
            return (varargsEnabled || !isVarargsRequired) &&
                   (boxingEnabled || !isBoxingRequired);
        }
    }

    private Map<MethodResolutionPhase, Symbol> methodResolutionCache =
        new HashMap<MethodResolutionPhase, Symbol>(MethodResolutionPhase.values().length);

    /*
    表示方法查找的三个阶段
     */
    final List<MethodResolutionPhase> methodResolutionSteps = List.of(BASIC, BOX, VARARITY);

    private MethodResolutionPhase currentStep = null;

    private MethodResolutionPhase firstErroneousResolutionPhase() {
        MethodResolutionPhase bestSoFar = BASIC;
        Symbol sym = methodNotFound;
        List<MethodResolutionPhase> steps = methodResolutionSteps;
        while (steps.nonEmpty() &&
               steps.head.isApplicable(boxingEnabled, varargsEnabled) &&
               sym.kind >= WRONG_MTHS) {
            sym = methodResolutionCache.get(steps.head);
            bestSoFar = steps.head;
            steps = steps.tail;
        }
        return bestSoFar;
    }
}
