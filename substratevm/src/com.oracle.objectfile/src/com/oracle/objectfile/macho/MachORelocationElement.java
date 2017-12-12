/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.objectfile.macho;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import com.oracle.objectfile.BuildDependency;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.ObjectFile.Element;
import com.oracle.objectfile.ObjectFile.RelocationKind;
import com.oracle.objectfile.ObjectFile.RelocationMethod;
import com.oracle.objectfile.ObjectFile.RelocationRecord;
import com.oracle.objectfile.ObjectFile.Section;
import com.oracle.objectfile.ObjectFile.Symbol;
import com.oracle.objectfile.SymbolTable;
import com.oracle.objectfile.io.AssemblyBuffer;
import com.oracle.objectfile.io.OutputAssembler;
import com.oracle.objectfile.macho.MachOObjectFile.MachOSection;
import com.oracle.objectfile.macho.MachOObjectFile.Segment64Command;

class MachORelocationElement extends MachOObjectFile.LinkEditElement {

    /*
     * We are a bunch of RelocationInfo.Structs grouped by relocated section. We choose an arbitrary
     * order for the sections.
     */
    private ArrayList<RelocationInfo> infoList = new ArrayList<>();
    Set<MachOSection> relocatedSections = new HashSet<>();
    Segment64Command containingSegment;

    MachORelocationElement(Segment64Command segment) {
        segment.getOwner().super("MachORelocationElement", segment);
        this.containingSegment = segment;
        assert segment.getOwner().relocs == null;
        segment.getOwner().relocs = this;
    }

    public void add(RelocationInfo rec) {
        infoList.add(rec);
        relocatedSections.add(rec.relocatedSection);
    }

    public boolean relocatesSection(MachOSection s) {
        return relocatedSections.contains(s);
    }

    public boolean relocatesSegment(Segment64Command seg) {
        for (Element e : seg.elementsInSegment) {
            if (!(e instanceof MachOSection)) {
                continue;
            }
            MachOSection sect = (MachOSection) e;
            if (relocatedSections.contains(sect)) {
                return true;
            }
        }
        return false;
    }

    private void ensureSorted() {
        /*
         * We generally want to sort the infolist by section...
         */
        Collections.sort(infoList, new Comparator<RelocationInfo>() {

            @Override
            public int compare(RelocationInfo arg0, RelocationInfo arg1) {
                return Integer.compare(System.identityHashCode(arg0.relocatedSection), System.identityHashCode(arg1.relocatedSection));
            }
        });

    }

    @Override
    public byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
        OutputAssembler out = AssemblyBuffer.createOutputAssembler(getOwner().getByteOrder());
        ensureSorted();
        // blat out the records
        for (RelocationInfo rec : infoList) {
            rec.write(out, alreadyDecided);
        }
        // check we wrote the amount of stuff we were expecting
        assert getOrDecideSize(alreadyDecided, -1) == out.pos();
        return out.getBlob();
    }

    @Override
    public int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
        return infoList.size() * (new RelocationInfo.Struct()).getWrittenSize();
    }

    @Override
    public Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
        HashSet<BuildDependency> deps = ObjectFile.minimalDependencies(decisions, this);
        return deps;
    }

    public int startIndexFor(MachOSection s) {
        ensureSorted();
        for (int i = 0; i < infoList.size(); ++i) {
            RelocationInfo info = infoList.get(i);
            if (info.getRelocatedSection() == s) {
                return i;
            }
        }
        return -1;
    }

    public int encodedEntrySize() {
        return (new RelocationInfo.Struct()).getWrittenSize();
    }

    public int countFor(MachOSection s) {
        int count = 0;
        for (int i = 0; i < infoList.size(); ++i) {
            RelocationInfo info = infoList.get(i);
            if (info.getRelocatedSection() == s) {
                ++count;
            }
        }
        return count;
    }
}

enum GenericReloc {
    /*
     * These are defined as an enum in /usr/include/mach-o/reloc.h, which we reproduce. Of course,
     * take care to preserve the order!
     */
    VANILLA,
    PAIR,
    SECTDIFF,
    PB_LA_PTR,
    LOCAL_SECTDIFF;

    public int getValue() {
        return ordinal();
    }
}

enum X86_64Reloc {
    /*
     * These are defined as an enum in /usr/include/mach-o/x86-64/reloc.h, which we reproduce. Of
     * course, take care to preserve the order!
     */
    UNSIGNED,
    SIGNED,
    BRANCH,
    GOT_LOAD,
    GOT,
    SUBTRACTOR,
    SIGNED_1,
    SIGNED_2,
    SIGNED_4,
    TLV;

    public int getValue() {
        return ordinal();
    }
}

class RelocationInfo implements RelocationRecord, RelocationMethod {

    MachORelocationElement e;
    MachOSection relocatedSection;
    RelocationKind k;
    int sectionOffset;
    Symbol sym;
    MachOSection targetSection;
    byte log2length;

    boolean isExtern() {
        // we record localness by grabbing the target section (see constructor)
        return targetSection == null;
    }

