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
package com.oracle.svm.hosted.image;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.oracle.objectfile.BasicProgbitsSectionImpl;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.ObjectFile.ProgbitsSectionImpl;
import com.oracle.objectfile.ObjectFile.RelocationKind;
import com.oracle.objectfile.ObjectFile.Section;
import com.oracle.objectfile.SectionName;
import com.oracle.svm.core.graal.code.CGlobalDataInfo;
import com.oracle.svm.core.imagelayer.ImageLayerBuildingSupport;
import com.oracle.svm.guest.staging.c.CGlobalDataImpl;
import com.oracle.svm.hosted.c.CGlobalDataFeature;
import com.oracle.svm.hosted.code.CFunctionLinkages;
import com.oracle.svm.hosted.imagelayer.HostedDynamicLayerInfo;
import com.oracle.svm.hosted.imagelayer.ImageLayerSectionFeature;

/**
 * Builds the PE/COFF metadata needed to patch shared-layer references to symbols that are defined
 * by the final application layer. The recorded fixup table is consumed by Windows runtime support
 * during early isolate startup.
 */
final class PECOFFLayerForwardReferenceSupport {
    private record ForwardSymbolFixup(Section section, int offset, String symbolName) {
    }

    /**
     * On PE/COFF shared layers, some references to symbols defined in the application layer cannot
     * be resolved at link time. Record those slots here so Windows can fix them up via
     * GetProcAddress during early isolate startup.
     */
    private final List<ForwardSymbolFixup> unresolvedForwardSymbolFixups;
    private final ObjectFile objectFile;
    private BasicProgbitsSectionImpl forwardSymbolFixupSectionImpl;

    PECOFFLayerForwardReferenceSupport(ObjectFile objectFile) {
        this.objectFile = objectFile;
        this.unresolvedForwardSymbolFixups = new ArrayList<>();
    }

    public static boolean isEnabled(ObjectFile objectFile) {
        return objectFile.getFormat() == ObjectFile.Format.PECOFF && ImageLayerBuildingSupport.buildingImageLayer();
    }

    void maybeRecordCGlobalForwardReference(Section section, int offset, String symbolName) {
        if (!ImageLayerBuildingSupport.buildingSharedLayer()) {
            return;
        }
        ObjectFile.Symbol sym = objectFile.getSymbolTable().getSymbol(symbolName);
        if (!sym.isDefined() && (CGlobalDataFeature.singleton().isAppLayerForwardReference(symbolName) ||
                        HostedDynamicLayerInfo.singleton().isDelayedMethodSymbol(symbolName))) {
            /*
             * The ADDR64 relocation is still created for every undefined symbol. The linker
             * resolves CRT/API/static-library symbols via import libraries, but symbols forwarded
             * to the application layer must be resolved through GetProcAddress on the executable.
             */
            recordForwardSymbolFixup(section, offset, symbolName);
        }
    }

    static boolean shouldRecordMethodForwardReference(RelocationKind relocationKind) {
        return ImageLayerBuildingSupport.buildingSharedLayer() && RelocationKind.isDirect(relocationKind);
    }

    void maybeRecordMethodForwardReference(ProgbitsSectionImpl sectionImpl, int offset, RelocationKind relocationKind, String localTargetSymbol, String relocationSymbol) {
        if (shouldRecordMethodForwardReference(relocationKind) && !relocationSymbol.equals(localTargetSymbol)) {
            recordForwardSymbolFixup((Section) sectionImpl.getElement(), offset, relocationSymbol);
        }
    }

    /**
     * On PE/COFF, ADDR64 relocations against imported DLL symbols resolve to import thunks (JMP
     * stubs in .text), not to the IAT slot containing the actual symbol address. Use the __imp_
     * symbol only for true external imports. Application-layer forward references still use the
     * CGlobal slot path because they are patched by SVM at runtime.
     */
    boolean markImportAddressTableRelocation(CGlobalDataInfo dataInfo, CGlobalDataImpl<?> data, ProgbitsSectionImpl sectionImpl, int offset, RelocatableBuffer.Info info) {
        if (!dataInfo.isSymbolReference() || objectFile.getFormat() != ObjectFile.Format.PECOFF || ImageLayerBuildingSupport.firstImageBuild() ||
                        CGlobalDataFeature.singleton().isAppLayerForwardReference(data.symbolName) || CFunctionLinkages.singleton().isFunctionLinkage(dataInfo)) {
            return false;
        }
        ObjectFile.Symbol existingSym = objectFile.getSymbolTable().getSymbol(data.symbolName);
        if (existingSym == null || !existingSym.isDefined()) {
            String impSymbol = "__imp_" + data.symbolName;
            if (objectFile.getSymbolTable().getSymbol(impSymbol) == null) {
                objectFile.createUndefinedSymbol(impSymbol, true);
            }
            sectionImpl.markRelocationSite(offset, info.getRelocationKind(), impSymbol, -info.getAddend());
            return true;
        }
        return false;
    }

