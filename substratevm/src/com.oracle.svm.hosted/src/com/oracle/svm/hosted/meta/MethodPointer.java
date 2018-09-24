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
package com.oracle.svm.hosted.meta;

import static com.oracle.svm.core.util.VMError.shouldNotReachHere;

import org.graalvm.nativeimage.c.function.CFunctionPointer;
import org.graalvm.word.ComparableWord;

import jdk.vm.ci.meta.ResolvedJavaMethod;

/**
 * A pointer to the compiled code of a method.
 */
public class MethodPointer implements CFunctionPointer {

    private final ResolvedJavaMethod method;

    public static CFunctionPointer factory(ResolvedJavaMethod method) {
        if (method == null) {
            return null;
        } else {
            return new MethodPointer(method);
        }
    }

    protected MethodPointer(ResolvedJavaMethod method) {
        assert method != null;
        this.method = method;
    }

    public ResolvedJavaMethod getMethod() {
        return method;
    }

    @Override
    public boolean isNull() {
        return false;
    }

    @Override
    public boolean isNonNull() {
        return true;
    }

    @Override
    public long rawValue() {
        throw shouldNotReachHere("must not be called in hosted mode");
    }

    @Override
    public boolean equal(ComparableWord val) {
        throw shouldNotReachHere("must not be called in hosted mode");
    }

    @Override
    public boolean notEqual(ComparableWord val) {
        throw shouldNotReachHere("must not be called in hosted mode");
    }
}
