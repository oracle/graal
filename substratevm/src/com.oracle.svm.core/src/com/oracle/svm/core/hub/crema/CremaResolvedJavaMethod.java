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
package com.oracle.svm.core.hub.crema;

import com.oracle.svm.core.graal.code.PreparedSignature;
import com.oracle.svm.core.jni.access.JNINativeLinkage;
import com.oracle.svm.core.jni.headers.JNIMethodId;
import com.oracle.svm.espresso.classfile.attributes.MethodParametersAttribute;

import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public interface CremaResolvedJavaMethod extends ResolvedJavaMethod {

    /**
     * Returns all exceptions as declared by this resolved Crema method as a JavaType array.
     *
     * @return the declared exceptions
     */
    JavaType[] getDeclaredExceptions();

    /**
     * Retrieves the raw annotation bytes for this method.
     *
     * @return the raw annotations as a byte array
     */
    byte[] getRawAnnotations();

    /**
     * Retrieves the raw parameter annotation bytes for this method.
     *
     * @return the raw parameter annotations as a byte array
     */
    byte[] getRawParameterAnnotations();

    /**
     * Retrieves the raw annotation default bytes for this method.
     *
     * @return the raw annotation default as a byte array
     */
    byte[] getRawAnnotationDefault();

    /**
     * Retrieves the {@code MethodParameters} attribute for this method.
     *
     * @return the {@code MethodParameters} attribute, or {@code null} if the attribute is not
     *         present
     */
    MethodParametersAttribute getParametersAttribute();

    /**
     * Retrieves the raw type annotation bytes for this method.
     *
     * @return the raw type annotations as a byte array
     */
    byte[] getRawTypeAnnotations();

    /**
     * Returns the accessor object, either a {@link com.oracle.svm.core.reflect.CremaMethodAccessor}
     * or {@link com.oracle.svm.core.reflect.CremaConstructorAccessor} which can be used to invoke
     * the resolved Crema method or create a new instance from the resolved Crema constructor.
     *
     * @return the accessor
     */
    Object getAccessor(Class<?> receiverType, Class<?>[] parameterTypes);

    /**
     * Gets or creates the runtime {@code jmethodID} for this method.
     */
    JNIMethodId getOrCreateJNIMethodId();

    /**
     * Returns the generic signature of this method.
     *
     * @return the generic signature
     */
    String getGenericSignature();

    /**
     * Returns the precomputed JNI signature consumed by interpreter stubs.
     */
    PreparedSignature getJNIDowncallPreparedSignature();

    /**
     * Returns the JNI linkage used when a native method of a runtime-loaded Crema class is linked
     * lazily, e.g. via RegisterNatives.
     */
    JNINativeLinkage getJNINativeLinkage();
}
