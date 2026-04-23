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

import static com.oracle.svm.truffle.SubstrateTruffleBytecodeHandlerStub.asTruffleBytecodeHandlerTypes;
import static com.oracle.svm.truffle.SubstrateTruffleBytecodeHandlerStub.unwrap;

import java.util.Arrays;
import java.util.List;

import org.graalvm.collections.EconomicMap;

import com.oracle.graal.pointsto.meta.AnalysisMetaAccess;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisUniverse;
import com.oracle.svm.common.meta.MethodVariant;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.annotation.AnnotationValueSupport;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import jdk.graal.compiler.truffle.BytecodeHandlerConfig;
import jdk.graal.compiler.truffle.TruffleBytecodeHandlerCallsite.TruffleBytecodeHandlerTypes;
import jdk.graal.compiler.truffle.TruffleBytecodeHandlerStubHelper;
import jdk.graal.compiler.truffle.host.TruffleHostEnvironment;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * A {@link NodePlugin} implementation that collects invocations of Truffle bytecode handlers and
 * generates stubs for them.
 * <p>
 * This plugin is responsible for detecting invocations of methods annotated with
 * {@code @BytecodeInterpreterHandler} within methods annotated with
 * {@code @BytecodeInterpreterSwitch}. It generates a stub for the handler method and registers that
 * stub as a root method in the analysis universe.
 */
public final class TruffleBytecodeHandlerInvokePlugin implements NodePlugin {

    private final EconomicMap<BytecodeHandlerStubKey, ResolvedJavaMethod> registeredBytecodeHandlers;
    private final SubstrateTruffleBytecodeHandlerStubHelper stubHolder;
    private final boolean threadingEnabled;

    private final EconomicMap<ResolvedJavaMethod, ResolvedJavaMethod> nextOpcodeCache = EconomicMap.create();

    public TruffleBytecodeHandlerInvokePlugin(EconomicMap<BytecodeHandlerStubKey, ResolvedJavaMethod> registeredBytecodeHandlers,
                    SubstrateTruffleBytecodeHandlerStubHelper stubHolder, boolean threadingEnabled) {
        this.registeredBytecodeHandlers = registeredBytecodeHandlers;
        this.stubHolder = stubHolder;
        this.threadingEnabled = threadingEnabled;
    }

    private static ResolvedJavaMethod nextOpcodeMethod(ResolvedJavaType typeBytecodeInterpreterFetchOpcode, ResolvedJavaType holder) {
        List<ResolvedJavaMethod> nextOpcodeAnnotated = Arrays.stream(holder.getDeclaredMethods(false))
                        .filter(m -> AnnotationValueSupport.isAnnotationPresent(typeBytecodeInterpreterFetchOpcode, m))
                        .toList();
        GraalError.guarantee(nextOpcodeAnnotated.size() == 1, "Expected exactly one method annotated with BytecodeInterpreterFetchOpcode, found %d", nextOpcodeAnnotated.size());
        return nextOpcodeAnnotated.getFirst();
    }

    @Override
    public boolean handleInvoke(GraphBuilderContext b, ResolvedJavaMethod target, ValueNode[] oldArguments) {
        ResolvedJavaMethod enclosingMethod = b.getMethod();
        if (enclosingMethod instanceof MethodVariant sm && !sm.isOriginalMethod()) {
            return false;
        }

        TruffleHostEnvironment truffleHostEnvironment = TruffleHostEnvironment.get(enclosingMethod);
        if (truffleHostEnvironment == null) {
            // TruffleHostEnvironment is not initialized yet
            return false;
        }
        // Test if in an @BytecodeInterpreterSwitch annotated method
        TruffleBytecodeHandlerTypes truffleTypes = asTruffleBytecodeHandlerTypes(truffleHostEnvironment.types());

        if (!AnnotationValueSupport.isAnnotationPresent(truffleTypes.typeBytecodeInterpreterSwitch(), enclosingMethod)) {
            return false;
        }

        // Test if calling an @BytecodeInterpreterHandler annotated method
        AnnotationValue handlerAnnotationValue = AnnotationValueSupport.getDeclaredAnnotationValue(truffleTypes.typeBytecodeInterpreterHandler(), target);
        if (handlerAnnotationValue == null) {
            return false;
        }

        boolean threading = threadingEnabled && handlerAnnotationValue.getBoolean("threading");
        boolean safepoint = handlerAnnotationValue.getBoolean("safepoint");

        BytecodeHandlerConfig handlerConfig = BytecodeHandlerConfig.getHandlerConfig(enclosingMethod, target, truffleTypes);
        ResolvedJavaType interpreterHolder = unwrap(enclosingMethod.getDeclaringClass());
        String stubName = TruffleBytecodeHandlerStubHelper.getStubName(target);

        ResolvedJavaMethod nextOpcode = null;
        if (threading) {
            if (!nextOpcodeCache.containsKey(enclosingMethod)) {
                ResolvedJavaMethod temp = nextOpcodeMethod(truffleTypes.typeBytecodeInterpreterFetchOpcode(), target.getDeclaringClass());
                synchronized (nextOpcodeCache) {
                    nextOpcodeCache.putIfAbsent(enclosingMethod, temp);
                }
            }
            nextOpcode = nextOpcodeCache.get(enclosingMethod);
        }
        SubstrateTruffleBytecodeHandlerStub stub = new SubstrateTruffleBytecodeHandlerStub(stubHolder, unwrap(target.getDeclaringClass()),
                        stubName, interpreterHolder, handlerConfig, threading, nextOpcode, safepoint, false, target);

        AnalysisUniverse universe = ((AnalysisMetaAccess) b.getMetaAccess()).getUniverse();
        AnalysisMethod handlerStubWrapper = universe.lookup(stub);
        universe.getBigbang().addRootMethod(handlerStubWrapper, true, "Bytecode handler stub " + stubName);

        BytecodeHandlerStubKey handlerKey = BytecodeHandlerStubKey.create(unwrap(target), interpreterHolder, handlerConfig);
        synchronized (registeredBytecodeHandlers) {
            registeredBytecodeHandlers.put(handlerKey, handlerStubWrapper);

            if (threading) {
                BytecodeHandlerStubKey defaultHandlerKey = BytecodeHandlerStubKey.createDefaultHandlerKey(interpreterHolder, handlerConfig);
                if (!registeredBytecodeHandlers.containsKey(defaultHandlerKey)) {
                    SubstrateTruffleBytecodeHandlerStub defaultHandlerStub = new SubstrateTruffleBytecodeHandlerStub(stubHolder, unwrap(target.getDeclaringClass()),
                                    "__stub_defaultHandler", interpreterHolder, handlerConfig, false, null, false, true, null);
                    AnalysisMethod defaultStubWrapper = universe.lookup(defaultHandlerStub);
                    universe.getBigbang().addRootMethod(defaultStubWrapper, true, "Default bytecode handler stub");

                    registeredBytecodeHandlers.put(defaultHandlerKey, defaultStubWrapper);
                }
            }
        }

        return false;
    }

}
