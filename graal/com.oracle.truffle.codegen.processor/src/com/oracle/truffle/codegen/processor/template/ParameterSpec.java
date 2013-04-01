/*
 * Copyright (c) 2012, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.codegen.processor.template;

import java.util.*;

import javax.lang.model.type.*;

import com.oracle.truffle.codegen.processor.*;
import com.oracle.truffle.codegen.processor.node.*;
import com.oracle.truffle.codegen.processor.typesystem.*;

public class ParameterSpec {

    public enum Cardinality {
        ONE, MULTIPLE;
    }

    private final String name;
    private final List<TypeMirror> allowedTypes;
    private Cardinality cardinality;
    private final boolean optional;
    private final boolean signature;
    private boolean indexed;
    private boolean local;

    public ParameterSpec(String name, List<TypeMirror> allowedTypes, boolean optional, Cardinality cardinality, boolean signature) {
        this.allowedTypes = allowedTypes;
        this.name = name;
        this.optional = optional;
        this.cardinality = cardinality;
        this.signature = signature;
    }

    /** Type constructor. */
    public ParameterSpec(String name, TypeMirror singleFixedType, boolean optional, boolean signature) {
        this(name, Arrays.asList(singleFixedType), optional, Cardinality.ONE, signature);
    }

    /** Type system value constructor. */
    public ParameterSpec(String name, TypeSystemData typeSystem, boolean optional, Cardinality cardinality, boolean signature) {
        this(name, typeSystem.getPrimitiveTypeMirrors(), optional, cardinality, signature);
    }

    /** Node value constructor. */
    public ParameterSpec(String name, NodeData nodeData, boolean optional, Cardinality cardinality, boolean signature) {
        this(name, nodeTypeMirrors(nodeData), optional, cardinality, signature);
    }

    public void setLocal(boolean local) {
        this.local = local;
    }

    public boolean isSignature() {
        return signature;
    }

    public boolean isLocal() {
        return local;
    }

    public boolean isIndexed() {
        return indexed;
    }

    public void setIndexed(boolean indexed) {
        this.indexed = indexed;
    }

    public void setCardinality(Cardinality cardinality) {
        this.cardinality = cardinality;
    }

    private static List<TypeMirror> nodeTypeMirrors(NodeData nodeData) {
        Set<TypeMirror> typeMirrors = new LinkedHashSet<>();

        for (ExecutableTypeData typeData : nodeData.getExecutableTypes()) {
            typeMirrors.add(typeData.getType().getPrimitiveType());
        }

        typeMirrors.add(nodeData.getTypeSystem().getGenericType());

        return new ArrayList<>(typeMirrors);
    }

    public final String getName() {
        return name;
    }

    public final boolean isOptional() {
        return optional;
    }

    public final Cardinality getCardinality() {
        return cardinality;
    }

    public List<TypeMirror> getAllowedTypes() {
        return allowedTypes;
    }

    public boolean matches(TypeMirror actualType) {
        for (TypeMirror mirror : allowedTypes) {
            if (Utils.typeEquals(actualType, mirror)) {
                return true;
            }
        }
        return false;
    }

}
