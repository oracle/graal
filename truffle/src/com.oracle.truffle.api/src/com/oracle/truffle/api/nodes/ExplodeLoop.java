/*
 * Copyright (c) 2012, 2018, Oracle and/or its affiliates. All rights reserved.
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
     * Controls behavior of {@link ExplodeLoop} annotation.
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
    LoopExplosionKind kind() default LoopExplosionKind.FULL_UNROLL;

}
