/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.runtime.hotspot.libgraal;

import static org.graalvm.libgraal.LibGraalScope.getIsolateThread;

import java.util.Map;

import org.graalvm.compiler.truffle.common.hotspot.HotSpotTruffleCompiler;
import org.graalvm.compiler.truffle.runtime.hotspot.AbstractHotSpotTruffleRuntime;
import org.graalvm.libgraal.LibGraal;
import org.graalvm.libgraal.LibGraalScope;
import org.graalvm.libgraal.OptionsEncoder;

import com.oracle.truffle.api.TruffleRuntime;

import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotResolvedJavaType;
import jdk.vm.ci.meta.MetaAccessProvider;

/**
 * A {@link TruffleRuntime} that uses libgraal for compilation.
 */
final class LibGraalTruffleRuntime extends AbstractHotSpotTruffleRuntime {

    private final long handle;

    @SuppressWarnings("try")
    LibGraalTruffleRuntime() {
        HotSpotJVMCIRuntime runtime = HotSpotJVMCIRuntime.runtime();
        runtime.registerNativeMethods(HotSpotToSVMCalls.class);
        MetaAccessProvider metaAccess = runtime.getHostJVMCIBackend().getMetaAccess();
        HotSpotResolvedJavaType type = (HotSpotResolvedJavaType) metaAccess.lookupJavaType(getClass());
        try (LibGraalScope scope = new LibGraalScope(runtime)) {
            long classLoaderDelegate = LibGraal.translate(runtime, type);
            handle = HotSpotToSVMCalls.initializeRuntime(getIsolateThread(), this, classLoaderDelegate);
        }
    }

    @SuppressWarnings("try")
    @Override
    public HotSpotTruffleCompiler newTruffleCompiler() {
        try (LibGraalScope scope = new LibGraalScope(HotSpotJVMCIRuntime.runtime())) {
            return new SVMHotSpotTruffleCompiler(HotSpotToSVMCalls.initializeCompiler(getIsolateThread(), handle));
        }
    }

    @SuppressWarnings("try")
    @Override
    protected String initLazyCompilerConfigurationName() {
        try (LibGraalScope scope = new LibGraalScope(HotSpotJVMCIRuntime.runtime())) {
            return HotSpotToSVMCalls.getCompilerConfigurationFactoryName(getIsolateThread());
        }
    }

    @SuppressWarnings("try")
    @Override
    protected Map<String, Object> createInitialOptions() {
        try (LibGraalScope scope = new LibGraalScope(HotSpotJVMCIRuntime.runtime())) {
            byte[] serializedOptions = HotSpotToSVMCalls.getInitialOptions(getIsolateThread(), handle);
            return OptionsEncoder.decode(serializedOptions);
        }
    }

    @SuppressWarnings("try")
    @Override
    public void log(String message) {
        try (LibGraalScope scope = new LibGraalScope(HotSpotJVMCIRuntime.runtime())) {
            HotSpotToSVMCalls.log(getIsolateThread(), message);
        }
    }
}
