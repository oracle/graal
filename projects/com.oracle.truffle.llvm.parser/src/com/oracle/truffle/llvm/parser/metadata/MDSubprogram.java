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

import com.oracle.truffle.llvm.parser.metadata.subtypes.MDName;

public final class MDSubprogram extends MDName implements MDBaseNode {

    private final MDReference scope;

    private final MDReference displayName;

    private final MDReference linkageName;

    private final MDReference file;

    private final long line;

    private final MDReference type;

    private final boolean isLocalToUnit;

    private final boolean isDefinedInCompileUnit;

    private final long scopeLine;

    private final MDReference containingType;

    private final long virtuality;

    private final long virtualIndex;

    private final long flags;

    private final boolean isOptimized;

    private final MDReference templateParams;

    private final MDReference declaration;

    private final MDReference variables;

    private MDReference function;

    private MDReference compileUnit;

    private MDSubprogram(MDReference scope, MDReference name, MDReference displayName, MDReference linkageName, MDReference file, long line, MDReference type, boolean isLocalToUnit,
                    boolean isDefinedInCompileUnit, long scopeLine, MDReference containingType, long virtuality, long virtualIndex, long flags, boolean isOptimized,
                    MDReference templateParams, MDReference declaration, MDReference variables, MDReference function, MDReference compileUnit) {
        super(name);
        this.scope = scope;
        this.displayName = displayName;
        this.linkageName = linkageName;
        this.file = file;
        this.line = line;
        this.type = type;
        this.isLocalToUnit = isLocalToUnit;
        this.isDefinedInCompileUnit = isDefinedInCompileUnit;
        this.scopeLine = scopeLine;
        this.containingType = containingType;
        this.virtuality = virtuality;
        this.virtualIndex = virtualIndex;
        this.flags = flags;
        this.isOptimized = isOptimized;
        this.templateParams = templateParams;
        this.declaration = declaration;
        this.variables = variables;
        this.function = function;
        this.compileUnit = compileUnit;
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    public MDReference getDisplayName() {
        return displayName;
    }

    public MDReference getLinkageName() {
        return linkageName;
    }

    public MDReference getFile() {
        return file;
    }

    public long getLine() {
        return line;
    }

    public MDReference getType() {
        return type;
    }

    public boolean isLocalToUnit() {
        return isLocalToUnit;
    }

    public boolean isDefinedInCompileUnit() {
        return isDefinedInCompileUnit;
    }

    public long getScopeLine() {
        return scopeLine;
    }

    public MDReference getContainingType() {
        return containingType;
    }

    public long getVirtuality() {
        return virtuality;
    }

    public long getVirtualIndex() {
        return virtualIndex;
    }

    public long getFlags() {
        return flags;
    }

    public boolean isOptimized() {
        return isOptimized;
    }

    public MDReference getTemplateParams() {
        return templateParams;
    }

    public MDReference getDeclaration() {
        return declaration;
    }

    public MDReference getVariables() {
        return variables;
    }

    public MDReference getScope() {
        return scope;
    }

    public void setFunction(MDReference function) {
        this.function = function;
    }

    public MDReference getFunction() {
        return function;
    }

    public MDBaseNode getCompileUnit() {
        return compileUnit;
    }

    public void setCompileUnit(MDReference compileUnit) {
        this.compileUnit = compileUnit;
    }

    @Override
    public String toString() {
        return String.format(
                        "Subprogram (scope=%s, name=%s, displayName=%s, linkageName=%s, file=%s, line=%d, type=%s, isLocalToUnit=%s, isDefinedInCompileUnit=%s, scopeLine=%d, " +
                                        "containingType=%s, virtuality=%d, virtualIndex=%d, flags=%d, isOptimized=%s, templateParams=%s, declaration=%s, variables=%s)",
                        scope, getName(), displayName, linkageName, file, line, type, isLocalToUnit, isDefinedInCompileUnit, scopeLine, containingType, virtuality, virtualIndex, flags, isOptimized,
                        templateParams, declaration, variables);
    }

    private static final int ARGINDEX_38_SCOPE = 1;
    private static final int ARGINDEX_38_NAME = 2;
    private static final int ARGINDEX_38_LINKAGENAME = 3;
    private static final int ARGINDEX_38_FILE = 4;
    private static final int ARGINDEX_38_LINE = 5;
    private static final int ARGINDEX_38_TYPE = 6;
    private static final int ARGINDEX_38_LOCALTOUNIT = 7;
    private static final int ARGINDEX_38_DEFINEDINCOMPILEUNIT = 8;
    private static final int ARGINDEX_38_SCOPELINE = 9;
    private static final int ARGINDEX_38_CONTAININGTYPE = 10;
    private static final int ARGINDEX_38_VIRTUALITY = 11;
    private static final int ARGINDEX_38_VIRTUALINDEX = 12;
    private static final int ARGINDEX_38_FLAGS = 13;
    private static final int ARGINDEX_38_OPTIMIZED = 14;
    private static final int ARGINDEX_38_FUNCTION = 15;
    private static final int ARGINDEX_38_COMPILEUNIT = 15;
    private static final int ARGINDEX_38_TEMPLATEPARAMS = 15;
    private static final int ARGINDEX_38_DECLARATION = 16;
    private static final int ARGINDEX_38_VARIABLES = 17;
    private static final int OFFSET_INDICATOR = 19;
    private static final int UNIT_INDICATOR = 0;

    public static MDSubprogram create38(long[] args, MetadataList md) {
        final int fnOffset = args.length == OFFSET_INDICATOR ? 1 : 0;

        final MDReference scope = md.getMDRefOrNullRef(args[ARGINDEX_38_SCOPE]);
        final MDReference name = md.getMDRefOrNullRef(args[ARGINDEX_38_NAME]);
        final MDReference linkageName = md.getMDRefOrNullRef(args[ARGINDEX_38_LINKAGENAME]);
        final MDReference file = md.getMDRefOrNullRef(args[ARGINDEX_38_FILE]);
        final long line = args[ARGINDEX_38_LINE];
        final MDReference type = md.getMDRefOrNullRef(args[ARGINDEX_38_TYPE]);
        final boolean localToCompileUnit = args[ARGINDEX_38_LOCALTOUNIT] == 1;
        final boolean definedInCompileUnit = args[ARGINDEX_38_DEFINEDINCOMPILEUNIT] == 1;
        final long scopeLine = args[ARGINDEX_38_SCOPELINE];
        final MDReference containingType = md.getMDRefOrNullRef(args[ARGINDEX_38_CONTAININGTYPE]);
        final long virtuality = args[ARGINDEX_38_VIRTUALITY];
        final long virtualIndex = args[ARGINDEX_38_VIRTUALINDEX];
        final long flags = args[ARGINDEX_38_FLAGS];
        final boolean optimized = args[ARGINDEX_38_OPTIMIZED] == 1;
        final MDReference templateParams = md.getMDRefOrNullRef(args[ARGINDEX_38_TEMPLATEPARAMS + fnOffset]);
        final MDReference declaration = md.getMDRefOrNullRef(args[ARGINDEX_38_DECLARATION + fnOffset]);
        final MDReference variables = md.getMDRefOrNullRef(args[ARGINDEX_38_VARIABLES + fnOffset]);

        final MDReference function;
        if (fnOffset != 0 && args[ARGINDEX_38_FUNCTION] != 0) {
            function = md.getMDRefOrNullRef(args[ARGINDEX_38_FUNCTION]);
        } else {
            function = MDReference.VOID;
        }

        final boolean hasUnit = args[UNIT_INDICATOR] >= 2;
        final MDReference unit = hasUnit ? md.getMDRefOrNullRef(args[ARGINDEX_38_COMPILEUNIT]) : MDReference.VOID;

        return new MDSubprogram(scope, name, MDReference.VOID, linkageName, file, line, type, localToCompileUnit, definedInCompileUnit, scopeLine, containingType, virtuality, virtualIndex, flags,
                        optimized, templateParams, declaration, variables, function, unit);
    }

    private static final int ARGINDEX_32_SCOPE = 2;
    private static final int ARGINDEX_32_NAME = 3;
    private static final int ARGINDEX_32_DISPLAYNAME = 4;
    private static final int ARGINDEX_32_LINKAGENAME = 5;
    private static final int ARGINDEX_32_FILE = 6;
    private static final int ARGINDEX_32_LINE = 7;
    private static final int ARGINDEX_32_TYPE = 8;
    private static final int ARGINDEX_32_LOCALTOUNIT = 9;
    private static final int ARGINDEX_32_DEFINEDINCOMPILEUNIT = 10;
    private static final int ARGINDEX_32_VIRTUALITY = 11;
    private static final int ARGINDEX_32_VIRTUALINDEX = 12;
    private static final int ARGINDEX_32_CONTAININGTYPE = 13;
    private static final int ARGINDEX_32_FLAGS = 14;
    private static final int ARGINDEX_3_OPTIMIZED = 15;
    private static final int ARGINDEX_32_FN = 16;
    private static final int ARGINDEX_32_TEMPLATEPARAMS = 17;
    private static final int ARGINDEX_32_DECLARATION = 18;
    private static final int ARGINDEX_32_VARIABLES = 19;
    private static final int ARGINDEX_32_SCOPELINE = 20;

    public static MDSubprogram create32(MDTypedValue[] args) {
        final MDReference scope = ParseUtil.getReference(args[ARGINDEX_32_SCOPE]);
        final MDReference name = ParseUtil.getReference(args[ARGINDEX_32_NAME]);
        final MDReference displayName = ParseUtil.getReference(args[ARGINDEX_32_DISPLAYNAME]);
        final MDReference linkageName = ParseUtil.getReference(args[ARGINDEX_32_LINKAGENAME]);
        final MDReference file = ParseUtil.getReference(args[ARGINDEX_32_FILE]);
        final long line = ParseUtil.asInt32(args[ARGINDEX_32_LINE]);
        final MDReference type = ParseUtil.getReference(args[ARGINDEX_32_TYPE]);
        final boolean localToCompileUnit = ParseUtil.asInt1(args[ARGINDEX_32_LOCALTOUNIT]);
        final boolean definedInCompileUnit = ParseUtil.asInt1(args[ARGINDEX_32_DEFINEDINCOMPILEUNIT]);
        final long virtuality = ParseUtil.asInt32(args[ARGINDEX_32_VIRTUALITY]);
        final long virtualIndex = ParseUtil.asInt32(args[ARGINDEX_32_VIRTUALINDEX]);
        final MDReference containingType = ParseUtil.getReference(args[ARGINDEX_32_CONTAININGTYPE]);
        final long flags = ParseUtil.asInt32(args[ARGINDEX_32_FLAGS]);
        final boolean optimized = ParseUtil.asInt1(args[ARGINDEX_3_OPTIMIZED]);
        final MDReference function = MDReference.fromSymbolRef(ParseUtil.getSymbolReference(args[ARGINDEX_32_FN]));

        final MDReference templateParams = ParseUtil.getReferenceIfPresent(args, ARGINDEX_32_TEMPLATEPARAMS);
        final MDReference declaration = ParseUtil.getReferenceIfPresent(args, ARGINDEX_32_DECLARATION);
        final MDReference variables = ParseUtil.getReferenceIfPresent(args, ARGINDEX_32_VARIABLES);
        final long scopeLine = ParseUtil.asInt64IfPresent(args, ARGINDEX_32_SCOPELINE);
        return new MDSubprogram(scope, name, displayName, linkageName, file, line, type, localToCompileUnit, definedInCompileUnit, scopeLine, containingType, virtuality, virtualIndex, flags,
                        optimized, templateParams, declaration, variables, function, MDReference.VOID);

    }

}
