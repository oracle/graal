/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.oracle.truffle.dsl.processor.operations.model;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import javax.lang.model.type.TypeMirror;

import com.oracle.truffle.dsl.processor.java.model.CodeTypeElement;
import com.oracle.truffle.dsl.processor.model.NodeData;
import com.oracle.truffle.dsl.processor.operations.model.OperationModel.CustomSignature;

public class InstructionModel implements InfoDumpable {
    public enum InstructionKind {
        BRANCH,
        BRANCH_FALSE,
        POP,
        INSTRUMENTATION_ENTER,
        INSTRUMENTATION_EXIT,
        INSTRUMENTATION_LEAVE,
        LOAD_ARGUMENT,
        LOAD_CONSTANT,
        LOAD_LOCAL,
        LOAD_LOCAL_MATERIALIZED,
        STORE_LOCAL,
        STORE_LOCAL_MATERIALIZED,
        LOAD_VARIADIC,
        MERGE_VARIADIC,
        STORE_NULL,

        RETURN,
        YIELD,
        THROW,

        CUSTOM,
        CUSTOM_QUICKENED,
        CUSTOM_SHORT_CIRCUIT,
        SUPERINSTRUCTION,
    }

    public static class InstructionField {
        public final TypeMirror type;
        public final String name;
        public final boolean needInUncached;
        public final boolean needLocationFixup;

        public InstructionField(TypeMirror type, String name, boolean needInUncached, boolean needLocationFixup) {
            this.type = type;
            this.name = name;
            this.needInUncached = needInUncached;
            this.needLocationFixup = needLocationFixup;
        }
    }

    public final int id;
    public final InstructionKind kind;
    public final String name;
    public CodeTypeElement nodeType;
    public CustomSignature signature;
    public NodeData nodeData;
    public int variadicPopCount = -1;

    public final List<InstructionField> fields = new ArrayList<>();
    public boolean continueWhen;

    public List<InstructionModel> subInstructions;

    public InstructionModel(int id, InstructionKind kind, String name) {
        this.id = id;
        this.kind = kind;
        this.name = name;
    }

    public void dump(Dumper dumper) {
        dumper.print("Instruction %s", name);
        dumper.field("kind", kind);
        if (nodeType != null) {
            dumper.field("nodeType", nodeType.getSimpleName());
        }
        dumper.field("signature", signature);
    }

    public boolean isInstrumentationOnly() {
        switch (kind) {
            case INSTRUMENTATION_ENTER:
            case INSTRUMENTATION_EXIT:
            case INSTRUMENTATION_LEAVE:
                return true;
            default:
                return false;
        }
    }

    public boolean isControlFlow() {
        switch (kind) {
            case BRANCH:
            case BRANCH_FALSE:
            case RETURN:
            case YIELD:
            case THROW:
            case CUSTOM_SHORT_CIRCUIT:
                return true;
            default:
                return false;
        }
    }

    public boolean needsUncachedData() {
        for (InstructionField field : fields) {
            if (field.needInUncached) {
                return true;
            }
        }

        return false;
    }

    public void addField(TypeMirror type, String fieldName, boolean needInUncached, boolean needLocationFixup) {
        fields.add(new InstructionField(type, fieldName, needInUncached, needLocationFixup));
    }

    public List<InstructionField> getUncachedFields() {
        return fields.stream().filter(x -> x.needInUncached).collect(Collectors.toList());
    }

    public List<InstructionField> getCachedFields() {
        return fields.stream().filter(x -> !x.needInUncached).collect(Collectors.toList());
    }

    public String getInternalName() {
        return name.replace('.', '_');
    }
}
