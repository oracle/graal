/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.posix.debug;

import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.objectfile.debugentry.CompiledMethodEntry;
import com.oracle.svm.core.code.InstalledCodeObserver;
import com.oracle.svm.core.code.InstalledCodeObserverSupport;
import com.oracle.svm.core.code.InstalledCodeObserverSupportFeature;
import com.oracle.svm.core.debug.SubstrateDebugInfoInstaller;
import com.oracle.svm.core.debug.SubstrateDebugInfoProvider;
import com.oracle.svm.core.debug.SubstrateDebugInfoWriter;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.jdk.RuntimeSupport;
import com.oracle.svm.core.posix.debug.jitdump.JitdumpProvider;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.word.Word;

public class PosixPerfSubstrateDebugInfoWriter implements SubstrateDebugInfoWriter {

    /**
     * Add a debug info record and a code load record to a jitdump file. Returns a
     * {@link Word#nullPointer} as the records are directly written to a file and no memory is
     * allocated.
     *
     * @param debug a {@code DebugContext} for logging
     * @param debugInfoProvider the {@code SubstrateDebugInfoProvider} for providing debug
     *            information
     * @return a null pointer as no handle is needed
     */
    @Override
    public InstalledCodeObserver.InstalledCodeObserverHandle writeDebugInfo(DebugContext debug, SubstrateDebugInfoProvider debugInfoProvider) {
        /* Fetch the compiled method entry for the run-time compiled method. */
        CompiledMethodEntry compiledMethodEntry = debugInfoProvider.lookupCompiledMethodEntry(debugInfoProvider.getMethod(), debugInfoProvider.getCompilation());

        /* Create the records for a run-time compilation and write them to the jitdump file. */
        JitdumpProvider.writeRecords(debugInfoProvider, compiledMethodEntry);

        /* No memory was allocated, so no handle is needed for tracking it. */
        return Word.nullPointer();
    }
}

@AutomaticallyRegisteredFeature
class PosixPerfSubstrateDebugInfoFeature implements InternalFeature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateDebugInfoInstaller.Options.hasRuntimeDebugInfoFormatSupport(SubstrateDebugInfoInstaller.DEBUG_INFO_JITDUMP_NAME);
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(InstalledCodeObserverSupportFeature.class);
    }

    @Override
    public void registerCodeObserver(RuntimeConfiguration runtimeConfig) {
        ImageSingletons.lookup(InstalledCodeObserverSupport.class).addObserverFactory(new SubstrateDebugInfoInstaller.Factory(runtimeConfig, new PosixPerfSubstrateDebugInfoWriter()));
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        RuntimeSupport.getRuntimeSupport().addStartupHook(JitdumpProvider.startupHook());
        RuntimeSupport.getRuntimeSupport().addShutdownHook(JitdumpProvider.shutdownHook());
    }
}
