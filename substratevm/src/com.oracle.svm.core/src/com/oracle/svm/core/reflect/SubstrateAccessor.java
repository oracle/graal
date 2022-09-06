/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.reflect;

import java.lang.reflect.Executable;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.function.CFunctionPointer;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.core.jdk.InternalVMMethod;

@InternalVMMethod
public abstract class SubstrateAccessor {
    /**
     * We do not want unnecessary Method/Constructor objects in the image heap, so this field is
     * only available at image build time to compute derived information (like the vtable offset),
     * and to help debugging at image build time.
     */
    @Platforms(Platform.HOSTED_ONLY.class) //
    final Executable member;

    /**
     * The first-level function that is invoked. It expands the boxed Object[] signature to the
     * expanded real signature.
     */
    final CFunctionPointer expandSignature;
    /**
     * The direct call target, if there is any. For non-virtual invokes, this is the second-level
     * function that is invoked. For virtual invokes, this value is ignored and the actual target
     * function is loaded from the vtable.
     */
    final CFunctionPointer directTarget;
    /**
     * Class that needs to be initialized before invoking the target method. Null when no
     * initialization is necessary, i.e., when invoking non-static methods or when the class is
     * already initialized at image build time.
     */
    final DynamicHub initializeBeforeInvoke;

    @Platforms(Platform.HOSTED_ONLY.class)
    SubstrateAccessor(Executable member, CFunctionPointer expandSignature, CFunctionPointer directTarget, DynamicHub initializeBeforeInvoke) {
        this.member = member;
        this.expandSignature = expandSignature;
        this.directTarget = directTarget;
        this.initializeBeforeInvoke = initializeBeforeInvoke;
    }

    public Executable getMember() {
        return member;
    }
}
