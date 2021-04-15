/*
 * Copyright (c) 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.target;

import java.util.Objects;

public final class TargetTriple {

    /**
     * Parses a target triple string and creates a {@link TargetTriple} instance. A target triple
     * has the form {@code <arch>-<vendor>-<sys>-<abi>}. The {@code <abi>} part is optional.
     * 
     * @see <a href="https://releases.llvm.org/10.0.0/docs/LangRef.html#target-triple">LLVM Language
     *      Reference Manual - Target Triple</a>
     */
    public static TargetTriple create(String triple) {
        String[] s = triple.split("-", 4);
        if (s.length < 3) {
            throw new IllegalArgumentException("Malformed target-triple string: " + triple);
        }
        final String arch = s[0];
        final String vendor = s[1];
        final String system = s[2];
        final String abi = s.length == 4 ? s[3] : null;
        final String systemName;
        final String systemVersion;
        if (system.startsWith(MACOSX_SYSTEM_NAME)) {
            systemName = MACOSX_SYSTEM_NAME;
            systemVersion = system.substring(MACOSX_SYSTEM_NAME.length());
        } else {
            systemName = system;
            systemVersion = null;
        }
        return new TargetTriple(triple, arch, vendor, systemName, systemVersion, abi);
    }

    private static final String MACOSX_SYSTEM_NAME = "macosx";

    /**
     * Only used for printing.
     */
    private final String triple;
    private final String arch;
    private final String vendor;
    private final String systemName;
    @SuppressWarnings("unused") private final String systemVersion;
    private final String abi;

    private TargetTriple(String triple, String arch, String vendor, String systemName, String systemVersion, String abi) {
        this.triple = triple;
        this.arch = arch;
        this.vendor = vendor;
        this.systemName = systemName;
        this.systemVersion = systemVersion;
        this.abi = abi;
    }

    @Override
    public String toString() {
        return triple;
    }

    /**
     * Checks whether this target triple matches another target triple. Two triple match if all
     * properties except for the {@link #systemVersion} are equal.
     */
    public boolean matches(TargetTriple other) {
        return triple.equals(other.triple) ||
                        // ignoring systemVersion
                        arch.equals(other.arch) && vendor.equals(other.vendor) && systemName.equals(other.systemName) && Objects.equals(abi, other.abi);
    }
}
