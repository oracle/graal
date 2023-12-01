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
package jdk.graal.compiler.nodes.spi;

import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.gc.BarrierSet;

public interface PlatformConfigurationProvider {
    /**
     * Returns the barrier set that is used to insert the needed read/write barriers.
     */
    BarrierSet getBarrierSet();

    /**
     * Returns whether the underlying VM can recover from virtualizing large primitive unsafe writes
     * in a byte array.
     */
    boolean canVirtualizeLargeByteArrayAccess();

    /**
     * Returns whether the underlying VM enforces strict monitorenter order.
     */
    default boolean requiresStrictLockOrder() {
        return false;
    }

    /**
     * Returns whether locks of thread local objects are side effect free and can be safely
     * virtualized.
     * <p>
     * The underlying problem is that materializing locks requires creating a
     * {@link jdk.graal.compiler.nodes.java.MonitorEnterNode} which must have a valid
     * {@code stateAfter} {@link FrameState}. The {@code stateAfter} chosen is the previous side
     * effecting state which will may not include the current objects or locks. Even if it does
     * contain those objects and locks they still virtual since the
     * {@link jdk.graal.compiler.nodes.virtual.CommitAllocationNode} is in the process of
     * materializing them. If a deopt occurs using that {@link FrameState} then execution will
     * resume before those locks are acquired and the objects created by the
     * {@link jdk.graal.compiler.nodes.virtual.CommitAllocationNode} will be reclaimed by the
     * garbage collector. In that case the locks may not be released leaving internal lock
     * bookkeeping in an inconsistent state.
     * <p>
     * The trivial example of a piece of code with this problem is this:
     *
     * <pre>
     *     Dummy v = new Dummy();
     *     v.f1 = 2;
     *     synchronized (v) {
     *         v.f1 +;
     *         sink = v;
     *     }
     * </pre>
     *
     * The object {@code v} escapes at the store to {@code sink} so the
     * {@link jdk.graal.compiler.nodes.virtual.CommitAllocationNode} would be materialized just
     * before that store. The {@code stateAFter} chosen for it will be the previous side effecting
     * state before the allocation which will obviously doesn't contain those locks. Any deopt using
     * that state will leave {@code v} to reclaimed.
     */
    default boolean areLocksSideEffectFree() {
        return true;
    }
}
