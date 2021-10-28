/*
 * Copyright (c) 2017, 2021, Oracle and/or its affiliates.
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
package com.oracle.truffle.llvm.parser.factories;

import java.nio.ByteOrder;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

import com.oracle.truffle.llvm.runtime.LLVMSyscallEntry;
import com.oracle.truffle.llvm.runtime.NativeContextExtension;
import com.oracle.truffle.llvm.runtime.memory.LLVMSyscallOperationNode;
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.LLVMInfo;
import com.oracle.truffle.llvm.runtime.nodes.asm.syscall.LLVMNativeSyscallNode;

public abstract class BasicPlatformCapability<S extends Enum<S> & LLVMSyscallEntry> extends PlatformCapabilityBase<S> {

    // Flags for DLOpen
    public static final int RTLD_LAZY = 1;
    public static final int RTLD_GLOBAL = 256;
    public static final long RTLD_DEFAULT = 0;

    @Override
    public ByteOrder getPlatformByteOrder() {
        return ByteOrder.nativeOrder();
    }

    @Override
    public boolean isGlobalDLOpenFlagSet(int flag) {
        return (flag & RTLD_GLOBAL) == RTLD_GLOBAL;
    }

    @Override
    public boolean isLazyDLOpenFlagSet(int flag) {
        return (flag & RTLD_LAZY) == RTLD_LAZY;
    }

    @Override
    public boolean isFirstDLOpenFlagSet(int flag) {
        return false;
    }

    @Override
    public boolean isDefaultDLSymFlagSet(long flag) {
        return flag == RTLD_DEFAULT;
    }

    public static BasicPlatformCapability<?> create(boolean loadCxxLibraries) {
        if (LLVMInfo.SYSNAME.equalsIgnoreCase("linux")) {
            if (LLVMInfo.MACHINE.equalsIgnoreCase("x86_64")) {
                return new LinuxAMD64PlatformCapability(loadCxxLibraries);
            }
            if (LLVMInfo.MACHINE.equalsIgnoreCase("aarch64")) {
                return new LinuxAArch64PlatformCapability(loadCxxLibraries);
            }
        }
        if (LLVMInfo.SYSNAME.equalsIgnoreCase("mac os x") && LLVMInfo.MACHINE.equalsIgnoreCase("x86_64")) {
            return new DarwinAMD64PlatformCapability(loadCxxLibraries);
        }
        if (LLVMInfo.SYSNAME.toLowerCase(Locale.ROOT).startsWith("windows") && LLVMInfo.MACHINE.equalsIgnoreCase("x86_64")) {
            return new WindowsAMD64PlatformCapability(loadCxxLibraries);
        }
        return new UnknownBasicPlatformCapability(loadCxxLibraries);
    }

    private static final Path SULONG_LIBDIR = Paths.get("native", "lib");
    public static final String LIBSULONG_FILENAME = NativeContextExtension.getNativeLibrary("sulong");
    public static final String LIBSULONGXX_FILENAME = NativeContextExtension.getNativeLibrary("sulong++");

    protected BasicPlatformCapability(Class<S> cls, boolean loadCxxLibraries) {
        super(cls, loadCxxLibraries);
    }

    @Override
    public String getBuiltinsLibrary() {
        return NativeContextExtension.getNativeLibraryVersioned("graalvm-llvm", 1);
    }

    @Override
    public Path getSulongLibrariesPath() {
        return SULONG_LIBDIR;
    }

    @Override
    public String getLibsulongxxFilename() {
        return LIBSULONGXX_FILENAME;
    }

    @Override
    public String getLibsulongFilename() {
        return LIBSULONG_FILENAME;
    }

    @Override
    public LLVMSyscallOperationNode createSyscallNode(long index) {
        try {
            return createSyscallNode(getSyscall(index));
        } catch (IllegalArgumentException e) {
            return new LLVMNativeSyscallNode(index);
        }
    }

    @Override
    public String getLibrarySuffix() {
        return NativeContextExtension.getNativeLibrarySuffix();
    }

    protected abstract LLVMSyscallOperationNode createSyscallNode(S syscall);
}
