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

package com.oracle.svm.hosted.webimage.wasmgc;

import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.NO_SIDE_EFFECT;

import java.util.stream.Stream;

import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.StaticFieldsSupport;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.meta.SubstrateForeignCallsProvider;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;
import com.oracle.svm.hosted.FeatureImpl;
import com.oracle.svm.hosted.webimage.wasm.debug.WasmDebug;
import com.oracle.svm.hosted.webimage.wasm.nodes.WasmTrapNode;
import com.oracle.svm.webimage.platform.WebImageWasmGCPlatform;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.options.Option;

public class WasmGCUnsafeSupport {

    static class Options {
        @Option(help = "Include human readable error messages for illegal Unsafe accesses")//
        public static final HostedOptionKey<Boolean> UnsafeErrorMessages = new HostedOptionKey<>(true);
    }

    public static final SnippetRuntime.SubstrateForeignCallDescriptor FATAL_ACCESS_ERROR = SnippetRuntime.findForeignCall(WasmGCUnsafeSupport.class, "fatalAccessError", NO_SIDE_EFFECT);

    /**
     * Stub objects representing the "base" for unsafe static field accesses (returned by
     * {@code sun.misc.Unsafe#staticFieldBase}). They replace the actual base objects stored in
     * {@link StaticFieldsSupport}. This is needed because we want non-array objects as the base
     * objects (explained further down), and {@link StaticFieldsSupport} uses array types for its
     * base objects.
     * <p>
     * Unsafe accesses for static fields work the same as for instance fields. These base objects
     * will have a custom {@link com.oracle.svm.core.hub.DynamicHub} with a custom dispatch access
     * array, pointing to access functions for static fields at certain offsets. It's as if the
     * static fields are instance fields of these base objects. If these objects were array types,
     * unsafe accesses on them would use the unsafe access mechanism of arrays, which does not use
     * an access dispatch table.
     */
    public static final Object STATIC_OBJECT_FIELD_BASE = new Object();
    public static final Object STATIC_PRIMITIVE_FIELD_BASE = new Object();

    /**
     * Error handler for failed unsafe accesses.
     * <p>
     * Produces a human-readable error message giving all the necessary context (object type,
     * offset, access kind) in addition to a caller-provided error message to find the offending
     * access.
     * <p>
     * Only used if {@link #includeErrorMessage()} is {@code true}, otherwise the access will most
     * likely trap without error or even keep going and produce an arbitrary result.
     *
     * @param receiver The object on which the access failed
     * @param s Error message with additional context
     * @param offset The offset at which the access failed
     */
    @SubstrateForeignCallTarget(stubCallingConvention = false)
    public static void fatalAccessError(Object receiver, String s, long offset, boolean isRead) {
        StringBuilder sb = new StringBuilder();
        sb.append("Fatal unsafe ").append(isRead ? "read" : "write").append(" error");
        if (receiver == null) {
            sb.append(" for null object: ");
        } else {
            sb.append(" for object of type ").append(receiver.getClass().getTypeName());
        }
        sb.append(" at offset ").append(offset).append(": ").append(s);

        WasmDebug.getErrorStream().println(sb);
        WasmTrapNode.trap();
    }

    @Fold
    public static boolean includeErrorMessage() {
        return Options.UnsafeErrorMessages.getValue();
    }

}

@AutomaticallyRegisteredFeature
@Platforms(WebImageWasmGCPlatform.class)
class WasmGCUnsafeFeature implements InternalFeature {

    @Override
    public void registerForeignCalls(SubstrateForeignCallsProvider foreignCalls) {
        if (WasmGCUnsafeSupport.includeErrorMessage()) {
            foreignCalls.register(WasmGCUnsafeSupport.FATAL_ACCESS_ERROR);
        }
        WasmGCUnalignedUnsafeSupport.READ_ARRAY_FOREIGN_CALLS.values().forEach(foreignCalls::register);
        WasmGCUnalignedUnsafeSupport.WRITE_ARRAY_FOREIGN_CALLS.values().forEach(foreignCalls::register);
    }

    @Override
    public void duringSetup(DuringSetupAccess access) {
        access.registerObjectReplacer(other -> {
            /*
             * The getters are only callable after the hosted universe is built, before, they throw
             * an exception.
             */
            if (BuildPhaseProvider.isHostedUniverseBuilt()) {
                if (other == StaticFieldsSupport.getCurrentLayerStaticObjectFields()) {
                    return WasmGCUnsafeSupport.STATIC_OBJECT_FIELD_BASE;
                }
                if (other == StaticFieldsSupport.getCurrentLayerStaticPrimitiveFields()) {
                    return WasmGCUnsafeSupport.STATIC_PRIMITIVE_FIELD_BASE;
                }
            }
            return other;
        });
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess a) {
        FeatureImpl.BeforeAnalysisAccessImpl access = (FeatureImpl.BeforeAnalysisAccessImpl) a;
        if (WasmGCUnsafeSupport.includeErrorMessage()) {
            access.getBigBang().addRootMethod((AnalysisMethod) WasmGCUnsafeSupport.FATAL_ACCESS_ERROR.findMethod(access.getMetaAccess()), true,
                            "Fatal Unsafe error handler, registered in " + WasmGCUnsafeFeature.class);
        }

        Stream.concat(WasmGCUnalignedUnsafeSupport.READ_ARRAY_FOREIGN_CALLS.values().stream(), WasmGCUnalignedUnsafeSupport.WRITE_ARRAY_FOREIGN_CALLS.values().stream()).forEach((descriptor -> {
            access.getBigBang().addRootMethod((AnalysisMethod) descriptor.findMethod(access.getMetaAccess()), true,
                            "Unsafe array accesses, registered in " + WasmGCUnsafeFeature.class);
        }));
    }

    @Override
    public void beforeCompilation(BeforeCompilationAccess a) {
        FeatureImpl.BeforeCompilationAccessImpl access = (FeatureImpl.BeforeCompilationAccessImpl) a;

        /*
         * We reset the static field base objects and rescan them so that the object replacer,
         * registered above is triggered.
         */
        StaticFieldsSupport.setData(new Object[0], new byte[0]);
        access.getHeapScanner().rescanObject(StaticFieldsSupport.getCurrentLayerStaticObjectFields());
        access.getHeapScanner().rescanObject(StaticFieldsSupport.getCurrentLayerStaticPrimitiveFields());
    }
}
