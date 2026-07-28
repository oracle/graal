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

import com.oracle.svm.shared.BuildPhaseProvider;
import com.oracle.svm.shared.util.VMError;

public final class ResourceStorageEntry extends ResourceStorageEntryBase {

    private static final byte[][] EMPTY_DATA = new byte[0][];

    private final boolean isDirectory;
    private final boolean fromJar;
    private byte[][] data;
    private int[] rootIds;

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

    public int getRootId(int dataIndex) {
        if (rootIds != null) {
            /*
             * rootIds stores only the non-trivial suffix; indexes before the suffix keep the implicit
             * rootId == dataIndex mapping.
             */
            int offset = rootIdsOffset();
            if (dataIndex >= offset) {
                return rootIds[dataIndex - offset];
            }
        }
        /* Compact entries and suffix-prefix entries both use the implicit identity mapping. */
        return dataIndex;
    }

    /// Maps a dense loader-root ID back to the local data index for this resource entry. Root IDs are
    /// dense per loader, while data indexes are dense only for one resource path. For example, if an
    /// application loader has roots A, B, and C with root IDs 0, 1, and 2, but resource
    /// `META-INF/services/A` exists only in roots A and C, then this entry stores:
    ///
    /// ```
    /// data[0] = content from root A
    ///
    /// data[1] = content from root C
    /// rootIds[0] = 2
    /// ```
    ///
    /// The `rootIds` array stores only the suffix that diverges from the trivial `rootId == dataIndex`
    /// mapping. Its offset is therefore `data.length - rootIds.length`; in the example, `rootIds[0]`
    /// describes `data[1]`.
    ///
    /// A URL such as `resource://.../2!/META-INF/services/A` therefore has to resolve root ID `2` to
    /// local data index `1`. When [#rootIds] is `null`, the compact representation is in use and root
    /// IDs are identical to local data indexes.
    public int getDataIndexForRootId(int rootId) {
        if (rootIds == null) {
            /* Compact representation: every stored data slot uses rootId == dataIndex. */
            return rootId >= 0 && rootId < data.length ? rootId : -1;
        }
        int offset = rootIdsOffset();
        if (rootId >= 0 && rootId < offset) {
            /* The requested root id is still inside the implicit identity-mapped prefix. */
            return rootId;
        }
        /* Search the explicit suffix where root ids no longer have to match local data indexes. */
        for (int i = 0; i < rootIds.length; i++) {
            if (rootIds[i] == rootId) {
                return offset + i;
            }
        }
        return -1;
    }

    private int rootIdsOffset() {
        /* The suffix starts where the implicit rootId == dataIndex prefix ends. */
        return data.length - rootIds.length;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @Override
    public void addData(byte[] datum, int rootId) {
        int dataIndex = data.length;
        int effectiveRootId = rootId >= 0 ? rootId : dataIndex;
        byte[][] newData = Arrays.copyOf(data, data.length + 1);
        newData[dataIndex] = datum;
        /* Always use a compact, immutable data structure in the image heap. */
        data = newData;
        /*
         * Keep the trivial rootId == dataIndex prefix implicit. Once a non-trivial mapping appears,
         * rootIds stores that divergent suffix and must be extended for every later data slot.
         */
        if (rootIds != null || effectiveRootId != dataIndex) {
            int[] newRootIds = rootIds != null ? Arrays.copyOf(rootIds, rootIds.length + 1) : new int[1];
            newRootIds[newRootIds.length - 1] = effectiveRootId;
            rootIds = newRootIds;
        }
    }

    /**
     * Helper method that allows replacing the data entry of an existing resource after analysis but
     * before the universe was built. This is only safe because byte[] is always discovered by the
     * analysis and the data is "registered as immutable" in an after compilation hook (see usages of
     * {@link #getData()}.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public void replaceData(byte[] replacementData) {
        VMError.guarantee(BuildPhaseProvider.isAnalysisFinished(), "Replacing data of a resource entry before analysis finished. Register standard resource instead.");
        VMError.guarantee(!BuildPhaseProvider.isCompilationFinished(), "Trying to replace data of a resource entry after compilation finished.");
        VMError.guarantee(data.length == 1, "Replacing data of a resource entry is only supported for entries with a single data slot.");
        this.data = new byte[][]{replacementData};
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
