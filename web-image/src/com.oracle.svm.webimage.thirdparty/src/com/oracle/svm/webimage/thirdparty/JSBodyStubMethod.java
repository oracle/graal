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

package com.oracle.svm.webimage.thirdparty;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Function;

import org.graalvm.nativeimage.AnnotationAccess;

import com.oracle.graal.pointsto.meta.AnalysisMethod;
import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.annotation.CustomSubstitutionMethod;
import com.oracle.svm.hosted.phases.HostedGraphKit;
import com.oracle.svm.hosted.webimage.codegen.JSCodeGenTool;
import com.oracle.svm.hosted.webimage.js.JSBody;
import com.oracle.svm.hosted.webimage.js.JSBodyWithExceptionNode;
import com.oracle.svm.webimage.hightiercodegen.CodeGenTool;

import jdk.graal.compiler.core.common.calc.FloatConvert;
import jdk.graal.compiler.core.common.type.Stamp;
import jdk.graal.compiler.core.common.type.StampFactory;
import jdk.graal.compiler.core.common.type.TypeReference;
import jdk.graal.compiler.debug.DebugContext;
import jdk.graal.compiler.java.FrameStateBuilder;
import jdk.graal.compiler.nodes.CallTargetNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.ProfileData;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.UnwindNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.FloatConvertNode;
import jdk.graal.compiler.nodes.calc.NarrowNode;
import jdk.graal.compiler.nodes.calc.SignExtendNode;
import jdk.graal.compiler.nodes.extended.BoxNode;
import jdk.graal.compiler.nodes.java.InstanceOfNode;
import jdk.graal.compiler.nodes.java.NewInstanceNode;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import net.java.html.js.JavaScriptBody;
import net.java.html.js.JavaScriptResource;

/**
 * Call stub for invoking Java methods that are annotated with {@link JavaScriptBody}.
 *
 * This call stub generates a function prologue and epilogue for the given JS code and adds a
 * {@link JSBodyWithExceptionNode} that will be lowered to the actual JS code in the annotation.
 */
public class JSBodyStubMethod extends CustomSubstitutionMethod {

    private static final double HIGH_INSTANCEOF_PROBABILITY = 0.9999;

