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
package com.oracle.truffle.llvm.parser.metadata;

import com.oracle.truffle.llvm.runtime.types.DwLangNameRecord;

public final class MDCompileUnit implements MDBaseNode {

    private final DwLangNameRecord language;
    private final MDReference file;
    private final MDReference directory;
    private final MDReference producer;
    private final boolean isOptimized;
    private final MDReference flags;
    private final long runtimeVersion;
    private final MDReference splitdebugFilename;
    private final long emissionKind;
    private final MDReference enumType;
    private final MDReference retainedTypes;
    private final MDReference subprograms;
    private final MDReference globalVariables;
    private final MDReference importedEntities;
    private final MDReference macros;
    private final long dwoId;

    private MDCompileUnit(DwLangNameRecord language, MDReference file, MDReference directory, MDReference producer, boolean isOptimized, MDReference flags, long runtimeVersion,
                    MDReference splitdebugFilename, long emissionKind, MDReference enumType,
                    MDReference retainedTypes, MDReference subprograms, MDReference globalVariables, MDReference importedEntities, MDReference macros, long dwoId) {
        this.language = language;
        this.file = file;
        this.directory = directory;
        this.producer = producer;
        this.isOptimized = isOptimized;
        this.flags = flags;
        this.runtimeVersion = runtimeVersion;
        this.splitdebugFilename = splitdebugFilename;
        this.emissionKind = emissionKind;
        this.enumType = enumType;
        this.retainedTypes = retainedTypes;
        this.subprograms = subprograms;
        this.globalVariables = globalVariables;
        this.importedEntities = importedEntities;
        this.macros = macros;
        this.dwoId = dwoId;
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    public DwLangNameRecord getLanguage() {
        return language;
    }

    public MDReference getFile() {
        return file;
    }

    public MDReference getDirectory() {
        return directory;
    }

    public MDReference getProducer() {
        return producer;
    }

    public boolean isOptimized() {
        return isOptimized;
    }

    public MDReference getFlags() {
        return flags;
    }

    public long getRuntimeVersion() {
        return runtimeVersion;
    }

    public MDBaseNode getEnumType() {
        return enumType;
    }

    public MDReference getRetainedTypes() {
        return retainedTypes;
    }

    public MDReference getSubprograms() {
        return subprograms;
    }

    public MDReference getGlobalVariables() {
        return globalVariables;
    }

    public MDReference getSplitdebugFilename() {
        return splitdebugFilename;
    }

    public long getEmissionKind() {
        return emissionKind;
    }

    public MDReference getImportedEntities() {
        return importedEntities;
    }

    public MDReference getMacros() {
        return macros;
    }

    public long getDwoId() {
        return dwoId;
    }

    @Override
    public String toString() {
        return String.format(
                        "CompileUnit (language=%s, file=%s, directory=%s, producer=%s, isOptimized=%s, flags=%s, runtimeVersion=%d, splitDebugFilename=%s, emissionKind = %d, enumType=%s, " +
                                        "retainedTypes=%s, subprograms=%s, globalVariables=%s, importedEntities=%s, macros=%s, dwoId=%d)",
                        language, file, directory, producer, isOptimized, flags, runtimeVersion, splitdebugFilename, emissionKind, enumType, retainedTypes, subprograms, globalVariables,
                        importedEntities, macros, dwoId);
    }

    private static final int ARGINDEX_38_LANGUAGE = 1;
    private static final int ARGINDEX_38_FILE = 2;
    private static final int ARGINDEX_38_PRODUCER = 3;
    private static final int ARGINDEX_38_OPTIMIZED = 4;
    private static final int ARGINDEX_38_FLAGS = 5;
    private static final int ARGINDEX_38_RUNTIMEVERSION = 6;
    private static final int ARGINDEX_38_SPLITDEBUGFILENAME = 7;
    private static final int ARGINDEX_38_EMISSIONKIND = 8;
    private static final int ARGINDEX_38_ENUMTYPE = 9;
    private static final int ARGINDEX_38_RETAINEDTYPES = 10;
    private static final int ARGINDEX_38_SUBPROGRAMS = 11;
    private static final int ARGINDEX_38_GLOBALVARIABLES = 12;
    private static final int ARGINDEX_38_IMPORTEDENTITIES = 13;
    private static final int ARGINDEX_38_DWOID = 14;
    private static final int ARGINDEX_38_MACROS = 15;

    public static MDCompileUnit create38(long[] args, MetadataList md) {
        final DwLangNameRecord language = DwLangNameRecord.decode(args[ARGINDEX_38_LANGUAGE]);
        final MDReference file = md.getMDRefOrNullRef(args[ARGINDEX_38_FILE]);
        final MDReference producer = md.getMDRefOrNullRef(args[ARGINDEX_38_PRODUCER]);
        final boolean optimized = args[ARGINDEX_38_OPTIMIZED] == 1;
        final MDReference flags = md.getMDRefOrNullRef(args[ARGINDEX_38_FLAGS]);
        final long runtimeVersion = args[ARGINDEX_38_RUNTIMEVERSION];
        final MDReference splitDebugFilename = md.getMDRefOrNullRef(args[ARGINDEX_38_SPLITDEBUGFILENAME]);
        final long emissionKind = args[ARGINDEX_38_EMISSIONKIND];
        final MDReference enumType = md.getMDRefOrNullRef(args[ARGINDEX_38_ENUMTYPE]);
        final MDReference retainedTypes = md.getMDRefOrNullRef(args[ARGINDEX_38_RETAINEDTYPES]);
        final MDReference subprograms = md.getMDRefOrNullRef(args[ARGINDEX_38_SUBPROGRAMS]);
        final MDReference globalVariables = md.getMDRefOrNullRef(args[ARGINDEX_38_GLOBALVARIABLES]);
        final MDReference importedEntities = md.getMDRefOrNullRef(args[ARGINDEX_38_IMPORTEDENTITIES]);

        final MDReference macros;
        if (args.length > ARGINDEX_38_MACROS) {
            macros = md.getMDRefOrNullRef(args[ARGINDEX_38_MACROS]);
        } else {
            macros = MDReference.VOID;
        }

        final long dwoId = args.length > ARGINDEX_38_DWOID ? args[ARGINDEX_38_DWOID] : 0;

        return new MDCompileUnit(language, file, MDReference.VOID, producer, optimized, flags, runtimeVersion, splitDebugFilename, emissionKind, enumType, retainedTypes, subprograms, globalVariables,
                        importedEntities, macros, dwoId);
    }

    private static final int ARGINDEX_32_LANGUAGE = 2;
    private static final int ARGINDEX_32_FILE = 3;
    private static final int ARGINDEX_32_DIRECTORY = 4;
    private static final int ARGINDEX_32_PRODUCER = 5;
    private static final int ARGINDEX_32_OPTIMIZED = 7;
    private static final int ARGINDEX_32_FLAGS = 8;
    private static final int ARGINDEX_32_RUNTIMEVERSION = 9;
    private static final int ARGINDEX_32_ENUMTYPE = 10;
    private static final int ARGINDEX_32_RETAINEDTYPES = 11;
    private static final int ARGINDEX_32_SUBPROGRAMS = 12;
    private static final int ARGINDEX_32_GLOBALVARIABLES = 13;

    public static MDCompileUnit create32(MDTypedValue[] args) {
        // TODO can splitdebugfilename be created from file and directory?
        final DwLangNameRecord language = DwLangNameRecord.decode(ParseUtil.asInt32(args[ARGINDEX_32_LANGUAGE]));
        final MDReference file = ParseUtil.getReference(args[ARGINDEX_32_FILE]);
        final MDReference directory = ParseUtil.getReference(args[ARGINDEX_32_DIRECTORY]);
        final MDReference producer = ParseUtil.getReference(args[ARGINDEX_32_PRODUCER]);
        // final boolean isMainCompileUnit = ParseUtil.asInt1(args[6]);
        final boolean optimized = ParseUtil.asInt1(args[ARGINDEX_32_OPTIMIZED]);
        final MDReference flags = ParseUtil.getReference(args[ARGINDEX_32_FLAGS]);
        final long runtimeVersion = ParseUtil.asInt32(args[ARGINDEX_32_RUNTIMEVERSION]);

        final MDReference enumType = ParseUtil.getReferenceIfPresent(args, ARGINDEX_32_ENUMTYPE);
        final MDReference retainedTypes = ParseUtil.getReferenceIfPresent(args, ARGINDEX_32_RETAINEDTYPES);
        final MDReference subprograms = ParseUtil.getReferenceIfPresent(args, ARGINDEX_32_SUBPROGRAMS);
        final MDReference globalVariables = ParseUtil.getReferenceIfPresent(args, ARGINDEX_32_GLOBALVARIABLES);
        return new MDCompileUnit(language, file, directory, producer, optimized, flags, runtimeVersion, MDReference.VOID, -1L, enumType, retainedTypes, subprograms, globalVariables,
                        MDReference.VOID, MDReference.VOID, -1L);
    }
}
