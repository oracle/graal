/*
 * Copyright (c) 2013, 2016, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.hotspot;

import static org.graalvm.compiler.truffle.hotspot.UnsafeAccess.UNSAFE;

import java.lang.reflect.Field;

import org.graalvm.compiler.asm.Assembler;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.spi.ForeignCallsProvider;
import org.graalvm.compiler.core.common.util.Util;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.hotspot.GraalHotSpotVMConfig;
import org.graalvm.compiler.hotspot.meta.HotSpotRegistersProvider;
import org.graalvm.compiler.lir.asm.CompilationResultBuilder;
import org.graalvm.compiler.lir.asm.DataBuilder;
import org.graalvm.compiler.lir.asm.FrameContext;
import org.graalvm.compiler.lir.framemap.FrameMap;
import org.graalvm.compiler.options.OptionValues;
import org.graalvm.compiler.truffle.OptimizedCallTarget;

import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.site.Mark;

/**
 * Mechanism for injecting special code into {@link OptimizedCallTarget#call(Object[])} .
 */
public abstract class OptimizedCallTargetInstrumentation extends CompilationResultBuilder {
    protected final GraalHotSpotVMConfig config;
    protected final HotSpotRegistersProvider registers;

    public OptimizedCallTargetInstrumentation(CodeCacheProvider codeCache, ForeignCallsProvider foreignCalls, FrameMap frameMap, Assembler asm, DataBuilder dataBuilder, FrameContext frameContext,
                    OptionValues options, CompilationResult compilationResult, GraalHotSpotVMConfig config, HotSpotRegistersProvider registers) {
        super(codeCache, foreignCalls, frameMap, asm, dataBuilder, frameContext, options, compilationResult);
        this.config = config;
        this.registers = registers;
    }

    @Override
    public Mark recordMark(Object id) {
        Mark mark = super.recordMark(id);
        if ((int) id == config.MARKID_VERIFIED_ENTRY) {
            injectTailCallCode();
        }
        return mark;
    }

    protected static int getFieldOffset(String name, Class<?> declaringClass) {
        try {
            Field field = declaringClass.getDeclaredField(name);
            Util.setAccessible(field, true);
            return (int) UNSAFE.objectFieldOffset(field);
        } catch (NoSuchFieldException | SecurityException e) {
            throw GraalError.shouldNotReachHere();
        }
    }

    /**
     * Injects code into the verified entry point of that makes a tail-call to the target callee.
     */
    protected abstract void injectTailCallCode();
}
