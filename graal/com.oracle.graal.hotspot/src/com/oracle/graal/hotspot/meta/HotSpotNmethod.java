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

import static com.oracle.graal.hotspot.HotSpotGraalRuntime.*;

import java.lang.reflect.*;

import com.oracle.graal.api.code.*;
import com.oracle.graal.api.meta.*;
import com.oracle.graal.hotspot.bridge.*;

/**
 * Implementation of {@link InstalledCode} for code installed as an nmethod. The nmethod stores a
 * weak reference to an instance of this class. This is necessary to keep the nmethod from being
 * unloaded while the associated {@link HotSpotNmethod} instance is alive.
 * <p>
 * Note that there is no (current) way for the reference from an nmethod to a {@link HotSpotNmethod}
 * instance to be anything but weak. This is due to the fact that HotSpot does not treat nmethods as
 * strong GC roots.
 */
public final class HotSpotNmethod extends HotSpotInstalledCode {

    private static final long serialVersionUID = -1784683588947054103L;

    private final HotSpotResolvedJavaMethod method;
    private final boolean isDefault;
    private final boolean isExternal;
    private final String name;

    public HotSpotNmethod(HotSpotResolvedJavaMethod method, String name, boolean isDefault) {
        this(method, name, isDefault, false);
    }

    public HotSpotNmethod(HotSpotResolvedJavaMethod method, String name, boolean isDefault, boolean isExternal) {
        this.method = method;
        this.isDefault = isDefault;
        this.isExternal = isExternal;
        this.name = name;
    }

    public boolean isDefault() {
        return isDefault;
    }

    public boolean isExternal() {
        return isExternal;
    }

    @Override
    public ResolvedJavaMethod getMethod() {
        return method;
    }

    @Override
    public boolean isValid() {
        return getCodeBlob() != 0;
    }

    @Override
    public void invalidate() {
        runtime().getCompilerToVM().invalidateInstalledCode(this);
    }

    @Override
    public String toString() {
        return String.format("InstalledNmethod[method=%s, codeBlob=0x%x, isDefault=%b, name=]", method, getCodeBlob(), isDefault, name);
    }

    @Override
    public Object execute(Object arg1, Object arg2, Object arg3) throws InvalidInstalledCodeException {
        assert checkThreeObjectArgs();
        return CompilerToVMImpl.executeCompiledMethodIntrinsic(arg1, arg2, arg3, this);
    }

    protected boolean checkThreeObjectArgs() {
        assert method.getSignature().getParameterCount(!Modifier.isStatic(method.getModifiers())) == 3;
        assert method.getSignature().getParameterKind(0) == Kind.Object;
        assert method.getSignature().getParameterKind(1) == Kind.Object;
        assert !Modifier.isStatic(method.getModifiers()) || method.getSignature().getParameterKind(2) == Kind.Object;
        return true;
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

    public Object executeParallel(int dimX, int dimY, int dimZ, Object... args) throws InvalidInstalledCodeException {

        // For HSAIL, we do not pass the iteration variable, it comes from the workitemid
        // assert checkArgs(args);

        assert isExternal(); // for now

        return runtime().getCompilerToGPU().executeParallelMethodVarargs(dimX, dimY, dimZ, args, this);

    }

    @Override
    public Object executeVarargs(Object... args) throws InvalidInstalledCodeException {
        assert checkArgs(args);
        if (isExternal()) {
            return runtime().getCompilerToGPU().executeExternalMethodVarargs(args, this);
        } else {
            return runtime().getCompilerToVM().executeCompiledMethodVarargs(args, this);
        }
    }

    @Override
    public long getStart() {
        return isValid() ? super.getStart() : 0;
    }
}
