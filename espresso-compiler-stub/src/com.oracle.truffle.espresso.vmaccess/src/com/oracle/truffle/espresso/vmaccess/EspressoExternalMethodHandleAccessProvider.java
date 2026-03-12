/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.vmaccess;

import java.util.Objects;

import org.graalvm.polyglot.Value;

import jdk.vm.ci.meta.JavaConstant;
import jdk.vm.ci.meta.MethodHandleAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;

public class EspressoExternalMethodHandleAccessProvider implements MethodHandleAccessProvider {
    private final EspressoExternalVMAccess access;

    EspressoExternalMethodHandleAccessProvider(EspressoExternalVMAccess access) {
        this.access = access;
    }

    @Override
    public IntrinsicMethod lookupMethodHandleIntrinsic(ResolvedJavaMethod method) {
        EspressoExternalResolvedJavaMethod espressoMethod = (EspressoExternalResolvedJavaMethod) method;
        Value intrinsic = espressoMethod.getMirror().getMember("methodHandleIntrinsic");
        if (intrinsic.isNull()) {
            return null;
        }
        return IntrinsicMethod.valueOf(intrinsic.asString());
    }

    @Override
    public ResolvedJavaMethod resolveInvokeBasicTarget(JavaConstant methodHandle, boolean forceBytecodeGeneration) {
        if (!(Objects.requireNonNull(methodHandle) instanceof EspressoExternalObjectConstant objectConstant)) {
            return null;
        }
        Value result = access.invokeJVMCIHelper("resolveInvokeBasicTarget", objectConstant.getValue(), forceBytecodeGeneration);
        if (result.isNull()) {
            return null;
        }
        return new EspressoExternalResolvedJavaMethod(result, access);
    }

    @Override
    public ResolvedJavaMethod resolveLinkToTarget(JavaConstant memberName) {
        if (Objects.requireNonNull(memberName).isNull()) {
            return null;
        }
        if (!(memberName instanceof EspressoExternalObjectConstant objectConstant)) {
            throw new IllegalArgumentException("Expected an object constant");
        }
        Value result = access.invokeJVMCIHelper("resolveLinkToTarget", objectConstant.getValue());
        if (result.isNull()) {
            return null;
        }
        if (result.isString()) {
            throw new IllegalArgumentException(result.asString());
        }
        return new EspressoExternalResolvedJavaMethod(result, access);
    }
}
