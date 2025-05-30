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
package com.oracle.svm.agent;

import static com.oracle.svm.core.jni.JNIObjectHandles.nullHandle;
import static com.oracle.svm.jvmtiagentbase.Support.clearException;
import static com.oracle.svm.jvmtiagentbase.Support.jniFunctions;

import java.util.ArrayList;
import java.util.List;

import com.oracle.svm.agent.tracing.core.Tracer;
import com.oracle.svm.core.jni.headers.JNIEnvironment;
import com.oracle.svm.core.jni.headers.JNIFieldId;
import com.oracle.svm.core.jni.headers.JNIObjectHandle;
import com.oracle.svm.jvmtiagentbase.JNIHandleSet;
import com.oracle.svm.jvmtiagentbase.Support;

/**
 * A utility class that contains helper methods for FFM API tracing.
 */
public final class ForeignUtil extends JNIHandleSet {

    public ForeignUtil(JNIEnvironment env) {
        super(env);
    }

    /**
     * Takes an object of type {@code Optional<MemoryLayout>} and returns the appropriate memory
     * layout string. Return type {@code void} is represented by {@code Optional.empty()} (because
     * there is no MemoryLayout for it) and will result in string {@code "void"}. Otherwise,
     * {@link #getLayoutString} will be applied on the element.
     */
    public static String getOptionalReturnLayoutString(JNIEnvironment env, NativeImageAgentJNIHandleSet handles, JNIObjectHandle optionalReturnLayout) {
        // Optional<MemoryLayout>
        assert isOptional(env, handles, optionalReturnLayout);
        boolean isEmpty = callOptionalIsEmpty(env, handles, optionalReturnLayout);
        if (clearException(env)) {
            return Tracer.UNKNOWN_VALUE;
        }
        if (isEmpty) {
            return "void";
        }
        // optionalReturnLayout.get()
        JNIObjectHandle returnLayout = Support.callObjectMethod(env, optionalReturnLayout, handles.getJavaUtilOptionalGet(env));
        if (clearException(env)) {
            return Tracer.UNKNOWN_VALUE;
        }
        return getLayoutString(env, handles, returnLayout);
    }

    /**
     * Recursively visits the provided memory layout and generates a layout descriptor string
     * parsable by {@code com.oracle.svm.hosted.foreign.MemoryLayoutParser}.
     */
    public static String getLayoutString(JNIEnvironment env, NativeImageAgentJNIHandleSet handles, JNIObjectHandle layout) {
        try {
            return visitMemoryLayout(env, handles, layout);
        } catch (MemoryLayoutTraverseException e) {
            return Tracer.UNKNOWN_VALUE;
        }
    }

    private static String visitMemoryLayout(JNIEnvironment env, NativeImageAgentJNIHandleSet handles, JNIObjectHandle layout) throws MemoryLayoutTraverseException {
        String layoutString;
        if (isValueLayout(env, handles, layout)) {
            layoutString = visitValueLayout(env, handles, layout);
        } else if (isStructLayout(env, handles, layout)) {
            layoutString = visitStructLayout(env, handles, layout);
        } else if (isUnionLayout(env, handles, layout)) {
            layoutString = visitUnionLayout(env, handles, layout);
        } else if (isPaddingLayout(env, handles, layout)) {
            layoutString = visitPaddingLayout(env, handles, layout);
        } else if (isSequenceLayout(env, handles, layout)) {
            layoutString = visitSequenceLayout(env, handles, layout);
        } else {
            throw MemoryLayoutTraverseException.INSTANCE;
        }
        return decorateWithAlignmentIfNecessary(env, handles, layout, layoutString);
    }

    private static String visitStructLayout(JNIEnvironment env, NativeImageAgentJNIHandleSet handles, JNIObjectHandle layout) throws MemoryLayoutTraverseException {
        String[] members = getMemberLayouts(env, handles, layout);
        return "struct(" + String.join(",", members) + ")";
    }

