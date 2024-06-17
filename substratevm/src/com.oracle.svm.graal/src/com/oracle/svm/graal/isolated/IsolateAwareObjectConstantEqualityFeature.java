/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.isolated;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.c.function.CEntryPoint;

import com.oracle.svm.core.SubstrateOptions;
import com.oracle.svm.core.feature.AutomaticallyRegisteredFeature;
import com.oracle.svm.core.feature.InternalFeature;
import com.oracle.svm.core.graal.RuntimeCompilation;
import com.oracle.svm.core.meta.DirectSubstrateObjectConstant;
import com.oracle.svm.core.meta.ObjectConstantEquality;
import com.oracle.svm.core.meta.SubstrateObjectConstant;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.Constant;

final class IsolateAwareObjectConstantEquality implements ObjectConstantEquality {
    @Override
    public boolean test(SubstrateObjectConstant x, SubstrateObjectConstant y) {
        if (x == y) {
            return true;
        } else if (x instanceof DirectSubstrateObjectConstant && y instanceof DirectSubstrateObjectConstant) {
            return ((DirectSubstrateObjectConstant) x).getObject() == ((DirectSubstrateObjectConstant) y).getObject();
        } else if (x instanceof IsolatedObjectConstant) {
            return compareIsolatedConstant((IsolatedObjectConstant) x, y);
        } else if (y instanceof IsolatedObjectConstant) {
            return compareIsolatedConstant((IsolatedObjectConstant) y, x);
        }
        throw VMError.shouldNotReachHere("Unknown object constants: " + x + " and " + y);
    }

    private static boolean compareIsolatedConstant(IsolatedObjectConstant a, Constant b) {
        ClientHandle<?> u = a.getHandle();
        if (b instanceof IsolatedObjectConstant) {
            ClientHandle<?> v = ((IsolatedObjectConstant) b).getHandle();
            return u.equal(v) || isolatedConstantHandleTargetsEqual(IsolatedCompileContext.get().getClient(), u, v);
        } else if (b instanceof DirectSubstrateObjectConstant) {
            ImageHeapRef<?> v = ImageHeapObjects.ref(((DirectSubstrateObjectConstant) b).getObject());
            return isolatedHandleTargetEqualImageObject(IsolatedCompileContext.get().getClient(), u, v);
        }
        throw VMError.shouldNotReachHere("Unknown object constant: " + b);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    static boolean isolatedConstantHandleTargetsEqual(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<?> x, ClientHandle<?> y) {
        return IsolatedCompileClient.get().unhand(x) == IsolatedCompileClient.get().unhand(y);
    }

    @CEntryPoint(include = CEntryPoint.NotIncludedAutomatically.class, publishAs = CEntryPoint.Publish.NotPublished)
    private static boolean isolatedHandleTargetEqualImageObject(@SuppressWarnings("unused") ClientIsolateThread client, ClientHandle<?> x, ImageHeapRef<?> y) {
        return IsolatedCompileClient.get().unhand(x) == ImageHeapObjects.deref(y);
    }
}

@AutomaticallyRegisteredFeature
final class IsolateAwareObjectConstantEqualityFeature implements InternalFeature {
    @Override
    public boolean isInConfiguration(IsInConfigurationAccess access) {
        return SubstrateOptions.supportCompileInIsolates();
    }

    @Override
    public void afterRegistration(AfterRegistrationAccess access) {
        if (RuntimeCompilation.isEnabled()) {
            ImageSingletons.add(ObjectConstantEquality.class, new IsolateAwareObjectConstantEquality());
        }
    }
}
