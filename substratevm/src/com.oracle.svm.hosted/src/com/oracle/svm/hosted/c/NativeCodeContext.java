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

import java.util.Collection;
import java.util.HashSet;

import org.graalvm.nativeimage.c.CContext;

import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * C annotation processing context for one @Descriptor.
 */
public class NativeCodeContext {
    private final CContext.Directives directives;
    private final boolean inConfiguration;

    private final Collection<ResolvedJavaMethod> constantAccessors;
    private final Collection<ResolvedJavaType> structTypes;
    private final Collection<ResolvedJavaType> rawStructTypes;
    private final Collection<ResolvedJavaType> pointerToTypes;
    private final Collection<ResolvedJavaType> enumTypes;

    NativeCodeContext(CContext.Directives directives) {
        this.inConfiguration = directives.isInConfiguration();

        if (inConfiguration) {
            this.constantAccessors = new HashSet<>();
            this.structTypes = new HashSet<>();
            this.rawStructTypes = new HashSet<>();
            this.pointerToTypes = new HashSet<>();
            this.enumTypes = new HashSet<>();
            this.directives = directives;
        } else {
            this.constantAccessors = null;
            this.structTypes = null;
            this.rawStructTypes = null;
            this.pointerToTypes = null;
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

    public void appendPointerToType(ResolvedJavaType type) {
        pointerToTypes.add(type);
    }

    public void appendEnumType(ResolvedJavaType type) {
        enumTypes.add(type);
    }

    public Collection<ResolvedJavaMethod> getConstantAccessors() {
        return constantAccessors;
    }

    public Collection<ResolvedJavaType> getStructTypes() {
        return structTypes;
    }

    public Collection<ResolvedJavaType> getRawStructTypes() {
        return rawStructTypes;
    }

    public Collection<ResolvedJavaType> getPointerToTypes() {
        return pointerToTypes;
    }

    public Collection<ResolvedJavaType> getEnumTypes() {
        return enumTypes;
    }

}
