/*
 * Copyright (c) 2015, 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.replacements.processor;

import static org.graalvm.compiler.replacements.processor.FoldHandler.FOLD_CLASS_NAME;
import static org.graalvm.compiler.replacements.processor.FoldHandler.INJECTED_PARAMETER_CLASS_NAME;

import java.io.PrintWriter;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

import org.graalvm.compiler.processor.AbstractProcessor;
import org.graalvm.compiler.replacements.processor.InjectedDependencies.WellKnownDependency;

/**
 * Create graph builder plugins for {@code Fold} methods.
 */
public class GeneratedFoldPlugin extends GeneratedPlugin {

    public GeneratedFoldPlugin(ExecutableElement intrinsicMethod) {
        super(intrinsicMethod);
    }

    @Override
    protected TypeElement getAnnotationClass(AbstractProcessor processor) {
        return processor.getTypeElement(FOLD_CLASS_NAME);
    }

    @Override
    protected String pluginSuperclass() {
        return "GeneratedFoldInvocationPlugin";
    }

    @Override
    public void extraImports(AbstractProcessor processor, Set<String> imports) {
        imports.add("jdk.vm.ci.meta.JavaConstant");
        imports.add("jdk.vm.ci.meta.JavaKind");
        imports.add("org.graalvm.compiler.nodes.ConstantNode");
        imports.add("org.graalvm.compiler.core.common.type.Stamp");
    }

    @Override
    protected void createExecute(AbstractProcessor processor, PrintWriter out, InjectedDependencies deps) {
        List<? extends VariableElement> params = intrinsicMethod.getParameters();

        out.printf("        if (b.shouldDeferPlugin(this)) {\n");
        out.printf("            b.replacePlugin%s(this, targetMethod, args, %s.FUNCTION);\n", getReplacementFunctionSuffix(processor), getReplacementName());
        out.printf("            return true;\n");
        out.printf("        }\n");

        int argCount = 0;
        Object receiver;
        if (intrinsicMethod.getModifiers().contains(Modifier.STATIC)) {
            receiver = intrinsicMethod.getEnclosingElement();
        } else {
            receiver = "arg0";
            TypeElement type = (TypeElement) intrinsicMethod.getEnclosingElement();
            constantArgument(processor, out, deps, argCount, type.asType(), argCount, false);
            argCount++;
        }

        int firstArg = argCount;
        for (VariableElement param : params) {
            if (processor.getAnnotation(param, processor.getType(INJECTED_PARAMETER_CLASS_NAME)) == null) {
                constantArgument(processor, out, deps, argCount, param.asType(), argCount, false);
            } else {
                out.printf("        if (!checkInjectedArgument(b, args[%d], targetMethod)) {\n", argCount);
                out.printf("            return false;\n");
                out.printf("        }\n");
                out.printf("        %s arg%d = %s;\n", param.asType(), argCount, deps.use(processor, (DeclaredType) param.asType()));
            }
            argCount++;
        }

        Set<String> suppressWarnings = new TreeSet<>();
        if (intrinsicMethod.getAnnotation(Deprecated.class) != null) {
            suppressWarnings.add("deprecation");
        }
        if (hasRawtypeWarning(intrinsicMethod.getReturnType())) {
            suppressWarnings.add("rawtypes");
        }
        for (VariableElement param : params) {
            if (hasUncheckedWarning(param.asType())) {
                suppressWarnings.add("unchecked");
            }
        }
        if (suppressWarnings.size() > 0) {
            out.printf("        @SuppressWarnings({");
            String sep = "";
            for (String suppressWarning : suppressWarnings) {
                out.printf("%s\"%s\"", sep, suppressWarning);
                sep = ", ";
            }
            out.printf("})\n");
        }

        out.printf("        %s result = %s.%s(", getErasedType(intrinsicMethod.getReturnType()), receiver, intrinsicMethod.getSimpleName());
        if (argCount > firstArg) {
            out.printf("arg%d", firstArg);
            for (int i = firstArg + 1; i < argCount; i++) {
                out.printf(", arg%d", i);
            }
        }
        out.printf(");\n");

        TypeMirror returnType = intrinsicMethod.getReturnType();
        switch (returnType.getKind()) {
            case BOOLEAN:
                out.printf("        JavaConstant constant = JavaConstant.forInt(result ? 1 : 0);\n");
                break;
            case BYTE:
            case SHORT:
            case CHAR:
            case INT:
                out.printf("        JavaConstant constant = JavaConstant.forInt(result);\n");
                break;
            case LONG:
                out.printf("        JavaConstant constant = JavaConstant.forLong(result);\n");
                break;
            case FLOAT:
                out.printf("        JavaConstant constant = JavaConstant.forFloat(result);\n");
                break;
            case DOUBLE:
                out.printf("        JavaConstant constant = JavaConstant.forDouble(result);\n");
                break;
            case ARRAY:
            case TYPEVAR:
            case DECLARED:
                if (returnType.equals(processor.getType("java.lang.String"))) {
                    out.printf("        JavaConstant constant = %s.forString(result);\n", deps.use(processor, WellKnownDependency.CONSTANT_REFLECTION));
                } else {
                    out.printf("        JavaConstant constant = %s.forObject(result);\n", deps.use(processor, WellKnownDependency.SNIPPET_REFLECTION));
                }
                break;
            default:
                throw new IllegalArgumentException(returnType.toString());
        }

        out.printf("        ConstantNode node = ConstantNode.forConstant(constant, %s, %s);\n", deps.use(processor, WellKnownDependency.META_ACCESS),
                        deps.use(processor, WellKnownDependency.STRUCTURED_GRAPH));
        out.printf("        b.push(JavaKind.%s, node);\n", getReturnKind(intrinsicMethod));
        out.printf("        return true;\n");
    }

