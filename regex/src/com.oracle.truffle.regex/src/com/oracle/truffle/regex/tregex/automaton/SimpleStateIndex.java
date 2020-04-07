/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.regex.tregex.automaton;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;

/**
 * Simple base class for implementors of {@link StateIndex}.
 */
public abstract class SimpleStateIndex<T> implements StateIndex<T>, Iterable<T> {

    private final ArrayList<T> states;
    private StateSet<SimpleStateIndex<T>, T> emptySet;

    protected SimpleStateIndex() {
        states = new ArrayList<>();
    }

    protected SimpleStateIndex(int size) {
        states = new ArrayList<>(size);
    }

    public void add(T state) {
        assert !states.contains(state);
        setStateId(state, states.size());
        states.add(state);
        assert states.get(getStateId(state)) == state;
    }

    protected abstract int getStateId(T state);

    protected abstract void setStateId(T state, int id);

    @Override
    public int getNumberOfStates() {
        return states.size();
    }

    @Override
    public int getId(T state) {
        int id = getStateId(state);
        assert states.get(id) == state;
        return id;
    }

    @Override
    public T getState(int id) {
        return states.get(id);
    }

    public StateSet<SimpleStateIndex<T>, T> getEmptySet() {
        if (emptySet == null) {
            emptySet = StateSet.create(this);
        }
        return emptySet;
    }

    public int size() {
        return states.size();
    }

    public T get(int i) {
        return states.get(i);
    }

    @Override
    public Iterator<T> iterator() {
        return states.iterator();
    }

    @TruffleBoundary
    @Override
    public Spliterator<T> spliterator() {
        return Spliterators.spliterator(iterator(), states.size(), Spliterator.DISTINCT | Spliterator.NONNULL);
    }

    @TruffleBoundary
    public Stream<T> stream() {
        return StreamSupport.stream(spliterator(), false);
    }
}
