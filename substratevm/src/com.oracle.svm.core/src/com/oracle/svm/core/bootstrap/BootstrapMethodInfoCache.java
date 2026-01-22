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
package com.oracle.svm.core.bootstrap;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.core.feature.AutomaticallyRegisteredImageSingleton;
import com.oracle.svm.core.traits.BuiltinTraits;
import com.oracle.svm.core.traits.BuiltinTraits.BuildtimeAccessOnly;
import com.oracle.svm.core.traits.SingletonTraits;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * This image singleton is used to cache the {@link BootstrapMethodInfo} computed at run time to
 * make sure they are only computed once.
 */
@SingletonTraits(access = BuildtimeAccessOnly.class, layeredCallbacks = BuiltinTraits.NoLayeredCallbacks.class)
@AutomaticallyRegisteredImageSingleton
public class BootstrapMethodInfoCache {

    /**
     * Map used to cache the {@link BootstrapMethodInfo} and reuse it for duplicated bytecode,
     * avoiding execution of the bootstrap method for the same bci and method pair. This can happen
     * during bytecode parsing as some blocks are duplicated, or for methods that are parsed
     * multiple times (see MultiMethod).
     */
    private final ConcurrentMap<BootstrapMethodRecord, BootstrapMethodInfo> bootstrapMethodInfoCache = new ConcurrentHashMap<>();

    /**
     * The key of the cache.
     */
    public record BootstrapMethodRecord(int bci, int cpi, ResolvedJavaMethod method) {
    }

    public static BootstrapMethodInfoCache singleton() {
        return ImageSingletons.lookup(BootstrapMethodInfoCache.class);
    }

    public ConcurrentMap<BootstrapMethodRecord, BootstrapMethodInfo> getBootstrapMethodInfoCache() {
        return bootstrapMethodInfoCache;
    }
}
