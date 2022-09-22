/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.heapdump;

import java.io.FileOutputStream;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.impl.HeapDumpSupport;

import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;

@AutomaticallyRegisteredFeature
public final class HeapDumpFeature implements InternalFeature {
    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(HeapDumpSupport.class, new HeapDumpSupportImpl());
        ImageSingletons.add(HeapDumpUtils.class, new HeapDumpUtils());
        if (Platform.includedIn(Platform.WINDOWS.class)) {
            ImageSingletons.add(HeapDumpWriter.class, new UnimplementedHeapDumpWriter("Currently not supported for " + ImageSingletons.lookup(Platform.class)));
        } else {
            ImageSingletons.add(HeapDumpWriter.class, new HeapDumpWriterImpl());
        }
    }
}

final class HeapDumpSupportImpl implements HeapDumpSupport {
    @Override
    public void dumpHeap(String outputFile, boolean live) throws java.io.IOException {
        try (FileOutputStream fileOutputStream = new FileOutputStream(outputFile)) {
            HeapDumpWriter.singleton().writeHeapTo(fileOutputStream, live);
        }
    }
}
