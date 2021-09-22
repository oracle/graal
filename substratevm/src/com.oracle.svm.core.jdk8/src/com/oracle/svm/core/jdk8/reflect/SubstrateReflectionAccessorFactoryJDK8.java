/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.jdk8.reflect;

// Checkstyle: allow reflection

import java.lang.reflect.Executable;

import org.graalvm.compiler.serviceprovider.JavaVersionUtil;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.nativeimage.hosted.Feature;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.reflect.SubstrateConstructorAccessor;
import com.oracle.svm.core.reflect.SubstrateMethodAccessor;
import com.oracle.svm.core.reflect.SubstrateReflectionAccessorFactory;

final class SubstrateReflectionAccessorFactoryJDK8 implements SubstrateReflectionAccessorFactory {
    @Override
    public SubstrateMethodAccessor createMethodAccessor(Executable member, CFunctionPointer invokeFunctionPointer) {
        return new SubstrateMethodAccessorJDK8(member, invokeFunctionPointer);
    }

    @Override
    public SubstrateConstructorAccessor createConstructorAccessor(Executable member, CFunctionPointer newInstanceFunctionPointer) {
        return new SubstrateConstructorAccessorJDK8(member, newInstanceFunctionPointer);
    }
}

@AutomaticFeature
final class SubstrateReflectionAccessorFactoryJDK8Feature implements Feature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return JavaVersionUtil.JAVA_SPEC == 8;
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(SubstrateReflectionAccessorFactory.class, new SubstrateReflectionAccessorFactoryJDK8());
    }
}

final class SubstrateMethodAccessorJDK8 extends SubstrateMethodAccessor implements sun.reflect.MethodAccessor {
    SubstrateMethodAccessorJDK8(Executable member, CFunctionPointer invokeFunctionPointer) {
        super(member, invokeFunctionPointer);
    }
}

final class SubstrateConstructorAccessorJDK8 extends SubstrateConstructorAccessor implements sun.reflect.ConstructorAccessor {
    SubstrateConstructorAccessorJDK8(Executable member, CFunctionPointer newInstanceFunctionPointer) {
        super(member, newInstanceFunctionPointer);
    }
}
