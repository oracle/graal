/*
 * Copyright (c) 2015, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.replacements.verifier;

import java.io.PrintWriter;
import java.util.List;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

import jdk.vm.ci.meta.JavaKind;

import com.oracle.graal.graph.Node.ConstantNodeParameter;
import com.oracle.graal.graph.Node.InjectedNodeParameter;
import com.oracle.graal.graph.Node.NodeIntrinsic;
import com.oracle.graal.replacements.verifier.InjectedDependencies.WellKnownDependency;

/**
 * Create graph builder plugins for {@link NodeIntrinsic} methods.
 */
public class NodeIntrinsicPluginGenerator extends PluginGenerator {

    public NodeIntrinsicPluginGenerator(ProcessingEnvironment env) {
        super(env);
    }

    private TypeMirror valueNodeType() {
        return env.getElementUtils().getTypeElement("com.oracle.graal.nodes.ValueNode").asType();
    }

    @Override
    protected String getBaseName() {
        return "NodeIntrinsicFactory";
    }

    @Override
    protected void createImports(PrintWriter out, ExecutableElement intrinsicMethod, ExecutableElement targetMethod) {
        if (targetMethod.getKind() == ElementKind.CONSTRUCTOR && getReturnKind(intrinsicMethod) != JavaKind.Void) {
            out.printf("import jdk.vm.ci.meta.JavaKind;\n");
        }
        super.createImports(out, intrinsicMethod, targetMethod);
    }

    @Override
    protected InjectedDependencies createExecute(PrintWriter out, ExecutableElement intrinsicMethod, ExecutableElement constructor, TypeMirror[] signature) {
        InjectedDependencies deps = new InjectedDependencies();

        List<? extends VariableElement> params = constructor.getParameters();

        boolean customFactory = constructor.getKind() != ElementKind.CONSTRUCTOR;
        int idx = customFactory ? 1 : 0;
        for (; idx < params.size(); idx++) {
            VariableElement param = params.get(idx);
            if (param.getAnnotation(InjectedNodeParameter.class) == null) {
                break;
            }

            out.printf("            %s arg%d = %s;\n", param.asType(), idx, deps.use(env, (DeclaredType) param.asType()));
        }

        for (int i = 0; i < signature.length; i++, idx++) {
            if (intrinsicMethod.getParameters().get(i).getAnnotation(ConstantNodeParameter.class) != null) {
                constantArgument(out, deps, idx, signature[i], i);
            } else {
                if (signature[i].equals(valueNodeType())) {
                    out.printf("            ValueNode arg%d = args[%d];\n", idx, i);
                } else {
                    out.printf("            %s arg%d = (%s) args[%d];\n", signature[i], idx, signature[i], i);
                }
            }
        }

        if (customFactory) {
            out.printf("            return %s.%s(b", constructor.getEnclosingElement(), constructor.getSimpleName());
            for (int i = 1; i < idx; i++) {
                out.printf(", arg%d", i);
            }
            out.printf(");\n");

            if (intrinsicMethod.getAnnotation(NodeIntrinsic.class).setStampFromReturnType()) {
                env.getMessager().printMessage(Kind.WARNING, "Ignoring setStampFromReturnType because a custom 'intrinsify' method is used.", intrinsicMethod);
            }
        } else {
            out.printf("            %s node = new %s(", constructor.getEnclosingElement(), constructor.getEnclosingElement());
            if (idx > 0) {
                out.printf("arg0");
                for (int i = 1; i < idx; i++) {
                    out.printf(", arg%d", i);
                }
            }
            out.printf(");\n");

            if (intrinsicMethod.getAnnotation(NodeIntrinsic.class).setStampFromReturnType()) {
                out.printf("            node.setStamp(%s);\n", deps.use(WellKnownDependency.RETURN_STAMP));
            }

            JavaKind returnKind = getReturnKind(intrinsicMethod);
            if (returnKind == JavaKind.Void) {
                out.printf("            b.add(node);\n");
            } else {
                out.printf("            b.addPush(JavaKind.%s, node);\n", returnKind.name());
            }
            out.printf("            return true;\n");
        }

        return deps;
    }
}
