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

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import com.oracle.objectfile.BuildDependency;
import com.oracle.objectfile.ElementImpl;
import com.oracle.objectfile.LayoutDecision;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.ObjectFile.Element;
import com.oracle.objectfile.ObjectFile.Section;
import com.oracle.objectfile.ObjectFile.Symbol;
import com.oracle.objectfile.ObjectFile.ValueEnum;
import com.oracle.objectfile.StringTable;
import com.oracle.objectfile.SymbolTable;
import com.oracle.objectfile.io.AssemblyBuffer;
import com.oracle.objectfile.io.OutputAssembler;
import com.oracle.objectfile.macho.MachOObjectFile.LinkEditSegment64Command;
import com.oracle.objectfile.macho.MachOObjectFile.MachOSection;
import com.oracle.objectfile.macho.MachOObjectFile.Segment64Command;

public final class MachOSymtab extends MachOObjectFile.LinkEditElement implements SymbolTable {

    /*
     * Mach-O symbol tables are not sections! They are opaque data inside some segment (__LINKEDIT
     * in a shared library, the anonymous segment in a relocatable file). They have no name. The
     * LC_SYMTAB command points to them.
     *
     * (The same goes for strtabs!)
     */

    final MachOStrtab strtab;

    private boolean isSorted = false;
    private final ArrayList<Entry> entries = new ArrayList<>();

    private static int compareEntries(Entry a, Entry b) {
        /*
         * Sort entries so that they are grouped into the following categories, in that order, and
         * sorted by name within their category: locals - defined externals - undefined externals.
         */
        int cmp = Boolean.compare(a.isLocal(), b.isLocal());
        if (cmp == 0) {
            cmp = Boolean.compare(a.isDefined() && a.isExternal(), b.isDefined() && b.isExternal());
        }
        if (cmp == 0) {
            cmp = Boolean.compare(!a.isDefined() && a.isExternal(), !b.isDefined() && b.isExternal());
        }
        if (cmp == 0) {
            cmp = a.getName().compareTo(b.getName());
        }
        return cmp;
    }

    private final HashMap<String, Entry> entriesByName = new HashMap<>();

    public MachOSymtab(String name, MachOObjectFile objectFile, Segment64Command containingSegment, MachOStrtab strtab) {
        objectFile.super(name, containingSegment, objectFile.getWordSizeInBytes());
        this.strtab = strtab;
        strtab.setContentProvider(() -> getSortedEntries().stream().map(Entry::getNameInObject).iterator());
    }

    public List<Entry> getSortedEntries() {
        if (!isSorted) {
            entries.sort(MachOSymtab::compareEntries);
            isSorted = true;
        }
        return entries;
    }

    private List<Entry> getModifiableEntries() {
        if (isSorted) {
            throw new RuntimeException("unexpected access to unsorted symtab entries");
        }
        return entries;
    }

    enum ReferenceType {
        REFERENCE_FLAG_UNDEFINED_NON_LAZY(0),
        REFERENCE_FLAG_UNDEFINED_LAZY(1),
        REFERENCE_FLAG_DEFINED(2),
        REFERENCE_FLAG_PRIVATE_DEFINED(3),
        REFERENCE_FLAG_PRIVATE_UNDEFINED_NON_LAZY(4),
        REFERENCE_FLAG_PRIVATE_UNDEFINED_LAZY(5);

        private final int value;

        ReferenceType(int value) {
            this.value = value;
        }

        int value() {
            return value;
        }
    }

    enum SymbolType {
        UNDF(0x0),
        ABS(0x2),
        SECT(0xe),
        PBUD(0xc),
        INDR(0xa);

        private final int value;

        SymbolType(int value) {
            this.value = value;
        }

        int value() {
            return value;
        }
    }

    enum DescFlag implements ValueEnum {
        REFERENCED_DYNAMICALLY(0x10),
        N_DESC_DISCARDED(0x20),
        N_WEAK_REF(0x40),
        N_WEAK_DEF(0x80);

        private final int value;

        DescFlag(int value) {
            this.value = value;
        }

        @Override
        public long value() {
            return value;
        }
    }

    public static final class Entry implements Symbol {
        /*
         * In Mach-O-speak, this class is modelling an 'nlist64', but I couldn't bring myself to
         * call it that.
         */

        private final boolean isCode;
        private final String name;
        private final MachOSection section; // use null for NO_SECT

        // these three fields model the content of 'n_type' (we ignore STABS stuff)
        private final boolean privateExtern;
        private final boolean extern;
        private final SymbolType type;

        // these two fields model the content of 'n_desc'
        private final ReferenceType refType;
        private final EnumSet<DescFlag> descFlags;

        // the symbol value
        private final long value;

        /**
         * Constructs an undefined symbol table entry.
         *
         * @param name the symbol name
         * @param isCode whether the symbol is expected to mark a code location
         */
        Entry(String name, boolean isCode) {
            this(name, null, false, /* isExtern */true, SymbolType.UNDF, ReferenceType.REFERENCE_FLAG_UNDEFINED_LAZY, EnumSet.noneOf(DescFlag.class), 0, isCode);
        }