    @Override
    protected void createHelpers(AbstractProcessor processor, PrintWriter out, InjectedDependencies deps) {
        out.printf("\n");
        out.printf("    @Override\n");
        out.printf("    public boolean replace(GraphBuilderContext b, GeneratedPluginInjectionProvider injection, Stamp stamp, NodeInputList<ValueNode> args) {\n");

        List<? extends VariableElement> params = intrinsicMethod.getParameters();

        int argCount = 0;
        Object receiver;
        if (intrinsicMethod.getModifiers().contains(Modifier.STATIC)) {
            receiver = intrinsicMethod.getEnclosingElement();
        } else {
            receiver = "arg0";
            TypeElement type = (TypeElement) intrinsicMethod.getEnclosingElement();
            constantArgument(processor, out, deps, argCount, type.asType(), argCount, true);
            argCount++;
        }

        int firstArg = argCount;
        for (VariableElement param : params) {
            if (processor.getAnnotation(param, processor.getType(INJECTED_PARAMETER_CLASS_NAME)) == null) {
                constantArgument(processor, out, deps, argCount, param.asType(), argCount, true);
            } else {
                out.printf("        assert args.get(%d).isNullConstant();\n", argCount);
                out.printf("        %s arg%d = %s;\n", param.asType(), argCount, deps.find(processor, (DeclaredType) param.asType()).getExpression(processor, intrinsicMethod));
            }
            argCount++;
        }

        Set<String> suppressWarnings = new TreeSet<>();
        if (intrinsicMethod.getAnnotation(Deprecated.class) != null) {
            suppressWarnings.add("deprecation");
        }
        if (hasRawtypeWarning(intrinsicMethod.getReturnType())) {
            suppressWarnings.add("rawtypes");
        }
        for (VariableElement param : params) {
            if (hasUncheckedWarning(param.asType())) {
                suppressWarnings.add("unchecked");
            }
        }
        if (suppressWarnings.size() > 0) {
            out.printf("        @SuppressWarnings({");
            String sep = "";
            for (String suppressWarning : suppressWarnings) {
                out.printf("%s\"%s\"", sep, suppressWarning);
                sep = ", ";
            }
            out.printf("})\n");
        }

        out.printf("        %s result = %s.%s(", getErasedType(intrinsicMethod.getReturnType()), receiver, intrinsicMethod.getSimpleName());
        if (argCount > firstArg) {
            out.printf("arg%d", firstArg);
            for (int i = firstArg + 1; i < argCount; i++) {
                out.printf(", arg%d", i);
            }
        }
        out.printf(");\n");

        TypeMirror returnType = intrinsicMethod.getReturnType();
        switch (returnType.getKind()) {
            case BOOLEAN:
                out.printf("        JavaConstant constant = JavaConstant.forInt(result ? 1 : 0);\n");
                break;
            case BYTE:
            case SHORT:
            case CHAR:
            case INT:
                out.printf("        JavaConstant constant = JavaConstant.forInt(result);\n");
                break;
            case LONG:
                out.printf("        JavaConstant constant = JavaConstant.forLong(result);\n");
                break;
            case FLOAT:
                out.printf("        JavaConstant constant = JavaConstant.forFloat(result);\n");
                break;
            case DOUBLE:
                out.printf("        JavaConstant constant = JavaConstant.forDouble(result);\n");
                break;
            case ARRAY:
            case TYPEVAR:
            case DECLARED:
                if (returnType.equals(processor.getType("java.lang.String"))) {
                    out.printf("        JavaConstant constant = %s.forString(result);\n", deps.use(processor, WellKnownDependency.CONSTANT_REFLECTION));
                } else {
                    out.printf("        JavaConstant constant = %s.forObject(result);\n", deps.use(processor, WellKnownDependency.SNIPPET_REFLECTION));
                }
                break;
            default:
                throw new IllegalArgumentException(returnType.toString());
        }

        out.printf("        ConstantNode node = ConstantNode.forConstant(constant, %s, %s);\n", deps.use(processor, WellKnownDependency.META_ACCESS),
                        deps.use(processor, WellKnownDependency.STRUCTURED_GRAPH));
        out.printf("        b.push(JavaKind.%s, node);\n", getReturnKind(intrinsicMethod));
        out.printf("        return true;\n");
        out.printf("    }\n");
    }
}
