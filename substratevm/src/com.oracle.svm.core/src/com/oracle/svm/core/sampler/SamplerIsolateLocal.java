/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.sampler;

import org.graalvm.compiler.nodes.NamedLocationIdentity;
import org.graalvm.nativeimage.CurrentIsolate;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.word.Pointer;
import org.graalvm.word.UnsignedWord;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.IsolateListenerSupport;
import com.oracle.svm.core.Isolates;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;

class SamplerIsolateLocal implements IsolateListenerSupport.IsolateListener {

    /** Stores the address of the first isolate created. */
    private static final CGlobalData<Pointer> firstIsolate = CGlobalDataFactory.createWord();

    /** Stores the isolate-specific key. */
    private static UnsignedWord key = WordFactory.zero();

    @Override
    @Uninterruptible(reason = "Thread state not yet set up.")
    public void afterCreateIsolate(Isolate isolate) {
        if (firstIsolate.get().logicCompareAndSwapWord(0, WordFactory.nullPointer(), Isolates.getHeapBase(isolate), NamedLocationIdentity.OFF_HEAP_LOCATION)) {
            key = SubstrateSigprofHandler.singleton().createThreadLocalKey();
        }
    }

    @Override
    @Uninterruptible(reason = "The isolate teardown is in progress.")
    public void onIsolateTeardown() {
        if (isKeySet()) {
            /* Invalidate the isolate-specific key. */
            UnsignedWord oldKey = key;
            key = WordFactory.zero();

            if (SubstrateSigprofHandler.singleton().isProfilingEnabled()) {
                /*
                 * Manually disable sampling for the current thread (no other threads are
                 * remaining).
                 */
                SamplerThreadLocal.teardown(CurrentIsolate.getCurrentThread());
            }

            /* Now, it's safe to delete the isolate-specific key. */
            SubstrateSigprofHandler.singleton().deleteThreadLocalKey(oldKey);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static Isolate getIsolate() {
        return firstIsolate.get().readWord(0);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static UnsignedWord getKey() {
        return key;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static boolean isKeySet() {
        return key.aboveThan(0);
    }
}