    /**
     * Pre-create the forward-symbol fixup section so that its symbol is defined before the layer
     * section records a relocation to it. In non-shared builds, the table is empty (count=0). The
     * content is filled after relocation processing.
     */
    void createFixupSectionIfNeeded(int pageSize) {
        forwardSymbolFixupSectionImpl = new BasicProgbitsSectionImpl(new byte[8]);
        SectionName fixupSectionName = new SectionName.ProgbitsSectionName("svm_fix");
        Section forwardSymbolFixupSection = objectFile.newProgbitsSection(
                        fixupSectionName.getFormatDependentName(objectFile.getFormat()),
                        pageSize, true, false, forwardSymbolFixupSectionImpl);
        objectFile.createDefinedSymbol(ImageLayerSectionFeature.PECOFF_FORWARD_SYMBOL_FIXUP_TABLE_SYMBOL_NAME, forwardSymbolFixupSection, 0, 0, false, false, false);
    }

    /**
     * Populate the PE/COFF fixup section with entries for unresolved forward references. The fixup
     * table is read at runtime by WindowsImageLayerRuntimeSupport to resolve forward references via
     * GetProcAddress.
     *
     * <pre>
     * Layout:
     *   int32: numEntries
     *   int32: stringDataOffset (from section start)
     *   int64[numEntries]: absolute addresses of slots (via ADDR64 relocations)
     *   int32[numEntries]: string offsets (relative to stringDataOffset)
     *   char[]: null-terminated symbol name strings
     * </pre>
     */
    void populateFixupSection() {
        if (forwardSymbolFixupSectionImpl == null) {
            return;
        }
        int count = unresolvedForwardSymbolFixups.size();

        if (count == 0) {
            /* Empty fixup table: just the header with count=0. */
            byte[] data = new byte[8];
            ByteBuffer buf = ByteBuffer.wrap(data).order(objectFile.getByteOrder());
            buf.putInt(0);
            buf.putInt(0);
            forwardSymbolFixupSectionImpl.setContent(data);
            return;
        }

        /* Encode symbol names as null-terminated ASCII strings. */
        byte[][] nameBytes = new byte[count][];
        int totalStringSize = 0;
        for (int i = 0; i < count; i++) {
            String name = unresolvedForwardSymbolFixups.get(i).symbolName();
            nameBytes[i] = (name + "\0").getBytes(StandardCharsets.US_ASCII);
            totalStringSize += nameBytes[i].length;
        }

        int headerSize = 8; /* count(4) + stringDataOffset(4) */
        int slotPtrsSize = count * 8; /* int64 per entry (with ADDR64 relocation) */
        int nameOffsetsSize = count * 4; /* int32 per entry */
        int stringDataOffset = headerSize + slotPtrsSize + nameOffsetsSize;
        int totalSize = stringDataOffset + totalStringSize;

        byte[] data = new byte[totalSize];
        ByteBuffer buf = ByteBuffer.wrap(data).order(objectFile.getByteOrder());

        /* Header. */
        buf.putInt(count);
        buf.putInt(stringDataOffset);

        /* Slot pointer placeholders (filled by ADDR64 relocations). */
        for (int i = 0; i < count; i++) {
            buf.putLong(0);
        }

        /* String offsets. */
        int currentStringOffset = 0;
        for (int i = 0; i < count; i++) {
            buf.putInt(currentStringOffset);
            currentStringOffset += nameBytes[i].length;
        }

        /* String data. */
        for (int i = 0; i < count; i++) {
            buf.put(nameBytes[i]);
        }

        forwardSymbolFixupSectionImpl.setContent(data);

        /*
         * Mark ADDR64 relocations for the slot pointer entries. Each relocation references the
         * .data section at the CGlobal slot's offset, so the PE loader resolves it to the absolute
         * runtime address of the slot.
         */
        for (int i = 0; i < count; i++) {
            int offsetInFixupSection = headerSize + i * 8;
            ForwardSymbolFixup fixup = unresolvedForwardSymbolFixups.get(i);
            forwardSymbolFixupSectionImpl.markRelocationSite(offsetInFixupSection, RelocationKind.DIRECT_8, fixup.section().getName(), fixup.offset());
        }
    }

    Set<String> getRecordedForwardReferenceSymbols() {
        return unresolvedForwardSymbolFixups.stream().map(ForwardSymbolFixup::symbolName).collect(Collectors.toSet());
    }

    /**
     * Record a PE/COFF runtime fixup for a slot that references a symbol from a later layer.
     */
    private void recordForwardSymbolFixup(Section section, int offset, String symbolName) {
        unresolvedForwardSymbolFixups.add(new ForwardSymbolFixup(section, offset, symbolName));
    }
}