    int getMachORelocationType() {
        switch (relocatedSection.getOwner().cpuType) {
            case X86_64:
                switch (k) {
                    case DIRECT:
                        return X86_64Reloc.UNSIGNED.getValue();
                    case PC_RELATIVE:
                        return X86_64Reloc.SIGNED.getValue();
                    case PROGRAM_BASE:
                        throw new IllegalArgumentException("Mach-O does not support PROGRAM_BASE relocations");
                    default:
                    case UNKNOWN:
                        throw new IllegalArgumentException("unknown relocation kind: " + k);
                }
            default:
                throw new IllegalArgumentException("unknown relocation kind: " + k);
        }
    }

    static class Struct {

        int address;   // 32 bits
        int symbolNum; // 24 bits only
        boolean pcRel; // one bit
        byte log2length;   // two bits
        boolean extern; // one bit
        int relocationType; // four bits

        int getWrittenSize() {
            return 8; // NOTE that we will double-check this against what we actually write
        }

        Struct() {
        }

        Struct(int address, int symbolNum, boolean pcRel, byte log2length, boolean extern, int type) {
            this.address = address;
            this.symbolNum = symbolNum;
            if (log2length < 0 || log2length >= 4) {
                throw new IllegalArgumentException("length must be in {1,2,4,8} bytes, so log2length must be in [0,3]");
            }
            this.pcRel = pcRel;
            this.log2length = log2length;
            this.extern = extern;
            this.relocationType = type;
        }

        void write(OutputAssembler oa) {
            int startPos = oa.pos();
            oa.write4Byte(address);
            /*
             * The Mach-O documentation just gives us a struct with bitfields. This doesn't give us
             * enough information, because the C compiler can define bitfield layout how it pleases.
             * Some Googling reveals that the "usual" way is endianness-dependent: on a big-endian
             * target, the "first" bitfield gets the most significant bits, whereas on a
             * little-endian target, the first bitfield gets the least significant bits. The net
             * result is that the bits are issued "in order" in both cases. So we can code this
             * fairly sanely without case-splitting for endianness.
             */
            int remainingWord = 0;
            //@formatter:off
                remainingWord |= symbolNum              & 0x00ffffff;      // "first" three bytes
                remainingWord |= pcRel                  ?    1 << 24  : 0; // next one bit
                remainingWord |= (log2length & 3)              << 25;      // next two bits
                remainingWord |= extern                 ?    1 << 27  : 0; // next one bit
                remainingWord |= ((long) relocationType & 0xf) << 28;      // next four bits
                //@formatter:on

            oa.write4Byte(remainingWord);
            assert oa.pos() - startPos == getWrittenSize(); // check we wrote how much we expected
        }
    }

    /**
     * Construct a relocation record.
     *
     * @param e the containing relocation element
     * @param s the section being relocated
     * @param offset the offset, within the relocated section, of the relocation site
     * @param requestedLength the length of the relocation site
     * @param k the kind of relocation to perform at the relocation site
     * @param symbolIsDynamic whether the symbol against which to relocate is dynamically-linked
     * @param symbolName the symbol against which to relocate
     */
    RelocationInfo(MachORelocationElement e, MachOSection s, int offset, int requestedLength, RelocationKind k, boolean symbolIsDynamic, String symbolName, boolean asLocalReloc) {
        this.e = e;
        relocatedSection = s;
        this.k = k;
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
        SymbolTable symtab = s.getOwner().getSymbolTable();
        // FIXME: also allow section numbers here, for non-extern symbols
        // FIXME: encode R_ABS symbol number
        this.sym = symtab.symbolsWithName(symbolName).get(0); // FIXME: better handling of ambiguity
        // if the symbol is defined in the same file, i.e. locally, we have a target section
        if (asLocalReloc) {
            assert this.sym.isDefined();
            this.targetSection = (MachOSection) sym.getDefinedSection();
        }
    }

    public void write(OutputAssembler out, @SuppressWarnings("unused") Map<Element, LayoutDecisionMap> alreadyDecided) {
        /*
         * We need to convert in-section offsets to vaddrs, if we are writing dynamic object.
         */
        // "extern" means symbolNum is a symbol not a section number
        int symbolNum;
        if (isExtern()) {
            // we're non-local, so use a symbol
            symbolNum = relocatedSection.getOwner().getSymbolTable().indexOf(sym);
        } else {
            // we're local, so use the section
            // symbolNum = relocatedSection.getOwner().getSymbolTable(isDynamic()).indexOf(sym);
            symbolNum = relocatedSection.getOwner().getSections().indexOf(sym.getDefinedSection());
            /*
             * HACK: in the case of relocating against a local symbol, we can only reference its
             * section, so we insist that its offset from the section base is zero. We should catch
             * this earlier, when the relocation is created. (You're supposed to use the addend to
             * encode the offset in this case, apparently.)
             */
            assert sym.getDefinedOffset() == 0;
        }
        boolean pcRel = k == RelocationKind.PC_RELATIVE;
        (new Struct(sectionOffset, symbolNum, pcRel, log2length, this.isExtern(), getMachORelocationType())).write(out);
    }

    @Override
    public RelocationKind getKind() {
        return k;
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

    @Override
    public int getRelocatedByteSize() {
        return 1 << log2length;
    }

    @Override
    public boolean canUseExplicitAddend() {
        return false;
    }

    @Override
    public boolean canUseImplicitAddend() {
        return true;
    }

    @Override
    public Section getRelocatedSection() {
        return relocatedSection;
    }
}
