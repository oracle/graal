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
 * specialized code. We do this by calling the {@link #isAttachedInlined(int)} method and, based on
 * the result, calling either {@link OptimizedCallTarget#doInvoke(Object[])} (for non-inlined calls)
 * or {@link OptimizedCallTarget#callBoundary(Object[])} (for inlined ones). The compiler, once an
 * inlining decision about this call site is made, ensures the correct branch is reachable and the
 * other one is dead code.
 *
 * To allow the compiler to correctly substitute the call to {@link #isAttachedInlined(int)} we need
 * to make a data-dependency between the call the {@link #isAttachedInlined(int)} and the call to
 * {@link OptimizedCallTarget#callBoundary(Object[])} which is the point of inlining. For this we
 * use the {@link #get()} and {@link #attach(Object[], int)} methods, first one as a data source and
 * the other as a data sink (wrapping the arguments of the call to
 * {@link OptimizedCallTarget#callBoundary(Object[])}.
 */
class InlineHandle {

    /**
     * Returns a dummy value used to indicate to the compiler that there exists data flow between
     * {@link #attach(Object[], int)} and {@link #isAttachedInlined(int)}.
     *
     * @return Dummy value. Further logic is handled by the compiler through intrinsification.
     */
    static int get() {
        return 0xdeadbeef;
    }

    /**
     * Wraps the arguments to {@link OptimizedCallTarget#callBoundary(Object[])} ensuring data flow
     * between {@link #attach(Object[], int)} and {@link #isAttachedInlined(int)}.
     *
     * @param args the arguments
     * @param handle the value returned by {@link #get()}
     * @return nothing. Further logic is handled by the compiler through intrinsification.
     */
    @SuppressWarnings("unused")
    static Object[] attach(Object[] args, int handle) {
        throw new IllegalStateException("Should never reach here. This method must be intrinsified.");
    }

    /**
     * Used to differentiate between inlined and non-inlined call sites. Is intrincified by the
     * compiler to a node that will, after inlining decisions have been made, be replaced with
     * {@code true} or {@code false}.
     *
     * @param handle a data-dependency handle to the call used for inlining. The same value should
     *            be used in the {@link #attach(Object[], int)} call wrapping the arguments of the
     *            call.
     * @return false, since the interpreted calls are never considered inlined. Further logic is
     *         handled by the compiler through intrinsification.
     */
    @SuppressWarnings("unused")
    static boolean isAttachedInlined(int handle) {
        return false;
    }

}
