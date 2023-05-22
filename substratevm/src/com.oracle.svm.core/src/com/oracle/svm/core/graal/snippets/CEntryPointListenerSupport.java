/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.snippets;

import java.util.Arrays;

import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

@AutomaticallyRegisteredImageSingleton
public class CEntryPointListenerSupport {
    private CEntryPointListener[] listeners;

    @Platforms(Platform.HOSTED_ONLY.class)
    public CEntryPointListenerSupport() {
        listeners = new CEntryPointListener[0];
    }

    @Fold
    public static CEntryPointListenerSupport singleton() {
        return ImageSingletons.lookup(CEntryPointListenerSupport.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public synchronized void register(CEntryPointListener listener) {
        assert listener != null;
        int oldLength = listeners.length;
        // We expect a very small number of listeners, so only increase the size by 1.
        listeners = Arrays.copyOf(listeners, oldLength + 1);
        listeners[oldLength] = listener;
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void beforeThreadAttach() {
        for (int i = 0; i < listeners.length; i++) {
            listeners[i].beforeThreadAttach();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void afterThreadAttach() {
        for (int i = 0; i < listeners.length; i++) {
            listeners[i].afterThreadAttach();
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void errorThreadAttach(int error) {
        if (isInstalled()) {
            CEntryPointListener.singleton().errorThreadAttach(error);
        }
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public void beforeThreadDetach() {
        for (int i = 0; i < listeners.length; i++) {
            listeners[i].beforeThreadDetach();
        }
    }
}
