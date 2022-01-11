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
package com.oracle.svm.core.reflect;

import java.lang.reflect.Executable;

import org.graalvm.nativeimage.c.function.CFunctionPointer;

import com.oracle.svm.core.jdk.InternalVMMethod;
import com.oracle.svm.core.reflect.SubstrateMethodAccessor.MethodInvokeFunctionPointer;
import com.oracle.svm.core.util.VMError;

import jdk.internal.reflect.ConstructorAccessor;

@InternalVMMethod
public final class SubstrateConstructorAccessor implements ConstructorAccessor {

    private final Executable member;
    private final CFunctionPointer newInstanceFunctionPointer;

    public SubstrateConstructorAccessor(Executable member, CFunctionPointer newInstanceFunctionPointer) {
        this.member = member;
        this.newInstanceFunctionPointer = newInstanceFunctionPointer;
    }

    @Override
    public Object newInstance(Object[] args) {
        MethodInvokeFunctionPointer functionPointer = (MethodInvokeFunctionPointer) this.newInstanceFunctionPointer;
        if (functionPointer.isNull()) {
            throw newInstanceError();
        }
        return functionPointer.invoke(false, null, args);
    }

    private RuntimeException newInstanceError() {
        throw VMError.shouldNotReachHere("No SubstrateConstructorAccessor.newInstanceFunctionPointer for " + member);
    }
}
