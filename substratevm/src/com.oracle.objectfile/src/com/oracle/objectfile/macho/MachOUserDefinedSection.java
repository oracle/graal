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
import com.oracle.objectfile.ObjectFile.Segment;
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
    public MachORelocationElement getOrCreateRelocationElement(long addend) {
        return getOwner().getOrCreateRelocationElement();
    }

    private static void handleAMD64RelocationAddend(AssemblyBuffer sbb, RelocationKind k, long addend) {
        /*
         * NOTE: x86-64 Mach-O does not support explicit addends, and inline addends are applied
         * even during dynamic linking.
         */
        int length = ObjectFile.RelocationKind.getRelocationSize(k);
        /*
         * The addend is passed as a method parameter. The initial implicit addend value within the
         * instruction does not need to be read, as it is noise.
         */
        long desiredInlineAddendValue = addend;

        /*
         * One more complication: for PC-relative relocation, at least on x86-64, Mach-O linkers
         * (both AOT ld and dyld) adjust the calculation to compensate for the fact that it's the
         * *next* instruction that the PC-relative reference gets resolved against. Note that ELF
         * doesn't do this compensation. Our interface duplicates the ELF behaviour, so we have to
         * act against this Mach-O-specific fixup here, by *adding* a little to the addend. The
         * amount we add is always the length in bytes of the relocation site (since on x86-64 the
         * reference is always the last field in a PC-relative instruction).
         */
        if (RelocationKind.isPCRelative(k)) {
            desiredInlineAddendValue += length;
        }

        // Write the inline addend back to the buffer.
        sbb.writeTruncatedLong(desiredInlineAddendValue, length);
    }

    private void handleAArch64RelocationAddend(MachORelocationElement el, AssemblyBuffer sbb, int offset, RelocationKind k, String symbolName, long addend) {
        switch (k) {
            case DIRECT_4:
            case DIRECT_8:
                sbb.writeTruncatedLong(addend, ObjectFile.RelocationKind.getRelocationSize(k));
                break;
            case AARCH64_R_AARCH64_ADR_PREL_PG_HI21:
            case AARCH64_R_AARCH64_LDST64_ABS_LO12_NC:
            case AARCH64_R_AARCH64_LDST32_ABS_LO12_NC:
            case AARCH64_R_AARCH64_LDST16_ABS_LO12_NC:
            case AARCH64_R_AARCH64_LDST8_ABS_LO12_NC:
            case AARCH64_R_AARCH64_ADD_ABS_LO12_NC:
                if (addend != 0) {
                    /*-
                     * According to the Mach-O ld code at:
                     *
                     * https://opensource.apple.com/source/ld64/ld64-274.2/src/ld/parsers/macho_relocatable_file.cpp.auto.html
                     *
                     * These relocations should use an explicit addend relocation record (ARM64_RELOC_ADDEND) instead of an
                     * implicit addend.
                     */
                    el.add(MachORelocationInfo.createARM64RelocAddend(el, this, offset, symbolName, addend));
                }
                break;
            default:
                throw new IllegalStateException("Unexpected relocation kind");
        }
    }

    @Override
    public void markRelocationSite(int offset, ByteBuffer bb, RelocationKind k, String symbolName, long addend) {
        MachORelocationElement el = getOrCreateRelocationElement(addend);
        AssemblyBuffer sbb = new AssemblyBuffer(bb);
        sbb.setByteOrder(getOwner().getByteOrder());
        sbb.pushSeek(offset);

        switch (getOwner().cpuType) {
            case X86_64:
                handleAMD64RelocationAddend(sbb, k, addend);
                break;
            case ARM64:
                handleAArch64RelocationAddend(el, sbb, offset, k, symbolName, addend);
                break;
            default:
                throw new IllegalStateException("Unexpected CPU Type");
        }

        /*
         * Set section flag to note that we have relocations. For now, we are always using external
         * relocations.
         */
        assert symbolName != null;
        flags.add(SectionFlag.EXT_RELOC);

        // return ByteBuffer cursor to where it was
        sbb.pop();
        el.add(MachORelocationInfo.createRelocation(el, this, offset, k, symbolName));
    }
}
