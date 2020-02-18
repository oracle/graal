/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.nio.ByteOrder;
import java.util.ArrayList;
import java.util.EnumSet;
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
import com.oracle.objectfile.SymbolTable;
import com.oracle.objectfile.io.AssemblyBuffer;
import com.oracle.objectfile.io.OutputAssembler;
import com.oracle.objectfile.pecoff.PECoff.IMAGE_FILE_HEADER;
import com.oracle.objectfile.pecoff.PECoff.IMAGE_SECTION_HEADER;

/**
 * Represents a PECoff object file.
 *
 * The main job of this class is to maintain the essential structure of an PECoff file, meaning its
 * header, section table, symbols and symbol table and relocation table.
 *
 * File output order: HEADER, SECTION TABLES, DATA FOR EACH SECTION, RELOCATION TABLE, SYMBOL TABLE
 * & STRING TABLE
 *
 * [TODO]:
 *
 * Fix section alignment in SectionHeaderTable.getOrDecideContent Currently hard coded at 16. Fix
 * relocation offset calculation in PECoffRelocationTable.getOffset().
 */
public class PECoffObjectFile extends ObjectFile {

    private static ByteOrder byteOrder;
    private PECoffMachine machine = PECoffMachine.getSystemNativeValue();
    private PECoffHeader header;
    private SectionHeaderTable sht;
    private PECoffSymtab symtab;
    private PECoffDirectiveSection directives;
    private boolean runtimeDebugInfoGeneration;

    private PECoffObjectFile(int pageSize, boolean runtimeDebugInfoGeneration) {
        super(pageSize);
        this.runtimeDebugInfoGeneration = runtimeDebugInfoGeneration;
        // Create the elements of an empty PECoff file:
        // 1. create header
        header = new PECoffHeader("PECoffHeader");
        // 2. create section header table
        sht = new SectionHeaderTable();
        // 3. create symbol table
        symtab = createSymbolTable();
        // 4. create the linker directive section
        directives = new PECoffDirectiveSection(".drectve", 1);
    }

    public PECoffObjectFile(int pageSize) {
        this(pageSize, false);
    }

    @Override
    public Format getFormat() {
        return Format.PECOFF;
    }

    @Override
    protected PECoffSymtab createSymbolTable() {
        String name = ".symtab";
        PECoffSymtab st = (PECoffSymtab) elementForName(".symtab");
        if (st == null) {
            st = new PECoffSymtab(this, name);
        }
        return st;
    }

    @Override
    public Symbol createDefinedSymbol(String name, Element baseSection, long position, int size, boolean isCode, boolean isGlobal) {
        PECoffSymtab st = createSymbolTable();
        return st.newDefinedEntry(name, (Section) baseSection, position, size, isGlobal, isCode);
    }

    @Override
    public Symbol createUndefinedSymbol(String name, int size, boolean isCode) {
        PECoffSymtab st = createSymbolTable();
        return st.newUndefinedEntry(name, isCode);
    }

    @Override
    protected Segment getOrCreateSegment(String maybeSegmentName, String sectionName, boolean writable, boolean executable) {
        return null;
    }

    @Override
    public PECoffUserDefinedSection newUserDefinedSection(Segment segment, String name, int alignment, ElementImpl impl) {
        PECoffUserDefinedSection userDefined = new PECoffUserDefinedSection(this, name, alignment, impl);
        assert userDefined.getImpl() == impl;
        if (segment != null) {
            getOrCreateSegment(segment.getName(), name, true, false).add(userDefined);
        }
        if (impl != null) {
            impl.setElement(userDefined);
        }
        return userDefined;
    }

    @Override
    public PECoffProgbitsSection newProgbitsSection(Segment segment, String name, int alignment, boolean writable, boolean executable, ProgbitsSectionImpl impl) {
        EnumSet<PECoffSectionFlag> flags = EnumSet.noneOf(PECoffSectionFlag.class);
        flags.add(PECoffSectionFlag.READ);
        if (executable) {
            flags.add(PECoffSectionFlag.EXECUTE);
            flags.add(PECoffSectionFlag.CODE);
        } else {
            flags.add(PECoffSectionFlag.INITIALIZED_DATA);
        }

        if (writable) {
            flags.add(PECoffSectionFlag.WRITE);
        }

        PECoffProgbitsSection progbits = new PECoffProgbitsSection(this, name, alignment, impl, flags);
        impl.setElement(progbits);
        return progbits;
    }

    @Override
    public PECoffNobitsSection newNobitsSection(Segment segment, String name, NobitsSectionImpl impl) {
        PECoffNobitsSection nobits = new PECoffNobitsSection(this, name, impl);
        impl.setElement(nobits);
        return nobits;
    }

