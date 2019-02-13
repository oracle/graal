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

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import com.oracle.truffle.api.CompilerAsserts;
import com.oracle.truffle.api.CompilerDirectives.TruffleBoundary;
import com.oracle.truffle.llvm.nodes.asm.support.LLVMString;
import com.oracle.truffle.llvm.runtime.memory.LLVMMemory;
import com.oracle.truffle.llvm.runtime.pointer.LLVMNativePointer;

public class LLVMInfo {
    // See http://man7.org/linux/man-pages/man2/uname.2.html and
    // https://github.com/torvalds/linux/blob/master/include/uapi/linux/utsname.h
    private static final int UTS_FIELD_LENGTH = 65;

    public static final String SYSNAME;
    public static final String RELEASE;
    public static final String MACHINE;

    static {
        SYSNAME = System.getProperty("os.name");
        RELEASE = System.getProperty("os.version");
        String arch = System.getProperty("os.arch");
        if ("amd64".equals(arch)) {
            arch = "x86_64";
        }
        MACHINE = arch;
    }

    private static String readFile(String name, String fallback) {
        CompilerAsserts.neverPartOfCompilation();
        try {
            Path path = Paths.get(name);
            if (Files.exists(path)) {
                return new String(Files.readAllBytes(path)).trim();
            }
        } catch (Exception e) {
        }
        return fallback;
    }

    @TruffleBoundary
    public static String getHostname() {
        String hostname = readFile("/proc/sys/kernel/hostname", null);
        if (hostname != null) {
            return hostname;
        }
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return null;
        }
    }

    @TruffleBoundary
    public static String getVersion() {
        return readFile("/proc/sys/kernel/version", null);
    }

    @TruffleBoundary
    public static String getDomainName() {
        return readFile("/proc/sys/kernel/domainname", null);
    }

    public static long uname(LLVMMemory memory, LLVMNativePointer name) {
        LLVMNativePointer ptr = name;
        LLVMString.strcpy(memory, ptr, SYSNAME);
        ptr = ptr.increment(UTS_FIELD_LENGTH);
        LLVMString.strcpy(memory, ptr, getHostname());
        ptr = ptr.increment(UTS_FIELD_LENGTH);
        LLVMString.strcpy(memory, ptr, RELEASE);
        ptr = ptr.increment(UTS_FIELD_LENGTH);
        LLVMString.strcpy(memory, ptr, getVersion());
        ptr = ptr.increment(UTS_FIELD_LENGTH);
        LLVMString.strcpy(memory, ptr, MACHINE);
        ptr = ptr.increment(UTS_FIELD_LENGTH);
        LLVMString.strcpy(memory, ptr, getDomainName());
        return 0;
    }

    @TruffleBoundary
    private static LLVMProcessStat getstat() {
        String stat = readFile("/proc/self/stat", null);
        if (stat == null) {
            return null;
        } else {
            return new LLVMProcessStat(stat);
        }
    }

    @TruffleBoundary
    public static long getpid() {
        LLVMProcessStat stat = getstat();
        if (stat != null) {
            return stat.getPid();
        }
        String info = java.lang.management.ManagementFactory.getRuntimeMXBean().getName();
        return Long.parseLong(info.split("@")[0]);
    }

    @TruffleBoundary
    public static long getppid() {
        LLVMProcessStat stat = getstat();
        if (stat != null) {
            return stat.getPpid();
        } else {
            return 1; // fallback: init
        }
    }
}
