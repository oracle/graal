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
package com.oracle.svm.truffle.isolated;

import java.util.function.Supplier;

import org.graalvm.nativeimage.c.function.CEntryPoint;

import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.graal.isolated.ClientHandle;
import com.oracle.svm.graal.isolated.CompilerHandle;
import com.oracle.svm.graal.isolated.CompilerIsolateThread;
import com.oracle.svm.graal.isolated.IsolatedCompileClient;
import com.oracle.svm.graal.isolated.IsolatedCompileContext;

final class IsolatedStringSupplier implements Supplier<String> {

    private final CompilerHandle<Supplier<String>> originalObjectHandle;

    IsolatedStringSupplier(CompilerHandle<Supplier<String>> originalObjectHandle) {
        this.originalObjectHandle = originalObjectHandle;
    }

    @Override
    public String get() {
        ClientHandle<String> resultHandle = getReasonAndStackTrace0(IsolatedCompileClient.get().getCompiler(), originalObjectHandle);
        return IsolatedCompileClient.get().unhand(resultHandle);
    }

    @CEntryPoint(exceptionHandler = IsolatedCompileContext.WordExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(callerEpilogue = IsolatedCompileContext.ExceptionRethrowCallerEpilogue.class)
    private static ClientHandle<String> getReasonAndStackTrace0(@SuppressWarnings("unused") CompilerIsolateThread compiler, CompilerHandle<Supplier<String>> reasonAndStackTraceHandle) {
        Supplier<String> supplier = IsolatedCompileContext.get().unhand(reasonAndStackTraceHandle);
        return IsolatedCompileContext.get().createStringInClient(supplier.get());
    }
}
