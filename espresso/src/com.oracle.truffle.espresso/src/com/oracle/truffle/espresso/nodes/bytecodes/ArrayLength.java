/*
 * Copyright (c) 2021, 2024, Oracle and/or its affiliates. All rights reserved.
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
import com.oracle.truffle.api.dsl.GenerateUncached;
import com.oracle.truffle.api.dsl.ImportStatic;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.espresso.EspressoLanguage;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.nodes.EspressoNode;
import com.oracle.truffle.espresso.nodes.quick.interop.Utils;
import com.oracle.truffle.espresso.runtime.staticobject.StaticObject;
import com.oracle.truffle.espresso.vm.InterpreterToVM;

/**
 * ARRAYLENGTH bytecode with interop extensions.
 *
 *
 * <h3>Extensions</h3>
 * <ul>
 * <li>For Truffle buffers ({@link InteropLibrary#hasBufferElements(Object) buffer-like} foreign
 * objects) wrapped as {@code byte[]}, ARRAYLENGTH is mapped to
 * {@link InteropLibrary#getBufferSize(Object)}.</li>
 * <li>For {@link InteropLibrary#hasArrayElements(Object) array-like} foreign objects wrapped as
 * {@code byte[]}, ARRAYLENGTH is mapped to {@link InteropLibrary#getBufferSize(Object)}.</li>
 * </ul>
 *
 * <b>Note</b>
 * <p>
 * If the size of the foreign array does NOT fit in an int, {@link Integer#MAX_VALUE} is returned.
 * </p>
 */
public abstract class ArrayLength {

    @GenerateUncached
    @ImportStatic(Utils.class)
    @NodeInfo(shortName = "ARRAYLENGTH !nullcheck")
    public abstract static class WithoutNullCheck extends EspressoNode {
        protected static final int LIMIT = 2;

        public final int executeAsInt(StaticObject array) {
            long arrayLength = executeAsLong(array);
            if (arrayLength > Integer.MAX_VALUE) {
                return Integer.MAX_VALUE;
            }
            return (int) arrayLength;
        }

        public abstract long executeAsLong(StaticObject array);

        @Specialization(guards = "array.isEspressoObject()")
        long doEspresso(StaticObject array) {
            assert !StaticObject.isNull(array);
            return InterpreterToVM.arrayLength(array, getLanguage());
        }

        @Specialization(guards = {
                        "array.isForeignObject()",
                        "isBufferLikeByteArray(language, getMeta(), interop, array)",
        })
        long doBufferLike(StaticObject array,
                        @Bind("getLanguage()") EspressoLanguage language,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            assert !StaticObject.isNull(array);
            try {
                return interop.getBufferSize(array.rawForeignObject(language));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        @Specialization(guards = {
                        "array.isForeignObject()",
                        "!isBufferLikeByteArray(language, getMeta(), interop, array)",
                        "isArrayLike(interop, array.rawForeignObject(language))"
        })
        long doArrayLike(StaticObject array,
                        @Bind("getLanguage()") EspressoLanguage language,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop) {
            assert !StaticObject.isNull(array);
            try {
                return interop.getArraySize(array.rawForeignObject(language));
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreterAndInvalidate();
                throw EspressoError.shouldNotReachHere(e);
            }
        }
    }
}
