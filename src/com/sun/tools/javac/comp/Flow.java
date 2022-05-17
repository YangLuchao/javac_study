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

//todo: one might eliminate uninits.andSets when monotonic

package com.sun.tools.javac.comp;

import java.util.HashMap;
import java.util.Map;
import java.util.LinkedHashMap;

import com.sun.tools.javac.code.*;
import com.sun.tools.javac.tree.*;
import com.sun.tools.javac.util.*;
import com.sun.tools.javac.util.JCDiagnostic.DiagnosticPosition;

import com.sun.tools.javac.code.Symbol.*;
import com.sun.tools.javac.tree.JCTree.*;

import static com.sun.tools.javac.code.Flags.*;
import static com.sun.tools.javac.code.Kinds.*;
import static com.sun.tools.javac.code.TypeTags.*;

/** This pass implements dataflow analysis for Java programs.
 *  Liveness analysis checks that every statement is reachable.
 *  Exception analysis ensures that every checked exception that is
 *  thrown is declared or caught.  Definite assignment analysis
 *  ensures that each variable is assigned when used.  Definite
 *  unassignment analysis ensures that no final variable is assigned
 *  more than once.
 *
 *  <p>The JLS has a number of problems in the
 *  specification of these flow analysis problems. This implementation
 *  attempts to address those issues.
 *
 *  <p>First, there is no accommodation for a finally clause that cannot
 *  complete normally. For liveness analysis, an intervening finally
 *  clause can cause a break, continue, or return not to reach its
 *  target.  For exception analysis, an intervening finally clause can
 *  cause any exception to be "caught".  For DA/DU analysis, the finally
 *  clause can prevent a transfer of control from propagating DA/DU
 *  state to the target.  In addition, code in the finally clause can
 *  affect the DA/DU status of variables.
 *
 *  <p>For try statements, we introduce the idea of a variable being
 *  definitely unassigned "everywhere" in a block.  A variable V is
 *  "unassigned everywhere" in a block iff it is unassigned at the
 *  beginning of the block and there is no reachable assignment to V
 *  in the block.  An assignment V=e is reachable iff V is not DA
 *  after e.  Then we can say that V is DU at the beginning of the
 *  catch block iff V is DU everywhere in the try block.  Similarly, V
 *  is DU at the beginning of the finally block iff V is DU everywhere
 *  in the try block and in every catch block.  Specifically, the
 *  following bullet is added to 16.2.2
 *  <pre>
 *      V is <em>unassigned everywhere</em> in a block if it is
 *      unassigned before the block and there is no reachable
 *      assignment to V within the block.
 *  </pre>
 *  <p>In 16.2.15, the third bullet (and all of its sub-bullets) for all
 *  try blocks is changed to
 *  <pre>
 *      V is definitely unassigned before a catch block iff V is
 *      definitely unassigned everywhere in the try block.
 *  </pre>
 *  <p>The last bullet (and all of its sub-bullets) for try blocks that
 *  have a finally block is changed to
 *  <pre>
 *      V is definitely unassigned before the finally block iff
 *      V is definitely unassigned everywhere in the try block
 *      and everywhere in each catch block of the try statement.
 *  </pre>
 *  <p>In addition,
 *  <pre>
 *      V is definitely assigned at the end of a constructor iff
 *      V is definitely assigned after the block that is the body
 *      of the constructor and V is definitely assigned at every
 *      return that can return from the constructor.
 *  </pre>
 *  <p>In addition, each continue statement with the loop as its target
 *  is treated as a jump to the end of the loop body, and "intervening"
 *  finally clauses are treated as follows: V is DA "due to the
 *  continue" iff V is DA before the continue statement or V is DA at
 *  the end of any intervening finally block.  V is DU "due to the
 *  continue" iff any intervening finally cannot complete normally or V
 *  is DU at the end of every intervening finally block.  This "due to
 *  the continue" concept is then used in the spec for the loops.
 *
 *  <p>Similarly, break statements must consider intervening finally
 *  blocks.  For liveness analysis, a break statement for which any
 *  intervening finally cannot complete normally is not considered to
 *  cause the target statement to be able to complete normally. Then
 *  we say V is DA "due to the break" iff V is DA before the break or
 *  V is DA at the end of any intervening finally block.  V is DU "due
 *  to the break" iff any intervening finally cannot complete normally
 *  or V is DU at the break and at the end of every intervening
 *  finally block.  (I suspect this latter condition can be
 *  simplified.)  This "due to the break" is then used in the spec for
 *  all statements that can be "broken".
 *
 *  <p>The return statement is treated similarly.  V is DA "due to a
 *  return statement" iff V is DA before the return statement or V is
 *  DA at the end of any intervening finally block.  Note that we
 *  don't have to worry about the return expression because this
 *  concept is only used for construcrors.
 *
 *  <p>There is no spec in the JLS for when a variable is definitely
 *  assigned at the end of a constructor, which is needed for final
 *  fields (8.3.1.2).  We implement the rule that V is DA at the end
 *  of the constructor iff it is DA and the end of the body of the
 *  constructor and V is DA "due to" every return of the constructor.
 *
 *  <p>Intervening finally blocks similarly affect exception analysis.  An
 *  intervening finally that cannot complete normally allows us to ignore
 *  an otherwise uncaught exception.
 *
 *  <p>To implement the semantics of intervening finally clauses, all
 *  nonlocal transfers (break, continue, return, throw, method call that
 *  can throw a checked exception, and a constructor invocation that can
 *  thrown a checked exception) are recorded in a queue, and removed
 *  from the queue when we complete processing the target of the
 *  nonlocal transfer.  This allows us to modify the queue in accordance
 *  with the above rules when we encounter a finally clause.  The only
 *  exception to this [no pun intended] is that checked exceptions that
 *  are known to be caught or declared to be caught in the enclosing
 *  method are not recorded in the queue, but instead are recorded in a
 *  global variable "Set<Type> thrown" that records the type of all
 *  exceptions that can be thrown.
 *
 *  <p>Other minor issues the treatment of members of other classes
 *  (always considered DA except that within an anonymous class
 *  constructor, where DA status from the enclosing scope is
 *  preserved), treatment of the case expression (V is DA before the
 *  case expression iff V is DA after the switch expression),
 *  treatment of variables declared in a switch block (the implied
 *  DA/DU status after the switch expression is DU and not DA for
 *  variables defined in a switch block), the treatment of boolean ?:
 *  expressions (The JLS rules only handle b and c non-boolean; the
 *  new rule is that if b and c are boolean valued, then V is
 *  (un)assigned after a?b:c when true/false iff V is (un)assigned
 *  after b when true/false and V is (un)assigned after c when
 *  true/false).
 *
 *  <p>There is the remaining question of what syntactic forms constitute a
 *  reference to a variable.  It is conventional to allow this.x on the
 *  left-hand-side to initialize a final instance field named x, yet
 *  this.x isn't considered a "use" when appearing on a right-hand-side
 *  in most implementations.  Should parentheses affect what is
 *  considered a variable reference?  The simplest rule would be to
 *  allow unqualified forms only, parentheses optional, and phase out
 *  support for assigning to a final field via this.x.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
// 变量赋值检查、语句活跃性分析及异常检查
public class Flow extends TreeScanner {
    protected static final Context.Key<Flow> flowKey =
        new Context.Key<Flow>();

    private final Names names;
    private final Log log;
    private final Symtab syms;
    private final Types types;
    private final Check chk;
    private       TreeMaker make;
    private final Resolve rs;
    private Env<AttrContext> attrEnv;
    private       Lint lint;
    private final boolean allowImprovedRethrowAnalysis;
    private final boolean allowImprovedCatchAnalysis;

    public static Flow instance(Context context) {
        Flow instance = context.get(flowKey);
        if (instance == null)
            instance = new Flow(context);
        return instance;
    }

    protected Flow(Context context) {
        context.put(flowKey, this);
        names = Names.instance(context);
        log = Log.instance(context);
        syms = Symtab.instance(context);
        types = Types.instance(context);
        chk = Check.instance(context);
        lint = Lint.instance(context);
        rs = Resolve.instance(context);
        Source source = Source.instance(context);
        allowImprovedRethrowAnalysis = source.allowImprovedRethrowAnalysis();
        allowImprovedCatchAnalysis = source.allowImprovedCatchAnalysis();
    }

    /** A flag that indicates whether the last statement could
     *  complete normally.
     */
    // 语句的活跃性是指这个语句是否有可能被执行，或者说语句是否可达。
    // 表示语句是否活跃
    // 一般在分析break、continue、return或throw等语句时会将alive的值更新为false
    private boolean alive;

    /** The set of definitely assigned variables.
     */
    // 有初始化，0代表没有初始化，1代表初始化
    // 由于程序中可能需要同时对多个变量赋值状态进行跟踪，所以将inits与unints变量声明为Bits类型。
    // Bits类可以进行位操作，也就是说两个状态变量可以通过某个相同位置上的位来共同表示某个变量初始化的情况，
    // 状态变量
    // 例14-6
    Bits inits; //inits内部数组最终结构int[]{0,1}，代表第一个变量没有初始化，第二个变量初始化了

    /** The set of definitely unassigned variables.
     */
    // 没有初始化，0代表初始化，1代表没有初始化
    // 状态变量
    // 例14-6
    // uninits中的值表示的是明确非赋值状态，主要用于final变量的检查
    Bits uninits;// uninits内部数组最终结构int[]{0,1}, 代表第一个变量初始化，第二个变量没有初始化

    // key为Symbol对象，对应的就是catch语句中定义的形式参数，
    // 而value为List<Type>列表，其中保存着所有可能重抛的异常类型
    HashMap<Symbol, List<Type>> preciseRethrowTypes;

    /** The set of variables that are definitely unassigned everywhere
     *  in current try block. This variable is maintained lazily; it is
     *  updated only when something gets removed from uninits,
     *  typically by being assigned in reachable code.  To obtain the
     *  correct set of variables which are definitely unassigned
     *  anywhere in current try block, intersect uninitsTry and
     *  uninits.
     */
    Bits uninitsTry;

    /** When analyzing a condition, inits and uninits are null.
     *  Instead we have:
     *  条件语句数据流分析
     */
    // 分析if语句的body体内的语句时使用initsWhenTrue与uninitsWhenTrue，
    // 分析else语句的body体内的语句时使用initsWhenFalse与uninitsWhenFalse。
    // 条件表达式结果为TRUE
    Bits initsWhenTrue;
    // 条件表达式结果为FALSE
    Bits initsWhenFalse;
    // 条件表达式结果为TRUE
    Bits uninitsWhenTrue;
    // 条件表达式结果为FALSE
    Bits uninitsWhenFalse;

    /*
    局部变量在使用前必须进行显式初始化，而声明在类型中的成员变量，Java虚拟机会默认初始化为对应的0值。
    但是有一种特殊情况就是，final修饰的成员变量必须显式初始化，可以在定义变量时也可以在构造方法中进行初始化
     */
    /** A mapping from addresses to variable symbols.
     */
    // 在进行变量赋值检查时，首先要将需要进行变量赋值检查的成员变量与局部变量存储起来
    // vars数组保存程序中已经声明的变量
    VarSymbol[] vars;

    /** The current class being defined.
     */
    JCClassDecl classDef;

    /** The first variable sequence number in this class definition.
     */
    // firstadr保存相关作用域内声明的第一个变量的位置
    int firstadr;

    /** The next available variable sequence number.
     */
    // 而nextadr保存vars中下一个可用的存储位置,由于保存的是数组下标，所以这个值通常是从0开始递增的
    int nextadr;

    /** The list of possibly thrown declarable exceptions.
     */
    // thrown列表保存了可能抛出的异常
    // 例14-21
    List<Type> thrown;

    /** The list of exceptions that are either caught or declared to be
     *  thrown.
     */
    // caught列表保存了可以捕获的或者在方法上声明抛出的异常
    // 例14-21
    List<Type> caught;

    /** The list of unreferenced automatic resources.
     */
    Scope unrefdResources;

    /** Set when processing a loop body the second time for DU analysis. */
    // loopPassTwo在do-while循环执行第2次时会将值设置为true，
    // 由于do-while循环对for语句进行了数据流分析，
    // 所以如果执行第2次do-while循环时，可以理解为要分析的for语句也要循环执行2次或多次。
    // 对于数据流分析来说，最多执行2次循环就可以找出所有的编译错误
    boolean loopPassTwo = false;

    /*-------------------- Environments ----------------------*/

    /** A pending exit.  These are the statements return, break, and
     *  continue.  In addition, exception-throwing expressions or
     *  statements are put here when not known to be caught.  This
     *  will typically result in an error unless it is within a
     *  try-finally whose finally block cannot complete normally.
     */
    static class PendingExit {
        JCTree tree;
        Bits inits;
        Bits uninits;
        Type thrown;
        PendingExit(JCTree tree, Bits inits, Bits uninits) {
            this.tree = tree;
            this.inits = inits.dup();
            this.uninits = uninits.dup();
        }
        PendingExit(JCTree tree, Type thrown) {
            this.tree = tree;
            this.thrown = thrown;
        }
    }

    /** The currently pending exits that go from current inner blocks
     *  to an enclosing block, in source order.
     */
    // 当循环语句中有continue和break等进行流程跳转的语句时，需要pendingExits辅助进行数据流分析
    // pendingExit保存执行中断语句的这条路径上的inits与uninits变量的值
    ListBuffer<PendingExit> pendingExits;

    /*-------------------- Exceptions ----------------------*/

    /** Complain that pending exceptions are not caught.
     */
    void errorUncaught() {
        // 当pendingExits列表中有值时会报错
        for (PendingExit exit = pendingExits.next();
             exit != null;
             exit = pendingExits.next()) {
            if (classDef != null &&
                classDef.pos == exit.tree.pos) {
                log.error(exit.tree.pos(),
                        "unreported.exception.default.constructor",
                        exit.thrown);
            } else if (exit.tree.getTag() == JCTree.VARDEF &&
                    ((JCVariableDecl)exit.tree).sym.isResourceVariable()) {
                log.error(exit.tree.pos(),
                        "unreported.exception.implicit.close",
                        exit.thrown,
                        ((JCVariableDecl)exit.tree).sym.name);
            } else {
                log.error(exit.tree.pos(),
                        "unreported.exception.need.to.catch.or.throw",
                        exit.thrown);
            }
        }
    }

    /** Record that exception is potentially thrown and check that it
     *  is caught.
     */
    // 记录可能引发的异常并检查它是否被捕获
    void markThrown(JCTree tree, Type exc) {
        if (!chk.isUnchecked(tree.pos(), exc)) { // 受检查异常
            if (!chk.isHandled(exc, caught)) // 没有被处理的异常
                // 具体就是创建一个PendingExit对象并追加到pendingExits列表中
                pendingExits.append(new PendingExit(tree, exc));
            // 如果exc是受检查异常，则调用chk.incl()方法保存到thrown列表中
            thrown = chk.incl(exc, thrown);
        }
    }

    /*-------------- Processing variables ----------------------*/

    /** Do we need to track init/uninit state of this symbol?
     *  I.e. is symbol either a local or a blank final variable?
     */
    // 调用trackable()方法来判断有没有必要对变量进行赋值检查
    // 返回true，表示需要检查变量的赋值状态
    // 例14-6
    boolean trackable(VarSymbol sym) {
        // trackable()方法中的局部变量或形式参数都需要检查，
        // 表示这些变量的VarSymbol对象的owner都是方法
        return
            (sym.owner.kind == MTH ||
             ((sym.flags() & (FINAL | HASINIT | PARAMETER)) == FINAL &&
              classDef.sym.isEnclosedBy((ClassSymbol)sym.owner)));
    }

    /** Initialize new trackable variable by setting its address field
     *  to the next available sequence number and entering it under that
     *  index into the vars array.
     */
    // 将变量对应的VarSymbol输入到vars数组中
    void newVar(VarSymbol sym) {
        if (nextadr == vars.length) { // 扩容操作
            // 当nextadr的值等于vars数组的大小时进行扩容，因为vars数组已经没有剩余的存储空间了，将vars数组容量扩大一倍后，
            // 调用System.arraycopy()方法将原数组内容复制到新数组并更新vars。
            VarSymbol[] newvars = new VarSymbol[nextadr * 2];
            System.arraycopy(vars, 0, newvars, 0, nextadr);
            vars = newvars;
        }
        // 将需要进行赋值检查的sym保存到vars数组中，为了能找到vars数组中保存的sym，
        // 将保存sym的数组下标nextadr的值保存到Symbol类中定义的adr变量中。
        sym.adr = nextadr;
        vars[nextadr] = sym; // 将需要进行赋值检查的变量保存到vars数组中
        inits.excl(nextadr);
        uninits.incl(nextadr);
        nextadr++;
    }

    /** Record an initialization of a trackable variable.
     */
    // letInit()方法的实现有些复杂，尤其是对final变量进行了很多检查，
    // 因为final变量如果没有明确初始化或者多次初始化都会引起错误
    void letInit(DiagnosticPosition pos, VarSymbol sym) {
        if (sym.adr >= firstadr && trackable(sym)) {
            if ((sym.flags() & FINAL) != 0) {
                if ((sym.flags() & PARAMETER) != 0) {
                    // 对catch语句中声明的形式参数进行赋值操作
                    // 对形式参数的检查，不能对catch语句中声明的形式参数进行赋值操作
                    // 错误形式： catch(Exception e = new Exception())
                    if ((sym.flags() & UNION) != 0) { //multi-catch parameter
                        log.error(pos, "multicatch.parameter.may.not.be.assigned",
                                  sym);
                    }
                    else {
                        log.error(pos, "final.parameter.may.not.be.assigned",
                              sym);
                    }
                } else if (!uninits.isMember(sym.adr)) {
                    // 对没有明确非初始化的final变量进行初始化
                    // 不能对没有明确非初始化的final变量进行初始化，
                    // 也就是调用letInit()方法可能会导致final变量重复初始化
                    log.error(pos,
                              loopPassTwo
                              ? "var.might.be.assigned.in.loop"
                              : "var.might.already.be.assigned",
                              sym);
                } else if (!inits.isMember(sym.adr)) {
                    // 当变量没有明确初始化时，更新uninits与inits的值
                    // 当final变量不是形式参数并且明确未初始化时，此时调用inits.isMember()方法将返回false，
                    // 表明这个变量能够进行初始化，将uninits与uninitsTry中相应的位的状态设置为0，
                    // 将inits中相应的位的状态设置为1，uninitsTry辅助进行try语句中变量的赋值状态检查，
                    uninits.excl(sym.adr);
                    uninitsTry.excl(sym.adr);
                } else {
                    // 最后对不可达的final变量也进行了初始化，将uninits中相应的位设置为0，这样下次如果重复初始化就会报错，这是对程序错误的一种兼容处理。
                    uninits.excl(sym.adr);
                }
            }
            inits.incl(sym.adr);
        } else if ((sym.flags() & FINAL) != 0) { // 多次对final变量进行初始化
            log.error(pos, "var.might.already.be.assigned", sym);
        }
    }

    /** If tree is either a simple name or of the form this.name or
     *  C.this.name, and tree represents a trackable variable,
     *  record an initialization of the variable.
     */
    void letInit(JCTree tree) {
        tree = TreeInfo.skipParens(tree);
        if (tree.getTag() == JCTree.IDENT || tree.getTag() == JCTree.SELECT) {
            Symbol sym = TreeInfo.symbol(tree);
            if (sym.kind == VAR) {
                letInit(tree.pos(), (VarSymbol)sym);
            }
        }
    }

    /** Check that trackable variable is initialized.
     */
    // 校验变量是否被初始化
    void checkInit(DiagnosticPosition pos, VarSymbol sym) {
        if ((sym.adr >= firstadr || sym.owner.kind != TYP) &&
            trackable(sym) &&
            !inits.isMember(sym.adr)) {
            log.error(pos, "var.might.not.have.been.initialized",
                      sym);
            inits.incl(sym.adr);
        }
    }

    /*-------------------- Handling jumps ----------------------*/

    /** Record an outward transfer of control. */
    void recordExit(JCTree tree) {
        // 分支中断语句辅助分析
        pendingExits.append(new PendingExit(tree, inits, uninits));
        // 调用markDead()将alive的值设置为false，表示后续语句不可达
        markDead();
    }

    /** Resolve all breaks of this statement. */
    // 和resolveContinues()相似
    // 例14-19
    boolean resolveBreaks(JCTree tree,
                          ListBuffer<PendingExit> oldPendingExits) {
        boolean result = false;
        List<PendingExit> exits = pendingExits.toList();
        // 这次合并了含有break语句的可执行路径上的状态变量
        pendingExits = oldPendingExits;
        for (; exits.nonEmpty(); exits = exits.tail) {
            PendingExit exit = exits.head;
            if (exit.tree.getTag() == JCTree.BREAK &&
                ((JCBreak) exit.tree).target == tree) {
                inits.andSet(exit.inits);
                uninits.andSet(exit.uninits);
                result = true;
            } else {
                pendingExits.append(exit);
            }
        }
        return result;
    }

    /** Resolve all continues of this statement. */
    // 处理continue语句
    // 例14-18
    boolean resolveContinues(JCTree tree) {
        // 参数tree为for语句对应的语法树节点
        boolean result = false;
        List<PendingExit> exits = pendingExits.toList();
        // 跳转目标为tree的PendingExit对象
        pendingExits = new ListBuffer<PendingExit>();
        for (; exits.nonEmpty(); exits = exits.tail) {
            PendingExit exit = exits.head;
            // 循环查找exit.tree为JCContinue树节点
            if (exit.tree.getTag() == JCTree.CONTINUE &&
                ((JCContinue) exit.tree).target == tree) {
                // 有满足条件的exit，就会对exit所代表的执行路径上的状态变量与inits与uninits进行合并
                inits.andSet(exit.inits);
                uninits.andSet(exit.uninits);
                result = true;
            } else {
                pendingExits.append(exit);
            }
        }
        return result;
    }

    /** Record that statement is unreachable.
     */
    void markDead() {
        inits.inclRange(firstadr, nextadr);
        uninits.inclRange(firstadr, nextadr);
        alive = false;
    }

    /** Split (duplicate) inits/uninits into WhenTrue/WhenFalse sets
     */
    void split(boolean setToNull) {
        initsWhenFalse = inits.dup();
        uninitsWhenFalse = uninits.dup();
        initsWhenTrue = inits;
        uninitsWhenTrue = uninits;
        if (setToNull)
            inits = uninits = null;
    }

    /** Merge (intersect) inits/uninits from WhenTrue/WhenFalse sets.
     */
    void merge() {
        inits = initsWhenFalse.andSet(initsWhenTrue);
        uninits = uninitsWhenFalse.andSet(uninitsWhenTrue);
    }

