/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
 *
 * This code is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
 * version 2 for more details (a copy is included in the LICENSE file that
 * accompanied this code).
 *
 * You should have received a copy of the GNU General Public License version
 * 2 along with this work; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
 *
 * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
 * or visit www.oracle.com if you need additional information or have any
 * questions.
 */

package com.oracle.svm.hosted.webimage.wasmgc.ast;

import java.util.Collections;
import java.util.List;

import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;

/**
 * Definition of a Wasm struct.
 * <p>
 * Consists of a name, optional final modifier, optional supertype, and a list of fields.
 */
public class StructType extends TypeDefinition {

    public final List<Field> fields;

    public StructType(WasmId.StructType id, WasmId.StructType supertype, boolean isFinal, List<Field> fields, Object comment) {
        super(id, supertype, isFinal, comment);

        this.fields = Collections.unmodifiableList(fields);
    }

    /**
     * Field of a {@link StructType}.
     * <p>
     * Consists of a name, optional mutability modifier, and a type.
     */
    public static class Field {
        /**
         * The {@link WasmId} used to refer to this field.
         */
        public final WasmId.Field id;

        public final FieldType fieldType;

        public final Object comment;

        public Field(WasmId.Field id, FieldType fieldType, Object comment) {
            this.id = id;
            this.fieldType = fieldType;
            this.comment = comment;
        }
    }
}
