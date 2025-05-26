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

package com.oracle.svm.hosted;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.AccessCondition;
import org.graalvm.nativeimage.hosted.RuntimeForeignAccess;
import org.graalvm.nativeimage.impl.RuntimeForeignAccessSupport;

import java.lang.invoke.MethodHandle;
import java.util.Arrays;

public final class RuntimeForeignAccessImpl implements RuntimeForeignAccess {

    @Override
    public void registerForDowncall(AccessCondition condition, Object desc, Object... options) {
        DynamicAccessSupport.printUserError("following descriptor and options pair for downcalls into foreign code: " + desc.toString() + Arrays.toString(options));
        ImageSingletons.lookup(RuntimeForeignAccessSupport.class).registerForDowncall(condition, desc, options);
    }

    @Override
    public void registerForUpcall(AccessCondition condition, Object desc, Object... options) {
        DynamicAccessSupport.printUserError("following descriptor and options pair for upcalls from foreign code: " + desc.toString() + Arrays.toString(options));
        ImageSingletons.lookup(RuntimeForeignAccessSupport.class).registerForUpcall(condition, desc, options);
    }

    @Override
    public void registerForDirectUpcall(AccessCondition condition, MethodHandle target, Object desc, Object... options) {
        DynamicAccessSupport.printUserError("following method handle as a fast upcall target: " + target);
        ImageSingletons.lookup(RuntimeForeignAccessSupport.class).registerForDirectUpcall(condition, target, desc, options);
    }
}
