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

package com.oracle.svm.hosted;

import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.graal.pointsto.infrastructure.OriginalClassProvider;

import jdk.vm.ci.meta.ResolvedJavaType;

public final class LinkAtBuildTimeSupport {

    private final LinkAtBuildTimeFeature feature;

    LinkAtBuildTimeSupport(LinkAtBuildTimeFeature feature) {
        this.feature = feature;
    }

    public static LinkAtBuildTimeSupport singleton() {
        return ImageSingletons.lookup(LinkAtBuildTimeSupport.class);
    }

    public boolean linkAtBuildTime(ResolvedJavaType type) {
        Class<?> clazz = ((OriginalClassProvider) type).getJavaClass();
        if (clazz == null) {
            /*
             * Some kind of synthetic class coming from a substitution. We assume all such classes
             * are linked at build time.
             */
            return true;
        }
        return linkAtBuildTime(clazz);
    }

    public boolean linkAtBuildTime(Class<?> clazz) {
        return feature.linkAtBuildTime(clazz);
    }

    public String errorMessageFor(ResolvedJavaType type) {
        Class<?> clazz = ((OriginalClassProvider) type).getJavaClass();
        if (clazz == null) {
            return "This error is reported at image build time because class " + type.toJavaName(true) + " is registered for linking at image build time.";
        }
        return errorMessageFor(clazz);
    }

    public String errorMessageFor(Class<?> clazz) {
        assert feature.linkAtBuildTime(clazz);
        return "This error is reported at image build time because class " + clazz.getTypeName() + " is registered for linking at image build time by " + feature.linkAtBuildTimeReason(clazz);
    }
}
