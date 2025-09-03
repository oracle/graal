/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.hub.registry;

import org.graalvm.nativeimage.impl.ClassLoadingSupport;

import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.hub.RuntimeClassLoading;

@AutomaticallyRegisteredImageSingleton(ClassLoadingSupport.class)
public class ClassLoadingSupportImpl implements ClassLoadingSupport {
    // This should work for virtual threads so it can't be a FastThreadLocal.
    private final ThreadLocal<Integer> ignoreReflectionConfiguration = new ThreadLocal<>() {
        @Override
        protected Integer initialValue() {
            return 0;
        }
    };

    @Override
    public boolean isSupported() {
        return RuntimeClassLoading.isSupported();
    }

    @Override
    public boolean followReflectionConfiguration() {
        return ignoreReflectionConfiguration.get() == 0;
    }

    @Override
    public void startIgnoreReflectionConfigurationScope() {
        int previous = ignoreReflectionConfiguration.get();
        ignoreReflectionConfiguration.set(Math.incrementExact(previous));
    }

    @Override
    public void endIgnoreReflectionConfigurationScope() {
        int previous = ignoreReflectionConfiguration.get();
        if (previous == 0) {
            throw new IllegalStateException("Unbalanced start/end arbitrary classloading allowed scopes");
        }
        assert previous > 0;
        ignoreReflectionConfiguration.set(previous - 1);
    }
}
