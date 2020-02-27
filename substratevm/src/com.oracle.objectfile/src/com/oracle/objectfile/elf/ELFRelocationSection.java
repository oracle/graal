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

package com.oracle.objectfile.elf;

import static java.lang.Math.toIntExact;

import java.nio.ByteBuffer;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;

import com.oracle.objectfile.BuildDependency;
import com.oracle.objectfile.ElementImpl;
import com.oracle.objectfile.LayoutDecision;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.ObjectFile.Element;
import com.oracle.objectfile.ObjectFile.RelocationKind;
import com.oracle.objectfile.ObjectFile.RelocationMethod;
import com.oracle.objectfile.ObjectFile.RelocationRecord;
import com.oracle.objectfile.ObjectFile.Symbol;
import com.oracle.objectfile.elf.ELFObjectFile.ELFSection;
import com.oracle.objectfile.elf.ELFObjectFile.ELFSectionFlag;
import com.oracle.objectfile.elf.ELFObjectFile.SectionType;
import com.oracle.objectfile.io.AssemblyBuffer;
import com.oracle.objectfile.io.OutputAssembler;

public class ELFRelocationSection extends ELFSection {

    @Override
    public ElementImpl getImpl() {
        return this;
    }

    /* Mirrors the on-disk representation of an ELF relocation record */
    class EntryStruct {

        long offset; // location at which the relocation should be applied
        long info; // relocation type and symbol index
        long addend; // we only use this if we're a rela section, i.e. if withExplicitAddends

        // used by getWrittenSize
        EntryStruct() {
        }

        EntryStruct(long offset, long info, long addend) {
            this.offset = offset;
            this.info = info;
            if (withExplicitAddends) {
                this.addend = addend;
            }
        }

        void write(OutputAssembler oa) {
            switch (getOwner().getFileClass()) {
                case ELFCLASS32:
                    oa.write4Byte(toIntExact(offset));
                    oa.write4Byte(toIntExact(info));
                    if (withExplicitAddends) {
                        oa.write4Byte(toIntExact(addend));
                    }
                    break;
                case ELFCLASS64:
                    oa.write8Byte(offset);
                    oa.write8Byte(info);
                    if (withExplicitAddends) {
                        oa.write8Byte(addend);
                    }
                    break;
                default:
                    throw new RuntimeException(getOwner().getFileClass().toString());
            }
        }

        int getWrittenSize() {
            ByteBuffer bb = ByteBuffer.allocate(24);
            write(AssemblyBuffer.createOutputAssembler(bb));
            return bb.position();
        }
    }

    interface ELFRelocationMethod extends RelocationMethod {

        long toLong();
    }

    private static final class Entry implements RelocationRecord {
        final ELFSection section;
        final long offset;
        final ELFRelocationMethod t;
        final ELFSymtab.Entry sym;
        final long addend; // we only use this if we're a rela section, i.e. if withExplicitAddends

        Entry(ELFSection section, long offset, ELFRelocationMethod t, ELFSymtab.Entry sym, long addend) {
            this.section = section;
            this.offset = offset;
            this.t = t;
            this.sym = sym;
            this.addend = addend;
        }

        @Override
        public RelocationKind getKind() {
            return t.getKind();
        }

        @Override
        public long getOffset() {
            return offset;
        }

        @Override
        public Symbol getReferencedSymbol() {
            return sym;
        }

        @Override
        public int getRelocatedByteSize() {
            /* All ELF relocation kinds work on a fixed size of relocation site. */
            return t.getRelocatedByteSize();
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj != null && getClass() == obj.getClass()) {
                Entry other = (Entry) obj;
                return Objects.equals(section, other.section) && offset == other.offset && Objects.equals(t, other.t) && Objects.equals(sym, other.sym) && addend == other.addend;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return (((section.hashCode() * 31 + Long.hashCode(offset)) * 31 + t.hashCode()) * 31 + sym.hashCode()) * 31 + Long.hashCode(addend);
        }
    }

    private final boolean withExplicitAddends;
    private final ELFSection relocated;
    private final ELFSymtab syms;
    private final Map<Entry, Entry> entries = new TreeMap<>(Comparator.comparingLong(Entry::getOffset));

    ELFRelocationSection(ELFObjectFile owner, String name, ELFSection relocated, ELFSymtab syms, boolean withExplicitAddends) {
        owner.super(name, owner.getWordSizeInBytes(), withExplicitAddends ? SectionType.RELA : SectionType.REL, EnumSet.noneOf(ELFSectionFlag.class), -1);
        this.withExplicitAddends = withExplicitAddends;
        this.syms = syms;
        this.relocated = relocated; // may be null for rel(a?).dyn
        if (relocated == null) {
            assert syms == null || syms.isDynamic();
            flags.add(ELFSectionFlag.ALLOC); // rel.dyn sections are allocated
        } else {
            assert name.equals((withExplicitAddends ? ".rela" : ".rel") + relocated.getName());
        }
    }

