/*
 * Copyright (c) 2016, 2018, Oracle and/or its affiliates.
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

import com.oracle.truffle.llvm.parser.listeners.Metadata;
import com.oracle.truffle.llvm.runtime.types.DwLangNameRecord;

public final class MDCompileUnit implements MDBaseNode {

    private final DwLangNameRecord language;
    private final boolean isOptimized;
    private final long runtimeVersion;
    private final long emissionKind;
    private final long dwoId;

    private MDBaseNode file;
    private MDBaseNode directory;
    private MDBaseNode producer;
    private MDBaseNode flags;
    private MDBaseNode splitdebugFilename;
    private MDBaseNode enums;
    private MDBaseNode retainedTypes;
    private MDBaseNode subprograms;
    private MDBaseNode globalVariables;
    private MDBaseNode importedEntities;
    private MDBaseNode macros;

    private MDCompileUnit(DwLangNameRecord language, boolean isOptimized, long runtimeVersion, long emissionKind, long dwoId) {
        this.language = language;
        this.isOptimized = isOptimized;
        this.runtimeVersion = runtimeVersion;
        this.emissionKind = emissionKind;
        this.dwoId = dwoId;

        this.file = MDVoidNode.INSTANCE;
        this.directory = MDVoidNode.INSTANCE;
        this.producer = MDVoidNode.INSTANCE;
        this.flags = MDVoidNode.INSTANCE;
        this.splitdebugFilename = MDVoidNode.INSTANCE;
        this.enums = MDVoidNode.INSTANCE;
        this.retainedTypes = MDVoidNode.INSTANCE;
        this.subprograms = MDVoidNode.INSTANCE;
        this.globalVariables = MDVoidNode.INSTANCE;
        this.importedEntities = MDVoidNode.INSTANCE;
        this.macros = MDVoidNode.INSTANCE;
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    public DwLangNameRecord getLanguage() {
        return language;
    }

    public MDBaseNode getFile() {
        return file;
    }

    public MDBaseNode getDirectory() {
        return directory;
    }

    public MDBaseNode getProducer() {
        return producer;
    }

    public boolean isOptimized() {
        return isOptimized;
    }

    public MDBaseNode getFlags() {
        return flags;
    }

    public long getRuntimeVersion() {
        return runtimeVersion;
    }

    public MDBaseNode getEnums() {
        return enums;
    }

    public MDBaseNode getRetainedTypes() {
        return retainedTypes;
    }

    public MDBaseNode getSubprograms() {
        return subprograms;
    }

    public MDBaseNode getGlobalVariables() {
        return globalVariables;
    }

    public MDBaseNode getSplitdebugFilename() {
        return splitdebugFilename;
    }

    public long getEmissionKind() {
        return emissionKind;
    }

    public MDBaseNode getImportedEntities() {
        return importedEntities;
    }

    public MDBaseNode getMacros() {
        return macros;
    }

    public long getDwoId() {
        return dwoId;
    }

    @Override
    public void replace(MDBaseNode oldValue, MDBaseNode newValue) {
        if (file == oldValue) {
            file = newValue;
        }
        if (directory == oldValue) {
            directory = newValue;
        }
        if (producer == oldValue) {
            producer = newValue;
        }
        if (flags == oldValue) {
            flags = newValue;
        }
        if (splitdebugFilename == oldValue) {
            splitdebugFilename = newValue;
        }
        if (enums == oldValue) {
            enums = newValue;
        }
        if (retainedTypes == oldValue) {
            retainedTypes = newValue;
        }
        if (subprograms == oldValue) {
            subprograms = newValue;
        }
        if (globalVariables == oldValue) {
            globalVariables = newValue;
        }
        if (importedEntities == oldValue) {
            importedEntities = newValue;
        }
        if (macros == oldValue) {
            macros = newValue;
        }
    }

    private static final int ARGINDEX_38_LANGUAGE = 1;
    private static final int ARGINDEX_38_FILE = 2;
    private static final int ARGINDEX_38_PRODUCER = 3;
    private static final int ARGINDEX_38_OPTIMIZED = 4;
    private static final int ARGINDEX_38_FLAGS = 5;
    private static final int ARGINDEX_38_RUNTIMEVERSION = 6;
    private static final int ARGINDEX_38_SPLITDEBUGFILENAME = 7;
    private static final int ARGINDEX_38_EMISSIONKIND = 8;
    private static final int ARGINDEX_38_ENUMS = 9;
    private static final int ARGINDEX_38_RETAINEDTYPES = 10;
    private static final int ARGINDEX_38_SUBPROGRAMS = 11;
    private static final int ARGINDEX_38_GLOBALVARIABLES = 12;
    private static final int ARGINDEX_38_IMPORTEDENTITIES = 13;
    private static final int ARGINDEX_38_DWOID = 14;
    private static final int ARGINDEX_38_MACROS = 15;

    public static MDCompileUnit create38(long[] args, MetadataValueList md) {
        final DwLangNameRecord language = DwLangNameRecord.decode(args[ARGINDEX_38_LANGUAGE]);
        final boolean optimized = args[ARGINDEX_38_OPTIMIZED] == 1;
        final long runtimeVersion = args[ARGINDEX_38_RUNTIMEVERSION];
        final long emissionKind = args[ARGINDEX_38_EMISSIONKIND];
        final long dwoId = args.length > ARGINDEX_38_DWOID ? args[ARGINDEX_38_DWOID] : 0;

        final MDCompileUnit compileUnit = new MDCompileUnit(language, optimized, runtimeVersion, emissionKind, dwoId);
        compileUnit.file = md.getNullable(args[ARGINDEX_38_FILE], compileUnit);
        compileUnit.producer = md.getNullable(args[ARGINDEX_38_PRODUCER], compileUnit);
        compileUnit.flags = md.getNullable(args[ARGINDEX_38_FLAGS], compileUnit);
        compileUnit.splitdebugFilename = md.getNullable(args[ARGINDEX_38_SPLITDEBUGFILENAME], compileUnit);
        compileUnit.enums = md.getNullable(args[ARGINDEX_38_ENUMS], compileUnit);
        compileUnit.retainedTypes = md.getNullable(args[ARGINDEX_38_RETAINEDTYPES], compileUnit);
        compileUnit.subprograms = md.getNullable(args[ARGINDEX_38_SUBPROGRAMS], compileUnit);
        compileUnit.globalVariables = md.getNullable(args[ARGINDEX_38_GLOBALVARIABLES], compileUnit);
        compileUnit.importedEntities = md.getNullable(args[ARGINDEX_38_IMPORTEDENTITIES], compileUnit);

        if (args.length > ARGINDEX_38_MACROS) {
            compileUnit.macros = md.getNullable(args[ARGINDEX_38_MACROS], compileUnit);
        }
        return compileUnit;
    }

    private static final int ARGINDEX_32_LANGUAGE = 2;
    private static final int ARGINDEX_32_FILE = 3;
    private static final int ARGINDEX_32_DIRECTORY = 4;
    private static final int ARGINDEX_32_PRODUCER = 5;
    private static final int ARGINDEX_32_OPTIMIZED = 7;
    private static final int ARGINDEX_32_FLAGS = 8;
    private static final int ARGINDEX_32_RUNTIMEVERSION = 9;
    private static final int ARGINDEX_32_ENUMS = 10;
    private static final int ARGINDEX_32_RETAINEDTYPES = 11;
    private static final int ARGINDEX_32_SUBPROGRAMS = 12;
    private static final int ARGINDEX_32_GLOBALVARIABLES = 13;

    public static MDCompileUnit create32(long[] args, Metadata md) {
        // TODO can splitdebugfilename be created from file and directory?
        final DwLangNameRecord language = DwLangNameRecord.decode(ParseUtil.asInt(args, ARGINDEX_32_LANGUAGE, md));
        final boolean optimized = ParseUtil.asBoolean(args, ARGINDEX_32_OPTIMIZED, md);
        final long runtimeVersion = ParseUtil.asInt(args, ARGINDEX_32_RUNTIMEVERSION, md);

        final MDCompileUnit compileUnit = new MDCompileUnit(language, optimized, runtimeVersion, -1L, -1L);

        compileUnit.file = ParseUtil.resolveReference(args, ARGINDEX_32_FILE, compileUnit, md);
        compileUnit.directory = ParseUtil.resolveReference(args, ARGINDEX_32_DIRECTORY, compileUnit, md);
        compileUnit.producer = ParseUtil.resolveReference(args, ARGINDEX_32_PRODUCER, compileUnit, md);
        compileUnit.flags = ParseUtil.resolveReference(args, ARGINDEX_32_FLAGS, compileUnit, md);

        compileUnit.enums = ParseUtil.resolveReference(args, ARGINDEX_32_ENUMS, compileUnit, md);
        compileUnit.retainedTypes = ParseUtil.resolveReference(args, ARGINDEX_32_RETAINEDTYPES, compileUnit, md);
        compileUnit.subprograms = ParseUtil.resolveReference(args, ARGINDEX_32_SUBPROGRAMS, compileUnit, md);
        compileUnit.globalVariables = ParseUtil.resolveReference(args, ARGINDEX_32_GLOBALVARIABLES, compileUnit, md);
        // final boolean isMainCompileUnit = ParseUtil.asInt1(args[6]);

        return compileUnit;
    }
}
