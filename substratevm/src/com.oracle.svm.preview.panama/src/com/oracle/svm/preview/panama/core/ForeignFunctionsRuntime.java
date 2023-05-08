/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.preview.panama.core;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.CodePointer;

import com.oracle.graal.pointsto.util.AnalysisError;
import com.oracle.svm.core.FunctionPointerHolder;

public class ForeignFunctionsRuntime {
    @Fold
    public static ForeignFunctionsRuntime singleton() {
        return ImageSingletons.lookup(ForeignFunctionsRuntime.class);
    }

    private final EconomicMap<NativeEntryPointInfo, FunctionPointerHolder> stubs = EconomicMap.create();

    public ForeignFunctionsRuntime() {
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void addStubPointer(NativeEntryPointInfo nepi, CFunctionPointer ptr) {
        AnalysisError.UserError.guarantee(!stubs.containsKey(nepi), "Seems like multiple stubs were generate for " + nepi);
        stubs.put(nepi, new FunctionPointerHolder(ptr));
    }

    /**
     * We'd rather report the function descriptor rather than the native method type, but we don't
     * have it available here. One could intercept this exception in
     * {@link jdk.internal.foreign.abi.DowncallLinker.getBoundMethodHandle} and add information
     * about the descriptor there.
     */
    public CodePointer getStubPointer(NativeEntryPointInfo nep) {
        FunctionPointerHolder pointer = stubs.get(nep);
        if (pointer == null) {
            throw new UnregisteredDowncallStubException(nep);
        } else {
            return pointer.functionPointer;
        }
    }

    @SuppressWarnings("serial")
    public static class UnregisteredDowncallStubException extends RuntimeException {
        private final NativeEntryPointInfo nep;

        UnregisteredDowncallStubException(NativeEntryPointInfo nep) {
            super(generateMessage(nep));
            this.nep = nep;
        }

        private static String generateMessage(NativeEntryPointInfo nep) {
            return "Cannot perform downcall with leaf type " + nep.nativeMethodType() + " as it was not registered at compilation time.";
        }
    }
}
