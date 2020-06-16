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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;

import com.oracle.objectfile.BuildDependency;
import com.oracle.objectfile.ElementImpl;
import com.oracle.objectfile.LayoutDecision;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.ObjectFile.Element;
import com.oracle.objectfile.ObjectFile.Section;
import com.oracle.objectfile.ObjectFile.Symbol;
import com.oracle.objectfile.StringTable;
import com.oracle.objectfile.SymbolTable;
import com.oracle.objectfile.elf.ELFObjectFile.ELFSection;
import com.oracle.objectfile.elf.ELFObjectFile.ELFSectionFlag;
import com.oracle.objectfile.elf.ELFObjectFile.SectionType;
import com.oracle.objectfile.io.AssemblyBuffer;
import com.oracle.objectfile.io.OutputAssembler;

public class ELFSymtab extends ELFObjectFile.ELFSection implements SymbolTable {

    @Override
    public ElementImpl getImpl() {
        return this;
    }

    static final class Entry implements ObjectFile.Symbol {
        private final String name;
        private final long value;
        private final long size;
        private final SymBinding binding;
        private final SymType symType;
        private final ELFSection referencedSection;
        private final PseudoSection pseudoSection;

        @Override
        public boolean isDefined() {
            return pseudoSection == null || pseudoSection != PseudoSection.UNDEF;
        }

        @Override
        public boolean isAbsolute() {
            return pseudoSection != null && pseudoSection == PseudoSection.ABS;
        }

        @Override
        public boolean isCommon() {
            return pseudoSection != null && pseudoSection == PseudoSection.COMMON;
        }

        @Override
        public boolean isFunction() {
            return symType == SymType.FUNC;
        }

        @Override
        public boolean isGlobal() {
            return SymBinding.GLOBAL.equals(binding);
        }

        public boolean isNull() {
            return name.isEmpty() && value == 0 && size == 0 && binding == null && symType == null && referencedSection == null && pseudoSection == null;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public long getDefinedOffset() {
            if (!isDefined() || isAbsolute()) {
                throw new IllegalStateException("queried offset of an undefined or absolute symbol");
            } else {
                return value;
            }
        }

        @Override
        public Section getDefinedSection() {
            return referencedSection;
        }

        @Override
        public long getSize() {
            return size;
        }

        @Override
        public long getDefinedAbsoluteValue() {
            if (!isAbsolute()) {
                throw new IllegalStateException("queried absolute value of a non-absolute symbol");
            } else {
                return value;
            }
        }

        private Entry(String name, long value, long size, SymBinding binding, SymType type, ELFSection referencedSection, PseudoSection pseudoSection) {
            this.name = name;
            this.value = value;
            this.size = size;
            this.binding = binding;
            this.symType = type;
            this.referencedSection = referencedSection;
            this.pseudoSection = pseudoSection;
            assert ((referencedSection == null) != (pseudoSection == null)) || isNull();
        }

        // public constructor, for referencing a real section
        Entry(String name, long value, long size, SymBinding binding, SymType type, ELFSection referencedSection) {
            this(name, value, size, binding, type, referencedSection, null);
        }

        // public constructor for referencing a pseudosection
        Entry(String name, long value, long size, SymBinding binding, SymType type, PseudoSection pseudoSection) {
            this(name, value, size, binding, type, null, pseudoSection);
        }

        private Entry() {
            // represents the null entry
            this("", 0, 0, null, null, null, null);
        }
    }

    public enum SymBinding {
        LOCAL {
            @Override
            byte createVisibilityByte() {
                return 2; /* set ELF Symbol Table Entry st_other to STV_HIDDEN */
            }
        },
        GLOBAL,
        WEAK,
        LOPROC,
        HIPROC;

        byte createInfoByte(SymType type) {
            return type.createInfoByte(this);
        }

        byte createVisibilityByte() {
            return 0; /* set ELF Symbol Table Entry st_other to STV_DEFAULT */
        }
    }

    public enum SymType {
        NOTYPE,
        OBJECT,
        FUNC,
        SECTION,
        FILE,
        LOPROC,
        HIPROC;

        byte createInfoByte(SymBinding b) {
            return (byte) (this.ordinal() | (b.ordinal() << 4));
        }

    }

    public enum PseudoSection {
        ABS,
        COMMON,
        UNDEF;
    }

    // a Java transcription of the on-disk layout, used for (de)serialization
    class EntryStruct {
        int name;
        long value;
        long size;
        byte info;
        byte other;
        short shndx;