        /**
         * Constructs a defined symbol table entry.
         *
         * @param isGlobal whether the symbol should be visible outside the defining object
         * @param isCode whether the symbol marks a code location
         */
        Entry(String name, Section referencedSection, long referencedOffset, boolean isGlobal, boolean isCode) {
            this(name, (MachOSection) referencedSection, false, isGlobal, SymbolType.SECT, ReferenceType.REFERENCE_FLAG_DEFINED, EnumSet.noneOf(DescFlag.class), (int) referencedOffset, isCode);
        }

        // private constructor initializing all fields and adding to the entries list
        private Entry(String name, MachOSection section, boolean privateExtern, boolean extern, SymbolType type, ReferenceType refType, EnumSet<DescFlag> descFlags, long value, boolean isCode) {
            this.name = name;
            this.section = section;
            this.privateExtern = privateExtern;
            this.extern = extern;
            this.type = type;
            this.refType = refType;
            this.descFlags = descFlags;
            this.value = value;
            this.isCode = isCode;
        }

        boolean isExternal() {
            return privateExtern || extern;
        }

        @Override
        public long getDefinedAbsoluteValue() {
            assert type == SymbolType.ABS;
            return value;
        }

        @Override
        public long getDefinedOffset() {
            assert type == SymbolType.SECT;
            return value;
        }

        @Override
        public Section getDefinedSection() {
            return section;
        }

        public String getNameInObject() {
            /* On Mach-O symbols are prefixed with underscore */
            return "_" + name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public long getSize() {
            return 0; // Mach-O appears to have no concept of symbol sizes
        }

        @Override
        public boolean isAbsolute() {
            return type == SymbolType.ABS;
        }

        @Override
        public boolean isCommon() {
            return type == SymbolType.UNDF && extern && value != 0;
        }

        @Override
        public boolean isDefined() {
            return type == SymbolType.SECT ||
                            type == SymbolType.ABS /* || type == SymbolType.INDR */;
        }

        public boolean isLocal() {
            return !extern && !privateExtern;
        }

        @Override
        public boolean isGlobal() {
            return extern;
        }

        @Override
        public boolean isFunction() {
            return isCode;
        }

        @Override
        public String toString() {
            return "symbol '" + name + "', value " + value;
        }
    }

    static class EntryStruct {

        int strx;
        byte type;
        byte sect;
        short desc;
        long value;

        static final short NO_SECT = 0;
        static final short MAX_SECT = 255;

        static final byte N_UNDF = 0x0;
        static final byte N_ABS = 0x2;
        static final byte N_SECT = 0xe;
        static final byte N_PBUD = 0xc;
        static final byte N_INDR = 0xa;

        static final byte N_STAB = (byte) 0xe0;
        static final byte N_PEXT = 0x10;
        static final byte N_TYPE = 0x0e;
        static final byte N_EXT = 0x01;

        void write(OutputAssembler oa) {
            oa.write4Byte(strx);
            oa.writeByte(type);
            oa.writeByte(sect);
            oa.write2Byte(desc);
            oa.write8Byte(value);
        }

        static int getWrittenSize() {
            return 16;
        }
    }

    @Override
    public Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
        HashSet<BuildDependency> deps = ObjectFile.minimalDependencies(decisions, this);
        // our content depends on strtab content
        LayoutDecision ourContent = decisions.get(this).getDecision(LayoutDecision.Kind.CONTENT);
        LayoutDecision strtabContent = decisions.get(strtab).getDecision(LayoutDecision.Kind.CONTENT);
        deps.add(BuildDependency.createOrGet(ourContent, strtabContent));
        /*
         * We also depend on the vaddr of any referenced defined symbol. It doesn't matter whether
         * we're dynamic! Every Mach-O section has a vaddr, even in a relocatable file.
         */
        for (Entry e : entries) {
            Section s = e.getDefinedSection();
            if (s != null) {
                deps.add(BuildDependency.createOrGet(ourContent, decisions.get(s).getDecision(LayoutDecision.Kind.VADDR)));
            }
        }
        return deps;
    }

    @Override
    public int getOrDecideOffset(Map<Element, LayoutDecisionMap> alreadyDecided, int offsetHint) {
        return ObjectFile.defaultGetOrDecideOffset(alreadyDecided, this, offsetHint);
    }

    private int getWrittenSize() {
        return getEntryCount() * EntryStruct.getWrittenSize();
    }