    public PECoffSection getSectionByIndex(int i) {
        // if this cast fails, our sectionIndexToElementIndex logic is wrong
        return (PECoffSection) elements.get(elements.sectionIndexToElementIndex(i - 1));
        // NOTE: two levels of translation here: PECoff (1-based) shndx to section index (0-based)
        // to
        // element index
    }

    public int getIndexForSection(PECoffSection s) {
        return elements.elementIndexToSectionIndex(elements.indexOf(s)) + 1;
    }

    @Override
    protected boolean elementsCanSharePage(Element s1, Element s2, int off1, int off2) {
        if (s1 instanceof PECoffSection && s2 instanceof PECoffSection) {
            PECoffSection es1 = (PECoffSection) s1;
            PECoffSection es2 = (PECoffSection) s2;

            boolean flagsCompatible = PECoffSectionFlag.getMemSegmentFlags(es1.getFlags()) == PECoffSectionFlag.getMemSegmentFlags(es2.getFlags());

            return flagsCompatible && super.elementsCanSharePage(es1, es2, off1, off2);
        } else if (s1 instanceof PECoffSection || s2 instanceof PECoffSection) {
            // If one element is a PECoffSection then don't share pages.
            // Could try to share READ only PECoffSections with reloctab, symtab etc.
            return false;
        } else {
            // There are no PECoffSections, the page is read-only
            assert !(s1 instanceof PECoffSection);
            assert !(s2 instanceof PECoffSection);
            return true;
        }
    }

    public List<PECoffSection> getPECoffSections() {
        List<PECoffSection> sections = new ArrayList<>(elements.sectionsCount());
        Iterator<Section> it = elements.sectionsIterator();
        while (it.hasNext()) {
            PECoffSection pe = (PECoffSection) it.next();
            sections.add(pe);
        }
        return sections;
    }

    public abstract class PECoffSection extends ObjectFile.Section {

        EnumSet<PECoffSectionFlag> flags;

        // Index of Sections written to object file. Does not include Elements which are not
        // Sections
        // This is set during layout.
        int sectionID;
        Object relocEntries;

        public PECoffSection(String name) {
            this(name, EnumSet.noneOf(PECoffSectionFlag.class));
        }

        public PECoffSection(String name, EnumSet<PECoffSectionFlag> flags) {
            this(name, getWordSizeInBytes(), flags, -1);
        }

        /**
         * Constructs an PECoff section of given name, flags and section index.
         *
         * @param name the section name
         * @param flags the section's flags
         * @param sectionIndex the desired index in the PECoff section header table
         */
        public PECoffSection(String name, int alignment, EnumSet<PECoffSectionFlag> flags, int sectionIndex) {
            // PECoff sections are aligned at least to a word boundary.
            super(name, alignment, (sectionIndex == -1) ? -1 : elements.sectionIndexToElementIndex(sectionIndex - 1));
            this.flags = flags;
        }

        @Override
        public PECoffObjectFile getOwner() {
            return PECoffObjectFile.this;
        }

        @Override
        public boolean isLoadable() {
            /*
             * NOTE the following distinction: whether a section is loadable is a property of the
             * section (abstractly). (This is also why we we delegate to the impl.)
             *
             * Whether an PECoff section is explicitly loaded is a property of the PHT contents. The
             * code in ObjectFile WILL assign vaddrs for all loadable sections! So
             * isExplicitlyLoaded is actually irrelevant.
             */

            // if we are our own impl, just go with what the flags say
            if (getImpl() == this) {
                return flags.contains(PECoffSectionFlag.READ);
            }

            // otherwise, the impl and flags should agree
            boolean implIsLoadable = getImpl().isLoadable();
            // our constructors and impl-setter are responsible for syncing flags with impl
            assert implIsLoadable == flags.contains(PECoffSectionFlag.READ);

            return implIsLoadable;
        }

        @Override
        public boolean isReferenceable() {
            if (getImpl() == this) {
                return isLoadable();
            }

            return getImpl().isReferenceable();
        }

        public PECoffSection getLinkedSection() {
            return null;
        }

        public long getLinkedInfo() {
            return 0;
        }

        public int getEntrySize() {
            return 0; // means "does not hold a table of fixed-size entries"
        }

        public EnumSet<PECoffSectionFlag> getFlags() {
            return flags;
        }

        public void setFlags(EnumSet<PECoffSectionFlag> flags) {
            this.flags = flags;
        }

        public void setSectionID(int id) {
            this.sectionID = id;
        }

        public int getSectionID() {
            return this.sectionID;
        }

