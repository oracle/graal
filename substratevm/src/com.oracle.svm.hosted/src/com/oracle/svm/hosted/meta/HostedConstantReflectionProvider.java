/*
 * Copyright (c) 2014, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.hosted.meta;

import java.util.function.ObjIntConsumer;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.graal.meta.SharedConstantReflectionProvider;
import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.hosted.SVMHost;
import com.oracle.svm.hosted.ameta.AnalysisConstantReflectionProvider;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

@Platforms(Platform.HOSTED_ONLY.class)
public class HostedConstantReflectionProvider extends SharedConstantReflectionProvider {
    private final SVMHost hostVM;
    private final AnalysisConstantReflectionProvider aConstantReflection;
    private final HostedUniverse hUniverse;
    private final HostedMetaAccess hMetaAccess;
    private final HostedMemoryAccessProvider hMemoryAccess;

    @SuppressWarnings("this-escape")
    public HostedConstantReflectionProvider(SVMHost hostVM, AnalysisConstantReflectionProvider aConstantReflection, HostedUniverse hUniverse, HostedMetaAccess hMetaAccess) {
        this.hostVM = hostVM;
        this.aConstantReflection = aConstantReflection;
        this.hUniverse = hUniverse;
        this.hMetaAccess = hMetaAccess;
        this.hMemoryAccess = new HostedMemoryAccessProvider(hMetaAccess, this);
    }

    @Override
    public Boolean constantEquals(Constant x, Constant y) {
        /* Delegate to the AnalysisConstantReflectionProvider. */
        return aConstantReflection.constantEquals(x, y);
    }

    @Override
    public MemoryAccessProvider getMemoryAccessProvider() {
        return hMemoryAccess;
    }

    @Override
    public JavaConstant unboxPrimitive(JavaConstant source) {
        /* Delegate to the AnalysisConstantReflectionProvider. */
        return aConstantReflection.unboxPrimitive(source);
    }

    @Override
    public ResolvedJavaType asJavaType(Constant constant) {
        /* Delegate to the AnalysisConstantReflectionProvider. */
        return hUniverse.lookup(aConstantReflection.asJavaType(constant));
    }

    @Override
    public JavaConstant asJavaClass(ResolvedJavaType type) {
        return aConstantReflection.asJavaClass(((HostedType) type).wrapped);
    }

    @Override
    public Integer readArrayLength(JavaConstant array) {
        /* Delegate to the AnalysisConstantReflectionProvider. */
        return aConstantReflection.readArrayLength(array);
    }

    @Override
    public JavaConstant readArrayElement(JavaConstant array, int index) {
        /* Delegate to the AnalysisConstantReflectionProvider. */
        return aConstantReflection.readArrayElement(array, index);
    }

    @Override
    public void forEachArrayElement(JavaConstant array, ObjIntConsumer<JavaConstant> consumer) {
        /* Delegate to the AnalysisConstantReflectionProvider. */
        aConstantReflection.forEachArrayElement(array, consumer);
    }

    @Override
    public JavaConstant readFieldValue(ResolvedJavaField field, JavaConstant receiver) {
        var hField = (HostedField) field;
        assert checkHub(receiver) : "Receiver " + receiver + " of field " + hField + " read should not be java.lang.Class. Expecting to see DynamicHub here.";
        return aConstantReflection.readValue(hField.getWrapped(), receiver, true);
    }

    public AnalysisConstantReflectionProvider getWrappedConstantReflection() {
        return aConstantReflection;
    }

    @Override
    public JavaConstant forString(String value) {
        return aConstantReflection.forString(value);
    }

    @Override
    protected JavaConstant forObject(Object object) {
        return aConstantReflection.forObject(object);
    }

    private boolean checkHub(JavaConstant constant) {
        if (hMetaAccess.isInstanceOf(constant, Class.class)) {
            Object classObject = hUniverse.getSnippetReflection().asObject(Object.class, constant);
            return classObject instanceof DynamicHub;
        }
        return true;
    }
}
