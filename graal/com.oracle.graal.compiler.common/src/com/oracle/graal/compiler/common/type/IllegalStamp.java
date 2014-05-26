/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.compiler.common.type;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.compiler.common.*;
import com.oracle.graal.compiler.common.spi.*;

/**
 * This stamp represents the illegal type. Values with this type can not exist at run time.
 */
public final class IllegalStamp extends Stamp {

    private IllegalStamp() {
    }

    @Override
    public Kind getStackKind() {
        return Kind.Illegal;
    }

    @Override
    public PlatformKind getPlatformKind(PlatformKindTool tool) {
        throw GraalInternalError.shouldNotReachHere("illegal stamp should not reach backend");
    }

    @Override
    public Stamp unrestricted() {
        return this;
    }

    @Override
    public Stamp illegal() {
        return this;
    }

    @Override
    public Stamp constant(Constant c, MetaAccessProvider meta) {
        throw GraalInternalError.shouldNotReachHere("illegal stamp has no value");
    }

    @Override
    public ResolvedJavaType javaType(MetaAccessProvider metaAccess) {
        throw GraalInternalError.shouldNotReachHere("illegal stamp has no Java type");
    }

    @Override
    public Stamp meet(Stamp other) {
        return this;
    }

    @Override
    public Stamp join(Stamp other) {
        return this;
    }

    @Override
    public boolean isCompatible(Stamp stamp) {
        return false;
    }

    @Override
    public String toString() {
        return "ILLEGAL";
    }

    @Override
    public boolean isLegal() {
        return false;
    }

    private static IllegalStamp instance = new IllegalStamp();

    static IllegalStamp getInstance() {
        return instance;
    }
}
