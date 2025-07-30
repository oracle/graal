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

import jdk.vm.ci.hotspot.HotSpotMemoryAccessProvider;
import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;

//JaCoCo Exclude

final class HotSpotMemoryAccessProviderProxy extends CompilationProxyBase implements HotSpotMemoryAccessProvider {
    HotSpotMemoryAccessProviderProxy(InvocationHandler handler) {
        super(handler);
    }

    private static SymbolicMethod method(String name, Class<?>... params) {
        return new SymbolicMethod(HotSpotMemoryAccessProvider.class, name, params);
    }

    private static final SymbolicMethod readNarrowOopConstantMethod = method("readNarrowOopConstant", Constant.class, long.class);
    private static final InvokableMethod readNarrowOopConstantInvokable = (receiver, args) -> ((HotSpotMemoryAccessProvider) receiver).readNarrowOopConstant((Constant) args[0], (long) args[1]);

    @Override
    public JavaConstant readNarrowOopConstant(Constant base, long displacement) {
        return (JavaConstant) handle(readNarrowOopConstantMethod, readNarrowOopConstantInvokable, base, displacement);
    }

    private static final SymbolicMethod readKlassPointerConstantMethod = method("readKlassPointerConstant", Constant.class, long.class);
    private static final InvokableMethod readKlassPointerConstantInvokable = (receiver, args) -> ((HotSpotMemoryAccessProvider) receiver).readKlassPointerConstant((Constant) args[0], (long) args[1]);

    @Override
    public Constant readKlassPointerConstant(Constant base, long displacement) {
        return (Constant) handle(readKlassPointerConstantMethod, readKlassPointerConstantInvokable, base, displacement);
    }

    private static final SymbolicMethod readNarrowKlassPointerConstantMethod = method("readNarrowKlassPointerConstant", Constant.class, long.class);
    private static final InvokableMethod readNarrowKlassPointerConstantInvokable = (receiver, args) -> ((HotSpotMemoryAccessProvider) receiver).readNarrowKlassPointerConstant((Constant) args[0],
                    (long) args[1]);

    @Override
    public Constant readNarrowKlassPointerConstant(Constant base, long displacement) {
        return (Constant) handle(readNarrowKlassPointerConstantMethod, readNarrowKlassPointerConstantInvokable, base, displacement);
    }

    private static final SymbolicMethod readMethodPointerConstantMethod = method("readMethodPointerConstant", Constant.class, long.class);
    private static final InvokableMethod readMethodPointerConstantInvokable = (receiver, args) -> ((HotSpotMemoryAccessProvider) receiver).readMethodPointerConstant((Constant) args[0],
                    (long) args[1]);

    @Override
    public Constant readMethodPointerConstant(Constant base, long displacement) {
        return (Constant) handle(readMethodPointerConstantMethod, readMethodPointerConstantInvokable, base, displacement);
    }

    private static final SymbolicMethod readPrimitiveConstantMethod = method("readPrimitiveConstant", JavaKind.class, Constant.class, long.class, int.class);
    private static final InvokableMethod readPrimitiveConstantInvokable = (receiver, args) -> ((HotSpotMemoryAccessProvider) receiver).readPrimitiveConstant((JavaKind) args[0], (Constant) args[1],
                    (long) args[2], (int) args[3]);

    @Override
    public JavaConstant readPrimitiveConstant(JavaKind kind, Constant base, long displacement, int bits) {
        return (JavaConstant) handle(readPrimitiveConstantMethod, readPrimitiveConstantInvokable, kind, base, displacement, bits);
    }

    private static final SymbolicMethod readObjectConstantMethod = method("readObjectConstant", Constant.class, long.class);
    private static final InvokableMethod readObjectConstantInvokable = (receiver, args) -> ((HotSpotMemoryAccessProvider) receiver).readObjectConstant((Constant) args[0], (long) args[1]);

    @Override
    public JavaConstant readObjectConstant(Constant base, long displacement) {
        return (JavaConstant) handle(readObjectConstantMethod, readObjectConstantInvokable, base, displacement);
    }
}
