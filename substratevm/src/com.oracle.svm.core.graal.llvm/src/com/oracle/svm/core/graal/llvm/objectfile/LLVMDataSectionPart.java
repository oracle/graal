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

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.objectfile.ObjectFile;
import com.oracle.svm.core.graal.llvm.util.LLVMIRBuilder;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.shadowed.org.bytedeco.llvm.LLVM.LLVMValueRef;

public class LLVMDataSectionPart {
    private final LLVMIRBuilder builder;
    private final int id;
    private final int batchOffset;
    private final int pageSize;
    private final ObjectFile.Element element;
    private final List<Integer> relocationsBatch;
    private final LLVMValueRef[] content;
    private final List<LLVMSymtab.Entry> symbols;

    private final Map<String, LLVMValueRef> symbolsToGlobal = new HashMap<>();

    private LLVMValueRef baseGlobal;

    public LLVMDataSectionPart(int id, int batchOffset, int pageSize, ObjectFile.Element element, LLVMValueRef[] content, List<Integer> relocationsBatch, List<LLVMSymtab.Entry> symbols) {
        this.builder = new LLVMIRBuilder("LLVMDataSection" + id);
        this.id = id;
        this.batchOffset = batchOffset;
        this.pageSize = pageSize;
        this.element = element;
        this.relocationsBatch = relocationsBatch;
        this.content = content;
        this.symbols = symbols;
        computeBitcode();
    }

    private void computeBitcode() {
        getBaseSymbol();

        if (symbols != null) {
            computeSymbolsToGlobals();
        }

        applyRelocations();

        initializeGlobals();
    }

    private void initializeGlobals() {
        builder.setInitializer(baseGlobal, builder.constantArray(builder.longType(), content));
    }

    private void applyRelocations() {
        if (!(element instanceof LLVMUserDefinedSection section)) {
            throw VMError.shouldNotReachHere("Only LLVMUserDefinedSections should be emitted to the data section.");
        }
        Map<Integer, LLVMUserDefinedSection.Entry> relocations = section.getRelocations();

        for (int relocOffset : relocationsBatch) {
            LLVMUserDefinedSection.Entry relocation = relocations.get(relocOffset);
            String name = relocation.getReferencedSymbol().getName();
            if (LLVMObjectFile.sectionToFirstSymbol.containsKey(name)) {
                name = LLVMObjectFile.sectionToFirstSymbol.get(name);
            }
            LLVMValueRef globalEntry = symbolsToGlobal.get(name);

            if (globalEntry == null) {
                globalEntry = builder.addGlobal(name);
                symbolsToGlobal.put(name, globalEntry);
            }

            content[(relocOffset - batchOffset) / Long.BYTES] = builder.buildAdd(builder.buildPtrToInt(globalEntry), builder.constantLong(relocation.addend));
        }
    }

    private void getBaseSymbol() {
        String sectionName = element.getName();
        String baseSymbol = sectionName + "_base_" + id;
        if (batchOffset == 0) {
            LLVMObjectFile.sectionToFirstSymbol.put(element.getName(), baseSymbol);
        }

        baseGlobal = builder.getUniqueGlobal(baseSymbol, builder.arrayType(builder.wordType(), content.length), false);
        builder.setAlignment(baseGlobal, pageSize);
        LLVMIRBuilder.setSection(baseGlobal, sectionName);

        symbolsToGlobal.put(baseSymbol, baseGlobal);
    }

    private void computeSymbolsToGlobals() {
        String sectionName = element.getName();

        int startSymbol = 0;

        symbols.sort(Comparator.comparingLong(LLVMSymtab.Entry::getDefinedOffset));

        for (int i = startSymbol; i < symbols.size(); ++i) {
            ObjectFile.Symbol symbol = symbols.get(i);
            long offset = symbol.getDefinedOffset();
            String name = symbol.getName();

            LLVMValueRef aliasOffset = builder.buildAdd(builder.buildPtrToInt(baseGlobal), builder.constantLong(offset));
            LLVMValueRef aliasAddress = builder.buildIntToPtr(aliasOffset, builder.pointerType(builder.wordType()));
            LLVMValueRef alias = builder.addAlias(aliasAddress, name);
            LLVMIRBuilder.setSection(alias, sectionName);
        }
    }

    public byte[] getBitcode() {
        byte[] bitcode = builder.getBitcode();
        builder.close();
        return bitcode;
    }

    public int getId() {
        return id;
    }
}
