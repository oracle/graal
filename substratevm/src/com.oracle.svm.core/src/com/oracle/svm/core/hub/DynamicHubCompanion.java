/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.hub;

import java.security.ProtectionDomain;

import com.oracle.svm.core.util.VMError;

/** An optional, non-immutable companion to a {@link DynamicHub} instance. */
public final class DynamicHubCompanion {
    private final DynamicHub hub;

    private String packageName;
    private ClassLoader classLoader;
    private ProtectionDomain protectionDomain;

    public DynamicHubCompanion(DynamicHub hub) {
        this.hub = hub;
    }

    public String getPackageName() {
        if (packageName == null) {
            packageName = hub.computePackageName();
        }
        return packageName;
    }

    boolean hasClassLoader() {
        return classLoader != null;
    }

    public ClassLoader getClassLoader() {
        ClassLoader loader = classLoader;
        VMError.guarantee(loader != null);
        return loader;
    }

    public void setClassLoader(ClassLoader loader) {
        VMError.guarantee(classLoader == null && loader != null);
        classLoader = loader;
    }

    public ProtectionDomain getProtectionDomain() {
        if (protectionDomain == null) {
            protectionDomain = DynamicHub.allPermDomainReference.get();
        }
        return protectionDomain;
    }

    public void setProtectionDomain(ProtectionDomain domain) {
        VMError.guarantee(protectionDomain == null && domain != null);
        protectionDomain = domain;
    }
}
