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

package com.oracle.svm.hosted.webimage.wasm.ast;

import java.util.Objects;

import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmIdFactory;

/**
 * Holds all information necessary to declare an import in a Wasm module.
 * <p>
 * Because pre-defined imports are often declared statically (without access to a {@link WasmModule}
 * or a {@link WasmIdFactory}), this class is independent of any module context. It is bound to a
 * specific context when it is used to create a
 * {@link com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId.Import}.
 */
public abstract sealed class ImportDescriptor {

    public final String module;
    public final String name;
    public final Object comment;

    protected ImportDescriptor(String module, String name, Object comment) {
        this.module = module;
        this.name = name;
        this.comment = comment;
    }

    /**
     * Returns the string used in the Wasm text format for this type of import.
     */
    public abstract String getType();

    public static final class Function extends ImportDescriptor {

        public final TypeUse typeUse;

        public Function(String module, String name, TypeUse typeUse, Object comment) {
            super(module, name, comment);
            this.typeUse = typeUse;
        }

        @Override
        public String getType() {
            return "func";
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            Function function = (Function) o;
            return Objects.equals(module, function.module) && Objects.equals(name, function.name) && Objects.equals(typeUse, function.typeUse);
        }

        @Override
        public int hashCode() {
            return Objects.hash(module, name, typeUse);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append(module).append('.').append(name);

        if (comment != null) {
            sb.append(" (").append(comment).append(")");
        }

        return sb.toString();
    }
}
