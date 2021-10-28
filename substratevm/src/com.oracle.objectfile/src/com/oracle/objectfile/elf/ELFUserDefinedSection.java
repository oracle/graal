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

package com.oracle.objectfile.elf;

import java.nio.ByteBuffer;
import java.util.EnumSet;
import java.util.Map;

import com.oracle.objectfile.BuildDependency;
import com.oracle.objectfile.ElementImpl;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.ObjectFile.Element;
import com.oracle.objectfile.elf.ELFObjectFile.ELFSection;
import com.oracle.objectfile.elf.ELFObjectFile.ELFSectionFlag;
import com.oracle.objectfile.elf.ELFObjectFile.SectionType;

/**
 * Clients defining their own kinds of section typically want to do so in a way that is agnostic
 * w.r.t. ELF, Mach-O or other object file formats. "User-defined section" means a section whose
 * implementation delegates to an ElementImpl implementation. By implementing this interface,
 * clients can breaks out of the ELF/Mach-O class hierarchy and implement common functionality (e.g.
 * DWARF debugging sections) in a format-agnostic way. ELFUserDefinedSection is the glue layer that
 * forwards calls to the ElementImpl.
 *
 * TODO: details like flags and segment membership are somewhat format-specific. These need to be
 * specifiable by the client, in a format-agnostic way.
 *
 * TODO: treatment of alignment needs to be squared with ELF-specific logic. I have removed the
 * getAlignment() / setAlignment() methods for now (even from ElementImpl).
 *
 * TODO: default implementations of many ElementImpl can be factored out of existing code in
 * ELFSection and/or ObjectFile.{Section,Element}. In particular,
 */
public class ELFUserDefinedSection extends ELFSection implements ObjectFile.RelocatableSectionImpl {

    private ELFRelocationSection rel; // the section holding our relocations without addends
    private ELFRelocationSection rela; // the section holding our relocations with addends

    protected ElementImpl impl;

    @Override
    public ElementImpl getImpl() {
        return impl;
    }

    ELFUserDefinedSection(ELFObjectFile owner, String name, int alignment, SectionType type, ElementImpl impl) {
        this(owner, name, alignment, type, impl, EnumSet.noneOf(ELFSectionFlag.class));
    }

    ELFUserDefinedSection(ELFObjectFile owner, String name, int alignment, SectionType type, ElementImpl impl, EnumSet<ELFSectionFlag> flags) {
        this(owner, name, alignment, type, impl, flags, -1);
    }

    ELFUserDefinedSection(ELFObjectFile owner, String name, int alignment, SectionType type, ElementImpl impl, EnumSet<ELFSectionFlag> flags, int sectionIndex) {
        owner.super(name, alignment, type, flags, sectionIndex);
        this.impl = impl;
    }

    public void setImpl(ElementImpl impl) {
        assert impl == null;
        this.impl = impl;
        if (impl.isLoadable()) {
            flags.add(ELFSectionFlag.ALLOC);
        } else {
            flags.remove(ELFSectionFlag.ALLOC);
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
    public Element getOrCreateRelocationElement(long addend) {
        ELFSymtab syms = (ELFSymtab) getOwner().elementForName(".symtab");
        if (syms == null) {
            throw new IllegalStateException("cannot create a relocation section without corresponding symtab");
        }

        if (ELFObjectFile.useExplicitAddend(addend)) {
            if (rela == null) {
                rela = getOwner().getOrCreateRelocSection(this, syms, true);
                assert rela != null;
            }
            return rela;
        } else {
            // use implicit addend
            if (rel == null) {
                rel = getOwner().getOrCreateRelocSection(this, syms, false);
                assert rel != null;
            }
            return rel;
        }
    }

    @Override
    public void markRelocationSite(int offset, ByteBuffer bb, ObjectFile.RelocationKind k, String symbolName, long addend) {
        ELFSymtab syms = (ELFSymtab) getOwner().elementForName(".symtab");
        ELFRelocationSection rs = (ELFRelocationSection) getOrCreateRelocationElement(addend);
        assert symbolName != null;
        ELFSymtab.Entry ent = syms.getSymbol(symbolName);
        assert ent != null;
        rs.addEntry(this, offset, ELFMachine.getRelocation(getOwner().getMachine(), k), ent, addend);
    }
}
