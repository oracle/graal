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
import java.nio.channels.FileLock;
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

    private SubstrateDebugInfoInstaller(DebugContext debugContext, SharedMethod method, CompilationResult compilation, RuntimeConfiguration runtimeConfig, Pointer code, int codeSize) {
        debug = debugContext;
        long codeAddress = code.rawValue();

        // Initialize the debug info generator.
        SubstrateDebugInfoProvider debugInfoProvider = new SubstrateDebugInfoProvider(debug, method, compilation, runtimeConfig, runtimeConfig.getProviders().getMetaAccess(), codeAddress, codeSize);

        // Produce a full debug info object file if needed.
        if (SubstrateDebugInfoFeature.Options.hasRuntimeDebugInfoObjectFileSupport()) {
            debugInfoData = getDebugInfoData(debugInfoProvider);
        } else {
            debugInfoData = Word.nullPointer();
        }

        // Create a perf map for the current pid if it does not exist and append a new entry.
        if (SubstrateDebugInfoFeature.Options.hasRuntimeDebugInfoPerfMapSupport()) {
            writePerfMap(debugInfoProvider);
        }

        // Append new records to the jitdump file.
        if (SubstrateDebugInfoFeature.Options.hasRuntimeDebugInfoJitdumpSupport()) {
            writeJitdump(debugInfoProvider);
        }
    }

    private NonmovableArray<Byte> getDebugInfoData(SubstrateDebugInfoProvider debugInfoProvider) {
        // Set up the debug info object file.
        int pageSize = NumUtil.safeToInt(ImageSingletons.lookup(VirtualMemoryProvider.class).getGranularity().rawValue());
        ObjectFile objectFile = ObjectFile.createRuntimeDebugInfo(pageSize);
        objectFile.newNobitsSection(SectionName.TEXT.getFormatDependentName(objectFile.getFormat()), new BasicNobitsSectionImpl(debugInfoProvider.getCodeSize()));

        // Generate debug info and bake the object file.
        objectFile.installDebugInfo(debugInfoProvider);
        ArrayList<ObjectFile.Element> sortedObjectFileElements = new ArrayList<>();
        int debugInfoSize = objectFile.bake(sortedObjectFileElements);

        // Store the object file in a byte array -> This is now an in-memory object file.
        NonmovableArray<Byte> debugInfoData = NonmovableArrays.createByteArray(debugInfoSize, NmtCategory.Code);
        objectFile.writeBuffer(sortedObjectFileElements, NonmovableArrays.asByteBuffer(debugInfoData));

        if (debug.isLogEnabled()) {
            // Dump the object file to the file system.
            StringBuilder sb = new StringBuilder(debugInfoProvider.getCompilationName()).append(".debug");
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

    @SuppressWarnings({"unsused", "try"})
    private void writePerfMap(SubstrateDebugInfoProvider debugInfoProvider) {
        String methodName = debugInfoProvider.getMethod().format("%R %H.%n(%P)");
        String perfMapFilename = "perf-" + ProcessProperties.getProcessID() + ".map";
        Path perfMapPath = Paths.get("/tmp", perfMapFilename);

        /*
         * Create one line for the perf map write to the perf map file.
         */
        ByteBuffer perfMapEntry = ByteBuffer.wrap(String.format("%x %x %s%n", debugInfoProvider.getCodeAddress(), debugInfoProvider.getCodeSize(), methodName).getBytes());
        try (FileChannel channel = FileChannel.open(perfMapPath, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                        FileLock lock = channel.lock()) {
            int written = channel.write(perfMapEntry);
            assert written == perfMapEntry.limit();
        } catch (IOException e) {
            debug.log("Failed to write perf map entry for " + methodName);
        }
    }

    @SuppressWarnings({"unsused", "try"})
    private void writeJitdump(SubstrateDebugInfoProvider debugInfoProvider) {
        /*
         * Fetch the compiled method entry for the run-time compiled method.
         */
        CompiledMethodEntry compiledMethodEntry = debugInfoProvider.lookupCompiledMethodEntry(debugInfoProvider.getMethod(), debugInfoProvider.getCompilation());

        /*
         * Create the records for a run-time compilation and write them to the jitdump file.
         */
        ByteBuffer records = JitdumpProvider.createRecords(debugInfoProvider, compiledMethodEntry);
        try (FileChannel channel = FileChannel.open(JitdumpProvider.getJitdumpPath(), StandardOpenOption.APPEND);
                        FileLock lock = channel.lock()) {
            int written = channel.write(records);
            assert written == records.limit();
        } catch (IOException e) {
            debug.log("Failed to write the jitdump records for " + compiledMethodEntry.primary().getFullMethodName());
        }
    }

    @Override
    public InstalledCodeObserverHandle install() {
        if (SubstrateDebugInfoFeature.Options.hasRuntimeDebugInfoObjectFileSupport()) {
            assert debugInfoData.isNonNull() : "Run-time debug info file is emtpy!";
            return GdbJitAccessor.createHandle(debug, debugInfoData);
        } else {
            return Word.nullPointer();
        }
    }
}
