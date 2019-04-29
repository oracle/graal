/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.core.llvm;

import static org.graalvm.compiler.core.llvm.LLVMUtils.NULL;
import static org.graalvm.compiler.core.llvm.LLVMUtils.TRUE;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.bytedeco.javacpp.BytePointer;
import org.bytedeco.javacpp.LLVM;
import org.bytedeco.javacpp.LLVM.LLVMMemoryBufferRef;
import org.bytedeco.javacpp.LLVM.LLVMModuleRef;
import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.core.common.NumUtil;
import org.graalvm.compiler.core.common.cfg.AbstractBlockBase;
import org.graalvm.compiler.debug.CounterKey;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.compiler.debug.GraalError;
import org.graalvm.compiler.nodes.StructuredGraph;

import jdk.vm.ci.code.DebugInfo;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.ConstantReference;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.code.site.InfopointReason;
import jdk.vm.ci.meta.Assumptions;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.VMConstant;

public class LLVMGenerationResult {
    private final Set<AbstractBlockBase<?>> processedBlocks = new HashSet<>();

    private final ResolvedJavaMethod method;
    private LLVMModuleRef module;

    private List<Call> directCallTargets = new ArrayList<>();
    private List<Call> indirectCallTargets = new ArrayList<>();

    private long startPatchpointID = -1L;
    private Map<Constant, String> constants = new HashMap<>();
    private Map<Long, Integer> exceptionHandlers = new LinkedHashMap<>();

    public LLVMGenerationResult(ResolvedJavaMethod method) {
        this.method = method;
    }

    public ResolvedJavaMethod getMethod() {
        return method;
    }

    public void setModule(LLVMModuleRef module) {
        this.module = module;
    }

    public byte[] getBitcode() {
        if (LLVM.LLVMVerifyModule(module, LLVM.LLVMPrintMessageAction, new BytePointer(NULL)) == TRUE) {
            LLVM.LLVMDumpModule(module);
            throw new GraalError("LLVM module verification failed");
        }

        LLVMMemoryBufferRef buffer = LLVM.LLVMWriteBitcodeToMemoryBuffer(module);
        LLVM.LLVMDisposeModule(module);

        BytePointer start = LLVM.LLVMGetBufferStart(buffer);
        int size = NumUtil.safeToInt(LLVM.LLVMGetBufferSize(buffer));
        byte[] bitcode = new byte[size];
        start.get(bitcode, 0, size);

        return bitcode;
    }

    public void setProcessed(AbstractBlockBase<?> b) {
        processedBlocks.add(b);
    }

    public boolean isProcessed(AbstractBlockBase<?> b) {
        return processedBlocks.contains(b);
    }

    public void setStartPatchpointID(long startPatchpointID) {
        this.startPatchpointID = startPatchpointID;
    }

    public void recordDirectCall(ResolvedJavaMethod targetMethod, long patchpointID, DebugInfo debugInfo) {
        directCallTargets.add(new Call(targetMethod, NumUtil.safeToInt(patchpointID), 0, true, debugInfo));
    }

    public List<Call> getDirectCallTargets() {
        return directCallTargets;
    }

    public void recordIndirectCall(ResolvedJavaMethod targetMethod, long patchpointID, DebugInfo debugInfo) {
        indirectCallTargets.add(new Call(targetMethod, NumUtil.safeToInt(patchpointID), 0, false, debugInfo));
    }

    public List<Call> getIndirectCallTargets() {
        return indirectCallTargets;
    }

    public void recordConstant(Constant constant, String symbolName) {
        constants.put(constant, symbolName);
    }

    public Map<Constant, String> getConstants() {
        return constants;
    }

    public String getSymbolNameForConstant(Constant constant) {
        return constants.get(constant);
    }

    public void recordExceptionHandler(long patchpointID, long handlerID) {
        exceptionHandlers.put(patchpointID, NumUtil.safeToInt(handlerID));
    }

    public Map<Long, Integer> getExceptionHandlers() {
        return exceptionHandlers;
    }

    public void populate(CompilationResult compilationResult, StructuredGraph graph) {
        DebugContext debug = graph.getDebug();

        byte[] bitcode = getBitcode();
        compilationResult.setTargetCode(bitcode, bitcode.length);

        compilationResult.recordInfopoint(NumUtil.safeToInt(startPatchpointID), null, InfopointReason.METHOD_START);

        for (Call call : getDirectCallTargets()) {
            compilationResult.addInfopoint(call);
        }
        for (Call call : getIndirectCallTargets()) {
            compilationResult.addInfopoint(call);
        }

        getExceptionHandlers().forEach((patchpointID, blockID) -> {
            compilationResult.recordExceptionHandler(NumUtil.safeToInt(patchpointID), blockID);
        });

        Assumptions assumptions = graph.getAssumptions();
        if (assumptions != null && !assumptions.isEmpty()) {
            compilationResult.setAssumptions(assumptions.toArray());
        }

        ResolvedJavaMethod rootMethod = graph.method();
        if (rootMethod != null) {
            compilationResult.setMethods(rootMethod, graph.getMethods());
            compilationResult.setFields(graph.getFields());
        }

        if (debug.isCountEnabled()) {
            List<DataPatch> ldp = compilationResult.getDataPatches();
            JavaKind[] kindValues = JavaKind.values();
            CounterKey[] dms = new CounterKey[kindValues.length];
            for (int i = 0; i < dms.length; i++) {
                dms[i] = DebugContext.counter("DataPatches-%s", kindValues[i]);
            }

            for (DataPatch dp : ldp) {
                JavaKind kind = JavaKind.Illegal;
                if (dp.reference instanceof ConstantReference) {
                    VMConstant constant = ((ConstantReference) dp.reference).getConstant();
                    if (constant instanceof JavaConstant) {
                        kind = ((JavaConstant) constant).getJavaKind();
                    }
                }
                dms[kind.ordinal()].add(debug, 1);
            }

            DebugContext.counter("CompilationResults").increment(debug);
            DebugContext.counter("InfopointsEmitted").add(debug, compilationResult.getInfopoints().size());
            DebugContext.counter("DataPatches").add(debug, ldp.size());
        }

        debug.dump(DebugContext.BASIC_LEVEL, compilationResult, "After code generation");
    }
}
