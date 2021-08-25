/*
 * Copyright (c) 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.library.internal;

import com.oracle.truffle.api.dsl.GenerateAOT;
import com.oracle.truffle.api.library.GenerateLibrary;
import com.oracle.truffle.api.library.Library;
import com.oracle.truffle.api.library.LibraryFactory;
import com.oracle.truffle.llvm.runtime.except.LLVMException;

@GenerateLibrary
@GenerateAOT
public abstract class LLVMCopyTargetLibrary extends Library {

    static final LibraryFactory<LLVMCopyTargetLibrary> FACTORY = LibraryFactory.resolve(LLVMCopyTargetLibrary.class);

    public static LibraryFactory<LLVMCopyTargetLibrary> getFactory() {
        return FACTORY;
    }

    public boolean canCopyFrom(@SuppressWarnings("unused") Object receiver, @SuppressWarnings("unused") Object source, @SuppressWarnings("unused") long length) {
        return false;
    }

    public void copyFrom(@SuppressWarnings("unused") Object receiver, @SuppressWarnings("unused") Object source, @SuppressWarnings("unused") long length) {
        throw new InvalidSourceException();
    }

    public class InvalidSourceException extends LLVMException {
        private static final long serialVersionUID = 3841115158039117295L;

        public InvalidSourceException() {
            super(null);
        }
    }

}
