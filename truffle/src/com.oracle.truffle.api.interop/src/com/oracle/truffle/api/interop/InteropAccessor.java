/*
 * Copyright (c) 2017, 2019, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.impl.Accessor;
import com.oracle.truffle.api.nodes.Node;

@SuppressWarnings("deprecation")
final class InteropAccessor extends Accessor {

    static final InteropAccessor ACCESSOR = new InteropAccessor();

    private InteropAccessor() {
    }

    static Object checkInteropType(Object obj) {
        assert checkInteropTypeImpl(obj);
        return obj;
    }

    private static boolean checkInteropTypeImpl(Object obj) {
        if (AssertUtils.isInteropValue(obj)) {
            return true;
        }
        CompilerDirectives.transferToInterpreter();
        Class<?> clazz = obj != null ? obj.getClass() : null;
        return yieldAnError(clazz);
    }

    private static boolean yieldAnError(Class<?> clazz) {
        CompilerDirectives.transferToInterpreter();
        StringBuilder sb = new StringBuilder();
        sb.append(clazz == null ? "null" : clazz.getName());
        sb.append(" isn't allowed Truffle interop type!\n");
        if (clazz == null) {
            throw new NullPointerException(sb.toString());
        } else {
            throw new ClassCastException(sb.toString());
        }
    }

    /*
     * Instantiated by Accessor.
     */
    static class InteropImpl extends InteropSupport {

        @Override
        public boolean isTruffleObject(Object value) {
            return value instanceof TruffleObject;
        }

        @Override
        public void checkInteropType(Object result) {
            InteropAccessor.checkInteropType(result);
        }

        @Override
        public boolean isExecutableObject(Object value) {
            return InteropLibrary.getFactory().getUncached().isExecutable(value);
        }

        @Override
        public Object createDefaultNodeObject(Node node) {
            return EmptyTruffleObject.INSTANCE;
        }

        @Override
        @TruffleBoundary
        public boolean isValidNodeObject(Object obj) {
            InteropLibrary interop = InteropLibrary.getFactory().getUncached(obj);

            if (!interop.hasMembers(obj)) {
                throw new AssertionError("Invalid node object: must return true for the hasMembers message.");
            }
            Object members;
            try {
                members = interop.getMembers(obj);
            } catch (UnsupportedMessageException e) {
                throw new AssertionError("Invalid node object: must support the getMembers message.", e);
            }
            InteropLibrary membersInterop = InteropLibrary.getFactory().getUncached(members);
            if (!membersInterop.hasArrayElements(members)) {
                throw new AssertionError("Invalid node object: the returned members object must support hasArrayElements.");
            }
            long size;
            try {
                size = membersInterop.getArraySize(members);
            } catch (UnsupportedMessageException e) {
                throw new AssertionError("Invalid node object: the returned members object must have a size.");
            }
            for (long i = 0; i < size; i++) {
                Object key;
                try {
                    key = membersInterop.readArrayElement(members, i);
                } catch (InvalidArrayIndexException | UnsupportedMessageException e) {
                    throw new AssertionError("Invalid node object: the returned members object must be readable at number index " + i);
                }
                InteropLibrary keyInterop = InteropLibrary.getFactory().getUncached(key);
                if (!keyInterop.isString(key)) {
                    throw new AssertionError("Invalid node object: the returned member must return a string at index " + i + ". But was " + key.getClass().getName() + ".");
                }
                String member;
                try {
                    member = keyInterop.asString(key);
                } catch (UnsupportedMessageException e1) {
                    throw new AssertionError("Invalid node object: the returned member must return a string  ");
                }
                try {
                    interop.readMember(obj, member);
                } catch (UnknownIdentifierException | UnsupportedMessageException e) {
                    throw new AssertionError("Invalid node object: the returned member must be readable with identifier " + member);
                }

                if (interop.isMemberWritable(obj, member)) {
                    throw new AssertionError("Invalid node object: The member " + member + " is marked as writable but node objects must not be writable.");
                }
            }
            if (interop.hasArrayElements(obj)) {
                throw new AssertionError("Invalid node object: the node object must not return true for hasArrayElements.");
            }

            return true;
        }
    }

    static final class EmptyTruffleObject implements TruffleObject {
        static final EmptyTruffleObject INSTANCE = new EmptyTruffleObject();
    }
}
