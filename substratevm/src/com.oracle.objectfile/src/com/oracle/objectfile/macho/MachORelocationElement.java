/*
 * Copyright (c) 2013, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.objectfile.macho;

import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import com.oracle.objectfile.BuildDependency;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.ObjectFile.Element;
import com.oracle.objectfile.ObjectFile.RelocationKind;
import com.oracle.objectfile.ObjectFile.RelocationMethod;
import com.oracle.objectfile.ObjectFile.RelocationRecord;
import com.oracle.objectfile.ObjectFile.Symbol;
import com.oracle.objectfile.SymbolTable;
import com.oracle.objectfile.io.AssemblyBuffer;
import com.oracle.objectfile.io.OutputAssembler;
import com.oracle.objectfile.macho.MachOObjectFile.MachOSection;
import com.oracle.objectfile.macho.MachOObjectFile.Segment64Command;

class MachORelocationElement extends MachOObjectFile.LinkEditElement {
    /*
     * We are a bunch of RelocationInfo.Structs grouped by relocated section, ordered by the offset
     * within the section.
     */

    private static int compareSectionThenOffset(MachORelocationInfo p, MachORelocationInfo q) {
        if (!p.getRelocatedSection().equals(q.getRelocatedSection())) {
            return p.getRelocatedSection().hashCode() - q.getRelocatedSection().hashCode();
        }
        return Math.toIntExact(p.getOffset() - q.getOffset());
    }

    private Map<MachORelocationInfo, MachORelocationInfo> infos = new TreeMap<>(MachORelocationElement::compareSectionThenOffset);
    private Set<MachOSection> relocatedSections = new HashSet<>();

    MachORelocationElement(Segment64Command segment) {
        segment.getOwner().super("MachORelocationElement", segment);
        assert segment.getOwner().relocs == null;
        segment.getOwner().relocs = this;
    }

    public void add(MachORelocationInfo rec) {
        if (infos.putIfAbsent(rec, rec) == null) {
            relocatedSections.add(rec.getRelocatedSection());
        }
    }

    public boolean relocatesSegment(Segment64Command seg) {
        return seg.elementsInSegment.stream().anyMatch(e -> e instanceof MachOSection && relocatedSections.contains(e));
    }

    @Override
    public byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
        OutputAssembler out = AssemblyBuffer.createOutputAssembler(getOwner().getByteOrder());
        for (MachORelocationInfo rec : infos.keySet()) {
            rec.write(out, alreadyDecided);
        }
        assert getOrDecideSize(alreadyDecided, -1) == out.pos();
        return out.getBlob();
    }

    @Override
    public int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
        return infos.size() * encodedEntrySize();
    }

    @Override
    public Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
        return ObjectFile.minimalDependencies(decisions, this);
    }

    public int startIndexFor(MachOSection s) {
        int i = 0;
        for (MachORelocationInfo info : infos.keySet()) {
            if (info.getRelocatedSection() == s) {
                return i;
            }
            i++;
        }
        return -1;
    }

    public int encodedEntrySize() {
        return MachORelocationInfo.getEncodedSize();
    }

    public int countFor(MachOSection s) {
        return Math.toIntExact(infos.keySet().stream().filter(struct -> s == struct.getRelocatedSection()).count());
    }
}

/**
 * These are defined as an enum in
 * https://github.com/apple/darwin-xnu/blob/2ff845c2e033bd0ff64b5b6aa6063a1f8f65aa32/EXTERNAL_HEADERS/mach-o/x86_64/reloc.h#L173
 * which we reproduce. Of course, take care to preserve the order!
 *
 * For examples of how these symbols are used, see the linked file above and
 * https://github.com/apple/darwin-xnu/blob/2ff845c2e033bd0ff64b5b6aa6063a1f8f65aa32/EXTERNAL_HEADERS/mach-o/reloc.h.
 */
enum X86_64Reloc {
    UNSIGNED, // for absolute addresses
    SIGNED, // for signed 32-bit displacement
    BRANCH, // a CALL/JMP instruction with 32-bit displacement
    GOT_LOAD, // a MOVQ load of a GOT entry
    GOT, // other GOT references
    SUBTRACTOR, // must be followed by a X86_64_RELOC_UNSIGNED
    SIGNED_1, // for signed 32-bit displacement with a -1 addend
    SIGNED_2, // for signed 32-bit displacement with a -2 addend
    SIGNED_4, // for signed 32-bit displacement with a -4 addend
    TLV; // for thread local variables

    public int getValue() {
        return ordinal();
    }
}

