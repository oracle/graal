/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.Map;

import com.oracle.objectfile.BuildDependency;
import com.oracle.objectfile.ElementImpl;
import com.oracle.objectfile.LayoutDecision;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.ObjectFile.Element;
import com.oracle.objectfile.ObjectFile.RelocationKind;
import com.oracle.objectfile.ObjectFile.RelocationRecord;
import com.oracle.objectfile.ObjectFile.Segment;
import com.oracle.objectfile.ObjectFile.Symbol;
import com.oracle.objectfile.io.AssemblyBuffer;
import com.oracle.objectfile.macho.MachOObjectFile.MachOSection;
import com.oracle.objectfile.macho.MachOObjectFile.SectionFlag;
import com.oracle.objectfile.macho.MachOObjectFile.SectionType;
import com.oracle.objectfile.macho.MachOObjectFile.Segment64Command;

/**
 * @see com.oracle.objectfile.elf.ELFUserDefinedSection
 */
public class MachOUserDefinedSection extends MachOSection implements ObjectFile.RelocatableSectionImpl {

    protected ElementImpl impl;

    @Override
    public ElementImpl getImpl() {
        return impl;
    }

    MachOUserDefinedSection(MachOObjectFile owner, String name, int alignment, Segment64Command segment, SectionType type, ElementImpl impl) {
        this(owner, name, alignment, segment, type, impl, EnumSet.noneOf(SectionFlag.class));
        this.impl = impl;
    }

    MachOUserDefinedSection(MachOObjectFile owner, String name, int alignment, Segment64Command segment, SectionType type, ElementImpl impl, EnumSet<SectionFlag> flags) {
        owner.super(name, alignment, segment, type, flags);
        this.impl = impl;
    }

    public void setImpl(ElementImpl impl) {
        this.impl = impl;
    }