    private static String visitUnionLayout(JNIEnvironment env, NativeImageAgentJNIHandleSet handles, JNIObjectHandle layout) throws MemoryLayoutTraverseException {
        String[] members = getMemberLayouts(env, handles, layout);
        return "union(" + String.join(",", members) + ")";
    }

    private static String[] getMemberLayouts(JNIEnvironment env, NativeImageAgentJNIHandleSet handles, JNIObjectHandle layout) throws MemoryLayoutTraverseException {
        // returns java.util.List
        JNIObjectHandle memberLayoutsList = Support.callObjectMethod(env, layout, handles.getJavaLangForeignGroupLayoutMemberLayouts(env));
        if (clearException(env)) {
            throw MemoryLayoutTraverseException.INSTANCE;
        }
        // convert list to Object[]
        JNIObjectHandle memberLayoutsArray = callListToArray(env, handles, memberLayoutsList);
        if (clearException(env)) {
            throw MemoryLayoutTraverseException.INSTANCE;
        }
        // visit each element of the array
        List<String> list = mapArrayElements(env, handles, memberLayoutsArray, MEMORY_LAYOUT_TO_STRING);
        if (list == null) {
            throw MemoryLayoutTraverseException.INSTANCE;
        }
        return list.toArray(new String[0]);
    }

    private static final ArrayElementFunction<MemoryLayoutTraverseException> MEMORY_LAYOUT_TO_STRING = new ArrayElementFunction<>() {
        @Override
        public String apply(JNIEnvironment env, NativeImageAgentJNIHandleSet handles, JNIObjectHandle element) throws MemoryLayoutTraverseException {
            return visitMemoryLayout(env, handles, element);
        }

        @Override
        public String handleException(JNIEnvironment env, NativeImageAgentJNIHandleSet handles, JNIObjectHandle arrayHandle, int index) throws MemoryLayoutTraverseException {
            throw MemoryLayoutTraverseException.INSTANCE;
        }
    };

    private static String visitSequenceLayout(JNIEnvironment env, NativeImageAgentJNIHandleSet handles, JNIObjectHandle layout) throws MemoryLayoutTraverseException {
        long elementCount = Support.callLongMethod(env, layout, handles.getJavaLangForeignSequenceLayoutElementCount(env));
        if (clearException(env)) {
            throw MemoryLayoutTraverseException.INSTANCE;
        }
        JNIObjectHandle elementLayout = Support.callObjectMethod(env, layout, handles.getJavaLangForeignSequenceLayoutElementLayout(env));
        if (clearException(env)) {
            throw MemoryLayoutTraverseException.INSTANCE;
        }
        return "sequence(" + elementCount + ", " + visitMemoryLayout(env, handles, elementLayout) + ")";
    }

    private static String visitValueLayout(JNIEnvironment env, NativeImageAgentJNIHandleSet handles, JNIObjectHandle layout) throws MemoryLayoutTraverseException {
        JNIObjectHandle carrier = Support.callObjectMethod(env, layout, handles.getJavaLangForeignValueLayoutCarrier(env));
        if (clearException(env)) {
            throw MemoryLayoutTraverseException.INSTANCE;
        }
        if (jniFunctions().getIsSameObject().invoke(env, handles.getJavaLangForeignMemorySegment(env), carrier)) {
            return "void*";
        }
        String classNameOrNull = Support.getClassNameOrNull(env, carrier);
        if (classNameOrNull == null) {
            throw MemoryLayoutTraverseException.INSTANCE;
        }
        assert isValidCarrierClass(classNameOrNull);
        return "j" + classNameOrNull;
    }

    private static boolean isValidCarrierClass(String carrierClassName) {
        return switch (carrierClassName) {
            case "boolean", "byte", "short", "char", "int", "long", "float", "double" -> true;
            default -> false;
        };
    }

    private static String visitPaddingLayout(JNIEnvironment env, NativeImageAgentJNIHandleSet handles, JNIObjectHandle layout) throws MemoryLayoutTraverseException {
        long byteSize = Support.callLongMethod(env, layout, handles.getJavaLangForeignMemoryLayoutByteSize(env));
        if (clearException(env)) {
            throw MemoryLayoutTraverseException.INSTANCE;
        }
        return "padding(" + byteSize + ")";
    }

