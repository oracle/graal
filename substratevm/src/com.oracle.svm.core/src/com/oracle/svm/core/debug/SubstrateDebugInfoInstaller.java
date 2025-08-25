/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.debug;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.word.Pointer;

import com.oracle.objectfile.BasicNobitsSectionImpl;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.SectionName;
import com.oracle.objectfile.debugentry.CompiledMethodEntry;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.code.InstalledCodeObserver;
import com.oracle.svm.core.debug.gdb.GdbJitAccessor;
import com.oracle.svm.core.debug.jitdump.JitdumpProvider;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.word.Word;

public final class SubstrateDebugInfoInstaller implements InstalledCodeObserver {

    private final DebugContext debug;
    private final NonmovableArray<Byte> debugInfoData;

    static final class Factory implements InstalledCodeObserver.Factory {

        private final RuntimeConfiguration runtimeConfig;

        Factory(RuntimeConfiguration runtimeConfig) {
            this.runtimeConfig = runtimeConfig;
        }

        @Override
        public InstalledCodeObserver create(DebugContext debugContext, SharedMethod method, CompilationResult compilation, Pointer code, int codeSize) {
            try {
                return new SubstrateDebugInfoInstaller(debugContext, method, compilation, runtimeConfig, code, codeSize);
            } catch (Throwable t) {
                throw VMError.shouldNotReachHere(t);
            }
        }
    }

    private SubstrateDebugInfoInstaller(DebugContext debugContext, SharedMethod method, CompilationResult compilation, RuntimeConfiguration runtimeConfig,
                    Pointer code, int codeSize) {
        debug = debugContext;
        long codeAddress = code.rawValue();

        // Produce a full debug info object file.
        if (SubstrateDebugInfoFeature.Options.hasRuntimeDebugInfoObjectFileSupport()) {
            debugInfoData = getDebugInfoData(method, compilation, runtimeConfig, codeSize, codeAddress);
        } else {
            debugInfoData = null;
        }

        // Create a perf map for the current pid if it does not exist and append a new entry.
        if (SubstrateDebugInfoFeature.Options.hasRuntimeDebugInfoPerfMapSupport()) {
            writePerfMap(method, codeSize, codeAddress);
        }

        // Create a jitdump file for the native image if it does not exist and append new records.
        if (SubstrateDebugInfoFeature.Options.hasRuntimeDebugInfoJitdumpSupport()) {
            writeJitdump(method, compilation, runtimeConfig, codeSize, codeAddress);
        }
    }

    private NonmovableArray<Byte> getDebugInfoData(SharedMethod method, CompilationResult compilation, RuntimeConfiguration runtimeConfig, int codeSize, long codeAddress) {
        // Initialize the debug info generator.
        SubstrateDebugInfoProvider substrateDebugInfoProvider = new SubstrateDebugInfoProvider(debug, method, compilation, runtimeConfig, runtimeConfig.getProviders().getMetaAccess(),
                        codeAddress);

        // Set up the debug info object file.
        int pageSize = NumUtil.safeToInt(ImageSingletons.lookup(VirtualMemoryProvider.class).getGranularity().rawValue());
        ObjectFile objectFile = ObjectFile.createRuntimeDebugInfo(pageSize);
        objectFile.newNobitsSection(SectionName.TEXT.getFormatDependentName(objectFile.getFormat()), new BasicNobitsSectionImpl(codeSize));

        // Generate debug info and bake the object file.
        objectFile.installDebugInfo(substrateDebugInfoProvider);
        ArrayList<ObjectFile.Element> sortedObjectFileElements = new ArrayList<>();
        int debugInfoSize = objectFile.bake(sortedObjectFileElements);

        // Store the object file in a byte array -> This is now an in-memory object file.
        NonmovableArray<Byte> debugInfoData = NonmovableArrays.createByteArray(debugInfoSize, NmtCategory.Code);
        objectFile.writeBuffer(sortedObjectFileElements, NonmovableArrays.asByteBuffer(debugInfoData));

        if (debug.isLogEnabled()) {
            // Dump the object file to the file system.
            StringBuilder sb = new StringBuilder(substrateDebugInfoProvider.getCompilationName()).append(".debug");
            try (FileChannel dumpFile = FileChannel.open(Paths.get(sb.toString()),
                            StandardOpenOption.READ, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING,
                            StandardOpenOption.CREATE)) {
                ByteBuffer buffer = dumpFile.map(FileChannel.MapMode.READ_WRITE, 0, debugInfoSize);
                objectFile.writeBuffer(sortedObjectFileElements, buffer);
            } catch (IOException e) {
                debug.log("Failed to dump %s", sb);
            }
        }

        return debugInfoData;
    }

    private void writePerfMap(SharedMethod method, int codeSize, long codeAddress) {
        String methodName = method.format("%R %H.%n(%P)");
        String perfMapFilename = "perf-" + ProcessProperties.getProcessID() + ".map";
        Path perfMapPath = Paths.get("/tmp", perfMapFilename);

        try {
            Files.write(perfMapPath, String.format("%x %x %s%n", codeAddress, codeSize, methodName).getBytes(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            debug.log("Failed to write perf map entry for " + methodName);
        }
    }

    private void writeJitdump(SharedMethod method, CompilationResult compilation, RuntimeConfiguration runtimeConfig, int codeSize, long codeAddress) {
        Path jitdumpPath = Paths.get("jit-" + Paths.get(ProcessProperties.getExecutableName()).getFileName() + ".dump");

        // Initialize the debug info generator and fetch the compiled method entry for the run-time
        // compiled method.
        SubstrateDebugInfoProvider substrateDebugInfoProvider = new SubstrateDebugInfoProvider(debug, method, compilation, runtimeConfig, runtimeConfig.getProviders().getMetaAccess(),
                        codeAddress);
        CompiledMethodEntry compiledMethodEntry = substrateDebugInfoProvider.lookupCompiledMethodEntry(method, compilation);

        try {
            // Check if file already exists.
            if (!Files.exists(jitdumpPath)) {
                // Create file and write header.
                Files.write(jitdumpPath, JitdumpProvider.writeHeader(), StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
            }
            // Write records for run-time compilation.
            Files.write(jitdumpPath, JitdumpProvider.writeRecords(compiledMethodEntry, compilation, codeSize, codeAddress), StandardOpenOption.APPEND);
        } catch (IOException e) {
            debug.log("Failed to write jitdump.");
        }
    }

    @Override
    public InstalledCodeObserverHandle install() {
        if (SubstrateDebugInfoFeature.Options.hasRuntimeDebugInfoObjectFileSupport()) {
            return GdbJitAccessor.createHandle(debug, debugInfoData);
        } else {
            return Word.nullPointer();
        }
    }
}
