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

import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;

/**
 * Represents a WASM export.
 */
public class Export extends ModuleField {
    public enum Type {
        FUNC("func"),
        TABLE("table"),
        MEM("memory"),
        GLOBAL("global"),
        TAG("tag");

        /**
         * The string used in the WASM text format.
         */
        public final String name;

        Type(String name) {
            this.name = name;
        }
    }

    public final Type type;
    public final String name;
    protected WasmId id;

    public Export(Type type, WasmId id, String name, Object comment) {
        super(comment);
        this.type = type;
        this.name = name;
        this.id = id;

        assert (switch (type) {
            case FUNC -> WasmId.Func.class;
            case TABLE -> WasmId.Table.class;
            case MEM -> WasmId.Memory.class;
            case GLOBAL -> WasmId.Global.class;
            case TAG -> WasmId.Tag.class;
        }).isAssignableFrom(id.getClass()) : "type=" + type + ", id=" + id;
    }

    public static Export forFunction(WasmId.Func id, String name, Object comment) {
        return new Export(Type.FUNC, id, name, comment);
    }

    public WasmId getId() {
        return id;
    }

    public WasmId.Func getFuncId() {
        assert type == Type.FUNC;
        return (WasmId.Func) id;
    }

    public WasmId.Table getTableId() {
        assert type == Type.TABLE;
        return (WasmId.Table) id;
    }

    public WasmId.Memory getMemoryId() {
        assert type == Type.MEM;
        return (WasmId.Memory) id;
    }

    public WasmId.Global getGlobalId() {
        assert type == Type.GLOBAL;
        return (WasmId.Global) id;
    }

    public WasmId.Tag getTagId() {
        assert type == Type.TAG;
        return (WasmId.Tag) id;
    }

    @Override
    public String toString() {
        return String.format("(export \"%s\" (%s %s))", name, type.name, id);
    }
}
