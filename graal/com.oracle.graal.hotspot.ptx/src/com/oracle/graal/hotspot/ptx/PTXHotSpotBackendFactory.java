/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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

import static com.oracle.graal.hotspot.InitTimer.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.api.runtime.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.java.*;
import com.oracle.graal.nodes.spi.*;
import com.oracle.graal.phases.tiers.*;
import com.oracle.graal.ptx.*;

@ServiceProvider(HotSpotBackendFactory.class)
public class PTXHotSpotBackendFactory implements HotSpotBackendFactory {

    public HotSpotBackend createBackend(HotSpotGraalRuntime runtime, HotSpotBackend hostBackend) {
        HotSpotProviders host = hostBackend.getProviders();
        HotSpotMetaAccessProvider metaAccess;
        PTXHotSpotCodeCacheProvider codeCache;
        ConstantReflectionProvider constantReflection;
        HotSpotForeignCallsProvider foreignCalls;
        LoweringProvider lowerer;
        Replacements replacements;
        HotSpotDisassemblerProvider disassembler;
        SuitesProvider suites;
        HotSpotRegistersProvider registers;
        HotSpotProviders providers;
        try (InitTimer t = timer("create providers")) {
            try (InitTimer rt = timer("create MetaAccess provider")) {
                metaAccess = host.getMetaAccess();
            }
            try (InitTimer rt = timer("create CodeCache provider")) {
                codeCache = new PTXHotSpotCodeCacheProvider(runtime, createTarget());
            }
            try (InitTimer rt = timer("create ConstantReflection provider")) {
                constantReflection = host.getConstantReflection();
            }
            try (InitTimer rt = timer("create ForeignCalls provider")) {
                foreignCalls = new PTXHotSpotForeignCallsProvider();
            }
            try (InitTimer rt = timer("create Lowerer provider")) {
                lowerer = new PTXHotSpotLoweringProvider(host.getLowerer());
            }
            try (InitTimer rt = timer("create Replacements provider")) {
                replacements = host.getReplacements();
            }
            try (InitTimer rt = timer("create Disassembler provider")) {
                disassembler = host.getDisassembler();
            }
            try (InitTimer rt = timer("create Suites provider")) {
                suites = new DefaultSuitesProvider();
            }
            try (InitTimer rt = timer("create HotSpotRegisters provider")) {
                registers = new HotSpotRegisters(PTX.tid, Register.None, Register.None);
            }
            providers = new HotSpotProviders(metaAccess, codeCache, constantReflection, foreignCalls, lowerer, replacements, disassembler, suites, registers, host.getSnippetReflection(),
                            host.getMethodHandleAccess());
        }
        try (InitTimer rt = timer("instantiate backend")) {
            return new PTXHotSpotBackend(runtime, providers);
        }
    }

    protected Architecture createArchitecture() {
        return new PTX();
    }

    protected TargetDescription createTarget() {
        final int stackFrameAlignment = 1;
        final int implicitNullCheckLimit = 0;
        final boolean inlineObjects = true;
        return new HotSpotTargetDescription(createArchitecture(), true, stackFrameAlignment, implicitNullCheckLimit, inlineObjects, Kind.Int);
    }

    public String getArchitecture() {
        return "PTX";
    }

    public String getGraalRuntimeName() {
        return "basic";
    }

    @Override
    public String toString() {
        return getGraalRuntimeName() + ":" + getArchitecture();
    }
}
