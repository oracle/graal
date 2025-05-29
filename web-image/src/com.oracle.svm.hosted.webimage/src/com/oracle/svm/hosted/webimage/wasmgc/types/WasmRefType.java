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

package com.oracle.svm.hosted.webimage.wasmgc.types;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import com.oracle.svm.hosted.webimage.wasm.ast.id.WasmId;
import com.oracle.svm.webimage.wasm.types.WasmValType;

import jdk.graal.compiler.debug.GraalError;

/**
 * Represents Wasm reference types according to the "Typed Function References" and "GC" proposals.
 * <p>
 * All references are either non-null or nullable.
 *
 * @see <a href=
 *      "https://github.com/WebAssembly/function-references/blob/main/proposals/function-references/Overview.md">Typed
 *      Function References</a>
 * @see <a href="https://github.com/WebAssembly/gc/blob/main/proposals/gc/MVP.md">GC</a>
 */
public abstract class WasmRefType implements WasmValType {

    /**
     * Cached instances of abstract types since there is only a handful of possible instances.
     */
    private static final Map<Kind, AbsHeap> nonNullTypes = new EnumMap<>(Kind.class);
    private static final Map<Kind, AbsHeap> nullableTypes = new EnumMap<>(Kind.class);

    public static final WasmRefType ANYREF = Kind.ANY.nullable();
    public static final WasmRefType FUNCREF = Kind.FUNC.nullable();
    public static final WasmRefType EXTERNREF = Kind.EXTERN.nullable();

    /**
     * Enum of all {@link AbsHeap} types. Pulled up to the superclass for convenience.
     */
    public enum Kind {
        ANY,
        EQ,
        I31,
        STRUCT,
        ARRAY,
        NONE,
        FUNC,
        NOFUNC,
        EXTERN,
        NOEXTERN;

        public AbsHeap nonNull() {
            return nonNullTypes.computeIfAbsent(this, (kind) -> new AbsHeap(false, kind));
        }

        public AbsHeap nullable() {
            return nullableTypes.computeIfAbsent(this, (kind) -> new AbsHeap(true, kind));
        }
    }

    /**
     * Whether this reference may be null.
     */
    public final boolean nullable;

    public WasmRefType(boolean nullable) {
        this.nullable = nullable;
    }

    /**
     * Get a non-null reference to the given id.
     */
    public static TypeIndex nonNull(WasmId.Type id) {
        return new TypeIndex(false, id);
    }

    /**
     * Get a nullable reference to the given id.
     */
    public static TypeIndex nullable(WasmId.Type id) {
        return new TypeIndex(true, id);
    }

    @Override
    public boolean isInt() {
        return false;
    }

    @Override
    public boolean isFloat() {
        return false;
    }

    @Override
    public boolean isRef() {
        return true;
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(nullable);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) {
            return false;
        }

        if (this == obj) {
            return true;
        }

        if (getClass() == obj.getClass()) {
            return innerEquals((WasmRefType) obj);
        }

        return false;
    }

    public boolean equalsWithoutNullability(WasmRefType other) {
        if (other == null) {
            return false;
        }

        if (this == other) {
            return true;
        }

        if (getClass() == other.getClass()) {
            return innerEqualsWithoutNullability(other);
        }

        return false;
    }

    /**
     * Checks whether the given object is equal to this, assuming the concrete classes of both
     * objects already match.
     * <p>
     * To avoid boilerplate code, this method is only called if the other object is of the same type
     * as this and is non-null.
     */
    protected final boolean innerEquals(WasmRefType other) {
        return this.nullable == other.nullable && innerEqualsWithoutNullability(other);
    }

    /**
     * Same as {@link #innerEquals(WasmRefType)} but disregards nullability.
     */
    protected abstract boolean innerEqualsWithoutNullability(WasmRefType other);

    /**
     * Get the variant of this reference with the given nullability.
     */
    public WasmRefType withNullability(boolean isNullable) {
        return isNullable ? asNullable() : asNonNull();
    }

    /**
     * Get the non-null variant of this reference.
     */
    public final WasmRefType asNonNull() {
        if (!this.nullable) {
            return this;
        }
        return constructNonNull();
    }

    /**
     * Get the nullable variant of this reference.
     */
    public WasmRefType asNullable() {
        if (this.nullable) {
            return this;
        }
        return constructNullable();
    }

    protected abstract WasmRefType constructNonNull();

    protected abstract WasmRefType constructNullable();

    public abstract boolean isFuncRef();

    /**
     * Abstract Wasm heap types.
     */
    public static final class AbsHeap extends WasmRefType {
        public final Kind kind;

        /**
         * Never call this directly, always use {@link Kind#nonNull()} and {@link Kind#nullable()}.
         */
        private AbsHeap(boolean nullable, Kind kind) {
            super(nullable);
            this.kind = kind;
        }

        @Override
        protected boolean innerEqualsWithoutNullability(WasmRefType other) {
            return this.kind == ((AbsHeap) other).kind;
        }

        @Override
        public WasmRefType constructNonNull() {
            return kind.nonNull();
        }

        @Override
        public WasmRefType constructNullable() {
            return kind.nullable();
        }

        @Override
        public boolean isFuncRef() {
            return kind == WasmRefType.Kind.FUNC;
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), kind);
        }
    }

    /**
     * Concrete Wasm heap types represented by the type index they reference.
     */
    public static final class TypeIndex extends WasmRefType {
        public final WasmId.Type id;

        private TypeIndex(boolean nullable, WasmId.Type id) {
            super(nullable);
            this.id = id;
        }

        @Override
        protected boolean innerEqualsWithoutNullability(WasmRefType other) {
            return this.id == ((TypeIndex) other).id;
        }

        @Override
        protected WasmRefType constructNonNull() {
            return id.asNonNull();
        }

        @Override
        protected WasmRefType constructNullable() {
            return id.asNullable();
        }

        @Override
        public boolean isFuncRef() {
            throw GraalError.unimplementedOverride();
        }

        @Override
        public int hashCode() {
            return Objects.hash(super.hashCode(), id);
        }
    }
}
