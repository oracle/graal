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
package com.oracle.svm.core.jdk11.reflect;

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

final class SubstrateReflectionAccessorFactoryJDK11 implements SubstrateReflectionAccessorFactory {
    @Override
    public SubstrateMethodAccessor createMethodAccessor(Executable member, CFunctionPointer invokeFunctionPointer) {
        return new SubstrateMethodAccessorJDK11(member, invokeFunctionPointer);
    }

    @Override
    public SubstrateConstructorAccessor createConstructorAccessor(Executable member, CFunctionPointer newInstanceFunctionPointer) {
        return new SubstrateConstructorAccessorJDK11(member, newInstanceFunctionPointer);
    }
}

@AutomaticFeature
final class SubstrateReflectionAccessorFactoryJDK11Feature implements Feature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return JavaVersionUtil.JAVA_SPEC >= 11;
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        ImageSingletons.add(SubstrateReflectionAccessorFactory.class, new SubstrateReflectionAccessorFactoryJDK11());
    }
}

final class SubstrateMethodAccessorJDK11 extends SubstrateMethodAccessor implements jdk.internal.reflect.MethodAccessor {
    SubstrateMethodAccessorJDK11(Executable member, CFunctionPointer invokeFunctionPointer) {
        super(member, invokeFunctionPointer);
    }
}

final class SubstrateConstructorAccessorJDK11 extends SubstrateConstructorAccessor implements jdk.internal.reflect.ConstructorAccessor {
    SubstrateConstructorAccessorJDK11(Executable member, CFunctionPointer newInstanceFunctionPointer) {
        super(member, newInstanceFunctionPointer);
    }
}
