/*
 * Copyright (c) 2024, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.guest.staging.hub;

import java.security.ProtectionDomain;

public final class ClassDefinitionInfo {
    public static final ClassDefinitionInfo EMPTY = new ClassDefinitionInfo(null, null, null, null, false, false, false);

    // Constructor for regular definition, but with a specified protection domain
    public ClassDefinitionInfo(ProtectionDomain protectionDomain) {
        this(protectionDomain, null);
    }

    // Constructor for regular definition, but with a specified source
    public ClassDefinitionInfo(ProtectionDomain protectionDomain, String source) {
        this(protectionDomain, source, null, null, false, false, false);
    }

    // Constructor for Hidden class definition.
    public ClassDefinitionInfo(ProtectionDomain protectionDomain, Class<?> dynamicNest, Object classData, boolean isStrongHidden, boolean forceAllowVMAnnotations) {
        this(protectionDomain, null, dynamicNest, classData, true, isStrongHidden, forceAllowVMAnnotations);
    }

    private ClassDefinitionInfo(ProtectionDomain protectionDomain,
                    String source, Class<?> dynamicNest,
                    Object classData,
                    boolean isHidden,
                    boolean isStrongHidden,
                    boolean forceAllowVMAnnotations) {
        assert !isStrongHidden || isHidden;
        assert dynamicNest == null || isHidden;
        assert classData == null || isHidden;
        assert !forceAllowVMAnnotations || isHidden;
        this.protectionDomain = protectionDomain;
        this.source = source;
        this.dynamicNest = dynamicNest;
        this.classData = classData;
        this.isHidden = isHidden;
        this.isStrongHidden = isStrongHidden;
        this.forceAllowVMAnnotations = forceAllowVMAnnotations;
    }

    public final ProtectionDomain protectionDomain;
    public final String source;

    // Hidden class
    public final Class<?> dynamicNest;
    public final Object classData;
    public final boolean isHidden;
    public final boolean isStrongHidden;
    public final boolean forceAllowVMAnnotations;

    public boolean addedToRegistry() {
        return !isHidden();
    }

    public boolean isHidden() {
        return isHidden;
    }

    public boolean isStrongHidden() {
        return isStrongHidden;
    }

    public boolean forceAllowVMAnnotations() {
        return forceAllowVMAnnotations;
    }

    @Override
    public String toString() {
        if (this == EMPTY) {
            return "EMPTY";
        }
        if (!isHidden) {
            return "ClassDefinitionInfo{protectionDomain=" + protectionDomain + ", source=" + source + "}";
        }
        return "ClassDefinitionInfo{" +
                        "protectionDomain=" + protectionDomain +
                        ", source=" + source +
                        ", dynamicNest=" + dynamicNest +
                        ", classData=" + classData +
                        ", isHidden=" + isHidden +
                        ", isStrongHidden=" + isStrongHidden +
                        ", forceAllowVMAnnotations=" + forceAllowVMAnnotations +
                        '}';
    }
}
