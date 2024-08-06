/*
 * Copyright (c) 2017, 2024, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.nfi.backend.spi.types.NativeSimpleType;
import com.oracle.truffle.nfi.backend.panama.ClosureArgumentNodeFactory.StringClosureArgumentNodeGen;
import com.oracle.truffle.nfi.backend.panama.ClosureArgumentNodeFactory.GenericClosureArgumentNodeGen;

import java.lang.foreign.MemoryLayout;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;

class PanamaType {

    @SuppressWarnings("preview") final MemoryLayout nativeLayout;
    final Class<?> javaType;
    final Class<?> javaRetType;
    final NativeSimpleType type;

    @SuppressWarnings("preview")
    PanamaType(NativeSimpleType type) throws UnsupportedOperationException {
        this.type = type;
        switch (type) {
            case VOID:
                nativeLayout = null;
                javaType = void.class;
                javaRetType = javaType;
                break;
            case UINT8:
            case SINT8:
                nativeLayout = ValueLayout.JAVA_BYTE;
                javaType = byte.class;
                javaRetType = javaType;
                break;
            case UINT16:
            case SINT16:
                nativeLayout = ValueLayout.JAVA_SHORT;
                javaType = short.class;
                javaRetType = javaType;
                break;
            case UINT32:
            case SINT32:
                nativeLayout = ValueLayout.JAVA_INT;
                javaType = int.class;
                javaRetType = javaType;
                break;
            case UINT64:
            case SINT64:
            case POINTER:
                nativeLayout = ValueLayout.JAVA_LONG;
                javaType = long.class;
                javaRetType = javaType;
                break;
            case FP80:
                throw new UnsupportedOperationException("FP80 not implemented");
            case FLOAT:
                nativeLayout = ValueLayout.JAVA_FLOAT;
                javaType = float.class;
                javaRetType = javaType;
                break;
            case DOUBLE:
                nativeLayout = ValueLayout.JAVA_DOUBLE;
                javaType = double.class;
                javaRetType = javaType;
                break;
            case STRING:
                javaType = MemorySegment.class;
                javaRetType = MemorySegment.class;
                nativeLayout = ValueLayout.ADDRESS;
                break;
            case OBJECT:
                javaType = Object.class;
                // TODO
                throw CompilerDirectives.shouldNotReachHere("OBJ not implemented");
            case NULLABLE:
                // TODO
                throw CompilerDirectives.shouldNotReachHere("Nullable not implemented");
            default:
                throw CompilerDirectives.shouldNotReachHere("Type does not exist.");
        }
    }

    public ArgumentNode createArgumentNode() {
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