        public void setRelocEntries(Object entries) {
            this.relocEntries = entries;
        }

        public Object getRelocEntries() {
            return this.relocEntries;
        }
    }

    /**
     * Representation of an PECoff binary header.
     */
    public class PECoffHeader extends ObjectFile.Header {

        PECoffHeaderStruct hdr;

        public PECoffHeader(String name) { // create an "empty" default PECoff header
            super(name);
            hdr = new PECoffHeaderStruct();
        }

        @Override
        public Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
            // The Header depends on the section count and symbol table size and offset.

            // We don't use the default dependencies, because our offset mustn't depend on anything.
            HashSet<BuildDependency> dependencies = new HashSet<>();

            LayoutDecision ourContent = decisions.get(this).getDecision(LayoutDecision.Kind.CONTENT);
            LayoutDecision ourOffset = decisions.get(this).getDecision(LayoutDecision.Kind.OFFSET);
            LayoutDecision ourSize = decisions.get(this).getDecision(LayoutDecision.Kind.SIZE);

            LayoutDecision shtSize = decisions.get(sht).getDecision(LayoutDecision.Kind.SIZE);

            LayoutDecision symtOffset = decisions.get(symtab).getDecision(LayoutDecision.Kind.OFFSET);
            LayoutDecision symtSize = decisions.get(symtab).getDecision(LayoutDecision.Kind.SIZE);

            // Mark that our offset depends on our size.
            dependencies.add(BuildDependency.createOrGet(ourOffset, ourSize));
            dependencies.add(BuildDependency.createOrGet(ourContent, shtSize));
            dependencies.add(BuildDependency.createOrGet(ourContent, symtOffset));
            dependencies.add(BuildDependency.createOrGet(ourContent, symtSize));

            return dependencies;
        }

        @Override
        public byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {

            // Calculate Section Count
            int sectSize = (int) alreadyDecided.get(sht).getDecidedValue(LayoutDecision.Kind.SIZE);
            hdr.setSectionCount(sectSize / IMAGE_SECTION_HEADER.totalsize);

            // Set offset of symbol table
            hdr.setSymbolOff((int) alreadyDecided.get(symtab).getDecidedValue(LayoutDecision.Kind.OFFSET));

            // Set symbol table count
            hdr.setSymbolCount(symtab.getSymbolCount());

            return hdr.getArray();
        }

        @Override
        public int getOrDecideOffset(Map<Element, LayoutDecisionMap> alreadyDecided, int offsetHint) {
            return 0; // we're always at 0
        }