/**
 * These are defined as an enum in
 * https://github.com/apple/darwin-xnu/blob/2ff845c2e033bd0ff64b5b6aa6063a1f8f65aa32/EXTERNAL_HEADERS/mach-o/arm64/reloc.h#L26,
 * which we reproduce. Of course, take care to preserve the order!
 *
 * For examples of how these symbols are used, see
 * https://github.com/apple/darwin-xnu/blob/2ff845c2e033bd0ff64b5b6aa6063a1f8f65aa32/EXTERNAL_HEADERS/mach-o/arm/reloc.h
 * (for AArch32 information, but does provide some insight) and
 * https://github.com/apple/darwin-xnu/blob/2ff845c2e033bd0ff64b5b6aa6063a1f8f65aa32/EXTERNAL_HEADERS/mach-o/reloc.h.
 */
enum ARM64Reloc {
    UNSIGNED, // for pointers
    SUBTRACTOR, // must be followed by a ARM64_RELOC_UNSIGNED
    BRANCH26, // a B/BL instruction with 26-bit displacement
    PAGE21, // pc-rel distance to page of target
    PAGEOFF12, // offset within page, scaled by r_length
    GOT_LOAD_PAGE21, // pc-rel distance to page of GOT slot
    GOT_LOAD_PAGEOFF12, // offset within page of GOT slot, scaled by r_length
    POINTER_TO_GOT, // for pointers to GOT slots
    TLVP_LOAD_PAGE21, // pc-rel distance to page of TLVP slot
    TLVP_LOAD_PAGEOFF12, // offset within page of TLVP slot, scaled by r_length
    ADDEND; // must be followed by PAGE21 or PAGEOFF12

    public int getValue() {
        return ordinal();
    }
}

final class MachORelocationInfo implements RelocationRecord, RelocationMethod {

    private final MachORelocationElement containingElement;
    private final MachOSection relocatedSection;
    private final RelocationKind kind;
    private final int sectionOffset;
    private final Symbol sym;
    private final MachOSection targetSection;
    private final byte log2length;

    /**
     * Construct a relocation record.
     *
     * @param containingElement the containing relocation element
     * @param relocatedSection the section being relocated
     * @param offset the offset, within the relocated section, of the relocation site
     * @param requestedLength the length of the relocation site
     * @param kind the kind of relocation to perform at the relocation site
     * @param symbolName the symbol against which to relocate
     */
    MachORelocationInfo(MachORelocationElement containingElement, MachOSection relocatedSection, int offset, int requestedLength, RelocationKind kind, String symbolName, boolean asLocalReloc) {
        this.containingElement = containingElement;
        this.relocatedSection = relocatedSection;
        this.sectionOffset = offset; // gets turned into a vaddr on write-out
        /*
         * NOTE: the Mach-O spec claims that r_length == 3 means a 4-byte length and not an 8-byte
         * length. But it doesn't say how to encode an 8-bytes-long relocation site. And the
         * following link seems to suggest that in the case of x86-64, r_length==3 does encode the
         * 8-byte case.
         * http://www.opensource.apple.com/source/xnu/xnu-1456.1.26/EXTERNAL_HEADERS/mach
         * -o/x86_64/reloc.h
         *
         * Experimenting....
         */
        if (requestedLength != 8 && requestedLength != 4 && requestedLength != 2 && requestedLength != 1) {
            throw new IllegalArgumentException("Mach-O cannot represent relocation lengths other than {1,2,4,8} bytes");
        }
        this.log2length = (byte) ((requestedLength == 8) ? 3 : (requestedLength == 4) ? 2 : (requestedLength == 2) ? 1 : 0);
        this.kind = kind;
        SymbolTable symtab = relocatedSection.getOwner().getSymbolTable();
        // FIXME: also allow section numbers here, for non-extern symbols
        // FIXME: encode R_ABS symbol number
        this.sym = symtab.getSymbol(symbolName);
        // if the symbol is defined in the same file, i.e. locally, we have a target section
        assert !asLocalReloc || this.sym.isDefined();
        this.targetSection = asLocalReloc ? (MachOSection) this.sym.getDefinedSection() : null;
    }

    public static int getEncodedSize() {
        return 8;
    }

