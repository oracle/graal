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

package com.oracle.objectfile.elf;

import static java.lang.Math.toIntExact;

import java.nio.ByteBuffer;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    // the essential contents of an ELF symbol
    public class Entry implements ObjectFile.Symbol {

        final String name;
        final long value;
        final long size;
        final SymBinding binding;
        final SymType symType;
        // these guys are not final -- see getReferencedSection()
        private ELFSection referencedSection;
        private int referencedSectionIndex; // ARGH: see getReferencedSection()
        final PseudoSection pseudoSection;

        @Override
        public boolean isDefined() {
            if (pseudoSection != null) {
                assert getReferencedSection() == null;
                return pseudoSection != PseudoSection.UNDEF;
            } else {
                return true;
            }
        }

        @Override
        public boolean isAbsolute() {
            if (pseudoSection != null) {
                assert getReferencedSection() == null;
                return pseudoSection == PseudoSection.ABS;
            } else {
                return false;
            }
        }

        @Override
        public boolean isCommon() {
            if (pseudoSection != null) {
                assert getReferencedSection() == null;
                return pseudoSection == PseudoSection.COMMON;
            } else {
                return false;
            }
        }

        @Override
        public boolean isFunction() {
            return symType == SymType.FUNC;
        }

        public boolean isNull() {
            return name.equals("") && value == 0 && size == 0 && binding == null && symType == null && referencedSection == null && referencedSectionIndex == 0 && pseudoSection == null;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public long getDefinedOffset() {
            if (!isDefined() || isAbsolute()) {
                throw new IllegalStateException("queried offset of an undefined symbol");
            } else {
                return value;
            }
        }

        @Override
        public Section getDefinedSection() {
            return getReferencedSection();
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

        @Override
        public SymbolTable getOwningSymbolTable() {
            return ELFSymtab.this;
        }

        // non-public constructor setting both referencedSection and pseudoSection
        Entry(String name, long value, long size, SymBinding binding, SymType type, int referencedSectionIndex, ELFSection referencedSection, PseudoSection pseudoSection) {
            this.name = name;
            this.value = value;
            this.size = size;
            this.binding = binding;
            this.symType = type;
            this.referencedSection = referencedSection;
            this.referencedSectionIndex = referencedSectionIndex;
            this.pseudoSection = pseudoSection;

            List<Entry> entriesWithName = entriesByName.computeIfAbsent(name, k -> new ArrayList<>());
            if (!entriesWithName.isEmpty()) {
                throw new RuntimeException("Duplicate symbol with name: " + name);
            }
            entriesWithName.add(this);
            entries.add(this);
            entriesAreSorted = false;
        }

        // public constructor, for referencing a real section
        public Entry(String name, long value, long size, SymBinding binding, SymType type, ELFSection referencedSection) {
            this(name, value, size, binding, type, 0, referencedSection, null);
            assert referencedSection != null;
        }

        // public constructor, for referencing a real section that doesn't exist yet
        public Entry(String name, long value, long size, SymBinding binding, SymType type, int referencedSectionIndex) {
            this(name, value, size, binding, type, referencedSectionIndex, null, null);
            assert referencedSectionIndex != 0; // 0 is the null SHT entry, so invalid
        }

        // public constructor for referencing a pseudosection
        public Entry(String name, long value, long size, SymBinding binding, SymType type, PseudoSection pseudoSection) {
            this(name, value, size, binding, type, 0, null, pseudoSection);
            assert isNull() || pseudoSection != null;
        }

        private Entry() {
            // represents the null entry
            this("", 0, 0, null, null, (PseudoSection) null);
            assert isNull();
        }

        ELFSection getReferencedSection() {
            /*
             * GAH. When reading in a large ELF file, we may want to create references to sections
             * that don't exist yet. Our hackaround is to keep *two* references: one the index, the
             * other the object reference. To avoid repeated linear searches (noting that
             * getSectionByIndex() is currently a linear search), we switch to using the object
             * reference as soon as we successfully find it.
             */
            if (referencedSection != null) {
                assert referencedSectionIndex == 0;
                return referencedSection;
            } else if (referencedSectionIndex != 0) {
                assert referencedSection == null;
                ELFSection found = ELFSymtab.this.getOwner().getSectionByIndex(referencedSectionIndex);
                if (found != null) {
                    referencedSection = found;
                    referencedSectionIndex = 0;
                    return found;
                } else {
                    throw new IllegalStateException("referenced section has not yet been constructed");
                }
            } else {
                return null;
            }
        }

        void setReferencedSection(ELFSection s) {
            this.referencedSection = s;
            this.referencedSectionIndex = 0;
        }
    }

    public enum SymBinding {
        LOCAL,
        GLOBAL,
        WEAK,
        LOPROC,
        HIPROC;

        static byte createInfoByte(SymType type, SymBinding binding) {
            return SymType.createInfoByte(type, binding);
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

        static byte createInfoByte(SymType type, SymBinding b) {
            if (type == null || b == null) {
                // they must both be null
                assert type == null;
                assert b == null;
                // the byte is zero -- it's for the null symtab entry
                return (byte) 0;
            }
            return (byte) (type.ordinal() | (b.ordinal() << 4)); // FIXME: handle non-ordinal values
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
            // FIXME: support 32- and 64-bit
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
            OutputAssembler oa = AssemblyBuffer.createOutputAssembler(ByteBuffer.allocate(4096));
            write(oa);
            return oa.pos();
        }
    }

    private final ELFStrtab strtab;

    /*
     * Note that we *do* represent the null entry (index 0) explicitly! This is so that indexOf()
     * and get() work as expected. However, clear() must re-create null entry.
     */
    private ArrayList<Entry> entries = new ArrayList<>();
    private boolean entriesAreSorted;
    private Map<String, List<Entry>> entriesByName = new HashMap<>();

    @SuppressWarnings("unused")
    private void createNullEntry() {
        assert entries.size() == 0;
        new Entry(); // adds itself
    }

    public List<Entry> getEntries() {
        return Collections.unmodifiableList(sortedEntries());
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
        for (ELFSectionFlag flag : extraFlags) {
            flags.add(flag);
        }
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

    class SymtabStringCollection extends AbstractCollection<String> {

        @Override
        public Iterator<String> iterator() {
            return new Iterator<String>() {

                Iterator<Entry> symbolsIterator = ELFSymtab.this.sortedEntries().iterator();
                Iterator<String> neededIterator;

                {
                    // fast-forward the iterator past the initial null entry
                    Entry first = symbolsIterator.next();
                    assert first.isNull();
                }

                @Override
                public boolean hasNext() {
                    return symbolsIterator.hasNext() || (neededIterator != null && neededIterator.hasNext());
                }

                @Override
                public String next() {
                    if (symbolsIterator.hasNext()) {
                        String n = symbolsIterator.next().name;
                        assert n != null;
                        return n;
                    } else if (neededIterator != null && neededIterator.hasNext()) {
                        return neededIterator.next();
                    } else {
                        throw new IllegalStateException("no more entries");
                    }
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public int size() {
            return ELFSymtab.this.entries.size();
        }
    }

    class DefaultStrtabImpl extends ELFStrtab {

        DefaultStrtabImpl(ELFObjectFile owner, String name) {
            super(owner, name);
            assert owner == getOwner();
            addContentProvider(new SymtabStringCollection());
        }
    }

    public ELFStrtab getStrtab() {
        return strtab;
    }

    @Override
    public ELFSection getLinkedSection() {
        return getStrtab();
    }

    private int lastLocalIndexCountingFromStart() {
        int lastLocal = -1;
        for (int i = 0; i < sortedEntries().size(); ++i) {
            Entry e = sortedEntries().get(i);
            if (e.isNull()) {
                continue;
            }
            if (e.binding != SymBinding.LOCAL) {
                break;
            }
            lastLocal = i;
        }
        return lastLocal;
    }

    private int lastLocalIndexCountingFromEnd() {
        for (int i = sortedEntries().size() - 1; i >= 0; --i) {
            Entry e = sortedEntries().get(i);
            if (e.binding == SymBinding.LOCAL) {
                return i;
            }
        }
        return -1;
    }

    public int getInfoValue() {
        /*
         * Info should be
         * "one greater than the symbol table index of the last local symbol (binding STB_LOCAL)."
         */
        // FIXME: maintain this value, rather than recomputing it here

        // we check that we're grouped into local/nonlocal, by counting from both ends
        int countingFromEnd = lastLocalIndexCountingFromEnd();
        int countingFromStart = lastLocalIndexCountingFromStart();
        assert countingFromEnd == countingFromStart;
        return countingFromEnd + 1;
    }

    @Override
    public long getLinkedInfo() {
        return getInfoValue();
    }

    @Override
    public byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
        // our strtab's content is already decided; get the string table
        byte[] strtabContent = (byte[]) alreadyDecided.get(strtab).getDecidedValue(LayoutDecision.Kind.CONTENT);
        StringTable table = new StringTable(AssemblyBuffer.createInputDisassembler(ByteBuffer.wrap(strtabContent).order(getOwner().getByteOrder())), strtabContent.length);
        ByteBuffer outBuffer = ByteBuffer.allocate(getWrittenSize()).order(getOwner().getByteOrder());
        OutputAssembler out = AssemblyBuffer.createOutputAssembler(outBuffer);

        for (Entry e : sortedEntries()) {
            EntryStruct s = new EntryStruct();
            // even the null entry has a non-null name ("")
            assert e.name != null;
            s.name = table.indexFor(e.name);
            // careful: our symbol might not be defined,
            // or might be absolute
            ELFSection referencedSection = e.getReferencedSection();
            if (e.pseudoSection == PseudoSection.ABS) {
                // just emit the value
                s.value = e.value;
            } else if (e.pseudoSection == PseudoSection.UNDEF) {
                // it's undefined
                s.value = 0;
            } else if (e.pseudoSection != null) {
                // it's a pseudosection we don't support yet
                assert false : "symbol " + e.name + " references unsupported pseudosection " + e.pseudoSection.name();
                s.value = 0;
            } else if (e.referencedSection == null) {
                assert e.isNull();
                s.value = 0;
            } else {
                assert referencedSection != null;
                // "value" is emitted as a vaddr in dynsym sections,
                // but as a section offset in normal symtabs
                s.value = isDynamic() ? ((int) alreadyDecided.get(e.getReferencedSection()).getDecidedValue(LayoutDecision.Kind.VADDR) + e.value) : e.value;
            }
            s.size = e.size;
            s.info = SymBinding.createInfoByte(e.symType, e.binding);
            if (e.isNull()) {
                assert s.info == 0;
            }
            s.other = (byte) 0;
            s.shndx = (short) getOwner().getIndexForSection(e.getReferencedSection());
            s.write(out);
        }
        return out.getBlob();
    }

    private int getWrittenSize() {
        return entries.size() * (new EntryStruct()).getWrittenSize();
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
        ArrayList<BuildDependency> ourDeps = new ArrayList<>();
        for (BuildDependency d : ObjectFile.defaultDependencies(decisions, this)) {
            ourDeps.add(d);
        }
        // we depend on the contents of our strtab
        ourDeps.add(BuildDependency.createOrGet(decisions.get(this).getDecision(LayoutDecision.Kind.CONTENT), decisions.get(strtab).getDecision(LayoutDecision.Kind.CONTENT)));

        // if we're dynamic,
        // we also depend on the vaddrs of any sections into which our symbols refer
        if (isDynamic()) {
            Set<ELFSection> referencedSections = new HashSet<>();
            for (Entry ent : sortedEntries()) {
                ELFSection es = ent.getReferencedSection();
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
        return new Entry(name, referencedOffset, size, isGlobal ? SymBinding.GLOBAL : SymBinding.LOCAL, isCode ? SymType.FUNC : SymType.OBJECT, (ELFSection) referencedSection);
    }

    @Override
    public Symbol newUndefinedEntry(String name, boolean isCode) {
        return new Entry(name, 0, 0, ELFSymtab.SymBinding.GLOBAL, isCode ? ELFSymtab.SymType.FUNC : ELFSymtab.SymType.OBJECT, PseudoSection.UNDEF);
    }

    private void sort() {
        /*
         * We need to keep the local symbols sorted so that local symbols appear first.
         */
        Comparator<Entry> comp = new Comparator<Entry>() {

            private int mapEntryToInteger(Entry e) {
                if (e.isNull()) {
                    return -1;
                } else if (e.binding == SymBinding.LOCAL) {
                    return 0;
                } else {
                    return 1;
                }
            }

            @Override
            public int compare(Entry arg0, Entry arg1) {
                return Integer.compare(mapEntryToInteger(arg0), mapEntryToInteger(arg1));
            }
        };

        Collections.sort(entries, comp);
    }

    public List<Entry> sortedEntries() {
        if (!entriesAreSorted) {
            sort();
            entriesAreSorted = true;
        }
        return entries;
    }

    // generated delegate methods follow

    @Override
    public boolean contains(Symbol o) {
        return entries.contains(o);
    }

    @Override
    public Entry get(int index) {
        return sortedEntries().get(index);
    }

    @Override
    public int indexOf(Symbol arg0) {
        return sortedEntries().indexOf(arg0);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public Iterator<Symbol> iterator() {
        return (Iterator) sortedEntries().iterator();
    }

    public int size() {
        return entries.size();
    }

    public List<Entry> entriesWithName(String symName) {
        List<Entry> found = entriesByName.get(symName);
        if (found == null) {
            found = new ArrayList<>();
            entriesByName.put(symName, found);
        }
        return Collections.unmodifiableList(found);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @Override
    public List<Symbol> symbolsWithName(String symName) {
        return (List) entriesWithName(symName);
    }

    // convenience methods -- these exist mostly because
    // doing syms.new Entry() generates a warning about the
    // unused allocated object
    public Entry addEntry(String name, long value, long size, SymBinding binding, SymType symType, PseudoSection pseudoSection) {
        return new Entry(name, value, size, binding, symType, pseudoSection);
    }

    public Entry addEntry(String name, long value, long size, SymBinding binding, SymType symType, ELFSection referencedSection) {
        return new Entry(name, value, size, binding, symType, referencedSection);
    }

    public Entry addEntry(String name, long value, long size, SymBinding binding, SymType symType, int referencedSectionIndex) {
        return new Entry(name, value, size, binding, symType, referencedSectionIndex);
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
