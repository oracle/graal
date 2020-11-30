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
import com.oracle.svm.reflect.target.Target_java_lang_reflect_AccessibleObject;

@TargetClass(className = "java.lang.invoke.MethodHandle", onlyWith = MethodHandlesSupported.class)
final class Target_java_lang_invoke_MethodHandle {

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
                assert args.length == 1;
                Object obj = args[0];
                return ((Field) memberName.reflectAccess).get(obj);
            } else { /* Method or constructor invocation */
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
                            return method.invoke(receiver, invokeArgs);
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
