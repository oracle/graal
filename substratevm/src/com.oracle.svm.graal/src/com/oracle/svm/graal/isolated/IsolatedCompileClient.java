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

import org.graalvm.nativeimage.ObjectHandle;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.handles.ThreadLocalHandles;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;

/**
 * Thread-local context object in a client isolate thread, that is, the isolate that has initiated a
 * compilation in a different isolate.
 *
 * @see IsolatedCompileContext
 */
public final class IsolatedCompileClient {

    private static final FastThreadLocalObject<IsolatedCompileClient> currentClient = //
                    FastThreadLocalFactory.createObject(IsolatedCompileClient.class, "IsolatedCompileClient.currentClient");

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public static IsolatedCompileClient get() {
        return currentClient.get();
    }

    public static void set(IsolatedCompileClient client) {
        assert (client == null) != (currentClient.get() == null);
        currentClient.set(client);
    }

    private final CompilerIsolateThread compiler;
    private final ThreadLocalHandles<ObjectHandle> handles = new ThreadLocalHandles<>(64);

    public IsolatedCompileClient(CompilerIsolateThread compiler) {
        this.compiler = compiler;
    }

    public CompilerIsolateThread getCompiler() {
        return compiler;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    ThreadLocalHandles<ObjectHandle> getHandleSet() {
        return handles;
    }

    @SuppressWarnings("unchecked")
    public <T> ClientHandle<T> hand(T object) {
        return (ClientHandle<T>) handles.create(object);
    }

    public <T> T unhand(ClientHandle<? extends T> handle) {
        return handles.getObject(handle);
    }

    public CompilerHandle<String> createStringInCompiler(String s) {
        try (CTypeConversion.CCharPointerHolder cstr = CTypeConversion.toCString(s)) {
            return createStringInCompiler0(compiler, cstr.get());
        }
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class)
    @CEntryPointOptions(publishAs = CEntryPointOptions.Publish.NotPublished)
    private static CompilerHandle<String> createStringInCompiler0(@SuppressWarnings("unused") CompilerIsolateThread compiler, CCharPointer cstr) {
        return IsolatedCompileContext.get().hand(CTypeConversion.toJavaString(cstr));
    }
}