        @Override
        public int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
            return IMAGE_FILE_HEADER.totalsize;
        }

    }

    public enum PECoffSectionFlag implements ValueEnum {
        CODE(IMAGE_SECTION_HEADER.IMAGE_SCN_CNT_CODE),
        INITIALIZED_DATA(IMAGE_SECTION_HEADER.IMAGE_SCN_CNT_INITIALIZED_DATA),
        UNINITIALIZED_DATA(IMAGE_SECTION_HEADER.IMAGE_SCN_CNT_UNINITIALIZED_DATA),
        READ(IMAGE_SECTION_HEADER.IMAGE_SCN_MEM_READ),
        WRITE(IMAGE_SECTION_HEADER.IMAGE_SCN_MEM_WRITE),
        EXECUTE(IMAGE_SECTION_HEADER.IMAGE_SCN_MEM_EXECUTE),
        LINKER(IMAGE_SECTION_HEADER.IMAGE_SCN_LNK_INFO | IMAGE_SECTION_HEADER.IMAGE_SCN_LNK_REMOVE);

        private final int value;

        PECoffSectionFlag(int value) {
            this.value = value;
        }

        @Override
        public long value() {
            return value;
        }

        public static long getMemSegmentFlags(EnumSet<PECoffSectionFlag> flags) {
            long out = PECoffSectionFlag.READ.value();
            if (flags.contains(PECoffSectionFlag.WRITE)) {
                out |= PECoffSectionFlag.WRITE.value();
            }
            if (flags.contains(PECoffSectionFlag.EXECUTE)) {
                out |= PECoffSectionFlag.EXECUTE.value();
            }
            return out;
        }
    }

    /**
     * PECoff's section header table "sht" is an Element which describes the file Sections.
     *
     */
    public class SectionHeaderTable extends ObjectFile.Element {

        @Override
        public ElementImpl getImpl() {
            return this;
        }

        // SHT is not loaded
        @Override
        public boolean isLoadable() {
            return false;
        }

        /**
         * Logically create an empty SectionHeaderTable. This is a no-op, except to add an Element
         * to the enclosing ObjectFile.
         */
        public SectionHeaderTable() {
            super(".secthdrtab");
        }

        @Override
        public Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
            /*
             * Our contents depends on almost every Element for one thing or another:
             *
             * - The size and offset of the PECoff Header since Section Headers follow immediately
             * after the Header
             *
             * - the size and offset of every Code and Data section in order to populate Section
             * Data size and offset - The size and offset of the symbol table and string table since
             * the Section Data Follows these sections - The size and offset of the relocation table
             * in order to populate the Relocation offset and count in the Section Header entries
             *
             * Since the Section Header table is dependent on most Elements, we layout all Elements
             * here and ensure that the Sections don't change order. This is needed since the symbol
             * table entries refer to Section indexes.
             *
             * Note: Only Data, Code and Directives are contained in "Sections". Everything else
             * such as the symbol table, string table, relocation table and even this Section Header
             * Table are "Elements" and not "Sections".
             *
             */
            HashSet<BuildDependency> deps = ObjectFile.defaultDependencies(decisions, this);

            LayoutDecision ourOffset = decisions.get(this).getDecision(LayoutDecision.Kind.OFFSET);
            LayoutDecision ourContent = decisions.get(this).getDecision(LayoutDecision.Kind.CONTENT);

            LayoutDecision hdrOffset = decisions.get(header).getDecision(LayoutDecision.Kind.OFFSET);
            LayoutDecision hdrSize = decisions.get(header).getDecision(LayoutDecision.Kind.SIZE);
            deps.add(BuildDependency.createOrGet(ourOffset, hdrOffset));
            deps.add(BuildDependency.createOrGet(ourOffset, hdrSize));

            LayoutDecision prevOffset = null;
            LayoutDecision prevSize = null;
            int sectionID = 0;
            for (Section s : getSections()) {
                assert s instanceof PECoffSection;
                PECoffSection pecoffS = (PECoffSection) s;
                pecoffS.setSectionID(sectionID++);

                // Make the offset of each section dependent on the previous in order to lock down
                // order.
                LayoutDecision nextOffset = decisions.get(s).getDecision(LayoutDecision.Kind.OFFSET);
                if (prevOffset != null) {
                    deps.add(BuildDependency.createOrGet(nextOffset, prevOffset));
                }
                LayoutDecision nextSize = decisions.get(s).getDecision(LayoutDecision.Kind.SIZE);
                if (prevSize != null) {
                    deps.add(BuildDependency.createOrGet(nextOffset, prevSize));
                }
                // Our content is dependent on the offset and size of every Section
                deps.add(BuildDependency.createOrGet(ourContent, nextOffset));
                deps.add(BuildDependency.createOrGet(ourContent, nextSize));
                prevOffset = nextOffset;
                prevSize = nextSize;
            }

            // The relocation table
            PECoffRelocationTable reloctable = getRelocationTable();
            LayoutDecision relocOffset = decisions.get(reloctable).getDecision(LayoutDecision.Kind.OFFSET);
            LayoutDecision relocSize = decisions.get(reloctable).getDecision(LayoutDecision.Kind.SIZE);
            deps.add(BuildDependency.createOrGet(relocOffset, prevOffset));
            deps.add(BuildDependency.createOrGet(relocOffset, prevSize));

            // Make the Section Header Table dependent on offset and size of relocation table
            // Since we need to know count and offset of relocs for each section
            deps.add(BuildDependency.createOrGet(ourContent, relocOffset));
            deps.add(BuildDependency.createOrGet(ourContent, relocSize));

            // The symbol table comes right after the relocation table
            LayoutDecision symtabOffset = decisions.get(symtab).getDecision(LayoutDecision.Kind.OFFSET);
            deps.add(BuildDependency.createOrGet(symtabOffset, relocOffset));
            deps.add(BuildDependency.createOrGet(symtabOffset, relocSize));

            return deps;
        }

        @Override
        public int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
            return (elements.sectionsCount()) * IMAGE_SECTION_HEADER.totalsize;
        }

        @Override
        public byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
            PECoffRelocationTable rt = (PECoffRelocationTable) elementForName(".reloctab");
            OutputAssembler out = AssemblyBuffer.createOutputAssembler(getByteOrder());
            int sectionIndex = 0;

            for (Section s : getSections()) {
                PECoffSection es = (PECoffSection) s;

                /* TODO: alignment is 1024?? */
                int align = es.getAlignment() >= 1024 ? 16 : es.getAlignment();

                PECoffSectionStruct ent = new PECoffSectionStruct(
                                nameForElement(s),
                                (int) ObjectFile.flagSetAsLong(es.getFlags()),
                                align,
                                sectionIndex + 1);

                // Set offset and size of section
                ent.setOffset((int) alreadyDecided.get(es).getDecidedValue(LayoutDecision.Kind.OFFSET));
                int sectionSize = (int) alreadyDecided.get(es).getDecidedValue(LayoutDecision.Kind.SIZE);

                if (sectionSize == 0) {
                    // For NobitsSections we have to use getMemSize as sectionSize
                    sectionSize = es.getMemSize(alreadyDecided);
                }
                ent.setSize(sectionSize);

                if (es.getFlags().contains(PECoffSectionFlag.READ) && runtimeDebugInfoGeneration) {
                    // For runtimeDebugInfoGeneration we allow virtualAddress to be set
                    ent.setVirtualAddress((int) alreadyDecided.get(es).getDecidedValue(LayoutDecision.Kind.VADDR));
                } else {
                    // We are building a relocatable object file -> virtualAddress has to be zero.
                    ent.setVirtualAddress(0);
                }

                // Set relocation table count and offset for this section
                int relocBaseOffset = ((int) alreadyDecided.get(rt).getDecidedValue(LayoutDecision.Kind.OFFSET));
                int relocCount = rt.getRelocCount(sectionIndex);
                if (relocCount > 0) {
                    ent.setRelcount(relocCount);
                    ent.setReloff(relocBaseOffset + rt.getRelocOffset(sectionIndex));
                }

                out.align(PECoffSectionStruct.getShdrAlign());
                out.writeBlob(ent.getArray());
                sectionIndex++;
            }
            return out.getBlob();
        }

        // forward everything we don't implement to the ObjectFile-supplied defaults
        @Override
        public LayoutDecisionMap getDecisions(LayoutDecisionMap copyingIn) {
            return defaultDecisions(this, copyingIn);
        }

        @Override
        public int getOrDecideOffset(Map<Element, LayoutDecisionMap> alreadyDecided, int offsetHint) {
            // return defaultGetOrDecideOffset(alreadyDecided, this, offsetHint);
            return IMAGE_FILE_HEADER.totalsize;
        }

        @Override
        public int getOrDecideVaddr(Map<Element, LayoutDecisionMap> alreadyDecided, int vaddrHint) {
            return defaultGetOrDecideVaddr(alreadyDecided, this, vaddrHint);
        }

    }

    public class PECoffDirectiveSection extends PECoffObjectFile.PECoffSection {

        public PECoffDirectiveSection(String name, int alignment) {
            super(name, alignment, EnumSet.of(PECoffSectionFlag.LINKER), -1);
        }

        @Override
        public ElementImpl getImpl() {
            return directives;
        }

        @Override
        public int getOrDecideOffset(Map<Element, LayoutDecisionMap> alreadyDecided, int offsetHint) {
            // Put the directives immediately after the section header table.
            return IMAGE_FILE_HEADER.totalsize + ((elements.sectionsCount()) * IMAGE_SECTION_HEADER.totalsize);
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
        public Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
            return ObjectFile.defaultDependencies(decisions, this);
        }

        @Override
        public byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
            return symtab.getDirectiveArray();
        }

        @Override
        public int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
            return symtab.getDirectiveSize();
        }
    }

    @Override
    public Set<Segment> getSegments() {
        return new HashSet<>();
    }

    @Override
    public ByteOrder getByteOrder() {
        return byteOrder;
    }

    @Override
    public void setByteOrder(ByteOrder byteorder) {
        byteOrder = byteorder;
    }

    public static ByteOrder getTargetByteOrder() {
        return byteOrder;
    }

    @Override
    public int getWordSizeInBytes() {
        return 8;
    }

    @Override
    public boolean shouldRecordDebugRelocations() {
        return true;
    }

    public PECoffMachine getMachine() {
        return machine;
    }

    public PECoffRelocationTable getOrCreateRelocSection(PECoffSymtab syms, boolean withExplicitAddends) {
        Element el = elementForName(".reloctab");
        PECoffRelocationTable rs;
        if (el == null) {
            rs = new PECoffRelocationTable(this, ".reloctab", syms, withExplicitAddends);
        } else if (el instanceof PECoffRelocationTable) {
            rs = (PECoffRelocationTable) el;
        } else {
            throw new IllegalStateException("section exists but is not an PECoffRelocationTable");
        }
        return rs;
    }

    @Override
    public SymbolTable getSymbolTable() {
        return (SymbolTable) elementForName(".symtab");
    }

    public PECoffRelocationTable getRelocationTable() {
        return (PECoffRelocationTable) elementForName(".reloctab");
    }

    @Override
    protected int getMinimumFileSize() {
        return 0;
    }
}
