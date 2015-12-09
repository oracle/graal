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
import java.util.Set;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;

import jdk.vm.ci.meta.JavaKind;

import com.oracle.graal.replacements.verifier.InjectedDependencies.Dependency;
import com.oracle.graal.replacements.verifier.InjectedDependencies.WellKnownDependency;

public abstract class GeneratedPlugin {

    protected final ExecutableElement intrinsicMethod;
    private boolean needInjectionProvider;

    public GeneratedPlugin(ExecutableElement intrinsicMethod) {
        this.intrinsicMethod = intrinsicMethod;
        this.needInjectionProvider = false;
    }

    public void generate(ProcessingEnvironment env, PrintWriter out, int idx) {
        out.printf("    // class:  %s\n", intrinsicMethod.getEnclosingElement());
        out.printf("    // method: %s\n", intrinsicMethod);
        out.printf("    private static final class Plugin%d extends GeneratedInvocationPlugin {\n", idx);
        out.printf("\n");
        out.printf("        @Override\n");
        out.printf("        public boolean execute(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode[] args) {\n");
        out.printf("            if (!b.parsingIntrinsic()) {\n");
        out.printf("                return false;\n");
        out.printf("            }\n");
        InjectedDependencies deps = createExecute(env, out);
        out.printf("        }\n");

        createPrivateMembers(out, deps, idx);

        out.printf("    }\n");
    }

    public void register(PrintWriter out, int idx) {
        out.printf("        plugins.register(new Plugin%d(", idx);
        if (needInjectionProvider) {
            out.printf("injection");
        }
        out.printf("), %s.class, \"%s\"", intrinsicMethod.getEnclosingElement(), intrinsicMethod.getSimpleName());
        if (!intrinsicMethod.getModifiers().contains(Modifier.STATIC)) {
            out.printf(", InvocationPlugin.Receiver.class");
        }
        for (VariableElement arg : intrinsicMethod.getParameters()) {
            out.printf(", %s.class", getErasedType(arg.asType()));
        }
        out.printf(");\n");
    }

    public abstract void extraImports(Set<String> imports);

    protected abstract InjectedDependencies createExecute(ProcessingEnvironment env, PrintWriter out);

    private static TypeMirror resolvedJavaTypeType(ProcessingEnvironment env) {
        return env.getElementUtils().getTypeElement("jdk.vm.ci.meta.ResolvedJavaType").asType();
    }

    static String getErasedType(TypeMirror type) {
        switch (type.getKind()) {
            case DECLARED:
                DeclaredType declared = (DeclaredType) type;
                TypeElement element = (TypeElement) declared.asElement();
                return element.getQualifiedName().toString();
            case TYPEVAR:
                return getErasedType(((TypeVariable) type).getUpperBound());
            case WILDCARD:
                return getErasedType(((WildcardType) type).getExtendsBound());
            case ARRAY:
                return getErasedType(((ArrayType) type).getComponentType()) + "[]";
            default:
                return type.toString();
        }
    }

    private void createPrivateMembers(PrintWriter out, InjectedDependencies deps, int idx) {
        if (!deps.isEmpty()) {
            out.printf("\n");
            for (Dependency dep : deps) {
                out.printf("        private final %s %s;\n", dep.type, dep.name);
            }

            out.printf("\n");
            out.printf("        private Plugin%d(InjectionProvider injection) {\n", idx);
            for (Dependency dep : deps) {
                out.printf("            this.%s = %s;\n", dep.name, dep.inject(intrinsicMethod));
            }
            out.printf("        }\n");

            needInjectionProvider = true;
        }
    }

    protected static JavaKind getReturnKind(ExecutableElement method) {
        switch (method.getReturnType().getKind()) {
            case BOOLEAN:
            case BYTE:
            case SHORT:
            case CHAR:
            case INT:
                return JavaKind.Int;
            case LONG:
                return JavaKind.Long;
            case FLOAT:
                return JavaKind.Float;
            case DOUBLE:
                return JavaKind.Double;
            case VOID:
                return JavaKind.Void;
            case ARRAY:
            case TYPEVAR:
            case DECLARED:
                return JavaKind.Object;
            default:
                throw new IllegalArgumentException(method.getReturnType().toString());
        }
    }

    protected static void constantArgument(ProcessingEnvironment env, PrintWriter out, InjectedDependencies deps, int argIdx, TypeMirror type, int nodeIdx) {
        out.printf("            %s arg%d;\n", type, argIdx);
        out.printf("            if (args[%d].isConstant()) {\n", nodeIdx);
        if (type.equals(resolvedJavaTypeType(env))) {
            out.printf("                arg%d = %s.asJavaType(args[%d].asConstant());\n", argIdx, deps.use(WellKnownDependency.CONSTANT_REFLECTION), nodeIdx);
        } else {
            switch (type.getKind()) {
                case BOOLEAN:
                    out.printf("                arg%d = args[%d].asJavaConstant().asInt() != 0;\n", argIdx, nodeIdx);
                    break;
                case BYTE:
                    out.printf("                arg%d = (byte) args[%d].asJavaConstant().asInt();\n", argIdx, nodeIdx);
                    break;
                case CHAR:
                    out.printf("                arg%d = (char) args[%d].asJavaConstant().asInt();\n", argIdx, nodeIdx);
                    break;
                case SHORT:
                    out.printf("                arg%d = (short) args[%d].asJavaConstant().asInt();\n", argIdx, nodeIdx);
                    break;
                case INT:
                    out.printf("                arg%d = args[%d].asJavaConstant().asInt();\n", argIdx, nodeIdx);
                    break;
                case LONG:
                    out.printf("                arg%d = args[%d].asJavaConstant().asLong();\n", argIdx, nodeIdx);
                    break;
                case FLOAT:
                    out.printf("                arg%d = args[%d].asJavaConstant().asFloat();\n", argIdx, nodeIdx);
                    break;
                case DOUBLE:
                    out.printf("                arg%d = args[%d].asJavaConstant().asDouble();\n", argIdx, nodeIdx);
                    break;
                case DECLARED:
                    out.printf("                arg%d = %s.asObject(%s.class, args[%d].asJavaConstant());\n", argIdx, deps.use(WellKnownDependency.SNIPPET_REFLECTION), type, nodeIdx);
                    break;
                default:
                    throw new IllegalArgumentException();
            }
        }
        out.printf("            } else {\n");
        out.printf("                return false;\n");
        out.printf("            }\n");
    }
}
