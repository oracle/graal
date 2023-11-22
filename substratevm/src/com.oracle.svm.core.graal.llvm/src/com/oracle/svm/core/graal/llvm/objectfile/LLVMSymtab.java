/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.llvm.objectfile;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
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
import com.oracle.svm.core.graal.llvm.objectfile.LLVMObjectFile.LLVMSection;
import com.oracle.svm.core.util.VMError;

public class LLVMSymtab extends LLVMSection implements SymbolTable {

    @Override
    public ElementImpl getImpl() {
        return this;
    }

    static final class Entry implements ObjectFile.Symbol {
        private final String name;
        private final long value;
        private final long size;
        private final LLVMSection referencedSection;

        @Override
        public boolean isDefined() {
            return referencedSection != null;
        }

        @Override
        public boolean isAbsolute() {
            throw VMError.unimplemented("Not needed for LLVM symbol table as LLVM will generate it itself.");
        }

        @Override
        public boolean isCommon() {
            throw VMError.unimplemented("Not needed for LLVM symbol table as LLVM will generate it itself.");
        }

        @Override
        public boolean isFunction() {
            throw VMError.unimplemented("Not needed for LLVM symbol table as LLVM will generate it itself.");
        }

        @Override
        public boolean isGlobal() {
            return true;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public long getDefinedOffset() {
            return value;
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
            throw VMError.unimplemented("Not needed for LLVM symbol table as LLVM will generate it itself.");
        }

        private Entry(String name, long value, long size, LLVMSection referencedSection) {
            this.name = name;
            this.value = value;
            this.size = size;
            this.referencedSection = referencedSection;
        }
    }

    private static int compareEntries(Entry a, Entry b) {
        return a.getName().compareTo(b.getName());
    }

    private SortedSet<Entry> entries = new TreeSet<>(LLVMSymtab::compareEntries);

    private Map<String, Entry> entriesByName = new HashMap<>();
    private Map<String, List<Entry>> entriesBySection = new HashMap<>();

    public LLVMSymtab(LLVMObjectFile owner, String name) {
        owner.super(name);
    }

    @Override
    public byte[] getOrDecideContent(Map<Element, LayoutDecisionMap> alreadyDecided, byte[] contentHint) {
        // The symtab is emitted by LLVM itself
        return new byte[0];
    }

    @Override
    public int getOrDecideSize(Map<Element, LayoutDecisionMap> alreadyDecided, int sizeHint) {
        // The symtab is emitted by LLVM itself
        return 0;
    }

    @Override
    public Iterable<BuildDependency> getDependencies(Map<Element, LayoutDecisionMap> decisions) {
        return ObjectFile.minimalDependencies(decisions, this);
    }

    @Override
    public Symbol newDefinedEntry(String name, Section referencedSection, long referencedOffset, long size, boolean isGlobal, boolean isCode) {
        assert referencedSection != null;
        return addEntry(new Entry(name, referencedOffset, size, (LLVMSection) referencedSection));
    }

    @Override
    public Symbol newUndefinedEntry(String name, boolean isCode) {
        return addEntry(new Entry(name, 0, 0, null));
    }

    private Entry addEntry(Entry entry) {
        entriesByName.compute(entry.getName(), (k, v) -> SymbolTable.tryReplace(v, entry));
        if (entry.getDefinedSection() != null) {
            entriesBySection.compute(entry.getDefinedSection().getName(), (k, v) -> {
                if (v == null) {
                    // It is not possible to define a global variable with the same name as the
                    // section it is in. The symbol is however defined by default.
                    if (!k.equals(entry.getName())) {
                        throw VMError.shouldNotReachHere(String.format("The first symbol should be the section itself (%s), but it is %s", k, entry.getName()));
                    }
                    return new ArrayList<>();
                } else {
                    v.add(entry);
                    return v;
                }
            });
        }
        entries.add(entry);
        return entry;
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

    public List<Entry> getSectionEntries(String sectionName) {
        return entriesBySection.get(sectionName);
    }
}
