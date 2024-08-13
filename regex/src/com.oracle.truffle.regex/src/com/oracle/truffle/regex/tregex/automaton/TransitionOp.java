/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.regex.tregex.nodes.dfa.TrackerCellAllocator;
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
 * multiple operations are taken at the same time. If we thake the same example, on the input
 * "abcb", when reading the last b, in the dfa, we will take both the transition from . -> b, and
 * the transition from c -> b, which would give the following operations.
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
 * a sequence of operation. This is all handled by
 * {@link TransitionOp#prepareForExecutor(LongArrayBuffer, TrackerCellAllocator[])}.
 * <p>
 * To make our life easier instead of using the actual NFA state ids, we map all nfa states in the
 * scope of a quantifier to the range 0,1,2,3,..., that way they can be uses as indices in an array.
 */
public class TransitionOp {
    public static final int NO_SOURCE = 0xFFFF;
    public static final long[] NO_OP = new long[]{};

    // Kind
    // Increment all counter values by from the source by 1 and put it in the destination, and
    // remove the top one if it becomes bigger than max
    public static final int inc = 0;
    // Add 1 to the target
    public static final int set1 = 1;
    // Set the target to all possible counter values from the current minimum value in the source
    // and minimum.
    public static final int setMin = 2;
    // Put the source in the target without modification.
    public static final int maintain = 3;

    // Modifiers
    // placeholder before the modifier is chosen
    public static final int noModifier = 0;
    // Overwrite the target value
    public static final int overwrite = 1;
    // Similar to overwrite, but the storage of the source can be reused.
    public static final int move = 2;
    // Take the union with the current value in target.
    public static final int union = 3;

    private static final int STATE_MASK = 0xFFFF;
    private static final int MAX_QID = 0xFFFFFFF;

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
        assert (qId & MAX_QID) == qId;
        assert (source & 0xFFFF) == source;
        assert (target & 0xFFFF) == target;
        long actualSource = source;
        if (kind == set1) {
            actualSource = NO_SOURCE;
        }
        // Each sID are 16 bits
        // and each kind takes 2 bits. The 2 Msb are used to store the modifiers (default to none);
        return (long) modifier << 62 | ((long) qId << 34) | ((long) target << 18) | (actualSource << 2) | kind;
    }

    /**
     * Sort the operations and remove duplicates (in-place).
     */
    private static void normalize(LongArrayBuffer operations) {
        operations.sort();
        var prev = -1L;
        var cursor = 0;
        for (var op : operations) {
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
        var result = (int) ((op >> 2) & STATE_MASK);
        var operation = getKind(op);
        assert operation != set1 || result == NO_SOURCE;
        return result;
    }

    public static int getTarget(long op) {
        return (int) ((op >> 18) & STATE_MASK);
    }

    public static int getKind(long op) {
        return (int) (op & 0b11);
    }

    public static int getQId(long op) {
        return (int) ((op >>> 34) & MAX_QID);
    }

    public static long setModifier(int modifier, long op) {
        assert modifier >= 0 && modifier < 4;
        assert getModifier(op) == noModifier;

        return ((long) modifier) << 62 | op;
    }

    public static int getModifier(long op) {
        return (int) (op >>> 62);
    }

    @TruffleBoundary
    public static String toString(long operation) {
        var sourceId = formatId(getSource(operation));
        var targetId = formatId(getTarget(operation));
        int modifier = getModifier(operation);

        int op = getKind(operation);
        var builder = new StringBuilder("qId = %d, %s ".formatted(getQId(operation), targetId));
        // Checkstyle: stop
        switch (modifier) {
            case overwrite -> builder.append("= ");
            case move -> builder.append("<-> ");
            case noModifier -> builder.append("<- ");
            case union -> builder.append("= %s ⋃ ".formatted(targetId));
            default -> throw CompilerDirectives.shouldNotReachHere();
        }
        // Checkstyle: resume
        switch (op) {
            case inc -> builder.append("inc(%s)".formatted(sourceId));
            case set1 -> builder.append("set1");
            case setMin -> builder.append("setMin(%s)".formatted(sourceId));
            case maintain -> builder.append("%s".formatted(sourceId));
            default -> throw CompilerDirectives.shouldNotReachHere();
        }
        return builder.toString();
    }

    private static String formatId(int id) {
        return "%d".formatted(id);
    }

    @TruffleBoundary
    public static JsonValue toJson(long op) {
        return Json.val(toString(op));
    }

    public static long[] prepareForExecutor(LongArrayBuffer operations, TrackerCellAllocator[] cellAllocators) {
        if (operations.isEmpty()) {
            return new long[0];
        }
        normalize(operations);
        var length = operations.length();
        var prevQid = getQId(operations.get(0));
        var prevFrom = 0;
        for (int i = 0; i < length; i++) {
            var qId = getQId(operations.get(i));
            if (qId != prevQid) {
                optimize(operations, cellAllocators[prevQid], prevFrom, i);
                cellAllocators[prevQid].resetTemp();
                prevQid = qId;
                prevFrom = i;
            }
        }
        optimize(operations, cellAllocators[prevQid], prevFrom, length);
        cellAllocators[prevQid].resetTemp();
        return operations.toArray();
    }

    private static void optimize(LongArrayBuffer operations, TrackerCellAllocator cellAllocator, int from, int to) {

        // This algorithm is O(n^3), but it can be implemented in O(n^2) as well. It could maybe be
        // faster.
        // However, note that n is often very small (<10), so the extra complexity does not seem
        // worth it.
        int i = from;
        int qId = getQId(operations.get(from));
        // First, we sort the operations such that we minimize the use of temporary values.
        while (i < to) {
            // Find all instructions whose target has the smallest use count after i.
            var bestIndex = i;
            var bestCount = Integer.MAX_VALUE;
            for (int k = i; k < to; k++) {
                int target = getTarget(operations.get(k));
                int count = 0;
                for (int l = i; l < to; l++) {
                    /*
                     * We compute the use count assuming instruction k will be the first to be
                     * executed, therefore its own source won't be affected. This is particularly
                     * important to avoid copying when we have self loops, like "1 <- inc(1)".
                     */
                    if (l == k) {
                        continue;
                    }

                    if (getSource(operations.get(l)) == target) {
                        count += 1;
                    }
                    if (count >= bestCount) {
                        break;
                    }
                }
                if (count < bestCount) {
                    bestCount = count;
                    bestIndex = k;
                }
            }
            var bestTarget = getTarget(operations.get(bestIndex));
            var newTarget = bestTarget;
            if (bestCount > 0) {
                /*
                 * If bestCount > 0, then we use a temporary cell to store the result and swap it
                 * back at the end
                 */
                newTarget = cellAllocator.allocTemp();
                operations.add(setModifier(move, create(qId, newTarget, bestTarget, maintain)));
            }
            var sortFrom = i;
            // Bubble up all operations whose target is bestTarget
            for (int k = i; k < to; k++) {
                var op = operations.get(k);
                if (getTarget(op) == bestTarget) {
                    operations.set(k, operations.get(i));
                    op = setTarget(newTarget, op);
                    operations.set(i, op);
                    i += 1;
                }
            }
            var sortTo = i - 1;
            // Reorder them such that self loop are on the top, and set1/setMin are at the end
            for (int k = sortFrom; k <= sortTo; k++) {
                var op = operations.get(k);
                var kind = TransitionOp.getKind(op);
                // Self loop
                if (TransitionOp.getSource(op) == bestTarget) {
                    operations.swap(sortFrom, k);
                    sortFrom++;
                } else if (kind == set1 || kind == setMin) {
                    operations.swap(k, sortTo);
                    sortTo--;
                    // Checkstyle: stop
                    k--;
                    // Checkstyle: resume
                }
            }
        }

        // Set all modifiers: either swap or overwrite for the first operations to a new target.
        // Then union for the others.
        i = from;
        int prevTarget = -1;
        while (i < to) {
            var op = operations.get(i);
            var target = getTarget(op);
            if (target != prevTarget) {
                prevTarget = target;
                var source = getSource(op);
                boolean canSwap = true;
                if (source != NO_SOURCE) {
                    for (int k = i + 1; k < to; k++) {
                        var op2 = operations.get(k);
                        var source2 = getSource(op2);
                        if (source2 == source) {
                            canSwap = false;
                            break;
                        }
                    }
                } else {
                    canSwap = false;
                }
                if (canSwap) {
                    op = setModifier(move, op);
                } else {
                    op = setModifier(overwrite, op);
                }
            } else {
                op = setModifier(union, op);
            }
            operations.set(i, op);
            i++;
        }
    }

    private static long setTarget(int newTarget, long op) {
        var qId = getQId(op);
        var src = getSource(op);
        var o = getKind(op);
        var modifier = getModifier(op);
        return create(qId, src, newTarget, o, modifier);
    }
}
