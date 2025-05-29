/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.heap;

import com.oracle.graal.pointsto.meta.AnalysisType;
import com.oracle.graal.pointsto.util.AnalysisError;

import jdk.vm.ci.meta.JavaConstant;

/**
 * Used for layered images only. Represents a reference to an object which will be defined in a
 * later layer. References to these constants will be patched at execution startup time to point to
 * the corresponding object created in a subsequent layer.
 */
public final class ImageHeapRelocatableConstant extends ImageHeapConstant {

    public static final class RelocatableConstantData extends ConstantData {
        public final String key;

        RelocatableConstantData(AnalysisType type, String key, int id) {
            super(type, null, -1, id);
            this.key = key;
        }
    }

    private ImageHeapRelocatableConstant(ConstantData constantData, boolean compressed) {
        super(constantData, compressed);
    }

    @Override
    public RelocatableConstantData getConstantData() {
        return (RelocatableConstantData) constantData;
    }

    public static ImageHeapRelocatableConstant create(AnalysisType type, String key, int id) {
        var data = new RelocatableConstantData(type, key, id);
        return new ImageHeapRelocatableConstant(data, false);
    }

    public static ImageHeapRelocatableConstant create(AnalysisType type, String key) {
        return create(type, key, -1);
    }

    @Override
    public JavaConstant compress() {
        throw AnalysisError.shouldNotReachHere("Unsupported in ImageHeapRelocatableConstant");
    }

    @Override
    public JavaConstant uncompress() {
        throw AnalysisError.shouldNotReachHere("Unsupported in ImageHeapRelocatableConstant");
    }

    @Override
    public ImageHeapConstant forObjectClone() {
        throw AnalysisError.shouldNotReachHere("Unsupported in ImageHeapRelocatableConstant");
    }

    @Override
    public String toString() {
        return "(ImageHeapRelocatableConstant) " + super.toString();
    }
}
