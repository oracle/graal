/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.hotspot.data;

import java.nio.*;
import java.util.*;

import com.oracle.graal.api.code.CompilationResult.Data;
import com.oracle.graal.api.code.CompilationResult.DataPatch;
import com.oracle.graal.api.code.CompilationResult.Site;
import com.oracle.graal.api.code.*;
import com.oracle.graal.asm.*;

/**
 * Represents the data section of a method.
 */
public final class DataSection {

    /**
     * The minimum alignment required for this data section.
     */
    public final int sectionAlignment;

    /**
     * The raw data contained in the data section.
     */
    public final byte[] data;

    /**
     * A list of locations where oop pointers need to be patched by the runtime.
     */
    public final DataPatch[] patches;

    public DataSection(TargetDescription target, Site[] sites) {
        int size = 0;
        int patchCount = 0;
        List<DataPatch> externalDataList = new ArrayList<>();

        // find all external data items and determine total size of data section
        for (Site site : sites) {
            if (site instanceof DataPatch) {
                DataPatch dataPatch = (DataPatch) site;
                Data d = dataPatch.data;
                if (dataPatch.inline) {
                    assert d instanceof PatchedData : "unnecessary data patch";
                } else {
                    size = NumUtil.roundUp(size, d.getAlignment());
                    size += d.getSize(target);
                    externalDataList.add(dataPatch);
                    if (d instanceof PatchedData) {
                        patchCount++;
                    }
                }
            }
        }

        data = new byte[size];
        patches = new DataPatch[patchCount];
        ByteBuffer buffer = ByteBuffer.wrap(data).order(ByteOrder.nativeOrder());
        int index = 0;
        int patchIndex = 0;
        int alignment = 0;

        // build data section
        for (DataPatch dataPatch : externalDataList) {
            assert !dataPatch.inline;
            Data d = dataPatch.data;

            alignment = Math.max(alignment, d.getAlignment());
            index = NumUtil.roundUp(index, d.getAlignment());
            buffer.position(index);

            DataSectionReference reference = new DataSectionReference(index);
            if (d instanceof PatchedData) {
                // record patch location
                patches[patchIndex++] = new DataPatch(index, d, true);
            }
            dataPatch.data = reference;

            index += d.getSize(target);
            d.emit(target, buffer);
        }

        this.sectionAlignment = alignment;
    }
}
