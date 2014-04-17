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
package com.oracle.graal.hotspot.meta;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CodeUtil.DefaultRefMapFormatter;
import com.oracle.graal.api.code.CodeUtil.RefMapFormatter;
import com.oracle.graal.api.code.CompilationResult.Call;
import com.oracle.graal.api.code.CompilationResult.Data;
import com.oracle.graal.api.code.CompilationResult.DataPatch;
import com.oracle.graal.api.code.CompilationResult.Infopoint;
import com.oracle.graal.api.code.CompilationResult.Mark;
import com.oracle.graal.api.code.CompilationResult.PrimitiveData;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.bridge.*;
import com.oracle.graal.hotspot.bridge.CompilerToVM.CodeInstallResult;
import com.oracle.graal.hotspot.data.*;
import com.oracle.graal.java.*;
import com.oracle.graal.lir.asm.*;
import com.oracle.graal.printer.*;

/**
 * HotSpot implementation of {@link CodeCacheProvider}.
 */
public abstract class HotSpotCodeCacheProvider implements CodeCacheProvider {

    protected final HotSpotGraalRuntime runtime;
    protected final TargetDescription target;
    protected final RegisterConfig regConfig;

    public HotSpotCodeCacheProvider(HotSpotGraalRuntime runtime, TargetDescription target) {
        this.runtime = runtime;
        this.target = target;
        regConfig = createRegisterConfig();
    }

    protected abstract RegisterConfig createRegisterConfig();

    /**
     * Constants used to mark special positions in code being installed into the code cache by Graal
     * C++ code.
     */
    public enum MarkId {
        VERIFIED_ENTRY(config().codeInstallerMarkIdVerifiedEntry),
        UNVERIFIED_ENTRY(config().codeInstallerMarkIdUnverifiedEntry),
        OSR_ENTRY(config().codeInstallerMarkIdOsrEntry),
        EXCEPTION_HANDLER_ENTRY(config().codeInstallerMarkIdExceptionHandlerEntry),
        DEOPT_HANDLER_ENTRY(config().codeInstallerMarkIdDeoptHandlerEntry),
        INVOKEINTERFACE(config().codeInstallerMarkIdInvokeinterface),
        INVOKEVIRTUAL(config().codeInstallerMarkIdInvokevirtual),
        INVOKESTATIC(config().codeInstallerMarkIdInvokestatic),
        INVOKESPECIAL(config().codeInstallerMarkIdInvokespecial),
        INLINE_INVOKE(config().codeInstallerMarkIdInlineInvoke),
        POLL_NEAR(config().codeInstallerMarkIdPollNear),
        POLL_RETURN_NEAR(config().codeInstallerMarkIdPollReturnNear),
        POLL_FAR(config().codeInstallerMarkIdPollFar),
        POLL_RETURN_FAR(config().codeInstallerMarkIdPollReturnFar);

        private final int value;

        private MarkId(int value) {
            this.value = value;
        }

        private static HotSpotVMConfig config() {
            return HotSpotGraalRuntime.runtime().getConfig();
        }

        public static MarkId getEnum(int value) {
            for (MarkId e : values()) {
                if (e.value == value) {
                    return e;
                }
            }
            throw GraalInternalError.shouldNotReachHere("unknown enum value " + value);
        }

        /**
         * Helper method to {@link CompilationResultBuilder#recordMark(Object) record a mark} with a
         * {@link CompilationResultBuilder}.
         */
        public static void recordMark(CompilationResultBuilder crb, MarkId mark) {
            crb.recordMark(mark.value);
        }
    }

    @Override
    public String disassemble(CompilationResult compResult, InstalledCode installedCode) {
        byte[] code = installedCode == null ? Arrays.copyOf(compResult.getTargetCode(), compResult.getTargetCodeSize()) : installedCode.getCode();
        if (code == null) {
            // Method was deoptimized/invalidated
            return "";
        }
        long start = installedCode == null ? 0L : installedCode.getStart();
        HexCodeFile hcf = new HexCodeFile(code, start, target.arch.getName(), target.wordSize * 8);
        if (compResult != null) {
            HexCodeFile.addAnnotations(hcf, compResult.getAnnotations());
            addExceptionHandlersComment(compResult, hcf);
            Register fp = regConfig.getFrameRegister();
            RefMapFormatter slotFormatter = new DefaultRefMapFormatter(target.arch, target.wordSize, fp, 0);
            for (Infopoint infopoint : compResult.getInfopoints()) {
                if (infopoint instanceof Call) {
                    Call call = (Call) infopoint;
                    if (call.debugInfo != null) {
                        hcf.addComment(call.pcOffset + call.size, CodeUtil.append(new StringBuilder(100), call.debugInfo, slotFormatter).toString());
                    }
                    addOperandComment(hcf, call.pcOffset, "{" + getTargetName(call) + "}");
                } else {
                    if (infopoint.debugInfo != null) {
                        hcf.addComment(infopoint.pcOffset, CodeUtil.append(new StringBuilder(100), infopoint.debugInfo, slotFormatter).toString());
                    }
                    addOperandComment(hcf, infopoint.pcOffset, "{infopoint: " + infopoint.reason + "}");
                }
            }
            for (DataPatch site : compResult.getDataReferences()) {
                hcf.addOperandComment(site.pcOffset, "{" + site.data.toString() + "}");
            }
            for (Mark mark : compResult.getMarks()) {
                hcf.addComment(mark.pcOffset, MarkId.getEnum((int) mark.id).toString());
            }
        }
        return hcf.toEmbeddedString();
    }

