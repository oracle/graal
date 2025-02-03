/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.isolated;

import org.graalvm.nativeimage.c.function.CEntryPoint;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.meta.SubstrateObjectConstant;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/** An object constant for an object in a compilation client's isolate, referenced via a handle. */
public final class IsolatedObjectConstant extends SubstrateObjectConstant {

    private final ClientHandle<?> handle;
    private Class<?> cachedClass = null;
    private int cachedIdentityHash = 0;

    public IsolatedObjectConstant(ClientHandle<?> handle, boolean compressed) {
        super(compressed);
        this.handle = handle;
        assert handle.notEqual(IsolatedHandles.nullHandle());
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public ClientHandle<?> getHandle() {
        return handle;
    }

    @Override
    public ResolvedJavaType getType(MetaAccessProvider provider) {
        return provider.lookupJavaType(getObjectClass());
    }

    private Class<?> getObjectClass() {
        if (cachedClass == null) {
            cachedClass = ImageHeapObjects.deref(getObjectClass0(IsolatedCompileContext.get().getClient(), handle));
        }
        return cachedClass;
    }

    @CEntryPoint(exceptionHandler = IsolatedCompileClient.WordExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(callerEpilogue = IsolatedCompileClient.ExceptionRethrowCallerEpilogue.class)
    private static ImageHeapRef<Class<?>> getObjectClass0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<?> h) {
        Object target = IsolatedCompileClient.get().unhand(h);
        return ImageHeapObjects.ref(target.getClass());
    }

    @Override
    public IsolatedObjectConstant compress() {
        assert !compressed;
        return new IsolatedObjectConstant(handle, true);
    }

    @Override
    public IsolatedObjectConstant uncompress() {
        assert compressed;
        return new IsolatedObjectConstant(handle, false);
    }

    @Override
    public String toValueString() {
        return getObjectClass().getName();
    }

    @Override
    public int getIdentityHashCode() {
        int h = cachedIdentityHash;
        if (h == 0) {
            h = getIdentityHashCode0(IsolatedCompileContext.get().getClient(), handle);
            h = (h == 0) ? 31 : h;
            cachedIdentityHash = h;
        }
        return h;
    }

    @CEntryPoint(exceptionHandler = IsolatedCompileClient.IntExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(callerEpilogue = IsolatedCompileClient.ExceptionRethrowCallerEpilogue.class)
    private static int getIdentityHashCode0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<?> h) {
        Object target = IsolatedCompileClient.get().unhand(h);
        return computeIdentityHashCode(target);
    }
}
