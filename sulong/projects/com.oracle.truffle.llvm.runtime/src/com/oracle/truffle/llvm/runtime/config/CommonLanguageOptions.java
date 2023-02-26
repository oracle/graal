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
package com.oracle.truffle.llvm.runtime.config;

import org.graalvm.options.OptionValues;

import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption.OSRMode;

/**
 * Options that are per language instance. If these options are different, they prevent code
 * sharing. Different Sulong configurations (e.g. native, managed) should subclass this, potentially
 * adding more configuration specific options that should also be per language instance.
 */
public abstract class CommonLanguageOptions {

    public final boolean loadCxxLibraries;
    public final OSRMode osrMode;

    protected CommonLanguageOptions(OptionValues options) {
        this.loadCxxLibraries = options.get(SulongEngineOption.LOAD_CXX_LIBRARIES);
        this.osrMode = options.get(SulongEngineOption.OSR_MODE);
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof CommonLanguageOptions)) {
            return false;
        }
        CommonLanguageOptions other = (CommonLanguageOptions) o;
        return this.loadCxxLibraries == other.loadCxxLibraries && this.osrMode == other.osrMode;
    }

    @Override
    public int hashCode() {
        int hash = 7;
        hash = 71 * hash + (this.loadCxxLibraries ? 1 : 0);
        hash = 71 * hash + this.osrMode.hashCode();
        return hash;
    }
}
