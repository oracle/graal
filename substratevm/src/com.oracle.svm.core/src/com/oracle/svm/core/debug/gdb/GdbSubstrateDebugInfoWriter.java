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

package com.oracle.svm.core.debug.gdb;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.objectfile.BasicNobitsSectionImpl;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.SectionName;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.code.InstalledCodeObserver;
import com.oracle.svm.core.code.InstalledCodeObserverSupport;
import com.oracle.svm.core.code.InstalledCodeObserverSupportFeature;
import com.oracle.svm.core.debug.SubstrateDebugInfoInstaller;
import com.oracle.svm.core.debug.SubstrateDebugInfoProvider;
import com.oracle.svm.core.debug.SubstrateDebugInfoWriter;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.meta.RuntimeConfiguration;
import com.oracle.svm.core.nmt.NmtCategory;
import com.oracle.svm.core.os.VirtualMemoryProvider;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.debug.DebugContext;

public class GdbSubstrateDebugInfoWriter implements SubstrateDebugInfoWriter {
    /**
     * Generate debug info and write it into an in-memory object file. The in-memory object file is
     * wrapped in a {@link GdbJitHandle}, which is responsible for tracking the allocated memory and
     * cleaning it up after deoptimization.
     *
     * @param debug a {@code DebugContext} for logging
     * @param debugInfoProvider the {@code SubstrateDebugInfoProvider} for providing debug
     *            information
     * @return a code observer handle wrapping an in-memory debug info object file
     */
    @Override
    public InstalledCodeObserver.InstalledCodeObserverHandle writeDebugInfo(DebugContext debug, SubstrateDebugInfoProvider debugInfoProvider) {
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

        return GdbJitHandleAccessor.createHandle(debug, debugInfoData);
    }
}

@AutomaticallyRegisteredFeature
class GdbSubstrateDebugInfoFeature implements InternalFeature {

    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateDebugInfoInstaller.Options.hasRuntimeDebugInfoFormatSupport(SubstrateDebugInfoInstaller.DEBUG_INFO_OBJFILE_NAME);
    }

    @Override
    public List<Class<? extends Feature>> getRequiredFeatures() {
        return List.of(InstalledCodeObserverSupportFeature.class);
    }

    @Override
    public void registerCodeObserver(RuntimeConfiguration runtimeConfig) {
        ImageSingletons.lookup(InstalledCodeObserverSupport.class).addObserverFactory(new SubstrateDebugInfoInstaller.Factory(runtimeConfig, new GdbSubstrateDebugInfoWriter()));
        ImageSingletons.add(GdbJitHandleAccessor.class, new GdbJitHandleAccessor());
    }
}
