/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

import java.util.Arrays;
import java.util.stream.Stream;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.regex.tregex.buffer.LongArrayBuffer;
import com.oracle.truffle.regex.tregex.buffer.ObjectArrayBuffer;
import com.oracle.truffle.regex.tregex.util.json.Json;
import com.oracle.truffle.regex.tregex.util.json.JsonValue;

public class TransitionConstraint {
    public static final long[] NO_CONSTRAINTS = new long[]{};

    /*
     * Kinds. Note that their value is chosen such that the negation of a kind is given by flipping
     * its least significant bit.
     */
    public static final int existLtMax = 0;
    public static final int allGeMax = 1;
    public static final int existGeMin = 2;
    public static final int allLtMin = 3;
    public static final int existLtMin = 4;
    public static final int allGeMin = 5;

    public static long create(int quantId, int stateId, int kind) {
        assert (stateId & 0x1FFFFFFF) == stateId;
        assert kind < 6;
        /*
         * The order of those three fields is important. That way given an array of constraints, if
         * we sort that array it will automatically sort by first quantifierId, then stateId then by
         * kind. Meaning that this easily let us group constraints by both their quantifierId and
         * their stateId.
         */
        return ((long) quantId << 32) | ((long) stateId << 3) | kind;
    }

    /**
     * See above.
     */
    public static int getKind(long constraint) {
        return (int) (constraint & 0b111);
    }

    /**
     * id of the (normalized) nfa state id associated with the tracker used by this constraint.
     */
    public static int getStateId(long constraint) {
        return (int) ((constraint >> 3) & 0x1FFFFFFF);
    }

    /**
     * id of the quantifier on which this constraint applies.
     */
    public static int getQuantId(long constraint) {
        return (int) (constraint >>> 32);
    }

    /**
     * Unique id combining both the quantifier id and the state id.
     */
    public static long getId(long constraint) {
        return constraint >> 3;
    }

    /**
     * Returns the negation of a given constraint Note: Each constraint is defined such that
     * flipping the lsb negates the constraint.
     */
    public static long not(long constraint) {
        return constraint ^ 1;
    }

    /**
     * Returns true iff a sequence of constraints is normalized, meaning there are no duplicate and
     * they are sorted.
     */
    public static boolean isNormalized(long[] constraints) {
        var prev = -1L;
        for (var constraint : constraints) {
            if (constraint <= prev) {
                return false;
            }
            prev = constraint;
        }
        return true;
    }

    /**
     * Normalize a list of constraints, i.e. sort them and remove duplicates (in-place)
     */
    public static long[] normalize(LongArrayBuffer constraints) {
        constraints.sort();
        var normalized = new LongArrayBuffer();
        var prev = -1L;
        for (var constr : constraints) {
            if (constr != prev) {
                normalized.add(constr);
                prev = constr;
            }
        }
        return normalized.toArray();
    }

    /**
     * Returns true iff constraint1 == !constraint2.
     */
    public static boolean areOpposite(long constraint1, long constraint2) {
        return (constraint1 ^ constraint2) == 1;
    }

    /**
     * Given two constraints formulas compute their intersections and subtraction, or null if they
     * cannot be both satisfied at the same time. For instance, given the two formulas: lhs = (a & b
     * & !c) and rhs = (!c & d), this function returns
     * 
     * <pre>
     *  f => lhs && !rhs       | lhs && rhs       | f => !lhs && rhs
     *  -----------------------------------------------------------------
     *  f = (a & b & !c & !d)  | (a & b & !c & d) | f = (!a & !c & d) ||
     *                         |                  |     (a & !b & !c & d)
     * </pre>
     * 
     * Note that the middle always contains one formula, but the two other results can contain
     * multiple of them. All formulas produced are guaranteed to be disjoint (in order to be used in
     * the DFA). See {@link MergeResult} for more details. The two input arrays must be sorted and
     * not contains duplicates, such that we can easily know which constraints they have in common.
     */
    public static MergeResult intersectAndSubtract(long[] lhs, long[] rhs) {
        if (lhs.length == 0 && rhs.length == 0) {
            return MergeResult.Empty;
        }

        MergeResultBuilder resultBuilder = new MergeResultBuilder(lhs, rhs);

        int leftIndex = 0;
        int rightIndex = 0;
        while (leftIndex < lhs.length && rightIndex < rhs.length) {
            long leftConstraint = lhs[leftIndex];
            long rightConstraint = rhs[rightIndex];
            if (areOpposite(leftConstraint, rightConstraint)) {
                return null;
            }
            if (leftConstraint == rightConstraint) {
                resultBuilder.addConstraintToLeft(leftConstraint);
                if (!resultBuilder.addToMiddleAndValidate(leftConstraint)) {
                    return null;
                }
                resultBuilder.addConstraintToRight(leftConstraint);
                leftIndex++;
                rightIndex++;
            } else if (leftConstraint > rightConstraint) {
                if (!resultBuilder.addToMiddleAndValidate(rightConstraint)) {
                    return null;
                }
                resultBuilder.addConstraintToRight(rightConstraint);
                resultBuilder.duplicateFormulaLeft(not(rightConstraint), leftIndex);
                rightIndex++;
            } else {
                if (!resultBuilder.addToMiddleAndValidate(leftConstraint)) {
                    return null;
                }
                resultBuilder.addConstraintToLeft(leftConstraint);
                resultBuilder.duplicateFormulaRight(not(leftConstraint), rightIndex);
                leftIndex++;
            }
        }
        for (int i = leftIndex; i < lhs.length; i++) {
            long leftConstraint = lhs[i];
            if (!resultBuilder.addToMiddleAndValidate(leftConstraint)) {
                return null;
            }
            resultBuilder.addConstraintToLeft(leftConstraint);
            resultBuilder.duplicateFormulaRight(not(leftConstraint), rightIndex);
        }
        for (int i = rightIndex; i < rhs.length; i++) {
            long rightConstraint = rhs[i];
            if (!resultBuilder.addToMiddleAndValidate(rightConstraint)) {
                return null;
            }
            resultBuilder.addConstraintToRight(rightConstraint);
            resultBuilder.duplicateFormulaLeft(not(rightConstraint), leftIndex);
        }

        return resultBuilder.build();
    }

