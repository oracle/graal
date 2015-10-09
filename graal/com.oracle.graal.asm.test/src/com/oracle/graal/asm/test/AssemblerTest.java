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
package com.oracle.graal.asm.test;

import java.lang.reflect.Method;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.CompilationResult;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.InvalidInstalledCodeException;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.runtime.JVMCI;
import jdk.vm.ci.runtime.JVMCIBackend;
import jdk.vm.ci.service.Services;

import org.junit.Assert;

import com.oracle.graal.code.DisassemblerProvider;
import com.oracle.graal.debug.Debug;
import com.oracle.graal.debug.Debug.Scope;
import com.oracle.graal.test.GraalTest;

public abstract class AssemblerTest extends GraalTest {

    private final MetaAccessProvider metaAccess;
    protected final CodeCacheProvider codeCache;

    public interface CodeGenTest {
        byte[] generateCode(CompilationResult compResult, TargetDescription target, RegisterConfig registerConfig, CallingConvention cc);
    }

    public AssemblerTest() {
        JVMCIBackend providers = JVMCI.getRuntime().getHostJVMCIBackend();
        this.metaAccess = providers.getMetaAccess();
        this.codeCache = providers.getCodeCache();
    }

    public MetaAccessProvider getMetaAccess() {
        return metaAccess;
    }

    @SuppressWarnings("try")
    protected InstalledCode assembleMethod(Method m, CodeGenTest test) {
        ResolvedJavaMethod method = getMetaAccess().lookupJavaMethod(m);
        try (Scope s = Debug.scope("assembleMethod", method, codeCache)) {
            RegisterConfig registerConfig = codeCache.getRegisterConfig();
            CallingConvention cc = CodeUtil.getCallingConvention(codeCache, CallingConvention.Type.JavaCallee, method, false);

            CompilationResult compResult = new CompilationResult();
            byte[] targetCode = test.generateCode(compResult, codeCache.getTarget(), registerConfig, cc);
            compResult.setTargetCode(targetCode, targetCode.length);
            compResult.setTotalFrameSize(0);

            InstalledCode code = codeCache.addCode(method, compResult, null, null);

            for (DisassemblerProvider dis : Services.load(DisassemblerProvider.class)) {
                String disasm1 = dis.disassembleCompiledCode(codeCache, compResult);
                Assert.assertTrue(compResult.toString(), disasm1 == null || disasm1.length() > 0);
                String disasm2 = dis.disassembleInstalledCode(codeCache, compResult, code);
                Assert.assertTrue(code.toString(), disasm2 == null || disasm2.length() > 0);
            }
            return code;
        } catch (Throwable e) {
            throw Debug.handle(e);
        }
    }

    protected Object runTest(String methodName, CodeGenTest test, Object... args) {
        Method method = getMethod(methodName);
        InstalledCode code = assembleMethod(method, test);
        try {
            return code.executeVarargs(args);
        } catch (InvalidInstalledCodeException e) {
            throw new RuntimeException(e);
        }
    }

    protected void assertReturn(String methodName, CodeGenTest test, Object expected, Object... args) {
        Object actual = runTest(methodName, test, args);
        Assert.assertEquals("unexpected return value", expected, actual);
    }
}
