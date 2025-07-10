/*
 * Copyright (c) 2024, 2025, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.buffer.LongArrayBuffer;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

/**
 * Attached to a DFA transitions, they represent the operations applied to the datastructures
 * tracking quantifier values at runtime (see
 * {@link com.oracle.truffle.regex.tregex.nodes.dfa.CounterTracker}). Each quantifier is tracked by
 * a {@link com.oracle.truffle.regex.tregex.nodes.dfa.CounterTracker}, and each operation target one
 * tracker with the value of a source (except for set1).
 * <p>
 * For instance, if we take the expression {@code .(?:bc){10,20}d}, it contains one quantifier, and
 * the scope of this quantifier contains two nfa state 'b' and 'c'. At the nfa level, it will
 * contain three transition with some operations:
 * 
 * <pre>{@code
 * . -> b: tracker(b) = set1
 * b -> c: tracker(c) = tracker(b)
 * c -> b: tracker(b) = (tracker(c) + 1)}</pre>
 * 
 * The structure of this operation is always the same, it goes from a source state (except for set1)
 * has a certain kind, and the result is put in a target nfa state.
 * <p>
 * On the dfa level things gets a little bit different, because multiple transition and therefore
 * multiple operations are taken at the same time. If we take the same example, on the input "abcb",
 * when reading the last b, in the dfa, we will take both the transition from . -> b, and the
 * transition from c -> b, which would give the following operations.
 * 
 * <pre>{@code
 * tracker(b) = tracker(c)
 * tracker(b) = set1
 *}</pre>
 *
 * But this is not correct since the second operation will always overwrite the result of the first
 * one. Instead, it will get merged like this:
 * 
 * <pre>{@code
 * tracker(b) = tracker(c)
 * tracker(b) = tracker(b) union set1
 *}</pre>
 * 
 * Other modifications needs to be taken, in some cases we need to store some intermediary result in
 * some temporary cell happens generally when a state is both a target and then a source later on in
 * a sequence of operation.
 * <p>
 * To make our life easier instead of using the actual NFA state ids, we map all nfa states in the
 * scope of a quantifier to the range 0,1,2,3,..., that way they can be uses as indices in an array.
 */
public class TransitionOp {
    public static final int NO_SOURCE = 0xFFFF;
    public static final long[] NO_OP = new long[]{};

    // Kind
    // Put the source in the target without modification.
    public static final int maintain = 0;
    // Increment all counter values by from the source by 1 and put it in the destination, and
    // remove the top one if it becomes bigger than max
    public static final int inc = 1;
    // Add 1 to the target
    public static final int set1 = 2;

    // Modifiers.
    /**
     * placeholder before the modifier is chosen.
     */
    public static final int noModifier = 0;
    /**
     * overwrite the target set with source set.
     */
    public static final int overwrite = 1;
    /**
     * write the union of source and target set into target set.
     */
    public static final int union = 2;
    /**
     * swap source and target set. allowed only on the {@link #maintain} operation.
     */
    public static final int swap = 3;

    private static final int OFFSET_SOURCE = 0;
    private static final int OFFSET_TARGET = 16;
    private static final int OFFSET_MODIFIER = 32;
    private static final int OFFSET_KIND = 34;
    private static final int OFFSET_QUANTIFIER_ID = 36;
    private static final int MASK_ID = 0xFFFF;
    private static final int MASK_MODIFIER = 0b11;
    private static final int MASK_KIND = 0b11;

    /**
     * @param qId ID of the quantifier on which this operation is applied
     * @param source ID of the source (NFA state) of this operation
     * @param target ID of the target (NFA state) of this operation
     * @param kind Operation kind
     */
    public static long create(int qId, int source, int target, int kind) {
        return create(qId, source, target, kind, noModifier);
    }

