/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.metadata;

public final class MDModule extends MDName implements MDBaseNode {

    private MDBaseNode scope;
    private MDBaseNode configurationMacros;
    private MDBaseNode includePath;
    private MDBaseNode sysRoot;

    private MDModule() {
        this.scope = MDVoidNode.INSTANCE;
        this.configurationMacros = MDVoidNode.INSTANCE;
        this.includePath = MDVoidNode.INSTANCE;
        this.sysRoot = MDVoidNode.INSTANCE;
    }

    public MDBaseNode getScope() {
        return scope;
    }

    public MDBaseNode getConfigurationMacros() {
        return configurationMacros;
    }

    public MDBaseNode getIncludePath() {
        return includePath;
    }

    public MDBaseNode getSysRoot() {
        return sysRoot;
    }

    @Override
    public void replace(MDBaseNode oldValue, MDBaseNode newValue) {
        super.replace(oldValue, newValue);
        if (scope == oldValue) {
            scope = newValue;
        }
        if (configurationMacros == oldValue) {
            configurationMacros = newValue;
        }
        if (includePath == oldValue) {
            includePath = newValue;
        }
        if (sysRoot == oldValue) {
            sysRoot = newValue;
        }
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    private static final int ARGINDEX_SCOPE = 1;
    private static final int ARGINDEX_NAME = 2;
    private static final int ARGINDEX_CONFIGURATIONMACROS = 3;
    private static final int ARGINDEX_INCLUDEPATH = 4;
    private static final int ARGINDEX_SYSROOT = 5;

    public static MDModule create38(long[] args, MetadataValueList md) {
        final MDModule module = new MDModule();

        module.scope = md.getNullable(args[ARGINDEX_SCOPE], module);
        module.configurationMacros = md.getNullable(args[ARGINDEX_CONFIGURATIONMACROS], module);
        module.includePath = md.getNullable(args[ARGINDEX_INCLUDEPATH], module);
        module.sysRoot = md.getNullable(args[ARGINDEX_SYSROOT], module);
        module.setName(md.getNullable(args[ARGINDEX_NAME], module));

        return module;
    }
}
