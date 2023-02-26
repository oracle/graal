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

import org.graalvm.compiler.core.common.NumUtil;

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
import org.graalvm.compiler.debug.GraalError;

class MachORelocationElement extends MachOObjectFile.LinkEditElement {
    /*
     * We are a bunch of RelocationInfo.Structs grouped by relocated section, ordered by the offset
     * within the section. Note also that, when present, an explicit addend for a given offset must
     * be stored immediately before its corresponding record.
     */

    private static int compareSectionThenOffset(MachORelocationInfo p, MachORelocationInfo q) {
        if (!p.getRelocatedSection().equals(q.getRelocatedSection())) {
            return p.getRelocatedSection().hashCode() - q.getRelocatedSection().hashCode();
        }
        if (p.getOffset() != q.getOffset()) {
            return Math.toIntExact(p.getOffset() - q.getOffset());
        }

        assert !(p.isAddendKind() && q.isAddendKind()) : "two addends for same relocation";
        // reverse arguments since want the addend kind first
        return Boolean.compare(q.isAddendKind(), p.isAddendKind());
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

interface MachORelocationType {
    boolean isPCRelative();

    int getValue();
}

/**
 * These are defined as an enum in <a href=
 * "https://github.com/apple/darwin-xnu/blob/2ff845c2e033bd0ff64b5b6aa6063a1f8f65aa32/EXTERNAL_HEADERS/mach-o/x86_64/reloc.h#L173">EXTERNAL_HEADERS/mach-o/x86_64/reloc.h#L173</a>
 * which we reproduce. Of course, take care to preserve the order!
 *
 * For examples of how these symbols are used, see the linked file above and <a href=
 * "https://github.com/apple/darwin-xnu/blob/2ff845c2e033bd0ff64b5b6aa6063a1f8f65aa32/EXTERNAL_HEADERS/mach-o/reloc.h">EXTERNAL_HEADERS/mach-o/reloc.h</a>.
 */
enum X86_64Reloc implements MachORelocationType {
    UNSIGNED(0), // for absolute addresses
    SIGNED(1, true), // for signed 32-bit displacement
    BRANCH(2), // a CALL/JMP instruction with 32-bit displacement
    GOT_LOAD(3), // a MOVQ load of a GOT entry
    GOT(4), // other GOT references
    SUBTRACTOR(5), // must be followed by a X86_64_RELOC_UNSIGNED
    SIGNED_1(6, true), // for signed 32-bit displacement with a -1 addend
    SIGNED_2(7, true), // for signed 32-bit displacement with a -2 addend
    SIGNED_4(8, true), // for signed 32-bit displacement with a -4 addend
    TLV(9); // for thread local variables

    private final boolean pcRelative;
    private final int value;

    X86_64Reloc(int value) {
        this.value = value;
        pcRelative = false;
    }

    X86_64Reloc(int value, boolean pcRelative) {
        this.value = value;
        this.pcRelative = pcRelative;
    }

    @Override
    public boolean isPCRelative() {
        return pcRelative;
    }

    @Override
    public int getValue() {
        return value;
    }
}

/**
 * These are defined as an enum in <a href=
 * "https://github.com/apple/darwin-xnu/blob/2ff845c2e033bd0ff64b5b6aa6063a1f8f65aa32/EXTERNAL_HEADERS/mach-o/arm64/reloc.h#L26">EXTERNAL_HEADERS/mach-o/arm64/reloc.h#L26</a>,
 * which we reproduce. Of course, take care to preserve the order!
 *
 * For examples of how these symbols are used, see <a href=
 * "https://github.com/apple/darwin-xnu/blob/2ff845c2e033bd0ff64b5b6aa6063a1f8f65aa32/EXTERNAL_HEADERS/mach-o/arm/reloc.h">EXTERNAL_HEADERS/mach-o/arm/reloc.h</a>
 * (for AArch32 information, but does provide some insight) and <a href=
 * "https://github.com/apple/darwin-xnu/blob/2ff845c2e033bd0ff64b5b6aa6063a1f8f65aa32/EXTERNAL_HEADERS/mach-o/reloc.h">EXTERNAL_HEADERS/mach-o/reloc.h</a>.
 */
enum ARM64Reloc implements MachORelocationType {
    UNSIGNED(0), // for pointers
    SUBTRACTOR(1), // must be followed by a ARM64_RELOC_UNSIGNED
    BRANCH26(2), // a B/BL instruction with 26-bit displacement
    PAGE21(3, true), // pc-rel distance to page of target
    PAGEOFF12(4), // offset within page, scaled by r_length
    GOT_LOAD_PAGE21(5, true), // pc-rel distance to page of GOT slot
    GOT_LOAD_PAGEOFF12(6), // offset within page of GOT slot, scaled by r_length
    POINTER_TO_GOT(7), // for pointers to GOT slots
    TLVP_LOAD_PAGE21(8, true), // pc-rel distance to page of TLVP slot
    TLVP_LOAD_PAGEOFF12(9), // offset within page of TLVP slot, scaled by r_length
    ADDEND(10); // must be followed by PAGE21 or PAGEOFF12

    private final boolean pcRelative;
    private final int value;

    ARM64Reloc(int value) {
        this.value = value;
        pcRelative = false;
    }

    ARM64Reloc(int value, boolean pcRelative) {
        this.value = value;
        this.pcRelative = pcRelative;
    }

    @Override
    public boolean isPCRelative() {
        return pcRelative;
    }

    @Override
    public int getValue() {
        return value;
    }
}

final class MachORelocationInfo implements RelocationRecord, RelocationMethod {

    private final MachORelocationElement containingElement;
    private final MachOSection relocatedSection;
    private final MachORelocationType kind;
    private final int sectionOffset;
    private final Symbol sym;
    private final MachOSection targetSection;
    private final byte log2length;
    private final int addend;

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
    private MachORelocationInfo(MachORelocationElement containingElement, MachOSection relocatedSection, int offset, int requestedLength, MachORelocationType kind, String symbolName,
                    boolean asLocalReloc, int addend) {
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
        this.addend = addend;
    }

    /* Creates an ARM64_RELOC_ADDEND relocation type. */
    static MachORelocationInfo createARM64RelocAddend(MachORelocationElement containingElement, MachOSection relocatedSection, int offset, String symbolName, long addend) {
        int length = 4; // This relocation record holds the addend for a 4-byte AArch64 instruction
        return new MachORelocationInfo(containingElement, relocatedSection, offset, length, ARM64Reloc.ADDEND, symbolName, false, Math.toIntExact(addend));
    }

    static MachORelocationInfo createRelocation(MachORelocationElement containingElement, MachOSection relocatedSection, int offset, RelocationKind kind, String symbolName) {
        int length = ObjectFile.RelocationKind.getRelocationSize(kind);
        MachORelocationType type = getMachORelocationType(relocatedSection, kind);
        return new MachORelocationInfo(containingElement, relocatedSection, offset, length, type, symbolName, false, 0);

    }

    public static int getEncodedSize() {
        return 8;
    }

    public void write(OutputAssembler oa, @SuppressWarnings("unused") Map<Element, LayoutDecisionMap> alreadyDecided) {
        /* We need to convert in-section offsets to vaddrs if we are writing dynamic object. */
        // "extern" means symbolNum is a symbol not a section number
        int symbolNum;
        if (isAddendKind()) {
            assert !isExtern() : "addend must be encoded as a local";
            GraalError.guarantee(NumUtil.isSignedNbit(24, addend), "Addend has to be 24bit signed number. Got value 0x%x", addend);
            // store addend as symbolnum
            symbolNum = addend;
        } else if (isExtern()) {
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
        remainingWord |= symbolNum & 0x00ffffff;
        remainingWord |= (kind.isPCRelative() ? 1 : 0) << 24;
        remainingWord |= (log2length & 0x3) << 25;
        remainingWord |= (isExtern() ? 1 : 0) << 27;
        remainingWord |= (kind.getValue() & 0xf) << 28;
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

    public boolean isAddendKind() {
        return kind == ARM64Reloc.ADDEND;
    }

    public long getAddend() {
        return addend;
    }

    public MachOSection getRelocatedSection() {
        return relocatedSection;
    }

    private boolean isExtern() {
        /*
         * We record localness by grabbing the target section (see constructor). Note that the
         * addend kind is not considered an extern.
         */

        return targetSection == null && !isAddendKind();
    }

    private static MachORelocationType getMachORelocationType(MachOSection relocatedSection, RelocationKind kind) {
        switch (relocatedSection.getOwner().cpuType) {
            case X86_64:
                switch (kind) {
                    case DIRECT_1:
                    case DIRECT_2:
                    case DIRECT_4:
                    case DIRECT_8:
                        return X86_64Reloc.UNSIGNED;
                    case PC_RELATIVE_1:
                    case PC_RELATIVE_2:
                    case PC_RELATIVE_4:
                    case PC_RELATIVE_8:
                        return X86_64Reloc.SIGNED;
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
                        return ARM64Reloc.UNSIGNED;
                    case AARCH64_R_AARCH64_ADR_PREL_PG_HI21:
                        return ARM64Reloc.PAGE21;
                    case AARCH64_R_AARCH64_LDST64_ABS_LO12_NC:
                    case AARCH64_R_AARCH64_LDST32_ABS_LO12_NC:
                    case AARCH64_R_AARCH64_LDST16_ABS_LO12_NC:
                    case AARCH64_R_AARCH64_LDST8_ABS_LO12_NC:
                    case AARCH64_R_AARCH64_ADD_ABS_LO12_NC:
                        return ARM64Reloc.PAGEOFF12;
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
            return sectionOffset == other.sectionOffset && log2length == other.log2length && Objects.equals(containingElement, other.containingElement) && addend == other.addend &&
                            Objects.equals(getRelocatedSection(), other.getRelocatedSection()) && kind == other.kind &&
                            Objects.equals(sym, other.sym) && Objects.equals(targetSection, other.targetSection);
        }
        return false;
    }

    @Override
    public int hashCode() {
        return Objects.hash(containingElement, relocatedSection, kind, sectionOffset, sym, targetSection, log2length, addend);
    }
}
