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

package com.sun.tools.javac.code;

import com.sun.tools.javac.util.*;
import java.util.Iterator;

/** A scope represents an area of visibility in a Java program. The
 *  Scope class is a container for symbols which provides
 *  efficient access to symbols given their names. Scopes are implemented
 *  as hash tables with "open addressing" and "double hashing".
 *  Scopes can be nested; the next field of a scope points
 *  to its next outer scope. Nested scopes can share their hash tables.
 *
 *  <p><b>This is NOT part of any supported API.
 *  If you write code that depends on this, you do so at your own risk.
 *  This code and its internal interfaces are subject to change or
 *  deletion without notice.</b>
 */
// 符号一般都是与作用域关联的，Java的作用域是嵌套的，
// 这样Javac在实现过程中，可以通过Scope类来实现作用域的嵌套，
// 然后将每个作用域中定义的符号分别保存到对应作用域的相关变量中，
// 这样就可以通过遍历作用域的方式查找唯一的符号引用了。
/*
Javac在实现过程中，为了能够体现出作用域的嵌套，以及为后序语句及表达式的分析提供更全面的上下文信息，
一般会在任意树节点的分析过程中伴随有Env、AttrContext与Scope对象。
Scope对象表示Java的作用域，其中保存了许多符号相关的具体信息，这些都可以理解为广义符号表的一部分
 */
// 作用域（Scope）是Java语言的一部分，大多数作用域都以花括号分隔，因此每个JCBlock对象都能形成具体的作用域
// 同一个符号在不同的作用域中可能指向不同的实体。
// 符号的有效区域始于名称的定义语句，以定义语句所在的作用域末端为结束
public class Scope {

    /** The number of scopes that share this scope's hash table.
     */
    // 为了节省符号表空间，存储符号的table数组还可能共享，也就是不同的作用域使用同一个table数组，可通过shared来表示共享
    // 如果当前Scope对象的next所指向的Scope对象的shared值为1，则表示next所指向的Scope对象与当前Scope对象共享同一个table数组。
    private int shared;

    /** Next enclosing scope (with whom this scope may share a hashtable)
     */
    // 如果当前Scope对象的next所指向的Scope对象的shared值为1，则表示next所指向的Scope对象与当前Scope对象共享同一个table数组。
    public Scope next;

    /** The scope's owner.
     */
    public Symbol owner;

    /** A hash table for the scope's entries.
     */
    // 符号表
    // table数组用来存储作用域内定义的符号。
    // 一个作用域内定义的多个符号用数组来存储，不过并不是直接存储Symbol对象，
    // 而是将Symbol对象进一步封装为Entry对象，然后存储到table数组中。
    Entry[] table;

    /** Mask for hash codes, always equal to (table.length - 1).
     */
    int hashMask;

    /** A linear list that also contains all entries in
     *  reverse order of appearance (i.e later entries are pushed on top).
     */
    // 同一个作用域内定义的所有符号会形成单链表，elems保存了这个单链表的首个Entry对象
    public Entry elems;

    /** The number of elements in this scope.
     * This includes deleted elements, whose value is the sentinel.
     */
    // nelems保存了单链表中Entry对象的总数
    int nelems = 0;

    /** A list of scopes to be notified if items are to be removed from this scope.
     */
    List<ScopeListener> listeners = List.nil();

    /** Use as a "not-found" result for lookup.
     * Also used to mark deleted entries in the table.
     */
    // 哨兵，站岗对象(占槽位用)
    private static final Entry sentinel = new Entry(null, null, null, null);

    /** The hash table's initial size.
     */
    private static final int INITIAL_SIZE = 0x10;

    /** A value for the empty scope.
     */
    public static final Scope emptyScope = new Scope(null, null, new Entry[]{});

    /** Construct a new scope, within scope next, with given owner, using
     *  given table. The table's length must be an exponent of 2.
     */
    private Scope(Scope next, Symbol owner, Entry[] table) {
        this.next = next;
        Assert.check(emptyScope == null || owner != null);
        this.owner = owner;
        this.table = table;
        this.hashMask = table.length - 1;
    }

    /** Convenience constructor used for dup and dupUnshared. */
    private Scope(Scope next, Symbol owner, Entry[] table, int nelems) {
        this(next, owner, table);
        this.nelems = nelems;
    }