    /**
     * Result of {@link #intersectAndSubtract(long[], long[])}. Given two series of constraints
     * cstr1 and cstr2 it returns:
     *
     * @param lhs For each series of constraints cstr in this array we have cstr => cstr1 and cstr
     *            => !cstr2
     * @param middle Series of constraints that satisfy both cstr1 and cstr2
     * @param rhs For each series of constraints cstr in this array we have cstr => !cstr1 and cstr
     *            => cstr2
     */
    public record MergeResult(long[][] lhs, long[] middle, long[][] rhs) {
        public static final MergeResult Empty = new MergeResult(new long[0][], new long[0], new long[0][]);
    }

    private static final class MergeResultBuilder {
        private final ObjectArrayBuffer<LongArrayBuffer> lhs;
        private final LongArrayBuffer middle;
        private final ObjectArrayBuffer<LongArrayBuffer> rhs;
        private final long[] originalLhs;
        private final long[] originalRhs;

        private MergeResultBuilder(long[] originalLhs, long[] originalRhs) {
            this.lhs = new ObjectArrayBuffer<>();
            this.middle = new LongArrayBuffer();
            this.rhs = new ObjectArrayBuffer<>();
            this.originalLhs = originalLhs;
            this.originalRhs = originalRhs;
        }

        public void addConstraintToLeft(long constraint) {
            addToAll(lhs, constraint);
        }

        public void addConstraintToRight(long constraint) {
            addToAll(rhs, constraint);
        }

        private static void addToAll(ObjectArrayBuffer<LongArrayBuffer> to, long constraint) {
            int length = to.length();
            for (int i = 0; i < length; i++) {
                LongArrayBuffer constraints = to.get(i);
                if (constraints != null) {
                    constraints.add(constraint);
                    if (!validateAndSimplify(constraints)) {
                        to.set(i, null);
                    }
                }
            }
        }

