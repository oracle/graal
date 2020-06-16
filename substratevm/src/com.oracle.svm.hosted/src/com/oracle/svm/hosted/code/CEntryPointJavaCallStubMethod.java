/*
 * Copyright (c) 2018, 2018, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.code;

import java.lang.annotation.Annotation;
import java.util.List;

import org.graalvm.compiler.core.common.type.StampPair;
import org.graalvm.compiler.nodes.ConstantNode;
import org.graalvm.compiler.nodes.ValueNode;
import org.graalvm.compiler.nodes.java.LoadFieldNode;
import org.graalvm.nativeimage.c.function.CEntryPoint;
import org.graalvm.nativeimage.c.function.CFunctionPointer;

import com.oracle.graal.pointsto.meta.HostedProviders;
import com.oracle.svm.core.c.BoxedRelocatedPointer;
import com.oracle.svm.core.thread.VMThreads.StatusSupport;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.hosted.phases.HostedGraphKit;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Call stub for invoking {@link CEntryPoint} methods via a Java-to-native call to the method's
 * {@linkplain CEntryPointCallStubMethod native-to-Java stub}.
 */
public class CEntryPointJavaCallStubMethod extends CCallStubMethod {
    private final String name;
    private final ResolvedJavaType declaringClass;
    private final CFunctionPointer target;

    CEntryPointJavaCallStubMethod(ResolvedJavaMethod original, String name, ResolvedJavaType declaringClass, CFunctionPointer target) {
        super(original, StatusSupport.STATUS_IN_NATIVE);
        this.name = name;
        this.declaringClass = declaringClass;
        this.target = target;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public ResolvedJavaType getDeclaringClass() {
        return declaringClass;
    }

    @Override
    protected String getCorrespondingAnnotationName() {
        return CEntryPoint.class.getSimpleName();
    }

    @Override
    protected ValueNode createTargetAddressNode(HostedGraphKit kit, HostedProviders providers, List<ValueNode> arguments) {
        try {
            /*
             * We currently cannot handle {@link MethodPointer} as a constant in the code, so we use
             * an indirection with a non-final field load from an object of BoxedRelocatedPointer.
             */
            BoxedRelocatedPointer box = new BoxedRelocatedPointer(target);
            ConstantNode boxNode = kit.createObject(box);
            ResolvedJavaField field = providers.getMetaAccess().lookupJavaField(BoxedRelocatedPointer.class.getDeclaredField("pointer"));
            return kit.append(LoadFieldNode.createOverrideStamp(StampPair.createSingle(kit.wordStamp((ResolvedJavaType) field.getType())), boxNode, field));
        } catch (NoSuchFieldException e) {
            throw VMError.shouldNotReachHere(e);
        }
    }

    @Override
    public Annotation[] getAnnotations() {
        return getDeclaredAnnotations();
    }

    @Override
    public Annotation[] getDeclaredAnnotations() {
        return new Annotation[0];
    }

    @Override
    public <T extends Annotation> T getAnnotation(Class<T> annotationClass) {
        return null;
    }
}