    @Override
    public int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
        return getWrittenSize();
    }

    private int firstIndexMatching(Predicate<Entry> p) {
        int i = 0;
        for (Entry e : getSortedEntries()) {
            if (p.test(e)) {
                return i;
            }
            i++;
        }
        return -1;
    }

    private int firstIndexMatchingOrZero(Predicate<Entry> p) {
        int firstIndex = firstIndexMatching(p);
        return (firstIndex != -1) ? firstIndex : 0;
    }

    private int nContiguousMatching(Predicate<Entry> p) {
        int n = 0;
        for (Entry e : getSortedEntries()) {
            if (p.test(e)) {
                n++;
            } else if (n != 0) {
                return n;
            }
        }
        return n;
    }

    int firstLocal() {
        return firstIndexMatchingOrZero(Entry::isLocal);
    }

    int nLocals() {
        return nContiguousMatching(Entry::isLocal);
    }

    int firstExtDef() {
        return firstIndexMatchingOrZero(e -> e.isExternal() && e.isDefined());
    }

    int nExtDef() {
        return nContiguousMatching(e -> e.isExternal() && e.isDefined());
    }

    int firstUndef() {
        return firstIndexMatchingOrZero(e -> !e.isDefined());
    }

    int nUndef() {
        return nContiguousMatching(e -> !e.isDefined());
    }

    @SuppressWarnings({"unused", "static-method"})
    private boolean isDynamic() {
        /*
         * FIXME: this method exists to allow the Dysymtab to identify a *subset* of symbols that
         * are dynamic. Then we need to do this test per-symbol (in getOrDecideContent) and this
         * method will go away. Currently it's unimplemented. Note that getOwner().hasVaddrSpace()
         * is probably not the right test, since even Mach-O relocatable files have a vaddrspace
         * (although in yet another dubious state of affairs, hasVaddrSpace() returns false for
         * relocatable files at present -- as if they were ELF relocatable files, which don't use
         * the vaddr space).
         */
        return true;
    }

    @Override
    public byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
        OutputAssembler oa = AssemblyBuffer.createOutputAssembler(getOwner().getByteOrder());
        byte[] strtabContent = (byte[]) alreadyDecided.get(strtab).getDecidedValue(LayoutDecision.Kind.CONTENT);
        StringTable t = new StringTable(strtabContent);
        EntryStruct s = new EntryStruct();

        for (Entry e : getSortedEntries()) {
            s.strx = t.indexFor(e.getNameInObject());
            assert s.strx != -1;
            s.type = (byte) (e.type.value() | (e.privateExtern ? EntryStruct.N_PEXT : 0) | (e.extern ? EntryStruct.N_EXT : 0));
            // NOTE: Mach-O section numbers are 1-based
            int sectionIndex = (e.section == null) ? 0 : getOwner().getSections().indexOf(e.section) + 1;
            assert !e.isDefined() || sectionIndex != -1;
            s.sect = (byte) (e.isDefined() ? sectionIndex : EntryStruct.NO_SECT);
            s.desc = (short) (ObjectFile.flagSetAsLong(e.descFlags) | e.refType.value());
            /*
             * If we're a defined non-absolute symbol, we need to make this the virtual address
             * (even for relocatable files!), so add the vaddr of the section. Absolute symbols are
             * denoted by sectionIndex == 0.
             */
            int valueToAdd = (sectionIndex == 0) ? 0 : (int) alreadyDecided.get(e.section).getDecidedValue(LayoutDecision.Kind.VADDR);
            s.value = e.value + valueToAdd;
            s.write(oa);
        }
        assert oa.pos() == getWrittenSize();
        return oa.getBlob();
    }

    @Override
    public int getOrDecideVaddr(Map<Element, LayoutDecisionMap> alreadyDecided, int vaddrHint) {
        return ObjectFile.defaultGetOrDecideVaddr(alreadyDecided, this, vaddrHint);
    }

    @Override
    public LayoutDecisionMap getDecisions(LayoutDecisionMap copyingIn) {
        return ObjectFile.defaultDecisions(this, copyingIn);
    }

    @Override
    public Symbol newDefinedEntry(String name, Section referencedSection, long referencedOffset, long size, boolean isGlobal, boolean isCode) {
        return addEntry(new Entry(name, referencedSection, referencedOffset, isGlobal, isCode));
    }

    @Override
    public Symbol newUndefinedEntry(String name, boolean isCode) {
        return addEntry(new Entry(name, isCode));
    }

    private Entry addEntry(Entry entry) {
        getModifiableEntries().add(entry);
        entriesByName.put(entry.getName(), entry);
        return entry;
    }

    @Override
    public Symbol getSymbol(String name) {
        return entriesByName.get(name);
    }

    @Override
    public ElementImpl getImpl() {
        return this;
    }

    @Override
    public boolean isLoadable() {
        /*
         * HACK: We're loadable iff we're in a LinkEditSegment64Command. If we're in a regular
         * segment, we're a plain old static symtab. FIXME: record this by some nicer means.
         */
        return segment instanceof LinkEditSegment64Command;
    }

    public int indexOf(Symbol sym) {
        int offset = Collections.binarySearch(getSortedEntries(), (Entry) sym, MachOSymtab::compareEntries);
        return offset < 0 ? -1 : offset;
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Iterator<Symbol> iterator() {
        return (Iterator) getSortedEntries().iterator();
    }

    public int getEntryCount() {
        return entries.size();
    }
}
