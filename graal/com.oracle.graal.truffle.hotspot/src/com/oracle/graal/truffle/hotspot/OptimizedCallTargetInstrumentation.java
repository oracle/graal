/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.graal.truffle.hotspot;

import static com.oracle.graal.truffle.hotspot.UnsafeAccess.UNSAFE;

import java.lang.reflect.Field;

import jdk.internal.jvmci.code.CodeCacheProvider;
import jdk.internal.jvmci.code.CompilationResult;
import jdk.internal.jvmci.code.CompilationResult.Mark;
import jdk.internal.jvmci.common.JVMCIError;
import jdk.internal.jvmci.hotspot.HotSpotCodeCacheProvider;
import jdk.internal.jvmci.hotspot.HotSpotVMConfig;

import com.oracle.graal.asm.Assembler;
import com.oracle.graal.compiler.common.spi.ForeignCallsProvider;
import com.oracle.graal.hotspot.HotSpotGraalRuntime;
import com.oracle.graal.hotspot.meta.HotSpotRegistersProvider;
import com.oracle.graal.lir.asm.CompilationResultBuilder;
import com.oracle.graal.lir.asm.FrameContext;
import com.oracle.graal.lir.framemap.FrameMap;
import com.oracle.graal.truffle.OptimizedCallTarget;

/**
 * Mechanism for injecting special code into {@link OptimizedCallTarget#call(Object[])} .
 */
public abstract class OptimizedCallTargetInstrumentation extends CompilationResultBuilder {

    public OptimizedCallTargetInstrumentation(CodeCacheProvider codeCache, ForeignCallsProvider foreignCalls, FrameMap frameMap, Assembler asm, FrameContext frameContext,
                    CompilationResult compilationResult) {
        super(codeCache, foreignCalls, frameMap, asm, frameContext, compilationResult);
    }

    @Override
    public Mark recordMark(Object id) {
        Mark mark = super.recordMark(id);
        HotSpotCodeCacheProvider hsCodeCache = (HotSpotCodeCacheProvider) codeCache;
        if ((int) id == hsCodeCache.config.MARKID_VERIFIED_ENTRY) {
            HotSpotRegistersProvider registers = HotSpotGraalRuntime.runtime().getHostProviders().getRegisters();
            injectTailCallCode(HotSpotGraalRuntime.runtime().getConfig(), registers);
        }
        return mark;
    }

    protected static int getFieldOffset(String name, Class<?> declaringClass) {
        try {
            declaringClass.getDeclaredField(name).setAccessible(true);
            Field field = declaringClass.getDeclaredField(name);
            return (int) UNSAFE.objectFieldOffset(field);
        } catch (NoSuchFieldException | SecurityException e) {
            throw JVMCIError.shouldNotReachHere();
        }
    }

    /**
     * Injects code into the verified entry point of that makes a tail-call to the target callee.
     */
    protected abstract void injectTailCallCode(HotSpotVMConfig config, HotSpotRegistersProvider registers);
}
