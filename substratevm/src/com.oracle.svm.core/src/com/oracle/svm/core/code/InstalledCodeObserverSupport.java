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

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.code.InstalledCodeObserver.InstalledCodeObserverHandle;
import com.oracle.svm.core.meta.SharedMethod;

public final class InstalledCodeObserverSupport {
    private final List<InstalledCodeObserver.Factory> observerFactories = new ArrayList<>();

    @Platforms(Platform.HOSTED_ONLY.class)
    public InstalledCodeObserverSupport() {
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

    public static InstalledCodeObserver.InstalledCodeObserverHandle[] installObservers(InstalledCodeObserver[] observers) {
        InstalledCodeObserver.InstalledCodeObserverHandle[] observerHandles = new InstalledCodeObserver.InstalledCodeObserverHandle[observers.length];
        int index = 0;
        for (InstalledCodeObserver observer : observers) {
            observerHandles[index++] = observer.install();
        }
        return observerHandles;
    }

    public static void activateObservers(InstalledCodeObserver.InstalledCodeObserverHandle[] observerHandles) {
        for (InstalledCodeObserverHandle handle : observerHandles) {
            if (handle != null) {
                handle.activate();
            }
        }
    }

    public static void removeObservers(InstalledCodeObserver.InstalledCodeObserverHandle[] observerHandles) {
        for (InstalledCodeObserverHandle handle : observerHandles) {
            if (handle != null) {
                handle.release();
            }
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code", mayBeInlined = true)
    public static void removeObserversOnTearDown(InstalledCodeObserverHandle[] observerHandles) {
        for (InstalledCodeObserverHandle handle : observerHandles) {
            if (handle != null) {
                handle.releaseOnTearDown();
            }
        }
    }
}
