/*
 * Copyright (c) 2019, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.elf;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import com.oracle.truffle.api.TruffleFile;
import com.oracle.truffle.api.source.Source;
import com.oracle.truffle.llvm.parser.binary.BinaryParser;
import com.oracle.truffle.llvm.runtime.DefaultLibraryLocator;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LibraryLocator;

/**
 * Locates libraries from {@link ElfFile ELF files}.
 */
public final class ElfLibraryLocator extends LibraryLocator {
    /** Extra library paths local to the module. */
    private final List<String> localPaths;

    private static final Pattern RPATH_PATTERN = Pattern.compile("\\$(ORIGIN|\\{ORIGIN\\})");

    private static class Replacer {
        private final Source source;
        private String origin;

        Replacer(Source source) {
            this.source = source;
        }

        /**
         * Replaces special rpath tokens. Currently, only {@code $ORIGIN} is supported and will be
         * replace by the directory containing executable or shared object.
         */
        public String replace(String e) {
            if (origin == null) {
                origin = BinaryParser.getOrigin(source);
            }
            if (origin == null) {
                return e;
            }
            return RPATH_PATTERN.matcher(e).replaceAll(origin);
        }
    }

    public ElfLibraryLocator(ElfFile elfFile, Source source) {
        List<String> elfPaths = null;
        ElfDynamicSection dynamicSection = elfFile.getDynamicSection();
        if (dynamicSection != null) {
            elfPaths = dynamicSection.getDTRunPathStream().map(new Replacer(source)::replace).collect(Collectors.toList());
            if (elfPaths.isEmpty()) {
                elfPaths = dynamicSection.getDTRPath();
            }
        }
        this.localPaths = elfPaths;
    }

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

        if (localPaths != null) {
            // search file local paths
            traceSearchPath(context, localPaths, reason);
            for (String p : localPaths) {
                Path absPath = Paths.get(p, lib);
                traceTry(context, absPath);
                TruffleFile file = context.getEnv().getInternalTruffleFile(absPath.toUri());
                if (file.exists()) {
                    return file;
                }
            }
        }
        return null;
    }
}
