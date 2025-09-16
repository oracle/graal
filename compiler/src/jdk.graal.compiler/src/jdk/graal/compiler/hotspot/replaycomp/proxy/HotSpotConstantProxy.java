/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.replaycomp.proxy;

import jdk.vm.ci.hotspot.HotSpotConstant;
import jdk.vm.ci.meta.Constant;

//JaCoCo Exclude

public sealed class HotSpotConstantProxy extends CompilationProxyBase implements HotSpotConstant permits HotSpotMetaspaceConstantProxy, HotSpotObjectConstantProxy {
    HotSpotConstantProxy(InvocationHandler handler) {
        super(handler);
    }

    private static SymbolicMethod method(String name, Class<?>... params) {
        return new SymbolicMethod(HotSpotConstant.class, name, params);
    }

    public static final SymbolicMethod isDefaultForKindMethod = method("isDefaultForKind");
    private static final InvokableMethod isDefaultForKindInvokable = (receiver, args) -> ((HotSpotConstant) receiver).isDefaultForKind();

    @Override
    public boolean isDefaultForKind() {
        return (boolean) handle(isDefaultForKindMethod, isDefaultForKindInvokable);
    }

    public static final SymbolicMethod toValueStringMethod = method("toValueString");
    public static final InvokableMethod toValueStringInvokable = (receiver, args) -> ((HotSpotConstant) receiver).toValueString();

    @Override
    public String toValueString() {
        return (String) handle(toValueStringMethod, toValueStringInvokable);
    }

    public static final SymbolicMethod isCompressedMethod = method("isCompressed");
    public static final InvokableMethod isCompressedInvokable = (receiver, args) -> ((HotSpotConstant) receiver).isCompressed();

    @Override
    public boolean isCompressed() {
        return (boolean) handle(isCompressedMethod, isCompressedInvokable);
    }

    public static final SymbolicMethod isCompressibleMethod = method("isCompressible");
    public static final InvokableMethod isCompressibleInvokable = (receiver, args) -> ((HotSpotConstant) receiver).isCompressible();

    @Override
    public boolean isCompressible() {
        return (boolean) handle(isCompressibleMethod, isCompressibleInvokable);
    }

    public static final SymbolicMethod compressMethod = method("compress");
    public static final InvokableMethod compressInvokable = (receiver, args) -> ((HotSpotConstant) receiver).compress();

    @Override
    public Constant compress() {
        return (Constant) handle(compressMethod, compressInvokable);
    }

    public static final SymbolicMethod uncompressMethod = method("uncompress");
    public static final InvokableMethod uncompressInvokable = (receiver, args) -> ((HotSpotConstant) receiver).uncompress();

    @Override
    public Constant uncompress() {
        return (Constant) handle(uncompressMethod, uncompressInvokable);
    }
}
