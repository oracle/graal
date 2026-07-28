/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.pointsto.standalone.plugins;

import java.util.function.Supplier;

import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.standalone.StandaloneHost;

import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.graphbuilderconf.ClassInitializationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.vm.ci.meta.ConstantPool;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Applies standalone build-time class initialization decisions during graph building.
 *
 * Standalone analysis does not reuse the full native-image initialization pipeline, so this plugin
 * bridges graph building to {@link StandaloneHost}, which already encapsulates both the allow/deny
 * decision and the runtime-handling fallback when eager initialization is not possible on the
 * current analysis thread.
 */
public final class StandaloneClassInitializationPlugin implements ClassInitializationPlugin {
    /**
     * Host entry point for standalone class initialization decisions.
     */
    private final StandaloneHost host;

    /**
     * Creates a plugin that delegates standalone class-initialization decisions to {@code host}.
     */
    public StandaloneClassInitializationPlugin(StandaloneHost host) {
        this.host = host;
    }

    /**
     * Returns {@code true} so graph building can defer initialization decisions to
     * {@link #apply(GraphBuilderContext, ResolvedJavaType, Supplier)} instead of forcing eager
     * loading through the constant pool alone.
     */
    @Override
    public boolean supportsLazyInitialization(ConstantPool cp) {
        return true;
    }

    /**
     * Resolves the referenced type on a best-effort basis without letting guest-specific resolution
     * failures abort parsing before standalone initialization policy can decide whether eager
     * build-time initialization is actually required.
     */
    @Override
    public void loadReferencedType(GraphBuilderContext builder, ConstantPool cp, int cpi, int bytecode) {
        try {
            cp.loadReferencedType(cpi, bytecode, false);
        } catch (Throwable ignored) {
            /*
             * Standalone class initialization should stay non-intrusive here, matching the default
             * no-class-initialization behavior when guest resolution needs runtime-only support on
             * the current analysis thread.
             */
        }
    }

    /**
     * Applies standalone build-time initialization for analysis types when the configured host
     * strategy currently allows it.
     */
    @Override
    public boolean apply(GraphBuilderContext builder, ResolvedJavaType type, Supplier<FrameState> frameState) {
        if (type instanceof AnalysisType analysisType) {
            host.maybeInitializeAtBuildTime(analysisType);
        }
        return false;
    }
}
