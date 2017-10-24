/*
 * Copyright (c) 2017, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.nodes.asm.syscall;

import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.LLVMAddress;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;

import static com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64Time.CLOCK_MONOTONIC;
import static com.oracle.truffle.llvm.nodes.asm.syscall.LLVMAMD64Time.CLOCK_REALTIME;

public abstract class LLVMAMD64SyscallClockGetTimeNode extends LLVMAMD64SyscallOperationNode {
    public LLVMAMD64SyscallClockGetTimeNode() {
        super("clock_gettime");
    }

    @Specialization
    protected long executeI64(long clkId, LLVMAddress tp) {
        return clockGetTime((int) clkId, tp);
    }

    @Specialization
    protected long executeI64(long clkId, long tp) {
        return executeI64(clkId, LLVMAddress.fromLong(tp));
    }

    @TruffleBoundary
    private static long currentTimeMillis() {
        return System.currentTimeMillis();
    }

    @TruffleBoundary
    private static long nanoTime() {
        return System.nanoTime();
    }

    public static int clockGetTime(int clkId, LLVMAddress timespec) {
        long s;
        long ns;
        switch (clkId) {
            case CLOCK_REALTIME: {
                long t = currentTimeMillis();
                s = t / 1000;
                ns = (t % 1000) * 1000000;
                break;
            }
            case CLOCK_MONOTONIC: {
                long t = nanoTime();
                s = t / 1000000000L;
                ns = (t % 1000000000L);
                break;
            }
            default:
                return -LLVMAMD64Error.EINVAL;
        }
        LLVMAddress ptr = timespec;
        LLVMMemory.putI64(ptr, s);
        ptr = ptr.increment(8);
        LLVMMemory.putI64(ptr, ns);
        return 0;
    }
}
