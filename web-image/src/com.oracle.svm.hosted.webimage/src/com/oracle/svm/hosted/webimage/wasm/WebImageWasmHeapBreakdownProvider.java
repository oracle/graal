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

package com.oracle.svm.hosted.webimage.wasm;

import com.oracle.svm.hosted.HeapBreakdownProvider;

public class WebImageWasmHeapBreakdownProvider extends HeapBreakdownProvider {

    /**
     * The actual total heap size to be used for heap breakdowns.
     * <p>
     * For Wasm, the heap size in the final executable may be smaller than the sum of individual
     * objects because Wasm can omit large chunks of null-bytes. However, for the heap breakdown, we
     * do want the full size including any gaps that were removed.
     */
    private int actualTotalHeapSize = -1;

    public void setActualTotalHeapSize(int actualTotalHeapSize) {
        assert this.actualTotalHeapSize == -1 : "Total heap size was set before";
        assert actualTotalHeapSize >= 0 : "Invalid total heap size: " + actualTotalHeapSize;
        this.actualTotalHeapSize = actualTotalHeapSize;
    }

    @Override
    protected void setTotalHeapSize(long totalHeapSize) {
        if (actualTotalHeapSize == -1) {
            super.setTotalHeapSize(totalHeapSize);
        } else {
            super.setTotalHeapSize(actualTotalHeapSize);
        }
    }
}
