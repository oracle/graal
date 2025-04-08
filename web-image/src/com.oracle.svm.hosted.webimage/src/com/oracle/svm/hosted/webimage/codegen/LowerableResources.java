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

package com.oracle.svm.hosted.webimage.codegen;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.webimage.AnalysisUtil;
import com.oracle.svm.hosted.webimage.WebImageHostedConfiguration;

public class LowerableResources {
    /**
     * The following order is observed in code generation:
     * <p>
     * 1. bootstrap definitions: for starting the VM, e.g., configuration.
     * <p>
     * 2. runtime definitions: runtime support, lowered before Java code and heap objects. May
     * access Java code in function body lazily.
     * <p>
     * 3. extra: initialization code AFTER lowering of Java classes but BEFORE heap objects.
     * Intended for initialization code that access Java classes eagerly.
     */
    public static final LowerableResource[] bootstrap = new LowerableResource[]{
                    bootstrapResource("runtime/feature-detection.js", false),
                    bootstrapResource("runtime/string-util.js", false),
                    bootstrapResource("runtime/log-console.js", false),
                    bootstrapResource("runtime/runtime.js", false),
    };

    public static final LowerableResource CWD = runtimeResource("runtime/cwd.js", false);
    public static final LowerableResource STACK_TRACE = runtimeResource("runtime/stack-trace.js", false);

    public static final LowerableResource[] runtime = new LowerableResource[]{
                    CWD,
                    runtimeResource("runtime/arrayinit.js", true),
                    runtimeResource("runtime/arraycopyof.js", true),
                    runtimeResource("runtime/arrayequals.js", true),
                    runtimeResource("runtime/array-vtableinit.js", false),
                    runtimeResource("runtime/internal-error.js", true),
                    runtimeResource("runtime/float-convert.js", true),
                    runtimeResource("runtime/instanceof-runtime.js", true),
                    runtimeResource("runtime/library-loader.js", true),
                    runtimeResource("runtime/long64.js", true),
                    runtimeResource("runtime/clone-runtime.js", true),
                    runtimeResource("runtime/class-object-init.js", true),
                    runtimeResource("runtime/reinterpret-for-lowering.js", true),
                    STACK_TRACE,
                    runtimeResource("runtime/unmanaged-memory.js", true),
                    runtimeResource("runtime/unsafe-runtime.js", true),
                    runtimeResource("runtime/unsigned-math.js", true),
    };
    public static final LowerableResource JSCONVERSION_COMMON = extraResource("runtime/jsconversion.js", false);

    public static final LowerableResource[] extra = new LowerableResource[]{
                    JSCONVERSION_COMMON,
                    extraResource("runtime/jsconversion-js.js", true),
                    extraResource("runtime/string-extensions.js", true),
    };

    /**
     * Optional resources are lowered on demand.
     * <p>
     * Each optional resource is handled specially relative to the ordering of other resources.
     */
    public static final LowerableResource LOAD_CMD_ARGS = new LowerableResource("runtime/load-cmd-args.js", WebImageCodeGen.class, false);
    public static final LowerableResource TIMER = new LowerableResource("runtime/timer.js", WebImageCodeGen.class, false);

    public static final List<LowerableResource> optional = new ArrayList<>(Arrays.asList(LOAD_CMD_ARGS, TIMER));
    public static final List<LowerableResource> thirdParty = new ArrayList<>();

    public static void lower(JSCodeGenTool codeGenTool, LowerableResource... resources) {
        for (LowerableResource resource : resources) {
            codeGenTool.lowerFile(resource);
        }
    }

    private static LowerableResource bootstrapResource(String name, boolean shouldIntrinsify) {
        return new LowerableResource(name, WebImageCodeGen.class, shouldIntrinsify);
    }

    /**
     * May access Java classes lazily inside functions.
     */
    private static LowerableResource runtimeResource(String name, boolean shouldIntrinsify) {
        return new LowerableResource(name, WebImageCodeGen.class, shouldIntrinsify);
    }

    /**
     * Access Java classes eagerly.
     */
    private static LowerableResource extraResource(String name, boolean shouldIntrinsify) {
        return new LowerableResource(name, WebImageCodeGen.class, shouldIntrinsify);
    }

    public static void processResources(Feature.BeforeAnalysisAccess access, WebImageHostedConfiguration config) {
        for (LowerableResource resource : config.getAnalysisResources()) {
            if (!resource.shouldIntrinsify()) {
                continue;
            }

            String content = JSIntrinsifyFile.readFile(resource::getStream);
            JSIntrinsifyFile.FileData data = new JSIntrinsifyFile.FileData(resource.getName(), content);
            JSIntrinsifyFile.collectIntrinsifications(data);
            AnalysisUtil.processFileData((FeatureImpl.BeforeAnalysisAccessImpl) access, data);
            resource.markRegistered(data);
        }
    }
}
