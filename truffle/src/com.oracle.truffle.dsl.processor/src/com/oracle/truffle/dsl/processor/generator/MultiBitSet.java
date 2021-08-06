/*
 * Copyright (c) 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.generator;

import java.util.List;

import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.FrameState;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;

class MultiBitSet<T extends BitSet> {

    private final List<T> sets;

    MultiBitSet(List<T> sets) {
        this.sets = sets;
    }

    public List<T> getSets() {
        return sets;
    }

    public int getCapacity() {
        int length = 0;
        for (BitSet a : sets) {
            length += a.getCapacity();
        }
        return length;
    }

    public CodeTree createContains(FrameState frameState, Object[] elements) {
        return createContainsImpl(sets, frameState, elements);
    }

    protected static CodeTree createContainsImpl(List<? extends BitSet> sets, FrameState frameState, Object[] elements) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        String sep = "";
        if (sets.size() > 1) {
            builder.string("(");
        }
        for (BitSet set : sets) {
            Object[] included = set.filter(elements);
            if (included.length > 0) {
                builder.string(sep);
                builder.tree(set.createContains(frameState, included));
                sep = " || ";
            }
        }
        if (sets.size() > 1) {
            builder.string(")");
        }
        return builder.build();
    }

    public CodeTree createSet(FrameState frameState, Object[] elements, boolean value, boolean persist) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        for (BitSet set : sets) {
            Object[] included = set.filter(elements);

            if (included.length > 0 || persist) {
                builder.tree(set.createSet(frameState, included, value, persist));
            }
        }
        return builder.build();
    }

    public CodeTree createContainsOnly(FrameState frameState, int offset, int length, Object[] selectedElements, Object[] allElements) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        String sep = "";
        for (BitSet set : sets) {
            Object[] selected = set.filter(selectedElements);
            Object[] filteredAll = set.filter(allElements);
            if (filteredAll.length > 0) {
                CodeTree containsOnly = set.createContainsOnly(frameState, offset, length, selected, filteredAll);
                if (containsOnly != null) {
                    builder.string(sep);
                    builder.tree(containsOnly);
                    sep = " && ";
                }
            }
        }
        return builder.build();
    }

    public CodeTree createIs(FrameState frameState, Object[] selectedElements, Object[] maskedElements) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        String sep = "";
        for (BitSet set : sets) {
            Object[] selected = set.filter(selectedElements);
            Object[] masked = set.filter(maskedElements);
            if (masked.length > 0) {
                builder.string(sep);
                builder.tree(set.createIs(frameState, selected, masked));
                sep = " && ";
            }
        }
        return builder.build();
    }

    public CodeTree createIsNotAny(FrameState frameState, Object[] elements) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.string("(");
        String sep = "";
        for (BitSet set : sets) {
            Object[] setElements = set.filter(elements);
            if (setElements.length > 0) {
                builder.string(sep);
                builder.tree(set.createIsNotAny(frameState, setElements));
                sep = " || "; // exclusive or needed for one bit check
            }
        }
        builder.string(")");
        return builder.build();
    }

    public CodeTree createExtractInteger(FrameState frameState, Object element) {
        for (BitSet set : sets) {
            if (set.contains(element)) {
                return set.createExtractInteger(frameState, element);
            }
        }
        throw new AssertionError("element not contained");
    }

    public CodeTree createNotContains(FrameState frameState, Object[] elements) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        String sep = "";
        for (BitSet set : sets) {
            Object[] setElements = set.filter(elements);
            if (setElements.length > 0) {
                builder.string(sep);
                builder.tree(set.createNotContains(frameState, setElements));
                sep = " && "; // exclusive or needed for one bit check
            }
        }
        return builder.build();
    }

    public CodeTree createSetInteger(FrameState frameState, Object element, CodeTree value) {
        for (BitSet set : sets) {
            if (set.contains(element)) {
                return set.createSetInteger(frameState, element, value);
            }
        }
        throw new AssertionError("element not contained");
    }

}
