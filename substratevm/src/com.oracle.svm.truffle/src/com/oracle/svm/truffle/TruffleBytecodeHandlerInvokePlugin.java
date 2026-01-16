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
import com.oracle.svm.common.meta.MultiMethod;

import jdk.graal.compiler.annotation.AnnotationValue;
import jdk.graal.compiler.annotation.AnnotationValueSupport;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.GraphBuilderContext;
import jdk.graal.compiler.nodes.graphbuilderconf.NodePlugin;
import jdk.graal.compiler.truffle.TruffleBytecodeHandlerCallsite;
import jdk.graal.compiler.truffle.TruffleBytecodeHandlerCallsite.TruffleBytecodeHandlerTypes;
import jdk.graal.compiler.truffle.host.TruffleHostEnvironment;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * A {@link NodePlugin} implementation that collects invocations of Truffle bytecode handlers and
 * generates stubs for them.
 * <p>
 * This plugin is responsible for detecting invocations of methods annotated with
 * {@code @BytecodeInterpreterHandler} within methods annotated with
 * {@code @BytecodeInterpreterSwitch}. It creates a {@link TruffleBytecodeHandlerCallsite} object to
 * represent the call site and generates a stub for the handler method. The stub is then registered
 * as a root method in the analysis universe.
 */
public final class TruffleBytecodeHandlerInvokePlugin implements NodePlugin {

    private final EconomicMap<ResolvedJavaMethod, ResolvedJavaMethod> registeredBytecodeHandlers;
    private final SubstrateTruffleBytecodeHandlerStubHolder stubHolder;
    private final boolean threadingEnabled;

    private final EconomicMap<ResolvedJavaMethod, ResolvedJavaMethod> nextOpcodeCache = EconomicMap.create();

    public TruffleBytecodeHandlerInvokePlugin(EconomicMap<ResolvedJavaMethod, ResolvedJavaMethod> registeredBytecodeHandlers,
                    SubstrateTruffleBytecodeHandlerStubHolder stubHolder, boolean threadingEnabled) {
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
        if (enclosingMethod instanceof MultiMethod sm && !sm.isOriginalMethod()) {
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

        TruffleBytecodeHandlerCallsite callSite = new TruffleBytecodeHandlerCallsite(enclosingMethod, b.bci(), target, truffleTypes);
        SubstrateTruffleBytecodeHandlerStub stub = new SubstrateTruffleBytecodeHandlerStub(stubHolder, unwrap(target.getDeclaringClass()),
                        callSite, threading, nextOpcode, safepoint);

        AnalysisUniverse universe = ((AnalysisMetaAccess) b.getMetaAccess()).getUniverse();
        AnalysisMethod stubWrapper = universe.lookup(stub);
        universe.getBigbang().addRootMethod(stubWrapper, true, "Bytecode handler stub " + callSite.getStubName());

        synchronized (registeredBytecodeHandlers) {
            registeredBytecodeHandlers.put(target, stubWrapper);
        }
        return false;
    }
}
