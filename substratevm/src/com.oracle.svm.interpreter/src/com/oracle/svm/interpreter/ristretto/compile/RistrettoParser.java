/*
 * Copyright (c) 2025, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter.ristretto.compile;

import com.oracle.svm.interpreter.ristretto.meta.RistrettoMetaAccess;

import jdk.graal.compiler.core.common.PermanentBailoutException;
import jdk.graal.compiler.java.BytecodeParser;
import jdk.graal.compiler.java.GraphBuilderPhase;
import jdk.graal.compiler.nodes.CallTargetNode.InvokeKind;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.graphbuilderconf.IntrinsicContext;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugin;
import jdk.graal.compiler.nodes.graphbuilderconf.InvocationPlugins;
import jdk.vm.ci.meta.ExceptionHandler;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Ristretto (native image bytecode based JIT) specific implementation of the bytecode parser.
 */
public class RistrettoParser extends BytecodeParser {
    private static final String GRAAL_DIRECTIVES_CLASS_NAME = "jdk.graal.compiler.api.directives.GraalDirectives";
    private static final String RISTRETTO_DIRECTIVES_CLASS_NAME = "com.oracle.svm.interpreter.ristretto.RistrettoDirectives";

    private MetaAccessProvider cachedMetaAccess;

    public RistrettoParser(GraphBuilderPhase.Instance graphBuilderInstance, StructuredGraph graph, BytecodeParser parent, ResolvedJavaMethod method, int entryBCI,
                    IntrinsicContext intrinsicContext) {
        super(graphBuilderInstance, graph, parent, method, entryBCI, intrinsicContext);
    }

    @Override
    public MetaAccessProvider getMetaAccess() {
        if (cachedMetaAccess != null) {
            return cachedMetaAccess;
        }
        MetaAccessProvider original = super.getMetaAccess();
        assert original != null;
        cachedMetaAccess = new RistrettoMetaAccess(original);
        return cachedMetaAccess;
    }

    /**
     * Do not apply ordinary invocation plugins while parsing a bytecode in an exception-handler
     * range. Ristretto needs the normal invoke path here because it explicitly creates the invoke's
     * local exception successor. Compiler directives are semantic graph markers, so their plugins
     * must still be applied. If any other plugin cannot be disabled, fail permanently instead of
     * silently dropping the exception edge.
     */
    @Override
    protected boolean tryInvocationPlugin(InvokeKind invokeKind, ValueNode[] args, ResolvedJavaMethod targetMethod, JavaKind resultType) {
        int currentBci = bci();
        if (hasLocalExceptionHandlerAtBci(currentBci)) {
            InvocationPlugins plugins = graphBuilderConfig.getPlugins().getInvocationPlugins();
            InvocationPlugin plugin = plugins.lookupInvocation(targetMethod, true, !parsingIntrinsic(), options);
            if (plugin != null && !isCompilerDirectivePluginTarget(targetMethod)) {
                if (intrinsicContext != null && intrinsicContext.isCallToOriginal(targetMethod)) {
                    return false;
                }
                if (plugin.canBeDisabled()) {
                    return false;
                }
                throw new PermanentBailoutException("Ristretto cannot apply non-disableable invocation plugin for %s at bci %d because the invoke is covered by a local exception handler.",
                                targetMethod.format("%H.%n(%p)"), currentBci);
            }
        }
        return super.tryInvocationPlugin(invokeKind, args, targetMethod, resultType);
    }

    private static boolean isCompilerDirectivePluginTarget(ResolvedJavaMethod targetMethod) {
        String methodName = targetMethod.format("%H.%n(%p)");
        return methodName.startsWith(GRAAL_DIRECTIVES_CLASS_NAME + ".") || methodName.startsWith(RISTRETTO_DIRECTIVES_CLASS_NAME + ".");
    }

    private boolean hasLocalExceptionHandlerAtBci(int currentBci) {
        if (currentBci < 0 || !method.hasBytecodes()) {
            return false;
        }
        for (ExceptionHandler handler : method.getExceptionHandlers()) {
            if (currentBci >= handler.getStartBCI() && currentBci < handler.getEndBCI()) {
                return true;
            }
        }
        return false;
    }
}