    private static String decorateWithAlignmentIfNecessary(JNIEnvironment env, NativeImageAgentJNIHandleSet handles, JNIObjectHandle layout, String layoutString) throws MemoryLayoutTraverseException {
        if (!hasNaturalAlignment(env, handles, layout)) {
            long byteAlignment = Support.callLongMethod(env, layout, handles.getJdkInternalForeignLayoutAbstractLayoutByteAlignment(env));
            if (clearException(env)) {
                throw MemoryLayoutTraverseException.INSTANCE;
            }
            return "align(" + byteAlignment + ", " + layoutString + ")";
        }
        return layoutString;
    }

    private static boolean isPaddingLayout(JNIEnvironment env, NativeImageAgentJNIHandleSet handles, JNIObjectHandle layout) {
        return jniFunctions().getIsInstanceOf().invoke(env, layout, handles.getJavaLangForeignPaddingLayout(env));
    }

    private static boolean isUnionLayout(JNIEnvironment env, NativeImageAgentJNIHandleSet handles, JNIObjectHandle layout) {
        return jniFunctions().getIsInstanceOf().invoke(env, layout, handles.getJavaLangForeignUnionLayout(env));
    }

    private static boolean isStructLayout(JNIEnvironment env, NativeImageAgentJNIHandleSet handles, JNIObjectHandle layout) {
        return jniFunctions().getIsInstanceOf().invoke(env, layout, handles.getJavaLangForeignStructLayout(env));
    }

    private static boolean isSequenceLayout(JNIEnvironment env, NativeImageAgentJNIHandleSet handles, JNIObjectHandle layout) {
        return jniFunctions().getIsInstanceOf().invoke(env, layout, handles.getJavaLangForeignSequenceLayout(env));
    }

    private static boolean isValueLayout(JNIEnvironment env, NativeImageAgentJNIHandleSet handles, JNIObjectHandle layout) {
        return jniFunctions().getIsInstanceOf().invoke(env, layout, handles.getJavaLangForeignValueLayout(env));
    }

    private static boolean isOptional(JNIEnvironment env, NativeImageAgentJNIHandleSet handles, JNIObjectHandle layout) {
        return jniFunctions().getIsInstanceOf().invoke(env, layout, handles.getJavaUtilOptional(env));
    }

    private static boolean callOptionalIsEmpty(JNIEnvironment env, NativeImageAgentJNIHandleSet handles, JNIObjectHandle optionalReturnLayout) {
        return Support.callBooleanMethod(env, optionalReturnLayout, handles.getJavaUtilOptionalIsEmpty(env));
    }

    private static boolean hasNaturalAlignment(JNIEnvironment env, NativeImageAgentJNIHandleSet handles, JNIObjectHandle layout) throws MemoryLayoutTraverseException {
        boolean hasNaturalAlignment = Support.callBooleanMethod(env, layout, handles.getJdkInternalForeignLayoutAbstractLayoutHasNaturalAlignment(env));
        if (clearException(env)) {
            throw MemoryLayoutTraverseException.INSTANCE;
        }
        return hasNaturalAlignment;
    }

    static JNIObjectHandle callListToArray(JNIEnvironment env, NativeImageAgentJNIHandleSet handles, JNIObjectHandle listHandle) {
        return Support.callObjectMethod(env, listHandle, handles.getJavaUtilListToArray(env));
    }

    interface ArrayElementFunction<E extends Exception> {
        String apply(JNIEnvironment env, NativeImageAgentJNIHandleSet handles, JNIObjectHandle element) throws E;

        @SuppressWarnings("unused")
        default String handleException(JNIEnvironment env, NativeImageAgentJNIHandleSet handles, JNIObjectHandle arrayHandle, int index) throws E {
            return Tracer.UNKNOWN_VALUE;
        }
    }