    public JSBodyStubMethod(ResolvedJavaMethod original) {
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

    @Override
    public StructuredGraph buildGraph(DebugContext debug, AnalysisMethod method, HostedProviders providers, Purpose purpose) {
        HostedGraphKit kit = new HostedGraphKit(debug, providers, method);
        FrameStateBuilder state = kit.getFrameState();
        ValueNode[] argNodes = kit.getInitialArguments().toArray(ValueNode.EMPTY_ARRAY);

        Stamp returnStamp = StampFactory.object();
        AnalysisType returnType = method.getSignature().getReturnType();
        JavaKind returnKind = returnType.getJavaKind();

        List<AnalysisType> argTypes = method.toParameterList();
        for (int i = 0; i < argNodes.length; i++) {
            argNodes[i] = convertArgument(kit, argTypes.get(i), argNodes[i]);
        }

        /*
         * JSBody node requires an explicit 'this' pointer. Insert a null constant for static
         * methods, non-static methods already have the receiver as the first argument.
         */
        if (method.isStatic()) {
            ValueNode[] fullArgNodes = new ValueNode[argNodes.length + 1];
            fullArgNodes[0] = kit.createConstant(JavaConstant.NULL_POINTER, JavaKind.Object);
            System.arraycopy(argNodes, 0, fullArgNodes, 1, argNodes.length);
            argNodes = fullArgNodes;
        }

        state.clearLocals();
        state.clearStack();

        JSBody.JSCode jsCode;
        JavaScriptBody javaScriptBody = AnnotationAccess.getAnnotation(method, JavaScriptBody.class);
        assert javaScriptBody != null;
        Function<CodeGenTool, String> codeSupplier;
        if (javaScriptBody.javacall()) {
            codeSupplier = x -> JavaScriptBodyIntrinsification.processJavaScriptBody(method.getName(), javaScriptBody.body(), (JSCodeGenTool) x).getProcessed();
        } else {
            codeSupplier = x -> javaScriptBody.body();
        }
        jsCode = new JSBody.JSCode(javaScriptBody.args(), javaScriptBody.body());

        ValueNode returnValue = createJSBody(method, kit, argNodes, returnStamp, jsCode, codeSupplier);

        returnValue = convertReturnValue(kit, returnKind, returnValue, returnType);

        kit.createReturn(returnValue, method.getSignature().getReturnKind());

        return kit.finalizeGraph();
    }

    private static ValueNode createJSBody(AnalysisMethod method, HostedGraphKit kit, ValueNode[] argNodes, Stamp returnStamp,
                    JSBody.JSCode jsCode, Function<CodeGenTool, String> codeSupplier) {
        boolean declaresResource = AnnotationAccess.isAnnotationPresent(method.getDeclaringClass(), JavaScriptResource.class);
        return kit.appendWithUnwind(new JSBodyWithExceptionNode(jsCode, method, argNodes, returnStamp, null, declaresResource, codeSupplier));
    }

    /**
     * Generates IR nodes to convert an argument node into a ValueNode holding an appropriate JS
     * native value.
     */
    private static ValueNode convertArgument(HostedGraphKit kit, AnalysisType argType, ValueNode argNode) {
        AnalysisMethod conversionMethod = null;

        try {
            if (!argType.isPrimitive()) {
                conversionMethod = kit.getMetaAccess().lookupJavaMethod(JavaScriptBodyConversion.class.getDeclaredMethod("convertObjectToJS", Object.class));
            } else if (argType.getJavaKind() == JavaKind.Long) {
                conversionMethod = kit.getMetaAccess().lookupJavaMethod(JavaScriptBodyConversion.class.getDeclaredMethod("longToJSNumber", long.class));
            } else if (argType.getJavaKind() == JavaKind.Boolean) {
                conversionMethod = kit.getMetaAccess().lookupJavaMethod(JavaScriptBodyConversion.class.getDeclaredMethod("toJSBool", boolean.class));
            }
        } catch (NoSuchMethodException e) {
            throw VMError.shouldNotReachHere(e);
        }

        if (conversionMethod != null) {
            return kit.createInvokeWithExceptionAndUnwind(conversionMethod, CallTargetNode.InvokeKind.Static, kit.getFrameState(), kit.bci(), argNode);
        }

        return argNode;
    }

    private static ValueNode convertReturnValue(HostedGraphKit kit, JavaKind returnKind, ValueNode returnValue, AnalysisType returnType) {
        final ValueNode javaValue;
        /*
         * For object return values, we may need to convert the value returned by JS:
         *
         * @formatter:off
         * - JS native strings must be converted to Java Strings
         * - Non-Java JS objects must be wrapped so that they can be
         *   used properly in Java code
         * - Primitive types may need to be boxed (depending on return type)
         * - Non-Java JS functions must be wrapped so that they can be
         *   properly used in Java code
         * - undefined must be converted to null
         * - Any numeric value must be converted to the proper Java numeric
         *   type (and possibly truncated)
         * @formatter:on
         *
         * The conversion is done in 3 steps.
         *
         * @formatter:off
         * 1. The JS value is converted into a Java object using JSConversion.convertObjectToJava.
         *    This method dynamically dispatches to the proper conversion based on the type of the
         *    JS value that is produced by the snippet.
         *    This ensures that the conversion rules are purely driven by dynamic JS types.
         *    The resulting object has a dynamic type D.
         * 2. The value returned from JSConversion.convertObjectToJava is checkcast
         *    depending on the return type T declaration of the JS-annotated method.
         *    This ensures that the return value corresponds to the user-specified return type,
         *    otherwise the Java semantics would be broken.
         *    All return types T that are non-boolean boxed primitives require that the dynamic type D is a java.lang.Double (implicit conversion follows).
         *    All other return types T require that the dynamic type D is a subtype of T.
         * 3. Finally, an implicit conversion is performed if the return type T <: Number.
         *    Double objects are converted using numeric conversions.
         * @formatter:on
         */

        // Step 1: Perform dynamic-type-driven JS-to-Java conversion.
        if (returnKind == JavaKind.Void) {
            // There is not dynamic conversion, type-check or implicit conversion necessary
            // when the return type is void.
            return null;
        }
        try {
            Method convertObjectToJava = JavaScriptBodyConversion.class.getDeclaredMethod("convertObjectToJavaForJavaScriptBody", Object.class);
            AnalysisMethod conversionMethod = kit.getMetaAccess().lookupJavaMethod(convertObjectToJava);
            javaValue = kit.createInvokeWithExceptionAndUnwind(conversionMethod, CallTargetNode.InvokeKind.Static, kit.getFrameState(), kit.bci(), returnValue);
        } catch (NoSuchMethodException e) {
            throw VMError.shouldNotReachHere(e);
        }

        // Step 2: Perform the static-return-type-driven check-cast.
        //
        // We know that the value returned by Step 1 is a Java object, so a check-cast is always
        // possible.
        //
        // We distinguish three cases:
        // @formatter:off
        // - the static return type is a non-boolean primitive -- typecheck against Double
        // - the static return type is a non-boolean boxed primitive -- typecheck against Double
        // - the static return type is any other object type -- subtype check against the return type
        // @formatter:on
        final AnalysisType primitiveBoolean = kit.getMetaAccess().lookupJavaType(boolean.class);
        final AnalysisType booleanType = kit.getMetaAccess().lookupJavaType(Boolean.class);
        final AnalysisType numberType = kit.getMetaAccess().lookupJavaType(Number.class);
        final AnalysisType characterType = kit.getMetaAccess().lookupJavaType(Character.class);
        final boolean isBoxedPrimitive = numberType.isAssignableFrom(returnType) || characterType.isAssignableFrom(returnType);
        final boolean isBoolean = booleanType.isAssignableFrom(returnType) || primitiveBoolean.isAssignableFrom(returnType);
        final TypeReference checkedTypeRef;
        if (returnKind.isPrimitive() || isBoxedPrimitive) {
            if (isBoolean) {
                checkedTypeRef = TypeReference.createExactTrusted(booleanType);
            } else {
                checkedTypeRef = TypeReference.createExactTrusted(kit.getMetaAccess().lookupJavaType(Double.class));
            }
        } else {
            checkedTypeRef = TypeReference.createTrusted(null, returnType);
        }
        LogicNode instanceOf = InstanceOfNode.createAllowNull(checkedTypeRef, javaValue, null, null);
        kit.startIf(instanceOf, ProfileData.BranchProbabilityData.injected(HIGH_INSTANCEOF_PROBABILITY));
        kit.elsePart();
        final AnalysisType classCastExceptionType = kit.getMetaAccess().lookupJavaType(ClassCastException.class);
        NewInstanceNode classCastExceptionNode = kit.append(new NewInstanceNode(classCastExceptionType, true));
        kit.append(new UnwindNode(classCastExceptionNode));
        kit.thenPart();
        kit.endIf();

        // Step 3: Perform the static-return-type-driven implicit conversion for certain types.
        if (!isBoolean && (returnKind.isPrimitive() || isBoxedPrimitive)) {
            // We previously checked that the dynamically converted value is a Double.
            // @formatter:off
            // 1. Unbox that Double.
            // 2. Perform implicit conversion on the primitive value, if necessary.
            // 3. Box the primitive value again, if necessary.
            // @formatter:on
            final ValueNode unboxedValue = kit.createUnboxing(javaValue, JavaKind.Double);
            final JavaKind primitiveKind;
            if (isBoxedPrimitive) {
                primitiveKind = unboxedKindFor(kit.getMetaAccess(), returnType);
            } else {
                primitiveKind = returnKind;
            }

            ValueNode implicitlyConvertedValue;
            if ((primitiveKind.isNumericInteger() || primitiveKind.isNumericFloat()) && primitiveKind != JavaKind.Double) {
                // Double does not need a conversion.
                int numBits = primitiveKind.getBitCount();
                FloatConvert op;
                if (primitiveKind == JavaKind.Float) {
                    op = FloatConvert.D2F;
                } else if (numBits == 64) {
                    op = FloatConvert.D2L;
                } else {
                    op = FloatConvert.D2I;
                }

                implicitlyConvertedValue = kit.append(new FloatConvertNode(op, unboxedValue));

                // For non-int, non-long integer types, we additionally need to add narrowing
                if (primitiveKind.isNumericInteger() && numBits < 32) {
                    implicitlyConvertedValue = kit.append(new NarrowNode(implicitlyConvertedValue, numBits));
                    implicitlyConvertedValue = kit.append(new SignExtendNode(implicitlyConvertedValue, 32));
                }
            } else {
                implicitlyConvertedValue = unboxedValue;
            }

            if (isBoxedPrimitive) {
                BoxNode boxNode = (BoxNode) kit.createBoxing(implicitlyConvertedValue, primitiveKind, returnType);
                boxNode.setHasIdentity();
                return boxNode;
            } else {
                return implicitlyConvertedValue;
            }
        } else if (isBoolean) {
            if (returnKind.isPrimitive()) {
                return kit.createUnboxing(javaValue, JavaKind.Boolean);
            } else {
                return javaValue;
            }
        } else {
            // An implicit conversion is not necessary, so we just return the (correctly
            // type-checked) object.
            return javaValue;
        }
    }

    private static JavaKind unboxedKindFor(MetaAccessProvider metaAccess, AnalysisType returnType) {
        if (metaAccess.lookupJavaType(Byte.class).isAssignableFrom(returnType)) {
            return JavaKind.Byte;
        } else if (metaAccess.lookupJavaType(Short.class).isAssignableFrom(returnType)) {
            return JavaKind.Short;
        } else if (metaAccess.lookupJavaType(Character.class).isAssignableFrom(returnType)) {
            return JavaKind.Char;
        } else if (metaAccess.lookupJavaType(Integer.class).isAssignableFrom(returnType)) {
            return JavaKind.Int;
        } else if (metaAccess.lookupJavaType(Float.class).isAssignableFrom(returnType)) {
            return JavaKind.Float;
        } else if (metaAccess.lookupJavaType(Long.class).isAssignableFrom(returnType)) {
            return JavaKind.Long;
        } else if (metaAccess.lookupJavaType(Double.class).isAssignableFrom(returnType)) {
            return JavaKind.Double;
        } else {
            return JavaKind.Illegal;
        }
    }

}
