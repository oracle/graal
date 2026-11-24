/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.test.unit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

import org.junit.Test;

import com.oracle.svm.core.image.ImageHeapPartition;
import com.oracle.svm.hosted.webimage.wasmgc.image.WasmGCHeapLayouter;
import com.oracle.svm.hosted.webimage.wasmgc.image.WasmGCPartition;
import com.oracle.svm.shared.util.VMError;

public class WasmGCHeapReleaseTest {
    @Test
    public void releaseIsTerminalButPreservesScalarMetadata() {
        WasmGCHeapLayouter layouter = new WasmGCHeapLayouter();
        for (ImageHeapPartition partition : layouter.getPartitions()) {
            WasmGCPartition wasmPartition = (WasmGCPartition) partition;
            wasmPartition.setSize(128);
        }

        layouter.releaseObjectReferences();
        for (ImageHeapPartition partition : layouter.getPartitions()) {
            WasmGCPartition wasmPartition = (WasmGCPartition) partition;
            assertEquals(128, wasmPartition.getSize());
            assertEquals(0, wasmPartition.getStartOffset());
            assertThrows(VMError.HostedError.class, wasmPartition::getObjects);
            assertThrows(VMError.HostedError.class, () -> wasmPartition.assign(null));
            assertThrows(VMError.HostedError.class, () -> wasmPartition.sortObjects(null));
        }
        assertThrows(VMError.HostedError.class, layouter::releaseObjectReferences);
    }
}
