/*
 * Copyright (c) 2020, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.methodhandles;

import static com.oracle.svm.core.util.VMError.unsupportedFeature;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;

import com.oracle.svm.core.LinkToNativeSupport;
import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Delete;
import com.oracle.svm.core.annotate.RecomputeFieldValue;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.core.classinitialization.EnsureClassInitializedNode;
import com.oracle.svm.core.invoke.MethodHandleUtils;
import com.oracle.svm.core.invoke.Target_java_lang_invoke_MemberName;
import com.oracle.svm.core.reflect.SubstrateMethodAccessor;
import com.oracle.svm.core.reflect.target.Target_java_lang_reflect_AccessibleObject;
import com.oracle.svm.core.reflect.target.Target_java_lang_reflect_Method;
import com.oracle.svm.core.reflect.target.Target_jdk_internal_reflect_MethodAccessor;
import com.oracle.svm.core.util.VMError;

import sun.invoke.util.ValueConversions;
import sun.invoke.util.Wrapper;

@TargetClass(className = "java.lang.invoke.MethodHandle")
final class Target_java_lang_invoke_MethodHandle {

    /**
     * A simple last-usage cache. Need to reset because it could cache a method handle for a random
     * type, e.g., a HotSpot or hosted type.
     */
    @Alias @RecomputeFieldValue(kind = RecomputeFieldValue.Kind.Reset) //
    private MethodHandle asTypeCache;

    @Alias @RecomputeFieldValue(isFinal = true, kind = RecomputeFieldValue.Kind.None) //
    MethodType type;

    @Alias
    native Target_java_lang_invoke_MemberName internalMemberName();

    @Alias
    native Target_java_lang_invoke_LambdaForm internalForm();

    /* All MethodHandle.invoke* methods funnel through here. */
    @Substitute(polymorphicSignature = true)
    Object invokeBasic(Object... args) throws Throwable {
        Target_java_lang_invoke_MemberName memberName = internalMemberName();
        Object ret;
        if (memberName != null) {
            /* Method handles associated with a member, typically direct method handles. */
            boolean delegates = Target_java_lang_invoke_DelegatingMethodHandle.class.isInstance(this);
            if (delegates && Util_java_lang_invoke_MethodHandleNatives.resolve(memberName, null, true) == null) {
                /*
                 * Method handles can get associated with a member that we cannot resolve and use
                 * directly, but interpreting the target handle's lambda form can still succeed. For
                 * example, VarHandles create MethodHandleImpl.WrappedMember method handle objects
                 * that are associated with universal methods such as VarHandle.getVolatile() which
                 * are unsuitable for reflection because of their signature polymorphism, but their
                 * lambda forms call implementation methods in VarHandle subclasses that we can use.
                 */
                var delegating = SubstrateUtil.cast(this, Target_java_lang_invoke_DelegatingMethodHandle.class);
                return delegating.getTarget().invokeBasic(args);
            }
            ret = Util_java_lang_invoke_MethodHandle.invokeInternal(memberName, type, args);
        } else {
            /* Interpretation mode */
            Target_java_lang_invoke_LambdaForm form = internalForm();
            Object[] interpreterArguments = new Object[args.length + 1];
            interpreterArguments[0] = this;
            System.arraycopy(args, 0, interpreterArguments, 1, args.length);
            ret = form.interpretWithArguments(interpreterArguments);
        }
        return MethodHandleUtils.cast(ret, type.returnType());
    }

    @Substitute(polymorphicSignature = true)
    Object invoke(Object... args) throws Throwable {
        MethodHandle self = SubstrateUtil.cast(this, MethodHandle.class);
        return self.asType(self.type()).invokeExact(args);
    }

    @Substitute(polymorphicSignature = true)
    Object invokeExact(Object... args) throws Throwable {
        return invokeBasic(args);
    }

    @Substitute(polymorphicSignature = true)
    static Object linkToVirtual(Object... args) throws Throwable {
        return Util_java_lang_invoke_MethodHandle.linkTo(true, args);
    }

    @Substitute(polymorphicSignature = true)
    static Object linkToStatic(Object... args) throws Throwable {
        return Util_java_lang_invoke_MethodHandle.linkTo(false, args);
    }

    @Substitute(polymorphicSignature = true)
    static Object linkToInterface(Object... args) throws Throwable {
        return Util_java_lang_invoke_MethodHandle.linkTo(true, args);
    }

    @Substitute(polymorphicSignature = true)
    static Object linkToSpecial(Object... args) throws Throwable {
        return Util_java_lang_invoke_MethodHandle.linkTo(true, args);
    }

    @Substitute(polymorphicSignature = true)
    static Object linkToNative(Object... args) throws Throwable {
        if (LinkToNativeSupport.isAvailable()) {
            return LinkToNativeSupport.singleton().linkToNative(args);
        } else {
            throw unsupportedFeature("The foreign downcalls feature is not available. Please make sure that preview features are enabled with '--enable-preview'.");
        }
    }

    @Substitute
    void maybeCustomize() {
        /*
         * JDK 8 update 60 added an additional customization possibility for method handles. For all
         * use cases that we care about, that seems to be unnecessary, so we can just do nothing.
         */
    }

    @Delete
    native void customize();
}

