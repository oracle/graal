/*
 * Copyright (c) 2018, 2020, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.compiler.truffle.common.processor;

import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.Filer;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic.Kind;

import org.graalvm.compiler.options.processor.OptionProcessor;
import org.graalvm.compiler.options.processor.OptionProcessor.OptionInfo;
import org.graalvm.compiler.options.processor.OptionProcessor.OptionsInfo;
import org.graalvm.compiler.processor.AbstractProcessor;

/**
 * Processor for {@value #SHARED_TRUFFLE_OPTIONS_CLASS_NAME} that generates a class declaring the
 * options specified by {@link Option#options}.
 */
@SupportedAnnotationTypes("org.graalvm.compiler.truffle.common.SharedTruffleOptions")
public class SharedTruffleOptionsProcessor extends AbstractProcessor {

    private static final String SHARED_TRUFFLE_OPTIONS_CLASS_NAME = "org.graalvm.compiler.truffle.common.SharedTruffleOptions";

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    public boolean doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return true;
        }

        TypeElement sharedTruffleOptionsTypeElement = getTypeElement(SHARED_TRUFFLE_OPTIONS_CLASS_NAME);
        TypeMirror sharedTruffleOptionsTypeMirror = sharedTruffleOptionsTypeElement.asType();

        for (Element element : roundEnv.getElementsAnnotatedWith(sharedTruffleOptionsTypeElement)) {
            Element pkgElement = element.getEnclosingElement();
            if (pkgElement.getKind() != ElementKind.PACKAGE) {
                processingEnv.getMessager().printMessage(Kind.ERROR, "Truffle options holder must be a top level class", element);
                break;
            }
            TypeElement topDeclaringType = (TypeElement) element;

            AnnotationMirror annotation = getAnnotation(topDeclaringType, sharedTruffleOptionsTypeMirror);
            Boolean isRuntime = getAnnotationValue(annotation, "runtime", Boolean.class);
            String className = getAnnotationValue(annotation, "name", String.class);
            String pkg = ((PackageElement) pkgElement).getQualifiedName().toString();
            String optionKeyClassName = isRuntime ? "org.graalvm.options.OptionKey" : "org.graalvm.compiler.options.OptionKey";

            OptionsInfo info = new OptionsInfo(pkg, className);
            info.originatingElements.add(topDeclaringType);
            Filer filer = processingEnv.getFiler();
            try (PrintWriter out = OptionProcessor.createSourceFile(pkg, className, filer, topDeclaringType)) {

                out.println("// CheckStyle: stop header check");
                out.println("// CheckStyle: stop line length check");
                out.println("package " + pkg + ";");
                out.println("");
                out.println("import " + optionKeyClassName + ";");
                out.println("import org.graalvm.compiler.truffle.options.PolyglotCompilerOptions;");
                if (isRuntime) {
                    out.println("import com.oracle.truffle.api.Option;");
                    out.println("import org.graalvm.options.OptionCategory;");
                    out.println("import org.graalvm.options.OptionType;");
                }
                out.println("");
                out.println("/**");
                out.println(" * Options shared between the Truffle runtime and compiler.");
                out.println(" *");
                out.println(" * To modify these options, edit " + Option.class.getName());
                out.println(" * and re-build. For Eclipse, this means restarting the IDE given");
                out.println(" * that an annotation processor is not reloaded when the jar");
                out.println(" * containing the annotation processor is updated.");
                out.println(" *");
                out.println(" * GENERATED CONTENT - DO NOT EDIT");
                out.println(" * GeneratedBy: " + getClass().getName());
                out.println(" * SpecifiedBy: " + Option.class.getName());
                out.println(" */");
                out.println("public abstract class " + className + " {");

                for (Option option : Option.options) {
                    String defaultValue;

                    if (isRuntime) {
                        if (option.deprecationMessage != null) {
                            out.printf("    /**\n");
                            out.printf("     * %s\n", option.deprecationMessage);
                            out.printf("     */\n");
                        }
                        String help;
                        if (option.help == null) {
                            help = "";
                        } else if (option.help.length > 1) {
                            // @formatter:off
                            help = option.help[0] + "%n" + Arrays.asList(option.help)
                                            .subList(1, option.help.length)
                                            .stream().map(s -> "%n\" +" + System.lineSeparator() + "                   \"" + s) //
                                            .collect(Collectors.joining());
                            // @formatter:on
                        } else {
                            help = option.help[0];
                        }
                        defaultValue = option.defaultValue + ", OptionType.defaultType(" + option.type + ".class)";
                        out.printf("    @Option(help = \"%s\", category = OptionCategory.%s)\n", help, option.category);
                    } else {
                        defaultValue = option.defaultValue;
                        String optionType;
                        if (option.category.equals("INTERNAL")) {
                            optionType = "Debug";
                        } else {
                            optionType = option.category.charAt(0) + option.category.substring(1).toLowerCase();
                        }
                        out.printf("    /**\n");
                        for (int i = 0; i < option.help.length; i++) {
                            String line = option.help[i];
                            if (i == option.help.length - 1) {
                                out.printf("     * %s%s\n", line, line.endsWith(".") ? "" : ".");
                                out.printf("     *\n");
                            } else {
                                out.printf("     * %s\n", line);
                            }
                        }
                        out.printf("     * OptionType: %s\n", optionType);

                        if (option.deprecationMessage != null) {
                            out.printf("     *\n");
                            out.printf("     * %s\n", option.deprecationMessage);
                        }
                        out.printf("     */\n");

                        String help = option.help[0];
                        List<String> extraHelp = option.help.length > 1 ? Arrays.asList(option.help).subList(1, option.help.length) : new ArrayList<>();
                        info.options.add(new OptionInfo(option.name, optionType, help, extraHelp, option.type, pkg + '.' + className, option.name, true));
                    }
                    out.printf("    static final OptionKey<%s> %s = new OptionKey<>(%s);\n", option.type, option.name, defaultValue);
                    out.println();
                }
                out.println("}");

                if (!info.options.isEmpty()) {
                    OptionProcessor.createOptionsDescriptorsFile(processingEnv, info);
                }
            }
        }
        return true;
    }
}
