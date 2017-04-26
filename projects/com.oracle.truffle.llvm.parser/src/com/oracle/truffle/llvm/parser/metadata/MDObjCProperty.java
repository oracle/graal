/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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

public final class MDObjCProperty extends MDName implements MDBaseNode {

    private final MDReference file;

    private final long line;

    private final MDReference getterName;

    private final MDReference setterName;

    private final long attribute;

    private final MDReference type;

    private MDObjCProperty(MDReference name, MDReference file, long line, MDReference getterName, MDReference setterName, long attribute, MDReference type) {
        super(name);
        this.file = file;
        this.line = line;
        this.getterName = getterName;
        this.setterName = setterName;
        this.attribute = attribute;
        this.type = type;
    }

    public MDReference getFile() {
        return file;
    }

    public long getLine() {
        return line;
    }

    public MDReference getGetterName() {
        return getterName;
    }

    public MDReference getSetterName() {
        return setterName;
    }

    public long getAttribute() {
        return attribute;
    }

    public MDReference getType() {
        return type;
    }

    @Override
    public String toString() {
        return String.format("ObjCProperty (name=%s, file=%s, line=%d, getterName=%s, setterName=%s, attribute=%d, type=%s)", getName(), file, line, getterName, setterName, attribute, type);
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

    public static MDObjCProperty create38(long[] args, MetadataList md) {
        final MDReference name = md.getMDRefOrNullRef(args[ARGINDEX_NAME]);
        final MDReference file = md.getMDRefOrNullRef(args[ARGINDEX_FILE]);
        final long line = args[ARGINDEX_LINE];
        final MDReference getterName = md.getMDRefOrNullRef(args[ARGINDEX_GETTERNAME]);
        final MDReference setterName = md.getMDRefOrNullRef(args[ARGINDEX_SETTERNAME]);
        final long attribute = args[ARGINDEX_ATTRIBUTE];
        final MDReference type = md.getMDRefOrNullRef(args[ARGINDEX_TYPE]);
        return new MDObjCProperty(name, file, line, getterName, setterName, attribute, type);
    }
}
