/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.nodes.bytecodes;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Bind;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidBufferOffsetException;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.api.profiles.ConditionProfile;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.nodes.quick.interop.ForeignArrayUtils;
import com.oracle.truffle.espresso.nodes.quick.interop.Utils;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * BALOAD bytecode with interop extensions. Similar to the BALOAD, supports both byte[] and
 * boolean[].
 *
 * <h3>Extensions</h3>
 * <ul>
 * <li>For Truffle buffers ({@link InteropLibrary#hasBufferElements(Object) buffer-like} foreign
 * objects) wrapped as {@code byte[]}, BALOAD is mapped to
 * {@link InteropLibrary#readBufferByte(Object, long)}.</li>
 * <li>For {@link InteropLibrary#hasArrayElements(Object) array-like} foreign objects, BALOAD is
 * mapped to {@link InteropLibrary#readArrayElement(Object, long)}.</li>
 * </ul>
 *
 * <h3>Exceptions</h3>
 * <ul>
 * <li>Throws guest {@link ArrayIndexOutOfBoundsException} if the index is out of bounds.</li>
 * <li>Throws guest {@link ClassCastException} if the result cannot be converted to
 * byte/boolean.</li>
 * </ul>
 *
 * @see BooleanArrayLoad
 */
@GenerateUncached
@NodeInfo(shortName = "BALOAD")
public abstract class ByteArrayLoad extends EspressoNode {

    public abstract byte execute(StaticObject receiver, int index);

    @Specialization
    byte executeWithNullCheck(StaticObject array, int index,
                    @Cached NullCheck nullCheck,
                    @Cached WithoutNullCheck byteArrayLoad) {
        return byteArrayLoad.execute(nullCheck.execute(array), index);
    }

    @GenerateUncached
    @ImportStatic(Utils.class)
    @NodeInfo(shortName = "BALOAD !nullcheck")
    public abstract static class WithoutNullCheck extends EspressoNode {

        protected static final int LIMIT = 2;

        public abstract byte execute(StaticObject receiver, int index);

        @Specialization(guards = "array.isEspressoObject()")
        byte doEspresso(StaticObject array, int index) {
            assert !StaticObject.isNull(array);
            return getContext().getInterpreterToVM().getArrayByte(getLanguage(), index, array);
        }

        @Specialization(guards = {
                        "array.isForeignObject()",
                        "isBufferLikeByteArray(language, meta, interop, array)"
        })
        byte doBufferLike(StaticObject array, int index,
                        @Bind("getLanguage()") EspressoLanguage language,
                        @Bind("getMeta()") Meta meta,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile outOfBoundsProfile) {
            assert !StaticObject.isNull(array);
            try {
                return interop.readBufferByte(array.rawForeignObject(language), index);
            } catch (InvalidBufferOffsetException e) {
                outOfBoundsProfile.enter();
                throw meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, e.getMessage());
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        @Specialization(guards = {
                        "array.isForeignObject()",
                        "!isBufferLikeByteArray(language, meta, arrayInterop, array)",
                        "isArrayLike(arrayInterop, array.rawForeignObject(language))"
        })
        byte doArrayLike(StaticObject array, int index,
                        @Bind("getLanguage()") EspressoLanguage language,
                        @Bind("getMeta()") Meta meta,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary arrayInterop,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary elemInterop,
                        @Cached BranchProfile exceptionProfile,
                        @Cached ConditionProfile isByteArrayProfile) {
            assert !StaticObject.isNull(array);
            Object result = ForeignArrayUtils.readForeignArrayElement(array, index, language, meta, arrayInterop, exceptionProfile);

            if (isByteArrayProfile.profile(array.getKlass() == meta._byte_array)) {
                try {
                    return elemInterop.asByte(result);
                } catch (UnsupportedMessageException e) {
                    exceptionProfile.enter();
                    throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, "Could not cast the foreign array element to byte");
                }
            } else {
                assert array.getKlass() == meta._boolean_array;
                try {
                    boolean element = elemInterop.asBoolean(result);
                    return (byte) (element ? 1 : 0);
                } catch (UnsupportedMessageException e) {
                    exceptionProfile.enter();
                    throw meta.throwExceptionWithMessage(meta.java_lang_ClassCastException, "Could not cast the foreign array element to boolean");
                }
            }
        }
    }
}
