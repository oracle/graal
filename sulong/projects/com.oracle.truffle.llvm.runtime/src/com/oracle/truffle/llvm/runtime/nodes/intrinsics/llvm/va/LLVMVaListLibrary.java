/*
 * Copyright (c) 2020, 2022, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va;

import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.frame.Frame;
import com.oracle.truffle.api.frame.FrameInstance;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.llvm.runtime.types.Type;

/**
 * This library is a sort of SPI that allows the vararg infrastructure to be split into platform
 * independent and platform specific code. The platform independent code (residing in
 * <code>com.oracle.truffle.llvm.runtime.nodes.intrinsics.llvm.va</code>) uses
 * {@link LLVMVaListLibrary} to manipulate platform specific <code>va_list</code> managed objects.
 * <p/>
 * Note: This is a sort of private library that should be used only from the <code>va_list</code>
 * intrinsic nodes such as {@link LLVMVAStart}, {@link LLVMVACopy}, {@link LLVMVAEnd} and
 * {@link LLVMVAArg}, which all use the cached version of the library. All uncached versions of this
 * library are disabled because of the {@link Frame} arguments in the methods. The reasons for that
 * restriction is to prevent a virtual (non-materialized) frame from leaking accidentally behind the
 * Truffle boundary. See {@link LLVMVaListLibrary.NoUncachedAssert}.
 *
 * @see LLVMVAStart
 * @see LLVMVAEnd
 * @see LLVMVACopy
 * @see LLVMVAArg
 */
@GenerateLibrary(assertions = LLVMVaListLibrary.NoUncachedAssert.class)
@GenerateAOT
public abstract class LLVMVaListLibrary extends Library {

    static final LibraryFactory<LLVMVaListLibrary> FACTORY = LibraryFactory.resolve(LLVMVaListLibrary.class);
    private static final Object STOP_ITERATE = new Object();

    public static LibraryFactory<LLVMVaListLibrary> getFactory() {
        return FACTORY;
    }

    /**
     * Initialize the va_list. It corresponds to the <code>va_start</code> macro.
     *
     * @param vaList
     * @param arguments
     * @param numberOfExplicitArguments
     */
    public abstract void initialize(Object vaList, Object[] arguments, int numberOfExplicitArguments, Frame frame);

    /**
     * Clean up the va_list. It corresponds to the <code>va_end</code> macro.
     *
     * @param vaList
     */
    public abstract void cleanup(Object vaList, Frame frame);

    /**
     * Copy the source va_list to the destination va_list. It corresponds to the
     * <code>va_copy</code> macro.
     *
     * @param srcVaList
     * @param destVaList
     */
    public abstract void copy(Object srcVaList, Object destVaList, Frame frame);

    /**
     * This is method works like the 3-arg <code>copy</code>, except that it obtains the frame using
     * the Truffle runtime. This method should be used only when the frame cannot be obtained
     * otherwise.
     */
    @SuppressWarnings("deprecation")
    public void copyWithoutFrame(Object srcVaList, Object destVaList) {
        Truffle.getRuntime().iterateFrames(frameInstance -> {
            copy(srcVaList, destVaList, frameInstance.getFrame(FrameInstance.FrameAccess.READ_WRITE));
            return STOP_ITERATE;
        });
    }

    /**
     * Shift the va_list argument to the next argument. It corresponds to the <code>va_arg</code>
     * macro, if expanded to the LLVM <code>va_arg</code> instruction.
     *
     * @param vaList the va_list instance
     * @param type the expected argument type
     * @return the current argument before shifting
     */
    public abstract Object shift(Object vaList, Type type, Frame frame);

    static class NoUncachedAssert extends LLVMVaListLibrary {

        @Child private LLVMVaListLibrary delegate;
        private final boolean isUncachedDelegate;

        NoUncachedAssert(LLVMVaListLibrary delegate) {
            this.delegate = delegate;
            isUncachedDelegate = delegate.getClass().getName().contains("Uncached");
        }

        @Override
        public boolean accepts(Object receiver) {
            return delegate.accepts(receiver);
        }

        @Override
        public void initialize(Object vaList, Object[] arguments, int numberOfExplicitArguments, Frame frame) {
            assert frame == null || !isUncachedDelegate;
            delegate.initialize(vaList, arguments, numberOfExplicitArguments, frame);
        }

        @Override
        public void cleanup(Object vaList, Frame frame) {
            assert frame == null || !isUncachedDelegate;
            delegate.cleanup(vaList, frame);
        }

        @Override
        public void copy(Object srcVaList, Object destVaList, Frame frame) {
            assert frame == null || !isUncachedDelegate;
            delegate.copy(srcVaList, destVaList, frame);
        }

        @Override
        public void copyWithoutFrame(Object srcVaList, Object destVaList) {
            delegate.copyWithoutFrame(srcVaList, destVaList);
        }

        @Override
        public Object shift(Object vaList, Type type, Frame frame) {
            assert frame == null || !isUncachedDelegate;
            return delegate.shift(vaList, type, frame);
        }
    }
}
