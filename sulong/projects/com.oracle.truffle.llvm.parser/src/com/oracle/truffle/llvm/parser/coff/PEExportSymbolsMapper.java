/*
 * Copyright (c) 2022, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package com.oracle.truffle.llvm.parser.coff;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.oracle.truffle.llvm.parser.coff.CoffFile.ImageSectionHeader;
import com.oracle.truffle.llvm.parser.coff.CoffFile.ImageSymbol;
import com.oracle.truffle.llvm.parser.coff.ExportTable.Export;
import com.oracle.truffle.llvm.parser.coff.ExportTable.ExportName;
import com.oracle.truffle.llvm.parser.coff.ExportTable.ExportRVA;
import com.oracle.truffle.llvm.runtime.ExportSymbolsMapper;
import com.oracle.truffle.llvm.runtime.LLVMAlias;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMScope;
import com.oracle.truffle.llvm.runtime.LLVMSymbol;

import org.graalvm.collections.Pair;

/**
 * Windows PE files declare available symbols in their export table section. Each export generally
 * points either to another dll or to a relative virtual address within the file.
 *
 * An example from the sulong.dll file is the exit function. The exit function is declared as the
 * symbol __sulong_exit and may receive the RVA 0x123 in section 0x1000. An entry in the export
 * table entry then maps the exit function to the address 0x1123. We try to find the corresponding
 * symbol for each export table entry, and create a mapping between these.
 */
public class PEExportSymbolsMapper extends ExportSymbolsMapper {
    List<Pair<String, String>> mappings;

    public PEExportSymbolsMapper(PEFile peFile) {
        CoffFile file = peFile.getCoffFile();

        mappings = new ArrayList<>();

        Map<Integer, String> symbolMappings = new HashMap<>();
        for (ImageSymbol symbol : peFile.getCoffFile().getSymbols()) {
            if (symbol.hasValidSectionNumber()) {
                ImageSectionHeader sectionHeader = file.getSectionByNumber(symbol.sectionNumber);
                symbolMappings.put(symbol.value + sectionHeader.virtualAddress, symbol.name);
            }
        }

        for (Pair<String, Export> entry : peFile.getExportTable().getExports()) {
            Export export = entry.getRight();
            if (export instanceof ExportRVA) {
                ExportRVA rvaExport = (ExportRVA) export;
                String symbol = symbolMappings.get(rvaExport.getRVA());
                if (symbol != null) {
                    mappings.add(Pair.create(entry.getLeft(), symbol));
                } else {
                    mappings.add(Pair.create(entry.getLeft(), entry.getLeft()));
                }
            } else if (export instanceof ExportName) {
                ExportName nameExport = (ExportName) export;
                // TODO: The forwarder RVA can be a symbol such as MYDLL.expfunc, specifying that
                // the function should be reexported from MYDLL.
                LLVMContext.llvmLogger().warning(
                                String.format("This library exports '%s' as '%s', but this is not yet supported. The entry will be ignored.", entry.getRight(), nameExport.getName()));
            }
        }
    }

    @Override
    public void registerExports(LLVMScope fileScope, LLVMScope publicFileScope) {

        for (Pair<String, String> pair : mappings) {
            LLVMSymbol symbol = fileScope.get(pair.getRight());
            if (symbol != null) {
                publicFileScope.register(pair.getLeft().equals(symbol.getName()) ? symbol : new LLVMAlias(pair.getLeft(), symbol, true));
            } else {
                LLVMContext.llvmLogger().warning(
                                String.format("Could not map %s to %s. Target symbol not found, ignoring export.", pair.getLeft(), pair.getRight()));
            }
        }
    }
}
