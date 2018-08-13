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
package com.oracle.truffle.llvm.nodes.asm.syscall;

import com.oracle.truffle.api.CompilerAsserts;

public class LLVMProcessStat {
    private final String stat;

    private final long pid;
    private final String comm;
    private final char state;
    private final long ppid;
    private final long pgrp;
    private final long session;
    private final long ttyNr;
    private final long tpgid;

    public LLVMProcessStat(String stat) {
        CompilerAsserts.neverPartOfCompilation();
        this.stat = stat;
        String[] fields = stat.split(" ");
        pid = Long.parseLong(fields[0]);
        comm = fields[1];
        state = fields[2].charAt(0);
        ppid = Long.parseLong(fields[3]);
        pgrp = Long.parseLong(fields[4]);
        session = Long.parseLong(fields[5]);
        ttyNr = Long.parseLong(fields[6]);
        tpgid = Long.parseLong(fields[7]);
    }

    public long getPid() {
        return pid;
    }

    public String getComm() {
        return comm;
    }

    public char getState() {
        return state;
    }

    public long getPpid() {
        return ppid;
    }

    public long getPgrp() {
        return pgrp;
    }

    public long getSession() {
        return session;
    }

    public long getTTYNr() {
        return ttyNr;
    }

    public long getTpgid() {
        return tpgid;
    }

    @Override
    public String toString() {
        return stat;
    }
}
