/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.substitutions.standard;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.espresso.impl.Klass;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.substitutions.EspressoSubstitutions;
import com.oracle.truffle.espresso.substitutions.JavaType;
import com.oracle.truffle.espresso.substitutions.Substitution;
import com.oracle.truffle.espresso.substitutions.SubstitutionNode;
import com.oracle.truffle.espresso.substitutions.VersionFilter;

@EspressoSubstitutions
public final class Target_jdk_internal_util_ArraysSupport {

    @Substitution(languageFilter = VersionFilter.Java9OrLater.class)
    abstract static class VectorizedMismatch extends SubstitutionNode {

        static final int LIMIT = 5;

        abstract int execute(
                        @JavaType(Object.class) StaticObject a, long aOffset, @JavaType(Object.class) StaticObject b, long bOffset, int length, int log2ArrayIndexScale);

        @Specialization(guards = {
                        "a.isEspressoObject()",
                        "b.isEspressoObject()"
        })
        int doEspresso(
                        @JavaType(Object.class) StaticObject a,
                        long aOffset,
                        @JavaType(Object.class) StaticObject b,
                        long bOffset,
                        int length,
                        int log2ArrayIndexScale,
                        @SuppressWarnings("unused") @Bind("getMeta()") Meta meta,
                        @Cached("create(meta.jdk_internal_util_ArraysSupport_vectorizedMismatch.getCallTargetNoSubstitution())") DirectCallNode original) {
            return (int) original.call(a, aOffset, b, bOffset, length, log2ArrayIndexScale);
        }

        @Specialization(guards = {
                        "a.isForeignObject()",
                        "b.isForeignObject()"
        })
        int doBothForeign(
                        @JavaType(Object.class) StaticObject a,
                        long aOffset,
                        @JavaType(Object.class) StaticObject b,
                        long bOffset,
                        int length,
                        int log2ArrayIndexScale,
                        @SuppressWarnings("unused") @Bind("getMeta()") Meta meta,
                        @Cached CopyToGuestArrayNode copyNode,
                        @Cached("create(meta.jdk_internal_util_ArraysSupport_vectorizedMismatch.getCallTargetNoSubstitution())") DirectCallNode original) {
            assert a.getKlass().isArray();
            assert a.getKlass() == b.getKlass();

            return (int) original.call(copyNode.execute(a), aOffset, copyNode.execute(b), bOffset, length, log2ArrayIndexScale);
        }

        @Specialization(guards = {
                        "a.isForeignObject()",
                        "b.isEspressoObject()"
        })
        int doFirstForeign(
                        @JavaType(Object.class) StaticObject a,
                        long aOffset,
                        @JavaType(Object.class) StaticObject b,
                        long bOffset,
                        int length,
                        int log2ArrayIndexScale,
                        @Cached CopyToGuestArrayNode copyNode,
                        @SuppressWarnings("unused") @Bind("getMeta()") Meta meta,
                        @Cached("create(meta.jdk_internal_util_ArraysSupport_vectorizedMismatch.getCallTargetNoSubstitution())") DirectCallNode original) {
            assert a.getKlass().isArray();
            assert a.getKlass() == b.getKlass();

            return (int) original.call(copyNode.execute(a), aOffset, b, bOffset, length, log2ArrayIndexScale);
        }

        @Specialization(guards = {
                        "a.isEspressoObject()",
                        "b.isForeignObject()"
        })
        int doSecondForeign(
                        @JavaType(Object.class) StaticObject a,
                        long aOffset,
                        @JavaType(Object.class) StaticObject b,
                        long bOffset,
                        int length,
                        int log2ArrayIndexScale,
                        @SuppressWarnings("unused") @Bind("getMeta()") Meta meta,
                        @Cached CopyToGuestArrayNode copyNode,
                        @Cached("create(meta.jdk_internal_util_ArraysSupport_vectorizedMismatch.getCallTargetNoSubstitution())") DirectCallNode original) {
            assert a.getKlass().isArray();
            assert a.getKlass() == b.getKlass();

            return (int) original.call(a, aOffset, copyNode.execute(b), bOffset, length, log2ArrayIndexScale);
        }
    }

    abstract static class CopyToGuestArrayNode extends EspressoNode {

        static final int LIMIT = 4;

        public abstract @JavaType(Object.class) StaticObject execute(StaticObject foreignArray);

        @SuppressWarnings("unused")
        @Specialization(guards = "foreignArray.getKlass() == cachedKlass", limit = "LIMIT")
        @JavaType(Object.class)
        StaticObject doCached(StaticObject foreignArray,
                        @Cached("foreignArray.getKlass()") Klass cachedKlass,
                        @Bind("getMeta()") Meta meta,
                        @CachedLibrary("foreignArray.rawForeignObject(meta.getLanguage())") InteropLibrary interop) {
            return copyToGuestArray(foreignArray.rawForeignObject(meta.getLanguage()), meta, interop, cachedKlass);
        }

