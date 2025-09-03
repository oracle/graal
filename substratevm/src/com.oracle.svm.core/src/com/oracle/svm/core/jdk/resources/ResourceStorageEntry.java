/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.core.jdk.resources;

import java.util.Arrays;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.util.VMError;

public final class ResourceStorageEntry extends ResourceStorageEntryBase {

    private static final byte[][] EMPTY_DATA = new byte[0][];

    private final boolean isDirectory;
    private final boolean fromJar;
    private byte[][] data;

    public ResourceStorageEntry(boolean isDirectory, boolean fromJar) {
        this.isDirectory = isDirectory;
        this.fromJar = fromJar;
        this.data = EMPTY_DATA;
    }

    @Override
    public boolean isDirectory() {
        return isDirectory;
    }

    @Override
    public boolean isFromJar() {
        return fromJar;
    }

    @Override
    public byte[][] getData() {
        return data;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @Override
    public void addData(byte[] datum) {
        byte[][] newData = Arrays.copyOf(data, data.length + 1);
        newData[data.length] = datum;
        /* Always use a compact, immutable data structure in the image heap. */
        data = newData;
    }

    /**
     * Helper method that allows replacing the data entries of an existing resource after analysis
     * but before the universe was built. This is only safe because byte[] is always discovered by
     * the analysis and the data is "registered as immutable" in an after compilation hook (see
     * usages of {@link #getData()}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public void replaceData(byte[]... replacementData) {
        VMError.guarantee(BuildPhaseProvider.isAnalysisFinished(), "Replacing data of a resource entry before analysis finished. Register standard resource instead.");
        VMError.guarantee(!BuildPhaseProvider.isCompilationFinished(), "Trying to replace data of a resource entry after compilation finished.");
        this.data = replacementData;
    }

    @Override
    public boolean isException() {
        return false;
    }

    @Override
    public boolean hasData() {
        return true;
    }
}
