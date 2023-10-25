/*
 * Copyright (c) 2019, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.runtime;

/**
 * Since SVM overrides {@link OptimizedCallTarget#doInvoke(Object[])} to specialize behaviour on
 * call sites, we need to differentiate the inlined call sites which should not contain this
 * specialized code. We do this by calling the {@link #get()} method and, based on the result,
 * calling either {@link OptimizedCallTarget#doInvoke(Object[])} (for non-inlined calls) or
 * {@link OptimizedCallTarget#callBoundary(Object[])} (for inlined ones). The compiler, once an
 * inlining decision about this call site is made, ensures the correct branch is reachable and the
 * other one is dead code.
 *
 * To allow the compiler to correctly substitute the result of the call to {@link #get()} we need to
 * make a data-dependency between the call to {@link #get()} and the call to
 * {@link OptimizedCallTarget#callBoundary(Object[])} which is the point of inlining. For this we
 * use the {@link #inject(Object[], boolean)} method, as a data sink (wrapping the arguments of the
 * call to {@link OptimizedCallTarget#callBoundary(Object[])}.
 */
class InlineDecision {

    /**
     * Produces a placeholder value for the inlining decision yet to be made by the compiler.
     *
     * @return false, since the interpreted calls are never considered inlined. Further logic is
     *         handled by the compiler through intrinsification.
     */
    static boolean get() {
        return false;
    }

    /**
     * Wraps the arguments to {@link OptimizedCallTarget#callBoundary(Object[])} ensuring data flow
     * between the inlining decision (produced by {@link #get()}) and the call who's argument are
     * being wrapped.
     *
     * @param args the arguments
     * @param decision the value returned by {@link #get()}, used to ensure a data dependency to the
     *            inlining decision
     * @return nothing. Further logic is handled by the compiler through intrinsification.
     */
    @SuppressWarnings("unused")
    static Object[] inject(Object[] args, boolean decision) {
        throw new IllegalStateException("Should never reach here. This method must be intrinsified.");
    }
}
