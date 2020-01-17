/*
 * Copyright (c) 2012, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.nodes;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Specifies for a method that the loops with constant number of invocations should be fully
 * unrolled.
 *
 * @since 0.8 or earlier
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface ExplodeLoop {

    /**
     * Controls the behavior of the {@link ExplodeLoop} annotation.
     *
     * <h2>Terminology</h2>
     *
     * In the explanations below, the term <i>loop end</i> refers to control flow reaching the end
     * of the loop body such as {@code continue} or a statement at the end of the loop body. The
     * term <i>loop exit</i> refers to control flow exiting the loop, such as {@code return} or
     * {@code break}. Example:
     *
     * {@codesnippet loopEndsExits}
     *
     * There are 4 loop explosion kinds (plus {@code MERGE_EXPLODE}, which is meant for bytecode
     * interpreters), configurable by 2 parameters: UNROLL vs EXPLODE and UNTIL_RETURN vs not.
     *
     * <h2>UNROLL vs EXPLODE</h2>
     *
     * The first parameter specifies whether the partial evaluator should duplicate loop ends.
     * UNROLL merges after each loop end and EXPLODE keeps exploding nested iterations like a tree.
     *
     * {@codesnippet unrollVsExplodeLoop}
     *
     * gets unrolled with {@code FULL_UNROLL} to:
     *
     * {@codesnippet unrollVsExplodeLoopUnrolled}
     *
     * and exploded with {@code FULL_EXPLODE} to:
     *
     * {@codesnippet unrollVsExplodeLoopExploded}
     *
     * <h2>UNTIL_RETURN</h2>
     *
     * The second parameter specifies whether the partial evaluator should duplicate loop exits.
     * UNTIL_RETURN duplicates them, otherwise control flow is merged.
     *
     * {@codesnippet untilReturnLoop}
     *
     * is expanded with {@code FULL_UNROLL_UNTIL_RETURN} to:
     *
     * {@codesnippet untilReturn}
     *
     * while {@code FULL_UNROLL} merges loop exits:
     *
     * {@codesnippet notUntilReturn}
     *
     * <h3>break</h3>
     *
     * Note that {@code break} statements inside the loop will duplicate code after the loop since
     * they add new loop exits:
     *
     * {@codesnippet breaksLoop}
     *
     * is expanded with {@code FULL_UNROLL_UNTIL_RETURN} to:
     *
     * {@codesnippet breaksLoopUnrollUntilReturn}
     *
     * @since 0.15
     */
    enum LoopExplosionKind {
        /**
         * Fully unroll all loops. The loops must have a known finite number of iterations. If a
         * loop has multiple loop ends, they are merged so that the subsequent loop iteration is
         * processed only once. For example, a loop with 4 iterations and 2 loop ends leads to
         * 1+1+1+1 = 4 copies of the loop body.
         *
         * @since 0.15
         */
        FULL_UNROLL,
        /**
         * Like {@link #FULL_UNROLL}, but in addition loop unrolling duplicates loop exits in every
         * iteration instead of merging them. Code after a loop exit is duplicated for every loop
         * exit and every loop iteration. For example, a loop with 4 iterations and 2 loop exits
         * (exit1 and exit2, where exit1 is an early return inside a loop, such as
         * {@link LoopExplosionKind untilReturnLoop()}) leads to 4 copies of the loop body and 4
         * copies of exit1 and 1 copy of exit2. After each exit all code until a return is
         * duplicated per iteration. Beware of break statements inside loops since they cause
         * additional loop exits leading to code duplication along exit2.
         *
         * @since 20.0
         */
        FULL_UNROLL_UNTIL_RETURN,
        /**
         * Fully explode all loops. The loops must have a known finite number of iterations. If a
         * loop has multiple loop ends, they are not merged so that subsequent loop iterations are
         * processed multiple times. For example, a loop with 4 iterations and 2 loop ends leads to
         * 1+2+4+8 = 15 copies of the loop body.
         *
         * @since 0.15
         */
        FULL_EXPLODE,
        /**
         * Like {@link #FULL_EXPLODE}, but in addition explosion does not stop at loop exits. Code
         * after the loop is duplicated for every loop exit of every loop iteration. For example, a
         * loop with 4 iterations and 2 loop exits leads to 4 * 2 = 8 copies of the code after the
         * loop.
         *
         * @since 0.15
         */
        FULL_EXPLODE_UNTIL_RETURN,
        /**
         * like {@link #FULL_EXPLODE}, but copies of the loop body that have the exact same state
         * (all local variables have the same value) are merged. This reduces the number of copies
         * necessary, but can introduce loops again. This kind is useful for bytecode interpreter
         * loops.
         *
         * @since 0.15
         */
        MERGE_EXPLODE
    }

    /**
     * The loop explosion kind.
     *
     * @since 0.15
     */
    LoopExplosionKind kind() default LoopExplosionKind.FULL_UNROLL_UNTIL_RETURN;

}

