/*
 * Copyright (c) 2016, 2019, Oracle and/or its affiliates.
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

public final class MDSubprogram extends MDNamedLocation implements MDBaseNode {

    private final boolean isLocalToUnit;
    private final boolean isDefinedInCompileUnit;
    private final long scopeLine;
    private final long virtuality;
    private final long virtualIndex;
    private final long flags;
    private final boolean isOptimized;

    private MDBaseNode displayName;
    private MDBaseNode linkageName;
    private MDBaseNode type;
    private MDBaseNode containingType;
    private MDBaseNode templateParams;
    private MDBaseNode declaration;
    private MDBaseNode function;
    private MDBaseNode compileUnit;

    private MDSubprogram(long line, boolean isLocalToUnit, boolean isDefinedInCompileUnit, long scopeLine, long virtuality, long virtualIndex, long flags, boolean isOptimized) {
        super(line);

        this.isLocalToUnit = isLocalToUnit;
        this.isDefinedInCompileUnit = isDefinedInCompileUnit;
        this.scopeLine = scopeLine;
        this.virtuality = virtuality;
        this.virtualIndex = virtualIndex;
        this.flags = flags;
        this.isOptimized = isOptimized;

        this.displayName = MDVoidNode.INSTANCE;
        this.linkageName = MDVoidNode.INSTANCE;
        this.type = MDVoidNode.INSTANCE;
        this.containingType = MDVoidNode.INSTANCE;
        this.templateParams = MDVoidNode.INSTANCE;
        this.declaration = MDVoidNode.INSTANCE;
        this.function = MDVoidNode.INSTANCE;
        this.compileUnit = MDVoidNode.INSTANCE;
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
        if (displayName == oldValue) {
            displayName = newValue;
        }
        if (linkageName == oldValue) {
            linkageName = newValue;
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
        if (function == oldValue) {
            function = newValue;
        }
        if (compileUnit == oldValue) {
            compileUnit = newValue;
        }
    }

    public static MDSubprogram createNewFormat(long[] args, MetadataValueList md) {
        final boolean localToCompileUnit;
        final boolean definedInCompileUnit;
        final boolean optimized;
        final long virtuality;

        final boolean hasDistinctFlags = (args[0] & 4) != 0;
        if (hasDistinctFlags) {
            // llvm introduces a new format to store these
            final long newFormatFlags = args[9];
            localToCompileUnit = (newFormatFlags & (1 << 2)) != 0;
            definedInCompileUnit = (newFormatFlags & (1 << 3)) != 0;
            optimized = (newFormatFlags & (1 << 4)) != 0;
            virtuality = newFormatFlags & 0b11;
        } else {
            localToCompileUnit = args[7] == 1;
            definedInCompileUnit = args[8] == 1;
            optimized = args[14] == 1;
            virtuality = args[11];
        }

        final boolean hasUnit = (args[0] & 2) != 0;
        boolean hasFunction = false;

        // these are literally the same names as in llvm
        int offsetA = 0;
        int offsetB = 0;
        if (!hasDistinctFlags) {
            offsetA = 2;
            offsetB = 2;
            if (args.length != 19) {
                hasFunction = !hasUnit;
                offsetB++;
            }
        }

        final long line = args[5];
        final long scopeLine = args[7 + offsetA];
        final long virtualIndex = args[10 + offsetA];
        final long flags = args[11 + offsetA];

        final MDSubprogram subprogram = new MDSubprogram(line, localToCompileUnit, definedInCompileUnit, scopeLine, virtuality, virtualIndex, flags, optimized);

        subprogram.setScope(md.getNullable(args[1], subprogram));
        subprogram.setName(md.getNullable(args[2], subprogram));
        subprogram.linkageName = md.getNullable(args[3], subprogram);
        subprogram.setFile(md.getNullable(args[4], subprogram));
        subprogram.type = md.getNullable(args[6], subprogram);
        subprogram.containingType = md.getNullable(args[8 + offsetA], subprogram);
        subprogram.templateParams = md.getNullable(args[13 + offsetB], subprogram);
        subprogram.declaration = md.getNullable(args[14 + offsetB], subprogram);

        if (hasUnit) {
            subprogram.compileUnit = md.getNullable(args[12 + offsetB], subprogram);
        }

        if (hasFunction) {
            subprogram.function = md.getNullable(args[12 + offsetB], subprogram);
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
    private static final int ARGINDEX_32_SCOPELINE = 20;

    public static MDSubprogram create32(long[] args, Metadata md) {
        final long line = ParseUtil.asInt(args, ARGINDEX_32_LINE, md);
        final boolean localToCompileUnit = ParseUtil.asBoolean(args, ARGINDEX_32_LOCALTOUNIT, md);
        final boolean definedInCompileUnit = ParseUtil.asBoolean(args, ARGINDEX_32_DEFINEDINCOMPILEUNIT, md);
        final long virtuality = ParseUtil.asInt(args, ARGINDEX_32_VIRTUALITY, md);
        final long virtualIndex = ParseUtil.asInt(args, ARGINDEX_32_VIRTUALINDEX, md);
        final long flags = ParseUtil.asInt(args, ARGINDEX_32_FLAGS, md);
        final boolean optimized = ParseUtil.asBoolean(args, ARGINDEX_3_OPTIMIZED, md);
        final long scopeLine = ParseUtil.asLong(args, ARGINDEX_32_SCOPELINE, md);

        final MDSubprogram subprogram = new MDSubprogram(line, localToCompileUnit, definedInCompileUnit, scopeLine, virtuality, virtualIndex, flags, optimized);

        subprogram.setScope(ParseUtil.resolveReference(args, ARGINDEX_32_SCOPE, subprogram, md));
        subprogram.setName(ParseUtil.resolveReference(args, ARGINDEX_32_NAME, subprogram, md));
        subprogram.displayName = ParseUtil.resolveReference(args, ARGINDEX_32_DISPLAYNAME, subprogram, md);
        subprogram.linkageName = ParseUtil.resolveReference(args, ARGINDEX_32_LINKAGENAME, subprogram, md);
        subprogram.setFile(ParseUtil.resolveReference(args, ARGINDEX_32_FILE, subprogram, md));
        subprogram.type = ParseUtil.resolveReference(args, ARGINDEX_32_TYPE, subprogram, md);
        subprogram.containingType = ParseUtil.resolveReference(args, ARGINDEX_32_CONTAININGTYPE, subprogram, md);
        subprogram.templateParams = ParseUtil.resolveReference(args, ARGINDEX_32_TEMPLATEPARAMS, subprogram, md);
        subprogram.declaration = ParseUtil.resolveReference(args, ARGINDEX_32_DECLARATION, subprogram, md);

        subprogram.function = ParseUtil.resolveSymbol(args, ARGINDEX_32_FN, md);

        return subprogram;

    }
}
