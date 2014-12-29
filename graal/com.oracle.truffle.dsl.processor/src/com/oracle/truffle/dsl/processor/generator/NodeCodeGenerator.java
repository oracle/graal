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
package com.oracle.truffle.dsl.processor.generator;

import java.util.*;

import javax.lang.model.element.*;

import com.oracle.truffle.dsl.processor.*;
import com.oracle.truffle.dsl.processor.java.model.*;
import com.oracle.truffle.dsl.processor.model.*;

public class NodeCodeGenerator extends CodeTypeElementFactory<NodeData> {

    @Override
    public CodeTypeElement create(ProcessorContext context, NodeData node) {
        List<CodeTypeElement> enclosedTypes = new ArrayList<>();
        for (NodeData childNode : node.getEnclosingNodes()) {
            CodeTypeElement type = create(context, childNode);
            if (type != null) {
                enclosedTypes.add(type);
            }
        }
        List<CodeTypeElement> generatedNodes = generateNodes(context, node);

        if (!generatedNodes.isEmpty() || !enclosedTypes.isEmpty()) {
            CodeTypeElement type = wrapGeneratedNodes(context, node, generatedNodes);

            for (CodeTypeElement enclosedFactory : enclosedTypes) {
                Set<Modifier> modifiers = enclosedFactory.getModifiers();
                if (!modifiers.contains(Modifier.STATIC)) {
                    modifiers.add(Modifier.STATIC);
                }
                type.add(enclosedFactory);
            }
            return type;
        } else {
            return null;
        }
    }

    private static CodeTypeElement wrapGeneratedNodes(ProcessorContext context, NodeData node, List<CodeTypeElement> generatedNodes) {
        // wrap all types into a generated factory
        CodeTypeElement factoryElement = new NodeFactoryFactory(context, node, generatedNodes.isEmpty() ? null : generatedNodes.get(0)).create();
        for (CodeTypeElement generatedNode : generatedNodes) {
            factoryElement.add(generatedNode);
        }
        return factoryElement;
    }

    private static List<CodeTypeElement> generateNodes(ProcessorContext context, NodeData node) {
        if (!node.needsFactory()) {
            return Collections.emptyList();
        }
        List<CodeTypeElement> nodeTypes = new ArrayList<>();
        SpecializationData generic = node.getGenericSpecialization() == null ? node.getSpecializations().get(0) : node.getGenericSpecialization();
        CodeTypeElement baseNode = new NodeBaseFactory(context, node, generic).create();
        nodeTypes.add(baseNode);

        for (SpecializationData specialization : node.getSpecializations()) {
            if (!specialization.isReachable() || specialization.isGeneric()) {
                continue;
            }
            if (specialization.isPolymorphic() && node.isPolymorphic(context)) {
                nodeTypes.add(new PolymorphicNodeFactory(context, node, specialization, baseNode).create());
                continue;
            }

            nodeTypes.add(new SpecializedNodeFactory(context, node, specialization, baseNode).create());
        }
        return nodeTypes;
    }

}