    /** Construct a new scope, within scope next, with given owner,
     *  using a fresh table of length INITIAL_SIZE.
     */
    public Scope(Symbol owner) {
        this(null, owner, new Entry[INITIAL_SIZE]);
    }

    /** Construct a fresh scope within this scope, with same owner,
     *  which shares its table with the outer scope. Used in connection with
     *  method leave if scope access is stack-like in order to avoid allocation
     *  of fresh tables.
     */
    // 将当前的Scope对象赋值给新的Scope对象的next变量
    public Scope dup() {
        return dup(this.owner);
    }

    /** Construct a fresh scope within this scope, with new owner,
     *  which shares its table with the outer scope. Used in connection with
     *  method leave if scope access is stack-like in order to avoid allocation
     *  of fresh tables.
     */
    // 将当前的Scope对象赋值给新的Scope对象的next变量
    public Scope dup(Symbol newOwner) {
        Scope result = new Scope(this, newOwner, this.table, this.nelems);
        shared++;
        // System.out.println("====> duping scope " + this.hashCode() + " owned by " + newOwner + " to " + result.hashCode());
        // new Error().printStackTrace(System.out);
        return result;
    }

    /** Construct a fresh scope within this scope, with same owner,
     *  with a new hash table, whose contents initially are those of
     *  the table of its outer scope.
     */
    // 调用env.info.scope.dupUnshared()方法完成Scope对象的复制
    // 这样创建出来的Scope对象与env.info.scope对象不共享table数组
    // 这样就可以避免方法声明的形式参数及方法体内声明的局部变量等信息被当前方法之外的作用域访问
    public Scope dupUnshared() {
        return new Scope(this, this.owner, this.table.clone(), this.nelems);
    }

    /** Remove all entries of this scope from its table, if shared
     *  with next.
     */
    // 如果离开相关的作用域，则应该调用leave()方法删除对应作用域内定义的符号
    public Scope leave() {
        Assert.check(shared == 0);
        if (table != next.table) return next;
        // 当前作用域中定义的所有符号都通过sibling连接为单链表，
        // 因此只需要从单链表的头部elems开始删除即可，同时也要更新外层作用域的nelems值
        while (elems != null) {
            int hash = getIndex(elems.sym.name);
            Entry e = table[hash];
            Assert.check(e == elems, elems.sym);
            table[hash] = elems.shadowed;
            elems = elems.sibling;
        }
        Assert.check(next.shared > 0);
        next.shared--;
        next.nelems = nelems;
        // System.out.println("====> leaving scope " + this.hashCode() + " owned by " + this.owner + " to " + next.hashCode());
        // new Error().printStackTrace(System.out);
        return next;
    }

    /** Double size of hash table.
     */
    private void dble() {
        Assert.check(shared == 0);
        Entry[] oldtable = table;
        Entry[] newtable = new Entry[oldtable.length * 2];
        for (Scope s = this; s != null; s = s.next) {
            if (s.table == oldtable) {
                Assert.check(s == this || s.shared != 0);
                s.table = newtable;
                s.hashMask = newtable.length - 1;
            }
        }
        int n = 0;
        for (int i = oldtable.length; --i >= 0; ) {
            Entry e = oldtable[i];
            if (e != null && e != sentinel) {
                table[getIndex(e.sym.name)] = e;
                n++;
            }
        }
        // We don't need to update nelems for shared inherited scopes,
        // since that gets handled by leave().
        nelems = n;
    }

    /** Enter symbol sym in this scope.
     */
    public void enter(Symbol sym) {
        Assert.check(shared == 0);
        enter(sym, this);
    }

    public void enter(Symbol sym, Scope s) {
        enter(sym, s, s);
    }

