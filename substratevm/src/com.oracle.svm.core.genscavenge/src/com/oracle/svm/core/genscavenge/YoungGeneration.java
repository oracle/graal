/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.genscavenge;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;

import com.oracle.svm.core.MemoryWalker;
import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.heap.ObjectVisitor;
import com.oracle.svm.core.log.Log;
import com.oracle.svm.core.util.VMError;

/**
 * A Young Generation has one space, for ordinary objects.
 */
public class YoungGeneration extends Generation {

    // Final State.
    private final Space space;

    /* Constructors. */

    @Platforms(Platform.HOSTED_ONLY.class)
    YoungGeneration(String name) {
        this(name, new Space("youngSpace", true));
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    private YoungGeneration(String name, Space space) {
        super(name);
        this.space = space;
    }

    /** Return all allocated virtual memory chunks to HeapChunkProvider. */
    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public final void tearDown() {
        ThreadLocalAllocation.tearDown();
        space.tearDown();
    }

    @Override
    public boolean walkObjects(ObjectVisitor visitor) {
        /* Flush the thread-local allocation data. */
        ThreadLocalAllocation.disableThreadLocalAllocation();
        return getSpace().walkObjects(visitor);
    }

    @Override
    public Log report(Log log, boolean traceHeapChunks) {
        log.string("[Young generation: ").indent(true);
        getSpace().report(log, traceHeapChunks);
        log.redent(false).string("]");
        return log;
    }

    /**
     * Space access method.
     *
     * This method is final because it is called (transitively) from the allocation snippets.
     */
    public final Space getSpace() {
        return space;
    }

    /** Check if that space is the young space. */
    boolean isYoungSpace(Space thatSpace) {
        return (getSpace() == thatSpace);
    }

    @Override
    protected Object promoteObject(Object original) {
        throw VMError.shouldNotReachHere("Can not promote to a YoungGeneration.");
    }

    void releaseSpaces() {
        getSpace().release();
    }

    @Override
    protected boolean isValidSpace(Space thatSpace) {
        return isYoungSpace(thatSpace);
    }

    @Override
    protected boolean verify(final HeapVerifierImpl.Occasion occasion) {
        // The young "generation" consists of just one space.
        boolean result = true;
        final HeapImpl heap = HeapImpl.getHeapImpl();
        final HeapVerifierImpl heapVerifier = heap.getHeapVerifierImpl();
        final SpaceVerifierImpl spaceVerifier = heapVerifier.getSpaceVerifierImpl();
        spaceVerifier.initialize(getSpace());
        if (occasion.equals(HeapVerifier.Occasion.AFTER_COLLECTION)) {
            // After a collection the young space should be empty.
            if (spaceVerifier.containsChunks()) {
                result = false;
                heapVerifier.getWitnessLog().string("[YoungGeneration.verify:").string("  young space contains chunks after collection").string("]").newline();
            }
        } else {
            // Otherwise, verify the space.
            if (!spaceVerifier.verify()) {
                result = false;
                heapVerifier.getWitnessLog().string("[YoungGeneration.verify:").string("  young space fails to verify").string("]").newline();
            }
        }
        return result;
    }

    boolean slowlyFindPointer(Pointer p) {
        if (HeapVerifierImpl.slowlyFindPointerInSpace(getSpace(), p, HeapVerifierImpl.ChunkLimit.top)) {
            return true;
        }

        return false;
    }

    boolean walkHeapChunks(MemoryWalker.Visitor visitor) {
        return getSpace().walkHeapChunks(visitor);
    }
}