@SuppressFBWarnings("UC")
@SuppressWarnings("static-method")
class Snippets {
    // BEGIN: loopEndsExits
    int loopEndExits() {
        int state = -1;
        for (int i = 0; i < 4; i++) {
            if (condition(i)) {
                continue; // loop end
            } else if (condition1(i)) {
                // loop exit (break)
                state = 2;
                break;
            } else if (condition2(i)) {
                // loop exit (return)
                state = 3;
                return state;
            } else {
                state = i;
                // loop end
            }
        }
        // loop exit (after last iteration)
        return state;
    }
    // END: loopEndsExits

    // BEGIN: unrollVsExplodeLoop
    @ExplodeLoop
    void unrollVsExplodeLoop() {
        int state = 1;
        for (int i = 0; i < 2; i++) {
            if (c(i, state)) {
                state = 2;
            } else {
                state = 3;
            }
        }
    }
    // END: unrollVsExplodeLoop

    // BEGIN: unrollVsExplodeLoopUnrolled
    void unrollVsExplodeLoopUnrolled() {
        int state = 1;
        if (c(0, 1)) {
            state = 2;
        } else {
            state = 3;
        }

        if (c(1, state)) {
            state = 2;
        } else {
            state = 3;
        }
    }
    // END: unrollVsExplodeLoopUnrolled

    @SuppressWarnings("unused")
    // BEGIN: unrollVsExplodeLoopExploded
    void unrollVsExplodeLoopExploded() {
        int state = 1;
        if (c(0, 1)) {
            if (c(1, 2)) {
                state = 2;
            } else {
                state = 3;
            }
        } else {
            if (c(1, 3)) {
                state = 2;
            } else {
                state = 3;
            }
        }
    }
    // END: unrollVsExplodeLoopExploded

    // BEGIN: untilReturnLoop
    @ExplodeLoop
    int untilReturnLoop() {
        for (int i = 0; i < 2; i++) {
            if (condition(i)) {
                // exit1
                return f(i);
            }
        }
        // exit2
        return fallback();
    }
    // END: untilReturnLoop

    // BEGIN: untilReturn
    int untilReturn() {
        if (condition(0)) {
            return f(0);
        }

        if (condition(1)) {
            return f(1);
        }

        return fallback();
    }
    // END: untilReturn

    // BEGIN: notUntilReturn
    int notUntilReturn() {
        int i;
        for (;;) {
            if (condition(0)) {
                i = 0;
                break;
            }

            if (condition(1)) {
                i = 1;
                break;
            }

            return fallback();
        }

        return f(i);
    }
    // END: notUntilReturn

    // BEGIN: breaksLoop
    @ExplodeLoop
    int breaksLoop() {
        int state = -1;

        for (int i = 0; i < 2; i++) {
            if (condition1(i)) {
                return f(i);
            } else if (condition2(i)) {
                state = i;
                break;
            }
        }

        return fallback(state);
    }
    // END: breaksLoop

    // BEGIN: breaksLoopUnrollUntilReturn
    int breaksLoopUnrollUntilReturn() {
        if (condition1(0)) {
            return f(0);
        } else if (condition2(0)) {
            return fallback(0);
        }

        if (condition1(1)) {
            return f(1);
        } else if (condition2(1)) {
            return fallback(1);
        }

        return fallback(-1);
    }
    // END: breaksLoopUnrollUntilReturn

    private boolean c(int i, int state) {
        return i == state;
    }

    private boolean condition(int i) {
        return i == 0;
    }

    private boolean condition1(int i) {
        return i == 0;
    }

    private boolean condition2(int i) {
        return i == 0;
    }

    private int f(int i) {
        return i;
    }

    private int fallback() {
        return -1;
    }

    private int fallback(int state) {
        return state;
    }

}
