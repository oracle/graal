/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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

import com.oracle.svm.core.c.function.CEntryPointOptions;

/** Base class for objects that act as a proxy for objects in the compilation client's isolate. */
public abstract class IsolatedObjectProxy<T> {
    protected final ClientHandle<T> handle;
    private int cachedHash = 0;
    private String cachedToString = null;

    protected IsolatedObjectProxy(ClientHandle<T> handle) {
        this.handle = handle;
    }

    public ClientHandle<T> getHandle() {
        return handle;
    }

    @Override
    public boolean equals(Object obj) {
        if (this != obj && obj != null && getClass().equals(obj.getClass())) {
            IsolatedObjectProxy<?> other = (IsolatedObjectProxy<?>) obj;
            if (cachedHash != 0 && other.cachedHash != 0 && cachedHash != other.cachedHash) {
                assert !isEqual(other) : "Sane hashCode implementation";
                return false;
            }
            return isEqual(other);
        }
        return (this == obj);
    }

    boolean isEqual(IsolatedObjectProxy<?> other) {
        return IsolateAwareObjectConstantEquality.isolatedConstantHandleTargetsEqual(
                        IsolatedCompileContext.get().getClient(), handle, other.handle);
    }

    @Override
    public int hashCode() {
        if (cachedHash == 0) {
            int h = hashCode0(IsolatedCompileContext.get().getClient(), handle);
            cachedHash = (h != 0) ? h : 1;
        }
        return cachedHash;
    }

    @Override
    public String toString() {
        // Cache: handlized objects already stay reachable until the end of the compilation
        String s = cachedToString;
        if (s == null) {
            CompilerHandle<String> h = toString0(IsolatedCompileContext.get().getClient(), handle);
            s = IsolatedCompileContext.get().unhand(h);
            cachedToString = s;
        }
        return s;
    }

    @CEntryPoint(exceptionHandler = IsolatedCompileClient.IntExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(callerEpilogue = IsolatedCompileClient.ExceptionRethrowCallerEpilogue.class)
    private static int hashCode0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<?> handle) {
        return IsolatedCompileClient.get().unhand(handle).hashCode();
    }

    @CEntryPoint(exceptionHandler = IsolatedCompileClient.WordExceptionHandler.class, include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    @CEntryPointOptions(callerEpilogue = IsolatedCompileClient.ExceptionRethrowCallerEpilogue.class)
    private static CompilerHandle<String> toString0(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<?> handle) {
        Object obj = IsolatedCompileClient.get().unhand(handle);
        String s = "Isolated: " + obj;
        return IsolatedCompileClient.get().createStringInCompiler(s);
    }
}
