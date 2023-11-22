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
package com.oracle.svm.graal.hotspot.libgraal.truffle;

import jdk.graal.compiler.core.common.util.MethodKey;
import jdk.graal.compiler.truffle.TruffleCompilerImpl;
import jdk.graal.compiler.truffle.TruffleElementCache;
import jdk.graal.compiler.truffle.host.TruffleHostEnvironment;
import jdk.graal.compiler.truffle.hotspot.HotSpotTruffleCompilerImpl;

import com.oracle.truffle.compiler.HostMethodInfo;
import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilerRuntime;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

final class LibGraalTruffleHostEnvironment extends TruffleHostEnvironment {

    private final HostMethodInfoCache hostCache = new HostMethodInfoCache();

    LibGraalTruffleHostEnvironment(TruffleCompilerRuntime runtime, MetaAccessProvider metaAccess) {
        super(runtime, metaAccess);
    }

    @Override
    public HostMethodInfo getHostMethodInfo(ResolvedJavaMethod method) {
        return hostCache.get(method);
    }

    @Override
    protected TruffleCompilerImpl createCompiler(TruffleCompilable ast) {
        HotSpotTruffleCompilerImpl compiler = HotSpotTruffleCompilerImpl.create(runtime());
        compiler.initialize(ast, true);
        return compiler;
    }

    final class HostMethodInfoCache extends TruffleElementCache<ResolvedJavaMethod, HostMethodInfo> {

        HostMethodInfoCache() {
            super(HOST_METHOD_CACHE_SIZE); // cache size
        }

        @Override
        protected Object createKey(ResolvedJavaMethod method) {
            /*
             * On libgraal we cannot reference ResolvedJavaMethod as part of a cache as it may
             * become invalid between compilations.
             */
            return new MethodKey(method);
        }

        @Override
        protected HostMethodInfo computeValue(ResolvedJavaMethod method) {
            return runtime().getHostMethodInfo(method);
        }

    }

}
