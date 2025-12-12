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
package com.oracle.svm.interpreter.ristretto.meta;

import java.util.function.Function;

import com.oracle.svm.core.hub.DynamicHub;
import com.oracle.svm.graal.meta.SubstrateType;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;

import jdk.graal.compiler.debug.GraalError;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * JVMCI representation of a {@link ResolvedJavaType} used by Ristretto for compilation. Exists once
 * per {@link InterpreterResolvedJavaType}. Allocated during runtime compilation every time a type
 * is accessed.
 * <p>
 * Life cycle: lives until the referencing {@link InterpreterResolvedJavaType} is gc-ed.
 */
public final class RistrettoType extends SubstrateType {
    private final InterpreterResolvedJavaType interpreterType;

    private RistrettoType(InterpreterResolvedJavaType interpreterType) {
        super(interpreterType.getJavaKind(), DynamicHub.fromClass(interpreterType.getJavaClass()));
        this.interpreterType = interpreterType;
    }

    public InterpreterResolvedJavaType getInterpreterType() {
        return interpreterType;
    }

    private static final Function<InterpreterResolvedJavaType, ResolvedJavaType> RISTRETTO_TYPE_FUNCTION = RistrettoType::new;

    public static RistrettoType create(InterpreterResolvedJavaType interpreterType) {
        return (RistrettoType) interpreterType.getRistrettoType(RISTRETTO_TYPE_FUNCTION);
    }

    @Override
    public String toString() {
        return "RistrettoType{super=" + super.toString() + ", interpreterType=" + interpreterType + "}";
    }

    @Override
    public ResolvedJavaType getComponentType() {
        if (isArray()) {
            InterpreterResolvedJavaType iComponentType = (InterpreterResolvedJavaType) interpreterType.getComponentType();
            GraalError.guarantee(iComponentType != null, "Must find component type if we are dealing with an array, this %s component type %s", this, iComponentType);
            return create(iComponentType);
        } else {

            return null;
        }
    }

    @Override
    public boolean isArray() {
        return interpreterType.isArray();
    }

    @Override
    public boolean isLinked() {
        /*
         * TODO GR-59739, GR-71851 - crema does not implement linking at the moment, so we assume
         * all resolved (==loaded) types successfully linked as well
         */
        return true;
    }
}
