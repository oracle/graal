/*
 * Copyright (c) 2022, 2022, Oracle and/or its affiliates. All rights reserved.
 * Copyright (c) 2022, 2022, Red Hat Inc. All rights reserved.
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
package com.oracle.svm.core.jfr;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.core.annotate.Uninterruptible;

import java.util.Collection;

public class JfrVMOperations {
    private Class<?>[] vmOperations;

    @Platforms(Platform.HOSTED_ONLY.class)
    public JfrVMOperations() {
        vmOperations = new Class<?>[0];
    }

    @Fold
    public static JfrVMOperations singleton() {
        return ImageSingletons.lookup(JfrVMOperations.class);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void addVMOperations(Collection<Class<?>> vmOps) {
        vmOperations = vmOps.toArray(new Class<?>[vmOps.size()]);
    }

    @Uninterruptible(reason = "Called from uninterruptible code.", mayBeInlined = true)
    public int getVMOperationId(Class<?> clazz) {
        for (int id = 0; id < vmOperations.length; id++) {
            if (vmOperations[id] == clazz) {
                return id + 1;    // id starts with 1
            }
        }
        return 0;
    }

    public Class<?>[] getVMOperations() {
        return vmOperations;
    }
}
