/*
 * Copyright (c) 2017, 2020, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.runtime.nodes.asm.syscall;

import static com.oracle.truffle.llvm.runtime.nodes.asm.syscall.LLVMAMD64Time.CLOCK_MONOTONIC;
import static com.oracle.truffle.llvm.runtime.nodes.asm.syscall.LLVMAMD64Time.CLOCK_REALTIME;

import com.oracle.truffle.api.CompilerDirectives;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNode;
import com.oracle.truffle.llvm.runtime.memory.LLVMSyscallOperationNode;
import com.oracle.truffle.llvm.runtime.nodes.memory.store.LLVMI64StoreNodeGen;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;
import com.oracle.truffle.llvm.runtime.pointer.LLVMPointer;

public abstract class LLVMAMD64SyscallClockGetTimeNode extends LLVMSyscallOperationNode {

    @Child private LLVMI64StoreNode writeI64;

    public LLVMAMD64SyscallClockGetTimeNode() {
        this.writeI64 = LLVMI64StoreNodeGen.create(null, null);
    }

    @Override
    public final String getName() {
        return "clock_gettime";
    }

    @Specialization
    protected long doI64(long clkId, LLVMPointer tp) {
        return clockGetTime((int) clkId, tp);
    }

    @Specialization
    protected long doI64(long clkId, long tp) {
        return doI64(clkId, LLVMNativePointer.create(tp));
    }

    // TODO (GR-22032): Remove the TruffleBoundary
    @CompilerDirectives.TruffleBoundary
    private static long getCurrentTimeMillis() {
        return System.currentTimeMillis();
    }

    private int clockGetTime(int clkId, LLVMPointer timespec) {
        long s;
        long ns;
        switch (clkId) {
            case CLOCK_REALTIME: {
                long t = getCurrentTimeMillis();
                s = t / 1000;
                ns = (t % 1000) * 1000000;
                break;
            }
            case CLOCK_MONOTONIC: {
                long t = System.nanoTime();
                s = t / 1000000000L;
                ns = (t % 1000000000L);
                break;
            }
            default:
                return -LLVMAMD64Error.EINVAL;
        }
        LLVMPointer ptr = timespec;
        writeI64.executeWithTarget(ptr, s);
        ptr = ptr.increment(8);
        writeI64.executeWithTarget(ptr, ns);
        return 0;
    }
}
