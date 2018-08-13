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

public final class MDObjCProperty extends MDName implements MDBaseNode {

    private final long line;
    private final long attribute;

    private MDBaseNode file;
    private MDBaseNode getterName;
    private MDBaseNode setterName;
    private MDBaseNode type;

    private MDObjCProperty(long line, long attribute) {
        this.line = line;
        this.attribute = attribute;

        this.file = MDVoidNode.INSTANCE;
        this.getterName = MDVoidNode.INSTANCE;
        this.setterName = MDVoidNode.INSTANCE;
        this.type = MDVoidNode.INSTANCE;
    }

    public MDBaseNode getFile() {
        return file;
    }

    public long getLine() {
        return line;
    }

    public MDBaseNode getGetterName() {
        return getterName;
    }

    public MDBaseNode getSetterName() {
        return setterName;
    }

    public long getAttribute() {
        return attribute;
    }

    public MDBaseNode getType() {
        return type;
    }

    @Override
    public void replace(MDBaseNode oldValue, MDBaseNode newValue) {
        super.replace(oldValue, newValue);
        if (file == oldValue) {
            file = newValue;
        }
        if (getterName == oldValue) {
            getterName = newValue;
        }
        if (setterName == oldValue) {
            setterName = newValue;
        }
        if (type == oldValue) {
            type = newValue;
        }
    }

    @Override
    public void accept(MetadataVisitor visitor) {
        visitor.visit(this);
    }

    private static final int ARGINDEX_NAME = 1;
    private static final int ARGINDEX_FILE = 2;
    private static final int ARGINDEX_LINE = 3;
    private static final int ARGINDEX_GETTERNAME = 4;
    private static final int ARGINDEX_SETTERNAME = 5;
    private static final int ARGINDEX_ATTRIBUTE = 6;
    private static final int ARGINDEX_TYPE = 7;

    public static MDObjCProperty create38(long[] args, MetadataValueList md) {
        final long line = args[ARGINDEX_LINE];
        final long attribute = args[ARGINDEX_ATTRIBUTE];

        final MDObjCProperty objCProperty = new MDObjCProperty(line, attribute);

        objCProperty.file = md.getNullable(args[ARGINDEX_FILE], objCProperty);
        objCProperty.getterName = md.getNullable(args[ARGINDEX_GETTERNAME], objCProperty);
        objCProperty.setterName = md.getNullable(args[ARGINDEX_SETTERNAME], objCProperty);
        objCProperty.type = md.getNullable(args[ARGINDEX_TYPE], objCProperty);
        objCProperty.setName(md.getNullable(args[ARGINDEX_NAME], objCProperty));

        return objCProperty;
    }
}
