/*
 * Copyright (c) 2017, 2018, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.profiling;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.CompilerDirectives.CompilationFinal;
import com.oracle.truffle.api.Truffle;
import com.oracle.truffle.api.nodes.NodeCloneable;

public abstract class ShortValueProfile extends NodeCloneable {

    ShortValueProfile() {
    }

    public abstract short profile(short value);

    public static ShortValueProfile create() {
        if (Truffle.getRuntime().isProfilingEnabled()) {
            return new Enabled();
        } else {
            return new Disabled();
        }
    }

    static final class Enabled extends ShortValueProfile {

        private static final byte UNINITIALIZED = 0;
        private static final byte SPECIALIZED = 1;
        private static final byte GENERIC = 2;

        @CompilationFinal private short cachedValue;
        @CompilationFinal private byte state = 0;

        @Override
        public short profile(short value) {
            byte localState = this.state;
            if (localState != GENERIC) {
                if (localState == SPECIALIZED) {
                    short v = cachedValue;
                    if (v == value) {
                        return v;
                    }
                }
                CompilerDirectives.transferToInterpreterAndInvalidate();
                if (localState == UNINITIALIZED) {
                    this.cachedValue = value;
                    this.state = SPECIALIZED;
                } else {
                    this.state = GENERIC;
                }
            }
            return value;
        }
    }

    static final class Disabled extends ShortValueProfile {

        @Override
        public short profile(short value) {
            return value;
        }
    }
}
