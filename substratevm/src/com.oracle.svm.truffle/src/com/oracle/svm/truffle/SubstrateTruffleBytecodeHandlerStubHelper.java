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

import static com.oracle.svm.truffle.SubstrateTruffleBytecodeHandlerStub.unwrap;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;

import org.graalvm.collections.EconomicMap;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.graal.pointsto.infrastructure.WrappedJavaType;
import com.oracle.svm.core.meta.MethodPointer;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.annotation.AnnotationValueSupport;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.truffle.host.TruffleHostEnvironment;
import jdk.graal.compiler.truffle.host.TruffleKnownHostTypes;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * This class is used to manage the bytecode handlers stubs for Truffle, providing a way to
 * initialize and retrieve the handlers.
 */
@Platforms(Platform.HOSTED_ONLY.class) //
public final class SubstrateTruffleBytecodeHandlerStubHelper {

    private record TemplateVariant(
                    ResolvedJavaMethod dispatchMethod,
                    int templateIndex) {
    }

    /**
     * Truffle bytecode handler table. This mapping is used during image building but each handler
     * array will be persisted in the generated image.
     */
    private final EconomicMap<TemplateVariant, MethodPointer[]> bytecodeHandlers = EconomicMap.create();
    private static final Path DEBUG_FILL_LOG = Path.of("/Users/yudzheng/Workspace/GR-73325/bch_table_fill.log");

    /**
     * Initializes and populates the bytecode handler table.
     */
    public void initializeBytecodeHandlers(EconomicMap<ResolvedJavaMethod, ResolvedJavaMethod[]> registeredBytecodeHandlers) {
        if (registeredBytecodeHandlers.isEmpty()) {
            return;
        }
        TruffleHostEnvironment truffleHostEnvironment = TruffleHostEnvironment.get(registeredBytecodeHandlers.getKeys().iterator().next());
        if (truffleHostEnvironment == null) {
            // TruffleHostEnvironment is not initialized
            return;
        }
        TruffleKnownHostTypes truffleTypes = truffleHostEnvironment.types();
        ResolvedJavaType typeBytecodeInterpreterHandler = ((WrappedJavaType) truffleTypes.BytecodeInterpreterHandler).getWrapped();
        ResolvedJavaType typeBytecodeInterpreterHandlerConfig = ((WrappedJavaType) truffleTypes.BytecodeInterpreterHandlerConfig).getWrapped();

        for (ResolvedJavaMethod handler : registeredBytecodeHandlers.getKeys()) {
            ResolvedJavaMethod[] stubWrappers = registeredBytecodeHandlers.get(handler);
            ResolvedJavaMethod caller = ((SubstrateTruffleBytecodeHandlerStub) unwrap(stubWrappers[0])).getCallsite().getEnclosingMethod();

            // Creates a table large enough to host all opcodes
            AnnotationValue bytecodeInterpreterHandlerConfigAnnotation = AnnotationValueSupport.getDeclaredAnnotationValue(typeBytecodeInterpreterHandlerConfig, caller);

            int templatesLength = bytecodeInterpreterHandlerConfigAnnotation.getInt("templates");
            GraalError.guarantee(templatesLength > 0 && templatesLength <= 8, "templates is %d", templatesLength);
            GraalError.guarantee(stubWrappers.length == templatesLength, "not all the stubs are generated");

            for (int i = 0; i < templatesLength; i++) {
                TemplateVariant key = new TemplateVariant(caller, i);
                MethodPointer[] handlerTable = bytecodeHandlers.get(key);
                if (handlerTable == null) {
                    // Creates a table large enough to host all opcodes
                    int maxOpcode = bytecodeInterpreterHandlerConfigAnnotation.getInt("maximumOperationCode");
                    GraalError.guarantee(maxOpcode >= 0 && maxOpcode < Integer.MAX_VALUE, "maximumOperationCode is %d", maxOpcode);
                    handlerTable = new MethodPointer[maxOpcode + 1];
                    // By default, all bytecode handlers point to defaultHandler, which is keyed by
                    // the caller in registeredBytecodeHandlers
                    ResolvedJavaMethod defaultHandler = registeredBytecodeHandlers.get(caller)[i];
                    GraalError.guarantee(defaultHandler != null, "default handler is null");
                    MethodPointer defaultHandlerPointer = new MethodPointer(defaultHandler);
                    Arrays.fill(handlerTable, defaultHandlerPointer);

                    bytecodeHandlers.put(key, handlerTable);
                }

                if (caller.equals(handler)) {
                    // default handler
                    continue;
                }

                AnnotationValue annotation = AnnotationValueSupport.getDeclaredAnnotationValue(typeBytecodeInterpreterHandler, handler);
                for (Integer opcode : annotation.getList("value", Integer.class)) {
                    // Assumes BytecodeInterpreterHandlerConfig is with a large enough maxOpcode
                    handlerTable[opcode] = new MethodPointer(stubWrappers[i]);
                    String line = String.format("caller=%s template=%d opcode=%d handler=%s stub=%s%n",
                                    caller.format("%H.%n(%p)"), i, opcode, handler.format("%H.%n(%p)"), stubWrappers[i].format("%H.%n(%p)"));
                    try {
                        Files.writeString(DEBUG_FILL_LOG, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
                    } catch (IOException e) {
                        throw GraalError.shouldNotReachHere(e);
                    }
                }
            }
        }
    }

    public MethodPointer[] getBytecodeHandlers(ResolvedJavaMethod caller, int i) {
        MethodPointer[] handlers = bytecodeHandlers.get(new TemplateVariant(caller, i));
        GraalError.guarantee(handlers != null, "Bytecode handlers not yet initialized!");
        return handlers;
    }

    public Iterable<MethodPointer[]> getAllBytecodeHandlers() {
        return bytecodeHandlers.getValues();
    }
}
