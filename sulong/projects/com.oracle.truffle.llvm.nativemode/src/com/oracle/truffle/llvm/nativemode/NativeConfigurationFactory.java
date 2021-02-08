/*
 * Copyright (c) 2016, 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nativemode;

import com.oracle.truffle.llvm.nativemode.NativeConfigurationFactory.Key;

import java.util.List;

import com.oracle.truffle.llvm.nativemode.runtime.SulongNativeOption;
import org.graalvm.options.OptionDescriptor;

import com.oracle.truffle.llvm.runtime.ContextExtension;
import com.oracle.truffle.llvm.runtime.LLVMLanguage;
import com.oracle.truffle.llvm.runtime.config.Configuration;
import com.oracle.truffle.llvm.runtime.config.ConfigurationFactory;
import com.oracle.truffle.llvm.runtime.options.SulongEngineOption;
import org.graalvm.options.OptionValues;

public class NativeConfigurationFactory implements ConfigurationFactory<Key> {

    public static final class Key {

        final boolean loadCxxLibraries;
        final boolean enableNFI;

        public Key(OptionValues options) {
            this.loadCxxLibraries = options.get(SulongEngineOption.LOAD_CXX_LIBRARIES);
            this.enableNFI = options.get(SulongNativeOption.ENABLE_NFI);
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Key)) {
                return false;
            }
            Key other = (Key) o;
            return this.loadCxxLibraries == other.loadCxxLibraries && this.enableNFI == other.enableNFI;
        }

        @Override
        public int hashCode() {
            int hash = 7;
            hash = 71 * hash + (this.loadCxxLibraries ? 1 : 0);
            hash = 71 * hash + (this.enableNFI ? 1 : 0);
            return hash;
        }
    }

    @Override
    public Key parseOptions(OptionValues options) {
        return new Key(options);
    }

    @Override
    public int getPriority() {
        return 0;
    }

    @Override
    public List<OptionDescriptor> getOptionDescriptors() {
        return SulongNativeOption.describeOptions();
    }

    @Override
    public String getName() {
        return "native mode";
    }

    @Override
    public String getHint() {
        return "enabled by default";
    }

    @Override
    public Configuration createConfiguration(LLVMLanguage language, ContextExtension.Registry ctxExtRegistry, Key key) {
        return new NativeConfiguration(language, ctxExtRegistry, key);
    }
}
