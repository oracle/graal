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

package com.oracle.svm.hosted.webimage.wasm.jni;

import java.lang.reflect.Modifier;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.snippets.ImplicitExceptions;
import com.oracle.svm.hosted.annotation.CustomSubstitutionMethod;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;
import com.oracle.svm.hosted.webimage.phases.WebImageHostedGraphKit;

import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnreachableControlSinkNode;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Stub method that substitutes native methods and just throws an exception.
 * <p>
 * This avoids build-time errors when native methods are found and instead throws an exception only
 * if the method is invoked.
 */
// TODO(GR-35288): Implement proper JNI call support.
public class WebImageWasmJNINativeCallWrapperMethod extends CustomSubstitutionMethod {
    public WebImageWasmJNINativeCallWrapperMethod(ResolvedJavaMethod original) {
        super(original);
    }

    @Override
    public boolean isSynthetic() {
        return true;
    }

    @Override
    public int getModifiers() {
        final int synthetic = 0x1000;
        /*
         * This wrapper method is synthetic, non-native, and not synchronized. Updates the original
         * modifiers to ensure this.
         */
        return (getOriginal().getModifiers() | synthetic) & ~(Modifier.NATIVE | Modifier.SYNCHRONIZED);
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, AnalysisMethod method, HostedProviders providers, Purpose purpose) {
        if (WebImageOptions.DebugOptions.VerificationPhases.getValue(debug.getOptions())) {
            /*
             * To catch any reachable non-substituted native JDK methods during testing, we fail
             * early here. Such methods should have a substitution (even if it just throws an
             * exception).
             */
            throw GraalError.shouldNotReachHere("Found non-substituted native method " + method.format("%H.%n(%P)"));
        }
        WebImageHostedGraphKit kit = new WebImageHostedGraphKit(debug, providers, method, purpose);
        JavaConstant errorMessage = providers.getConstantReflection().forString(method.format("Found unsupported native method %H.%n(%P)"));
        ConstantNode errorConstant = kit.append(ConstantNode.forConstant(errorMessage, providers.getMetaAccess()));
        kit.createInvokeWithExceptionAndUnwind(ImplicitExceptions.class, "throwLinkageError", CallTargetNode.InvokeKind.Static, errorConstant);
        kit.append(new UnreachableControlSinkNode());
        return kit.finalizeGraph();
    }
}
