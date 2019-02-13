/*
 * Copyright (c) 2013, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.meta;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.meta.Assumptions.AssumptionResult;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * The type interface which is both used in the hosted and substrate worlds.
 */
public interface SharedType extends ResolvedJavaType {

    DynamicHub getHub();

    /**
     * Gets the start of the type-ID range for instance-of checks. If the type-ID of the object in
     * question is within this range then the object is an instance of this type.
     * <p>
     * Now if there is not a single type-ID range to be used for the check (e.g. for interface
     * types), then the instance-of check must be done with a bit test. In this case
     * {@link #getInstanceOfNumTypeIDs()} return -1 and this method returns the bit number to check
     * in the bit-set of the object's {@link DynamicHub}.
     */
    int getInstanceOfFromTypeID();

    /**
     * Returns the size of the type-ID range for instance-of checks. Specifically:
     * <ul>
     * <li>0: there are no instantiated objects of that type and the instance-of check fails always
     * </li>
     * <li>-1: the instance-of check must be done with a bit test and
     * {@link #getInstanceOfFromTypeID()} returns the bit number</li>
     * <li>< -1: this type should not be in any type checks</li>
     * </ul>
     *
     * @see #getInstanceOfFromTypeID()
     */
    int getInstanceOfNumTypeIDs();

    /**
     * The kind of the field in memory (in contrast to {@link #getJavaKind()}, which is the kind of
     * the field on the Java type system level).
     */
    JavaKind getStorageKind();

    @Override
    default ResolvedJavaMethod resolveMethod(ResolvedJavaMethod method, ResolvedJavaType callerType) {
        /*
         * Not needed on Substrate VM for now, and we do not have the necessary information
         * available to implement it. method.getImplementations() does not contain abstract methods.
         */
        throw VMError.unimplemented();
    }

    @Override
    default ResolvedJavaMethod resolveConcreteMethod(ResolvedJavaMethod m, ResolvedJavaType callerType) {
        SharedMethod method = (SharedMethod) m;

        SharedMethod found = null;
        if (!isInterface()) {
            for (SharedMethod cur : method.getImplementations()) {
                assert !cur.isAbstract();
                ResolvedJavaType curHolder = cur.getDeclaringClass();
                if (curHolder.isAssignableFrom(this)) {
                    if (found == null) {
                        /* The first matching method. */
                        found = cur;
                    } else if (found.getDeclaringClass().isAssignableFrom(curHolder)) {
                        /*
                         * A better match, defined in a subclass of the previously found matching
                         * method.
                         */
                        found = cur;
                    }
                }
            }
        }
        return found;
    }

    @Override
    default AssumptionResult<ResolvedJavaMethod> findUniqueConcreteMethod(ResolvedJavaMethod m) {
        SharedMethod method = (SharedMethod) m;

        SharedMethod[] implementations = method.getImplementations();
        if (implementations.length == 1) {
            return new AssumptionResult<>(implementations[0]);
        }
        return null;
    }
}
