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
import com.oracle.truffle.espresso.meta.Meta;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.nodes.quick.interop.ForeignArrayUtils;
import com.oracle.truffle.espresso.nodes.quick.interop.Utils;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * BASTORE bytecode with interop extensions. Similar to the BALOAD, supports both byte[] and
 * boolean[].
 *
 * <h3>Extensions</h3>
 * <ul>
 * <li>For Truffle buffers ({@link InteropLibrary#hasBufferElements(Object) buffer-like} foreign
 * objects) wrapped as {@code byte[]}, BASTORE is mapped to
 * {@link InteropLibrary#writeBufferByte(Object, long, byte)}.</li>
 * <li>For {@link InteropLibrary#hasArrayElements(Object) array-like} foreign objects, BASTORE is
 * mapped to {@link InteropLibrary#writeArrayElement(Object, long, Object)}.</li>
 * </ul>
 *
 * <h3>Exceptions</h3>
 * <ul>
 * <li>Throws guest {@link ArrayIndexOutOfBoundsException} if the index is out of bounds.</li>
 * <li>Throws guest {@link ClassCastException} if the result cannot be converted to
 * byte/boolean.</li>
 * <li>Throws guest {@link ArrayStoreException} if the underlying interop array/buffer is
 * read-only.</li>
 * </ul>
 *
 * @see BooleanArrayStore
 */
@GenerateUncached
@NodeInfo(shortName = "BASTORE")
public abstract class ByteArrayStore extends EspressoNode {

    public abstract void execute(StaticObject receiver, int index, byte value);

    @Specialization
    void executeWithNullCheck(StaticObject array, int index, byte value,
                    @Cached NullCheck nullCheck,
                    @Cached ByteArrayStore.WithoutNullCheck byteArrayStore) {
        byteArrayStore.execute(nullCheck.execute(array), index, value);
    }

    @GenerateUncached
    @ImportStatic(Utils.class)
    @NodeInfo(shortName = "BASTORE !nullcheck")
    public abstract static class WithoutNullCheck extends EspressoNode {
        static final int LIMIT = 2;

        public abstract void execute(StaticObject receiver, int index, byte value);

        @Specialization(guards = "array.isEspressoObject()")
        void doEspresso(StaticObject array, int index, byte value) {
            assert !StaticObject.isNull(array);
            getContext().getInterpreterToVM().setArrayByte(getLanguage(), value, index, array);
        }

        @Specialization(guards = {
                        "array.isForeignObject()",
                        "isBufferLikeByteArray(language, meta, interop, array)"
        })
        void doBufferLike(StaticObject array, int index, byte value,
                        @Bind("getLanguage()") EspressoLanguage language,
                        @Bind("getMeta()") Meta meta,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile outOfBoundsProfile,
                        @Cached BranchProfile readOnlyProfile) {
            assert !StaticObject.isNull(array);
            try {
                interop.writeBufferByte(array.rawForeignObject(language), index, value);
            } catch (InvalidBufferOffsetException e) {
                outOfBoundsProfile.enter();
                throw meta.throwExceptionWithMessage(meta.java_lang_ArrayIndexOutOfBoundsException, e.getMessage());
            } catch (UnsupportedMessageException e) {
                // Read-only foreign object.
                readOnlyProfile.enter();
                throw meta.throwExceptionWithMessage(meta.java_lang_ArrayStoreException, e.getMessage());
            }
        }

        @Specialization(guards = {
                        "array.isForeignObject()",
                        "!isBufferLikeByteArray(language, meta, interop, array)",
                        "isArrayLike(interop, array.rawForeignObject(language))"
        })
        void doArrayLike(StaticObject array, int index, byte value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Bind("getLanguage()") EspressoLanguage language,
                        @Bind("getMeta()") Meta meta,
                        @Cached BranchProfile exceptionProfile,
                        @Cached ConditionProfile isByteArrayProfile) {
            assert !StaticObject.isNull(array);
            if (isByteArrayProfile.profile(array.getKlass() == meta._byte_array)) {
                ForeignArrayUtils.writeForeignArrayElement(array, index, value, language, meta, interop, exceptionProfile);
            } else {
                assert array.getKlass() == meta._boolean_array;
                boolean booleanValue = value != 0;
                ForeignArrayUtils.writeForeignArrayElement(array, index, booleanValue, language, meta, interop, exceptionProfile);
            }
        }
    }
}
