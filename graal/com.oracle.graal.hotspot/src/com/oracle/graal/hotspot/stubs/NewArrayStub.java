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

import static com.oracle.graal.api.code.DeoptimizationAction.*;
import static com.oracle.graal.api.meta.DeoptimizationReason.*;
import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;
import static com.oracle.graal.hotspot.replacements.HotSpotReplacementsUtil.*;
import static com.oracle.graal.hotspot.replacements.NewObjectSnippets.*;
import static com.oracle.graal.hotspot.stubs.NewInstanceStub.*;

import com.oracle.graal.api.code.RuntimeCallTarget.Descriptor;
import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.Node.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hotspot.nodes.*;
import com.oracle.graal.hotspot.replacements.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.replacements.*;
import com.oracle.graal.replacements.Snippet.ConstantParameter;
import com.oracle.graal.replacements.SnippetTemplate.Arguments;
import com.oracle.graal.replacements.SnippetTemplate.SnippetInfo;
import com.oracle.graal.word.*;

/**
 * Stub implementing the fast path for TLAB refill during instance class allocation. This stub is
 * called via {@link NewArrayStubCall} from the {@linkplain NewObjectSnippets inline} allocation
 * code when TLAB allocation fails. If this stub fails to refill the TLAB or allocate the object, it
 * calls out to the HotSpot C++ runtime to complete the allocation.
 */
public class NewArrayStub extends Stub {

    public NewArrayStub(final HotSpotRuntime runtime, Replacements replacements, TargetDescription target, HotSpotRuntimeCallTarget linkage) {
        super(runtime, replacements, target, linkage);
    }

    @Override
    protected Arguments makeArguments(SnippetInfo stub) {
        HotSpotResolvedObjectType intArrayType = (HotSpotResolvedObjectType) runtime.lookupJavaType(int[].class);

        // RuntimeStub cannot (currently) support oops or metadata embedded in the code so we
        // convert the hub (i.e., Klass*) for int[] to be a naked word. This should be safe since
        // the int[] class will never be unloaded.
        Constant intArrayHub = intArrayType.klass();
        intArrayHub = Constant.forIntegerKind(graalRuntime().getTarget().wordKind, intArrayHub.asLong(), null);

        Arguments args = new Arguments(stub);
        args.add("hub", null);
        args.add("length", null);
        args.addConst("intArrayHub", intArrayHub);
        args.addConst("log", Boolean.getBoolean("graal.logNewArrayStub"));
        return args;
    }

    /**
     * Re-attempts allocation after an initial TLAB allocation failed or was skipped (e.g., due to
     * -XX:-UseTLAB).
     * 
     * @param hub the hub of the object to be allocated
     * @param length the length of the array
     * @param intArrayHub the hub for {@code int[].class}
     * @param log specifies if logging is enabled
     */
    @Snippet
    private static Object newArray(Word hub, int length, @ConstantParameter Word intArrayHub, @ConstantParameter boolean log) {
        int layoutHelper = hub.readInt(layoutHelperOffset(), FINAL_LOCATION);
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
            if (memory.notEqual(0)) {
                log(log, "newArray: allocated new array at %p\n", memory);
                formatArray(hub, sizeInBytes, length, headerSize, memory, Word.unsigned(arrayPrototypeMarkWord()), true);
                return memory.toObject();
            }
        }
        log(log, "newArray: calling new_array_c\n", 0L);

        newArrayC(NEW_ARRAY_C, thread(), hub, length);

        if (clearPendingException(thread())) {
            log(log, "newArray: deoptimizing to caller\n", 0L);
            getAndClearObjectResult(thread());
            DeoptimizeCallerNode.deopt(InvalidateReprofile, RuntimeConstraint);
        }
        return getAndClearObjectResult(thread());
    }

    public static final Descriptor NEW_ARRAY_C = descriptorFor(NewArrayStub.class, "newArrayC", false);

    @NodeIntrinsic(CRuntimeCall.class)
    public static native void newArrayC(@ConstantNodeParameter Descriptor newArrayC, Word thread, Word hub, int length);
}
