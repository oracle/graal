/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.macho;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.parser.binary.BinaryParser;
import com.oracle.truffle.llvm.parser.elf.ElfFile;
import com.oracle.truffle.llvm.runtime.DefaultLibraryLocator;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LibraryLocator;

/**
 * Locates libraries from {@link ElfFile ELF files}.
 */
public final class MachOLibraryLocator extends LibraryLocator {
    /** Extra library paths local to the module. */
    private final List<String> rPaths;

    public MachOLibraryLocator(MachOFile machOFile, Source source) {
        String origin = BinaryParser.getOrigin(source);
        List<String> machoPaths = machOFile.getRPaths(origin);
        this.rPaths = machoPaths;
    }

    private static final String RPATH_PATTERN = "@rpath/";

    @Override
    public TruffleFile locateLibrary(LLVMContext context, String lib, Object reason) {
        Path libPath = Paths.get(lib);
        if (libPath.isAbsolute()) {
            return DefaultLibraryLocator.locateAbsolute(context, lib, libPath);
        }
        TruffleFile path = DefaultLibraryLocator.locateGlobal(context, lib);
        if (path != null) {
            return path;
        }

        if (lib.startsWith(RPATH_PATTERN)) {
            String subLib = lib.substring(RPATH_PATTERN.length());
            if (!rPaths.isEmpty()) {
                // search file local paths
                traceSearchPath(context, rPaths, reason);
                for (String p : rPaths) {
                    Path absPath = Paths.get(p, subLib);
                    traceTry(context, absPath);
                    TruffleFile file = context.getEnv().getInternalTruffleFile(absPath.toUri());
                    if (file.exists()) {
                        return file;
                    }
                }
            }
            // search global without @rpath
            return DefaultLibraryLocator.locateGlobal(context, subLib);
        }
        return null;
    }

}
