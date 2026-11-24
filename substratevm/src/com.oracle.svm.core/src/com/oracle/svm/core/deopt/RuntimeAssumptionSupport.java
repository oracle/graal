/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.deopt;

import java.util.Objects;
import java.util.function.Supplier;

/**
 * Shared synchronization and dependent contracts for runtime assumptions that invalidate installed
 * code. Runtime hierarchy and CallSite-target assumptions use the same metadata monitor so
 * Ristretto can validate, attach, and publish every dependency of one installed-code object in one
 * transaction. Nested callbacks restore the outer monitor owner on exit; owner queries never
 * acquire the monitor.
 */
public abstract class RuntimeAssumptionSupport {
    /** Owner of this class's monitor, also read by threads that must not acquire it. */
    private static volatile Thread lockHolder;

    /**
     * Exact dependency attachment created by one publication transaction. Callers retain these
     * handles until publication succeeds. A rejected transaction invalidates code outside the
     * monitor before undoing only the registrations it created:
     *
     * <pre>{@code
     * attachments = validateAndAttach(code.assumptions)
     * if (!publish(code)) {
     *     code.invalidate() // outside the assumption monitor; retain attachments if this fails
     *     withAssumptionLock(() -> {
     *         for (attachment : reverse(attachments)) {
     *             attachment.detach()
     *         }
     *     })
     * }
     * }</pre>
     */
    public interface DependentAttachment {
        /** Removes this registration while its owning assumption-family lock is held. */
        void detach();
    }

    /**
     * Executes a metadata update or final publication step while the shared runtime-assumption
     * monitor is held. Sharing one monitor across assumption families prevents a hierarchy or
     * CallSite update from interleaving with final validation and attachment.
     */
    public static synchronized void withAssumptionLock(Runnable action) {
        Objects.requireNonNull(action, "action");
        Thread previousHolder = lockHolder;
        lockHolder = Thread.currentThread();
        try {
            action.run();
        } finally {
            lockHolder = previousHolder;
        }
    }

    /**
     * Computes a value while holding the shared runtime-assumption monitor. This form is used when
     * validation, dependency attachment, and the returned publication decision must observe one
     * indivisible assumption state.
     */
    public static synchronized <T> T withAssumptionLock(Supplier<T> action) {
        Objects.requireNonNull(action, "action");
        Thread previousHolder = lockHolder;
        lockHolder = Thread.currentThread();
        try {
            return action.get();
        } finally {
            lockHolder = previousHolder;
        }
    }

    /** Returns whether the caller holds the shared monitor, without acquiring it. */
    public static boolean isAssumptionLockHeldByCurrentThread() {
        return lockHolder == Thread.currentThread();
    }
}
