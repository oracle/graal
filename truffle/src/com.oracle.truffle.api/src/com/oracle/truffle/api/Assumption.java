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
package com.oracle.truffle.api;

import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.InvalidAssumptionException;
import com.oracle.truffle.api.nodes.Node;

/**
 * An assumption is a global boolean flag that starts with the value true (i.e., the assumption is
 * valid) and can subsequently be invalidated (using {@link Assumption#invalidate()}). Once
 * invalidated, an assumption can never get valid again. Assumptions can be created using the
 * {@link TruffleRuntime#createAssumption()} or the {@link TruffleRuntime#createAssumption(String)}
 * method. The Truffle compiler has special knowledge of this class in order to produce efficient
 * machine code for checking an assumption in case the assumption object is a compile time constant.
 * Therefore, assumptions should be stored in final fields in Truffle nodes.
 *
 * All instances of classes implementing {@code Assumption} must be held in {@code final} fields for
 * compiler optimizations to take effect.
 *
 * @since 0.8 or earlier
 */
public interface Assumption {

    /**
     * Checks that this assumption is still valid. The method throws an exception, if this is no
     * longer the case. This method is preferred over the {@link #isValid()} method when writing
     * guest language interpreter code. The catch block should perform a node rewrite (see
     * {@link Node#replace(Node)}) with a node that no longer relies on the assumption.
     *
     * @throws InvalidAssumptionException If the assumption is no longer valid.
     * @since 0.8 or earlier
     */
    void check() throws InvalidAssumptionException;

    /**
     * Checks whether the assumption is still valid.
     *
     * @return a boolean value indicating the validity of the assumption
     * @since 0.8 or earlier
     */
    boolean isValid();

    /**
     * Invalidates this assumption. Performs no operation, if the assumption is already invalid.
     *
     * @since 0.8 or earlier
     */
    void invalidate();

    /**
     * Invalidates this assumption. Performs no operation, if the assumption is already invalid.
     *
     * @param message a message stating the reason of the invalidation
     * @since 0.33
     */
    default void invalidate(String message) {
        invalidate();
    }

    /**
     * A name for the assumption that is used for debug output.
     *
     * @return the name of the assumption
     * @since 0.8 or earlier
     */
    String getName();

    /**
     * Checks whether an assumption is not <code>null</code> and valid.
     *
     * @since 19.0
     */
    static boolean isValidAssumption(Assumption assumption) {
        return assumption != null && assumption.isValid();
    }

    /**
     * Checks whether all assumptions in an array are not <code>null</code> and valid. Returns
     * <code>false</code> if the assumptions array itself is <code>null</code>. This method is
     * designed for compilation. Note that the provided assumptions array must be a compilation
     * final array with {@link CompilationFinal#dimensions() dimensions} set to one.
     *
     * @since 19.0
     */
    @ExplodeLoop
    static boolean isValidAssumption(Assumption[] assumptions) {
        CompilerDirectives.isPartialEvaluationConstant(assumptions);
        if (assumptions == null) {
            return false;
        }
        for (Assumption assumption : assumptions) {
            if (!isValidAssumption(assumption)) {
                return false;
            }
        }
        return true;
    }

}