final class Util_java_lang_invoke_MethodHandle {
    static Object linkTo(boolean hasReceiver, Object... args) throws Throwable {
        assert args.length > 0;
        Target_java_lang_invoke_MemberName memberName = (Target_java_lang_invoke_MemberName) args[args.length - 1];
        MethodType methodType = memberName.getMethodType();
        if (hasReceiver) {
            methodType = methodType.insertParameterTypes(0, memberName.getDeclaringClass());
        }
        return MethodHandleUtils.cast(invokeInternal(memberName, methodType, Arrays.copyOf(args, args.length - 1)), methodType.returnType());
    }

    static Object invokeInternal(Target_java_lang_invoke_MemberName memberName, MethodType methodType, Object... args) throws Throwable {
        /*
         * The method handle may have been resolved at build time. If that is the case, the
         * SVM-specific information needed to perform the invoke is not stored in the handle yet, so
         * we perform the resolution again.
         */
        if (memberName.reflectAccess == null && memberName.intrinsic == null) {
            Util_java_lang_invoke_MethodHandleNatives.resolve(memberName, null, false);
        }

        if (memberName.intrinsic != null) { /* Intrinsic call */
            assert memberName.reflectAccess == null;
            return memberName.intrinsic.execute(args);
        } else if (memberName.isField()) { /* Field access */
            Target_java_lang_reflect_AccessibleObject executable = SubstrateUtil.cast(memberName.reflectAccess, Target_java_lang_reflect_AccessibleObject.class);

            /* Access control was already performed by the JDK code calling invokeBasic */
            boolean oldOverride = executable.override;
            executable.override = true;
            try {
                Field field = (Field) memberName.reflectAccess;
                byte refKind = memberName.getReferenceKind();
                if (Modifier.isStatic(field.getModifiers())) {
                    if (refKind == Target_java_lang_invoke_MethodHandleNatives_Constants.REF_getStatic) {
                        assert (args == null || args.length == 0) && field.canAccess(null);
                        return field.get(null);
                    } else if (refKind == Target_java_lang_invoke_MethodHandleNatives_Constants.REF_putStatic) {
                        assert args.length == 1 && field.canAccess(null);
                        Object value = args[0];
                        field.set(null, value);
                        return null;
                    } else {
                        throw VMError.shouldNotReachHere("Wrong reference kind for static field access: " + memberName.getReferenceKind());
                    }
                } else {
                    if (refKind == Target_java_lang_invoke_MethodHandleNatives_Constants.REF_getField) {
                        assert args.length == 1 && field.canAccess(args[0]);
                        Object receiver = args[0];
                        return field.get(receiver);
                    } else if (refKind == Target_java_lang_invoke_MethodHandleNatives_Constants.REF_putField) {
                        assert args.length == 2 && field.canAccess(args[0]);
                        Object receiver = args[0];
                        Object value = args[1];
                        field.set(receiver, value);
                        return null;
                    } else {
                        throw VMError.shouldNotReachHere("Wrong reference kind for instance field access: " + memberName.getReferenceKind());
                    }
                }
            } finally {
                executable.override = oldOverride;
            }
        } else { /* Method or constructor invocation */
            assert args.length == methodType.parameterCount();
            for (int i = 0; i < args.length; ++i) {
                Class<?> expectedParamType = methodType.parameterType(i);
                if (expectedParamType.isPrimitive()) {
                    Wrapper destWrapper = Wrapper.forPrimitiveType(expectedParamType);
                    Wrapper srcWrapper = Wrapper.forWrapperType(args[i].getClass());
                    if (destWrapper != srcWrapper) {
                        /* We can't rely on automatic casting for the argument */
                        Target_java_lang_invoke_MethodHandle typeConverter = SubstrateUtil.cast(ValueConversions.convertPrimitive(srcWrapper, destWrapper),
                                        Target_java_lang_invoke_MethodHandle.class);
                        args[i] = typeConverter.invokeBasic(args[i]);
                    }
                }
            }

            Target_java_lang_reflect_AccessibleObject executable = SubstrateUtil.cast(memberName.reflectAccess, Target_java_lang_reflect_AccessibleObject.class);

            /* Access control was already performed by the JDK code calling invokeBasic */
            boolean oldOverride = executable.override;
            executable.override = true;
            try {
                if (memberName.isConstructor()) {
                    Constructor<?> constructor = (Constructor<?>) memberName.reflectAccess;
                    assert constructor.canAccess(null);
                    return constructor.newInstance(args);
                } else {
                    Method method = (Method) memberName.reflectAccess;
                    if (Modifier.isStatic(method.getModifiers())) {
                        assert method.canAccess(null);
                        return method.invoke(null, args);
                    } else {
                        assert method.canAccess(args[0]);
                        Object receiver = args[0];
                        Object[] invokeArgs = Arrays.copyOfRange(args, 1, args.length);
                        if (memberName.getReferenceKind() == Target_java_lang_invoke_MethodHandleNatives_Constants.REF_invokeSpecial) {
                            Target_jdk_internal_reflect_MethodAccessor accessor = SubstrateUtil.cast(method, Target_java_lang_reflect_Method.class).acquireMethodAccessor();
                            return SubstrateUtil.cast(accessor, SubstrateMethodAccessor.class).invokeSpecial(receiver, invokeArgs);
                        } else {
                            return method.invoke(receiver, invokeArgs);
                        }
                    }
                }
            } catch (InvocationTargetException e) {
                /* Exceptions are thrown unchanged from method handles */
                throw e.getCause();
            } finally {
                executable.override = oldOverride;
            }
        }
    }
}

@TargetClass(className = "java.lang.invoke.DirectMethodHandle")
final class Target_java_lang_invoke_DirectMethodHandle {
    @Alias @RecomputeFieldValue(isFinal = true, kind = RecomputeFieldValue.Kind.None) //
    Target_java_lang_invoke_MemberName member;

    @Substitute
    void ensureInitialized() {
        // This method is also intrinsified to avoid initialization altogether whenever possible.
        EnsureClassInitializedNode.ensureClassInitialized(member.getDeclaringClass());
    }
}

@TargetClass(className = "java.lang.invoke.DelegatingMethodHandle")
final class Target_java_lang_invoke_DelegatingMethodHandle {
    @Alias
    native Target_java_lang_invoke_MethodHandle getTarget();
}

@TargetClass(className = "java.lang.invoke.MethodHandleImpl")
final class Target_java_lang_invoke_MethodHandleImpl {
}

@TargetClass(className = "java.lang.invoke.MethodHandleImpl", innerClass = "ArrayAccessor")
final class Target_java_lang_invoke_MethodHandleImpl_ArrayAccessor {
}
