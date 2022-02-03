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

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Isolate;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Uninterruptible;

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
        for (int i = 0; i < listeners.length; i++) {
            listeners[i].afterCreateIsolate(isolate);
        }
    }

    public interface IsolateListener {
        @Uninterruptible(reason = "Thread state not yet set up.")
        void afterCreateIsolate(Isolate isolate);
    }
}
