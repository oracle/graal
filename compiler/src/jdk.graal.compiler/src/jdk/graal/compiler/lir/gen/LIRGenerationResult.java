/*
 * Copyright (c) 2014, 2022, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.lir.gen;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.Equivalence;

import jdk.graal.compiler.core.common.CompilationIdentifier;
import jdk.graal.compiler.core.common.CompilationIdentifier.Verbosity;
import jdk.graal.compiler.core.common.alloc.RegisterAllocationConfig;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.lir.LIR;
import jdk.graal.compiler.lir.LIRInstruction;
import jdk.graal.compiler.lir.framemap.FrameMap;
import jdk.graal.compiler.lir.framemap.FrameMapBuilder;
import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.RegisterConfig;

public class LIRGenerationResult {

    private final LIR lir;
    private final FrameMapBuilder frameMapBuilder;
    private FrameMap frameMap;
    private final RegisterAllocationConfig registerAllocationConfig;
    private final CallingConvention callingConvention;
    /**
     * Records whether the code being generated makes at least one foreign call.
     */
    private boolean hasForeignCall;
    /**
     * Unique identifier of this compilation.
     */
    private CompilationIdentifier compilationId;

    /**
     * Stores comments about a {@link LIRInstruction} , e.g., which phase created it.
     */
    private EconomicMap<LIRInstruction, String> comments;

    public LIRGenerationResult(CompilationIdentifier compilationId, LIR lir, FrameMapBuilder frameMapBuilder, RegisterAllocationConfig registerAllocationConfig, CallingConvention callingConvention) {
        this.lir = lir;
        this.frameMapBuilder = frameMapBuilder;
        this.registerAllocationConfig = registerAllocationConfig;
        this.callingConvention = callingConvention;
        this.compilationId = compilationId;
    }

    public RegisterAllocationConfig getRegisterAllocationConfig() {
        return registerAllocationConfig;
    }

    /**
     * Adds a comment to a {@link LIRInstruction}. Existing comments are replaced.
     */
    public final void setComment(LIRInstruction op, String comment) {
        DebugContext debug = lir.getDebug();
        if (debug.isDumpEnabled(DebugContext.BASIC_LEVEL)) {
            if (comments == null) {
                comments = EconomicMap.create(Equivalence.IDENTITY);
            }
            comments.put(op, comment);
        }
    }

    /**
     * Gets the comment attached to a {@link LIRInstruction}.
     */
    public final String getComment(LIRInstruction op) {
        if (comments == null) {
            return null;
        }
        return comments.get(op);
    }

    /**
     * Returns the incoming calling convention for the parameters of the method that is compiled.
     */
    public CallingConvention getCallingConvention() {
        return callingConvention;
    }

    /**
     * Returns the {@link FrameMapBuilder} for collecting the information to build a
     * {@link FrameMap}.
     *
     * This method can only be used prior calling {@link #buildFrameMap}.
     */
    public final FrameMapBuilder getFrameMapBuilder() {
        assert frameMap == null : "getFrameMapBuilder() can only be used before calling buildFrameMap()!";
        return frameMapBuilder;
    }

    /**
     * Creates a {@link FrameMap} out of the {@link FrameMapBuilder}. This method should only be
     * called once. After calling it, {@link #getFrameMapBuilder()} can no longer be used.
     *
     * @see FrameMapBuilder#buildFrameMap
     */
    public void buildFrameMap() {
        assert frameMap == null : "buildFrameMap() can only be called once!";
        frameMap = frameMapBuilder.buildFrameMap(this);
    }

    /**
     * Returns the {@link FrameMap} associated with this {@link LIRGenerationResult}.
     *
     * This method can only be called after {@link #buildFrameMap}.
     */
    public FrameMap getFrameMap() {
        assert frameMap != null : "getFrameMap() can only be used after calling buildFrameMap()!";
        return frameMap;
    }

    public final RegisterConfig getRegisterConfig() {
        return frameMapBuilder.getRegisterConfig();
    }

    public LIR getLIR() {
        return lir;
    }

    /**
     * Determines whether the code being generated makes at least one foreign call.
     */
    public boolean hasForeignCall() {
        return hasForeignCall;
    }

    public final void setForeignCall(boolean hasForeignCall) {
        this.hasForeignCall = hasForeignCall;
    }

    public String getCompilationUnitName() {
        if (compilationId == null || compilationId == CompilationIdentifier.INVALID_COMPILATION_ID) {
            return "<unknown>";
        }
        return compilationId.toString(Verbosity.NAME);
    }

    /**
     * Return the first position to insert a LIR instruction. No instruction should be inserted
     * before this position.
     *
     * @return index of the first insert position
     */
    @SuppressWarnings("static-method")
    public final int getFirstInsertPosition() {
        return 1;
    }

    public boolean emitIndirectTargetBranchMarkers() {
        return false;
    }
}