    static <E extends Exception> List<String> mapArrayElements(JNIEnvironment jni, NativeImageAgentJNIHandleSet handles, JNIObjectHandle arrayHandle, ArrayElementFunction<E> op) throws E {
        int length = jniFunctions().getGetArrayLength().invoke(jni, arrayHandle);
        if (!clearException(jni) && length >= 0) {
            List<String> result = new ArrayList<>(length);
            for (int i = 0; i < length; i++) {
                String elementString;
                JNIObjectHandle element = jniFunctions().getGetObjectArrayElement().invoke(jni, arrayHandle, i);
                if (clearException(jni)) {
                    elementString = op.handleException(jni, handles, arrayHandle, i);
                } else {
                    elementString = op.apply(jni, handles, element);
                }
                result.add(elementString);
            }
            return result;
        }
        return null;
    }

    private static final ArrayElementFunction<RuntimeException> ARRAY_ELEMENT_TO_STRING = (env, handles, option) -> {
        JNIObjectHandle optionString = Support.callObjectMethod(env, option, handles.javaLangObjectToString);
        if (!clearException(env)) {
            return Support.fromJniString(env, optionString);
        }
        return Tracer.UNKNOWN_VALUE;
    };

    static Object getOptionsStrings(JNIEnvironment jni, NativeImageAgentJNIHandleSet handles, JNIObjectHandle options) {
        Object optionStrings = Tracer.EXPLICIT_NULL;
        if (options.notEqual(nullHandle())) {
            optionStrings = mapArrayElements(jni, handles, options, ARRAY_ELEMENT_TO_STRING);
        }
        return optionStrings;
    }

    static Object getArgumentLayoutStrings(JNIEnvironment jni, NativeImageAgentJNIHandleSet handles, JNIObjectHandle function) {
        // desc.argumentLayouts() -> List<MemoryLayout>
        JNIObjectHandle argumentLayoutsList = Support.callObjectMethod(jni, function, handles.getJavaLangForeignFunctionDescriptorArgumentLayouts(jni));
        if (clearException(jni)) {
            return Tracer.EXPLICIT_NULL;
        }
        JNIObjectHandle argumentLayoutsArray = callListToArray(jni, handles, argumentLayoutsList);
        if (clearException(jni)) {
            return Tracer.EXPLICIT_NULL;
        }
        return mapArrayElements(jni, handles, argumentLayoutsArray, ForeignUtil::getLayoutString);
    }

    static String getReturnLayoutString(JNIEnvironment jni, NativeImageAgentJNIHandleSet handles, JNIObjectHandle function) {
        // function.returnLayout() -> Optional<MemoryLayout>
        JNIObjectHandle returnLayout = Support.callObjectMethod(jni, function, handles.getJavaLangForeignFunctionDescriptorReturnLayout(jni));
        String returnLayoutString = Tracer.UNKNOWN_VALUE;
        if (!clearException(jni)) {
            returnLayoutString = getOptionalReturnLayoutString(jni, handles, returnLayout);
        }
        return returnLayoutString;
    }

    private static int javaLangInvokeMethodHandleInfoREFInvokeStatic = -1;

    private static int getJavaLangInvokeMethodHandleInfoREFInvokeStatic(JNIEnvironment env, NativeImageAgentJNIHandleSet handles) {
        if (javaLangInvokeMethodHandleInfoREFInvokeStatic == -1) {
            JNIObjectHandle javaLangInvokeMethodHandleInfo = handles.findClass(env, "java/lang/invoke/MethodHandleInfo");
            JNIFieldId fieldId = handles.getFieldId(env, javaLangInvokeMethodHandleInfo, "REF_invokeStatic", "I", true);
            javaLangInvokeMethodHandleInfoREFInvokeStatic = Support.jniFunctions().getGetStaticIntField().invoke(env, javaLangInvokeMethodHandleInfo, fieldId);
        }
        return javaLangInvokeMethodHandleInfoREFInvokeStatic;
    }

