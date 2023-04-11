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

import static com.oracle.svm.core.util.VMError.unsupportedFeature;

import org.graalvm.collections.EconomicMap;
import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.c.function.CodePointer;

import com.oracle.svm.core.FunctionPointerHolder;

public class ForeignFunctionsRuntime {
    @Fold
    public static ForeignFunctionsRuntime singleton() {
        return ImageSingletons.lookup(ForeignFunctionsRuntime.class);
    }

    private final EconomicMap<NativeEntryPointInfo, FunctionPointerHolder> stubs = EconomicMap.create();

    public ForeignFunctionsRuntime() {}

    @Platforms(Platform.HOSTED_ONLY.class)
    public void addStubPointer(NativeEntryPointInfo nepi, CFunctionPointer ptr) {
        assert(!stubs.containsKey(nepi));
        stubs.put(nepi, new FunctionPointerHolder(ptr));
    }

    public CodePointer getStubPointer(NativeEntryPointInfo nep) {
        FunctionPointerHolder pointer = stubs.get(nep);
        if (pointer == null) {
            throw unsupportedFeature("Cannot perform downcall if the descriptor was not registered.");
        }
        else {
            return pointer.functionPointer;
        }
    }
}
