/*
 * Copyright (c) 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.factories;

import com.oracle.truffle.llvm.runtime.ExternalLibrary;
import com.oracle.truffle.llvm.runtime.LLVMContext;
import com.oracle.truffle.llvm.runtime.LLVMSyscallEntry;
import com.oracle.truffle.llvm.runtime.PlatformCapability;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public abstract class PlatformCapabilityBase<S extends Enum<S> & LLVMSyscallEntry> extends PlatformCapability<S> {
    public static final String LIBCXXABI_PREFIX = "libc++abi.";
    public static final String LIBCXX_PREFIX = "libc++.";
    protected final boolean loadCxxLibraries;

    public PlatformCapabilityBase(Class<S> cls, boolean loadCxxLibraries) {
        super(cls);
        this.loadCxxLibraries = loadCxxLibraries;
    }

    @Override
    public String[] getSulongDefaultLibraries() {
        if (loadCxxLibraries) {
            return new String[]{getLibsulongFilename(), getLibsulongxxFilename()};
        } else {
            return new String[]{getLibsulongFilename()};
        }
    }

    public abstract String getLibsulongxxFilename();

    public abstract String getLibsulongFilename();

    @Override
    public List<String> preprocessDependencies(LLVMContext ctx, ExternalLibrary library, List<String> dependencies) {
        List<String> newDeps = null;
        boolean libSulongXXAdded = false;
        // inject libsulong++ dependency
        if (ctx.isInternalLibrary(library) && library.hasFile()) {
            Path path = Paths.get(library.getFile().getPath());
            String remainder = ctx.getInternalLibraryPath().relativize(path).toString();
            if (remainder.startsWith(LIBCXXABI_PREFIX) || remainder.startsWith(LIBCXX_PREFIX)) {
                newDeps = new ArrayList<>(dependencies);
                newDeps.add(getLibsulongxxFilename());
                libSulongXXAdded = true;
            }
        }

        // replace absolute dependencies to libc++* to relative ones (in the llvm home)
        for (int i = 0; i < dependencies.size(); i++) {
            String dep = dependencies.get(i);
            if (dep.startsWith("/usr/lib/libc++")) {
                Path namePath = Paths.get(dep).getFileName();
                if (namePath != null) {
                    String filename = namePath.toString();
                    if (filename.startsWith("libc++.") || filename.startsWith("libc++abi.")) {
                        if (newDeps == null) {
                            newDeps = new ArrayList<>(dependencies);
                        }
                        // replace with file name
                        newDeps.set(i, filename);
                        dep = filename;
                    }
                }
            }
            if (!libSulongXXAdded && (dep.startsWith(LIBCXXABI_PREFIX) || dep.startsWith(LIBCXX_PREFIX))) {
                // inject libsulong++ dependency
                if (newDeps == null) {
                    newDeps = new ArrayList<>(dependencies);
                }
                newDeps.add(getLibsulongxxFilename());
                libSulongXXAdded = true;
            }
        }
        if (newDeps != null) {
            return newDeps;
        }
        return dependencies;
    }
}
