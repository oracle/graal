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
package com.oracle.truffle.regex.tregex.automaton;

import java.util.ArrayList;
import java.util.Iterator;

public class SimpleStateIndex<T> implements StateIndex<T>, Iterable<T> {

    @FunctionalInterface
    public interface IdGetter<T> {
        short getId(T state);
    }

    @FunctionalInterface
    public interface IdSetter<T> {
        void setId(T state, short id);
    }

    private final IdGetter<T> idGetter;
    private final IdSetter<T> idSetter;
    private final ArrayList<T> states = new ArrayList<>();
    private StateSet<T> emptySet;

    public SimpleStateIndex(IdGetter<T> idGetter, IdSetter<T> idSetter) {
        this.idGetter = idGetter;
        this.idSetter = idSetter;
    }

    public void add(T rootNode) {
        assert !states.contains(rootNode);
        idSetter.setId(rootNode, (short) states.size());
        states.add(rootNode);
    }

    @Override
    public int getNumberOfStates() {
        return states.size();
    }

    @Override
    public short getId(T state) {
        short id = idGetter.getId(state);
        assert states.get(id) == state;
        return id;
    }

    @Override
    public T getState(int id) {
        return states.get(id);
    }

    public StateSet<T> getEmptySet() {
        if (emptySet == null) {
            emptySet = StateSet.create(this);
        }
        return emptySet;
    }

    public boolean isEmpty() {
        return states.isEmpty();
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
}
