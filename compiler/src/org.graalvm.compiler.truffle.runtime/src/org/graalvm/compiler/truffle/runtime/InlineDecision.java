/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */
package org.graalvm.compiler.truffle.runtime;

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
