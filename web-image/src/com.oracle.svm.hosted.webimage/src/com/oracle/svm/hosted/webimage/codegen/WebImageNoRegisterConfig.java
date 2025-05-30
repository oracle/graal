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
package com.oracle.svm.hosted.webimage.codegen;

import com.oracle.svm.core.graal.meta.SubstrateRegisterConfig;
import com.oracle.svm.core.util.VMError;

import jdk.vm.ci.code.CallingConvention;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterAttributes;
import jdk.vm.ci.code.ValueKindFactory;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.JavaType;
import jdk.vm.ci.meta.PlatformKind;

import java.util.List;

public class WebImageNoRegisterConfig implements SubstrateRegisterConfig {
    @Override
    public Register getReturnRegister(JavaKind kind) {
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    public CallingConvention getCallingConvention(CallingConvention.Type type, JavaType returnType, JavaType[] parameterTypes, ValueKindFactory<?> valueKindFactory) {
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    public List<Register> getCallingConventionRegisters(CallingConvention.Type type, JavaKind kind) {
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    public List<Register> getAllocatableRegisters() {
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    public List<Register> filterAllocatableRegisters(PlatformKind kind, List<Register> registers) {
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    public List<Register> getCallerSaveRegisters() {
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    public List<Register> getCalleeSaveRegisters() {
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    public List<RegisterAttributes> getAttributesMap() {
        throw VMError.shouldNotReachHereAtRuntime();
    }

    @Override
    public boolean areAllAllocatableRegistersCallerSaved() {
        throw VMError.shouldNotReachHereAtRuntime();
    }
}
