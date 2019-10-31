/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.code;

import java.util.ArrayList;
import java.util.List;

import org.graalvm.compiler.code.CompilationResult;
import org.graalvm.compiler.debug.DebugContext;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.word.Pointer;
import org.graalvm.word.WordFactory;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.c.NonmovableArray;
import com.oracle.svm.core.c.NonmovableArrays;
import com.oracle.svm.core.code.InstalledCodeObserver.InstalledCodeObserverHandle;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.snippets.KnownIntrinsics;

public final class InstalledCodeObserverSupport {
    private static final InstalledCodeObserverHandleAction ACTION_ATTACH = h -> getAccessor(h).attachToCurrentIsolate(h);
    private static final InstalledCodeObserverHandleAction ACTION_DETACH = h -> getAccessor(h).detachFromCurrentIsolate(h);
    private static final InstalledCodeObserverHandleAction ACTION_RELEASE = h -> getAccessor(h).release(h);
    private static final InstalledCodeObserverHandleAction ACTION_ACTIVATE = h -> getAccessor(h).activate(h);

    private final List<InstalledCodeObserver.Factory> observerFactories = new ArrayList<>();

    @Platforms(Platform.HOSTED_ONLY.class)
    InstalledCodeObserverSupport() {
    }

    public void addObserverFactory(InstalledCodeObserver.Factory observerFactory) {
        observerFactories.add(observerFactory);
    }

    public InstalledCodeObserver[] createObservers(DebugContext debug, SharedMethod method, CompilationResult compilation, Pointer code) {
        InstalledCodeObserver[] observers = new InstalledCodeObserver[observerFactories.size()];
        int index = 0;
        for (InstalledCodeObserver.Factory factory : observerFactories) {
            observers[index++] = factory.create(debug, method, compilation, code);
        }
        return observers;
    }

    public static NonmovableArray<InstalledCodeObserverHandle> installObservers(InstalledCodeObserver[] observers) {
        if (observers.length == 0) {
            return NonmovableArrays.nullArray();
        }
        NonmovableArray<InstalledCodeObserverHandle> observerHandles = NonmovableArrays.createWordArray(observers.length);
        for (int i = 0; i < observers.length; i++) {
            InstalledCodeObserverHandle handle = observers[i].install();
            NonmovableArrays.setWord(observerHandles, i, handle);
        }
        return observerHandles;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    private static InstalledCodeObserver.InstalledCodeObserverHandleAccessor getAccessor(InstalledCodeObserverHandle handle) {
        return KnownIntrinsics.convertUnknownValue(handle.getAccessor(), InstalledCodeObserver.InstalledCodeObserverHandleAccessor.class);
    }

    public static void activateObservers(NonmovableArray<InstalledCodeObserverHandle> observerHandles) {
        forEach(observerHandles, ACTION_ACTIVATE);
    }

    public static void detachFromCurrentIsolate(NonmovableArray<InstalledCodeObserverHandle> observerHandles) {
        forEach(observerHandles, ACTION_DETACH);
    }

    public static void attachToCurrentIsolate(NonmovableArray<InstalledCodeObserverHandle> observerHandles) {
        forEach(observerHandles, ACTION_ATTACH);
    }

    public static void removeObservers(NonmovableArray<InstalledCodeObserverHandle> observerHandles) {
        forEach(observerHandles, ACTION_RELEASE);
    }

    private interface InstalledCodeObserverHandleAction {
        void invoke(InstalledCodeObserverHandle handle);
    }

    private static void forEach(NonmovableArray<InstalledCodeObserverHandle> array, InstalledCodeObserverHandleAction action) {
        if (array.isNonNull()) {
            int length = NonmovableArrays.lengthOf(array);
            for (int i = 0; i < length; i++) {
                InstalledCodeObserverHandle handle = NonmovableArrays.getWord(array, i);
                if (handle.isNonNull()) {
                    action.invoke(handle);
                }
            }
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    public static void removeObserversOnTearDown(NonmovableArray<InstalledCodeObserverHandle> observerHandles) {
        if (observerHandles.isNonNull()) {
            int length = NonmovableArrays.lengthOf(observerHandles);
            for (int i = 0; i < length; i++) {
                InstalledCodeObserverHandle handle = NonmovableArrays.getWord(observerHandles, i);
                if (handle.isNonNull()) {
                    getAccessor(handle).releaseOnTearDown(handle);
                    NonmovableArrays.setWord(observerHandles, i, WordFactory.nullPointer());
                }
            }
        }
    }
}
