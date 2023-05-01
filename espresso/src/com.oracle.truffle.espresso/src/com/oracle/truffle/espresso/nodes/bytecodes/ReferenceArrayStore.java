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

import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.nodes.quick.interop.ForeignArrayUtils;
import com.oracle.truffle.espresso.runtime.StaticObject;

/**
 * AASTORE bytecode with interop extensions.
 *
 * <h3>Extensions</h3>
 * <ul>
 * <li>For {@link InteropLibrary#hasArrayElements(Object) array-like} foreign objects, AASTORE is
 * mapped to {@link InteropLibrary#writeArrayElement(Object, long, Object)}.</li>
 * </ul>
 *
 * <h3>Exceptions</h3>
 * <ul>
 * <li>Throws guest {@link ArrayIndexOutOfBoundsException} if the index is out of bounds.</li>
 * <li>Throws guest {@link ClassCastException} if the underlying interop array does not accept
 * writing an Object.</li>
 * <li>Throws guest {@link ArrayStoreException} if the underlying interop array is read-only.</li>
 * </ul>
 */
@GenerateUncached
@NodeInfo(shortName = "AASTORE")
public abstract class ReferenceArrayStore extends EspressoNode {

    public abstract void execute(StaticObject receiver, int index, StaticObject value);

    @Specialization
    void doWithNullCheck(StaticObject array, int index, StaticObject value,
                    @Cached NullCheck nullCheck,
                    @Cached WithoutNullCheck objectArrayStore) {
        objectArrayStore.execute(nullCheck.execute(array), index, value);
    }

    @GenerateUncached
    @NodeInfo(shortName = "AASTORE !nullcheck")
    public abstract static class WithoutNullCheck extends EspressoNode {
        static final int LIMIT = 2;

        public abstract void execute(StaticObject receiver, int index, StaticObject value);

        @Specialization(guards = "array.isEspressoObject()")
        void doEspresso(StaticObject array, int index, StaticObject value) {
            assert !StaticObject.isNull(array);
            getContext().getInterpreterToVM().setArrayObject(getLanguage(), value, index, array);
        }

        @Specialization(guards = "array.isForeignObject()")
        void doArrayLike(StaticObject array, int index, StaticObject value,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile exceptionProfile) {
            assert !StaticObject.isNull(array);
            EspressoLanguage language = getLanguage();
            Object unwrappedValue = value.isForeignObject() ? value.rawForeignObject(language) : value;
            ForeignArrayUtils.writeForeignArrayElement(array, index, unwrappedValue, language, getContext().getMeta(), interop, exceptionProfile);
        }
    }
}
