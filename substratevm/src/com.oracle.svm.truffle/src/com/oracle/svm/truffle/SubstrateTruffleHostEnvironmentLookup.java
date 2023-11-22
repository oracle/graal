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

import jdk.graal.compiler.truffle.host.TruffleHostEnvironment;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.truffle.api.SubstrateTruffleRuntime;

import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This handles the Truffle host environment lookup on SVM. During native image compilation the host
 * environment is set. Later during runtime the host environment must not be used.
 */
public final class SubstrateTruffleHostEnvironmentLookup implements TruffleHostEnvironment.Lookup {

    private final TruffleHostEnvironment environment;

    /**
     * Used for SVM with the {@link TruffleFeature} enabled.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateTruffleHostEnvironmentLookup(SubstrateTruffleRuntime runtime, MetaAccessProvider metaAccess) {
        this.environment = new SubstrateTruffleHostEnvironment(runtime, metaAccess);
    }

    /**
     * Used for SVM with only the {@link TruffleBaseFeature} enabled.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public SubstrateTruffleHostEnvironmentLookup() {
        this.environment = null;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    @Override
    public TruffleHostEnvironment lookup(ResolvedJavaType forType) {
        return environment;
    }

}