    public Entry addEntry(ELFSection s, long offset, ELFRelocationMethod t, ELFSymtab.Entry sym, Long explicitAddend) {
        if (explicitAddend != null) {
            if (!t.canUseExplicitAddend()) {
                throw new IllegalArgumentException("cannot use relocation method " + t + " with explicit addends");
            }
            if (!withExplicitAddends) {
                throw new IllegalStateException("cannot create relocation with addend in .rel section");
            }
        } else {
            if (!t.canUseImplicitAddend()) {
                throw new IllegalArgumentException("cannot use relocation method " + t + " with implicit addends");
            }
            if (withExplicitAddends) {
                throw new IllegalStateException("cannot create relocation without addend in .rela section");
            }
        }
        long addend = (explicitAddend != null) ? explicitAddend : 0L;
        return entries.computeIfAbsent(new Entry(s, offset, t, sym, addend), Function.identity());
    }

    public boolean isDynamic() {
        return syms.isDynamic();
    }

    @Override
    public ELFSection getLinkedSection() {
        return syms; // as per ELF spec
    }

    @Override
    public long getLinkedInfo() {
        return (relocated == null) ? 0 : getOwner().getIndexForSection(relocated); // ELF spec'd
    }

    @Override
    public int getEntrySize() {
        return (new EntryStruct()).getWrittenSize();
    }

    @Override
    public Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
        /* We use minimal deps because our size doesn't depend on our bytewise content. */
        HashSet<BuildDependency> deps = ObjectFile.minimalDependencies(decisions, this);
        /*
         * Our content depends on the content of our symtab. WHY? it's only the abstract content,
         * not the physical content. Try removing this one.
         */
        LayoutDecision ourContent = decisions.get(this).getDecision(LayoutDecision.Kind.CONTENT);
        // LayoutDecision symtabContent =
        // decisions.get(syms).getDecision(LayoutProperty.Kind.CONTENT);
        // deps.add(BuildDependency.createOrGet(ourContent, symtabContent));
        /* If we're dynamic, it also depends on the vaddr of all referenced sections. */

        // our content depends on the content of the section being relocated
        // (because entries only get registered during generation)
        if (relocated != null) {
            LayoutDecision relocatedSectionContent = decisions.get(relocated).getDecision(LayoutDecision.Kind.CONTENT);
            deps.add(BuildDependency.createOrGet(ourContent,
                            relocatedSectionContent));
        }

        if (isDynamic()) {
            Set<ELFSection> referenced = new HashSet<>();
            for (Entry ent : entries.keySet()) {
                referenced.add(ent.section);
            }
            for (ELFSection es : referenced) {
                deps.add(BuildDependency.createOrGet(ourContent, decisions.get(es).getDecision(LayoutDecision.Kind.VADDR)));
            }
        }

        return deps;
    }

    @Override
    public byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
        /* We blat out our list of relocation records. */
        OutputAssembler oa = AssemblyBuffer.createOutputAssembler(ByteBuffer.allocate(entries.size() * new EntryStruct().getWrittenSize()).order(getOwner().getByteOrder()));
        for (Entry ent : entries.keySet()) {
            long offset = !isDynamic() ? ent.offset : (int) alreadyDecided.get(ent.section).getDecidedValue(LayoutDecision.Kind.VADDR) + ent.offset;
            long info;
            int symIndex = syms.indexOf(ent.sym);
            assert symIndex >= 0 : "symbol not found";
            switch (getOwner().getFileClass()) {
                case ELFCLASS32:
                    info = ((symIndex << 8) & 0xffffffffL) + (ent.t.toLong() & 0xffL);
                    break;
                case ELFCLASS64:
                    info = (((long) symIndex) << 32) + (ent.t.toLong() & 0xffffffffL);
                    break;
                default:
                    throw new RuntimeException(getOwner().getFileClass().toString());
            }
            long addend = ent.addend;
            new EntryStruct(offset, info, addend).write(oa);
        }
        return oa.getBlob();
    }

    @Override
    public int getOrDecideOffset(Map<Element, LayoutDecisionMap> alreadyDecided, int offsetHint) {
        return ObjectFile.defaultGetOrDecideOffset(alreadyDecided, this, offsetHint);
    }

    @Override
    public int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
        return entries.size() * (new EntryStruct().getWrittenSize());
    }

    @Override
    public int getOrDecideVaddr(Map<Element, LayoutDecisionMap> alreadyDecided, int vaddrHint) {
        return ObjectFile.defaultGetOrDecideVaddr(alreadyDecided, this, vaddrHint);
    }

    @Override
    public LayoutDecisionMap getDecisions(LayoutDecisionMap copyingIn) {
        return ObjectFile.defaultDecisions(this, copyingIn);
    }
}
