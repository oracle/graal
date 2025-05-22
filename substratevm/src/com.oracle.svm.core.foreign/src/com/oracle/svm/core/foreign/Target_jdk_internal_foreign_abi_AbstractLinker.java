/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.  Oracle designates this
 * particular file as subject to the "Classpath" exception as provided
 * by Oracle in the LICENSE file that accompanied this code.
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
package com.oracle.svm.core.foreign;

import java.lang.constant.DirectMethodHandleDesc;
import java.lang.constant.MethodHandleDesc;
import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.MemorySegment;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.util.Optional;

import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

import jdk.internal.foreign.abi.AbstractLinker;
import jdk.internal.foreign.abi.AbstractLinker.UpcallStubFactory;
import jdk.internal.foreign.abi.LinkerOptions;
import jdk.internal.foreign.abi.aarch64.linux.LinuxAArch64Linker;
import jdk.internal.foreign.abi.aarch64.macos.MacOsAArch64Linker;
import jdk.internal.foreign.abi.x64.sysv.SysVx64Linker;
import jdk.internal.foreign.abi.x64.windows.Windowsx64Linker;

@TargetClass(value = AbstractLinker.class, onlyWith = ForeignAPIPredicates.FunctionCallsSupported.class)
public final class Target_jdk_internal_foreign_abi_AbstractLinker {
    // Checkstyle: stop
    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClassName = "jdk.internal.foreign.abi.SoftReferenceCache") //
    private Target_jdk_internal_foreign_abi_SoftReferenceCache DOWNCALL_CACHE;

    @Alias //
    @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.NewInstance, declClassName = "jdk.internal.foreign.abi.SoftReferenceCache") //
    private Target_jdk_internal_foreign_abi_SoftReferenceCache UPCALL_CACHE;
    // Checkstyle: resume
}

@TargetClass(className = "jdk.internal.foreign.abi.SoftReferenceCache", onlyWith = ForeignAPIPredicates.FunctionCallsSupported.class)
final class Target_jdk_internal_foreign_abi_SoftReferenceCache {
}

/**
 * A decorator for jdk.internal.foreign.abi.UpcallStubFactory which intercepts the call to method
 * 'makeStub'. It will (1) call the original factory to create the upcall, and (2) then use the
 * method handle's descriptor to lookup if a specialized (direct) upcall stub is available. If so,
 * the trampoline will be updated with the specialized stub's address.
 * 
 * @param delegate The original upcall stub factory as created by JDK's call arranger.
 */
record UpcallStubFactoryDecorator(UpcallStubFactory delegate, FunctionDescriptor function, LinkerOptions options) implements UpcallStubFactory {

    @Override
    public MemorySegment makeStub(MethodHandle target, Arena arena) {
        MemorySegment segment = delegate.makeStub(target, arena);

        /*
         * We cannot do this in 'UpcallLinker.makeUpcallStub' because that one already gets a
         * different method handle that will handle parameter/return value bindings. Further, method
         * handles cannot be compared. If the provided method handle is a DirectMethodHandle, we use
         * the MH descriptor to check if there is a registered direct upcall stub. Then, we will
         * patch the already allocated trampoline with a different upcall stub pointer.
         */
        Optional<MethodHandleDesc> methodHandleDesc = target.describeConstable();
        if (methodHandleDesc.isPresent() && methodHandleDesc.get() instanceof DirectMethodHandleDesc desc) {
            ForeignFunctionsRuntime.singleton().patchForDirectUpcall(segment.address(), desc, function, options);
        }
        return segment;
    }
}

@TargetClass(value = SysVx64Linker.class, onlyWith = ForeignAPIPredicates.FunctionCallsSupported.class)
final class Target_jdk_internal_foreign_abi_x64_sysv_SysVx64Linker {

    @SuppressWarnings("static-method")
    @Substitute
    UpcallStubFactory arrangeUpcall(MethodType targetType, FunctionDescriptor function, LinkerOptions options) {
        return new UpcallStubFactoryDecorator(jdk.internal.foreign.abi.x64.sysv.CallArranger.arrangeUpcall(targetType, function, options), function, options);
    }
}

@TargetClass(value = Windowsx64Linker.class, onlyWith = ForeignAPIPredicates.FunctionCallsSupported.class)
final class Target_jdk_internal_foreign_abi_x64_windows_Windowsx64Linker {

    @SuppressWarnings("static-method")
    @Substitute
    UpcallStubFactory arrangeUpcall(MethodType targetType, FunctionDescriptor function, LinkerOptions options) {
        return new UpcallStubFactoryDecorator(jdk.internal.foreign.abi.x64.windows.CallArranger.arrangeUpcall(targetType, function, options), function, options);
    }
}

@TargetClass(value = MacOsAArch64Linker.class, onlyWith = ForeignAPIPredicates.FunctionCallsSupported.class)
final class Target_jdk_internal_foreign_abi_aarch64_macos_MacOsAArch64Linker {

    @SuppressWarnings("static-method")
    @Substitute
    UpcallStubFactory arrangeUpcall(MethodType targetType, FunctionDescriptor function, LinkerOptions options) {
        return new UpcallStubFactoryDecorator(jdk.internal.foreign.abi.aarch64.CallArranger.MACOS.arrangeUpcall(targetType, function, options), function, options);
    }
}

@TargetClass(value = LinuxAArch64Linker.class, onlyWith = ForeignAPIPredicates.FunctionCallsSupported.class)
final class Target_jdk_internal_foreign_abi_aarch64_linux_LinuxAArch64Linker {

    @SuppressWarnings("static-method")
    @Substitute
    UpcallStubFactory arrangeUpcall(MethodType targetType, FunctionDescriptor function, LinkerOptions options) {
        return new UpcallStubFactoryDecorator(jdk.internal.foreign.abi.aarch64.CallArranger.LINUX.arrangeUpcall(targetType, function, options), function, options);
    }
}