/* ************************************************************************
 * Visitor methods for statements and definitions
 *************************************************************************/

    /** Analyze a definition.
     */
    // 遍历定义
    // 通常用来遍历匿名块
    void scanDef(JCTree tree) {
        // 调用scanStat()方法对匿名块进行扫描
        scanStat(tree);
        // 如果tree为匿名块并且alive的值为false时，Javac将报错
        /*
        class Test {
           {
                throw new RuntimeException();// 报错，初始化程序必须能够正常完成
            }
        }
         */
        if (tree != null && tree.getTag() == JCTree.BLOCK && !alive) {
            log.error(tree.pos(),
                      "initializer.must.be.able.to.complete.normally");
        }
    }

    /** Analyze a statement. Check that statement is reachable.
     */
    // 遍历表达式
    void scanStat(JCTree tree) {
        // 当alive的值为false并且当前还有要执行的语句时会报错
        if (!alive && tree != null) {
            log.error(tree.pos(), "unreachable.stmt");
            // 如果当前要执行的语句为非JCSkip时，会将alive的值更新为true
            if (tree.getTag() != JCTree.SKIP)
                alive = true;
            // 如果当前要执行的语句为JCSkip时，不会将alive的值更新为true，
            // 因为这种语句没有执行的逻辑，直接忽略即可。
        }
        scan(tree);
    }

    /** Analyze list of statements.
     */
    void scanStats(List<? extends JCStatement> trees) {
        if (trees != null)
            for (List<? extends JCStatement> l = trees; l.nonEmpty(); l = l.tail)
                scanStat(l.head);
    }

    /** Analyze an expression. Make sure to set (un)inits rather than
     *  (un)initsWhenTrue(WhenFalse) on exit.
     */
    // 遍历语句
    void scanExpr(JCTree tree) {
        if (tree != null) {
            scan(tree);
            if (inits == null)
                merge();
        }
    }

    /** Analyze a list of expressions.
     */
    void scanExprs(List<? extends JCExpression> trees) {
        if (trees != null)
            for (List<? extends JCExpression> l = trees; l.nonEmpty(); l = l.tail)
                scanExpr(l.head);
    }

    /** Analyze a condition. Make sure to set (un)initsWhenTrue(WhenFalse)
     *  rather than (un)inits on exit.
     */
    // 遍历表达式
    void scanCond(JCTree tree) {
        if (tree.type.isFalse()) {
            // 例14-11
            // 条件判断表达式的结果为布尔常量false
            // 调用tree.type.isFalse()方法返回true时，表示条件判断表达式的结果是布尔常量false，
            // 对于if语句来说，if分支下的语句将永远得不到执行，最终的变量赋值情况要看else分支下语句的执行情况
            if (inits == null)
                merge();
            initsWhenTrue = inits.dup();
            initsWhenTrue.inclRange(firstadr, nextadr);
            uninitsWhenTrue = uninits.dup();
            uninitsWhenTrue.inclRange(firstadr, nextadr);
            initsWhenFalse = inits;
            uninitsWhenFalse = uninits;
        } else if (tree.type.isTrue()) {
            // 条件判断表达式的结果为布尔常量true
            // 表示条件表达式结果布尔常量true,else分支将永远得不到执行
            if (inits == null)
                merge();
            initsWhenFalse = inits.dup();
            initsWhenFalse.inclRange(firstadr, nextadr);
            uninitsWhenFalse = uninits.dup();
            uninitsWhenFalse.inclRange(firstadr, nextadr);
            initsWhenTrue = inits;
            uninitsWhenTrue = uninits;
        } else {
            scan(tree);
            if (inits != null)
                split(tree.type != syms.unknownType);
        }
        if (tree.type != syms.unknownType)
            inits = uninits = null;
    }

    /* ------------ Visitor methods for various sorts of trees -------------*/

    public void visitClassDef(JCClassDecl tree) {
        if (tree.sym == null) return;

        JCClassDecl classDefPrev = classDef;
        List<Type> thrownPrev = thrown;
        List<Type> caughtPrev = caught;
        boolean alivePrev = alive;
        int firstadrPrev = firstadr;
        int nextadrPrev = nextadr;
        ListBuffer<PendingExit> pendingExitsPrev = pendingExits;
        Lint lintPrev = lint;

        pendingExits = new ListBuffer<PendingExit>();
        if (tree.name != names.empty) {
            caught = List.nil();
            firstadr = nextadr;
        }
        classDef = tree;
        thrown = List.nil();
        lint = lint.augment(tree.sym.attributes_field);

        try {
            // define all the static fields
            for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                if (l.head.getTag() == JCTree.VARDEF) {
                    JCVariableDecl def = (JCVariableDecl)l.head;
                    if ((def.mods.flags & STATIC) != 0) {
                        VarSymbol sym = def.sym;
                        if (trackable(sym))
                            newVar(sym);
                    }
                }
            }

            // process all the static initializers
            for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                if (l.head.getTag() != JCTree.METHODDEF &&
                    (TreeInfo.flags(l.head) & STATIC) != 0) {
                    scanDef(l.head);
                    errorUncaught();
                }
            }

            // add intersection of all thrown clauses of initial constructors
            // to set of caught exceptions, unless class is anonymous.
            if (tree.name != names.empty) {
                boolean firstConstructor = true;
                for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                    if (TreeInfo.isInitialConstructor(l.head)) {
                        List<Type> mthrown =
                            ((JCMethodDecl) l.head).sym.type.getThrownTypes();
                        if (firstConstructor) {
                            caught = mthrown;
                            firstConstructor = false;
                        } else {
                            caught = chk.intersect(mthrown, caught);
                        }
                    }
                }
            }

            // define all the instance fields
            for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                if (l.head.getTag() == JCTree.VARDEF) {
                    JCVariableDecl def = (JCVariableDecl)l.head;
                    if ((def.mods.flags & STATIC) == 0) {
                        VarSymbol sym = def.sym;
                        if (trackable(sym))
                            newVar(sym);
                    }
                }
            }

            // process all the instance initializers
            for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                if (l.head.getTag() != JCTree.METHODDEF &&
                    (TreeInfo.flags(l.head) & STATIC) == 0) {
                    scanDef(l.head);
                    errorUncaught();
                }
            }

            // in an anonymous class, add the set of thrown exceptions to
            // the throws clause of the synthetic constructor and propagate
            // outwards.
            // Changing the throws clause on the fly is okay here because
            // the anonymous constructor can't be invoked anywhere else,
            // and its type hasn't been cached.
            if (tree.name == names.empty) {
                for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                    if (TreeInfo.isInitialConstructor(l.head)) {
                        JCMethodDecl mdef = (JCMethodDecl)l.head;
                        mdef.thrown = make.Types(thrown);
                        mdef.sym.type = types.createMethodTypeWithThrown(mdef.sym.type, thrown);
                    }
                }
                thrownPrev = chk.union(thrown, thrownPrev);
            }

            // 分析类中的全部方法
            for (List<JCTree> l = tree.defs; l.nonEmpty(); l = l.tail) {
                if (l.head.getTag() == JCTree.METHODDEF) {
                    scan(l.head);
                    // 当pendingExits列表中有值时会报错
                    errorUncaught();
                }
            }

            thrown = thrownPrev;
        } finally {
            pendingExits = pendingExitsPrev;
            alive = alivePrev;
            nextadr = nextadrPrev;
            firstadr = firstadrPrev;
            caught = caughtPrev;
            classDef = classDefPrev;
            lint = lintPrev;
        }
    }

    public void visitMethodDef(JCMethodDecl tree) {
        if (tree.body == null) return;

        List<Type> caughtPrev = caught;
        List<Type> mthrown = tree.sym.type.getThrownTypes();
        Bits initsPrev = inits.dup();
        Bits uninitsPrev = uninits.dup();
        int nextadrPrev = nextadr;
        int firstadrPrev = firstadr;
        Lint lintPrev = lint;

        lint = lint.augment(tree.sym.attributes_field);

        Assert.check(pendingExits.isEmpty());

        try {
            // TreeInfo.isInitialConstructor()方法判断构造方法中的第一个语句是否为this(...)这样的形式，
            // 也就是是否调用了其他构造方法，如果是则返回false。
            boolean isInitialConstructor =
                TreeInfo.isInitialConstructor(tree);

            if (!isInitialConstructor)
                firstadr = nextadr;
            for (List<JCVariableDecl> l = tree.params; l.nonEmpty(); l = l.tail) {
                JCVariableDecl def = l.head;
                scan(def);
                inits.incl(def.sym.adr);
                uninits.excl(def.sym.adr);
            }
            if (isInitialConstructor)
                caught = chk.union(caught, mthrown);
            else if ((tree.sym.flags() & (BLOCK | STATIC)) != BLOCK)
                caught = mthrown;
            // else we are in an instance initializer block;
            // leave caught unchanged.

            alive = true;
            scanStat(tree.body);

            if (alive && tree.sym.type.getReturnType().tag != VOID)
                log.error(TreeInfo.diagEndPos(tree.body), "missing.ret.stmt");

            if (isInitialConstructor) {
                for (int i = firstadr; i < nextadr; i++)
                    if (vars[i].owner == classDef.sym)
                        checkInit(TreeInfo.diagEndPos(tree.body), vars[i]);
            }
            //
            List<PendingExit> exits = pendingExits.toList();
            pendingExits = new ListBuffer<PendingExit>();
            while (exits.nonEmpty()) {
                PendingExit exit = exits.head;
                exits = exits.tail;
                if (exit.thrown == null) {
                    Assert.check(exit.tree.getTag() == JCTree.RETURN);
                    // 当要分析的方法中有返回语句return时
                    // 如果isInitialConstructor的值为true，也就是构造方法不以this(...)形式的语句开头
                    if (isInitialConstructor) {
                        inits = exit.inits;
                        for (int i = firstadr; i < nextadr; i++)
                            // 要调用checkInit()方法根据exit.inits检查作用域有效范围内的变量初始化情况
                            checkInit(exit.tree.pos(), vars[i]);
                    }
                } else {
                    // uncaught throws will be reported later
                    pendingExits.append(exit);
                }
            }
        } finally {
            inits = initsPrev;
            uninits = uninitsPrev;
            nextadr = nextadrPrev;
            firstadr = firstadrPrev;
            caught = caughtPrev;
            lint = lintPrev;
        }
    }

    public void visitVarDef(JCVariableDecl tree) {
        boolean track = trackable(tree.sym);
        if (track && tree.sym.owner.kind == MTH) newVar(tree.sym);
        if (tree.init != null) {
            Lint lintPrev = lint;
            lint = lint.augment(tree.sym.attributes_field);
            try{
                scanExpr(tree.init);
                if (track) letInit(tree.pos(), tree.sym);
            } finally {
                lint = lintPrev;
            }
        }
    }

    public void visitBlock(JCBlock tree) {
        int nextadrPrev = nextadr;
        scanStats(tree.stats);
        nextadr = nextadrPrev;
    }

    public void visitDoLoop(JCDoWhileLoop tree) {
        ListBuffer<PendingExit> prevPendingExits = pendingExits;
        boolean prevLoopPassTwo = loopPassTwo;
        pendingExits = new ListBuffer<PendingExit>();
        int prevErrors = log.nerrors;
        do {
            Bits uninitsEntry = uninits.dup();
            uninitsEntry.excludeFrom(nextadr);
            scanStat(tree.body);
            alive |= resolveContinues(tree);
            scanCond(tree.cond);
            if (log.nerrors !=  prevErrors ||
                loopPassTwo ||
                uninitsEntry.dup().diffSet(uninitsWhenTrue).nextBit(firstadr)==-1)
                break;
            inits = initsWhenTrue;
            uninits = uninitsEntry.andSet(uninitsWhenTrue);
            loopPassTwo = true;
            alive = true;
        } while (true);
        loopPassTwo = prevLoopPassTwo;
        inits = initsWhenFalse;
        uninits = uninitsWhenFalse;
        alive = alive && !tree.cond.type.isTrue();
        alive |= resolveBreaks(tree, prevPendingExits);
    }

    public void visitWhileLoop(JCWhileLoop tree) {
        ListBuffer<PendingExit> prevPendingExits = pendingExits;
        boolean prevLoopPassTwo = loopPassTwo;
        Bits initsCond;
        Bits uninitsCond;
        pendingExits = new ListBuffer<PendingExit>();
        int prevErrors = log.nerrors;
        do {
            Bits uninitsEntry = uninits.dup();
            uninitsEntry.excludeFrom(nextadr);
            scanCond(tree.cond);
            initsCond = initsWhenFalse;
            uninitsCond = uninitsWhenFalse;
            inits = initsWhenTrue;
            uninits = uninitsWhenTrue;
            alive = !tree.cond.type.isFalse();
            scanStat(tree.body);
            alive |= resolveContinues(tree);
            if (log.nerrors != prevErrors ||
                loopPassTwo ||
                uninitsEntry.dup().diffSet(uninits).nextBit(firstadr) == -1)
                break;
            uninits = uninitsEntry.andSet(uninits);
            loopPassTwo = true;
            alive = true;
        } while (true);
        loopPassTwo = prevLoopPassTwo;
        inits = initsCond;
        uninits = uninitsCond;
        alive = resolveBreaks(tree, prevPendingExits) ||
            !tree.cond.type.isTrue();
    }

    // 访问for循环语句，检查语句的活跃性及变量赋值状态
    public void visitForLoop(JCForLoop tree) {
        // 局部变量保存pendingExits、loopPassTwo与nextadr成员变量
        ListBuffer<PendingExit> prevPendingExits = pendingExits;
        boolean prevLoopPassTwo = loopPassTwo;
        int nextadrPrev = nextadr;
        scanStats(tree.init);
        // initsCond与uninitsCond保存的是假设运行了tree.body与tree.step后的变量状态，
        // 将这个状态作为处理for语句后的inits与uninits值没有办法检测出更多的错误
        // 例14-16
        Bits initsCond;
        Bits uninitsCond;
        pendingExits = new ListBuffer<PendingExit>();
        int prevErrors = log.nerrors;
        // 假设do-while循环只循环执行1次，也就是do-while语句在首次循环进行数据流分析时就发现了编译错误，
        // 或者要分析的for语句没有操作final变量，则直接跳出
        do {
            // do-while循环首先使用局部变量uninitsEntry保存uninits的值，
            // uninitsEntry可以辅助检查要分析的for语句中的tree.cond、
            // tree.body和tree.step中有没有对final变量进行操作
            Bits uninitsEntry = uninits.dup();
            uninitsEntry.excludeFrom(nextadr);

            if (tree.cond != null) {
                // 如果tree.cond不为空，在分析tree.body之前，需要将inits与uninints初始化为initsWhenTrue与uninitsWhenTrue，
                // 因为只有条件判断表达式的结果为true时才会执行tree.body
                scanCond(tree.cond);
                // initsCond与uninitsCond分别被初始化为initsWhenFalse与uninitsWhenFalse
                // initsCond与uninitsCond在do-while循环执行完成之后将值赋值给了inits与unints，
                // 也就是说，initsWhenFalse与uninitsWhenFalse是执行for语句后续语句时使用的状态变量。
                // 当tree.cond的结果不为常量值false时，tree.body中的语句可能被执行，alive的值为true。
                initsCond = initsWhenFalse;
                uninitsCond = uninitsWhenFalse;
                inits = initsWhenTrue;
                uninits = uninitsWhenTrue;
                alive = !tree.cond.type.isFalse();
            } else {
                // 如果tree.cond为空，情况等价于条件判断表达式为常量值true，
                initsCond = inits.dup();
                // 调用inclRange()方法将有效范围内的initsCond与uninitsCond的状态位全部设置为1，
                // 所以最终的变量状态要看break等的操作结果
                // 也就是调用resolveBreaks()方法会再次操作inits与uninits。
                // tree.body中的语句一定会执行，alive的值为true。
                initsCond.inclRange(firstadr, nextadr);
                uninitsCond = uninits.dup();
                uninitsCond.inclRange(firstadr, nextadr);
                alive = true;
            }
            // 分析body
            scanStat(tree.body);
            // 调用resolveContinues()方法处理循环中的continue语句
            // 例14-17
            alive |= resolveContinues(tree);
            // 分析step
            scan(tree.step);
            // 如果发现了编译错误log.nerrors != prevErrors返回TRUE
            if (log.nerrors != prevErrors ||
                loopPassTwo ||
                    // 如果要分析的for语句没有操作final变量
                    // uninitsEntry.dup().diffSet(uninits).nextBit(firstadr) == -1 返回TRUE
                    // 例14-13/14-14
                    // 没有final变量，就跳出do-while循环
                uninitsEntry.dup().diffSet(uninits).nextBit(firstadr) == -1)
                break;
            // 有final变量，最终会与unints取交集
            uninits = uninitsEntry.andSet(uninits);
            // 假设do-while循环执行第2次循环，此时loopPassTwo的值为true，final变量重复赋值的错误会被检查出来
            // 例14-15
            loopPassTwo = true;
            alive = true;
        } while (true);
        loopPassTwo = prevLoopPassTwo;
        inits = initsCond;
        uninits = uninitsCond;
        // 处理break语句
        // 如果for语句的body体中有break语句或者条件不是永恒为true时，for语句之后的语句就有可能被执行，alive的值为true。
        alive = resolveBreaks(tree, prevPendingExits) ||
            tree.cond != null && !tree.cond.type.isTrue();
        nextadr = nextadrPrev;
    }

    public void visitForeachLoop(JCEnhancedForLoop tree) {
        visitVarDef(tree.var);

        ListBuffer<PendingExit> prevPendingExits = pendingExits;
        boolean prevLoopPassTwo = loopPassTwo;
        int nextadrPrev = nextadr;
        scan(tree.expr);
        Bits initsStart = inits.dup();
        Bits uninitsStart = uninits.dup();

        letInit(tree.pos(), tree.var.sym);
        pendingExits = new ListBuffer<PendingExit>();
        int prevErrors = log.nerrors;
        do {
            Bits uninitsEntry = uninits.dup();
            uninitsEntry.excludeFrom(nextadr);
            scanStat(tree.body);
            alive |= resolveContinues(tree);
            if (log.nerrors != prevErrors ||
                loopPassTwo ||
                uninitsEntry.dup().diffSet(uninits).nextBit(firstadr) == -1)
                break;
            uninits = uninitsEntry.andSet(uninits);
            loopPassTwo = true;
            alive = true;
        } while (true);
        loopPassTwo = prevLoopPassTwo;
        inits = initsStart;
        uninits = uninitsStart.andSet(uninits);
        resolveBreaks(tree, prevPendingExits);
        alive = true;
        nextadr = nextadrPrev;
    }

    public void visitLabelled(JCLabeledStatement tree) {
        ListBuffer<PendingExit> prevPendingExits = pendingExits;
        pendingExits = new ListBuffer<PendingExit>();
        scanStat(tree.body);
        alive |= resolveBreaks(tree, prevPendingExits);
    }

    public void visitSwitch(JCSwitch tree) {
        ListBuffer<PendingExit> prevPendingExits = pendingExits;
        pendingExits = new ListBuffer<PendingExit>();
        int nextadrPrev = nextadr;
        scanExpr(tree.selector);
        Bits initsSwitch = inits;
        Bits uninitsSwitch = uninits.dup();
        boolean hasDefault = false;
        for (List<JCCase> l = tree.cases; l.nonEmpty(); l = l.tail) {
            alive = true;
            inits = initsSwitch.dup();
            uninits = uninits.andSet(uninitsSwitch);
            JCCase c = l.head;
            if (c.pat == null)
                hasDefault = true;
            else
                scanExpr(c.pat);
            scanStats(c.stats);
            addVars(c.stats, initsSwitch, uninitsSwitch);
            // Warn about fall-through if lint switch fallthrough enabled.
            if (!loopPassTwo &&
                alive &&
                lint.isEnabled(Lint.LintCategory.FALLTHROUGH) &&
                c.stats.nonEmpty() && l.tail.nonEmpty())
                log.warning(Lint.LintCategory.FALLTHROUGH,
                            l.tail.head.pos(),
                            "possible.fall-through.into.case");
        }
        if (!hasDefault) {
            inits.andSet(initsSwitch);
            alive = true;
        }
        alive |= resolveBreaks(tree, prevPendingExits);
        nextadr = nextadrPrev;
    }
    // where
        /** Add any variables defined in stats to inits and uninits. */
        private static void addVars(List<JCStatement> stats, Bits inits,
                                    Bits uninits) {
            for (;stats.nonEmpty(); stats = stats.tail) {
                JCTree stat = stats.head;
                if (stat.getTag() == JCTree.VARDEF) {
                    int adr = ((JCVariableDecl) stat).sym.adr;
                    inits.excl(adr);
                    uninits.incl(adr);
                }
            }
        }

    // 访问try语句 异常检查、变量赋值检查、活跃性分析
    public void visitTry(JCTry tree) {
        // 通过局部变量保存caught与thrown列表的值，
        // 然后将当前try语句中含有的各个catch语句中能够捕获到的异常类型添加到caught列表中
        // 例14-22
        List<Type> caughtPrev = caught;
        List<Type> thrownPrev = thrown;
        thrown = List.nil();
        for (List<JCCatch> l = tree.catchers; l.nonEmpty(); l = l.tail) {
            List<JCExpression> subClauses = TreeInfo.isMultiCatch(l.head) ?
                    ((JCTypeUnion)l.head.param.vartype).alternatives :
                    List.of(l.head.param.vartype);
            for (JCExpression ct : subClauses) {
                caught = chk.incl(ct.type, caught);
            }
        }
        ListBuffer<JCVariableDecl> resourceVarDecls = ListBuffer.lb();
        // 在处理try语句body体之前，会通过initsTry与uninitsTry保存inits与uninits的值
        // 因为在处理try语句的body体时会更新inits与uninits的值，但是在分析catch语句或者finally语句的body体时，
        // 使用的仍然是initsTry与uninitsTry变量的值
        Bits uninitsTryPrev = uninitsTry;
        ListBuffer<PendingExit> prevPendingExits = pendingExits;
        pendingExits = new ListBuffer<PendingExit>();
        Bits initsTry = inits.dup();
        uninitsTry = uninits.dup();
        for (JCTree resource : tree.resources) {
            if (resource instanceof JCVariableDecl) {
                JCVariableDecl vdecl = (JCVariableDecl) resource;
                visitVarDef(vdecl);
                unrefdResources.enter(vdecl.sym);
                resourceVarDecls.append(vdecl);
            } else if (resource instanceof JCExpression) {
                scanExpr((JCExpression) resource);
            } else {
                throw new AssertionError(tree);  // parser error
            }
        }
        for (JCTree resource : tree.resources) {
            List<Type> closeableSupertypes = resource.type.isCompound() ?
                types.interfaces(resource.type).prepend(types.supertype(resource.type)) :
                List.of(resource.type);
            for (Type sup : closeableSupertypes) {
                // 第1个if语句
                // 如果try语句是具体的try-with-resources语句，那么在自动调用close()方法时可能抛出异常，
                // 所以也要将这些异常记录到throw列表中，以便后续在分析catch语句时提供必要的异常抛出类型
                // 调用asSuper()方法查找sup或sup的父类型，这个类型的tsym等于syms.autoCloseableType.tsym，
                // 这个类型必须存在，也就是sup必须实现AutoCloseable接口
                if (types.asSuper(sup, syms.autoCloseableType.tsym) != null) {
                    // 然后调用resolveQualifiedMethod()方法从sup中查找close()方法，
                    // 将close()方法中可能抛出的异常通过markThrow()方法记录到throw列表中
                    Symbol closeMethod = rs.resolveQualifiedMethod(tree,
                            attrEnv,
                            sup,
                            names.close,
                            List.<Type>nil(),
                            List.<Type>nil());
                    // 第2个if语句
                    if (closeMethod.kind == MTH) {
                        for (Type t : ((MethodSymbol)closeMethod).getThrownTypes()) {
                            markThrown(resource, t);
                        }
                    }
                }
            }
        }
        // 处理try的body体
        // 在处理try语句的body体时，可能会通过throw关键字抛出异常，如果抛出受检查的异常，则会记录到throw列表中
        scanStat(tree.body);
        // 通过局部变量thrownInTry保存thrown列表的值，这样thrownInTry列表中包含了try语句body体中抛出的受检查异常
        List<Type> thrownInTry = allowImprovedCatchAnalysis ?
            chk.union(thrown, List.of(syms.runtimeExceptionType, syms.errorType)) :
            thrown;
        thrown = thrownPrev;
        caught = caughtPrev;
        // 更新状态变量，处理catch语句或finally语句时使用的状态变量应该为initsTry与uninitsTry
        boolean aliveEnd = alive;
        // 对于uninitsTry来说，因为tree.body是一条可能的执行路径，
        // 所以最终的取值为处理tree.body之前保存的值uninitsTry与之后的值uninits取交集
        uninitsTry.andSet(uninits);
        // 最终会与各个catch语句body体运行后的状态变量取交集，
        // 最终这个initsEnd与uninitsEnd会作为分析try语句后续语句的状态变量
        Bits initsEnd = inits;
        Bits uninitsEnd = uninits;
        int nextadrCatch = nextadr;

        if (!resourceVarDecls.isEmpty() &&
                lint.isEnabled(Lint.LintCategory.TRY)) {
            for (JCVariableDecl resVar : resourceVarDecls) {
                if (unrefdResources.includes(resVar.sym)) {
                    log.warning(Lint.LintCategory.TRY, resVar.pos(),
                                "try.resource.not.referenced", resVar.sym);
                    unrefdResources.remove(resVar.sym);
                }
            }
        }

        List<Type> caughtInTry = List.nil();
        for (List<JCCatch> l = tree.catchers; l.nonEmpty(); l = l.tail) {
            alive = true;
            JCVariableDecl param = l.head.param;
            List<JCExpression> subClauses = TreeInfo.isMultiCatch(l.head) ?
                    ((JCTypeUnion)l.head.param.vartype).alternatives :
                    List.of(l.head.param.vartype);
            List<Type> ctypes = List.nil();
            // 调用chk.diff()方法计算重抛的异常，也就是说，如果在try语句body体中抛出的异常被当前分析的try语句中的catch语句捕获了，
            // 则在thrownInTry列表中移除，这样当前分析的try语句后续的catch语句就不用处理这些异常了；
            // 如果在当前catch语句的body体中重抛异常时，rethrownTypes列表中保存了这些可能被重抛的异常
            List<Type> rethrownTypes = chk.diff(thrownInTry, caughtInTry);
            // 循环当前catch语句中声明捕获的异常类型列表subClauses，调用checkCaughtType()方法对异常类型进行检查
            for (JCExpression ct : subClauses) {
                Type exc = ct.type;
                if (exc != syms.unknownType) {
                    ctypes = ctypes.append(exc);
                    if (types.isSameType(exc, syms.objectType))
                        continue;
                    // 调用checkCaughtType()方法对异常类型进行检查
                    checkCaughtType(l.head.pos(), exc, thrownInTry, caughtInTry);
                    // 调用chk.incl()方法将exc添加到caughtInTry列表中，新列表将作为新的caughtInTry变量的值，这是分析下一个catch语句时使用的变量值
                    caughtInTry = chk.incl(exc, caughtInTry);
                }
            }
            // 在分析catch语句时，将inits与uninits初始化为initsTry与uninitsTry，
            inits = initsTry.dup();
            uninits = uninitsTry.dup();
            // 然后调用scan()方法处理catch语句中的形式参数param。
            scan(param);
            // 处理完成后再次更新inits与uninits，因为catch语句body体中同样可以使用当前catch语句中声明的形式参数
            inits.incl(param.sym.adr);
            uninits.excl(param.sym.adr);
            // 调用chk.intersect()方法对ctypes与rethrownTypes列表中的类型取交集
            // chk.intersect()方法得到的值存储到preciseRethrowType集合中
            preciseRethrowTypes.put(param.sym, chk.intersect(ctypes, rethrownTypes));
            // 调用scanStat()方法处理catch语句的body体
            scanStat(l.head.body);
            // 如果异常类型进行了重抛，查看前面关于异常抛出的相关内容，
            // 重抛中的增强型throws声明语法正是借助preciseRethrowType集合来完成的。
            // 处理完catch语句body体后，需要更新initsEnd与uninitsEnd
            // 这两个状态变量是运行try语句body体后的状态变量
            initsEnd.andSet(inits);
            uninitsEnd.andSet(uninits);
            nextadr = nextadrCatch;
            preciseRethrowTypes.remove(param.sym);
            aliveEnd |= alive;
        }
        if (tree.finalizer != null) {
            // try语句含有finally语句
            List<Type> savedThrown = thrown;
            thrown = List.nil();
            // 在执行finally语句之前初始化inits与uninits
            inits = initsTry.dup();
            uninits = uninitsTry.dup();
            ListBuffer<PendingExit> exits = pendingExits;
            pendingExits = prevPendingExits;
            alive = true;
            // 调用scanStat()方法处理完finally语句之后计算thrown与pendingExits列表的值
            scanStat(tree.finalizer);
            if (!alive) {
                // alive的值为false
                // 最终的thrown列表中的异常为finally语句body体中抛出的异常加上try语句之前抛出的异常，
                // 这样会导致当前try语句body体及catch语句body体中抛出的异常被抑制，可能会提示“finally子句无法正常完成”。
                thrown = chk.union(thrown, thrownPrev);
                if (!loopPassTwo &&
                    lint.isEnabled(Lint.LintCategory.FINALLY)) {
                    log.warning(Lint.LintCategory.FINALLY,
                            TreeInfo.diagEndPos(tree.finalizer),
                            "finally.cannot.complete");
                }
            } else {
                /*
                如果alive的值为true，thrown列表中的异常由以下三部分组成
                    1:try语句（包括body体及自动调用resource中的close()方法抛出的异常）可能抛出的异常而catch语句没有捕获的异常。
                    2:catch语句的body体中可能抛出的异常与try语句之前的语句可能抛出的异常。
                    3:finally语句的body体中可能抛出的异常。
                 */
                thrown = chk.union(thrown, chk.diff(thrownInTry, caughtInTry));
                thrown = chk.union(thrown, savedThrown);
                // 在更新uninits时与uninitsEnd取交集，在更新inits时与initsEnd取并集，
                // 这是因为finally语句不是一条可选择的执行路径，而是一条必须执行的路径
                uninits.andSet(uninitsEnd);
                while (exits.nonEmpty()) {
                    // 在有finally语句并且alive的值为true的情况下，对PendingExit对象中的inits与uninits也会做处理，确定在这一条可能执行的路径上明确初始化的变量和未初始化的变量
                    PendingExit exit = exits.next();
                    if (exit.inits != null) {
                        // 当调用scanStat()方法处理完tree.finalizer并且alive的值仍然为true时
                        // 更新相关的状态变量及alive的值
                        // 在更新exit.inits时与inits取并集，因为变量需要在每条可选择的执行路径上都初始化才能变为明确初始化状态；
                        exit.inits.orSet(inits);
                        // 在更新exit.uninits时与uninits取交集，因为变量需要在每条可选择的执行路径上都没有初始化才能变为非明确初始化状态。
                        exit.uninits.andSet(uninits);
                    }
                    // 最后将exits列表中的值追到pendingExits列表中。
                    pendingExits.append(exit);
                }
                inits.orSet(initsEnd);
                // 最后无论有没有finally语句都需要将alive的值更新为aliveEnd
                // 只要try语句的body体与各个catch语句这几条可选择的执行路径中有一条是活跃的，最终的aliveEnd的值就为true
                alive = aliveEnd;
            }
        } else {
            /*
            如果try语句不含有finally语句，同样会计算thrown与pendingExits列表的值。
            thrown列表的异常由以下两部分组成：
                1:try语句的body体中抛出的而catch语句没有捕获的受检查异常。
                2:catch语句的body体可能抛出的异常thrown与在try语句之前抛出的异常。
             */
            thrown = chk.union(thrown, chk.diff(thrownInTry, caughtInTry));
            // 当没有finally语句时直接将inits与uninits初始化为initsEnd与uninitsEnd
            inits = initsEnd;
            uninits = uninitsEnd;
            // 最后无论有没有finally语句都需要将alive的值更新为aliveEnd
            alive = aliveEnd;
            ListBuffer<PendingExit> exits = pendingExits;
            pendingExits = prevPendingExits;
            while (exits.nonEmpty())
                pendingExits.append(exits.next());
        }
        uninitsTry.andSet(uninitsTryPrev).andSet(uninits);
    }

    /*
    checkCaughtType()方法主要对以下两种情况进行了检查
        1:对于同一个try语句含有的多个catch语句来说，在分析当前catch语句时，检查之前是否已经有catch语句捕获了exc异常，如果已经捕获，Javac将报错，报错摘要为“已捕获到异常错误”。
        2:如果不可能在try语句的body体中抛出的受检查异常也在catch语句中声明了捕获，Javac将报错，报错摘要为“在相应的try语句主体中不能抛出异常错误”。
     */
    void checkCaughtType(DiagnosticPosition pos, Type exc, List<Type> thrownInTry, List<Type> caughtInTry) {
        if (chk.subset(exc, caughtInTry)) {
            log.error(pos, "except.already.caught", exc);
        } else if (!chk.isUnchecked(pos, exc) &&
                !isExceptionOrThrowable(exc) &&
                !chk.intersects(exc, thrownInTry)) {
            log.error(pos, "except.never.thrown.in.try", exc);
        } else if (allowImprovedCatchAnalysis) {
            List<Type> catchableThrownTypes = chk.intersect(List.of(exc), thrownInTry);
            if (chk.diff(catchableThrownTypes, caughtInTry).isEmpty() &&
                    !isExceptionOrThrowable(exc)) {
                String key = catchableThrownTypes.length() == 1 ?
                        "unreachable.catch" :
                        "unreachable.catch.1";
                log.warning(pos, key, catchableThrownTypes);
            }
        }
    }
    //where
        private boolean isExceptionOrThrowable(Type exc) {
            return exc.tsym == syms.throwableType.tsym ||
                exc.tsym == syms.exceptionType.tsym;
        }

    // 访问三元表达式
    public void visitConditional(JCConditional tree) {
        scanCond(tree.cond);
        Bits initsBeforeElse = initsWhenFalse;
        Bits uninitsBeforeElse = uninitsWhenFalse;
        // 在分析tree.truepart之前，将inits与uninits变量初始化为initsWhenTrue与uninitsWhenTrue变量的值；
        inits = initsWhenTrue;
        uninits = uninitsWhenTrue;
        // 当三元表达式中的tree.truepart与tree.falsepart的结果为布尔类型时，
        // 调用scanCond()方法分析tree.cond后得到4个条件状态变量
        if (tree.truepart.type.tag == BOOLEAN &&
            tree.falsepart.type.tag == BOOLEAN) {
            scanCond(tree.truepart);
            Bits initsAfterThenWhenTrue = initsWhenTrue.dup();
            Bits initsAfterThenWhenFalse = initsWhenFalse.dup();
            Bits uninitsAfterThenWhenTrue = uninitsWhenTrue.dup();
            Bits uninitsAfterThenWhenFalse = uninitsWhenFalse.dup();
            // 在分析tree.falsepart之前，将inits与uninits变量初始化为initsBeforeElse与uninitsBeforeElse变量的值，
            // 也就是初始化为initsWhenFalse与uninitsWhenFalse变量的值。
            inits = initsBeforeElse;
            uninits = uninitsBeforeElse;
            scanCond(tree.falsepart);
            // 在分析tree.truepart与tree.falsepart时，调用2次scanCond()方法会产生2组共8个条件状态变量，
            // 两两进行与运算后就可以得到最终作为条件判断表达式的4个条件状态变量的值了。
            initsWhenTrue.andSet(initsAfterThenWhenTrue);
            initsWhenFalse.andSet(initsAfterThenWhenFalse);
            uninitsWhenTrue.andSet(uninitsAfterThenWhenTrue);
            uninitsWhenFalse.andSet(uninitsAfterThenWhenFalse);
        } else {
            scanExpr(tree.truepart);
            Bits initsAfterThen = inits.dup();
            Bits uninitsAfterThen = uninits.dup();
            inits = initsBeforeElse;
            uninits = uninitsBeforeElse;
            scanExpr(tree.falsepart);
            inits.andSet(initsAfterThen);
            uninits.andSet(uninitsAfterThen);
        }
    }

    // 检查if语句的数据流
    // 检查if语句的活跃性及变量赋值的情况
    public void visitIf(JCIf tree) {
        // 在调用scanCond()方法处理条件判断表达式tree.cond时，会初始化4个条件状态变量
        scanCond(tree.cond);
        // 由于在执行if分支语句的过程中，
        // initsWhenFalse与unintsWhenFalse的值有可能被修改，
        // 如if分支语句中又有条件判断表达式需要调用scanCond()方法进行处理，
        // 所以要通过方法的局部变量initsBeforeElse与uninitsBeforeElse来保存。
        Bits initsBeforeElse = initsWhenFalse;
        Bits uninitsBeforeElse = uninitsWhenFalse;
        inits = initsWhenTrue;
        uninits = uninitsWhenTrue;
        // 在调用scanStat()方法分析if分支tree.thenpart之前，
        // 将initsWhenTrue与unintsWhenTrue的值赋值给inits与unints，
        // 这样分析if分支语句时，使用inits与uninits就相当于使用initsWhenTrue与uninitsWhenTrue变量的值。
        scanStat(tree.thenpart);
        // 例14-9/14-10
        if (tree.elsepart != null) { // if语句有else分支
            boolean aliveAfterThen = alive;
            alive = true;
            // 如果当前分析的if语句有else分支tree.elsepart，
            // 则通过initsAfterThen与uninitsAfterThen保存处理tree.thenpart(if部分)后的状态变量的值，
            // 然后将之前保存的initsWhenFalse（通过initsBeforeElse暂时保存）与unintsWhenFalse（通过uninitsBeforeElse暂时保存）的值赋值给inits与unints，
            // 这样分析else分支语句时，使用inits与uninits就相当于使用initsWhenFalse与unintsWhenFalse变量的值
            Bits initsAfterThen = inits.dup();
            Bits uninitsAfterThen = uninits.dup();
            inits = initsBeforeElse;
            uninits = uninitsBeforeElse;
            scanStat(tree.elsepart);
            // 完成else分支elsepart处理后，调用andSet()方法将inits与unints变量的值分别与initsAfterThen与uninitsAfterThen变量的值进行与运算，
            // 求得的inits与uninits就是分析if语句之后的变量使用的状态变量
            inits.andSet(initsAfterThen);
            uninits.andSet(uninitsAfterThen);
            // 通过alive=alive|aliveAfterThen来计算alive值
            // 也就是if分支与else分支两条可能的执行路径中，只要有一条是活跃的，最终if语句之后的语句就是活跃的。
            alive = alive | aliveAfterThen;
        } else { // if语句没有else分支
            // 如果当前分析的if语句没有else分支tree.elsepart，
            // 则调用andSet()方法将inits与unints变量的值分别与initsWhenFalse（通过initsBeforeElse暂时保存）与unintsWhenFalse（通过uninitsBeforeElse暂时保存）变量的值进行与运算，
            // 求得的inits与uninits就是分析if语句之后的变量使用的状态变量。
            inits.andSet(initsBeforeElse);
            uninits.andSet(uninitsBeforeElse);
            // 在没有else分支的情况下，alive被设置为true，因为else分支不存在，所以if语句后续的语句都有可能被执行
            alive = true;
        }
    }



    // 访问break语句 活跃性分析
    public void visitBreak(JCBreak tree) {
        recordExit(tree);
    }

    // 访问continue语句 活跃性分析
    public void visitContinue(JCContinue tree) {
        recordExit(tree);
    }

    // 访问return语句 活跃性分析
    public void visitReturn(JCReturn tree) {
        scanExpr(tree.expr);
        // if not initial constructor, should markDead instead of recordExit
        recordExit(tree);
    }

    // 访问throw语句 活跃性分析
    public void visitThrow(JCThrow tree) {
        scanExpr(tree.expr);
        // 调用TreeInfo.symbol()方法获取tree.expr所引用的符号
        Symbol sym = TreeInfo.symbol(tree.expr);
        if (sym != null &&
            sym.kind == VAR &&
            (sym.flags() & (FINAL | EFFECTIVELY_FINAL)) != 0 &&
            // 对重抛语句throw e之前的代码分析时得出的e可能的异常类型，
            // 将可能抛出的受检查异常和运行时异常记录到preciseRethrowTypes集合中
            preciseRethrowTypes.get(sym) != null &&
            // 通过allowImprovedRethrowAnalysis变量来控制是否使用增强型throws声明语法
            allowImprovedRethrowAnalysis) {
            // 允许使用增强型throws声明
            // 例14-20
            for (Type t : preciseRethrowTypes.get(sym)) {
                // 记录抛出的异常
                markThrown(tree, t);
            }
        }
        else {
            // 记录抛出的异常
            markThrown(tree, tree.expr.type);
        }
        // 调用的markDead()方法会将alive变量的值设置为false，表示后续的语句不可达。
        markDead();
    }

    public void visitApply(JCMethodInvocation tree) {
        scanExpr(tree.meth);
        scanExprs(tree.args);
        for (List<Type> l = tree.meth.type.getThrownTypes(); l.nonEmpty(); l = l.tail)
            markThrown(tree, l.head);
    }

    public void visitNewClass(JCNewClass tree) {
        scanExpr(tree.encl);
        scanExprs(tree.args);
       // scan(tree.def);
        for (List<Type> l = tree.constructorType.getThrownTypes();
             l.nonEmpty();
             l = l.tail) {
            markThrown(tree, l.head);
        }
        List<Type> caughtPrev = caught;
        try {
            // If the new class expression defines an anonymous class,
            // analysis of the anonymous constructor may encounter thrown
            // types which are unsubstituted type variables.
            // However, since the constructor's actual thrown types have
            // already been marked as thrown, it is safe to simply include
            // each of the constructor's formal thrown types in the set of
            // 'caught/declared to be thrown' types, for the duration of
            // the class def analysis.
            if (tree.def != null)
                for (List<Type> l = tree.constructor.type.getThrownTypes();
                     l.nonEmpty();
                     l = l.tail) {
                    caught = chk.incl(l.head, caught);
                }
            scan(tree.def);
        }
        finally {
            caught = caughtPrev;
        }
    }

    public void visitNewArray(JCNewArray tree) {
        scanExprs(tree.dims);
        scanExprs(tree.elems);
    }

    public void visitAssert(JCAssert tree) {
        Bits initsExit = inits.dup();
        Bits uninitsExit = uninits.dup();
        scanCond(tree.cond);
        uninitsExit.andSet(uninitsWhenTrue);
        if (tree.detail != null) {
            inits = initsWhenFalse;
            uninits = uninitsWhenFalse;
            scanExpr(tree.detail);
        }
        inits = initsExit;
        uninits = uninitsExit;
    }

    public void visitAssign(JCAssign tree) {
        JCTree lhs = TreeInfo.skipParens(tree.lhs);
        if (!(lhs instanceof JCIdent)) scanExpr(lhs);
        scanExpr(tree.rhs);
        letInit(lhs);
    }

    public void visitAssignop(JCAssignOp tree) {
        scanExpr(tree.lhs);
        scanExpr(tree.rhs);
        letInit(tree.lhs);
    }

    // 一元表达式检查

    public void visitUnary(JCUnary tree) {
        switch (tree.getTag()) {
        case JCTree.NOT:// 含有非运算符的一元表达式
            // 只有当一元表达式的结果为布尔类型时才可以作为if语句的条件判断表达式
            // 只需要调用visitUnary()方法处理含有非运算符的一元表达式即可
            scanCond(tree.arg);
            Bits t = initsWhenFalse;
            initsWhenFalse = initsWhenTrue;
            initsWhenTrue = t;
            t = uninitsWhenFalse;
            uninitsWhenFalse = uninitsWhenTrue;
            uninitsWhenTrue = t;
            break;
        case JCTree.PREINC: case JCTree.POSTINC:
        case JCTree.PREDEC: case JCTree.POSTDEC:
            scanExpr(tree.arg);
            letInit(tree.arg);
            break;
        default:
            scanExpr(tree.arg);
        }
    }

    // 访问二元表达式
    // 这&& || 两个运算符有“短路”的功能，会影响条件判断表达式的执行，从而可能影响变量的赋值状态，所以在visitBinary()方法中会重点处理含有这两个运算符的二元表达式
    public void visitBinary(JCBinary tree) {
        switch (tree.getTag()) {
        case JCTree.AND: // 含有与运算符的二元表达式 例14-12
            // 对于与运算符来说，如果tree.lhs的值为false，则不会继续执行tree.rhs
            scanCond(tree.lhs);
            Bits initsWhenFalseLeft = initsWhenFalse;
            Bits uninitsWhenFalseLeft = uninitsWhenFalse;
            // 如果要分析tree.rhs，则tree.lhs的值一定为true，
            // 那么就需要将initsWhenTrue与uninitsWhenTrue的值赋值给inits与unints
            inits = initsWhenTrue;
            uninits = uninitsWhenTrue;
            // 然后调用scanCond()方法分析tree.rhs，最终inits与unints变量保存的就是二元表达式结果为true时变量的赋值状态
            scanCond(tree.rhs);
            // 对tree.lhs的值为false与tree.rhs的值为false时的状态变量进行与运算后，得到二元表达式的结果为false时的变量赋值状态。
            initsWhenFalse.andSet(initsWhenFalseLeft);
            uninitsWhenFalse.andSet(uninitsWhenFalseLeft);
            break;
        case JCTree.OR:// 含有或运算符的二元表达式
            scanCond(tree.lhs);
            Bits initsWhenTrueLeft = initsWhenTrue;
            Bits uninitsWhenTrueLeft = uninitsWhenTrue;
            // 对于或运算符来说，当tree.lhs的值为false时才会分析tree.rhs，在调用scanCond()方法分析tree.rhs之前，
            // 需要将inits与unints初始化为initsWhenFalse与uninitsWhenFalse
            inits = initsWhenFalse;
            uninits = uninitsWhenFalse;
            // 最终的inits与unints变量保存的就是二元表达式结果为false时的变量赋值状态，
            scanCond(tree.rhs);
            // 对tree.lhs的值为true与tree.rhs的值为true时的状态变量进行与运算后，得到二元表达式的结果为true时的变量赋值状态
            initsWhenTrue.andSet(initsWhenTrueLeft);
            uninitsWhenTrue.andSet(uninitsWhenTrueLeft);
            break;
        default:
            scanExpr(tree.lhs);
            scanExpr(tree.rhs);
        }
    }

    public void visitIdent(JCIdent tree) {
        if (tree.sym.kind == VAR) {
            // 如果是变量则调用checkInit()方法进行检查
            checkInit(tree.pos(), (VarSymbol)tree.sym);
            referenced(tree.sym);
        }
    }

    void referenced(Symbol sym) {
        unrefdResources.remove(sym);
    }

    public void visitTypeCast(JCTypeCast tree) {
        super.visitTypeCast(tree);
        if (!tree.type.isErroneous()
            && lint.isEnabled(Lint.LintCategory.CAST)
            && types.isSameType(tree.expr.type, tree.clazz.type)
            && !is292targetTypeCast(tree)) {
            log.warning(Lint.LintCategory.CAST,
                    tree.pos(), "redundant.cast", tree.expr.type);
        }
    }
    //where
        private boolean is292targetTypeCast(JCTypeCast tree) {
            boolean is292targetTypeCast = false;
            JCExpression expr = TreeInfo.skipParens(tree.expr);
            if (expr.getTag() == JCTree.APPLY) {
                JCMethodInvocation apply = (JCMethodInvocation)expr;
                Symbol sym = TreeInfo.symbol(apply.meth);
                is292targetTypeCast = sym != null &&
                    sym.kind == MTH &&
                    (sym.flags() & POLYMORPHIC_SIGNATURE) != 0;
            }
            return is292targetTypeCast;
        }

    public void visitTopLevel(JCCompilationUnit tree) {
        // Do nothing for TopLevel since each class is visited individually
    }

