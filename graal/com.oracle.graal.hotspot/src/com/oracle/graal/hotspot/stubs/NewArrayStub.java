/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.stubs;

import static com.oracle.graal.hotspot.snippets.HotSpotSnippetUtils.*;
import static com.oracle.graal.hotspot.snippets.NewObjectSnippets.*;
import static com.oracle.graal.hotspot.stubs.NewInstanceStub.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.snippets.*;
import com.oracle.graal.snippets.*;
import com.oracle.graal.snippets.Snippet.ConstantParameter;
import com.oracle.graal.snippets.Snippet.Parameter;
import com.oracle.graal.snippets.SnippetTemplate.Key;
import com.oracle.graal.word.*;

/**
 * Stub implementing the fast path for TLAB refill during instance class allocation.
 * This stub is called from the {@linkplain NewObjectSnippets inline} allocation
 * code when TLAB allocation fails. If this stub fails to refill the TLAB
 * or allocate the object, it calls out to the HotSpot C++ runtime for
 * to complete the allocation.
 */
@SuppressWarnings("unused")
public class NewArrayStub extends Stub {

    public NewArrayStub(final HotSpotRuntime runtime, Assumptions assumptions, TargetDescription target) {
        super(runtime, assumptions, target, NewArrayStubCall.NEW_ARRAY);
    }

    @Override
    protected void populateKey(Key key) {
        HotSpotResolvedObjectType intArrayType = (HotSpotResolvedObjectType) runtime.lookupJavaType(int[].class);
        Constant intArrayHub = intArrayType.klass();
        key.add("intArrayHub", intArrayHub).add("log", Boolean.getBoolean("graal.logNewArrayStub"));
    }

    /**
     * Re-attempts allocation after an initial TLAB allocation failed or was skipped (e.g., due to -XX:-UseTLAB).
     *
     * @param hub the hub of the object to be allocated
     * @param length the length of the array
     * @param intArrayHub the hub for {@code int[].class}
     * @param log specifies if logging is enabled
     */
    @Snippet
    private static Object newArray(
                    @Parameter("hub") Word hub,
                    @Parameter("length") int length,
                    @ConstantParameter("intArrayHub") Word intArrayHub,
                    @ConstantParameter("log") boolean log) {
        int layoutHelper = hub.readInt(layoutHelperOffset());
        int log2ElementSize = (layoutHelper >> layoutHelperLog2ElementSizeShift()) & layoutHelperLog2ElementSizeMask();
        int headerSize = (layoutHelper >> layoutHelperHeaderSizeShift()) & layoutHelperHeaderSizeMask();
        int elementKind = (layoutHelper >> layoutHelperElementTypeShift()) & layoutHelperElementTypeMask();
        int sizeInBytes = computeArrayAllocationSize(length, wordSize(), headerSize, log2ElementSize);
        log(log, "newArray: element kind %d\n", elementKind);
        log(log, "newArray: array length %d\n", length);
        log(log, "newArray: array size %d\n", sizeInBytes);
        log(log, "newArray: hub=%p\n", hub);

        // check that array length is small enough for fast path.
        if (length <= MAX_ARRAY_FAST_PATH_ALLOCATION_LENGTH) {
            Word memory = refillAllocate(intArrayHub, sizeInBytes, log);
            if (memory != Word.zero()) {
                log(log, "newArray: allocated new array at %p\n", memory);
                formatArray(hub, sizeInBytes, length, headerSize, memory, Word.unsigned(arrayPrototypeMarkWord()), true);
                return verifyOop(memory.toObject());
            }
        }
        log(log, "newArray: calling new_array_slow", 0L);
        return verifyOop(NewArraySlowStubCall.call(hub, length));
    }
}
