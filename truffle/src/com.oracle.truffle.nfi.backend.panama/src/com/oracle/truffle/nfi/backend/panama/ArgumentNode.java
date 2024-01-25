/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.interop.UnsupportedTypeException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.profiles.InlinedBranchProfile;

abstract class ArgumentNode extends Node {
    final PanamaType type;

    ArgumentNode(PanamaType type) {
        this.type = type;
    }

    abstract Object execute(Object value) throws UnsupportedTypeException;

    abstract static class ToVOIDNode extends ArgumentNode {

        ToVOIDNode(PanamaType type) {
            super(type);
        }

        @Specialization
        Object doConvert(@SuppressWarnings("unused") Object value) {
            return null;
        }
    }

    abstract static class ToINT8Node extends ArgumentNode {

        ToINT8Node(PanamaType type) {
            super(type);
        }

        @Specialization(limit = "3")
        byte doConvert(Object value,
                        @CachedLibrary("value") InteropLibrary interop) throws UnsupportedTypeException {
            try {
                return interop.asByte(value);
            } catch (UnsupportedMessageException ex) {
                throw UnsupportedTypeException.create(new Object[]{value});
            }
        }
    }

    abstract static class ToINT16Node extends ArgumentNode {

        ToINT16Node(PanamaType type) {
            super(type);
        }

        @Specialization(limit = "3")
        short doConvert(Object value,
                        @CachedLibrary("value") InteropLibrary interop) throws UnsupportedTypeException {
            try {
                return interop.asShort(value);
            } catch (UnsupportedMessageException ex) {
                throw UnsupportedTypeException.create(new Object[]{value});
            }
        }
    }

    abstract static class ToINT32Node extends ArgumentNode {

        ToINT32Node(PanamaType type) {
            super(type);
        }

        @Specialization(limit = "3")
        int doConvert(Object value,
                        @CachedLibrary("value") InteropLibrary interop) throws UnsupportedTypeException {
            try {
                return interop.asInt(value);
            } catch (UnsupportedMessageException ex) {
                throw UnsupportedTypeException.create(new Object[]{value});
            }
        }
    }

    abstract static class ToINT64Node extends ArgumentNode {

        ToINT64Node(PanamaType type) {
            super(type);
        }

        @Specialization(limit = "3")
        long doConvert(Object value,
                        @CachedLibrary("value") InteropLibrary interop) throws UnsupportedTypeException {
            try {
                return interop.asLong(value);
            } catch (UnsupportedMessageException ex) {
                throw UnsupportedTypeException.create(new Object[]{value});
            }
        }
    }

    abstract static class ToPointerNode extends ArgumentNode {

        ToPointerNode(PanamaType type) {
            super(type);
        }

        @Specialization(limit = "3", guards = "interop.isPointer(arg)", rewriteOn = UnsupportedMessageException.class)
        long putPointer(Object arg,
                        @CachedLibrary("arg") InteropLibrary interop) throws UnsupportedMessageException {
            return interop.asPointer(arg);
        }

        @Specialization(limit = "3", guards = {"!interop.isPointer(arg)", "interop.isNull(arg)"})
        long putNull(@SuppressWarnings("unused") Object arg,
                        @SuppressWarnings("unused") @CachedLibrary("arg") InteropLibrary interop) {
            return NativePointer.NULL.asPointer();
        }

        @Specialization(limit = "3", replaces = {"putPointer", "putNull"})
        static long putGeneric(Object arg,
                        @Bind("this") Node node,
                        @CachedLibrary("arg") InteropLibrary interop,
                        @Cached InlinedBranchProfile exception) throws UnsupportedTypeException {
            try {
                if (!interop.isPointer(arg)) {
                    interop.toNative(arg);
                }
                if (interop.isPointer(arg)) {
                    return interop.asPointer(arg);
                }
            } catch (UnsupportedMessageException ex) {
                // fallthrough
            }
            exception.enter(node);
            if (interop.isNull(arg)) {
                return NativePointer.NULL.asPointer();
            } else {
                try {
                    if (interop.isNumber(arg)) {
                        return interop.asLong(arg);
                    }
                } catch (UnsupportedMessageException ex2) {
                    // fallthrough
                }
            }
            throw UnsupportedTypeException.create(new Object[]{arg});
        }
    }

    abstract static class ToFLOATNode extends ArgumentNode {

        ToFLOATNode(PanamaType type) {
            super(type);
        }

        @Specialization(limit = "3")
        float doConvert(Object value,
                        @CachedLibrary("value") InteropLibrary interop) throws UnsupportedTypeException {
            try {
                return interop.asFloat(value);
            } catch (UnsupportedMessageException ex) {
                throw UnsupportedTypeException.create(new Object[]{value});
            }
        }
    }

    abstract static class ToDOUBLENode extends ArgumentNode {

        ToDOUBLENode(PanamaType type) {
            super(type);
        }

        @Specialization(limit = "3")
        double doConvert(Object value,
                        @CachedLibrary("value") InteropLibrary interop) throws UnsupportedTypeException {
            try {
                return interop.asDouble(value);
            } catch (UnsupportedMessageException ex) {
                throw UnsupportedTypeException.create(new Object[]{value});
            }
        }
    }

    abstract static class ToSTRINGNode extends ArgumentNode {

        ToSTRINGNode(PanamaType type) {
            super(type);
        }

        @Specialization(limit = "3")
        Object doConvert(Object value,
                        @CachedLibrary("value") InteropLibrary interop) throws UnsupportedTypeException {
            PanamaNFIContext ctx = PanamaNFIContext.get(this);
            try {
                return ctx.getContextArena().allocateFrom(interop.asString(value));
            } catch (UnsupportedMessageException ex) {
                throw UnsupportedTypeException.create(new Object[]{value});
            }
        }
    }
}