/**************************************************************************
 * main method
 *************************************************************************/

    /** Perform definite assignment/unassignment analysis on a tree.
     */
    public void analyzeTree(Env<AttrContext> env, TreeMaker make) {
        try {
            attrEnv = env;
            JCTree tree = env.tree;
            this.make = make;
            inits = new Bits();
            uninits = new Bits();
            uninitsTry = new Bits();
            initsWhenTrue = initsWhenFalse =
                uninitsWhenTrue = uninitsWhenFalse = null;
            // 初始化赋值检查所需的变量
            if (vars == null)
                vars = new VarSymbol[32];
            else
                for (int i=0; i<vars.length; i++)
                    vars[i] = null;
            firstadr = 0;
            nextadr = 0;
            pendingExits = new ListBuffer<PendingExit>();
            preciseRethrowTypes = new HashMap<Symbol, List<Type>>();
            alive = true;
            this.thrown = this.caught = null;
            this.classDef = null;
            unrefdResources = new Scope(env.enclClass.sym);
            scan(tree);
        } finally {
            // note that recursive invocations of this method fail hard
            inits = uninits = uninitsTry = null;
            initsWhenTrue = initsWhenFalse =
                uninitsWhenTrue = uninitsWhenFalse = null;
            if (vars != null) for (int i=0; i<vars.length; i++)
                vars[i] = null;
            firstadr = 0;
            nextadr = 0;
            pendingExits = null;
            this.make = null;
            this.thrown = this.caught = null;
            this.classDef = null;
            unrefdResources = null;
        }
    }
}
