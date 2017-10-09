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

public final class MDSubprogram extends MDName implements MDBaseNode {

    private final long line;
    private final boolean isLocalToUnit;
    private final boolean isDefinedInCompileUnit;
    private final long scopeLine;
    private final long virtuality;
    private final long virtualIndex;
    private final long flags;
    private final boolean isOptimized;

    private MDBaseNode scope;
    private MDBaseNode displayName;
    private MDBaseNode linkageName;
    private MDBaseNode file;
    private MDBaseNode type;
    private MDBaseNode containingType;
    private MDBaseNode templateParams;
    private MDBaseNode declaration;
    private MDBaseNode variables;
    private MDBaseNode function;
    private MDBaseNode compileUnit;

    private MDSubprogram(long line, boolean isLocalToUnit, boolean isDefinedInCompileUnit, long scopeLine, long virtuality, long virtualIndex, long flags, boolean isOptimized) {
        this.line = line;
        this.isLocalToUnit = isLocalToUnit;
        this.isDefinedInCompileUnit = isDefinedInCompileUnit;
        this.scopeLine = scopeLine;
        this.virtuality = virtuality;
        this.virtualIndex = virtualIndex;
        this.flags = flags;
        this.isOptimized = isOptimized;

        this.scope = MDReference.VOID;
        this.displayName = MDReference.VOID;
        this.linkageName = MDReference.VOID;
        this.file = MDReference.VOID;
        this.type = MDReference.VOID;
        this.containingType = MDReference.VOID;
        this.templateParams = MDReference.VOID;
        this.declaration = MDReference.VOID;
        this.variables = MDReference.VOID;
        this.function = MDReference.VOID;
        this.compileUnit = MDReference.VOID;
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    public MDBaseNode getDisplayName() {
        return displayName;
    }

    public MDBaseNode getLinkageName() {
        return linkageName;
    }

    public MDBaseNode getFile() {
        return file;
    }

    public long getLine() {
        return line;
    }

    public MDBaseNode getType() {
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

    public MDBaseNode getContainingType() {
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

    public MDBaseNode getTemplateParams() {
        return templateParams;
    }

    public MDBaseNode getDeclaration() {
        return declaration;
    }

    public MDBaseNode getVariables() {
        return variables;
    }

    public MDBaseNode getScope() {
        return scope;
    }

    public void setFunction(MDBaseNode function) {
        this.function = function;
    }

    public MDBaseNode getFunction() {
        return function;
    }

    public MDBaseNode getCompileUnit() {
        return compileUnit;
    }

    public void setCompileUnit(MDBaseNode compileUnit) {
        this.compileUnit = compileUnit;
    }

    @Override
    public void replace(MDBaseNode oldValue, MDBaseNode newValue) {
        super.replace(oldValue, newValue);
        if (scope == oldValue) {
            scope = newValue;
        }
        if (displayName == oldValue) {
            displayName = newValue;
        }
        if (linkageName == oldValue) {
            linkageName = newValue;
        }
        if (file == oldValue) {
            file = newValue;
        }
        if (type == oldValue) {
            type = newValue;
        }
        if (containingType == oldValue) {
            containingType = newValue;
        }
        if (templateParams == oldValue) {
            templateParams = newValue;
        }
        if (declaration == oldValue) {
            declaration = newValue;
        }
        if (variables == oldValue) {
            variables = newValue;
        }
        if (function == oldValue) {
            function = newValue;
        }
        if (compileUnit == oldValue) {
            compileUnit = newValue;
        }
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

    public static MDSubprogram create38(long[] args, MetadataValueList md) {
        final int fnOffset = args.length == OFFSET_INDICATOR ? 1 : 0;
        final long line = args[ARGINDEX_38_LINE];
        final boolean localToCompileUnit = args[ARGINDEX_38_LOCALTOUNIT] == 1;
        final boolean definedInCompileUnit = args[ARGINDEX_38_DEFINEDINCOMPILEUNIT] == 1;
        final long scopeLine = args[ARGINDEX_38_SCOPELINE];
        final long virtuality = args[ARGINDEX_38_VIRTUALITY];
        final long virtualIndex = args[ARGINDEX_38_VIRTUALINDEX];
        final long flags = args[ARGINDEX_38_FLAGS];
        final boolean optimized = args[ARGINDEX_38_OPTIMIZED] == 1;

        final MDSubprogram subprogram = new MDSubprogram(line, localToCompileUnit, definedInCompileUnit, scopeLine, virtuality, virtualIndex, flags, optimized);

        subprogram.scope = md.getNullable(args[ARGINDEX_38_SCOPE], subprogram);
        subprogram.setName(md.getNullable(args[ARGINDEX_38_NAME], subprogram));
        subprogram.linkageName = md.getNullable(args[ARGINDEX_38_LINKAGENAME], subprogram);
        subprogram.file = md.getNullable(args[ARGINDEX_38_FILE], subprogram);
        subprogram.type = md.getNullable(args[ARGINDEX_38_TYPE], subprogram);
        subprogram.containingType = md.getNullable(args[ARGINDEX_38_CONTAININGTYPE], subprogram);
        subprogram.templateParams = md.getNullable(args[ARGINDEX_38_TEMPLATEPARAMS + fnOffset], subprogram);
        subprogram.declaration = md.getNullable(args[ARGINDEX_38_DECLARATION + fnOffset], subprogram);
        subprogram.variables = md.getNullable(args[ARGINDEX_38_VARIABLES + fnOffset], subprogram);

        if (fnOffset != 0 && args[ARGINDEX_38_FUNCTION] != 0) {
            subprogram.function = md.getNullable(args[ARGINDEX_38_FUNCTION], subprogram);
        }

        if (args[UNIT_INDICATOR] >= 2) {
            subprogram.compileUnit = md.getNullable(args[ARGINDEX_38_COMPILEUNIT], subprogram);
        }

        return subprogram;
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

    public static MDSubprogram create32(MDTypedValue[] args, MetadataValueList md) {
        final long line = ParseUtil.asInt32(args[ARGINDEX_32_LINE]);
        final boolean localToCompileUnit = ParseUtil.asInt1(args[ARGINDEX_32_LOCALTOUNIT]);
        final boolean definedInCompileUnit = ParseUtil.asInt1(args[ARGINDEX_32_DEFINEDINCOMPILEUNIT]);
        final long virtuality = ParseUtil.asInt32(args[ARGINDEX_32_VIRTUALITY]);
        final long virtualIndex = ParseUtil.asInt32(args[ARGINDEX_32_VIRTUALINDEX]);
        final long flags = ParseUtil.asInt32(args[ARGINDEX_32_FLAGS]);
        final boolean optimized = ParseUtil.asInt1(args[ARGINDEX_3_OPTIMIZED]);
        final long scopeLine = ParseUtil.asInt64IfPresent(args, ARGINDEX_32_SCOPELINE);

        final MDSubprogram subprogram = new MDSubprogram(line, localToCompileUnit, definedInCompileUnit, scopeLine, virtuality, virtualIndex, flags, optimized);

        subprogram.scope = ParseUtil.resolveReference(args[ARGINDEX_32_SCOPE], subprogram, md);
        subprogram.setName(ParseUtil.resolveReference(args[ARGINDEX_32_NAME], subprogram, md));
        subprogram.displayName = ParseUtil.resolveReference(args[ARGINDEX_32_DISPLAYNAME], subprogram, md);
        subprogram.linkageName = ParseUtil.resolveReference(args[ARGINDEX_32_LINKAGENAME], subprogram, md);
        subprogram.file = ParseUtil.resolveReference(args[ARGINDEX_32_FILE], subprogram, md);
        subprogram.type = ParseUtil.resolveReference(args[ARGINDEX_32_TYPE], subprogram, md);
        subprogram.containingType = ParseUtil.resolveReference(args[ARGINDEX_32_CONTAININGTYPE], subprogram, md);
        subprogram.templateParams = ParseUtil.resolveReferenceIfPresent(args, ARGINDEX_32_TEMPLATEPARAMS, subprogram, md);
        subprogram.declaration = ParseUtil.resolveReferenceIfPresent(args, ARGINDEX_32_DECLARATION, subprogram, md);
        subprogram.variables = ParseUtil.resolveReferenceIfPresent(args, ARGINDEX_32_VARIABLES, subprogram, md);

        subprogram.function = MDValue.createFromSymbolReference(args[ARGINDEX_32_FN]);

        return subprogram;

    }

}
