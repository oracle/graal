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

import java.lang.reflect.Method;
import java.util.Objects;
import java.util.ResourceBundle;

import com.oracle.svm.core.util.UserError;
import com.oracle.svm.util.ReflectionUtil;
import org.graalvm.nativeimage.dynamicaccess.AccessCondition;
import org.graalvm.nativeimage.impl.RuntimeResourceSupport;
import org.graalvm.nativeimage.dynamicaccess.ResourceAccess;

public final class InternalResourceAccess implements ResourceAccess {

    private final RuntimeResourceSupport<AccessCondition> rrsInstance;
    private static InternalResourceAccess instance;

    private InternalResourceAccess() {
        rrsInstance = RuntimeResourceSupport.singleton();
    }

    public static InternalResourceAccess singleton() {
        if (instance == null) {
            instance = new InternalResourceAccess();
        }
        return instance;
    }

    @Override
    public void register(AccessCondition condition, Module module, String pattern) {
        Objects.requireNonNull(pattern, "Resource pattern cannot be null. Please ensure that all values you register are not null.");
        if (pattern.replace("\\*", "").contains("*")) {
            String moduleName = module == null ? null : module.getName();
            rrsInstance.addGlob(condition, moduleName, pattern, "Registered from API");
        } else {
            rrsInstance.addResource(condition, module, pattern.replace("\\*", "*"), "Registered from API");
        }
    }

    @Override
    public void registerResourceBundle(AccessCondition condition, ResourceBundle... bundles) {
        for (ResourceBundle bundle : bundles) {
            Objects.requireNonNull(bundle, "ResourceBundle value cannot be null. Please ensure that all values you register are not null.");
            var cache = ReflectionUtil.readField(ResourceBundle.class, "cacheKey", bundle);
            if (cache == null) {
                UserError.abort(new IllegalStateException(), "ResourceBundle instances must be obtained via ResourceBundle#getBundle.");
            }

            Method m = ReflectionUtil.lookupMethod(cache.getClass(), "getModule");
            Module modul = ReflectionUtil.invokeMethod(m, cache);

            Method m2 = ReflectionUtil.lookupMethod(cache.getClass(), "getName");
            String name = ReflectionUtil.invokeMethod(m2, cache);

            String finalBundleName = (modul != null && modul.isNamed()) ? modul.getName() + ":" + name : name;
            rrsInstance.addResourceBundles(condition, false, finalBundleName);
        }
    }
}
