/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;

import com.oracle.objectfile.BuildDependency;
import com.oracle.objectfile.ElementImpl;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.ObjectFile.Element;
import com.oracle.objectfile.ObjectFile.RelocationMethod;
import com.oracle.objectfile.ObjectFile.RelocationRecord;
import com.oracle.objectfile.ObjectFile.Symbol;
import com.oracle.objectfile.io.AssemblyBuffer;
import com.oracle.objectfile.io.OutputAssembler;
import com.oracle.objectfile.pecoff.PECoff.IMAGE_RELOCATION;
import com.oracle.objectfile.pecoff.PECoffObjectFile.PECoffSection;

@SuppressWarnings("unchecked")
public class PECoffRelocationTable extends ObjectFile.Element {

    @Override
    public ElementImpl getImpl() {
        return this;
    }

    interface PECoffRelocationMethod extends RelocationMethod {

        long toLong();
    }

    static final class Entry implements RelocationRecord {
        final PECoffSection section;
        final long offset;
        final PECoffRelocationMethod t;
        final PECoffSymtab.Entry sym;
        final long addend; // we only use this if we're a rela section, i.e. if withExplicitAddends

        Entry(PECoffSection section, long offset, PECoffRelocationMethod t, PECoffSymtab.Entry sym, long addend) {
            this.section = section;
            this.offset = offset;
            this.t = t;
            this.sym = sym;
            this.addend = addend;
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

    private final PECoffSymtab syms;
    private PECoffObjectFile owner = null;

    PECoffRelocationTable(PECoffObjectFile owner, String name, PECoffSymtab syms) {
        owner.super(name, 4);

        this.owner = owner;
        this.syms = syms;
    }

    void addEntry(PECoffSection s, long offset, PECoffRelocationMethod t, PECoffSymtab.Entry sym, long addend) {
        Map<Entry, Entry> entries = (Map<Entry, Entry>) s.getRelocEntries();

        if (entries == null) {
            entries = new TreeMap<>(Comparator.comparingLong(Entry::getOffset));
            s.setRelocEntries(entries);
        }

        entries.computeIfAbsent(new Entry(s, offset, t, sym, addend), Function.identity());
    }

    // Returns count of relocation entries for section
    public int getRelocCount(int sectionIndex) {
        int entryCount = 0;
        PECoffSection s = getOwner().getSectionByIndex(sectionIndex + 1);
        Map<Entry, Entry> entries = (Map<Entry, Entry>) s.getRelocEntries();

        if (entries == null) {
            return 0;
        }

        entryCount = entries.size();

        if (entryCount > 65535) {
            entryCount++;
        }

        return entryCount;
    }

    // Returns the relative offset from the start of the relocation area
    // The first relocation entry is at offset 0.
    public int getRelocOffset(int sectionIndex) {
        int offset = 0;
        int maxSection = getOwner().getPECoffSections().size();

        for (int i = 0; i < maxSection; i++) {
            if (i == sectionIndex) {
                return offset;
            }
            offset = offset + (getRelocCount(i) * IMAGE_RELOCATION.totalsize);
        }
        return offset;
    }

    @Override
    public PECoffObjectFile getOwner() {
        return owner;
    }

    @Override
    public boolean isLoadable() {
        return true;
    }

    PECoffRelocTableStruct relocTabStruct = null;

    /**
     * This function uses the entries Set to create the native byte array that will be written out
     * to disk.
     */
    private PECoffRelocTableStruct getNativeReloctab() {
        if (relocTabStruct != null) {
            return relocTabStruct;
        }

        relocTabStruct = new PECoffRelocTableStruct(getOwner().getSections().size());

        for (PECoffSection s : getOwner().getPECoffSections()) {
            Map<Entry, Entry> entries = (Map<Entry, Entry>) s.getRelocEntries();
            if (entries != null) {
                for (Entry ent : entries.keySet()) {
                    long offset = ent.getOffset();
                    int sectionID = ent.section == null ? 0 : s.getSectionID();
                    relocTabStruct.createRelocationEntry(sectionID, (int) offset, syms.indexOf(ent.sym), (int) ent.t.toLong());
                }
            }
        }
        return relocTabStruct;
    }

    @Override
    public Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
        HashSet<BuildDependency> deps = ObjectFile.minimalDependencies(decisions, this);
        return deps;
    }

    private int getWrittenSize() {
        int size = 0;
        for (PECoffSection s : getOwner().getPECoffSections()) {
            Map<Entry, Entry> entries = (Map<Entry, Entry>) s.getRelocEntries();
            if (entries != null) {
                int esize = entries.size();
                // If there's more than 65535 entries in a section,
                // then first entry is used for the larger size
                if (esize > 65535) {
                    esize++;
                }
                esize *= IMAGE_RELOCATION.totalsize;
                size += esize;
            }
        }
        return size;
    }

    @Override
    public byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
        /* We blat out our list of relocation records. */
        PECoffRelocTableStruct rt = getNativeReloctab();
        OutputAssembler oa = AssemblyBuffer.createOutputAssembler(ByteBuffer.allocate(getWrittenSize()).order(getOwner().getByteOrder()));
        int sectionIndex = 0;
        for (PECoffSection s : getOwner().getPECoffSections()) {
            if (s.getSectionID() != sectionIndex) {
                System.out.println("Out of order PECoffSection " + s.getSectionID() + " should be " + sectionIndex);
                System.out.println(s);

            }
            sectionIndex++;
            if (s.getRelocEntries() != null) {
                oa.writeBlob(rt.getRelocData(s.getSectionID()));
            }
        }
        return oa.getBlob();
    }

    @Override
    public int getOrDecideOffset(Map<Element, LayoutDecisionMap> alreadyDecided, int offsetHint) {
        return ObjectFile.defaultGetOrDecideOffset(alreadyDecided, this, offsetHint);
    }

    @Override
    public int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
        return getWrittenSize();
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