    /**
     * Enter symbol sym in this scope, but mark that it comes from
     * given scope `s' accessed through `origin'.  The last two
     * arguments are only used in import scopes.
     */
    // enter()方法向符号表中输入已经定义好的符号
    public void enter(Symbol sym, Scope s, Scope origin) {
        // 断言shared是否为0，如果为1，表示符号表被共享，但是不知道当前符号是否被共享
        Assert.check(shared == 0);
        if (nelems * 3 >= hashMask * 2)
            dble();
        // genIndex()方法获取存储的槽位，如果各个符号的名称相同，则返回的哈希值一定相同
        int hash = getIndex(sym.name);
        Entry old = table[hash];
        if (old == null) {
            old = sentinel;
            nelems++;
        }
        // 对应的槽位上如果有值，则将调用makeEntry()方法创建代表当前符号的e对象并存储到对应的槽位上
        Entry e = makeEntry(sym, old, elems, s, origin);
        table[hash] = e;
        elems = e;

        //notify listeners
        for (List<ScopeListener> l = listeners; l.nonEmpty(); l = l.tail) {
            l.head.symbolAdded(sym, this);
        }
    }

    Entry makeEntry(Symbol sym, Entry shadowed, Entry sibling, Scope scope, Scope origin) {
        // e的sibling也指向了之前elems的值
        return new Entry(sym, shadowed, sibling, scope);
    }


    public interface ScopeListener {
        public void symbolAdded(Symbol sym, Scope s);
        public void symbolRemoved(Symbol sym, Scope s);
    }

    public void addScopeListener(ScopeListener sl) {
        listeners = listeners.prepend(sl);
    }

    /** Remove symbol from this scope.  Used when an inner class
     *  attribute tells us that the class isn't a package member.
     */
    public void remove(Symbol sym) {
        Assert.check(shared == 0);
        Entry e = lookup(sym.name);
        if (e.scope == null) return;

        // remove e from table and shadowed list;
        int i = getIndex(sym.name);
        Entry te = table[i];
        if (te == e)
            table[i] = e.shadowed;
        else while (true) {
            if (te.shadowed == e) {
                te.shadowed = e.shadowed;
                break;
            }
            te = te.shadowed;
        }

        // remove e from elems and sibling list
        te = elems;
        if (te == e)
            elems = e.sibling;
        else while (true) {
            if (te.sibling == e) {
                te.sibling = e.sibling;
                break;
            }
            te = te.sibling;
        }

        //notify listeners
        for (List<ScopeListener> l = listeners; l.nonEmpty(); l = l.tail) {
            l.head.symbolRemoved(sym, this);
        }
    }

    /** Enter symbol sym in this scope if not already there.
     */
    public void enterIfAbsent(Symbol sym) {
        Assert.check(shared == 0);
        Entry e = lookup(sym.name);
        while (e.scope == this && e.sym.kind != sym.kind) e = e.next();
        if (e.scope != this) enter(sym);
    }

    /** Given a class, is there already a class with same fully
     *  qualified name in this (import) scope?
     */
    // 确保toScope作用域中不包含sym
    public boolean includes(Symbol c) {
        for (Scope.Entry e = lookup(c.name);
             e.scope == this;
             e = e.next()) {
            if (e.sym == c)
                return true;
        }
        return false;
    }

    static final Filter<Symbol> noFilter = new Filter<Symbol>() {
        public boolean accepts(Symbol s) {
            return true;
        }
    };

    /** Return the entry associated with given name, starting in
     *  this scope and proceeding outwards. If no entry was found,
     *  return the sentinel, which is characterized by having a null in
     *  both its scope and sym fields, whereas both fields are non-null
     *  for regular entries.
     */
    public Entry lookup(Name name) {
        return lookup(name, noFilter);
    }
    // 查找相关符号
    // 在语句及表达式的标注过程中，会频繁调用lookup()方法查找被引用的符号，
    // 这个方法通过传入的Name类型的参数与Filter<Symbol>类型的参数对符号进行快速查找
    public Entry lookup(Name name, Filter<Symbol> sf) {
        // 当通过哈希值得到符号表中对应槽位上的值为空或者为sentinel时，则直接返回sentinel即可，表示没有找到合适的Entry对象。
        Entry e = table[getIndex(name)];
        if (e == null || e == sentinel)
            return sentinel;
        // 当e.scope不为空且名称不一致或名称一致，但是调用Filter类型的方法accepts()返回false时，
        // 则将e更新为e.shadowed后继续查找
        while (e.scope != null && (e.sym.name != name || !sf.accepts(e.sym)))
            e = e.shadowed;
        return e;
    }

    /*void dump (java.io.PrintStream out) {
        out.println(this);
        for (int l=0; l < table.length; l++) {
            Entry le = table[l];
            out.print("#"+l+": ");
            if (le==sentinel) out.println("sentinel");
            else if(le == null) out.println("null");
            else out.println(""+le+" s:"+le.sym);
        }
    }*/

