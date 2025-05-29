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

package com.oracle.svm.hosted.webimage.wasm.ast.id;

import java.util.concurrent.atomic.AtomicInteger;

import com.oracle.svm.hosted.webimage.wasm.ast.ImportDescriptor;
import com.oracle.svm.hosted.webimage.wasm.ast.Instruction;
import com.oracle.svm.hosted.webimage.wasm.ast.TypeUse;
import com.oracle.svm.hosted.webimage.wasmgc.types.WasmRefType;
import com.oracle.svm.util.ClassUtil;
import com.oracle.svm.webimage.wasm.types.WasmValType;

import jdk.graal.compiler.core.common.NumUtil;

/**
 * Immutable value for named indices in WASM's text format.
 * <p>
 * An id is used to refer to a type, function, table, memory, global, local, label, etc. by name
 * instead of a number.
 * <p>
 * Ids are strongly typed and direct subclasses of {@link WasmId} exist for each kind of id. If
 * possible (to avoid unnecessary class casts) fields and variables holding ids should not be
 * declared as simply {@link WasmId}, but as one of its subtypes.
 * <p>
 * During lowering, ids start out unresolved/symbolic and use some object as an identifier (e.g. a
 * method handle). Later, all these are resolved using a resolver context to generate valid,
 * non-conflicting name.
 * <p>
 * Ref: https://webassembly.github.io/spec/core/text/modules.html#indices
 */
public abstract sealed class WasmId {

    protected String resolvedName = null;

    public void resolve(ResolverContext ctxt) {
        assert !isResolved() : "Id already resolved: " + this;
        String name = doResolve(ctxt);
        assert name != null;
        resolvedName = name;
    }

    public boolean isResolved() {
        return resolvedName != null;
    }

    public String getName() {
        assert isResolved() : "Id not yet resolved: " + this;
        return resolvedName;
    }

    protected abstract String doResolve(ResolverContext ctxt);

    @Override
    public String toString() {
        String inner = toInnerString();
        String name = ClassUtil.getUnqualifiedName(this.getClass());
        return name + "{" + (isResolved() ? getName() + ", " : "") + inner + "}";
    }

    protected String toInnerString() {
        return Integer.toHexString(hashCode());
    }

    /**
     * Marker interface for ids that represent an import.
     * <p>
     * Imports are not a general id category because you can't refer to an import (you can refer to
     * a function, table, global, or memory, which may or may not be an import). This is why imports
     * are grouped together through this interface.
     *
     * @param <T> The kind of import this id represents
     */
    public sealed interface Import<T extends ImportDescriptor> {
        WasmId getId();

        T getDescriptor();
    }

    public abstract static sealed class Type extends WasmId {
        /**
         * Creates a nullable {@link WasmRefType type reference} to this type.
         */
        public WasmRefType asNullable() {
            return WasmRefType.nullable(this);
        }

        /**
         * Creates a non-null {@link WasmRefType type reference} to this type.
         */
        public WasmRefType asNonNull() {
            return WasmRefType.nonNull(this);
        }
    }

    /**
     * Id to refer to function types.
     */
    public abstract static non-sealed class FuncType extends Type {
    }

    public abstract static non-sealed class StructType extends Type {
    }

    public abstract static non-sealed class ArrayType extends Type {
    }

    public abstract static non-sealed class Func extends WasmId {
    }

    /**
     * Id to refer to a function import.
     */
    public static non-sealed class FunctionImport extends Func implements Import<ImportDescriptor.Function> {

        public final ImportDescriptor.Function descriptor;

        protected FunctionImport(ImportDescriptor.Function descriptor) {
            this.descriptor = descriptor;
        }

        @Override
        protected String doResolve(ResolverContext ctxt) {
            return descriptor.module + "_" + descriptor.name;
        }

        @Override
        public String toInnerString() {
            return descriptor.toString();
        }

        @Override
        public FunctionImport getId() {
            return this;
        }

        @Override
        public ImportDescriptor.Function getDescriptor() {
            return descriptor;
        }
    }

    public static non-sealed class Table extends WasmId {

        // TODO GR-41720 Move state into ResolverContext
        static AtomicInteger num = new AtomicInteger(0);

        @Override
        protected String doResolve(ResolverContext ctxt) {
            return "table" + num.getAndIncrement();
        }
    }

    public static non-sealed class Memory extends WasmId {

        public final int num;

        public Memory(int num) {
            assert NumUtil.assertNonNegativeInt(num);
            this.num = num;
        }

        @Override
        protected String doResolve(ResolverContext ctxt) {
            return "mem" + num;
        }

        @Override
        protected String toInnerString() {
            return String.valueOf(num);
        }
    }

    /**
     * Abstract id for local and global variables.
     */
    public abstract static sealed class Variable extends WasmId {

        /**
         * The type of the variable.
         */
        private final WasmValType variableType;

        protected Variable(WasmValType variableType) {
            assert variableType != null;
            this.variableType = variableType;
        }

        public final WasmValType getVariableType() {
            return variableType;
        }

        /**
         * Create instruction that reads the variable referenced by this id.
         */
        public abstract Instruction getter();

        /**
         * Create instruction that sets the variable referenced by this id to the given value.
         */
        public abstract Instruction setter(Instruction value);
    }

    public static non-sealed class Global extends WasmId.Variable {

        public final String name;

        public Global(WasmValType variableType, String name) {
            super(variableType);
            this.name = name;
        }

        @Override
        public Instruction.GlobalGet getter() {
            return new Instruction.GlobalGet(this);
        }

        @Override
        public Instruction.GlobalSet setter(Instruction value) {
            return new Instruction.GlobalSet(this, value);
        }

        @Override
        protected String doResolve(ResolverContext ctxt) {
            return name;
        }

        @Override
        protected String toInnerString() {
            return name;
        }
    }

    public abstract static non-sealed class Local extends WasmId.Variable {
        protected Local(WasmValType variableType) {
            super(variableType);
        }

        @Override
        public Instruction.LocalGet getter() {
            return new Instruction.LocalGet(this);
        }

        @Override
        public Instruction.LocalSet setter(Instruction value) {
            return new Instruction.LocalSet(this, value);
        }

        public Instruction.LocalTee tee(Instruction value) {
            return new Instruction.LocalTee(this, value);
        }
    }

    /**
     * Id to refer to a data segment.
     */
    public static non-sealed class Data extends WasmId {

        /**
         * Symbolic name used in text format and for debugging.
         */
        public final String name;

        public Data(String name) {
            this.name = name;
        }

        @Override
        protected String doResolve(ResolverContext ctxt) {
            return "data." + name;
        }
    }

    public abstract static non-sealed class Label extends WasmId {
    }

    /**
     * Id to refer to a WASM tag from the exception handling proposal.
     * <p>
     * Ref: https://webassembly.github.io/exception-handling/core/syntax/modules.html#tags
     */
    public static non-sealed class Tag extends WasmId {

        public final TypeUse typeUse;

        public Tag(TypeUse typeUse) {
            assert typeUse.results.isEmpty() : "Tag typeUses cannot have a result: " + typeUse;
            this.typeUse = typeUse;
        }

        // TODO GR-41720 Move state into ResolverContext
        static AtomicInteger num = new AtomicInteger(0);

        @Override
        protected String doResolve(ResolverContext ctxt) {
            return "tag" + num.getAndIncrement();
        }

        @Override
        protected String toInnerString() {
            return typeUse.toString() + ", " + super.toInnerString();
        }
    }

    /**
     * Abstract id for any struct field.
     */
    public abstract static non-sealed class Field extends WasmId {
    }
}
