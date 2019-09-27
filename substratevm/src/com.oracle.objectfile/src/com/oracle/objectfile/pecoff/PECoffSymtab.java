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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.SortedSet;
import java.util.TreeSet;

import com.oracle.objectfile.BuildDependency;
import com.oracle.objectfile.ElementImpl;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.ObjectFile.Element;
import com.oracle.objectfile.ObjectFile.Section;
import com.oracle.objectfile.ObjectFile.Symbol;
import com.oracle.objectfile.SymbolTable;
import com.oracle.objectfile.io.AssemblyBuffer;
import com.oracle.objectfile.io.OutputAssembler;
import com.oracle.objectfile.pecoff.PECoff.IMAGE_SYMBOL;
import com.oracle.objectfile.pecoff.PECoffObjectFile.PECoffSection;

public class PECoffSymtab extends ObjectFile.Element implements SymbolTable {

    @Override
    public ElementImpl getImpl() {
        return this;
    }

    static final class Entry implements ObjectFile.Symbol {
        private final String name;
        private final long value;
        private final long size;
        private final int symClass;
        private final int symType;
        private final PECoffSection referencedSection;
        private final PseudoSection pseudoSection;

        @Override
        public boolean isDefined() {
            return pseudoSection == null || pseudoSection != PseudoSection.UNDEF;
        }

        @Override
        public boolean isAbsolute() {
            return false;
        }

        @Override
        public boolean isCommon() {
            return false;
        }

        @Override
        public boolean isFunction() {
            return symType == IMAGE_SYMBOL.IMAGE_SYM_DTYPE_FUNCTION;
        }

        @Override
        public boolean isGlobal() {
            return symClass == IMAGE_SYMBOL.IMAGE_SYM_CLASS_EXTERNAL;
        }

        public boolean isNull() {
            return name.isEmpty() && value == 0 && size == 0 && symClass == 0 && symType == 0 && referencedSection == null && pseudoSection == null;
        }

        @Override
        public String getName() {
            return name;
        }

        public int getSymClass() {
            return symClass;
        }

        public int getSymType() {
            return symType;
        }

        @Override
        public long getDefinedOffset() {
            if (!isDefined()) {
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
        public long getDefinedAbsoluteValue() {
            return 0L;
        }

        @Override
        public long getSize() {
            return size;
        }

        private Entry(String name, long value, long size, int symclass, int symtype, PECoffSection referencedSection, PseudoSection pseudoSection) {
            this.name = name;
            this.value = value;
            this.size = size;
            this.symClass = symclass;
            this.symType = symtype;
            this.referencedSection = referencedSection;
            this.pseudoSection = pseudoSection;
            assert ((referencedSection == null) != (pseudoSection == null)) || isNull();
        }

        // public constructor, for referencing a real section
        Entry(String name, long value, long size, int symclass, int symtype, PECoffSection referencedSection) {
            this(name, value, size, symclass, symtype, referencedSection, null);
        }

        // public constructor for referencing a pseudosection
        Entry(String name, long value, long size, int symclass, int symtype, PseudoSection pseudoSection) {
            this(name, value, size, symclass, symtype, null, pseudoSection);
        }

        PECoffSection getReferencedSection() {
            return referencedSection;
        }
    }

    public enum PseudoSection {
        UNDEF;
    }

    /*
     * Note that we *do* represent the null entry (index 0) explicitly! This is so that indexOf()
     * and get() work as expected. However, clear() must re-create null entry.
     */

    private static int compareEntries(Entry a, Entry b) {
        int cmp = -Boolean.compare(a.isNull(), b.isNull()); // null symbol first
        if (cmp == 0) { // compare class
            cmp = Integer.compare(a.symClass, b.symClass);
        }
        if (cmp == 0) { // compare type
            cmp = Integer.compare(a.symType, b.symType);
        }
        // order does not matter from here, but try to be reproducible
        if (cmp == 0) {
            cmp = Boolean.compare(a.isDefined(), b.isDefined());
        }
        if (cmp == 0 && a.isDefined()) {
            cmp = Math.toIntExact(a.getDefinedOffset() - b.getDefinedOffset());
        }
        if (cmp == 0) {
            return a.getName().compareTo(b.getName());
        }
        return cmp;
    }

    private SortedSet<Entry> entries = new TreeSet<>(PECoffSymtab::compareEntries);

    private Map<String, Entry> entriesByName = new HashMap<>();
    private Map<Entry, Integer> entriesToIndex;

    private PECoffSymtabStruct symtabStruct;

    /**
     * PECoffSymtab Element encompases the Symbol table array and the String table. The String table
     * immediately follows the Symbol table.
     */
    public PECoffSymtab(PECoffObjectFile owner, String name) {
        owner.super(name);
    }

    /**
     * This function uses the entries Set to create the native byte array that will be written out
     * to disk.
     */
    private PECoffSymtabStruct getNativeSymtab() {
        if (symtabStruct != null) {
            return symtabStruct;
        }
        symtabStruct = new PECoffSymtabStruct();
        entriesToIndex = new HashMap<>();

        int i = 0;
        for (Entry e : entries) {
            PECoffSection sect = e.getReferencedSection();
            // Undefined symbols have a null ReferencedSection
            // Pass -1 as sectID since addSymbolEntry will add 1
            int sectID = sect == null ? -1 : sect.getSectionID();
            long offset = e.isDefined() ? e.getDefinedOffset() : 0L;

            symtabStruct.addSymbolEntry(e.getName(),
                            (byte) e.getSymType(),
                            (byte) e.getSymClass(),
                            (byte) sectID,
                            offset);
            entriesToIndex.put(e, i++);
        }

        return symtabStruct;
    }

    @Override
    public byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
        // Generate the native symbol table from our internal representation
        PECoffSymtabStruct sts = getNativeSymtab();
        ByteBuffer outBuffer = ByteBuffer.allocate(getWrittenSize()).order(getOwner().getByteOrder());
        OutputAssembler out = AssemblyBuffer.createOutputAssembler(outBuffer);
        out.writeBlob(sts.getSymtabArray());
        out.writeBlob(sts.getStrtabArray());
        return out.getBlob();
    }