        public void write(OutputAssembler out) {
            switch (getOwner().getFileClass()) {
                case ELFCLASS32:
                    out.write4Byte(name);
                    out.write4Byte(toIntExact(value));
                    out.write4Byte(toIntExact(size));
                    out.writeByte(info);
                    out.writeByte(other);
                    out.write2Byte(shndx);
                    break;
                case ELFCLASS64:
                    out.write4Byte(name);
                    out.writeByte(info);
                    out.writeByte(other);
                    out.write2Byte(shndx);
                    out.write8Byte(value);
                    out.write8Byte(size);
                    break;
            }
        }

        public int getWrittenSize() {
            switch (getOwner().getFileClass()) {
                case ELFCLASS32:
                    return 16;
                case ELFCLASS64:
                    return 24;
            }
            throw new IllegalArgumentException();
        }
    }

    private final ELFStrtab strtab;

    /*
     * Note that we *do* represent the null entry (index 0) explicitly! This is so that indexOf()
     * and get() work as expected. However, clear() must re-create null entry.
     */

    private static int compareEntries(Entry a, Entry b) {
        int cmp = -Boolean.compare(a.isNull(), b.isNull()); // null symbol first
        if (cmp == 0) { // local symbols next
            cmp = -Boolean.compare(a.binding == SymBinding.LOCAL, b.binding == SymBinding.LOCAL);
        }
        // order does not matter from here, but try to be reproducible
        if (cmp == 0) {
            cmp = Boolean.compare(a.isDefined(), b.isDefined());
        }
        if (cmp == 0) {
            cmp = Boolean.compare(a.isAbsolute(), b.isAbsolute());
        }
        if (cmp == 0 && a.isDefined() && !a.isAbsolute()) {
            cmp = Math.toIntExact(a.getDefinedOffset() - b.getDefinedOffset());
        }
        if (cmp == 0) {
            return a.getName().compareTo(b.getName());
        }
        return cmp;
    }

    private SortedSet<Entry> entries = new TreeSet<>(ELFSymtab::compareEntries);

    private Map<String, Entry> entriesByName = new HashMap<>();

    private Map<Entry, Integer> entriesToIndex;

    private void createNullEntry() {
        assert entries.size() == 0;
        addEntry(new Entry());
    }

    @Override
    public int getEntrySize() {
        return (new EntryStruct()).getWrittenSize();
    }

    public ELFSymtab(ELFObjectFile owner, String name, boolean dynamic) {
        this(owner, name, dynamic, EnumSet.noneOf(ELFSectionFlag.class));
    }

    public ELFSymtab(ELFObjectFile owner, String name, boolean dynamic, EnumSet<ELFSectionFlag> extraFlags) {
        owner.super(name, dynamic ? ELFObjectFile.SectionType.DYNSYM : ELFObjectFile.SectionType.SYMTAB);
        createNullEntry();
        flags.add(ELFSectionFlag.ALLOC);
        flags.addAll(extraFlags);
        // NOTE: our SHT info and link entries are handled by overrides below.
        // NOTE: we create a default strtab for ourselves, but the user can replace it
        // FIXME: hmm, this is unclean, because in the case where the user replaces it,
        // a reference to this unwanted section might get into some other sections... maybe?

        if (!dynamic) {
            strtab = new DefaultStrtabImpl(owner, ".strtab");
        } else {
            strtab = new DefaultStrtabImpl(owner, ".dynstr");
            flags.add(ELFSectionFlag.ALLOC);
            strtab.flags.add(ELFSectionFlag.ALLOC);
            // the ELFDynamicSection will call setDynamic() when it's constructed
        }
    }

    class DefaultStrtabImpl extends ELFStrtab {

        DefaultStrtabImpl(ELFObjectFile owner, String name) {
            super(owner, name);
            assert owner == getOwner();
            addContentProvider(entriesByName.keySet());
        }
    }

    @Override
    public ELFSection getLinkedSection() {
        return strtab;
    }

    @Override
    public long getLinkedInfo() {
        /*
         * Info should be
         * "one greater than the symbol table index of the last local symbol (binding STB_LOCAL)."
         */
        int lastLocal = -1;
        int i = 0;
        for (Entry entry : entries) {
            if (!entry.isNull()) {
                if (entry.binding == SymBinding.LOCAL) {
                    lastLocal = i;
                } else if (lastLocal != -1) {
                    // locals are contiguous
                    break;
                }
            }
            i++;
        }
        return lastLocal + 1;
    }

    @Override
    public byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
        // our strtab's content is already decided; get the string table
        byte[] strtabContent = (byte[]) alreadyDecided.get(strtab).getDecidedValue(LayoutDecision.Kind.CONTENT);
        StringTable table = new StringTable(ByteBuffer.wrap(strtabContent).order(getOwner().getByteOrder()));
        ByteBuffer outBuffer = ByteBuffer.allocate(getWrittenSize()).order(getOwner().getByteOrder());
        OutputAssembler out = AssemblyBuffer.createOutputAssembler(outBuffer);

