/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.dsl.processor.generator;

import static com.oracle.truffle.dsl.processor.java.ElementUtils.*;
import static javax.lang.model.element.Modifier.*;

import java.util.*;

import javax.lang.model.type.*;

import com.oracle.truffle.api.nodes.*;
import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.java.model.*;
import com.oracle.truffle.dsl.processor.model.*;

class PolymorphicNodeFactory extends SpecializedNodeFactory {

    public PolymorphicNodeFactory(ProcessorContext context, NodeData node, SpecializationData specialization, CodeTypeElement nodeGen) {
        super(context, node, specialization, nodeGen);
    }

    @Override
    public CodeTypeElement create() {
        TypeMirror baseType = node.getNodeType();
        if (nodeGen != null) {
            baseType = nodeGen.asType();
        }
        CodeTypeElement clazz = GeneratorUtils.createClass(node, modifiers(PRIVATE, STATIC, FINAL), nodePolymorphicClassName(node), baseType, false);

        clazz.getAnnotationMirrors().add(createNodeInfo(NodeCost.POLYMORPHIC));

        for (Parameter polymorphParameter : specialization.getSignatureParameters()) {
            if (!polymorphParameter.getTypeSystemType().isGeneric()) {
                continue;
            }
            Set<TypeData> types = new HashSet<>();
            for (SpecializationData otherSpecialization : node.getSpecializations()) {
                if (!otherSpecialization.isSpecialized()) {
                    continue;
                }
                Parameter parameter = otherSpecialization.findParameter(polymorphParameter.getLocalName());
                assert parameter != null;
                types.add(parameter.getTypeSystemType());
            }

        }

        for (NodeExecutionData execution : node.getChildExecutions()) {
            String fieldName = polymorphicTypeName(execution);
            CodeVariableElement var = new CodeVariableElement(modifiers(PRIVATE), context.getType(Class.class), fieldName);
            var.getAnnotationMirrors().add(new CodeAnnotationMirror(context.getTruffleTypes().getCompilationFinal()));
            clazz.add(var);
        }

        createConstructors(clazz);
        createExecuteMethods(clazz);

        clazz.add(createUpdateTypes0());
        createCachedExecuteMethods(clazz);

        return clazz;
    }

}