    /** Look for slot in the table.
     *  We use open addressing with double hashing.
     */
    // genIndex()方法获取存储的槽位，如果各个符号的名称相同，则返回的哈希值一定相同
    int getIndex (Name name) {
        // 计算name的hash值
        int h = name.hashCode();
        // 然后与hashMask取与运算，得到table数组中存储的槽位值
        int i = h & hashMask;
        // The expression below is always odd, so it is guaranteed
        // to be mutually prime with table.length, a power of 2.
        int x = hashMask - ((h + (h >> 16)) << 1);
        int d = -1; // Index of a deleted item.
        // for循环中查找合适的存储槽位
        /*
        在每次循环中，取出的槽位值e有以下4种情况。
        1:e为null，这个槽位从来没有存储过相关的Entry对象，默认值为null。当d小于0时才能使用这个槽位值。d小于0则表示之前查找的槽位值没有出现过只存储sentinel的槽位值
        2:e为sentinel，表示这个槽位曾经存储过Entry对象，但是后来这个槽位的Entry对象都删除了，因此只剩下了sentinel。当d小于0时表示找到了一个可以存储值的槽位，直接更新d的值。这里并没有直接返回当前的槽位，因为这很可能不是一个合适的槽位，之前是因为当前的槽位有值，所以为了避免冲突，想要在这个槽位存的Entry对象被存储到了其他槽位上，所以只能继续查找。
        3:存储的符号名称与当前的符号名称相同，直接返回当前槽位即可，将相同名称的Entry对象存储到同一个槽位上。
        4:存储的符号名称与当前的符号名称不同，只能更新槽位i后继续查找。
         */
        for (;;) {
            Entry e = table[i];
            if (e == null)
                return d >= 0 ? d : i;
            if (e == sentinel) {
                // We have to keep searching even if we see a deleted item.
                // However, remember the index in case we fail to find the name.
                if (d < 0)
                    d = i;
            } else if (e.sym.name == name)
                return i;
            i = (i + x) & hashMask;
        }
    }

    public Iterable<Symbol> getElements() {
        return getElements(noFilter);
    }

    public Iterable<Symbol> getElements(final Filter<Symbol> sf) {
        return new Iterable<Symbol>() {
            public Iterator<Symbol> iterator() {
                return new Iterator<Symbol>() {
                    private Scope currScope = Scope.this;
                    private Scope.Entry currEntry = elems;
                    {
                        update();
                    }

                    public boolean hasNext() {
                        return currEntry != null;
                    }

                    public Symbol next() {
                        Symbol sym = (currEntry == null ? null : currEntry.sym);
                        if (currEntry != null) {
                            currEntry = currEntry.sibling;
                        }
                        update();
                        return sym;
                    }

                    public void remove() {
                        throw new UnsupportedOperationException();
                    }

                    private void update() {
                        skipToNextMatchingEntry();
                        while (currEntry == null && currScope.next != null) {
                            currScope = currScope.next;
                            currEntry = currScope.elems;
                            skipToNextMatchingEntry();
                        }
                    }

                    void skipToNextMatchingEntry() {
                        while (currEntry != null && !sf.accepts(currEntry.sym)) {
                            currEntry = currEntry.sibling;
                        }
                    }
                };
            }
        };
    }

    public Iterable<Symbol> getElementsByName(Name name) {
        return getElementsByName(name, noFilter);
    }

