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

import com.oracle.truffle.llvm.parser.metadata.subtypes.MDVariable;

public final class MDGlobalVariable extends MDVariable implements MDBaseNode {

    private final MDReference displayName;

    private final MDReference linkageName;

    private final boolean isLocalToCompileUnit;

    private final boolean isDefinedInCompileUnit;

    private final MDReference staticMembers;

    private final MDReference variable;

    private MDGlobalVariable(MDReference scope, MDReference name, MDReference displayName, MDReference linkageName, MDReference file, long line,
                    MDReference type, boolean isLocalToCompileUnit, boolean isDefinedInCompileUnit, MDReference staticMembers, MDReference variable) {
        super(scope, name, type, file, line);
        this.displayName = displayName;
        this.linkageName = linkageName;
        this.isLocalToCompileUnit = isLocalToCompileUnit;
        this.isDefinedInCompileUnit = isDefinedInCompileUnit;
        this.staticMembers = staticMembers;
        this.variable = variable;
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

    public boolean isLocalToCompileUnit() {
        return isLocalToCompileUnit;
    }

    public boolean isDefinedInCompileUnit() {
        return isDefinedInCompileUnit;
    }

    public MDReference getStaticMembers() {
        return staticMembers;
    }

    public MDReference getVariable() {
        return variable;
    }

    @Override
    public String toString() {
        return String.format(
                        "GlobalVariable (scope=%s, name=%s, displayName=%s, linkageName=%s, file=%s, line=%d, type=%s, isLocalToCompileUnit=%s, isDefinedInCompileUnit=%s, staticMembers=%s, variable=%s)",
                        getScope(), getName(), displayName, linkageName, getFile(), getLine(), getType(), isLocalToCompileUnit, isDefinedInCompileUnit, staticMembers, variable);
    }

    private static final int ARGINDEX_38_SCOPE = 1;
    private static final int ARGINDEX_38_NAME = 2;
    private static final int ARGINDEX_38_LINKAGENAME = 3;
    private static final int ARGINDEX_38_FILE = 4;
    private static final int ARGINDEX_38_LINE = 5;
    private static final int ARGINDEX_38_TYPE = 6;
    private static final int ARGINDEX_38_LOCALTOCOMPILEUNIT = 7;
    private static final int ARGINDEX_38_DEFINEDINCOMPILEUNIT = 8;
    private static final int ARGINDEX_38_VARIABLE = 9;
    private static final int ARGINDEX_38_STATICMEMBERS = 10;

    public static MDGlobalVariable create38(long[] args, MetadataList md) {
        final MDReference scope = md.getMDRefOrNullRef(args[ARGINDEX_38_SCOPE]);
        final MDReference name = md.getMDRefOrNullRef(args[ARGINDEX_38_NAME]);
        final MDReference linkageName = md.getMDRefOrNullRef(args[ARGINDEX_38_LINKAGENAME]);
        final MDReference file = md.getMDRefOrNullRef(args[ARGINDEX_38_FILE]);
        final long line = args[ARGINDEX_38_LINE];
        final MDReference type = md.getMDRefOrNullRef(args[ARGINDEX_38_TYPE]);
        final boolean localToCompileUnit = args[ARGINDEX_38_LOCALTOCOMPILEUNIT] == 1;
        final boolean definedInCompileUnit = args[ARGINDEX_38_DEFINEDINCOMPILEUNIT] == 1;
        final MDReference variable = md.getMDRefOrNullRef(args[ARGINDEX_38_VARIABLE]);
        final MDReference staticMembers = md.getMDRefOrNullRef(args[ARGINDEX_38_STATICMEMBERS]);
        return new MDGlobalVariable(scope, name, linkageName, linkageName, file, line, type, localToCompileUnit, definedInCompileUnit, staticMembers, variable);
    }

    private static final int ARGINDEX_32_SCOPE = 2;
    private static final int ARGINDEX_32_NAME = 3;
    private static final int ARGINDEX_32_DISPLAYNAME = 4;
    private static final int ARGINDEX_32_LINKAGENAME = 5;
    private static final int ARGINDEX_32_FILE = 6;
    private static final int ARGINDEX_32_LINE = 7;
    private static final int ARGINDEX_32_TYPE = 8;
    private static final int ARGINDEX_32_LOCALTOCOMPILEUNIT = 9;
    private static final int ARGINDEX_32_DEFINEDINCOMPILEUNIT = 10;
    private static final int ARGINDEX_32_VARIABLE = 11;

    public static MDGlobalVariable create32(MDTypedValue[] args) {
        final MDReference context = ParseUtil.getReference(args[ARGINDEX_32_SCOPE]);
        final MDReference name = ParseUtil.getReference(args[ARGINDEX_32_NAME]);
        final MDReference displayName = ParseUtil.getReference(args[ARGINDEX_32_DISPLAYNAME]);
        final MDReference linkageName = ParseUtil.getReference(args[ARGINDEX_32_LINKAGENAME]);
        final MDReference file = ParseUtil.getReference(args[ARGINDEX_32_FILE]);
        final long line = ParseUtil.asInt32(args[ARGINDEX_32_LINE]);
        final MDReference type = ParseUtil.getReference(args[ARGINDEX_32_TYPE]);
        final boolean localToCompileUnit = ParseUtil.asInt1(args[ARGINDEX_32_LOCALTOCOMPILEUNIT]);
        final boolean inCompileUnit = ParseUtil.asInt1(args[ARGINDEX_32_DEFINEDINCOMPILEUNIT]);
        final MDReference variable = MDReference.fromSymbolRef(ParseUtil.getSymbolReference(args[ARGINDEX_32_VARIABLE]));
        return new MDGlobalVariable(context, name, displayName, linkageName, file, line, type, localToCompileUnit, inCompileUnit, MDReference.VOID, variable);
    }
}
