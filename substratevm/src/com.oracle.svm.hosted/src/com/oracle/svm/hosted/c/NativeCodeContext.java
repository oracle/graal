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
package com.oracle.svm.hosted.c;

import org.graalvm.collections.EconomicSet;
import org.graalvm.nativeimage.c.CContext;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * C annotation processing context for one @Descriptor.
 */
public class NativeCodeContext {
    private final CContext.Directives directives;
    private final boolean inConfiguration;

    private final EconomicSet<ResolvedJavaMethod> constantAccessors;
    private final EconomicSet<ResolvedJavaType> structTypes;
    private final EconomicSet<ResolvedJavaType> rawStructTypes;
    private final EconomicSet<ResolvedJavaType> cPointerToTypes;
    private final EconomicSet<ResolvedJavaType> rawPointerToTypes;
    private final EconomicSet<ResolvedJavaType> enumTypes;

    NativeCodeContext(CContext.Directives directives) {
        this.inConfiguration = directives.isInConfiguration();

        if (inConfiguration) {
            this.constantAccessors = EconomicSet.create();
            this.structTypes = EconomicSet.create();
            this.rawStructTypes = EconomicSet.create();
            this.cPointerToTypes = EconomicSet.create();
            this.rawPointerToTypes = EconomicSet.create();
            this.enumTypes = EconomicSet.create();
            this.directives = directives;
        } else {
            this.constantAccessors = null;
            this.structTypes = null;
            this.rawStructTypes = null;
            this.cPointerToTypes = null;
            this.rawPointerToTypes = null;
            this.enumTypes = null;
            this.directives = null;
        }
    }

    public boolean isInConfiguration() {
        return inConfiguration;
    }

    public CContext.Directives getDirectives() {
        return directives;
    }

    public void appendConstantAccessor(ResolvedJavaMethod method) {
        constantAccessors.add(method);
    }

    public void appendStructType(ResolvedJavaType type) {
        structTypes.add(type);
    }

    public void appendRawStructType(ResolvedJavaType type) {
        rawStructTypes.add(type);
    }

    public void appendCPointerToType(ResolvedJavaType type) {
        cPointerToTypes.add(type);
    }

    public void appendRawPointerToType(ResolvedJavaType type) {
        rawPointerToTypes.add(type);
    }

    public void appendEnumType(ResolvedJavaType type) {
        enumTypes.add(type);
    }

    public EconomicSet<ResolvedJavaMethod> getConstantAccessors() {
        return constantAccessors;
    }

    public EconomicSet<ResolvedJavaType> getStructTypes() {
        return structTypes;
    }

    public EconomicSet<ResolvedJavaType> getRawStructTypes() {
        return rawStructTypes;
    }

    public EconomicSet<ResolvedJavaType> getCPointerToTypes() {
        return cPointerToTypes;
    }

    public EconomicSet<ResolvedJavaType> getRawPointerToTypes() {
        return rawPointerToTypes;
    }

    public EconomicSet<ResolvedJavaType> getEnumTypes() {
        return enumTypes;
    }

}
