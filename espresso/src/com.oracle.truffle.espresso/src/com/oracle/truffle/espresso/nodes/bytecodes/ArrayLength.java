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
import com.oracle.truffle.api.interop.UnsupportedMessageException;
import com.oracle.truffle.api.library.CachedLibrary;
import com.oracle.truffle.api.nodes.Node;
import com.oracle.truffle.api.nodes.NodeInfo;
import com.oracle.truffle.api.profiles.BranchProfile;
import com.oracle.truffle.espresso.meta.EspressoError;
import com.oracle.truffle.espresso.nodes.quick.interop.Utils;
import com.oracle.truffle.espresso.runtime.EspressoContext;
import com.oracle.truffle.espresso.runtime.StaticObject;
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
@GenerateUncached
@NodeInfo(shortName = "ARRAYLENGTH")
public abstract class ArrayLength {

    public abstract byte execute(StaticObject receiver);

    @Specialization
    int executeWithNullCheck(StaticObject array,
                    @Cached NullCheck nullCheck,
                    @Cached WithoutNullCheck arrayLength) {
        return arrayLength.execute(nullCheck.execute(array));
    }

    @GenerateUncached
    @ImportStatic(Utils.class)
    @NodeInfo(shortName = "ARRAYLENGTH !nullcheck")
    public abstract static class WithoutNullCheck extends Node {
        protected static final int LIMIT = 2;

        public abstract int execute(StaticObject array);

        protected EspressoContext getContext() {
            return EspressoContext.get(this);
        }

        @Specialization(guards = "array.isEspressoObject()")
        int doEspresso(StaticObject array) {
            assert !StaticObject.isNull(array);
            return InterpreterToVM.arrayLength(array);
        }

        @Specialization(guards = {
                        "array.isForeignObject()",
                        "isBufferLikeByteArray(context, interop, array)",
        })
        int doBufferLike(StaticObject array,
                        @SuppressWarnings("unused") @Bind("getContext()") EspressoContext context,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile sizeOverflowProfile) {
            assert !StaticObject.isNull(array);
            try {
                long bufferLength = interop.getBufferSize(array.rawForeignObject());
                if (bufferLength > Integer.MAX_VALUE) {
                    sizeOverflowProfile.enter();
                    return Integer.MAX_VALUE;
                }
                return (int) bufferLength;
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw EspressoError.shouldNotReachHere(e);
            }
        }

        @Specialization(guards = {
                        "array.isForeignObject()",
                        "!isBufferLikeByteArray(context, interop, array)",
                        "isArrayLike(interop, array.rawForeignObject())"
        })
        int doArrayLike(StaticObject array,
                        @SuppressWarnings("unused") @Bind("getContext()") EspressoContext context,
                        @CachedLibrary(limit = "LIMIT") InteropLibrary interop,
                        @Cached BranchProfile sizeOverflowProfile) {
            assert !StaticObject.isNull(array);
            try {
                long arrayLength = interop.getArraySize(array.rawForeignObject());
                if (arrayLength > Integer.MAX_VALUE) {
                    sizeOverflowProfile.enter();
                    return Integer.MAX_VALUE;
                }
                return (int) arrayLength;
            } catch (UnsupportedMessageException e) {
                CompilerDirectives.transferToInterpreter();
                throw EspressoError.shouldNotReachHere(e);
            }
        }
    }
}
