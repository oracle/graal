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
package com.oracle.svm.methodhandles;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
// Checkstyle: stop
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
// Checkstyle: resume
import java.util.Arrays;

import com.oracle.svm.core.SubstrateUtil;
import com.oracle.svm.core.annotate.Alias;
import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import com.oracle.svm.reflect.helpers.InvokeSpecialReflectionProxy;
import com.oracle.svm.reflect.target.Target_java_lang_reflect_AccessibleObject;
import com.oracle.svm.reflect.target.Target_java_lang_reflect_Method;
import com.oracle.svm.reflect.target.Target_jdk_internal_reflect_MethodAccessor;

// Checkstyle: stop
import sun.invoke.util.ValueConversions;
import sun.invoke.util.Wrapper;
// Checkstyle: resume

@TargetClass(className = "java.lang.invoke.MethodHandle", onlyWith = MethodHandlesSupported.class)
final class Target_java_lang_invoke_MethodHandle {

    @Alias private MethodType type;

    @Alias
    native Target_java_lang_invoke_MemberName internalMemberName();

    @Alias
    native Target_java_lang_invoke_LambdaForm internalForm();

    /* All MethodHandle.invoke* methods funnel through here. */
    @Substitute(polymorphicSignature = true)
    Object invokeBasic(Object... args) throws Throwable {
        Target_java_lang_invoke_MemberName memberName = internalMemberName();
        if (memberName != null) { /* Direct method handle */
            /*
             * The method handle may have been resolved at build time. If that is the case, the
             * SVM-specific information needed to perform the invoke is not stored in the handle
             * yet, so we perform the resolution again.
             */
            if (memberName.reflectAccess == null && memberName.intrinsic == null) {
                Target_java_lang_invoke_MethodHandleNatives.resolve(memberName, null, false);
            }

            if (memberName.intrinsic != null) { /* Intrinsic call */
                assert memberName.reflectAccess == null;
                return memberName.intrinsic.execute(args);
            } else if (memberName.isField()) { /* Field access */
                Field field = (Field) memberName.reflectAccess;
                if (Modifier.isStatic(field.getModifiers())) {
                    assert args == null || args.length == 0;
                    return field.get(null);
                } else {
                    assert args.length == 1;
                    Object receiver = args[0];
                    return field.get(receiver);
                }
            } else { /* Method or constructor invocation */
                assert args.length == type.parameterCount();
                for (int i = 0; i < args.length; ++i) {
                    Class<?> expectedParamType = type.parameterType(i);
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
                        return ((Constructor<?>) memberName.reflectAccess).newInstance(args);
                    } else {
                        Method method = (Method) memberName.reflectAccess;
                        if (Modifier.isStatic(method.getModifiers())) {
                            return method.invoke(null, args);
                        } else {
                            Object receiver = args[0];
                            Object[] invokeArgs = Arrays.copyOfRange(args, 1, args.length);
                            if (memberName.getReferenceKind() == Target_java_lang_invoke_MethodHandleNatives_Constants.REF_invokeSpecial) {
                                Target_jdk_internal_reflect_MethodAccessor accessor = SubstrateUtil.cast(method, Target_java_lang_reflect_Method.class).acquireMethodAccessor();
                                return SubstrateUtil.cast(accessor, InvokeSpecialReflectionProxy.class).invokeSpecial(receiver, invokeArgs);
                            } else {
                                return method.invoke(receiver, invokeArgs);
                            }
                        }
                    }
                } finally {
                    executable.override = oldOverride;
                }
            }
        } else { /* Interpretation mode */
            Target_java_lang_invoke_LambdaForm form = internalForm();
            Object[] interpreterArguments = new Object[args.length + 1];
            interpreterArguments[0] = this;
            System.arraycopy(args, 0, interpreterArguments, 1, args.length);
            return form.interpretWithArguments(interpreterArguments);
        }
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
}

@TargetClass(className = "java.lang.invoke.MethodHandleImpl", onlyWith = MethodHandlesSupported.class)
final class Target_java_lang_invoke_MethodHandleImpl {
}

@TargetClass(className = "java.lang.invoke.MethodHandleImpl", innerClass = "ArrayAccessor", onlyWith = MethodHandlesSupported.class)
final class Target_java_lang_invoke_MethodHandleImpl_ArrayAccessor {
}