    @Override
    public int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
        return getWrittenSize();
    }

    private int getWrittenSize() {
        PECoffSymtabStruct sts = getNativeSymtab();
        return ((sts.getSymtabCount() * IMAGE_SYMBOL.totalsize) + sts.getStrtabSize());
    }

    @Override
    public Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
        return new ArrayList<>(ObjectFile.defaultDependencies(decisions, this));
    }

    @Override
    public boolean isLoadable() {
        return true;
    }

    @Override
    public Symbol newDefinedEntry(String name, Section referencedSection, long referencedOffset, long size, boolean isGlobal, boolean isCode) {
        int symClass;
        int symType;

        symClass = isGlobal ? IMAGE_SYMBOL.IMAGE_SYM_CLASS_EXTERNAL : IMAGE_SYMBOL.IMAGE_SYM_CLASS_STATIC;
        symType = isCode ? IMAGE_SYMBOL.IMAGE_SYM_DTYPE_FUNCTION : IMAGE_SYMBOL.IMAGE_SYM_DTYPE_NONE;

        return addEntry(new Entry(name, referencedOffset, size, symClass, symType, (PECoffSection) referencedSection));
    }

    @Override
    public Symbol newUndefinedEntry(String name, boolean isCode) {
        int symClass;
        int symType;

        symClass = IMAGE_SYMBOL.IMAGE_SYM_CLASS_EXTERNAL;
        symType = isCode ? IMAGE_SYMBOL.IMAGE_SYM_DTYPE_FUNCTION : IMAGE_SYMBOL.IMAGE_SYM_DTYPE_NONE;

        return addEntry(new Entry(name, 0, 0, symClass, symType, PseudoSection.UNDEF));
    }

    private Entry addEntry(Entry entry) {
        if (symtabStruct != null) {
            throw new IllegalStateException("Symbol table content is already decided.");
        }
        entries.add(entry);
        entriesByName.put(entry.getName(), entry);
        return entry;
    }

    public Entry getNullEntry() {
        return entries.iterator().next();
    }

    public int indexOf(Symbol sym) {
        if (symtabStruct == null) {
            throw new IllegalStateException("Symbol table content is not decided yet.");
        }
        return entriesToIndex.get(sym);
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

    public int getSymbolCount() {
        int count = getNativeSymtab().getSymtabCount();
        int entcount = entries.size();

        if (entcount != count) {
            System.out.println("Counts don't match, entcount: " + entcount + " count: " + count);
        }
        return entcount;
    }

    public int getDirectiveSize() {
        return getNativeSymtab().getDirectiveSize();
    }

    public byte[] getDirectiveArray() {
        return getNativeSymtab().getDirectiveArray();
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