    /**
     * Extracts the qualified class name and method name from a crackable method handle and returns
     * a string in form of {@code className::methodName} (e.g.
     * {@code "Lorg/my/MyClass$InnerClass::foo"}). If extraction fails, {@link Tracer#UNKNOWN_VALUE}
     * is returned.
     * 
     * Unfortunately, we cannot just call 'toString()' on the method handle descriptor because the
     * output will only contain the simple class name. Therefore, we need to do:
     * 
     * <pre>{@code
     * Optional<MethodHandleDesc> methodHandleDescOptional = target.describeConstable();
     * if (methodHandleDescOptional.isEmpty())
     *     return Tracer.UNKNOWN_VALUE;
     * MethodHandleDesc methodHandleDesc = methodHandleDescOptional.get();
     * if (!(methodHandleDesc instanceof DirectMethodHandleDesc directMethodHandleDesc))
     *     return Tracer.UNKNOWN_VALUE;
     * if (directMethodHandleDesc.refKind() != MethodHandleInfo.REF_invokeStatic)
     *     return Tracer.UNKNOWN_VALUE;
     * return directMethodHandleDesc.owner().descriptorString() + "::" + methodHandleDesc.methodName();
     * }
     * </pre>
     */
    public static String getTargetString(JNIEnvironment env, NativeImageAgentJNIHandleSet handles, JNIObjectHandle target) {
        // Optional<MethodHandleDesc> describeConstable()
        JNIObjectHandle methodHandleDescOptional = Support.callObjectMethod(env, target, handles.getJavaLangInvokeMethodHandleDescribeConstable(env));
        if (clearException(env)) {
            return Tracer.UNKNOWN_VALUE;
        }
        boolean isEmpty = callOptionalIsEmpty(env, handles, methodHandleDescOptional);
        if (clearException(env) || isEmpty) {
            return Tracer.UNKNOWN_VALUE;
        }
        // methodHandleDescOptional.get()
        JNIObjectHandle methodHandleDesc = Support.callObjectMethod(env, methodHandleDescOptional, handles.getJavaUtilOptionalGet(env));
        if (clearException(env)) {
            return Tracer.UNKNOWN_VALUE;
        }
        // methodHandleDesc instance DirectMethodHandleDesc
        if (!jniFunctions().getIsInstanceOf().invoke(env, methodHandleDesc, handles.getJavaLangConstantDirectMethodHandleDesc(env))) {
            return Tracer.UNKNOWN_VALUE;
        }
        // methodHandleDesc.refKind() != MethodHandleInfo.REF_invokeStatic
        int refKind = Support.callIntMethod(env, methodHandleDesc, handles.getJavaLangConstantDirectMethodHandleDescRefKind(env));
        if (clearException(env) || refKind != getJavaLangInvokeMethodHandleInfoREFInvokeStatic(env, handles)) {
            return Tracer.UNKNOWN_VALUE;
        }
        // methodHandleDesc.owner() -> ClassDesc
        JNIObjectHandle owner = Support.callObjectMethod(env, methodHandleDesc, handles.getJavaLangConstantDirectMethodHandleDescOwner(env));
        if (clearException(env)) {
            return Tracer.UNKNOWN_VALUE;
        }
        // methodHandleDesc.owner().descriptorString()
        JNIObjectHandle descriptorString = Support.callObjectMethod(env, owner, handles.getJavaLangConstantClassDescDescriptorString(env));
        if (clearException(env)) {
            return Tracer.UNKNOWN_VALUE;
        }
        // methodHandleDesc.methodName()
        JNIObjectHandle methodName = Support.callObjectMethod(env, methodHandleDesc, handles.getJavaLangConstantDirectMethodHandleDescMethodName(env));
        if (clearException(env)) {
            return Tracer.UNKNOWN_VALUE;
        }
        return Support.fromJniString(env, descriptorString) + "::" + Support.fromJniString(env, methodName);
    }

    @SuppressWarnings("serial")
    private static final class MemoryLayoutTraverseException extends Exception {
        private static final MemoryLayoutTraverseException INSTANCE = new MemoryLayoutTraverseException();
    }
}
