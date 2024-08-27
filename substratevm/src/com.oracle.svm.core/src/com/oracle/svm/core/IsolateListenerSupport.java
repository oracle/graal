/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import java.util.Arrays;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.util.VMError;

import jdk.graal.compiler.api.replacements.Fold;

@AutomaticallyRegisteredImageSingleton
public class IsolateListenerSupport {
    private IsolateListener[] listeners;

    @Platforms(Platform.HOSTED_ONLY.class)
    public IsolateListenerSupport() {
        listeners = new IsolateListener[0];
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public synchronized void register(IsolateListener listener) {
        assert listener != null;
        int oldLength = listeners.length;
        listeners = Arrays.copyOf(listeners, oldLength + 1);
        listeners[oldLength] = listener;
    }

    @Fold
    public static IsolateListenerSupport singleton() {
        return ImageSingletons.lookup(IsolateListenerSupport.class);
    }

    @Uninterruptible(reason = "Thread state not yet set up.")
    public void afterCreateIsolate(Isolate isolate) {
        for (IsolateListener listener : listeners) {
            try {
                listener.afterCreateIsolate(isolate);
            } catch (Throwable e) {
                throw VMError.shouldNotReachHere(e);
            }
        }
    }

    @Uninterruptible(reason = "The isolate teardown is in progress.")
    public void onIsolateTeardown() {
        for (int i = listeners.length - 1; i >= 0; i--) {
            try {
                listeners[i].onIsolateTeardown();
            } catch (Throwable e) {
                throw VMError.shouldNotReachHere(e);
            }
        }
    }

    public interface IsolateListener {
        /**
         * Implementations must not throw any exceptions. Note that the thread that creates the
         * isolate is still unattached when this method is called.
         */
        @Uninterruptible(reason = "Thread state not yet set up.")
        default void afterCreateIsolate(@SuppressWarnings("unused") Isolate isolate) {
        }

        /**
         * Implementations must not throw any exceptions. Note that this method is called on
         * listeners in the reverse order of {@link #afterCreateIsolate}.
         *
         * This method is called during isolate teardown, when the VM is guaranteed to be
         * single-threaded (i.e., all other threads already exited on the OS-level). This method is
         * not called for applications that use {@link JavaMainWrapper}.
         */
        @Uninterruptible(reason = "The isolate teardown is in progress.")
        default void onIsolateTeardown() {
        }
    }
}
