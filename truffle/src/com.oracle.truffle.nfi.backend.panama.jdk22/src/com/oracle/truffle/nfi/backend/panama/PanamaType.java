/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.nfi.backend.panama;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.nfi.backend.panama.ClosureArgumentNodeFactory.GenericClosureArgumentNodeGen;
import com.oracle.truffle.nfi.backend.panama.ClosureArgumentNodeFactory.StringClosureArgumentNodeGen;
import com.oracle.truffle.nfi.backend.spi.types.NativeSimpleType;

final class PanamaType {

    final MemoryLayout nativeLayout;
    final Class<?> javaType;
    final Class<?> javaRetType;
    final NativeSimpleType type;
    final boolean isArray;

    /**
     * Creates an instance representing a simple (non-array) type.
     */
    private PanamaType(NativeSimpleType type, MemoryLayout nativeLayout, Class<?> javaType, Class<?> javaRetType) {
        this(type, false, nativeLayout, javaType, javaRetType);
    }

    private PanamaType(NativeSimpleType type, boolean isArray, MemoryLayout nativeLayout, Class<?> javaType, Class<?> javaRetType) {
        this.nativeLayout = nativeLayout;
        this.javaType = javaType;
        this.javaRetType = javaRetType;
        this.type = type;
        this.isArray = isArray;
    }

    static PanamaType createSimple(NativeSimpleType type) throws UnsupportedOperationException {
        return switch (type) {
            case VOID -> new PanamaType(type, null, void.class, void.class);
            case UINT8, SINT8 -> new PanamaType(type, ValueLayout.JAVA_BYTE, byte.class, byte.class);
            case UINT16, SINT16 -> new PanamaType(type, ValueLayout.JAVA_SHORT, short.class, short.class);
            case UINT32, SINT32 -> new PanamaType(type, ValueLayout.JAVA_INT, int.class, int.class);
            case UINT64, SINT64, POINTER -> new PanamaType(type, ValueLayout.JAVA_LONG, long.class, long.class);
            case FP80 -> throw new UnsupportedOperationException("FP80 not implemented");
            case FLOAT -> new PanamaType(type, ValueLayout.JAVA_FLOAT, float.class, float.class);
            case DOUBLE -> new PanamaType(type, ValueLayout.JAVA_DOUBLE, double.class, double.class);
            case STRING -> new PanamaType(type, ValueLayout.ADDRESS, MemorySegment.class, MemorySegment.class);
            case OBJECT -> throw CompilerDirectives.shouldNotReachHere("OBJ not implemented");
            case NULLABLE -> throw CompilerDirectives.shouldNotReachHere("Nullable not implemented");
            default -> throw CompilerDirectives.shouldNotReachHere("Type does not exist.");
        };
    }

    static PanamaType createArray(NativeSimpleType type) throws UnsupportedOperationException {
        return switch (type) {
            case UINT8, SINT8, UINT16, SINT16, UINT32, SINT32, UINT64, SINT64, FLOAT, DOUBLE -> new PanamaType(type, true, ValueLayout.ADDRESS, MemorySegment.class, MemorySegment.class);
            default -> throw CompilerDirectives.shouldNotReachHere("Type does not exist.");
        };
    }

    public boolean needsPostCallProcessing() {
        return isArray;
    }

    public boolean needsArena() {
        return isArray || type == NativeSimpleType.STRING;
    }

    public ArgumentNode createArgumentNode() {
        if (isArray) {
            return ArgumentNodeFactory.ToArrayNodeGen.create(this);
        }
        switch (type) {
            case VOID:
                return ArgumentNodeFactory.ToVOIDNodeGen.create(this);
            case UINT8:
            case SINT8:
                return ArgumentNodeFactory.ToINT8NodeGen.create(this);
            case UINT16:
            case SINT16:
                return ArgumentNodeFactory.ToINT16NodeGen.create(this);
            case UINT32:
            case SINT32:
                return ArgumentNodeFactory.ToINT32NodeGen.create(this);
            case UINT64:
            case SINT64:
                return ArgumentNodeFactory.ToINT64NodeGen.create(this);
            case POINTER:
                return ArgumentNodeFactory.ToPointerNodeGen.create(this);
            case FP80:
                throw new UnsupportedOperationException();
            case FLOAT:
                return ArgumentNodeFactory.ToFLOATNodeGen.create(this);
            case DOUBLE:
                return ArgumentNodeFactory.ToDOUBLENodeGen.create(this);
            case STRING:
                return ArgumentNodeFactory.ToSTRINGNodeGen.create(this);
            case OBJECT:
                // TODO
                throw CompilerDirectives.shouldNotReachHere("OBJ not imple");
            case NULLABLE:
                // TODO
                throw CompilerDirectives.shouldNotReachHere("Nullable not imple");
            default:
                throw CompilerDirectives.shouldNotReachHere("Type does not exist.");
        }
    }

    /**
     * Creates a node to be called after the downcall, typically to copy back data from native
     * memory to Java object (such as an array).
     * 
     * @return `null` if no post-processing is needed
     */
    public PostCallArgumentNode createPostCallArgumentNode() {
        if (isArray) {
            return PostCallArgumentNodeFactory.CopyBackArrayNodeGen.create(this);
        }
        return null;
    }

    public ClosureArgumentNode createClosureArgumentNode(ClosureArgumentNode arg) {
        switch (type) {
            case VOID:
            case UINT8:
            case SINT8:
            case UINT16:
            case SINT16:
            case UINT32:
            case SINT32:
            case UINT64:
            case SINT64:
            case POINTER:
            case FLOAT:
            case FP80:
            case DOUBLE:
                return GenericClosureArgumentNodeGen.create(this, arg);
            case STRING:
                return StringClosureArgumentNodeGen.create(this, arg);
            case OBJECT:
                // TODO
                throw CompilerDirectives.shouldNotReachHere("OBJ not imple");
            case NULLABLE:
                // TODO
                throw CompilerDirectives.shouldNotReachHere("Nullable not imple");
            default:
                throw CompilerDirectives.shouldNotReachHere("Type does not exist.");
        }
    }
}
