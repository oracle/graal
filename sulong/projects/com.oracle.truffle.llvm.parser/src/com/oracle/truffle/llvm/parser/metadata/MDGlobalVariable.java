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

public final class MDGlobalVariable extends MDVariable implements MDBaseNode {

    private final boolean isLocalToCompileUnit;
    private final boolean isDefinedInCompileUnit;

    private MDBaseNode displayName;
    private MDBaseNode linkageName;
    private MDBaseNode staticMemberDeclaration;
    private MDBaseNode variable;
    private MDBaseNode compileUnit;

    private MDGlobalVariable(long line, boolean isLocalToCompileUnit, boolean isDefinedInCompileUnit) {
        super(line);
        this.isLocalToCompileUnit = isLocalToCompileUnit;
        this.isDefinedInCompileUnit = isDefinedInCompileUnit;

        this.displayName = MDVoidNode.INSTANCE;
        this.linkageName = MDVoidNode.INSTANCE;
        this.staticMemberDeclaration = MDVoidNode.INSTANCE;
        this.variable = MDVoidNode.INSTANCE;
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

    public boolean isLocalToCompileUnit() {
        return isLocalToCompileUnit;
    }

    public boolean isDefinedInCompileUnit() {
        return isDefinedInCompileUnit;
    }

    public MDBaseNode getStaticMemberDeclaration() {
        return staticMemberDeclaration;
    }

    public MDBaseNode getVariable() {
        return variable;
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
        if (staticMemberDeclaration == oldValue) {
            staticMemberDeclaration = newValue;
        }
        if (variable == oldValue) {
            variable = newValue;
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
    private static final int ARGINDEX_38_LOCALTOCOMPILEUNIT = 7;
    private static final int ARGINDEX_38_DEFINEDINCOMPILEUNIT = 8;

    public static MDGlobalVariable create38(long[] args, MetadataValueList md) {

        final long version = args[0] >> 1;

        final long line = args[ARGINDEX_38_LINE];
        final boolean localToCompileUnit = args[ARGINDEX_38_LOCALTOCOMPILEUNIT] == 1;
        final boolean definedInCompileUnit = args[ARGINDEX_38_DEFINEDINCOMPILEUNIT] == 1;

        final MDGlobalVariable globalVariable = new MDGlobalVariable(line, localToCompileUnit, definedInCompileUnit);

        globalVariable.setScope(md.getNullable(args[ARGINDEX_38_SCOPE], globalVariable));
        globalVariable.setFile(md.getNullable(args[ARGINDEX_38_FILE], globalVariable));
        globalVariable.setType(md.getNullable(args[ARGINDEX_38_TYPE], globalVariable));

        final MDBaseNode name = md.getNullable(args[ARGINDEX_38_NAME], globalVariable);
        globalVariable.setName(name);
        globalVariable.displayName = name;

        if (version == 2) {
            globalVariable.staticMemberDeclaration = md.getNullable(args[9], globalVariable);
        } else {
            globalVariable.staticMemberDeclaration = md.getNullable(args[10], globalVariable);
            globalVariable.variable = md.getNullable(args[9], globalVariable);
        }

        globalVariable.linkageName = md.getNullable(args[ARGINDEX_38_LINKAGENAME], globalVariable);

        return globalVariable;
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

    public static MDGlobalVariable create32(long[] args, Metadata md) {
        final long line = ParseUtil.asInt(args, ARGINDEX_32_LINE, md);
        final boolean localToCompileUnit = ParseUtil.asBoolean(args, ARGINDEX_32_LOCALTOCOMPILEUNIT, md);
        final boolean inCompileUnit = ParseUtil.asBoolean(args, ARGINDEX_32_DEFINEDINCOMPILEUNIT, md);

        final MDGlobalVariable globalVariable = new MDGlobalVariable(line, localToCompileUnit, inCompileUnit);

        globalVariable.setScope(ParseUtil.resolveReference(args, ARGINDEX_32_SCOPE, globalVariable, md));
        globalVariable.setFile(ParseUtil.resolveReference(args, ARGINDEX_32_FILE, globalVariable, md));
        globalVariable.setType(ParseUtil.resolveReference(args, ARGINDEX_32_TYPE, globalVariable, md));
        globalVariable.setName(ParseUtil.resolveReference(args, ARGINDEX_32_NAME, globalVariable, md));

        globalVariable.displayName = ParseUtil.resolveReference(args, ARGINDEX_32_DISPLAYNAME, globalVariable, md);
        globalVariable.linkageName = ParseUtil.resolveReference(args, ARGINDEX_32_LINKAGENAME, globalVariable, md);
        globalVariable.variable = ParseUtil.resolveSymbol(args, ARGINDEX_32_VARIABLE, md);

        return globalVariable;
    }
}
