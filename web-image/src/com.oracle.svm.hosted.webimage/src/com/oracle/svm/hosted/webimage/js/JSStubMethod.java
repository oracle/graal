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

package com.oracle.svm.hosted.webimage.js;

import java.util.List;
import java.util.Objects;

import org.graalvm.nativeimage.AnnotationAccess;
import org.graalvm.webimage.api.JS;

import com.oracle.graal.pointsto.infrastructure.ResolvedSignature;
import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.hosted.annotation.CustomSubstitutionMethod;
import com.oracle.svm.hosted.phases.HostedGraphKit;
import com.oracle.svm.hosted.webimage.options.WebImageOptions;
import com.oracle.svm.hosted.webimage.phases.WebImageHostedGraphKit;
import com.oracle.svm.util.ReflectionUtil;
import com.oracle.svm.webimage.annotation.JSRawCall;
import com.oracle.svm.webimage.functionintrinsics.JSConversion;

import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.java.FrameStateBuilder;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.ConstantNode;
import jdk.graal.compiler.nodes.DeadEndNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.PiNode;
import jdk.graal.compiler.nodes.ProfileData;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.vm.ci.common.JVMCIError;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * Call stub for invoking Java methods that are annotated with {@link JS}.
 * <p>
 * This call stub generates a function prologue and epilogue for the given JS code and adds a
 * {@link JSBodyWithExceptionNode} that will be lowered to the actual JS code in the annotation.
 */
public class JSStubMethod extends CustomSubstitutionMethod {

    private static final double HIGH_INSTANCEOF_PROBABILITY = 0.9999;

    public JSStubMethod(ResolvedJavaMethod original) {
        super(original);
    }

    @Override
    public boolean canBeInlined() {
        return false;
    }

    @Override
    public boolean shouldBeInlined() {
        return false;
    }

    @Override
    public boolean hasNeverInlineDirective() {
        return true;
    }

    private static Stamp returnStamp(ResolvedSignature<AnalysisType> sig) {
        JavaKind returnKind = sig.getReturnKind();
        if (returnKind == JavaKind.Object) {
            return StampFactory.object(TypeReference.createTrustedWithoutAssumptions(sig.getReturnType()));
        } else {
            return StampFactory.forKind(returnKind);
        }
    }

    @Override
    public StructuredGraph buildGraph(DebugContext debug, AnalysisMethod method, HostedProviders providers, Purpose purpose) {
        boolean rawCall;
        boolean coercion;
        JSBody.JSCode jsCode;
        if (getOriginal() instanceof JSObjectAccessMethod jsObjectAccessMethod) {
            rawCall = false;
            /*
             * Only the load return value should be coerced. For stores, only regular conversion
             * should be applied.
             *
             * TODO GR-65036 We should coerce in both directions
             */
            coercion = jsObjectAccessMethod.isLoad();
            jsCode = jsObjectAccessMethod.getJSCode();
        } else {
            rawCall = AnnotationAccess.isAnnotationPresent(method, JSRawCall.class);
            coercion = AnnotationAccess.isAnnotationPresent(method, JS.Coerce.class);
            JS js = Objects.requireNonNull(AnnotationAccess.getAnnotation(method, JS.class));
            jsCode = new JSBody.JSCode(js, method);
        }
        return buildGraph(debug, method, providers, purpose, jsCode, coercion, rawCall);
    }

