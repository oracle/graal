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

import org.graalvm.nativeimage.dynamicaccess.AccessCondition;
import org.graalvm.nativeimage.dynamicaccess.ResourceAccess;

import java.util.ResourceBundle;

import com.oracle.svm.shared.util.ReflectionUtil;
import com.oracle.svm.util.GuestAccess;
import com.oracle.svm.util.dynamicaccess.JVMCIResourceAccess;

import jdk.graal.compiler.vmaccess.ResolvedJavaModule;
import jdk.vm.ci.meta.JavaConstant;

public final class ResourceAccessImpl implements ResourceAccess, JVMCIResourceAccess {

    private final InternalResourceAccess rdaInstance;
    private static ResourceAccessImpl instance;

    private ResourceAccessImpl() {
        rdaInstance = InternalResourceAccess.singleton();
    }

    public static ResourceAccessImpl singleton() {
        if (instance == null) {
            instance = new ResourceAccessImpl();
        }
        return instance;
    }

    @Override
    public void register(AccessCondition condition, Module module, String pattern) {
        DynamicAccessSupport.printErrorIfSealedOrInvalidCondition(condition, pattern);
        rdaInstance.register(condition, module, pattern);
    }

    @Override
    public void registerResourceBundle(AccessCondition condition, ResourceBundle... bundles) {
        for (ResourceBundle bundle : bundles) {
            DynamicAccessSupport.printErrorIfSealedOrInvalidCondition(condition, bundle.getBaseBundleName());
            rdaInstance.registerResourceBundle(condition, bundle);
        }
    }

    @Override
    public void register(AccessCondition condition, ResolvedJavaModule module, String pattern) {
        // TODO GR-71805: remove this fallback once resource registration accepts JVMCI modules.
        Module reflectionModule = module == null ? null : ReflectionUtil.readField(module.getClass(), "module", module);
        register(condition, reflectionModule, pattern);
    }

    @Override
    public void registerResourceBundle(AccessCondition condition, JavaConstant... bundles) {
        for (JavaConstant bundle : bundles) {
            // TODO GR-71805: register JVMCI resource bundles without materializing ResourceBundle.
            registerResourceBundle(condition, GuestAccess.get().getSnippetReflection().asObject(ResourceBundle.class, bundle));
        }
    }
}
