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

import java.util.Arrays;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.infrastructure.WrappedJavaType;
import com.oracle.svm.core.meta.MethodPointer;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.annotation.AnnotationValueSupport;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.truffle.BytecodeHandlerConfig;
import jdk.graal.compiler.truffle.host.TruffleHostEnvironment;
import jdk.graal.compiler.truffle.host.TruffleKnownHostTypes;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Manages the SubstrateVM bytecode-handler tables for Truffle interpreters.
 * <p>
 * One handler table is created per interpreter (identified via {@code interpreterHolder} and
 * {@code BytecodeHandlerConfig}). Each table is prefilled with that interpreter's default stub and
 * then patched with opcode-specific stub entries for the bytecode handlers used from that
 * interpreter.
 */
@Platforms(Platform.HOSTED_ONLY.class) //
public final class SubstrateTruffleBytecodeHandlerStubHelper {

    /**
     * Truffle bytecode handler table. This mapping is used during image building but each handler
     * array will be persisted in the generated image.
     */
    private final EconomicMap<BytecodeHandlerStubKey, MethodPointer[]> bytecodeHandlers = EconomicMap.create();

    /**
     * Initializes and populates all bytecode-handler tables from the registered stub wrappers.
     */
    public void initializeBytecodeHandlers(EconomicMap<BytecodeHandlerStubKey, ResolvedJavaMethod> registeredBytecodeHandlers) {
        if (registeredBytecodeHandlers.isEmpty()) {
            return;
        }
        ResolvedJavaMethod firstMethod = null;
        for (BytecodeHandlerStubKey key : registeredBytecodeHandlers.getKeys()) {
            if (key.method() != null) {
                firstMethod = key.method();
                break;
            }
        }
        GraalError.guarantee(firstMethod != null, "No bytecode handler methods were registered");

        TruffleHostEnvironment truffleHostEnvironment = TruffleHostEnvironment.get(firstMethod);
        if (truffleHostEnvironment == null) {
            // TruffleHostEnvironment is not initialized
            return;
        }
        TruffleKnownHostTypes truffleTypes = truffleHostEnvironment.types();
        ResolvedJavaType typeBytecodeInterpreterHandler = ((WrappedJavaType) truffleTypes.BytecodeInterpreterHandler).getWrapped();

        for (BytecodeHandlerStubKey key : registeredBytecodeHandlers.getKeys()) {
            BytecodeHandlerStubKey tableKey = BytecodeHandlerStubKey.createDefaultHandlerKey(key.interpreterHolder(), key.handlerConfig());
            BytecodeHandlerConfig handlerConfig = key.handlerConfig();
            ResolvedJavaMethod stubWrapper = registeredBytecodeHandlers.get(key);

            MethodPointer[] handlerTable = bytecodeHandlers.get(tableKey);
            if (handlerTable == null) {
                int maxOpcode = handlerConfig.getMaximumOperationCode();
                GraalError.guarantee(maxOpcode >= 0 && maxOpcode < Integer.MAX_VALUE, "maximumOperationCode is %d", maxOpcode);
                handlerTable = new MethodPointer[maxOpcode + 1];

                BytecodeHandlerStubKey defaultKey = BytecodeHandlerStubKey.createDefaultHandlerKey(key.interpreterHolder(), handlerConfig);
                ResolvedJavaMethod defaultHandler = registeredBytecodeHandlers.get(defaultKey);
                GraalError.guarantee(defaultHandler != null, "default handler is null");
                MethodPointer defaultHandlerPointer = new MethodPointer(defaultHandler);

                Arrays.fill(handlerTable, defaultHandlerPointer);
                bytecodeHandlers.put(tableKey, handlerTable);
            }

            if (key.method() == null) {
                // default handler
                continue;
            }

            AnnotationValue annotation = AnnotationValueSupport.getDeclaredAnnotationValue(typeBytecodeInterpreterHandler, key.method());
            for (Integer opcode : annotation.getList("value", Integer.class)) {
                GraalError.guarantee(handlerTable[opcode].getMethod().equals(registeredBytecodeHandlers.get(tableKey)), "Method for opcode %d already set.", opcode);
                handlerTable[opcode] = new MethodPointer(stubWrapper);
            }
        }
    }

    /**
     * Returns the handler table for one interpreter (identified via {@code interpreterHolder} and
     * {@code BytecodeHandlerConfig}).
     */
    public MethodPointer[] getBytecodeHandlers(ResolvedJavaType interpreterHolder, BytecodeHandlerConfig handlerConfig) {
        MethodPointer[] handlers = bytecodeHandlers.get(BytecodeHandlerStubKey.createDefaultHandlerKey(interpreterHolder, handlerConfig));
        GraalError.guarantee(handlers != null, "Bytecode handlers not yet initialized!");
        return handlers;
    }

    public Iterable<MethodPointer[]> getAllBytecodeHandlers() {
        return bytecodeHandlers.getValues();
    }
}
