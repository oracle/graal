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

import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import com.oracle.objectfile.BuildDependency;
import com.oracle.objectfile.ElementImpl;
import com.oracle.objectfile.LayoutDecisionMap;
import com.oracle.objectfile.ObjectFile;
import com.oracle.objectfile.ObjectFile.Element;
import com.oracle.svm.core.graal.llvm.objectfile.LLVMObjectFile.LLVMSection;
import com.oracle.svm.core.util.VMError;

public class LLVMUserDefinedSection extends LLVMSection implements ObjectFile.RelocatableSectionImpl {
    protected ElementImpl impl;

    private final Map<Integer, Entry> relocations = new HashMap<>();

    static final class Entry implements ObjectFile.RelocationRecord {
        final long offset;
        final LLVMSymtab.Entry sym;
        final long addend;

        Entry(long offset, LLVMSymtab.Entry sym, long addend) {
            this.offset = offset;
            this.sym = sym;
            this.addend = addend;
        }

        @Override
        public long getOffset() {
            return offset;
        }

        @Override
        public ObjectFile.Symbol getReferencedSymbol() {
            return sym;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj) {
                return true;
            }
            if (obj != null && getClass() == obj.getClass()) {
                Entry other = (Entry) obj;
                return offset == other.offset && Objects.equals(sym, other.sym) && addend == other.addend;
            }
            return false;
        }

        @Override
        public int hashCode() {
            return (Long.hashCode(offset) * 31 + sym.hashCode()) * 31 + Long.hashCode(addend);
        }
    }

    @Override
    public ElementImpl getImpl() {
        return impl;
    }

    LLVMUserDefinedSection(LLVMObjectFile owner, String name, ElementImpl impl) {
        this(owner, name, owner.getWordSizeInBytes(), impl);
    }

    LLVMUserDefinedSection(LLVMObjectFile owner, String name, int alignment, ElementImpl impl) {
        owner.super(name, alignment);
        this.impl = impl;
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
        throw VMError.unimplemented("Not needed for LLVM user defined section as LLVM will generate the relocation itself.");
    }

    @Override
    public void markRelocationSite(int offset, ByteBuffer bb, ObjectFile.RelocationKind k, String symbolName, long addend) {
        LLVMSymtab syms = (LLVMSymtab) getOwner().elementForName("symtab");
        if (k != ObjectFile.RelocationKind.DIRECT_8) {
            VMError.shouldNotReachHere("Got relocation " + k + ": " + symbolName + " at offset: " + offset + " with addend: " + addend);
        }
        relocations.put(offset, new Entry(offset, syms.getSymbol(symbolName), addend));
    }

    public Map<Integer, Entry> getRelocations() {
        return relocations;
    }
}
