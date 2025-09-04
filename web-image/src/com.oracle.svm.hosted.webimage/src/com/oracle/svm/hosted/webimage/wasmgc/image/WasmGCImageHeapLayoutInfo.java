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

package com.oracle.svm.hosted.webimage.wasmgc.image;

import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.heap.UnknownPrimitiveField;
import com.oracle.svm.core.image.ImageHeapLayoutInfo;

/**
 * Additional layout information required for the WasmGC backend.
 * <p>
 * The fields in the superclass are set so that other Native Image code does not encounter
 * unexpected values (e.g. {@link com.oracle.svm.hosted.ProgressReporter}, which requires an image
 * heap size that corresponds to the sum of all object sizes plus padding).
 */
public class WasmGCImageHeapLayoutInfo extends ImageHeapLayoutInfo {

    @UnknownPrimitiveField(availability = BuildPhaseProvider.AfterHeapLayout.class) private final long serializedSize;

    /**
     * @param serializedSize Size of the object data that is serialized into a data segment.
     * @param theoreticalSize Size of all the image heap objects, if Native Image was laying them
     *            out in memory. This is mainly used for the build statistics and does not
     *            correspond directly to the image heap size in the Wasm binary (this cannot be
     *            known before the binary is assembled).
     */
    public WasmGCImageHeapLayoutInfo(long serializedSize, long theoreticalSize) {
        super(0, theoreticalSize, 0, theoreticalSize, 0L, 0L, 0L, 0L);

        this.serializedSize = serializedSize;
    }

    @Override
    protected boolean verifyAlignment() {
        /* Ignore alignment. */
        return true;
    }

    public long getSerializedSize() {
        return serializedSize;
    }
}
