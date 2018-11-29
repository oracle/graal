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

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.Map;

import com.oracle.objectfile.BuildDependency;
import com.oracle.objectfile.ElementImpl;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.ObjectFile.Element;
import com.oracle.objectfile.ObjectFile.RelocationRecord;
import com.oracle.objectfile.io.AssemblyBuffer;
import com.oracle.objectfile.pecoff.PECoffObjectFile.PECoffSection;
import com.oracle.objectfile.pecoff.PECoffObjectFile.PECoffSectionFlag;

/**
 * Clients defining their own kinds of section typically want to do so in a way that is agnostic
 * w.r.t. PECoff, Mach-O or other object file formats. "User-defined section" means a section whose
 * implementation delegates to an ElementImpl implementation. By implementing this interface,
 * clients can breaks out of the PECoff class hierarchy and implement common functionality (e.g.
 * DWARF debugging sections) in a format-agnostic way. PECoffUserDefinedSection is the glue layer
 * that forwards calls to the ElementImpl.
 */
public class PECoffUserDefinedSection extends PECoffSection implements ObjectFile.RelocatableSectionImpl {

    private PECoffRelocationTable rel; // the section holding our relocations without addends
    private PECoffRelocationTable rela; // the section holding our relocations with addends

    protected ElementImpl impl;

    @Override
    public ElementImpl getImpl() {
        return impl;
    }

    PECoffUserDefinedSection(PECoffObjectFile owner, String name, int alignment, ElementImpl impl) {
        this(owner, name, alignment, impl, EnumSet.noneOf(PECoffSectionFlag.class));
    }

    PECoffUserDefinedSection(PECoffObjectFile owner, String name, int alignment, ElementImpl impl, EnumSet<PECoffSectionFlag> flags) {
        this(owner, name, alignment, impl, flags, -1);
    }

    PECoffUserDefinedSection(PECoffObjectFile owner, String name, int alignment, ElementImpl impl, EnumSet<PECoffSectionFlag> flags, int sectionIndex) {
        owner.super(name, alignment, flags, sectionIndex);
        this.impl = impl;
    }

    public void setImpl(ElementImpl impl) {
        assert impl == null;
        this.impl = impl;

        // Use READ to signify a loadable section
        if (impl.isLoadable()) {
            flags.add(PECoffSectionFlag.READ);
        } else {
            flags.remove(PECoffSectionFlag.READ);
        }
    }

    @Override
    public Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
        return impl.getDependencies(decisions);
    }

    @Override
    public int getOrDecideOffset(Map<Element, LayoutDecisionMap> alreadyDecided, int offsetHint) {
        return impl.getOrDecideOffset(alreadyDecided, offsetHint);
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
        return impl.getOrDecideVaddr(alreadyDecided, vaddrHint);
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
    public Element getOrCreateRelocationElement(boolean useImplicitAddend) {
        PECoffSymtab syms = (PECoffSymtab) getOwner().elementForName(".symtab");
        if (syms == null) {
            throw new IllegalStateException("cannot create a relocation section without corresponding symtab");
        }
        boolean withExplicitAddends = !useImplicitAddend;
        PECoffRelocationTable rs = withExplicitAddends ? rela : rel;
        if (rs == null) {
            // we have to create the section if it doesn't exist
            rs = getOwner().getOrCreateRelocSection(syms, withExplicitAddends);
            assert rs != null;
            if (withExplicitAddends) {
                rela = rs;
            } else {
                rel = rs;
            }
        }
        return rs;
    }

    @Override
    public RelocationRecord markRelocationSite(int offset, int length, ByteBuffer bb, ObjectFile.RelocationKind k, String symbolName, boolean useImplicitAddend, Long explicitAddend) {
        if (useImplicitAddend != (explicitAddend == null)) {
            throw new IllegalArgumentException("must have either an explicit or implicit addend");
        }
        PECoffSymtab syms = (PECoffSymtab) getOwner().elementForName(".symtab");
        PECoffRelocationTable rs = (PECoffRelocationTable) getOrCreateRelocationElement(useImplicitAddend);
        PECoffSymtab.Entry ent;
        if (symbolName != null) {
            ent = syms.getSymbol(symbolName);
        } else {
            // else we're a reloc type that doesn't need a symbol
            // assert this about the reloc type
            assert !k.usesSymbolValue();
            // use the null symtab entry
            ent = syms.getNullEntry();
            assert ent.isNull();
        }

        AssemblyBuffer sbb = new AssemblyBuffer(bb);
        sbb.setByteOrder(getOwner().getByteOrder());
        sbb.pushSeek(offset);
        /*
         * NOTE: Windows does not support explicit addends, and inline addends are applied even
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
         * One more complication: for PC-relative relocation, at least on x86-64, Coff linkers
         * adjust the calculation to compensate for the fact that it's the *next* instruction that
         * the PC-relative reference gets resolved against. Note that ELF doesn't do this
         * compensation. Our interface duplicates the ELF behaviour, so we have to act against this
         * Windows-specific fixup here, by *adding* a little to the addend. The amount we add is
         * always the length in bytes of the relocation site (since on x86-64 the reference is
         * always the last field in a PC-relative instruction).
         */
        if (k == ObjectFile.RelocationKind.PC_RELATIVE) {
            desiredInlineAddendValue += length;
        }

        // Write the inline addend back to the buffer.
        sbb.seek(offset);
        sbb.writeTruncatedLong(desiredInlineAddendValue, length);

        // return ByteBuffer cursor to where it was
        sbb.pop();

        return rs.addEntry(this, offset, PECoffMachine.getRelocation(getOwner().getMachine(), k, length), ent, explicitAddend);
    }
}
