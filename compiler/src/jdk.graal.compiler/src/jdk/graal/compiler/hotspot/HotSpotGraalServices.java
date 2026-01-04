/*
 * Copyright (c) 2019, 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot;

import java.lang.reflect.Field;
import java.util.Objects;

import jdk.graal.compiler.core.common.LibGraalSupport;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;
import jdk.vm.ci.hotspot.HotSpotObjectConstantScope;
import jdk.vm.ci.hotspot.HotSpotProfilingInfo;
import jdk.vm.ci.hotspot.HotSpotSpeculationLog;
import jdk.vm.ci.hotspot.VMIntrinsicMethod;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ProfilingInfo;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.SpeculationLog;

/**
 * Interface to HotSpot specific functionality that abstracts over which JDK version Graal is
 * running on.
 */
public class HotSpotGraalServices {

    /**
     * Read the decompile count from the {@code HotSpotMethodData} and either bail out if the count
     * is too high or enable some debugging logic to detect the cause of the cycle.
     */
    public static int getDecompileCount(ResolvedJavaMethod method) {
        ProfilingInfo info = method.getProfilingInfo();
        if (info instanceof HotSpotProfilingInfo hotSpotProfilingInfo) {
            return hotSpotProfilingInfo.getDecompileCount();
        }
        return -1;
    }

    /**
     * Enters the global context. This is useful to escape a local context for execution that will
     * create foreign object references that need to outlive the local context.
     *
     * Foreign object references encapsulated by {@link JavaConstant}s created in the global context
     * are only subject to reclamation once the {@link JavaConstant} wrapper dies.
     *
     * @return {@code null} if the current runtime does not support remote object references or if
     *         this thread is currently in the global context
     */
    public static CompilationContext enterGlobalCompilationContext() {
        HotSpotObjectConstantScope impl = HotSpotObjectConstantScope.enterGlobalScope();
        return impl == null ? null : new CompilationContext(impl);
    }

    /**
     * Opens a local context that upon closing, will release foreign object references encapsulated
     * by {@link JavaConstant}s created in the context.
     *
     * @param description an non-null object whose {@link Object#toString()} value describes the
     *            context being opened
     * @return {@code null} if the current runtime does not support remote object references
     */
    public static CompilationContext openLocalCompilationContext(Object description) {
        HotSpotObjectConstantScope impl = HotSpotObjectConstantScope.openLocalScope(Objects.requireNonNull(description));
        return impl == null ? null : new CompilationContext(impl);
    }

    /**
     * Exits Graal's runtime. This calls {@link System#exit(int)} in HotSpot's runtime if possible
     * otherwise calls {@link System#exit(int)} in the current runtime.
     *
     * This exists so that the HotSpot VM can be exited from within libgraal.
     */
    public static void exit(int status, HotSpotJVMCIRuntime runtime) {
        if (LibGraalSupport.inLibGraalRuntime()) {
            runtime.exitHotSpot(status);
        } else {
            System.exit(status);
        }
    }

    public static SpeculationLog newHotSpotSpeculationLog(long cachedFailedSpeculationsAddress) {
        return new HotSpotSpeculationLog(cachedFailedSpeculationsAddress);
    }

    /**
     * Returns true if the {@code intrinsic} is available in HotSpot's runtime. Note that this flag
     * is affected by -XX:ControlIntrinsic, -XX:DisableIntrinsic, and -XX:-UseXXXIntrinsic.
     */
    public static boolean isIntrinsicAvailable(VMIntrinsicMethod intrinsic) {
        try {
            Field isAvailable = VMIntrinsicMethod.class.getField("isAvailable");
            return isAvailable.getBoolean(intrinsic);
        } catch (ReflectiveOperationException e) {
            return true;
        }
    }

    /**
     * Returns true if the {@code intrinsic} is supported by HotSpot's C2 compiler.
     */
    public static boolean isIntrinsicSupportedByC2(VMIntrinsicMethod intrinsic) {
        try {
            Field c2Supported = VMIntrinsicMethod.class.getField("c2Supported");
            return c2Supported.getBoolean(intrinsic);
        } catch (ReflectiveOperationException e) {
            return true;
        }
    }
}
