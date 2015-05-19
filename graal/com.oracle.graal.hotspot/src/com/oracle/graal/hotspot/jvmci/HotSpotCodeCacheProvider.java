/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.jvmci;

import static com.oracle.graal.hotspot.jvmci.HotSpotCompressedNullConstant.*;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.code.CodeUtil.DefaultRefMapFormatter;
import com.oracle.graal.api.code.CodeUtil.RefMapFormatter;
import com.oracle.graal.api.code.CompilationResult.Call;
import com.oracle.graal.api.code.CompilationResult.ConstantReference;
import com.oracle.graal.api.code.CompilationResult.DataPatch;
import com.oracle.graal.api.code.CompilationResult.Infopoint;
import com.oracle.graal.api.code.CompilationResult.Mark;
import com.oracle.graal.api.code.DataSection.Data;
import com.oracle.graal.api.code.DataSection.DataBuilder;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.debug.*;
import com.oracle.graal.hotspot.*;
import com.oracle.graal.hotspot.meta.*;
import com.oracle.graal.printer.*;

/**
 * HotSpot implementation of {@link CodeCacheProvider}.
 */
public class HotSpotCodeCacheProvider implements CodeCacheProvider {

    protected final HotSpotGraalRuntimeProvider runtime;
    public final HotSpotVMConfig config;
    protected final TargetDescription target;
    protected final RegisterConfig regConfig;