    public void write(OutputAssembler oa, @SuppressWarnings("unused") Map<Element, LayoutDecisionMap> alreadyDecided) {
        /* We need to convert in-section offsets to vaddrs if we are writing dynamic object. */
        // "extern" means symbolNum is a symbol not a section number
        int symbolNum;
        if (isExtern()) {
            // we're non-local, so use a symbol
            symbolNum = relocatedSection.getOwner().getSymbolTable().indexOf(sym);
        } else {
            // we're local, so use the section
            symbolNum = relocatedSection.getOwner().getSections().indexOf(sym.getDefinedSection());
            assert sym.getDefinedOffset() == 0 : "Relocation for non-external symbol with section base offset != 0 not supported";
        }
        if (log2length < 0 || log2length >= 4) {
            throw new IllegalArgumentException("length must be in {1,2,4,8} bytes, so log2length must be in [0,3]");
        }
        int startPos = oa.pos();
        oa.write4Byte(sectionOffset);
        /*
         * The Mach-O documentation just gives us a struct with bitfields. This doesn't give us
         * enough information, because the C compiler can define bitfield layout how it pleases.
         * Some Googling reveals that the "usual" way is endianness-dependent: on a big-endian
         * target, the "first" bitfield gets the most significant bits, whereas on a little-endian
         * target, the first bitfield gets the least significant bits. The net result is that the
         * bits are issued "in order" in both cases. So we can code this fairly sanely without
         * case-splitting for endianness.
         */
        int remainingWord = 0;
        //@formatter:off
        remainingWord |=                       symbolNum & 0x00ffffff;
        remainingWord |=                       isPCRelative() ? (1 << 24) : 0;
        remainingWord |=                        (log2length & 0x3) << 25;
        remainingWord |=                           isExtern() ? (1 << 27) : 0;
        remainingWord |=          (getMachORelocationType() & 0xf) << 28;
        //@formatter:on
        oa.write4Byte(remainingWord);
        assert oa.pos() - startPos == 8; // check we wrote how much we expected
    }

    @Override
    public long getOffset() {
        return sectionOffset;
    }

    @Override
    public Symbol getReferencedSymbol() {
        // assert extern;
        // FIXME: what about the !extern case?
        return sym;
    }

    public MachOSection getRelocatedSection() {
        return relocatedSection;
    }

    private boolean isExtern() {
        // we record localness by grabbing the target section (see constructor)
        return targetSection == null;
    }

    private boolean isPCRelative() {
        switch (kind) {
            case PC_RELATIVE_1:
            case PC_RELATIVE_2:
            case PC_RELATIVE_4:
            case PC_RELATIVE_8:
            case AARCH64_R_AARCH64_ADR_PREL_PG_HI21:
                return true;
            default:
                return false;
        }
    }

    private int getMachORelocationType() {
        switch (getRelocatedSection().getOwner().cpuType) {
            case X86_64:
                switch (kind) {
                    case DIRECT_1:
                    case DIRECT_2:
                    case DIRECT_4:
                    case DIRECT_8:
                        return X86_64Reloc.UNSIGNED.getValue();
                    case PC_RELATIVE_1:
                    case PC_RELATIVE_2:
                    case PC_RELATIVE_4:
                    case PC_RELATIVE_8:
                        return X86_64Reloc.SIGNED.getValue();
                    default:
                    case UNKNOWN:
                        throw new IllegalArgumentException("unknown relocation kind: " + kind);
                }
            case ARM64:
                switch (kind) {
                    case DIRECT_1:
                    case DIRECT_2:
                    case DIRECT_4:
                    case DIRECT_8:
                        return ARM64Reloc.UNSIGNED.getValue();
                    case AARCH64_R_AARCH64_ADR_PREL_PG_HI21:
                        return ARM64Reloc.PAGE21.getValue();
                    case AARCH64_R_AARCH64_LDST64_ABS_LO12_NC:
                    case AARCH64_R_AARCH64_LDST32_ABS_LO12_NC:
                    case AARCH64_R_AARCH64_LDST16_ABS_LO12_NC:
                    case AARCH64_R_AARCH64_LDST8_ABS_LO12_NC:
                    case AARCH64_R_AARCH64_ADD_ABS_LO12_NC:
                        return ARM64Reloc.PAGEOFF12.getValue();
                    default:
                    case UNKNOWN:
                        throw new IllegalArgumentException("unknown relocation kind: " + kind);
                }
            default:
                throw new IllegalArgumentException("unknown relocation kind: " + kind);
        }
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj != null && getClass() == obj.getClass()) {
            MachORelocationInfo other = (MachORelocationInfo) obj;
            return sectionOffset == other.sectionOffset && log2length == other.log2length && Objects.equals(containingElement, other.containingElement) &&
                            Objects.equals(getRelocatedSection(), other.getRelocatedSection()) && kind == other.kind &&
                            Objects.equals(sym, other.sym) && Objects.equals(targetSection, other.targetSection);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return (((((containingElement.hashCode() * 31 + relocatedSection.hashCode()) * 31 + kind.hashCode()) * 31 +
                        sectionOffset) * 31 + sym.hashCode()) * 31 + targetSection.hashCode()) * 31 + log2length;
    }
}
