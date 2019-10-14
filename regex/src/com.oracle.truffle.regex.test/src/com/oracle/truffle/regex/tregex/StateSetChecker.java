/*
 * Copyright (c) 2019, 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.regex.tregex;

import java.util.Collection;
import java.util.Iterator;

import com.oracle.truffle.regex.tregex.automaton.StateIndex;
import com.oracle.truffle.regex.tregex.automaton.StateSet;

public class StateSetChecker<S> implements StateSet<S> {

    private final StateSet<S> a;
    private final StateSet<S> b;

    public StateSetChecker(StateSet<S> a, StateSet<S> b) {
        this.a = a;
        this.b = b;
        assert a.equals(b);
    }

    @Override
    public int size() {
        assert a.size() == b.size();
        return a.size();
    }

    @Override
    public boolean isEmpty() {
        assert a.isEmpty() == b.isEmpty();
        return a.isEmpty();
    }

    @Override
    public boolean contains(Object o) {
        assert a.contains(o) == b.contains(o);
        return a.contains(o);
    }

    @Override
    public Iterator<S> iterator() {
        return a.iterator();
    }

    @Override
    public boolean add(S e) {
        boolean r1 = a.add(e);
        boolean r2 = b.add(e);
        assert r1 == r2;
        assert a.equals(b);
        return r1;
    }

    @Override
    public boolean remove(Object o) {
        boolean r1 = a.remove(o);
        boolean r2 = b.remove(o);
        assert r1 == r2;
        assert a.equals(b);
        return r1;
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        boolean r1 = a.containsAll(extractA(c));
        boolean r2 = b.containsAll(extractB(c));
        assert r1 == r2;
        return r1;
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean addAll(Collection<? extends S> c) {
        boolean r1 = a.addAll((Collection<S>) extractA(c));
        boolean r2 = b.addAll((Collection<S>) extractB(c));
        assert r1 == r2;
        assert a.equals(b);
        return r1;
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        boolean r1 = a.retainAll(extractA(c));
        boolean r2 = b.retainAll(extractB(c));
        assert r1 == r2;
        assert a.equals(b);
        return r1;
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        boolean r1 = a.removeAll(extractA(c));
        boolean r2 = b.removeAll(extractB(c));
        assert r1 == r2;
        assert a.equals(b);
        return r1;
    }

    @Override
    public void clear() {
        a.clear();
        b.clear();
        assert a.equals(b);
    }

    @Override
    public StateSet<S> copy() {
        StateSet<S> r1 = a.copy();
        StateSet<S> r2 = b.copy();
        assert r1.equals(r2);
        return r1;
    }

    @Override
    public StateIndex<? super S> getStateIndex() {
        return a.getStateIndex();
    }

    @Override
    public void addBatch(S state) {
        a.addBatch(state);
        b.addBatch(state);
    }

    @Override
    public void addBatchFinish() {
        assert a.equals(b);
    }

    @Override
    public void replace(S oldState, S newState) {
        a.replace(oldState, newState);
        b.replace(oldState, newState);
        assert a.equals(b);
    }

    @SuppressWarnings("unchecked")
    @Override
    public boolean isDisjoint(StateSet<? extends S> other) {
        boolean r1 = a.isDisjoint(((StateSetChecker<S>) other).a);
        boolean r2 = b.isDisjoint(((StateSetChecker<S>) other).b);
        assert r1 == r2;
        return r1;
    }

    @SuppressWarnings("unchecked")
    private Collection<?> extractA(Collection<?> c) {
        if (c instanceof StateSetChecker) {
            return ((StateSetChecker<S>) c).a;
        }
        return c;
    }

    @SuppressWarnings("unchecked")
    private Collection<?> extractB(Collection<?> c) {
        if (c instanceof StateSetChecker) {
            return ((StateSetChecker<S>) c).b;
        }
        return c;
    }
}
