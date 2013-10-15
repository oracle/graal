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
package com.oracle.graal.hotspot.ptx;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.phases.util.*;
import com.oracle.graal.ptx.*;

/**
 * PTX specific implementation of {@link HotSpotGraalRuntime}.
 */
public class PTXHotSpotGraalRuntime extends HotSpotGraalRuntime {

    protected PTXHotSpotGraalRuntime() {
    }

    /**
     * Called from C++ code to retrieve the singleton instance, creating it first if necessary.
     */
    public static HotSpotGraalRuntime makeInstance() {
        HotSpotGraalRuntime graalRuntime = graalRuntime();
        if (graalRuntime == null) {
            HotSpotGraalRuntimeFactory factory = findFactory("PTX");
            if (factory != null) {
                graalRuntime = factory.createRuntime();
            } else {
                graalRuntime = new PTXHotSpotGraalRuntime();
            }
            graalRuntime.completeInitialization();
        }
        return graalRuntime;
    }

    @Override
    protected HotSpotProviders createProviders() {
        HotSpotMetaAccessProvider metaAccess = new HotSpotMetaAccessProvider(this);
        PTXHotSpotCodeCacheProvider codeCache = new PTXHotSpotCodeCacheProvider(this);
        HotSpotConstantReflectionProvider constantReflection = new HotSpotConstantReflectionProvider(this);
        HotSpotForeignCallsProvider foreignCalls = new HotSpotForeignCallsProvider(this);
        HotSpotLoweringProvider lowerer = new PTXHotSpotLoweringProvider(this, metaAccess, foreignCalls);
        // Replacements cannot have speculative optimizations since they have
        // to be valid for the entire run of the VM.
        Assumptions assumptions = new Assumptions(false);
        Providers p = new Providers(metaAccess, codeCache, constantReflection, foreignCalls, lowerer, null);
        HotSpotReplacementsImpl replacements = new HotSpotReplacementsImpl(p, getConfig(), assumptions);
        HotSpotDisassemblerProvider disassembler = new HotSpotDisassemblerProvider(this);
        HotSpotSuitesProvider suites = new HotSpotSuitesProvider(this);
        HotSpotRegisters registers = new HotSpotRegisters(PTX.tid, Register.None, Register.None);
        return new HotSpotProviders(metaAccess, codeCache, constantReflection, foreignCalls, lowerer, replacements, disassembler, suites, registers);
    }

    protected Architecture createArchitecture() {
        return new PTX();
    }

    @Override
    protected TargetDescription createTarget() {
        final int stackFrameAlignment = 16;
        final int implicitNullCheckLimit = 4096;
        final boolean inlineObjects = true;
        return new TargetDescription(createArchitecture(), true, stackFrameAlignment, implicitNullCheckLimit, inlineObjects);
    }

    @Override
    protected HotSpotBackend createBackend() {
        return new PTXHotSpotBackend(this, getProviders());
    }

    @Override
    protected Value[] getNativeABICallerSaveRegisters() {
        throw new InternalError("NYI");
    }
}
