/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.hotspot.hsail;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.hsail.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.lir.*;
import com.oracle.graal.lir.hsail.*;
import com.oracle.graal.lir.hsail.HSAILMove.LoadCompressedPointer;
import com.oracle.graal.lir.hsail.HSAILMove.LoadOp;
import com.oracle.graal.lir.hsail.HSAILMove.StoreCompressedPointer;
import com.oracle.graal.lir.hsail.HSAILMove.StoreOp;
import com.oracle.graal.nodes.*;
import com.oracle.graal.phases.util.*;

/**
 * The HotSpot specific portion of the HSAIL LIR generator.
 */
public class HSAILHotSpotLIRGenerator extends HSAILLIRGenerator {

    private final HotSpotVMConfig config;

    public HSAILHotSpotLIRGenerator(StructuredGraph graph, Providers providers, HotSpotVMConfig config, FrameMap frameMap, CallingConvention cc, LIR lir) {
        super(graph, providers, frameMap, cc, lir);
        this.config = config;
    }

    private static boolean isCompressCandidate(DeoptimizingNode access) {
        return access != null && ((HeapAccess) access).isCompressible();
    }

    @Override
    public Variable emitLoad(Kind kind, Value address, DeoptimizingNode access) {
        HSAILAddressValue loadAddress = asAddressValue(address);
        Variable result = newVariable(kind);
        LIRFrameState state = access != null ? state(access) : null;
        assert access == null || access instanceof HeapAccess;
        if (config.useCompressedOops && isCompressCandidate(access)) {
            Variable scratch = newVariable(Kind.Long);
            append(new LoadCompressedPointer(kind, result, scratch, loadAddress, state, config.narrowOopBase, config.narrowOopShift, config.logMinObjAlignment()));
        } else {
            append(new LoadOp(kind, result, loadAddress, state));
        }
        return result;
    }

    @Override
    public void emitStore(Kind kind, Value address, Value inputVal, DeoptimizingNode access) {
        HSAILAddressValue storeAddress = asAddressValue(address);
        LIRFrameState state = access != null ? state(access) : null;
        Variable input = load(inputVal);
        if (config.useCompressedOops && isCompressCandidate(access)) {
            Variable scratch = newVariable(Kind.Long);
            append(new StoreCompressedPointer(kind, storeAddress, input, scratch, state, config.narrowOopBase, config.narrowOopShift, config.logMinObjAlignment()));
        } else {
            append(new StoreOp(kind, storeAddress, input, state));
        }
    }
}
