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
package com.oracle.graal.code;

import java.lang.reflect.*;
import java.util.*;

import com.oracle.jvmci.code.*;
import com.oracle.jvmci.code.CodeUtil.DefaultRefMapFormatter;
import com.oracle.jvmci.code.CodeUtil.RefMapFormatter;
import com.oracle.jvmci.code.CompilationResult.Call;
import com.oracle.jvmci.code.CompilationResult.DataPatch;
import com.oracle.jvmci.code.CompilationResult.Infopoint;
import com.oracle.jvmci.code.CompilationResult.Mark;
import com.oracle.jvmci.service.*;

/**
 * {@link HexCodeFile} based implementation of {@link DisassemblerProvider}.
 */
@ServiceProvider(DisassemblerProvider.class)
public class HexCodeFileDisassemblerProvider implements DisassemblerProvider {

    @Override
    public String disassembleCompiledCode(CodeCacheProvider codeCache, CompilationResult compResult) {
        assert compResult != null;
        return disassemble(codeCache, compResult, null);
    }

    public String getName() {
        return "hcf";
    }

    @Override
    public String disassembleInstalledCode(CodeCacheProvider codeCache, CompilationResult compResult, InstalledCode installedCode) {
        assert installedCode != null;
        return installedCode.isValid() ? disassemble(codeCache, compResult, installedCode) : null;
    }

    private static String disassemble(CodeCacheProvider codeCache, CompilationResult compResult, InstalledCode installedCode) {
        TargetDescription target = codeCache.getTarget();
        RegisterConfig regConfig = codeCache.getRegisterConfig();
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
                    addOperandComment(hcf, call.pcOffset, "{" + codeCache.getTargetName(call) + "}");
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
                hcf.addComment(mark.pcOffset, codeCache.getMarkName(mark));
            }
        }
        String hcfEmbeddedString = hcf.toEmbeddedString();
        return HexCodeFileDisTool.tryDisassemble(hcfEmbeddedString);
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
                    throw new InternalError(e);
                }
            }
            return hcfEmbeddedString;
        }
    }

}
