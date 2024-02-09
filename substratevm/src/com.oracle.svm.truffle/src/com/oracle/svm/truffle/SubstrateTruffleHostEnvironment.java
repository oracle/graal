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
package com.oracle.svm.truffle;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.util.UserError;
import com.oracle.truffle.compiler.HostMethodInfo;
import com.oracle.truffle.compiler.TruffleCompilable;
import com.oracle.truffle.compiler.TruffleCompilerRuntime;

import jdk.graal.compiler.truffle.TruffleCompilerImpl;
import jdk.graal.compiler.truffle.TruffleElementCache;
import jdk.graal.compiler.truffle.host.TruffleHostEnvironment;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

@Platforms(Platform.HOSTED_ONLY.class)
final class SubstrateTruffleHostEnvironment extends TruffleHostEnvironment {

    private final HostMethodInfoCache hostCache;

    SubstrateTruffleHostEnvironment(TruffleCompilerRuntime runtime, MetaAccessProvider metaAccess) {
        super(runtime, metaAccess);
        this.hostCache = new HostMethodInfoCache();
    }

    @Override
    protected TruffleCompilerImpl createCompiler(TruffleCompilable compilable) {
        throw UserError.abort("Creating a truffle compiler during SVM host compilation is not supported.");
    }

    @Override
    public HostMethodInfo getHostMethodInfo(ResolvedJavaMethod method) {
        return hostCache.get(method);
    }

    final class HostMethodInfoCache extends TruffleElementCache<ResolvedJavaMethod, HostMethodInfo> {

        HostMethodInfoCache() {
            super(HOST_METHOD_CACHE_SIZE); // cache size
        }

        @Override
        protected Object createKey(ResolvedJavaMethod method) {
            /*
             * At SVM image build time we can directly refer to the method as a key.
             */
            return method;
        }

        @Override
        protected HostMethodInfo computeValue(ResolvedJavaMethod method) {
            return runtime().getHostMethodInfo(method);
        }

    }

}
