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
package com.oracle.svm.core.genscavenge;

import org.graalvm.word.Pointer;

import com.oracle.svm.core.heap.AllocationFreeList;
import com.oracle.svm.core.log.Log;

/**
 * Verification of the heap.
 *
 * This, maybe, could be just an ObjectVisitor, but since I want a little more information about
 * where I am, I'm writing this as a set of recursive-descent methods.
 *
 * TODO: Make this into an abstract base class with the common functionality implemented here.
 */
public interface HeapVerifier {

    enum Occasion {
        BEFORE_COLLECTION,
        DURING_COLLECTION,
        AFTER_COLLECTION
    }

    /** Verify the heap without an occasion. */
    boolean verify(String cause);

    /** What caused this verification? */
    String getCause();

    /** What caused this verification? */
    void setCause(String cause);

    /** Verify an object in the heap. */
    boolean verifyObjectAt(Pointer obj);

    /** A log for tracing verification. */
    Log getTraceLog();

    /** A log for witnessing failures. */
    Log getWitnessLog();

    /**
     * Throw one of these to signal that verification has failed. Since I cannot allocate the error,
     * e.g., during heap verification before collection, there is a have a pre-allocated singleton
     * instance available.
     */
    final class HeapVerificationError extends Error {

        /** Every Error should have one of these. */
        private static final long serialVersionUID = 4167117225081088445L;

        /** A singleton instance. */
        private static final HeapVerificationError SINGLETON = new HeapVerificationError();

        /** A private constructor because there is only the singleton instance. */
        private HeapVerificationError() {
            super();
        }

        static void throwError() {
            /* Log the message and throw the error. */
            Log.log().string("[HeapVerificationError.throwError:  message: ").string("Heap verification failed").string("]").newline();
            throw SINGLETON;
        }
    }

    abstract class MemoryChecker extends AllocationFreeList.Element<HeapVerifier.MemoryChecker> {

        /**
         * Check if a pointer meets some criteria, and do something useful if it does. In the
         * context of a heap verifier, if this method returns true, verification fails.
         *
         * @param ptr The pointer to be checked.
         * @return true of the pointer was identified, false otherwise.
         */
        public abstract boolean check(Pointer ptr);

        /** For tracing. */
        public abstract String getName();
    }
}