    @Override
    public Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
        return impl.getDependencies(decisions);
    }

    @Override
    public int getOrDecideOffset(Map<Element, LayoutDecisionMap> alreadyDecided, int offsetHint) {
        /*
         * We have to be careful about our offset.
         *
         * - If we're the first section in the next segment *after* the text segment, we're not
         * allowed to start before offset 4096. Why? because the native tools always produce objects
         * like this, and I can't get my objects to load if I deviate from this.
         *
         * - If we're the first section in the text segment, we should have offset at least 4096.
         */
        int implOffset = impl.getOrDecideOffset(alreadyDecided, offsetHint);
        Segment ourSegment = getSegment();
        Segment prevSegment = null;
        Segment firstSegment = getOwner().getSegments().iterator().next();
        for (Segment s : getOwner().getSegments()) {
            if (s == ourSegment) {
                break;
            }
            prevSegment = s;
        }
        if (getSegment().get(0) == this && prevSegment != null && prevSegment.getName().equals("__TEXT")) {
            assert prevSegment == firstSegment;
            if (implOffset < getOwner().getPageSize()) {
                // we've hit the special case -- pad to 4096 while keeping congruence
                return ObjectFile.nextIntegerMultipleWithCongruence(getOwner().getPageSize(), impl.getAlignment(), implOffset, getOwner().getPageSize());
            }
        } else if (getSegment().get(0) == this && ourSegment == firstSegment) {
            if (implOffset < getOwner().getPageSize()) {
                return getOwner().getPageSize();
            }
        }

        // else we're ordinary
        return implOffset;
    }

    @Override
    public int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
        return impl.getOrDecideSize(alreadyDecided, sizeHint);
    }

    @Override
    public byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
        return impl.getOrDecideContent(alreadyDecided, contentHint);
    }

    @Override
    public int getOrDecideVaddr(Map<Element, LayoutDecisionMap> alreadyDecided, int vaddrHint) {
        /*
         * We have to be careful about our vaddr. If we're the first section in the text segment, we
         * should have vaddr at least 4096. Why? to reproduce what the native tools do.
         */
        int implVaddr = impl.getOrDecideVaddr(alreadyDecided, vaddrHint);
        // we should already have decided our file offset
        Object offsetObj = alreadyDecided.get(this).getDecidedValue(LayoutDecision.Kind.OFFSET);
        assert offsetObj != null;
        assert offsetObj instanceof Integer;
        // test for the special case
        if (getSegment() == getOwner().getSegments().iterator().next() && getSegment().get(0) == this) {
            if (implVaddr < getOwner().getPageSize()) {
                // choose the next congruent
                return ObjectFile.nextIntegerMultipleWithCongruence(getOwner().getPageSize(), impl.getAlignment(), (int) offsetObj, getOwner().getPageSize());
            }
        }
        return implVaddr;
    }

    @Override
    public int getMemSize(Map<Element, LayoutDecisionMap> alreadyDecided) {
        return impl.getMemSize(alreadyDecided);
    }

    @Override
    public LayoutDecisionMap getDecisions(LayoutDecisionMap copyingIn) {
        return impl.getDecisions(copyingIn);
    }

    @Override
    public MachORelocationElement getOrCreateRelocationElement(boolean useImplicitAddend) {
        return getOwner().getOrCreateRelocationElement(useImplicitAddend);
    }

    @Override
    public RelocationRecord markRelocationSite(int offset, int length, ByteBuffer bb, RelocationKind k, String symbolName, boolean useImplicitAddend, Long explicitAddend) {
        MachORelocationElement el = getOrCreateRelocationElement(useImplicitAddend);
        AssemblyBuffer sbb = new AssemblyBuffer(bb);
        sbb.setByteOrder(getOwner().getByteOrder());
        sbb.pushSeek(offset);
        /*
         * NOTE: Mach-O does not support explicit addends, and inline addends are applied even
         * during dynamic linking. So if the caller supplies an explicit addend, we turn it into an
         * implicit one by updating our content.
         */
        long currentInlineAddendValue = sbb.readTruncatedLong(length);
        long desiredInlineAddendValue;
        if (explicitAddend != null) {
            /*
             * This assertion is conservatively disallowing double-addend (could
             * "add currentValue to explicitAddend"), because that seems more likely to be a bug
             * than a feature.
             */
            assert currentInlineAddendValue == 0;
            desiredInlineAddendValue = explicitAddend;
        } else {
            desiredInlineAddendValue = currentInlineAddendValue;
        }

        /*
         * One more complication: for PC-relative relocation, at least on x86-64, Mach-O linkers
         * (both AOT ld and dyld) adjust the calculation to compensate for the fact that it's the
         * *next* instruction that the PC-relative reference gets resolved against. Note that ELF
         * doesn't do this compensation. Our interface duplicates the ELF behaviour, so we have to
         * act against this Mach-O-specific fixup here, by *adding* a little to the addend. The
         * amount we add is always the length in bytes of the relocation site (since on x86-64 the
         * reference is always the last field in a PC-relative instruction).
         */
        if (k == RelocationKind.PC_RELATIVE) {
            desiredInlineAddendValue += length;
        }

        // Write the inline addend back to the buffer.
        sbb.seek(offset);
        sbb.writeTruncatedLong(desiredInlineAddendValue, length);

        // set section flag to note that we have relocations
        Symbol sym = getOwner().getSymbolTable().getSymbol(symbolName);
        boolean symbolIsDefinedLocally = (sym != null && sym.isDefined());
        // see note in MachOObjectFile's createDefinedSymbol
        boolean createAsLocalReloc = false;
        assert !createAsLocalReloc || symbolIsDefinedLocally;
        flags.add(createAsLocalReloc ? SectionFlag.LOC_RELOC : SectionFlag.EXT_RELOC);

        // return ByteBuffer cursor to where it was
        sbb.pop();
        RelocationInfo rec = new RelocationInfo(el, this, offset, length, k, symbolName, createAsLocalReloc);
        el.add(rec);

        return rec;
    }
}
