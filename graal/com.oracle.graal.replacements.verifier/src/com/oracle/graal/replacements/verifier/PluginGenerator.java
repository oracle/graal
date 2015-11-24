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

import java.io.IOException;
import java.io.PrintWriter;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.type.TypeVariable;
import javax.lang.model.type.WildcardType;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import jdk.vm.ci.meta.JavaKind;

import com.oracle.graal.replacements.verifier.InjectedDependencies.Dependency;
import com.oracle.graal.replacements.verifier.InjectedDependencies.WellKnownDependency;

public abstract class PluginGenerator {

    protected final ProcessingEnvironment env;

    public PluginGenerator(ProcessingEnvironment env) {
        this.env = env;
    }

    private TypeMirror resolvedJavaTypeType() {
        return env.getElementUtils().getTypeElement("jdk.vm.ci.meta.ResolvedJavaType").asType();
    }

    private static PackageElement getPackage(Element element) {
        Element enclosing = element;
        while (enclosing != null && enclosing.getKind() != ElementKind.PACKAGE) {
            enclosing = enclosing.getEnclosingElement();
        }
        return (PackageElement) enclosing;
    }

    private static void mkClassName(StringBuilder ret, Element cls) {
        Element enclosingClass = cls.getEnclosingElement();
        if (enclosingClass.getKind() == ElementKind.CLASS || enclosingClass.getKind() == ElementKind.INTERFACE) {
            mkClassName(ret, enclosingClass);
            ret.append('_');
        }
        ret.append(cls.getSimpleName());
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

    protected abstract String getBaseName();

    private String mkFactoryClassName(ExecutableElement intrinsicMethod) {
        StringBuilder ret = new StringBuilder();
        ret.append(getBaseName());
        ret.append('_');
        mkClassName(ret, intrinsicMethod.getEnclosingElement());
        ret.append('_');
        ret.append(intrinsicMethod.getSimpleName());
        if (!intrinsicMethod.getParameters().isEmpty()) {
            ret.append('_');
            ret.append(Integer.toHexString(APHotSpotSignature.toSignature(intrinsicMethod).hashCode()));
        }
        return ret.toString();
    }

    void createPluginFactory(ExecutableElement intrinsicMethod, ExecutableElement targetMethod, TypeMirror[] constructorSignature) {
        Element declaringClass = intrinsicMethod.getEnclosingElement();
        PackageElement pkg = getPackage(declaringClass);

        String genClassName = mkFactoryClassName(intrinsicMethod);

        try {
            JavaFileObject factory = env.getFiler().createSourceFile(pkg.getQualifiedName() + "." + genClassName, intrinsicMethod);
            try (PrintWriter out = new PrintWriter(factory.openWriter())) {
                out.printf("// CheckStyle: stop header check\n");
                out.printf("// CheckStyle: stop line length check\n");
                out.printf("// GENERATED CONTENT - DO NOT EDIT\n");
                out.printf("package %s;\n", pkg.getQualifiedName());
                out.printf("\n");
                createImports(out, intrinsicMethod, targetMethod);
                out.printf("\n");
                out.printf("@ServiceProvider(NodeIntrinsicPluginFactory.class)\n");
                out.printf("public class %s implements NodeIntrinsicPluginFactory {\n", genClassName);
                out.printf("\n");
                out.printf("    private static final class Plugin extends GeneratedInvocationPlugin {\n");
                out.printf("\n");

                out.printf("        @Override\n");
                out.printf("        public boolean execute(GraphBuilderContext b, ResolvedJavaMethod targetMethod, InvocationPlugin.Receiver receiver, ValueNode[] args) {\n");
                out.printf("            if (!b.parsingIntrinsic()) {\n");
                out.printf("                return false;\n");
                out.printf("            }\n");
                InjectedDependencies deps = createExecute(out, intrinsicMethod, targetMethod, constructorSignature);
                out.printf("        }\n");

                createPrivateMembers(out, intrinsicMethod, deps);

                out.printf("    }\n");
                out.printf("\n");
                createPluginFactoryMethod(out, intrinsicMethod, deps);
                out.printf("}\n");
            }
        } catch (IOException e) {
            env.getMessager().printMessage(Diagnostic.Kind.ERROR, e.getMessage());
        }
    }

    protected abstract InjectedDependencies createExecute(PrintWriter out, ExecutableElement intrinsicMethod, ExecutableElement constructor, TypeMirror[] signature);

    protected void createImports(PrintWriter out, @SuppressWarnings("unused") ExecutableElement intrinsicMethod, @SuppressWarnings("unused") ExecutableElement targetMethod) {
        out.printf("import jdk.vm.ci.meta.ResolvedJavaMethod;\n");
        out.printf("import jdk.vm.ci.service.ServiceProvider;\n");
        out.printf("\n");
        out.printf("import com.oracle.graal.nodes.ValueNode;\n");
        out.printf("import com.oracle.graal.nodes.graphbuilderconf.GraphBuilderContext;\n");
        out.printf("import com.oracle.graal.nodes.graphbuilderconf.GeneratedInvocationPlugin;\n");
        out.printf("import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugin;\n");
        out.printf("import com.oracle.graal.nodes.graphbuilderconf.InvocationPlugins;\n");
        out.printf("import com.oracle.graal.nodes.graphbuilderconf.NodeIntrinsicPluginFactory;\n");
    }

    private static void createPrivateMembers(PrintWriter out, ExecutableElement intrinsicMethod, InjectedDependencies deps) {
        if (!deps.isEmpty()) {
            out.printf("\n");
            for (Dependency dep : deps) {
                out.printf("        private final %s %s;\n", dep.type, dep.name);
            }

            out.printf("\n");
            out.printf("        private Plugin(InjectionProvider injection) {\n");
            for (Dependency dep : deps) {
                out.printf("            this.%s = %s;\n", dep.name, dep.inject(intrinsicMethod));
            }
            out.printf("        }\n");
        }
    }

    private static void createPluginFactoryMethod(PrintWriter out, ExecutableElement intrinsicMethod, InjectedDependencies deps) {
        out.printf("    public void registerPlugin(InvocationPlugins plugins, InjectionProvider injection) {\n");
        out.printf("        Plugin plugin = new Plugin(%s);\n", deps.isEmpty() ? "" : "injection");
        out.printf("        plugins.register(plugin, %s.class, \"%s\"", intrinsicMethod.getEnclosingElement(), intrinsicMethod.getSimpleName());
        for (VariableElement arg : intrinsicMethod.getParameters()) {
            out.printf(", %s.class", getErasedType(arg.asType()));
        }
        out.printf(");\n");
        out.printf("    }\n");
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

    protected void constantArgument(PrintWriter out, InjectedDependencies deps, int argIdx, TypeMirror type, int nodeIdx) {
        out.printf("            %s _arg%d;\n", type, argIdx);
        out.printf("            if (args[%d].isConstant()) {\n", nodeIdx);
        if (type.equals(resolvedJavaTypeType())) {
            out.printf("                _arg%d = %s.asJavaType(args[%d].asConstant());\n", argIdx, deps.use(WellKnownDependency.CONSTANT_REFLECTION), nodeIdx);
        } else {
            switch (type.getKind()) {
                case BOOLEAN:
                    out.printf("                _arg%d = args[%d].asJavaConstant().asInt() != 0;\n", argIdx, nodeIdx);
                    break;
                case BYTE:
                    out.printf("                _arg%d = (byte) args[%d].asJavaConstant().asInt();\n", argIdx, nodeIdx);
                    break;
                case CHAR:
                    out.printf("                _arg%d = (char) args[%d].asJavaConstant().asInt();\n", argIdx, nodeIdx);
                    break;
                case SHORT:
                    out.printf("                _arg%d = (short) args[%d].asJavaConstant().asInt();\n", argIdx, nodeIdx);
                    break;
                case INT:
                    out.printf("                _arg%d = args[%d].asJavaConstant().asInt();\n", argIdx, nodeIdx);
                    break;
                case LONG:
                    out.printf("                _arg%d = args[%d].asJavaConstant().asLong();\n", argIdx, nodeIdx);
                    break;
                case FLOAT:
                    out.printf("                _arg%d = args[%d].asJavaConstant().asFloat();\n", argIdx, nodeIdx);
                    break;
                case DOUBLE:
                    out.printf("                _arg%d = args[%d].asJavaConstant().asDouble();\n", argIdx, nodeIdx);
                    break;
                case DECLARED:
                    out.printf("                _arg%d = %s.asObject(%s.class, args[%d].asJavaConstant());\n", argIdx, deps.use(WellKnownDependency.SNIPPET_REFLECTION), type, nodeIdx);
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
