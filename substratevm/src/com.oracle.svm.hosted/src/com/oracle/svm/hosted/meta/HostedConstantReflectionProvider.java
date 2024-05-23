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

import static com.oracle.svm.core.util.VMError.shouldNotReachHereAtRuntime;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.hosted.ameta.AnalysisConstantReflectionProvider;
import com.oracle.svm.hosted.classinitialization.ClassInitializationSupport;

import jdk.vm.ci.meta.Constant;
import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MemoryAccessProvider;
import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaType;

@Platforms(Platform.HOSTED_ONLY.class)
public class HostedConstantReflectionProvider extends AnalysisConstantReflectionProvider {
    private final HostedUniverse hUniverse;
    private final HostedMetaAccess hMetaAccess;
    private final HostedMemoryAccessProvider hMemoryAccess;

    @SuppressWarnings("this-escape")
    public HostedConstantReflectionProvider(HostedUniverse hUniverse, HostedMetaAccess hMetaAccess, ClassInitializationSupport classInitializationSupport) {
        super(hUniverse.getBigBang().getUniverse(), hUniverse.getBigBang().getMetaAccess(), classInitializationSupport);
        this.hUniverse = hUniverse;
        this.hMetaAccess = hMetaAccess;
        this.hMemoryAccess = new HostedMemoryAccessProvider(hMetaAccess, this);
    }

    @Override
    public MemoryAccessProvider getMemoryAccessProvider() {
        return hMemoryAccess;
    }

    @Override
    public ResolvedJavaType asJavaType(Constant constant) {
        return hUniverse.lookup(super.asJavaType(constant));
    }

    @Override
    public JavaConstant asJavaClass(ResolvedJavaType type) {
        return super.asJavaClass(((HostedType) type).wrapped);
    }

    @Override
    public JavaConstant readFieldValue(ResolvedJavaField field, JavaConstant receiver) {
        var hField = (HostedField) field;
        assert checkHub(receiver) : "Receiver " + receiver + " of field " + hField + " read should not be java.lang.Class. Expecting to see DynamicHub here.";
        return super.readValue(hField.getWrapped(), receiver, true);
    }

    private boolean checkHub(JavaConstant constant) {
        if (hMetaAccess.isInstanceOf(constant, Class.class)) {
            Object classObject = hUniverse.getSnippetReflection().asObject(Object.class, constant);
            return classObject instanceof DynamicHub;
        }
        return true;
    }

    @Override
    public MethodHandleAccessProvider getMethodHandleAccess() {
        throw shouldNotReachHereAtRuntime(); // ExcludeFromJacocoGeneratedReport
    }
}
