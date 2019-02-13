/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap;

import com.oracle.svm.core.annotate.NeverInline;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;
import com.oracle.svm.core.util.VMError;

/**
 * Motivation: Open an instance of this class to detect attempts to allocate in the Heap.
 *
 * Design: There shouldn't be tests for a locked heap on the allocation fast-path, so the key is
 * that creating one of these sets top to end in the young space, so allocation attempts fail over
 * to the slow-path, and there can be a test for a locked heap on the slow path.
 *
 * TODO: When SVM becomes multi-threaded, this should only prevent allocation on a particular
 * thread. That could be done by setting the thread's TLAB to null, which should force it on to the
 * slow-path to allocation a new TLAB.
 */
public class NoAllocationVerifier implements AutoCloseable {

    public static final String ERROR_MSG = "Attempt to allocate while allocation was explicitly disabled using a NoAllocationVerifier";

    /** A guard to place before an allocation, giving the call site and the allocation type. */
    public static void exit(final String callSite, final String typeName) {
        Log.log().string("[NoAllocationVerifier detected disallowed allocation: ").string(callSite).string(": ").string(typeName).newline();
        if (openVerifiers.get() != null) {
            Log.log().string("[NoAllocationVerifier stack: ");
            for (NoAllocationVerifier rest = openVerifiers.get(); rest != null; rest = rest.next) {
                Log.log().newline().string("  ").string("  reason: ").string(rest.reason).newline();
            }
            Log.log().string("]").newline();
        }
        Log.log().string("]").newline();

        throw VMError.shouldNotReachHere(ERROR_MSG);
    }

    private static final FastThreadLocalObject<NoAllocationVerifier> openVerifiers = FastThreadLocalFactory.createObject(NoAllocationVerifier.class);

    /**
     * Create an opened instance.
     *
     * Usage is:
     *
     * <pre>
     *     try (NoAllocationVerifier verifier = NoAllocationVerifier.factory()) {
     *         ....
     *     }
     * </pre>
     */
    public static NoAllocationVerifier factory(String reason) {
        return NoAllocationVerifier.factory(reason, true);
    }

    /**
     * Create an instance that can be opened lazily.
     *
     * Usage is:
     *
     * <pre>
     *     NoAllocationVerifier instance = NoAllocationVerifier.factory(false);
     *     ....
     *     try(NoAllocationVerifier resource = instance.open()) {
     *         ....
     *     }
     * </pre>
     */
    public static NoAllocationVerifier factory(String reason, boolean open) {
        NoAllocationVerifier result = new NoAllocationVerifier(reason);
        if (open) {
            result.open();
        }
        return result;
    }

    /**
     * Returns true if there is an open NoAllocationVerifier, i.e., returns true if no allocation is
     * allowed in this thread.
     */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isActive() {
        return openVerifiers.get() != null;
    }

    private String reason;
    private boolean isOpen;
    private NoAllocationVerifier next;

    protected NoAllocationVerifier(String reason) {
        this.reason = reason;
        isOpen = false;
        next = null;
    }

    @NeverInline("Access of fields also used by allocation fast path, so must not be inlined in a method that performs allocation")
    public NoAllocationVerifier open() {
        VMError.guarantee(!isOpen, "NoAllocationVerifier already open");

        if (!isActive()) {
            /* No other verifier open yet, so suspend allocation. */
            Heap.getHeap().suspendAllocation();
        }

        /* Push to linked list of open verifiers. */
        isOpen = true;
        next = openVerifiers.get();
        openVerifiers.set(this);

        return this;
    }

    @Override
    @NeverInline("Access of fields also used by allocation fast path, so must not be inlined in a method that performs allocation")
    public void close() {
        VMError.guarantee(isOpen, "NoAllocationVerifier not open");

        openVerifiers.set(next);
        next = null;
        isOpen = false;

        if (!isActive()) {
            /* No other verifier open anymore, so resume allocation. */
            Heap.getHeap().resumeAllocation();
        }
    }
}
