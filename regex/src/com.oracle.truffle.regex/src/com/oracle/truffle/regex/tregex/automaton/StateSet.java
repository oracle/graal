/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonConvertible;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

/**
 * A specialized set for sequentially indexed objects. Uses an {@link StateIndex index} for mapping
 * indices to actual objects.
 */
public interface StateSet<SI extends StateIndex<? super S>, S> extends Set<S>, Iterable<S>, JsonConvertible {

    static <SI extends StateIndex<? super S>, S> StateSet<SI, S> create(SI stateIndex) {
        return new StateSetImpl<>(stateIndex);
    }

    static <SI extends StateIndex<? super S>, S> StateSet<SI, S> create(SI stateIndex, S initial) {
        StateSet<SI, S> s = create(stateIndex);
        s.add(initial);
        return s;
    }

    static <SI extends StateIndex<? super S>, S> StateSet<SI, S> create(SI stateIndex, Collection<S> initial) {
        StateSet<SI, S> s = create(stateIndex);
        s.addAll(initial);
        return s;
    }

    StateSet<SI, S> copy();

    SI getStateIndex();

    boolean isDisjoint(StateSet<SI, ? extends S> other);

    /**
     * Returns the hash code value for this set.
     *
     * Note that unlike other {@link Set}s, the hash code value returned by this implementation is
     * <em>not</em> the sum of the hash codes of its elements.
     *
     * @see Set#hashCode()
     */
    @Override
    int hashCode();

    default int[] toArrayOfIndices() {
        int[] array = new int[size()];
        int i = 0;
        for (S s : this) {
            array[i++] = getStateIndex().getId(s);
        }
        assert isSorted(array);
        return array;
    }

    @Override
    default Object[] toArray() {
        Object[] ret = new Object[size()];
        int i = 0;
        for (S s : this) {
            ret[i++] = s;
        }
        return ret;
    }

    @SuppressWarnings("unchecked")
    @Override
    default <T> T[] toArray(T[] a) {
        T[] r = a.length >= size() ? a : (T[]) java.lang.reflect.Array.newInstance(a.getClass().getComponentType(), size());
        int i = 0;
        for (S s : this) {
            r[i++] = (T) s;
        }
        return r;
    }

    @TruffleBoundary
    @Override
    default Stream<S> stream() {
        return StreamSupport.stream(this.spliterator(), false);
    }

    @TruffleBoundary
    default String defaultToString() {
        return stream().map(S::toString).collect(Collectors.joining(",", "{", "}"));
    }

    @TruffleBoundary
    @Override
    default JsonValue toJson() {
        return Json.array(this);
    }

    static boolean isSorted(int[] array) {
        int prev = Integer.MIN_VALUE;
        for (int i : array) {
            if (prev > i) {
                return false;
            }
            prev = i;
        }
        return true;
    }
}
