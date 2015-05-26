/*
 * Copyright (c) 2012, 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.amd64.jvmci;

import com.oracle.jvmci.code.RegisterConfig;
import com.oracle.jvmci.code.TargetDescription;
import com.oracle.jvmci.code.Architecture;
import com.oracle.jvmci.meta.ConstantReflectionProvider;
import static com.oracle.jvmci.hotspot.InitTimer.*;

import java.util.*;

import com.oracle.graal.amd64.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.amd64.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.jvmci.hotspot.*;
import com.oracle.jvmci.runtime.*;

@ServiceProvider(HotSpotJVMCIBackendFactory.class)
public class AMD64HotSpotJVMCIBackendFactory implements HotSpotJVMCIBackendFactory {

    protected Architecture createArchitecture(HotSpotVMConfig config) {
        return new AMD64(computeFeatures(config), computeFlags(config));
    }

    protected EnumSet<AMD64.CPUFeature> computeFeatures(HotSpotVMConfig config) {
        // Configure the feature set using the HotSpot flag settings.
        EnumSet<AMD64.CPUFeature> features = EnumSet.noneOf(AMD64.CPUFeature.class);
        assert config.useSSE >= 2 : "minimum config for x64";
        features.add(AMD64.CPUFeature.SSE);
        features.add(AMD64.CPUFeature.SSE2);
        if ((config.x86CPUFeatures & config.cpuSSE3) != 0) {
            features.add(AMD64.CPUFeature.SSE3);
        }
        if ((config.x86CPUFeatures & config.cpuSSSE3) != 0) {
            features.add(AMD64.CPUFeature.SSSE3);
        }
        if ((config.x86CPUFeatures & config.cpuSSE4A) != 0) {
            features.add(AMD64.CPUFeature.SSE4a);
        }
        if ((config.x86CPUFeatures & config.cpuSSE41) != 0) {
            features.add(AMD64.CPUFeature.SSE4_1);
        }
        if ((config.x86CPUFeatures & config.cpuSSE42) != 0) {
            features.add(AMD64.CPUFeature.SSE4_2);
        }
        if ((config.x86CPUFeatures & config.cpuAVX) != 0) {
            features.add(AMD64.CPUFeature.AVX);
        }
        if ((config.x86CPUFeatures & config.cpuAVX2) != 0) {
            features.add(AMD64.CPUFeature.AVX2);
        }
        if ((config.x86CPUFeatures & config.cpuERMS) != 0) {
            features.add(AMD64.CPUFeature.ERMS);
        }
        if ((config.x86CPUFeatures & config.cpuLZCNT) != 0) {
            features.add(AMD64.CPUFeature.LZCNT);
        }
        if ((config.x86CPUFeatures & config.cpuPOPCNT) != 0) {
            features.add(AMD64.CPUFeature.POPCNT);
        }
        if ((config.x86CPUFeatures & config.cpuAES) != 0) {
            features.add(AMD64.CPUFeature.AES);
        }
        if ((config.x86CPUFeatures & config.cpu3DNOWPREFETCH) != 0) {
            features.add(AMD64.CPUFeature.AMD_3DNOW_PREFETCH);
        }
        if ((config.x86CPUFeatures & config.cpuBMI1) != 0) {
            features.add(AMD64.CPUFeature.BMI1);
        }
        return features;
    }

    protected EnumSet<AMD64.Flag> computeFlags(HotSpotVMConfig config) {
        EnumSet<AMD64.Flag> flags = EnumSet.noneOf(AMD64.Flag.class);
        if (config.useCountLeadingZerosInstruction) {
            flags.add(AMD64.Flag.UseCountLeadingZerosInstruction);
        }
        if (config.useCountTrailingZerosInstruction) {
            flags.add(AMD64.Flag.UseCountTrailingZerosInstruction);
        }
        return flags;
    }

    protected TargetDescription createTarget(HotSpotVMConfig config) {
        final int stackFrameAlignment = 16;
        final int implicitNullCheckLimit = 4096;
        final boolean inlineObjects = true;
        return new HotSpotTargetDescription(createArchitecture(config), true, stackFrameAlignment, implicitNullCheckLimit, inlineObjects);
    }

    protected AMD64HotSpotBackend createBackend(HotSpotGraalRuntimeProvider runtime, HotSpotProviders providers) {
        return new AMD64HotSpotBackend(runtime, providers);
    }

    protected HotSpotConstantReflectionProvider createConstantReflection(HotSpotJVMCIRuntimeProvider runtime) {
        return new HotSpotConstantReflectionProvider(runtime);
    }

    protected RegisterConfig createRegisterConfig(HotSpotJVMCIRuntimeProvider runtime, TargetDescription target) {
        return new AMD64HotSpotRegisterConfig(target.arch, runtime.getConfig());
    }

    protected HotSpotCodeCacheProvider createCodeCache(HotSpotJVMCIRuntimeProvider runtime, TargetDescription target, RegisterConfig regConfig) {
        return new HotSpotCodeCacheProvider(runtime, runtime.getConfig(), target, regConfig);
    }

    protected HotSpotMetaAccessProvider createMetaAccess(HotSpotJVMCIRuntimeProvider runtime) {
        return new HotSpotMetaAccessProvider(runtime);
    }

    public String getArchitecture() {
        return "AMD64";
    }

    public String getGraalRuntimeName() {
        return "basic";
    }

    @Override
    public String toString() {
        return getGraalRuntimeName() + ":" + getArchitecture();
    }

    public JVMCIBackend createJVMCIBackend(HotSpotJVMCIRuntimeProvider runtime, JVMCIBackend host) {

        assert host == null;
        TargetDescription target = createTarget(runtime.getConfig());

        RegisterConfig regConfig;
        HotSpotCodeCacheProvider codeCache;
        ConstantReflectionProvider constantReflection;
        HotSpotMetaAccessProvider metaAccess;
        try (InitTimer t = timer("create providers")) {
            try (InitTimer rt = timer("create MetaAccess provider")) {
                metaAccess = createMetaAccess(runtime);
            }
            try (InitTimer rt = timer("create RegisterConfig")) {
                regConfig = createRegisterConfig(runtime, target);
            }
            try (InitTimer rt = timer("create CodeCache provider")) {
                codeCache = createCodeCache(runtime, target, regConfig);
            }
            try (InitTimer rt = timer("create ConstantReflection provider")) {
                constantReflection = createConstantReflection(runtime);
            }
        }
        try (InitTimer rt = timer("instantiate backend")) {
            return createBackend(metaAccess, codeCache, constantReflection);
        }
    }

    protected JVMCIBackend createBackend(HotSpotMetaAccessProvider metaAccess, HotSpotCodeCacheProvider codeCache, ConstantReflectionProvider constantReflection) {
        return new JVMCIBackend(metaAccess, codeCache, constantReflection);
    }

    public String getJVMCIRuntimeName() {
        return "basic";
    }
}
