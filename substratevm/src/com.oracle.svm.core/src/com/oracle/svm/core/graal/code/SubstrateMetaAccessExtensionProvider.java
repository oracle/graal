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

package com.oracle.svm.core.graal.code;

import org.graalvm.compiler.core.common.spi.MetaAccessExtensionProvider;

import com.oracle.svm.core.annotate.Uninterruptible;
import com.oracle.svm.core.meta.SharedMethod;
import com.oracle.svm.core.meta.SharedType;

import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class SubstrateMetaAccessExtensionProvider implements MetaAccessExtensionProvider {

    @Override
    public JavaKind getStorageKind(JavaType type) {
        return ((SharedType) type).getStorageKind();
    }

    @Override
    public boolean canConstantFoldDynamicAllocation(ResolvedJavaType type) {
        return type != null && ((SharedType) type).getHub().isInstantiated();
    }

    @Override
    public boolean isGuaranteedSafepoint(ResolvedJavaMethod method, boolean isDirect) {
        if (method == null) {
            /*
             * Don't know the method target, so cannot say anything definitive.
             */
            return false;
        }

        // check if the method itself indicates it will not have a safepoint.
        SharedMethod sharedMethod = (SharedMethod) method;
        if (Uninterruptible.Utils.isUninterruptible(sharedMethod)) {
            return false;
        }

        // for indirect calls, confirming all implementations also have safepoints.
        if (!isDirect) {
            for (SharedMethod implementation : sharedMethod.getImplementations()) {
                if (Uninterruptible.Utils.isUninterruptible(implementation)) {
                    return false;
                }
            }
        }

        return true;
    }
}
