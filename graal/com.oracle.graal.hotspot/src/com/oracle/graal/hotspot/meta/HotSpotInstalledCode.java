/*
 * Copyright (c) 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.hotspot.meta;

import java.lang.reflect.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.*;

/**
 * Implementation of {@link InstalledCode} for HotSpot. Stores a reference to the nmethod which
 * contains the compiled code. The nmethod also stores a weak reference to the HotSpotCompiledMethod
 * instance which is necessary to keep the nmethod from being unloaded.
 */
public class HotSpotInstalledCode extends CompilerObject implements InstalledCode {

    private static final long serialVersionUID = 156632908220561612L;

    private final HotSpotResolvedJavaMethod method;
    private final boolean isDefault;
    long nmethod;
    long start;

    public HotSpotInstalledCode(HotSpotResolvedJavaMethod method, boolean isDefault) {
        this.method = method;
        this.isDefault = isDefault;
    }

    public boolean isDefault() {
        return isDefault;
    }

    @Override
    public ResolvedJavaMethod getMethod() {
        return method;
    }

    @Override
    public boolean isValid() {
        return nmethod != 0;
    }

    @Override
    public String toString() {
        return String.format("InstalledCode[method=%s, nmethod=0x%x]", method, nmethod);
    }

    @Override
    public Object execute(Object arg1, Object arg2, Object arg3) {
        assert method.getSignature().getParameterCount(!Modifier.isStatic(method.getModifiers())) == 3;
        assert method.getSignature().getParameterKind(0) == Kind.Object;
        assert method.getSignature().getParameterKind(1) == Kind.Object;
        assert !Modifier.isStatic(method.getModifiers()) || method.getSignature().getParameterKind(2) == Kind.Object;
        return HotSpotGraalRuntime.getInstance().getCompilerToVM().executeCompiledMethod(method.metaspaceMethod, nmethod, arg1, arg2, arg3);
    }

    private boolean checkArgs(Object... args) {
        JavaType[] sig = MetaUtil.signatureToTypes(method);
        assert args.length == sig.length : MetaUtil.format("%H.%n(%p): expected ", method) + sig.length + " args, got " + args.length;
        for (int i = 0; i < sig.length; i++) {
            Object arg = args[i];
            if (arg == null) {
                assert sig[i].getKind() == Kind.Object : MetaUtil.format("%H.%n(%p): expected arg ", method) + i + " to be Object, not " + sig[i];
            } else if (sig[i].getKind() != Kind.Object) {
                assert sig[i].getKind().toBoxedJavaClass() == arg.getClass() : MetaUtil.format("%H.%n(%p): expected arg ", method) + i + " to be " + sig[i] + ", not " + arg.getClass();
            }
        }
        return true;
    }

    @Override
    public Object executeVarargs(Object... args) {
        assert checkArgs(args);
        return HotSpotGraalRuntime.getInstance().getCompilerToVM().executeCompiledMethodVarargs(method.metaspaceMethod, nmethod, args);
    }

    @Override
    public long getStart() {
        return isValid() ? start : 0;
    }

    @Override
    public byte[] getCode() {
        return HotSpotGraalRuntime.getInstance().getCompilerToVM().getCode(nmethod);
    }
}
