/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.listeners;

import java.util.List;

import com.oracle.truffle.llvm.parser.listeners.function.Function;
import com.oracle.truffle.llvm.parser.listeners.function.FunctionVersion.FunctionV32;
import com.oracle.truffle.llvm.parser.listeners.function.FunctionVersion.FunctionV38;
import com.oracle.truffle.llvm.parser.listeners.module.Module;
import com.oracle.truffle.llvm.parser.listeners.module.ModuleVersionHelper;
import com.oracle.truffle.llvm.parser.listeners.module.ModuleVersionHelper.ModuleV32;
import com.oracle.truffle.llvm.parser.listeners.module.ModuleVersionHelper.ModuleV38;
import com.oracle.truffle.llvm.parser.model.ModelModule;
import com.oracle.truffle.llvm.parser.model.generators.FunctionGenerator;
import com.oracle.truffle.llvm.runtime.options.LLVMOptions;
import com.oracle.truffle.llvm.runtime.types.Type;

public final class IRVersionController {

    @FunctionalInterface
    private interface ModuleParser {

        ModuleVersionHelper instantiate();
    }

    @FunctionalInterface
    private interface FunctionParser {

        Function instantiate(IRVersionController version, Types types, List<Type> symbols, FunctionGenerator generator, int mode);
    }

    private enum IRVersion {
        DEFAULT(ModuleV32::new, FunctionV32::new, "3.2", "3.3"),
        LLVM_32(ModuleV32::new, FunctionV32::new, "3.2", "3.3"),
        LLVM_38(ModuleV38::new, FunctionV38::new, "3.8", "3.9");

        private final String[] versionInformation;
        private final FunctionParser function;
        private final ModuleParser module;

        IRVersion(ModuleParser module, FunctionParser function, String... strings) {
            this.function = function;
            this.versionInformation = strings;
            this.module = module;
        }

        private boolean isVersion(String versionString) {
            for (String v : versionInformation) {
                if (versionString.contains(v)) {
                    return true;
                }
            }
            return false;
        }

    }

    private IRVersion version;

    public IRVersionController() {
        version = getVersion(LLVMOptions.ENGINE.llvmVersion());
    }

    public void setVersion(String versionStr) {
        IRVersion newVersion = getVersion(versionStr);
        if (version == IRVersion.DEFAULT || version == newVersion) {
            version = newVersion;
        } else {
            throw new IllegalStateException(version.toString());
        }
    }

    private static IRVersion getVersion(String versionStr) {
        for (IRVersion v : IRVersion.values()) {
            if (v.isVersion(versionStr)) {
                return v;
            }
        }
        return IRVersion.DEFAULT;
    }

    public Function createFunction(Types types, List<Type> symbols, FunctionGenerator generator, int mode) {
        return version.function.instantiate(this, types, symbols, generator, mode);
    }

    public ModuleVersionHelper createModuleVersionHelper() {
        return version.module.instantiate();
    }

    public Module createModule(ModelModule generator) {
        return new Module(this, generator);
    }

}
