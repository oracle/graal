/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.objectfile.pecoff;

import java.util.ArrayList;
import java.nio.ByteBuffer;

import com.oracle.objectfile.pecoff.PECoff.IMAGE_RELOCATION;

final class PECoffRelocTableStruct {
    ArrayList<ArrayList<PECoffRelocStruct>> relocEntries;

    PECoffRelocTableStruct(int numsects) {
        relocEntries = new ArrayList<>(numsects);
        for (int i = 0; i < numsects; i++) {
            relocEntries.add(new ArrayList<>());
        }
    }

    void createRelocationEntry(int sectindex, int offset, int symno, int type) {
        PECoffRelocStruct entry = new PECoffRelocStruct(offset, symno, type);
        relocEntries.get(sectindex).add(entry);
    }

    static int getAlign() {
        return (4);
    }

    int getNumRelocs(int sectionIndex) {
        return relocEntries.get(sectionIndex).size();
    }

    // Return the relocation entries for a single section
    // or null if no entries added to section
    byte[] getRelocData(int sectionIndex) {
        ArrayList<PECoffRelocStruct> entryList = relocEntries.get(sectionIndex);
        int entryCount = entryList.size();
        int allocCount = entryCount;

        if (entryCount == 0) {
            return null;
        }
        if (entryCount > 0xFFFF) {
            allocCount++;
        }
        ByteBuffer relocData = PECoffByteBuffer.allocate(allocCount * IMAGE_RELOCATION.totalsize);

        // If number of relocs exceeds 65K, add the real size
        // in a dummy first reloc entry
        if (entryCount > 0xFFFF) {
            PECoffRelocStruct entry = new PECoffRelocStruct(allocCount, 0, 0);
            relocData.put(entry.getArray());
        }

        // Copy each entry to a single ByteBuffer
        for (int i = 0; i < entryCount; i++) {
            PECoffRelocStruct entry = entryList.get(i);
            relocData.put(entry.getArray());
        }

        return (relocData.array());
    }
}