    /**
     * Decodes a call target to a mnemonic if possible.
     */
    private String getTargetName(Call call) {
        Field[] fields = runtime.getConfig().getClass().getDeclaredFields();
        for (Field f : fields) {
            if (f.getName().endsWith("Stub")) {
                f.setAccessible(true);
                try {
                    Object address = f.get(runtime.getConfig());
                    if (address.equals(call.target)) {
                        return f.getName() + ":0x" + Long.toHexString((Long) address);
                    }
                } catch (Exception e) {
                }
            }
        }
        return String.valueOf(call.target);
    }

    private static void addExceptionHandlersComment(CompilationResult compResult, HexCodeFile hcf) {
        if (!compResult.getExceptionHandlers().isEmpty()) {
            String nl = HexCodeFile.NEW_LINE;
            StringBuilder buf = new StringBuilder("------ Exception Handlers ------").append(nl);
            for (CompilationResult.ExceptionHandler e : compResult.getExceptionHandlers()) {
                buf.append("    ").append(e.pcOffset).append(" -> ").append(e.handlerPos).append(nl);
                hcf.addComment(e.pcOffset, "[exception -> " + e.handlerPos + "]");
                hcf.addComment(e.handlerPos, "[exception handler for " + e.pcOffset + "]");
            }
            hcf.addComment(0, buf.toString());
        }
    }

    private static void addOperandComment(HexCodeFile hcf, int pos, String comment) {
        String oldValue = hcf.addOperandComment(pos, comment);
        assert oldValue == null : "multiple comments for operand of instruction at " + pos + ": " + comment + ", " + oldValue;
    }

    @Override
    public RegisterConfig getRegisterConfig() {
        return regConfig;
    }

    @Override
    public int getMinimumOutgoingSize() {
        return runtime.getConfig().runtimeCallStackSize;
    }

    public InstalledCode logOrDump(InstalledCode installedCode, CompilationResult compResult) {
        if (Debug.isDumpEnabled()) {
            Debug.dump(new Object[]{compResult, installedCode}, "After code installation");
        }
        if (Debug.isLogEnabled()) {
            Debug.log("%s", disassemble(installedCode));
        }
        return installedCode;
    }

    public InstalledCode installMethod(HotSpotResolvedJavaMethod method, CompilationResult compResult) {
        if (compResult.getId() == -1) {
            compResult.setId(method.allocateCompileId(compResult.getEntryBCI()));
        }
        HotSpotInstalledCode installedCode = new HotSpotNmethod(method, compResult.getName(), true);
        runtime.getCompilerToVM().installCode(new HotSpotCompiledNmethod(target, method, compResult), installedCode, method.getSpeculationLog());
        return logOrDump(installedCode, compResult);
    }

    @Override
    public InstalledCode addMethod(ResolvedJavaMethod method, CompilationResult compResult, SpeculationLog log, InstalledCode predefinedInstalledCode) {
        HotSpotResolvedJavaMethod hotspotMethod = (HotSpotResolvedJavaMethod) method;
        if (compResult.getId() == -1) {
            compResult.setId(hotspotMethod.allocateCompileId(compResult.getEntryBCI()));
        }
        InstalledCode installedCode = predefinedInstalledCode;
        if (installedCode == null) {
            HotSpotInstalledCode code = new HotSpotNmethod(hotspotMethod, compResult.getName(), false);
            installedCode = code;
        }
        CodeInstallResult result = runtime.getCompilerToVM().installCode(new HotSpotCompiledNmethod(target, hotspotMethod, compResult), installedCode, log);
        if (result != CodeInstallResult.OK) {
            return null;
        }
        return logOrDump(installedCode, compResult);
    }

    @Override
    public InstalledCode setDefaultMethod(ResolvedJavaMethod method, CompilationResult compResult) {
        HotSpotResolvedJavaMethod hotspotMethod = (HotSpotResolvedJavaMethod) method;
        return installMethod(hotspotMethod, compResult);
    }

    public HotSpotNmethod addExternalMethod(ResolvedJavaMethod method, CompilationResult compResult) {
        HotSpotResolvedJavaMethod javaMethod = (HotSpotResolvedJavaMethod) method;
        if (compResult.getId() == -1) {
            compResult.setId(javaMethod.allocateCompileId(compResult.getEntryBCI()));
        }
        HotSpotNmethod code = new HotSpotNmethod(javaMethod, compResult.getName(), false, true);
        HotSpotCompiledNmethod compiled = new HotSpotCompiledNmethod(target, javaMethod, compResult);
        CompilerToVM vm = runtime.getCompilerToVM();
        CodeInstallResult result = vm.installCode(compiled, code, null);
        if (result != CodeInstallResult.OK) {
            return null;
        }
        return code;
    }

    public boolean needsDataPatch(Constant constant) {
        return constant instanceof HotSpotMetaspaceConstant;
    }

    public Data createDataItem(Constant constant, int alignment) {
        if (constant instanceof HotSpotMetaspaceConstant) {
            // constant.getKind() == target.wordKind for uncompressed pointers
            // otherwise, it's a compressed pointer
            return new MetaspaceData(alignment, constant.asLong(), HotSpotMetaspaceConstant.getMetaspaceObject(constant), constant.getKind() != target.wordKind);
        } else if (constant.getKind().isObject()) {
            return new OopData(alignment, HotSpotObjectConstant.asObject(constant), false);
        } else {
            return new PrimitiveData(constant, alignment);
        }
    }

    @Override
    public TargetDescription getTarget() {
        return target;
    }

    public String disassemble(InstalledCode code) {
        if (code.isValid()) {
            long codeBlob = ((HotSpotInstalledCode) code).getAddress();
            return runtime.getCompilerToVM().disassembleCodeBlob(codeBlob);
        }
        return null;
    }

    public String disassemble(ResolvedJavaMethod method) {
        return new BytecodeDisassembler().disassemble(method);
    }
}
