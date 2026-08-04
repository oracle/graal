/*
 * Copyright (c) 2023, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import com.oracle.svm.shared.singletons.traits.BuiltinTraits.AllAccess;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.NoLayeredCallbacks;
import com.oracle.svm.shared.singletons.traits.BuiltinTraits.PartiallyLayerAware;
import com.oracle.svm.shared.singletons.traits.SingletonLayeredInstallationKind.Duplicable;
import com.oracle.svm.shared.singletons.traits.SingletonTraits;
import com.oracle.svm.shared.util.SubstrateUtil;

import jdk.graal.compiler.api.replacements.Fold;

@SingletonTraits(access = AllAccess.class, layeredCallbacks = NoLayeredCallbacks.class, layeredInstallationKind = Duplicable.class, other = PartiallyLayerAware.class)
public class MissingRegistrationSupport {
    @Platforms(Platform.HOSTED_ONLY.class)
    public MissingRegistrationSupport() {
    }

    @Fold
    public static MissingRegistrationSupport singleton() {
        return ImageSingletons.lookup(MissingRegistrationSupport.class);
    }

    public boolean reportMissingRegistrationErrors(StackTraceElement responsibleClass) {
        return reportMissingRegistrationErrors(responsibleClass.getModuleName(), getPackageName(responsibleClass.getClassName()), responsibleClass.getClassName());
    }

    public boolean reportMissingRegistrationErrors(Class<?> clazz) {
        return reportMissingRegistrationErrors(clazz.getModule().getName(), clazz.getPackageName(), clazz.getName());
    }

    private boolean reportMissingRegistrationErrors(String moduleName, String packageName, String className) {
        /* Hosted plugins must retain the metadata needed by either runtime mode. */
        return SubstrateUtil.HOSTED || MissingRegistrationUtils.exactReflection();
    }

    private static String getPackageName(String className) {
        int lastDot = className.lastIndexOf('.');
        return (lastDot != -1) ? className.substring(0, lastDot) : "";
    }
}
