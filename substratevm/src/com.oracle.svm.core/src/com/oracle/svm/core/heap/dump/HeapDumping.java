/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heap.dump;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.nativeimage.impl.HeapDumpSupport;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.util.TimeUtils;

import jdk.graal.compiler.api.replacements.Fold;

public abstract class HeapDumping implements HeapDumpSupport {
    @Fold
    public static HeapDumping singleton() {
        return ImageSingletons.lookup(HeapDumping.class);
    }

    public abstract void initializeDumpHeapOnOutOfMemoryError();

    public abstract void teardownDumpHeapOnOutOfMemoryError();

    public static String getHeapDumpPath(String defaultFilename) {
        String heapDumpFilenameOrDirectory = SubstrateOptions.HeapDumpPath.getValue();
        if (heapDumpFilenameOrDirectory.isEmpty()) {
            return defaultFilename;
        }
        var targetPath = Paths.get(heapDumpFilenameOrDirectory);
        if (Files.isDirectory(targetPath)) {
            targetPath = targetPath.resolve(defaultFilename);
        }
        return targetPath.toFile().getAbsolutePath();
    }

    public void dumpHeap(boolean gcBefore) throws IOException {
        String suffix = Long.toString(TimeUtils.currentTimeMillis());
        String defaultFilename = getDefaultHeapDumpFilename(suffix);
        dumpHeap(getHeapDumpPath(defaultFilename), gcBefore);
    }

    protected static String getDefaultHeapDumpFilename(String suffix) {
        return SubstrateOptions.HeapDumpDefaultFilenamePrefix.getValue() + ProcessProperties.getProcessID() + "-" + suffix + ".hprof";
    }

    public abstract void dumpHeapOnOutOfMemoryError();
}
