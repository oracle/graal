/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.hosted.webimage.wasmgc;

import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.webimage.wasm.WasmImports;
import com.oracle.svm.webimage.platform.WebImageWasmGCPlatform;
import com.oracle.svm.webimage.wasmgc.WasmExtern;
import com.oracle.svm.webimage.wasmgc.annotation.WasmExport;

import jdk.graal.compiler.core.common.spi.ForeignCallDescriptor;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.nodes.extended.ForeignCallNode;

@Platforms(WebImageWasmGCPlatform.class)
public class WasmGCConversion {

    public static final SnippetRuntime.SubstrateForeignCallDescriptor PROXY_OBJECT = SnippetRuntime.findForeignCall(WasmGCConversion.class, "proxyObject",
                    ForeignCallDescriptor.CallSideEffect.NO_SIDE_EFFECT);

    public static void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        foreignCalls.register(PROXY_OBJECT);
    }

    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static WasmExtern proxyObject(Object o) {
        if (o instanceof WasmExtern extern) {
            return extern;
        }

        if (o instanceof char[] chars) {
            return proxyCharArray(WasmImports.PROXY_CHAR_ARRAY, chars);
        }

        throw VMError.shouldNotReachHere("Tried to proxy unsupported object type: " + o.getClass());
    }

    @Node.NodeIntrinsic(ForeignCallNode.class)
    public static native WasmExtern proxyCharArray(@Node.ConstantNodeParameter ForeignCallDescriptor descriptor, char[] arr);

    @WasmExport(value = "string.fromchars", comment = "Create Java String from Java char array")
    public static String stringFromCharArray(char[] chars) {
        return new String(chars);
    }
}
