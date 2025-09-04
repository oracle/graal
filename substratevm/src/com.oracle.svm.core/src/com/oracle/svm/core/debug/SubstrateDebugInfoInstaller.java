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
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.struct.RawField;
import org.graalvm.nativeimage.c.struct.RawStructure;
import org.graalvm.nativeimage.c.struct.SizeOf;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.word.Pointer;

import com.oracle.objectfile.BasicNobitsSectionImpl;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.SectionName;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.code.InstalledCodeObserver;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.memory.NativeMemory;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.os.VirtualMemoryProvider;
import com.oracle.svm.core.thread.VMOperation;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.code.CompilationResult;
import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.debug.DebugContext;
import jdk.vm.ci.meta.MetaAccessProvider;

public final class SubstrateDebugInfoInstaller implements InstalledCodeObserver {

    private final DebugContext debug;
    private final SubstrateDebugInfoProvider substrateDebugInfoProvider;
    private final ObjectFile objectFile;
    private final ArrayList<ObjectFile.Element> sortedObjectFileElements;
    private final int debugInfoSize;

    static final class Factory implements InstalledCodeObserver.Factory {

        private final MetaAccessProvider metaAccess;
        private final RuntimeConfiguration runtimeConfiguration;

        Factory(MetaAccessProvider metaAccess, RuntimeConfiguration runtimeConfiguration) {
            this.metaAccess = metaAccess;
            this.runtimeConfiguration = runtimeConfiguration;
        }

        @Override
        public InstalledCodeObserver create(DebugContext debugContext, SharedMethod method, CompilationResult compilation, Pointer code, int codeSize) {
            try {
                return new SubstrateDebugInfoInstaller(debugContext, method, compilation, metaAccess, runtimeConfiguration, code, codeSize);
            } catch (Throwable t) {
                throw VMError.shouldNotReachHere(t);
            }
        }
    }

    private SubstrateDebugInfoInstaller(DebugContext debugContext, SharedMethod method, CompilationResult compilation, MetaAccessProvider metaAccess, RuntimeConfiguration runtimeConfiguration,
                    Pointer code, int codeSize) {
        debug = debugContext;
        substrateDebugInfoProvider = new SubstrateDebugInfoProvider(debugContext, method, compilation, runtimeConfiguration, metaAccess, code.rawValue());

        int pageSize = NumUtil.safeToInt(ImageSingletons.lookup(VirtualMemoryProvider.class).getGranularity().rawValue());
        objectFile = ObjectFile.createRuntimeDebugInfo(pageSize);
        objectFile.newNobitsSection(SectionName.TEXT.getFormatDependentName(objectFile.getFormat()), new BasicNobitsSectionImpl(codeSize));
        objectFile.installDebugInfo(substrateDebugInfoProvider);
        sortedObjectFileElements = new ArrayList<>();
        debugInfoSize = objectFile.bake(sortedObjectFileElements);

        if (debugContext.isLogEnabled()) {
            dumpObjectFile();
        }
    }

    private void dumpObjectFile() {
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

    @RawStructure
    private interface Handle extends InstalledCodeObserverHandle {
        int INITIALIZED = 0;
        int ACTIVATED = 1;
        int RELEASED = 2;

        @RawField
        GdbJitInterface.JITCodeEntry getRawHandle();

        @RawField
        void setRawHandle(GdbJitInterface.JITCodeEntry value);

        @RawField
        NonmovableArray<Byte> getDebugInfoData();

        @RawField
        void setDebugInfoData(NonmovableArray<Byte> data);

        @RawField
        int getState();

        @RawField
        void setState(int value);
    }

    static final class GdbJitAccessor implements InstalledCodeObserverHandleAccessor {

        static Handle createHandle(NonmovableArray<Byte> debugInfoData) {
            Handle handle = NativeMemory.malloc(SizeOf.get(Handle.class), NmtCategory.Code);
            GdbJitInterface.JITCodeEntry entry = NativeMemory.calloc(SizeOf.get(GdbJitInterface.JITCodeEntry.class), NmtCategory.Code);
            handle.setAccessor(ImageSingletons.lookup(GdbJitAccessor.class));
            handle.setRawHandle(entry);
            handle.setDebugInfoData(debugInfoData);
            handle.setState(Handle.INITIALIZED);
            return handle;
        }

        @Override
        public void activate(InstalledCodeObserverHandle installedCodeObserverHandle) {
            Handle handle = (Handle) installedCodeObserverHandle;
            VMOperation.guaranteeInProgressAtSafepoint("SubstrateDebugInfoInstaller.Accessor.activate must run in a VMOperation");
            VMError.guarantee(handle.getState() == Handle.INITIALIZED);

            NonmovableArray<Byte> debugInfoData = handle.getDebugInfoData();
            CCharPointer address = NonmovableArrays.addressOf(debugInfoData, 0);
            int size = NonmovableArrays.lengthOf(debugInfoData);
            GdbJitInterface.registerJITCode(address, size, handle.getRawHandle());

            handle.setState(Handle.ACTIVATED);
        }

        @Override
        @Uninterruptible(reason = "Called during GC or teardown.")
        public void release(InstalledCodeObserverHandle installedCodeObserverHandle) {
            Handle handle = (Handle) installedCodeObserverHandle;
            GdbJitInterface.JITCodeEntry entry = handle.getRawHandle();
            // Handle may still be just initialized here, so it never got registered in GDB.
            if (handle.getState() == Handle.ACTIVATED) {
                GdbJitInterface.unregisterJITCode(entry);
                handle.setState(Handle.RELEASED);
            }
            NativeMemory.free(entry);
            NonmovableArrays.releaseUnmanagedArray(handle.getDebugInfoData());
            NativeMemory.free(handle);
        }

        @Override
        public void detachFromCurrentIsolate(InstalledCodeObserverHandle installedCodeObserverHandle) {
            Handle handle = (Handle) installedCodeObserverHandle;
            NonmovableArrays.untrackUnmanagedArray(handle.getDebugInfoData());
        }

        @Override
        public void attachToCurrentIsolate(InstalledCodeObserverHandle installedCodeObserverHandle) {
            Handle handle = (Handle) installedCodeObserverHandle;
            NonmovableArrays.trackUnmanagedArray(handle.getDebugInfoData());
        }
    }

    @Override
    @SuppressWarnings("try")
    public InstalledCodeObserverHandle install() {
        NonmovableArray<Byte> debugInfoData = writeDebugInfoData();
        Handle handle = GdbJitAccessor.createHandle(debugInfoData);
        if (debug.isLogEnabled()) {
            try (DebugContext.Scope s = debug.scope("RuntimeCompilation")) {
                debug.log(toString(handle));
            }
        }
        return handle;
    }

    private NonmovableArray<Byte> writeDebugInfoData() {
        NonmovableArray<Byte> array = NonmovableArrays.createByteArray(debugInfoSize, NmtCategory.Code);
        objectFile.writeBuffer(sortedObjectFileElements, NonmovableArrays.asByteBuffer(array));
        return array;
    }

    private static String toString(Handle handle) {
        return "DebugInfoHandle(handle = 0x" + Long.toHexString(handle.getRawHandle().rawValue()) +
                        ", address = 0x" +
                        Long.toHexString(NonmovableArrays.addressOf(handle.getDebugInfoData(), 0).rawValue()) +
                        ", size = " +
                        NonmovableArrays.lengthOf(handle.getDebugInfoData()) +
                        ", handleState = " +
                        handle.getState() +
                        ")";
    }
}