        for (Entry e : entries) {
            EntryStruct s = new EntryStruct();
            s.name = table.indexFor(e.name);

            if (e.name.isEmpty()) {
                /* If this is the NullEntry we are done */
                assert e.isNull();
                s.write(out);
                continue;
            }

            if (e.pseudoSection != null) {
                if (e.pseudoSection == PseudoSection.ABS) {
                    s.value = e.value;
                } else {
                    assert e.pseudoSection == PseudoSection.UNDEF;
                }
            } else {
                s.value = e.value;
                if (isDynamic()) {
                    s.value += (int) alreadyDecided.get(e.referencedSection).getDecidedValue(LayoutDecision.Kind.VADDR);
                }
            }

            s.size = e.size;
            s.info = e.binding.createInfoByte(e.symType);
            s.other = e.binding.createVisibilityByte();
            s.shndx = (short) getOwner().getIndexForSection(e.referencedSection);
            s.write(out);
        }
        return out.getBlob();
    }

    private int getWrittenSize() {
        return entries.size() * getEntrySize();
    }

    @Override
    public int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
        return getWrittenSize();
    }

    /*
     * Dependencies: it might appear we have a circular dependency here, with the string table. We
     * don't! BUT remember that it's not the abstract contents that matter; it's the physical
     * on-disk contents. In this case, the physical contents of the string table depend only on our
     * abstract contents (i.e. what names our symbols have). There is no dependency from the string
     * table's contents to our physical contents. By contrast, our physical contents *do* depend on
     * the strtab's physical contents, since we embed the strtab indices into our symbol entries.
     */

    @Override
    public Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
        ArrayList<BuildDependency> ourDeps = new ArrayList<>(ObjectFile.defaultDependencies(decisions, this));
        // we depend on the contents of our strtab
        ourDeps.add(BuildDependency.createOrGet(decisions.get(this).getDecision(LayoutDecision.Kind.CONTENT), decisions.get(strtab).getDecision(LayoutDecision.Kind.CONTENT)));
        // if we're dynamic, we also depend on vaddrs of any sections into which our symbols refer
        if (isDynamic()) {
            Set<ELFSection> referencedSections = new HashSet<>();
            for (Entry ent : entries) {
                ELFSection es = ent.referencedSection;
                if (es != null) {
                    referencedSections.add(es);
                }
            }
            for (ELFSection es : referencedSections) {
                ourDeps.add(BuildDependency.createOrGet(decisions.get(this).getDecision(LayoutDecision.Kind.CONTENT), decisions.get(es).getDecision(LayoutDecision.Kind.VADDR)));
            }
        }
        return ourDeps;
    }

    public boolean isDynamic() {
        return this.type.equals(SectionType.DYNSYM);
    }

    @Override
    public Symbol newDefinedEntry(String name, Section referencedSection, long referencedOffset, long size, boolean isGlobal, boolean isCode) {
        return addEntry(new Entry(name, referencedOffset, size, isGlobal ? SymBinding.GLOBAL : SymBinding.LOCAL, isCode ? SymType.FUNC : SymType.OBJECT, (ELFSection) referencedSection));
    }

    @Override
    public Symbol newUndefinedEntry(String name, boolean isCode) {
        return addEntry(new Entry(name, 0, 0, ELFSymtab.SymBinding.GLOBAL, isCode ? ELFSymtab.SymType.FUNC : ELFSymtab.SymType.OBJECT, PseudoSection.UNDEF));
    }

    private Entry addEntry(Entry entry) {
        if (entriesToIndex != null) {
            throw new IllegalArgumentException("Symbol table already sealed");
        }
        entries.add(entry);
        entriesByName.put(entry.getName(), entry);
        return entry;
    }

    public Entry getNullEntry() {
        return entries.iterator().next();
    }

    public int indexOf(Symbol sym) {
        if (entriesToIndex == null) {
            initializeEntriesToIndex();
        }
        Integer result = entriesToIndex.get(sym);
        if (result == null) {
            return -1;
        } else {
            return result;
        }
    }

    private void initializeEntriesToIndex() {
        entriesToIndex = new HashMap<>(entries.size());
        int index = 0;
        for (Entry entry : entries) {
            entriesToIndex.put(entry, index);
            index++;
        }
        assert entriesToIndex.size() == entries.size();
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Iterator<Symbol> iterator() {
        return (Iterator) entries.iterator();
    }

    @Override
    public Entry getSymbol(String name) {
        return entriesByName.get(name);
    }

    @Override
    public int getOrDecideOffset(Map<Element, LayoutDecisionMap> alreadyDecided, int offsetHint) {
        return ObjectFile.defaultGetOrDecideOffset(alreadyDecided, this, offsetHint);
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