    private static StructuredGraph buildGraph(DebugContext debug, AnalysisMethod method, HostedProviders providers, Purpose purpose, JSBody.JSCode jsCode, boolean coercion, boolean rawCall) {
        if (rawCall && coercion) {
            throw JVMCIError.shouldNotReachHere("Cannot use JS.Coerce and JSRawCall annotation simultaneously: " + method.format("%H.%n"));
        }
        WebImageHostedGraphKit kit = new WebImageHostedGraphKit(debug, providers, method, purpose);
        FrameStateBuilder state = kit.getFrameState();
        ValueNode[] arguments = kit.getInitialArguments().toArray(ValueNode.EMPTY_ARRAY);

        AnalysisType returnType = method.getSignature().getReturnType();

        // Step 1: convert the Java arguments to JavaScript values.
        List<AnalysisType> paramTypes = method.toParameterList();
        for (int i = 0; i < arguments.length; i++) {
            arguments[i] = convertArgument(kit, arguments[i], paramTypes.get(i), rawCall, coercion);
            state.clearLocals();
            state.clearStack();
        }

        /*
         * JSBody node requires an explicit 'this' pointer. Insert a null constant for static
         * methods, non-static methods already have the receiver as the first argument.
         */
        if (method.isStatic()) {
            ValueNode[] fullArguments = new ValueNode[arguments.length + 1];
            fullArguments[0] = kit.createConstant(JavaConstant.NULL_POINTER, JavaKind.Object);
            System.arraycopy(arguments, 0, fullArguments, 1, arguments.length);
            arguments = fullArguments;
        }

        // Step 2: insert the JS body representation node.
        int bci = kit.bci();

        Stamp jsBodyStamp = rawCall ? returnStamp(method.getSignature()) : StampFactory.object();
        AnalysisMethod exceptionHandler;

        if (WebImageOptions.getBackend() == WebImageOptions.CompilerBackend.WASMGC) {
            exceptionHandler = null;
        } else {
            exceptionHandler = kit.getMetaAccess().lookupJavaMethod(ReflectionUtil.lookupMethod(JSConversion.class, "handleJSError", Object.class));
        }

        ValueNode returnValue = kit.createJSBody(jsCode, method, arguments, jsBodyStamp, state, bci, exceptionHandler).asNode();

        // Step 3: convert the JavaScript return value back to a Java value.
        returnValue = convertReturnValue(kit, returnValue, returnType, rawCall, coercion);

        kit.createReturn(returnValue, returnType.getJavaKind());
        return kit.finalizeGraph();
    }

    private static ValueNode convertArgument(HostedGraphKit kit, ValueNode initialArgument, AnalysisType paramType, boolean rawCall, boolean coercion) {
        ValueNode argument = initialArgument;

        if (rawCall) {
            return argument;
        }

        // Step 1: box the argument.
        if (paramType.isPrimitive()) {
            argument = kit.createBoxing(argument, paramType.getJavaKind(), boxedTypeFor(kit, paramType.getJavaKind()));
        }

        // Step 2: perform Java-to-JavaScript type conversion.
        // Step 2a: if coercion is enabled, implicitly convert Java values to JSValue objects
        // where possible, according to the coercion rules.
        if (coercion) {
            try {
                AnalysisMethod coerceJavaToJavaScriptMethod = kit.getMetaAccess().lookupJavaMethod(JSConversion.class.getDeclaredMethod("coerceJavaToJavaScript", Object.class));
                argument = kit.createInvokeWithExceptionAndUnwind(coerceJavaToJavaScriptMethod, CallTargetNode.InvokeKind.Static, kit.getFrameState(), kit.bci(), argument);
            } catch (NoSuchMethodException e) {
                throw JVMCIError.shouldNotReachHere(e);
            }
        }

        // Step 2b: apply Java-to-JavaScript conversion rules.
        try {
            AnalysisMethod javaToJavaScriptMethod = kit.getMetaAccess().lookupJavaMethod(JSConversion.class.getDeclaredMethod("javaToJavaScript", Object.class));
            argument = kit.createInvokeWithExceptionAndUnwind(javaToJavaScriptMethod, CallTargetNode.InvokeKind.Static, kit.getFrameState(), kit.bci(), argument);
        } catch (NoSuchMethodException e) {
            throw JVMCIError.shouldNotReachHere(e);
        }

        return argument;
    }

    private static AnalysisType boxedTypeFor(HostedGraphKit kit, JavaKind kind) {
        Class<?> boxedType = kind.toBoxedJavaClass();
        assert boxedType != null : "Unexpected kind: " + kind;
        return kit.getMetaAccess().lookupJavaType(boxedType);
    }

