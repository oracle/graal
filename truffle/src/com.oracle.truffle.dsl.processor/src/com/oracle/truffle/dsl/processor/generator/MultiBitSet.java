/*
 * Copyright (c) 2020, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

import com.oracle.truffle.dsl.processor.generator.FlatNodeGenFactory.FrameState;
import com.oracle.truffle.dsl.processor.java.model.CodeTree;
import com.oracle.truffle.dsl.processor.java.model.CodeTreeBuilder;

class MultiBitSet {

    private final List<BitSet> sets;

    MultiBitSet(List<BitSet> sets) {
        this.sets = sets;
    }

    public List<BitSet> getSets() {
        return sets;
    }

    public int getCapacity() {
        int length = 0;
        for (BitSet a : sets) {
            length += a.getBitCount();
        }
        return length;
    }

    public CodeTree createContains(FrameState frameState, StateQuery elements) {
        return createContainsImpl(sets, frameState, elements);
    }

    protected static CodeTree createContainsImpl(List<? extends BitSet> sets, FrameState frameState, StateQuery elements) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        List<CodeTree> conditions = new ArrayList<>();
        for (BitSet set : sets) {
            StateQuery included = set.filter(elements);
            if (!included.isEmpty()) {
                conditions.add(set.createContains(frameState, included));
            }
        }

        if (conditions.size() > 1) {
            builder.string("(");
        }
        String sep = "";
        for (CodeTree tree : conditions) {
            builder.string(sep);
            builder.tree(tree);
            sep = " || ";
        }
        if (conditions.size() > 1) {
            builder.string(")");
        }
        return builder.build();
    }

    static final class StateTransaction {

        private final LinkedHashSet<BitSet> modified = new LinkedHashSet<>();

        void markModified(BitSet bitSet) {
            modified.add(bitSet);
        }

    }

    public CodeTree persistTransaction(FrameState frameState, StateTransaction transaction) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        for (BitSet set : transaction.modified) {
            if (set.hasLocal(frameState)) {
                builder.tree(set.createSet(frameState, null, null, true));
            } else {
                throw new AssertionError("Cannot persist transaction state local without a local variable in the frame state.");
            }
        }
        return builder.build();

    }

    public CodeTree createSet(FrameState frameState, StateTransaction transaction, StateQuery query, boolean value, boolean persist) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        for (BitSet set : sets) {
            StateQuery included = set.getStates().filter(query);
            if (!included.isEmpty()) {
                if (transaction != null) {
                    if (!set.hasLocal(frameState) || persist) {
                        throw new AssertionError("Must be loaded for transactional write.");
                    }
                    transaction.modified.add(set);
                }
                builder.tree(set.createSet(frameState, included, value, persist));
            }
        }
        return builder.build();
    }

    public CodeTree createSetInteger(FrameState frameState, StateTransaction transaction, StateQuery element, CodeTree value) {
        for (BitSet set : sets) {
            if (set.contains(element)) {
                if (transaction != null) {
                    if (!set.hasLocal(frameState)) {
                        throw new AssertionError("Cannot use transactions without the state being loaded.");
                    }
                    transaction.modified.add(set);
                }
                return set.createSetInteger(frameState, element, value);
            }
        }
        throw new AssertionError("element not contained");
    }

    public CodeTree createContainsOnly(FrameState frameState, int offset, int length, StateQuery selectedElements, StateQuery allElements) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        String sep = "";
        for (BitSet set : sets) {
            StateQuery selected = set.filter(selectedElements);
            StateQuery filteredAll = set.filter(allElements);
            if (!filteredAll.isEmpty()) {
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

    public CodeTree createIs(FrameState frameState, StateQuery selectedElements, StateQuery maskedElements) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        String sep = "";
        for (BitSet set : sets) {
            StateQuery masked = set.filter(maskedElements);
            if (!masked.isEmpty()) {
                StateQuery selected = set.filter(selectedElements);
                builder.string(sep);
                builder.tree(set.createIs(frameState, selected, masked));
                sep = " && ";
            }
        }
        return builder.build();
    }

    public CodeTree createIsNotAny(FrameState frameState, StateQuery elements) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        builder.string("(");
        String sep = "";
        for (BitSet set : sets) {
            StateQuery filteredElements = set.filter(elements);
            if (!filteredElements.isEmpty()) {
                builder.string(sep);
                builder.tree(set.createIsNotAny(frameState, filteredElements));
                sep = " || "; // exclusive or needed for one bit check
            }
        }
        builder.string(")");
        return builder.build();
    }

    public CodeTree createExtractInteger(FrameState frameState, StateQuery element) {
        for (BitSet set : sets) {
            if (set.contains(element)) {
                return set.createExtractInteger(frameState, element);
            }
        }
        throw new AssertionError("element not contained");
    }

    public CodeTree createNotContains(FrameState frameState, StateQuery elements) {
        CodeTreeBuilder builder = CodeTreeBuilder.createBuilder();
        String sep = "";
        for (BitSet set : sets) {
            StateQuery setElements = set.filter(elements);
            if (!setElements.isEmpty()) {
                builder.string(sep);
                builder.tree(set.createNotContains(frameState, setElements));
                sep = " && "; // exclusive or needed for one bit check
            }
        }
        return builder.build();
    }

}
