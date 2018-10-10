/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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

import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
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
import javax.tools.JavaFileObject;

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

        TypeElement optionTypeElement = getTypeElement(SHARED_TRUFFLE_OPTIONS_CLASS_NAME);
        TypeMirror optionTypeMirror = optionTypeElement.asType();

        for (Element element : roundEnv.getElementsAnnotatedWith(getTypeElement(SHARED_TRUFFLE_OPTIONS_CLASS_NAME))) {
            Element pkgElement = element.getEnclosingElement();
            if (pkgElement.getKind() != ElementKind.PACKAGE) {
                processingEnv.getMessager().printMessage(Kind.ERROR, "Truffle options holder must be a top level class", element);
                break;
            }
            TypeElement topDeclaringType = (TypeElement) element;

            AnnotationMirror annotation = getAnnotation(topDeclaringType, optionTypeMirror);
            Boolean isRuntime = getAnnotationValue(annotation, "runtime", Boolean.class);
            String name = getAnnotationValue(annotation, "name", String.class);
            String pkg = ((PackageElement) pkgElement).getQualifiedName().toString();
            String optionKeyClassName;
            String optionClassName;
            String optionCategoryElementName;
            String optionCategoryClassName;
            Map<String, String> optionCategoryTranslation;
            if (isRuntime) {
                optionClassName = "com.oracle.truffle.api.Option";
                optionKeyClassName = "org.graalvm.options.OptionKey";
                optionCategoryElementName = "category";
                optionCategoryClassName = "org.graalvm.options.OptionCategory";
                optionCategoryTranslation = null;
            } else {
                optionClassName = "org.graalvm.compiler.options.Option";
                optionKeyClassName = "org.graalvm.compiler.options.OptionKey";
                optionCategoryElementName = "type";
                optionCategoryClassName = "org.graalvm.compiler.options.OptionType";
                optionCategoryTranslation = new HashMap<>();
                optionCategoryTranslation.put("DEBUG", "Debug");
                optionCategoryTranslation.put("USER", "User");
                optionCategoryTranslation.put("EXPERT", "Expert");
            }

            Filer filer = processingEnv.getFiler();
            try (PrintWriter out = createSourceFile(pkg, name, filer, topDeclaringType)) {

                out.println("// CheckStyle: stop header check");
                out.println("// CheckStyle: stop line length check");
                out.println("package " + pkg + ";");
                out.println("");
                out.println("import " + optionClassName + ";");
                out.println("import " + optionKeyClassName + ";");
                out.println("import " + optionCategoryClassName + ";");
                out.println("");
                out.println("/**");
                out.println(" * Options shared between the Truffle runtime and compiler.");
                out.println(" *");
                out.println(" * To modify these options, edit " + Option.class.getName());
                out.println(" * and re-build. For Eclipse, this means restarting the IDE given");
                out.println(" * that an annotation processor is not reloaded when the jar");
                out.println(" * containing the annotation processor is updated.");
                out.println(" */");
                out.println("// GENERATED CONTENT - DO NOT EDIT");
                out.println("// GeneratedBy: " + getClass().getName());
                out.println("// SpecifiedBy: " + Option.class.getName());
                out.println("public abstract class " + name + " {");

                for (Option option : Option.options) {
                    if (option.javadoc != null) {
                        out.printf("    /**\n");
                        for (String line : option.javadoc) {
                            out.printf("     * %s\n", line);
                        }
                        out.printf("     */\n");
                    }
                    String help;
                    if (option.help == null) {
                        help = "";
                    } else if (option.help.length > 1) {
                        // @formatter:off
                        help = option.help[0] + Arrays.asList(option.help)
                                        .subList(1, option.help.length)
                                        .stream().map(s -> "\" +" + System.lineSeparator() + "                   \"" + s) //
                                        .collect(Collectors.joining());
                        // @formatter:on
                    } else {
                        help = option.help[0];
                    }

                    String category = optionCategoryTranslation == null ? option.category : optionCategoryTranslation.get(option.category);
                    String defaultValue = option.defaultValue;
                    if (isRuntime && "null".equals(defaultValue)) {
                        defaultValue = "null, org.graalvm.options.OptionType.defaultType(" + option.type + ".class)";
                    }
                    out.printf("    @Option(help = \"%s\", %s = %s.%s)\n", help, optionCategoryElementName, getSimpleName(optionCategoryClassName), category);
                    out.printf("    public static final OptionKey<%s> %s = new OptionKey<>(%s);\n", option.type, option.name, defaultValue);
                    out.println();
                }
                out.println("}");
            }
        }
        return true;
    }

    protected PrintWriter createSourceFile(String pkg, String relativeName, Filer filer, Element... originatingElements) {
        try {
            // Ensure Unix line endings to comply with code style guide checked by Checkstyle
            JavaFileObject sourceFile = filer.createSourceFile(pkg + "." + relativeName, originatingElements);
            return new PrintWriter(sourceFile.openWriter()) {

                @Override
                public void println() {
                    print("\n");
                }
            };

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
