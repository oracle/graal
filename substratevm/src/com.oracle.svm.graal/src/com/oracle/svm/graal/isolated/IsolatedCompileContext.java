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
import org.graalvm.nativeimage.c.type.CCharPointerPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.function.CEntryPointOptions;
import com.oracle.svm.core.handles.ThreadLocalHandles;
import com.oracle.svm.core.threadlocal.FastThreadLocalFactory;
import com.oracle.svm.core.threadlocal.FastThreadLocalObject;

/**
 * Thread-local context object in the thread in the compiler isolate that is compiling on behalf of
 * a client.
 *
 * @see IsolatedCompileClient
 */
public final class IsolatedCompileContext {
    private static final FastThreadLocalObject<IsolatedCompileContext> currentContext = //
                    FastThreadLocalFactory.createObject(IsolatedCompileContext.class);

    public static IsolatedCompileContext get() {
        return currentContext.get();
    }

    public static void set(IsolatedCompileContext context) {
        assert (context == null) != (currentContext.get() == null);
        currentContext.set(context);
    }

    private final ClientIsolateThread client;
    private final ThreadLocalHandles<ObjectHandle> handles = new ThreadLocalHandles<>(64);

    public IsolatedCompileContext(ClientIsolateThread clientIsolate) {
        this.client = clientIsolate;
    }

    public ClientIsolateThread getClient() {
        return client;
    }

    @SuppressWarnings("unchecked")
    public <T> CompilerHandle<T> hand(T object) {
        return (CompilerHandle<T>) handles.create(object);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public <T> T unhand(CompilerHandle<? extends T> handle) {
        return handles.getObject(handle);
    }

    public ClientHandle<String> createStringInClient(CharSequence s) {
        try (CTypeConversion.CCharPointerHolder cstr = CTypeConversion.toCString(s)) {
            return createStringInClient0(client, cstr.get());
        }
    }

    public ClientHandle<String[]> createStringArrayInClient(String[] array) {
        try (CTypeConversion.CCharPointerPointerHolder cstrs = CTypeConversion.toCStrings(array)) {
            return createStringArrayInClient0(client, array.length, cstrs.get());
        }
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static ClientHandle<String> createStringInClient0(@SuppressWarnings("unused") ClientIsolateThread client, CCharPointer cstr) {
        return IsolatedCompileClient.get().hand(CTypeConversion.toJavaString(cstr));
    }

    @CEntryPoint
    @CEntryPointOptions(include = CEntryPointOptions.NotIncludedAutomatically.class, publishAs = CEntryPointOptions.Publish.NotPublished)
    private static ClientHandle<String[]> createStringArrayInClient0(@SuppressWarnings("unused") ClientIsolateThread client, int length, CCharPointerPointer ptrs) {
        String[] array = new String[length];
        for (int i = 0; i < length; i++) {
            array[i] = CTypeConversion.toJavaString(ptrs.read(i));
        }
        return IsolatedCompileClient.get().hand(array);
    }
}
