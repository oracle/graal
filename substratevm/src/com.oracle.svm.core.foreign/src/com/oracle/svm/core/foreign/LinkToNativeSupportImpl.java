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
package com.oracle.svm.core.foreign;

import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.LinkToNativeSupport;
import com.oracle.svm.core.c.InvokeJavaFunctionPointer;

public final class LinkToNativeSupportImpl implements LinkToNativeSupport {
    /**
     * Arguments follow the same structure as described in {@link NativeEntryPointInfo}, with an
     * additional {@link Target_jdk_internal_foreign_abi_NativeEntryPoint} (NEP) as the last
     * argument, i.e.
     * 
     * <pre>
     * {@code
     *      [return buffer address] <call address> [capture state address] <actual arg 1> <actual arg 2> ... <NEP>
     * }
     * </pre>
     *
     * where <actual arg i>s are the arguments which end up being passed to the C native function
     */
    @Override
    public Object linkToNative(Object... args) throws Throwable {
        Target_jdk_internal_foreign_abi_NativeEntryPoint nep = (Target_jdk_internal_foreign_abi_NativeEntryPoint) args[args.length - 1];
        StubPointer pointer = WordFactory.pointer(nep.downcallStubAddress);
        /* The nep argument will be dropped in the invoked function */
        return pointer.invoke(args);
    }
}

interface StubPointer extends CFunctionPointer {
    @InvokeJavaFunctionPointer
    Object invoke(Object... args);
}
