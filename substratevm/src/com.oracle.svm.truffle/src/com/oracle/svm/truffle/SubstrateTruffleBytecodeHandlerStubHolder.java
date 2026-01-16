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
package com.oracle.svm.truffle;

import static com.oracle.svm.core.deopt.Deoptimizer.StubType.NoDeoptStub;
import static com.oracle.svm.truffle.SubstrateTruffleBytecodeHandlerStub.unwrap;
import static jdk.graal.compiler.core.common.spi.ForeignCallDescriptor.CallSideEffect.NO_SIDE_EFFECT;

import java.util.Arrays;
import java.util.List;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.infrastructure.WrappedJavaType;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.svm.core.Uninterruptible;
import com.oracle.svm.core.deopt.Deoptimizer.DeoptStub;
import com.oracle.svm.core.meta.MethodPointer;
import com.oracle.svm.core.snippets.SnippetRuntime;
import com.oracle.svm.core.snippets.SnippetRuntime.SubstrateForeignCallDescriptor;
import com.oracle.svm.core.snippets.SubstrateForeignCallTarget;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.annotation.AnnotationValueSupport;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.truffle.host.TruffleHostEnvironment;
import jdk.graal.compiler.truffle.host.TruffleKnownHostTypes;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This class is used to manage the bytecode handlers stubs for Truffle, providing a way to
 * initialize and retrieve the handlers.
 */
public final class SubstrateTruffleBytecodeHandlerStubHolder {

    /**
     * Default handler for Truffle bytecode during threading. This method cannot be invoked
     * directly; it is intended to be jumped to as a threading target. It assumes that return values
     * are already in place and will directly return to the caller.
     */
    @SubstrateForeignCallTarget(stubCallingConvention = false, fullyUninterruptible = true)
    @Uninterruptible(reason = "empty stub")
    @DeoptStub(stubType = NoDeoptStub)
    static void defaultHandler() {
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public static AnalysisMethod getDefaultHandler(MetaAccessProvider metaAccess) {
        SubstrateForeignCallDescriptor defaultHandlerDescriptor = SnippetRuntime.findForeignCall(SubstrateTruffleBytecodeHandlerStubHolder.class, "defaultHandler", NO_SIDE_EFFECT);
        return (AnalysisMethod) defaultHandlerDescriptor.findMethod(metaAccess);
    }

    /**
     * Truffle bytecode handler table. This mapping is used during image building but each handler
     * array will be persisted in the generated image.
     */
    @Platforms(Platform.HOSTED_ONLY.class) //
    private final EconomicMap<ResolvedJavaMethod, MethodPointer[]> bytecodeHandlers = EconomicMap.create();

    /**
     * Initializes and populates the bytecode handler table.
     */
    @Platforms(Platform.HOSTED_ONLY.class)
    public void initializeBytecodeHandlers(AnalysisMethod defaultHandler, EconomicMap<ResolvedJavaMethod, ResolvedJavaMethod> registeredBytecodeHandlers) {
        GraalError.guarantee(defaultHandler != null, "default handler is null");
        TruffleHostEnvironment truffleHostEnvironment = TruffleHostEnvironment.get(defaultHandler);
        if (truffleHostEnvironment == null) {
            // TruffleHostEnvironment is not initialized
            return;
        }
        TruffleKnownHostTypes truffleTypes = truffleHostEnvironment.types();
        ResolvedJavaType typeBytecodeInterpreterHandler = ((WrappedJavaType) truffleTypes.BytecodeInterpreterHandler).getWrapped();
        ResolvedJavaType typeBytecodeInterpreterHandlerConfig = ((WrappedJavaType) truffleTypes.BytecodeInterpreterHandlerConfig).getWrapped();

        MethodPointer defaultHandlerPointer = new MethodPointer(defaultHandler);
        for (ResolvedJavaMethod handler : registeredBytecodeHandlers.getKeys()) {
            ResolvedJavaMethod stubWrapper = registeredBytecodeHandlers.get(handler);
            ResolvedJavaMethod caller = ((SubstrateTruffleBytecodeHandlerStub) unwrap(stubWrapper)).getCallsite().getEnclosingMethod();

            MethodPointer[] handlerTable = bytecodeHandlers.get(caller);
            if (handlerTable == null) {
                // Creates a table large enough to host all opcodes
                AnnotationValue bytecodeInterpreterHandlerConfigAnnotation = AnnotationValueSupport.getDeclaredAnnotationValue(typeBytecodeInterpreterHandlerConfig, caller);
                int maxOpcode = bytecodeInterpreterHandlerConfigAnnotation.get("maximumOperationCode", Integer.class);
                GraalError.guarantee(maxOpcode >= 0 && maxOpcode < Integer.MAX_VALUE, "maximumOperationCode is %d", maxOpcode);
                handlerTable = new MethodPointer[maxOpcode + 1];
                // By default, all bytecode handlers point to defaultHandler
                Arrays.fill(handlerTable, defaultHandlerPointer);
                bytecodeHandlers.put(caller, handlerTable);
            }

            AnnotationValue annotation = AnnotationValueSupport.getDeclaredAnnotationValue(typeBytecodeInterpreterHandler, handler);
            for (Object opcode : annotation.get("value", List.class)) {
                // Assumes BytecodeInterpreterHandlerConfig is with a large enough maxOpcode
                handlerTable[(Integer) opcode] = new MethodPointer(stubWrapper);
            }
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public MethodPointer[] getBytecodeHandlers(ResolvedJavaMethod caller) {
        MethodPointer[] handlers = bytecodeHandlers.get(caller);
        GraalError.guarantee(handlers != null, "Bytecode handlers not yet initialized!");
        return handlers;
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public Iterable<MethodPointer[]> getAllBytecodeHandlers() {
        return bytecodeHandlers.getValues();
    }
}