        @Specialization(replaces = "doCached")
        @JavaType(Object.class)
        StaticObject generic(StaticObject foreignArray,
                        @Bind("getMeta()") Meta meta,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            return copyToGuestArray(foreignArray.rawForeignObject(meta.getLanguage()), meta, interop, foreignArray.getKlass());
        }

        private static StaticObject copyToGuestArray(Object rawForeignObject, Meta meta, InteropLibrary interop, Klass klass) {
            assert interop.hasArrayElements(rawForeignObject) || interop.hasBufferElements(rawForeignObject);

            StaticObject result = null;
            try {
                if (klass == meta._boolean_array) {
                    long arraySize = interop.getArraySize(rawForeignObject);
                    boolean[] primitiveArray = new boolean[(int) arraySize];
                    for (int i = 0; i < primitiveArray.length; i++) {
                        primitiveArray[i] = (boolean) interop.readArrayElement(rawForeignObject, i);
                    }
                    result = StaticObject.createArray(meta._boolean_array, primitiveArray, meta.getContext());
                } else if (klass == meta._int_array) {
                    long arraySize = interop.getArraySize(rawForeignObject);
                    int[] primitiveArray = new int[(int) arraySize];
                    for (int i = 0; i < primitiveArray.length; i++) {
                        primitiveArray[i] = (int) interop.readArrayElement(rawForeignObject, i);
                    }
                    result = StaticObject.createArray(meta._int_array, primitiveArray, meta.getContext());
                } else if (klass == meta._long_array) {
                    long arraySize = interop.getArraySize(rawForeignObject);
                    long[] primitiveArray = new long[(int) arraySize];
                    for (int i = 0; i < primitiveArray.length; i++) {
                        primitiveArray[i] = (long) interop.readArrayElement(rawForeignObject, i);
                    }
                    result = StaticObject.createArray(meta._long_array, primitiveArray, meta.getContext());
                } else if (klass == meta._double_array) {
                    long arraySize = interop.getArraySize(rawForeignObject);
                    double[] primitiveArray = new double[(int) arraySize];
                    for (int i = 0; i < primitiveArray.length; i++) {
                        primitiveArray[i] = (double) interop.readArrayElement(rawForeignObject, i);
                    }
                    result = StaticObject.createArray(meta._double_array, primitiveArray, meta.getContext());
                } else if (klass == meta._float_array) {
                    long arraySize = interop.getArraySize(rawForeignObject);
                    float[] primitiveArray = new float[(int) arraySize];
                    for (int i = 0; i < primitiveArray.length; i++) {
                        primitiveArray[i] = (float) interop.readArrayElement(rawForeignObject, i);
                    }
                    result = StaticObject.createArray(meta._float_array, primitiveArray, meta.getContext());
                } else if (klass == meta._short_array) {
                    long arraySize = interop.getArraySize(rawForeignObject);
                    short[] primitiveArray = new short[(int) arraySize];
                    for (int i = 0; i < primitiveArray.length; i++) {
                        primitiveArray[i] = (short) interop.readArrayElement(rawForeignObject, i);
                    }
                    result = StaticObject.createArray(meta._short_array, primitiveArray, meta.getContext());
                } else if (klass == meta._byte_array) {
                    if (interop.hasBufferElements(rawForeignObject)) {
                        long bufferSize = interop.getBufferSize(rawForeignObject);
                        byte[] primitiveArray = new byte[(int) bufferSize];
                        interop.readBuffer(rawForeignObject, 0, primitiveArray, 0, (int) bufferSize);
                        result = StaticObject.createArray(meta._byte_array, primitiveArray, meta.getContext());
                    } else {
                        long arraySize = interop.getArraySize(rawForeignObject);
                        byte[] primitiveArray = new byte[(int) arraySize];
                        for (int i = 0; i < primitiveArray.length; i++) {
                            primitiveArray[i] = (byte) interop.readArrayElement(rawForeignObject, i);
                        }
                        result = StaticObject.createArray(meta._byte_array, primitiveArray, meta.getContext());
                    }
                } else if (klass == meta._char_array) {
                    long arraySize = interop.getArraySize(rawForeignObject);
                    char[] primitiveArray = new char[(int) arraySize];
                    for (int i = 0; i < primitiveArray.length; i++) {
                        primitiveArray[i] = (char) interop.readArrayElement(rawForeignObject, i);
                    }
                    result = StaticObject.createArray(meta._char_array, primitiveArray, meta.getContext());
                }
            } catch (UnsupportedMessageException | InvalidArrayIndexException | InvalidBufferOffsetException ex) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere("unsupported foreign array in ArraysSupport.vectorizedMismatch", ex);
            }
            return result;
        }
    }
}