    /**
     * @param qId ID of the quantifier on which this operation is applied
     * @param source ID of the source (NFA state) of this operation
     * @param target ID of the target (NFA state) of this operation
     * @param kind Operation kind
     * @param modifier Operation modifier
     */
    public static long create(int qId, int source, int target, int kind, int modifier) {
        assert (qId & MASK_ID) == qId;
        assert (source & MASK_ID) == source;
        assert (target & MASK_ID) == target;
        long actualSource = source;
        if (kind == set1) {
            actualSource = NO_SOURCE;
        }
        return ((long) qId << OFFSET_QUANTIFIER_ID) | ((long) kind << OFFSET_KIND) | ((long) modifier << OFFSET_MODIFIER) | ((long) target << OFFSET_TARGET) | (actualSource << OFFSET_SOURCE);
    }

    public static long setTarget(long op, int newTarget) {
        return create(getQuantifierID(op), getSource(op), newTarget, getKind(op), getModifier(op));
    }

    public static long setSourceAndTarget(long op, int newSource, int newTarget) {
        return create(getQuantifierID(op), newSource, newTarget, getKind(op), getModifier(op));
    }

    /**
     * Sort the operations and remove duplicates (in-place). Ops are sorted according to the
     * following dimensions:
     * <ul>
     * <li>Quantifier ID</li>
     * <li>Kind, in order: [maintain, inc, setMin, set1]</li>
     * <li>Modifier, in order: [noModifier, overwrite, move, union]</li>
     * <li>Target ID</li>
     * <li>Source ID</li>
     * </ul>
     */
    public static void normalize(LongArrayBuffer operations) {
        operations.sort();
        var prev = -1L;
        var cursor = 0;
        for (Long op : operations) {
            if (prev != op) {
                operations.set(cursor, op);
                prev = op;
                cursor++;
            }
        }
        operations.setLength(cursor);
    }

    /**
     * Return the source of this operation, or {@link TransitionOp#NO_SOURCE} if the operation is
     * {@link TransitionOp#set1}.
     */
    public static int getSource(long op) {
        var result = (int) ((op >> OFFSET_SOURCE) & MASK_ID);
        var operation = getKind(op);
        assert operation != set1 || result == NO_SOURCE;
        return result;
    }

    /**
     * Return the target of this operation.
     */
    public static int getTarget(long op) {
        return (int) ((op >> OFFSET_TARGET) & MASK_ID);
    }

    public static int getKind(long op) {
        return (int) ((op >> OFFSET_KIND) & MASK_KIND);
    }

    public static int getQuantifierID(long op) {
        return (int) ((op >>> OFFSET_QUANTIFIER_ID) & MASK_ID);
    }

    public static long setModifier(int modifier, long op) {
        assert modifier >= 0 && modifier < 4;
        assert getModifier(op) == noModifier;

        return (((long) modifier) << OFFSET_MODIFIER) | op;
    }

    public static int getModifier(long op) {
        return (int) ((op >> OFFSET_MODIFIER) & MASK_MODIFIER);
    }

    @TruffleBoundary
    public static String toString(long operation) {
        int sourceId = getSource(operation);
        int targetId = getTarget(operation);
        int modifier = getModifier(operation);

        int op = getKind(operation);
        var builder = new StringBuilder("qId: %d, %d ".formatted(getQuantifierID(operation), targetId));
        // Checkstyle: stop
        switch (modifier) {
            case overwrite -> builder.append("= ");
            case noModifier -> builder.append("<- ");
            case union -> builder.append("= %d ⋃ ".formatted(targetId));
            case swap -> builder.append("<-> ");
            default -> throw CompilerDirectives.shouldNotReachHere();
        }
        // Checkstyle: resume
        switch (op) {
            case inc -> builder.append("inc(%d)".formatted(sourceId));
            case set1 -> builder.append("set1");
            case maintain -> builder.append(sourceId);
            default -> throw CompilerDirectives.shouldNotReachHere();
        }
        return builder.toString();
    }

    @TruffleBoundary
    public static JsonValue toJson(long op) {
        return Json.val(toString(op));
    }
}