    public HotSpotCodeCacheProvider(HotSpotGraalRuntimeProvider runtime, HotSpotVMConfig config, TargetDescription target, RegisterConfig regConfig) {
        this.runtime = runtime;
        this.config = config;
        this.target = target;
        this.regConfig = regConfig;
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
            for (DataPatch site : compResult.getDataPatches()) {
                hcf.addOperandComment(site.pcOffset, "{" + site.reference.toString() + "}");
            }
            for (Mark mark : compResult.getMarks()) {
                hcf.addComment(mark.pcOffset, getMarkIdName((int) mark.id));
            }
        }
        String hcfEmbeddedString = hcf.toEmbeddedString();
        return HexCodeFileDisTool.tryDisassemble(hcfEmbeddedString);
    }

    /**
     * Interface to the tool for disassembling an {@link HexCodeFile#toEmbeddedString() embedded}
     * {@link HexCodeFile}.
     */
    static class HexCodeFileDisTool {
        static final Method processMethod;
        static {
            Method toolMethod = null;
            try {
                Class<?> toolClass = Class.forName("com.oracle.max.hcfdis.HexCodeFileDis", true, ClassLoader.getSystemClassLoader());
                toolMethod = toolClass.getDeclaredMethod("processEmbeddedString", String.class);
            } catch (Exception e) {
                // Tool not available on the class path
            }
            processMethod = toolMethod;
        }

        public static String tryDisassemble(String hcfEmbeddedString) {
            if (processMethod != null) {
                try {
                    return (String) processMethod.invoke(null, hcfEmbeddedString);
                } catch (IllegalAccessException | IllegalArgumentException | InvocationTargetException e) {
                    // If the tool is available, for now let's be noisy when it fails
                    throw new GraalInternalError(e);
                }
            }
            return hcfEmbeddedString;
        }
    }

    private String getMarkIdName(int markId) {
        Field[] fields = runtime.getConfig().getClass().getDeclaredFields();
        for (Field f : fields) {
            if (f.getName().startsWith("MARKID_")) {
                f.setAccessible(true);
                try {
                    if (f.getInt(runtime.getConfig()) == markId) {
                        return f.getName();
                    }
                } catch (Exception e) {
                }
            }
        }
        return String.valueOf(markId);
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

    public InstalledCode installMethod(HotSpotResolvedJavaMethod method, CompilationResult compResult, long graalEnv, boolean isDefault) {
        if (compResult.getId() == -1) {
            compResult.setId(method.allocateCompileId(compResult.getEntryBCI()));
        }
        HotSpotInstalledCode installedCode = new HotSpotNmethod(method, compResult.getName(), isDefault);
        runtime.getCompilerToVM().installCode(new HotSpotCompiledNmethod(method, compResult, graalEnv), installedCode, method.getSpeculationLog());
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
        HotSpotCompiledNmethod compiledCode = new HotSpotCompiledNmethod(hotspotMethod, compResult);
        int result = runtime.getCompilerToVM().installCode(compiledCode, installedCode, log);
        if (result != config.codeInstallResultOk) {
            String msg = compiledCode.getInstallationFailureMessage();
            String resultDesc = config.getCodeInstallResultDescription(result);
            if (msg != null) {
                msg = String.format("Code installation failed: %s%n%s", resultDesc, msg);
            } else {
                msg = String.format("Code installation failed: %s", resultDesc);
            }
            if (result == config.codeInstallResultDependenciesInvalid) {
                throw new AssertionError(resultDesc + " " + msg);
            }
            throw new BailoutException(result != config.codeInstallResultDependenciesFailed, msg);
        }
        return logOrDump(installedCode, compResult);
    }

    @Override
    public InstalledCode setDefaultMethod(ResolvedJavaMethod method, CompilationResult compResult) {
        HotSpotResolvedJavaMethod hotspotMethod = (HotSpotResolvedJavaMethod) method;
        return installMethod(hotspotMethod, compResult, 0L, true);
    }

    public HotSpotNmethod addExternalMethod(ResolvedJavaMethod method, CompilationResult compResult) {
        HotSpotResolvedJavaMethod javaMethod = (HotSpotResolvedJavaMethod) method;
        if (compResult.getId() == -1) {
            compResult.setId(javaMethod.allocateCompileId(compResult.getEntryBCI()));
        }
        HotSpotNmethod code = new HotSpotNmethod(javaMethod, compResult.getName(), false, true);
        HotSpotCompiledNmethod compiled = new HotSpotCompiledNmethod(javaMethod, compResult);
        CompilerToVM vm = runtime.getCompilerToVM();
        int result = vm.installCode(compiled, code, null);
        if (result != runtime.getConfig().codeInstallResultOk) {
            return null;
        }
        return code;
    }

    public boolean needsDataPatch(JavaConstant constant) {
        return constant instanceof HotSpotMetaspaceConstant;
    }

    public Data createDataItem(Constant constant) {
        int size;
        DataBuilder builder;
        if (constant instanceof VMConstant) {
            VMConstant vmConstant = (VMConstant) constant;
            boolean compressed;
            long raw;
            if (constant instanceof HotSpotObjectConstant) {
                HotSpotObjectConstant c = (HotSpotObjectConstant) vmConstant;
                compressed = c.isCompressed();
                raw = 0xDEADDEADDEADDEADL;
            } else if (constant instanceof HotSpotMetaspaceConstant) {
                HotSpotMetaspaceConstant meta = (HotSpotMetaspaceConstant) constant;
                compressed = meta.isCompressed();
                raw = meta.rawValue();
            } else {
                throw GraalInternalError.shouldNotReachHere();
            }

            size = target.getSizeInBytes(compressed ? Kind.Int : target.wordKind);
            if (size == 4) {
                builder = (buffer, patch) -> {
                    patch.accept(new DataPatch(buffer.position(), new ConstantReference(vmConstant)));
                    buffer.putInt((int) raw);
                };
            } else {
                assert size == 8;
                builder = (buffer, patch) -> {
                    patch.accept(new DataPatch(buffer.position(), new ConstantReference(vmConstant)));
                    buffer.putLong(raw);
                };
            }
        } else if (JavaConstant.isNull(constant)) {
            boolean compressed = COMPRESSED_NULL.equals(constant);
            size = target.getSizeInBytes(compressed ? Kind.Int : target.wordKind);
            builder = DataBuilder.zero(size);
        } else if (constant instanceof SerializableConstant) {
            SerializableConstant s = (SerializableConstant) constant;
            size = s.getSerializedSize();
            builder = DataBuilder.serializable(s);
        } else {
            throw GraalInternalError.shouldNotReachHere();
        }

        return new Data(size, size, builder);
    }

    @Override
    public TargetDescription getTarget() {
        return target;
    }

    public String disassemble(InstalledCode code) {
        if (code.isValid()) {
            long codeBlob = code.getAddress();
            return runtime.getCompilerToVM().disassembleCodeBlob(codeBlob);
        }
        return null;
    }

    public SpeculationLog createSpeculationLog() {
        return new HotSpotSpeculationLog();
    }
}
