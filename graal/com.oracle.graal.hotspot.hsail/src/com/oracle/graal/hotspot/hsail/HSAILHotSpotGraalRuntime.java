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
package com.oracle.graal.hotspot.hsail;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.hsail.*;
import com.oracle.graal.nodes.spi.*;

/**
 * HSAIL specific implementation of {@link HotSpotGraalRuntime}.
 */
public class HSAILHotSpotGraalRuntime extends HotSpotGraalRuntime {

    @Override
    protected HotSpotProviders createProviders() {
        HotSpotProviders host = HotSpotGraalRuntime.graalRuntime().getProviders();

        HotSpotMetaAccessProvider metaAccess = host.getMetaAccess();
        HSAILHotSpotCodeCacheProvider codeCache = new HSAILHotSpotCodeCacheProvider(this);
        ConstantReflectionProvider constantReflection = host.getConstantReflection();
        HotSpotForeignCallsProvider foreignCalls = host.getForeignCalls();
        LoweringProvider lowerer = new HSAILHotSpotLoweringProvider(host.getLowerer());
        Replacements replacements = host.getReplacements();
        HotSpotDisassemblerProvider disassembler = host.getDisassembler();
        HotSpotSuitesProvider suites = host.getSuites();
        HotSpotRegisters registers = new HotSpotRegisters(Register.None, Register.None, Register.None);
        return new HotSpotProviders(metaAccess, codeCache, constantReflection, foreignCalls, lowerer, replacements, disassembler, suites, registers);
    }

    @Override
    protected TargetDescription createTarget() {
        final int stackFrameAlignment = 8;
        final int implicitNullCheckLimit = 0;
        final boolean inlineObjects = true;
        return new TargetDescription(new HSAIL(), true, stackFrameAlignment, implicitNullCheckLimit, inlineObjects);
    }

    @Override
    protected HotSpotBackend createBackend() {
        return new HSAILHotSpotBackend(this, getProviders());
    }

    @Override
    protected Value[] getNativeABICallerSaveRegisters() {
        throw new InternalError("NYI");
    }
}
