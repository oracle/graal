/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.wasm.types;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import org.graalvm.wasm.WasmTypedHeapObject;
import org.graalvm.wasm.struct.WasmStructAccess;

import java.util.Objects;

/**
 * This represents a user-defined type in a WebAssembly module, and it corresponds to the
 * {@code deftype} abstract syntax category in the WebAssembly specification. These are represented
 * as projections from a list of mutually recursive types (with {@link RecursiveTypes} representing
 * the list of recursive types and {@link SubType} representing an element of that list).
 * <p>
 * These types can be recursive and in our representation, all type indices are expanded and any
 * recursion is unrolled by creating cyclic graphs. Whenever a group of mutually recursive types has
 * finished parsing, its types are unrolled via {@link DefinedType#unroll(RecursiveTypes)}. However,
 * for certain operations which would try to traverse the entire AST, we treat the recursive
 * references as not unrolled by first checking {@link #recursiveReference} and stopping further
 * traversal. This is notably the case for {@link #equals(Object)} and {@link #hashCode()}, where
 * this yields the iso-recursive type equality predicate that is required by the spec.
 */
public final class DefinedType implements HeapType {

    @CompilerDirectives.CompilationFinal private RecursiveTypes recursiveTypes;
    private final int subTypeIndex;

    private final boolean recursiveReference;

    @CompilerDirectives.CompilationFinal private int typeEquivalenceClass;
    @CompilerDirectives.CompilationFinal private WasmStructAccess structAccess;

    private DefinedType(int subTypeIndex, boolean recursiveReference) {
        this.subTypeIndex = subTypeIndex;
        this.recursiveReference = recursiveReference;
    }

    public static DefinedType makeTopLevelType(RecursiveTypes recursiveTypes, int subTypeIndex) {
        DefinedType toplevelType = new DefinedType(subTypeIndex, false);
        toplevelType.recursiveTypes = recursiveTypes;
        return toplevelType;
    }

    public static DefinedType makeRecursiveReference(int subTypeIndex) {
        return new DefinedType(subTypeIndex, true);
    }

    public void setTypeEquivalenceClass(int typeEquivalenceClass) {
        this.typeEquivalenceClass = typeEquivalenceClass;
    }

    /**
     * Gets the type equivalence class of this type. This can be used to implement more efficient
     * equality checks on defined types, since {@code a.equals(b)} iff
     * {@code a.typeEquivalenceClass() == b.typeEquivalenceClass()} (this works across different
     * modules and contexts, as long as they share the same {@link org.graalvm.wasm.WasmLanguage}
     * instance).
     */
    public int typeEquivalenceClass() {
        return typeEquivalenceClass;
    }

    public void setStructAccess(WasmStructAccess structAccess) {
        this.structAccess = structAccess;
    }

    public WasmStructAccess structAccess() {
        return structAccess;
    }

    public boolean isFinal() {
        return recursiveTypes.subTypes()[subTypeIndex].isFinal();
    }

    public HeapType superType() {
        return recursiveTypes.subTypes()[subTypeIndex].superType();
    }

    public CompositeType expand() {
        return recursiveTypes.subTypes()[subTypeIndex].compositeType();
    }

    public ArrayType asArrayType() {
        assert isArrayType();
        return (ArrayType) expand();
    }

    public StructType asStructType() {
        assert isStructType();
        return (StructType) expand();
    }

    public FunctionType asFunctionType() {
        assert isFunctionType();
        return (FunctionType) expand();
    }

    @Override
    public HeapKind heapKind() {
        return HeapKind.DefinedType;
    }

    @Override
    public boolean isSubtypeOf(HeapType thatHeapType) {
        if (thatHeapType instanceof DefinedType that) {
            return isSubtypeOf(that);
        } else {
            return expand().isSubtypeOf(thatHeapType);
        }
    }

    @TruffleBoundary
    public boolean isSubtypeOf(DefinedType that) {
        if (this.recursiveTypes == that.recursiveTypes && this.subTypeIndex == that.subTypeIndex) {
            return true;
        }
        if (this.recursiveTypes.equals(that.recursiveTypes) && this.subTypeIndex == that.subTypeIndex) {
            return true;
        }
        DefinedType superType = this.recursiveTypes.subTypes()[subTypeIndex].superType();
        return superType != null && superType.isSubtypeOf(that);
    }

    @Override
    public boolean isArrayType() {
        return expand() instanceof ArrayType;
    }

    @Override
    public boolean isStructType() {
        return expand() instanceof StructType;
    }

    @Override
    public boolean isFunctionType() {
        return expand() instanceof FunctionType;
    }

    @Override
    public boolean matchesValue(Object value) {
        return value instanceof WasmTypedHeapObject heapValue && heapValue.type().isSubtypeOf(this);
    }

    @Override
    public void unroll(@SuppressWarnings("hiding") RecursiveTypes recursiveTypes) {
        if (recursiveReference) {
            this.recursiveTypes = recursiveTypes;
        }
    }

    @Override
    public boolean equals(Object obj) {
        return obj instanceof DefinedType that && this.recursiveReference == that.recursiveReference && this.subTypeIndex == that.subTypeIndex &&
                        (recursiveReference || this.recursiveTypes.equals(that.recursiveTypes));
    }

    @Override
    public int hashCode() {
        return recursiveReference ? subTypeIndex : Objects.hash(subTypeIndex, recursiveTypes);
    }

    @Override
    public String toString() {
        CompilerAsserts.neverPartOfCompilation();
        if (recursiveReference) {
            return "$" + subTypeIndex;
        } else if (recursiveTypes.subTypes().length == 1 && recursiveTypes.subTypes()[0].isFinal()) {
            return recursiveTypes.subTypes()[0].compositeType().toString();
        } else {
            return recursiveTypes + "." + subTypeIndex;
        }
    }
}