        /**
         * Returns true whenever a given set of constraints is satisfiable. If true it also
         * simplifies the result by removing redundant constraints: for instance allLtMin &&
         * existLtMax gets simplified to allLtMin. Assumes the input is sorted and without any
         * duplicate, and that only the last sequence of operation might be invalid. It therefore
         * needs to be called everytime a new constraints is added to the buffer.
         */
        private static boolean validateAndSimplify(LongArrayBuffer constraints) {
            int length = constraints.length();
            long lastConstraint = constraints.get(length - 1);
            int sequenceLength = 1;
            long id = getId(lastConstraint);
            for (int i = length - 2; i >= 0; i--) {
                long constraint = constraints.get(i);
                if (getId(constraint) != id) {
                    break;
                }
                sequenceLength++;
            }
            if (sequenceLength == 1) {
                return true;
            }
            if (sequenceLength == 2) {
                var thisKind = getKind(lastConstraint);
                var prevConstraint = constraints.get(length - 2);
                var prevKind = getKind(prevConstraint);
                if (thisKind == allLtMin && prevKind == allGeMax || thisKind == existLtMin && prevKind == allGeMax || thisKind == allGeMin && prevKind == allLtMin) {
                    return false;
                }
                if (thisKind == allLtMin && prevKind == existLtMax || thisKind == allGeMin && prevKind == existGeMin) {
                    constraints.set(length - 2, lastConstraint);
                    constraints.setLength(length - 1);

                } else if (thisKind == existGeMin && prevKind == allGeMax) {
                    constraints.setLength(length - 1);
                }
            } else {
                assert sequenceLength == 3;
                var constraint1 = constraints.get(length - 3);
                var constraint2 = constraints.get(length - 2);
                var constraint3 = lastConstraint;
                var kind1 = getKind(constraint1);
                var kind2 = getKind(constraint2);
                var kind3 = getKind(constraint3);
                if (kind1 == existLtMax) {
                    if (kind2 == existGeMin) {
                        if (kind3 == existLtMin) {
                            constraints.set(length - 3, constraint2);
                            constraints.set(length - 2, constraint3);
                            constraints.setLength(length - 1);
                        } else if (kind3 == allGeMin) {
                            constraints.set(length - 2, constraint3);
                            constraints.setLength(length - 1);
                        }
                    } else if (kind2 == allLtMin) {
                        if (kind3 == existLtMin) {
                            constraints.set(length - 3, constraint2);
                            constraints.setLength(length - 2);
                        } else if (kind3 == allGeMin) {
                            return false;
                        }
                    }
                } else if (kind1 == allGeMax) {
                    if (kind2 == existGeMin) {
                        if (kind3 == existLtMin) {
                            return false;
                        } else if (kind3 == allGeMin) {
                            constraints.setLength(length - 2);
                        }
                    } else if (kind2 == allLtMin) {
                        if (kind3 == existLtMin || kind3 == allGeMin) {
                            return false;
                        } else {
                            throw CompilerDirectives.shouldNotReachHere();
                        }
                    }
                }
            }
            return true;
        }

        /**
         * Add the given constraints to the middle buffer, and returns true whenever it can still be
         * satisfied at runtime.
         */
        public boolean addToMiddleAndValidate(long constraint) {
            middle.add(constraint);
            return validateAndSimplify(middle);
        }

        private static LongArrayBuffer copyUntil(long[] buffer, int to) {
            LongArrayBuffer result = new LongArrayBuffer(buffer.length);
            result.addAll(buffer, to);
            return result;
        }

        public void duplicateFormulaLeft(long constraint, int leftIndex) {
            LongArrayBuffer newConstraints = copyUntil(originalLhs, leftIndex);
            newConstraints.add(constraint);
            lhs.add(newConstraints);
        }

        public void duplicateFormulaRight(long appendConstraint, int rightIndex) {
            LongArrayBuffer newConstraints = copyUntil(originalRhs, rightIndex);
            newConstraints.add(appendConstraint);
            rhs.add(newConstraints);
        }

        private static int countNumberOfValid(ObjectArrayBuffer<LongArrayBuffer> constraintsList) {
            int result = 0;
            for (var constraints : constraintsList) {
                if (constraints != null) {
                    result++;
                }
            }
            return result;
        }

        private static long[][] constraintsListToArray(ObjectArrayBuffer<LongArrayBuffer> constraintsList) {
            long[][] result = new long[countNumberOfValid(constraintsList)][];
            int i = 0;
            for (var constraints : constraintsList) {
                if (constraints != null) {
                    result[i] = constraints.toArray();
                    i++;
                }
            }
            return result;
        }

        public MergeResult build() {
            return new MergeResult(constraintsListToArray(lhs), middle.toArray(), constraintsListToArray(rhs));
        }
    }

    @TruffleBoundary
    public static String toString(long constraint) {
        int qId = getQuantId(constraint);
        int stateId = getStateId(constraint);
        int kind = getKind(constraint);
        StringBuilder b = new StringBuilder("qId: %d, if".formatted(qId));
        switch (kind) {
            // Checkstyle: stop
            case existLtMax -> b.append(" ∃c ∈ %d, c < max".formatted(stateId));
            case allGeMax -> b.append(" ∀c ∈ %d, c ≥ max".formatted(stateId));
            case existGeMin -> b.append(" ∃c ∈ %d, c ≥ min".formatted(stateId));
            case allLtMin -> b.append(" ∀c ∈ %d, c < min".formatted(stateId));
            case existLtMin -> b.append(" ∃c ∈ %d, c < min".formatted(stateId));
            case allGeMin -> b.append(" ∀c ∈ %d, c >= min".formatted(stateId));
            // Checkstyle: resume
        }

        return b.toString();
    }

    @TruffleBoundary
    public static JsonValue toJson(long guard) {
        return Json.val(toString(guard));
    }

    @TruffleBoundary
    public static Stream<JsonValue> combineToJson(long[] constraints, long[] operations) {
        Stream<JsonValue> s1;
        if (constraints.length == 0) {
            s1 = Stream.of(Json.val("No Constraints"));
        } else {
            s1 = Arrays.stream(constraints).mapToObj(TransitionConstraint::toJson);
        }

        var s2 = Arrays.stream(operations).mapToObj(TransitionOp::toJson);
        return Stream.concat(s1, s2);
    }
}