    public Iterable<Symbol> getElementsByName(final Name name, final Filter<Symbol> sf) {
        return new Iterable<Symbol>() {
            public Iterator<Symbol> iterator() {
                 return new Iterator<Symbol>() {
                    Scope.Entry currentEntry = lookup(name, sf);

                    public boolean hasNext() {
                        return currentEntry.scope != null;
                    }
                    public Symbol next() {
                        Scope.Entry prevEntry = currentEntry;
                        currentEntry = currentEntry.next(sf);
                        return prevEntry.sym;
                    }
                    public void remove() {
                        throw new UnsupportedOperationException();
                    }
                };
            }
        };
    }

    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("Scope[");
        for (Scope s = this; s != null ; s = s.next) {
            if (s != this) result.append(" | ");
            for (Entry e = s.elems; e != null; e = e.sibling) {
                if (e != s.elems) result.append(", ");
                result.append(e.sym);
            }
        }
        result.append("]");
        return result.toString();
    }

    /** A class for scope entries.
     */
    public static class Entry {

        /** The referenced symbol.
         *  sym == null   iff   this == sentinel
         */
        // sym保存的就是这个Entry对象所封装的符号
        public Symbol sym;

        /** An entry with the same hash code, or sentinel.
         */
        // shadowed用来解决冲突
        // 如果符号的名称相同，则通过单链表来避免冲突。
        // shadowed指向下一个元素，这样就可以支持Java中相同名称的多重定义
        private Entry shadowed;

        /** Next entry in same scope.
         */
        // sibling用来指向单链表的下一个节点；
        public Entry sibling;

        /** The entry's scope.
         *  scope == null   iff   this == sentinel
         *  for an entry in an import scope, this is the scope
         *  where the entry came from (i.e. was imported from).
         */
        // scope为保存sym所属的作用域
        public Scope scope;

        public Entry(Symbol sym, Entry shadowed, Entry sibling, Scope scope) {
            this.sym = sym;
            this.shadowed = shadowed;
            this.sibling = sibling;
            this.scope = scope;
        }

        /** Return next entry with the same name as this entry, proceeding
         *  outwards if not found in this scope.
         */
        public Entry next() {
            return shadowed;
        }

        public Entry next(Filter<Symbol> sf) {
            if (shadowed.sym == null || sf.accepts(shadowed.sym)) return shadowed;
            else return shadowed.next(sf);
        }

        public Scope getOrigin() {
            // The origin is only recorded for import scopes.  For all
            // other scope entries, the "enclosing" type is available
            // from other sources.  See Attr.visitSelect and
            // Attr.visitIdent.  Rather than throwing an assertion
            // error, we return scope which will be the same as origin
            // in many cases.
            return scope;
        }
    }

    public static class ImportScope extends Scope {

        public ImportScope(Symbol owner) {
            super(owner);
        }

        @Override
        Entry makeEntry(Symbol sym, Entry shadowed, Entry sibling, Scope scope, Scope origin) {
            return new ImportEntry(sym, shadowed, sibling, scope, origin);
        }

        static class ImportEntry extends Entry {
            private Scope origin;

            ImportEntry(Symbol sym, Entry shadowed, Entry sibling, Scope scope, Scope origin) {
                super(sym, shadowed, sibling, scope);
                this.origin = origin;
            }

            @Override
            public Scope getOrigin() { return origin; }
        }
    }

    public static class StarImportScope extends ImportScope implements ScopeListener {

        public StarImportScope(Symbol owner) {
            super(owner);
        }

        public void importAll (Scope fromScope) {
            // 循环取出fromScope中的所有符号
            for (Scope.Entry e = fromScope.elems; e != null; e = e.sibling) {
                // includes:判断当前域已经包含引用
                if (e.sym.kind == Kinds.TYP && !includes(e.sym))
                    // 调用Scope类中的enter()方法将符号添加到当前的作用域中
                    enter(e.sym, fromScope);
            }
            // Register to be notified when imported items are removed
            fromScope.addScopeListener(this);
        }

        public void symbolRemoved(Symbol sym, Scope s) {
            remove(sym);
        }
        public void symbolAdded(Symbol sym, Scope s) { }
    }

    /** An empty scope, into which you can't place anything.  Used for
     *  the scope for a variable initializer.
     */
    public static class DelegatedScope extends Scope {
        Scope delegatee;
        public static final Entry[] emptyTable = new Entry[0];

        public DelegatedScope(Scope outer) {
            super(outer, outer.owner, emptyTable);
            delegatee = outer;
        }
        public Scope dup() {
            return new DelegatedScope(next);
        }
        public Scope dupUnshared() {
            return new DelegatedScope(next);
        }
        public Scope leave() {
            return next;
        }
        public void enter(Symbol sym) {
            // only anonymous classes could be put here
        }
        public void enter(Symbol sym, Scope s) {
            // only anonymous classes could be put here
        }
        public void remove(Symbol sym) {
            throw new AssertionError(sym);
        }
        public Entry lookup(Name name) {
            return delegatee.lookup(name);
        }
    }

    /** A class scope adds capabilities to keep track of changes in related
     *  class scopes - this allows client to realize whether a class scope
     *  has changed, either directly (because a new member has been added/removed
     *  to this scope) or indirectly (i.e. because a new member has been
     *  added/removed into a supertype scope)
     */
    public static class CompoundScope extends Scope implements ScopeListener {

        public static final Entry[] emptyTable = new Entry[0];

        private List<Scope> subScopes = List.nil();
        private int mark = 0;

        public CompoundScope(Symbol owner) {
            super(null, owner, emptyTable);
        }

        public void addSubScope(Scope that) {
           if (that != null) {
                subScopes = subScopes.prepend(that);
                that.addScopeListener(this);
                mark++;
                for (ScopeListener sl : listeners) {
                    sl.symbolAdded(null, this); //propagate upwards in case of nested CompoundScopes
                }
           }
         }

        public void symbolAdded(Symbol sym, Scope s) {
            mark++;
            for (ScopeListener sl : listeners) {
                sl.symbolAdded(sym, s);
            }
        }

        public void symbolRemoved(Symbol sym, Scope s) {
            mark++;
            for (ScopeListener sl : listeners) {
                sl.symbolRemoved(sym, s);
            }
        }

        public int getMark() {
            return mark;
        }

        @Override
        public String toString() {
            StringBuilder buf = new StringBuilder();
            buf.append("CompoundScope{");
            String sep = "";
            for (Scope s : subScopes) {
                buf.append(sep);
                buf.append(s);
                sep = ",";
            }
            buf.append("}");
            return buf.toString();
        }

        @Override
        public Iterable<Symbol> getElements(final Filter<Symbol> sf) {
            return new Iterable<Symbol>() {
                public Iterator<Symbol> iterator() {
                    return new CompoundScopeIterator(subScopes) {
                        Iterator<Symbol> nextIterator(Scope s) {
                            return s.getElements(sf).iterator();
                        }
                    };
                }
            };
        }

        @Override
        public Iterable<Symbol> getElementsByName(final Name name, final Filter<Symbol> sf) {
            return new Iterable<Symbol>() {
                public Iterator<Symbol> iterator() {
                    return new CompoundScopeIterator(subScopes) {
                        Iterator<Symbol> nextIterator(Scope s) {
                            return s.getElementsByName(name, sf).iterator();
                        }
                    };
                }
            };
        }

        abstract class CompoundScopeIterator implements Iterator<Symbol> {

            private Iterator<Symbol> currentIterator;
            private List<Scope> scopesToScan;

            public CompoundScopeIterator(List<Scope> scopesToScan) {
                this.scopesToScan = scopesToScan;
                update();
            }

            abstract Iterator<Symbol> nextIterator(Scope s);

            public boolean hasNext() {
                return currentIterator != null;
            }

            public Symbol next() {
                Symbol sym = currentIterator.next();
                if (!currentIterator.hasNext()) {
                    update();
                }
                return sym;
            }

            public void remove() {
                throw new UnsupportedOperationException();
            }

            private void update() {
                while (scopesToScan.nonEmpty()) {
                    currentIterator = nextIterator(scopesToScan.head);
                    scopesToScan = scopesToScan.tail;
                    if (currentIterator.hasNext()) return;
                }
                currentIterator = null;
            }
        }

        @Override
        public Entry lookup(Name name, Filter<Symbol> sf) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Scope dup(Symbol newOwner) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void enter(Symbol sym, Scope s, Scope origin) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void remove(Symbol sym) {
            throw new UnsupportedOperationException();
        }
    }

    /** An error scope, for which the owner should be an error symbol. */
    public static class ErrorScope extends Scope {
        ErrorScope(Scope next, Symbol errSymbol, Entry[] table) {
            super(next, /*owner=*/errSymbol, table);
        }
        public ErrorScope(Symbol errSymbol) {
            super(errSymbol);
        }
        public Scope dup() {
            return new ErrorScope(this, owner, table);
        }
        public Scope dupUnshared() {
            return new ErrorScope(this, owner, table.clone());
        }
        public Entry lookup(Name name) {
            Entry e = super.lookup(name);
            if (e.scope == null)
                return new Entry(owner, null, null, null);
            else
                return e;
        }
    }
}