    private static ValueNode convertReturnValue(HostedGraphKit kit, ValueNode jsBody, AnalysisType returnType, boolean rawCall, boolean coercion) {
        ValueNode returnValue = jsBody;

        // Step 1: check void, and discard JavaScript value in this case.
        if (returnType.getJavaKind() == JavaKind.Void) {
            return null;
        }

        if (rawCall) {
            return returnValue;
        }

        AnalysisType referenceReturnType = returnType.isPrimitive() ? boxedTypeFor(kit, returnType.getJavaKind()) : returnType;

        // Step 2a: apply JavaScript-to-Java conversion rules.
        try {
            AnalysisMethod javaScriptToJavaMethod = kit.getMetaAccess().lookupJavaMethod(JSConversion.class.getDeclaredMethod("javaScriptToJava", Object.class));
            returnValue = kit.createInvokeWithExceptionAndUnwind(javaScriptToJavaMethod, CallTargetNode.InvokeKind.Static, kit.getFrameState(), kit.bci(), returnValue);
        } catch (NoSuchMethodException e) {
            throw JVMCIError.shouldNotReachHere(e);
        }

        // Step 2b: if coercion is enabled, implicitly convert JSValue objects to Java objects
        // where possible, according to the coercion rules.
        if (coercion) {
            try {
                AnalysisMethod coerceJavaScriptToJavaMethod = kit.getMetaAccess().lookupJavaMethod(JSConversion.class.getDeclaredMethod("coerceJavaScriptToJava", Object.class, Class.class));
                Stamp classStamp = StampFactory.forDeclaredType(null, kit.getMetaAccess().lookupJavaType(Class.class), true).getTrustedStamp();
                // Note: we use the boxed type (where applicable) for the conversion, as
                // unboxing happens in a later step.
                ConstantNode hub = ConstantNode.forConstant(classStamp, kit.getConstantReflection().asObjectHub(referenceReturnType), kit.getMetaAccess(), kit.getGraph());
                returnValue = kit.createInvokeWithExceptionAndUnwind(coerceJavaScriptToJavaMethod, CallTargetNode.InvokeKind.Static, kit.getFrameState(), kit.bci(), returnValue, hub);
            } catch (NoSuchMethodException e) {
                throw JVMCIError.shouldNotReachHere(e);
            }
        }

        // Step 3: Perform checkcast against the expected (boxed) return type.
        try {
            // Note: it's important to explicitly encode the instanceof check in the graph (i.e. not
            // call a Java method that does the check and the cast), because the instanceof needs to
            // be visible during analysis.
            final TypeReference referenceReturnTypeRef = TypeReference.createTrusted(null, referenceReturnType);
            LogicNode instanceOf = InstanceOfNode.createAllowNull(referenceReturnTypeRef, returnValue, null, null);
            IfNode ifNode = kit.startIf(instanceOf, ProfileData.BranchProbabilityData.injected(HIGH_INSTANCEOF_PROBABILITY));
            kit.elsePart();
            AnalysisMethod throwClassCastExceptionMethod = kit.getMetaAccess().lookupJavaMethod(
                            JSConversion.class.getDeclaredMethod("throwClassCastExceptionForClass", Object.class, Class.class));
            ConstantNode classValue = ConstantNode.forConstant(kit.getConstantReflection().asJavaClass(referenceReturnType), kit.getMetaAccess(), kit.getGraph());
            kit.createInvokeWithExceptionAndUnwind(throwClassCastExceptionMethod, CallTargetNode.InvokeKind.Static, kit.getFrameState(), kit.bci(), returnValue, classValue);
            kit.append(new DeadEndNode());
            kit.thenPart();
            kit.endIf();

            // Insert PiNode to strengthen stamp of type checked node
            returnValue = PiNode.create(returnValue, returnValue.stamp(NodeView.DEFAULT).join(StampFactory.object(referenceReturnTypeRef)), ifNode.trueSuccessor());
        } catch (NoSuchMethodException e) {
            throw JVMCIError.shouldNotReachHere(e);
        }

        // Step 4: Perform unboxing if necessary.
        if (returnType.isPrimitive()) {
            returnValue = kit.createUnboxing(returnValue, returnType.getJavaKind());
        }

        return returnValue;
    }

}
