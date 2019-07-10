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
package com.oracle.truffle.llvm.runtime;

import com.oracle.truffle.llvm.runtime.except.LLVMLinkerException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * Locates libraries based on the global {@link LLVMContext#getLibraryPaths() library paths}.
 */
public final class DefaultLibraryLocator extends LibraryLocator {

    public static DefaultLibraryLocator INSTANCE = new DefaultLibraryLocator();

    private DefaultLibraryLocator() {
    }

    @Override
    public Path locateLibrary(LLVMContext context, String lib, Object reason) {
        Path libPath = Paths.get(lib);
        if (libPath.isAbsolute()) {
            return locateAbsolute(context, lib, libPath);
        }
        Path absPath = locateGlobal(context, lib);
        if (absPath != null) {
            return absPath;
        }

        traceTry(context, libPath);
        return libPath;
    }

    public static Path locateGlobal(LLVMContext context, String lib) {
        // search global paths
        List<Path> libraryPaths = context.getLibraryPaths();
        traceSearchPath(context, libraryPaths);
        for (Path p : libraryPaths) {
            Path absPath = Paths.get(p.toString(), lib);
            traceTry(context, absPath);
            if (context.getEnv().getTruffleFile(absPath.toUri()).exists()) {
                return absPath;
            }
        }
        return null;
    }

    public static Path locateAbsolute(LLVMContext context, String lib, Path libPath) {
        assert libPath.isAbsolute();
        traceTry(context, libPath);
        if (context.getEnv().getTruffleFile(libPath.toUri()).exists()) {
            return libPath;
        } else {
            throw new LLVMLinkerException(String.format("Library \"%s\" does not exist.", lib));
        }
    }

}
