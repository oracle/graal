/*
 * Copyright (c) 2016, Oracle and/or its affiliates.
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are
 * permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of
 * conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided
 * with the distribution.
 *
 * 3. Neither the name of the copyright holder nor the names of its contributors may be used to
 * endorse or promote products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS
 * OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF
 * MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL,
 * EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE
 * GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING
 * NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.oracle.truffle.llvm.option.processor;

import java.io.IOException;
import java.io.Writer;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.JavaFileObject;

import com.oracle.truffle.llvm.option.Constants;
import com.oracle.truffle.llvm.option.Option;
import com.oracle.truffle.llvm.option.OptionCategory;

public final class OptionsClassGenerator {

    private final ProcessingEnvironment processingEnv;
    private final TypeElement optionClassElement;
    private final List<VariableElement> optionElements;
    private final String packageName;
    private final String clazzName;
    private final Set<Consumer<Writer>> optionInitializer = new HashSet<>();

    OptionsClassGenerator(ProcessingEnvironment processingEnv, TypeElement optionClassElement, List<VariableElement> optionElements) {
        this.processingEnv = processingEnv;
        this.optionClassElement = optionClassElement;
        this.optionElements = optionElements;
        this.clazzName = Utils.getSimpleSubClassName(optionClassElement);
        this.packageName = Utils.getPackageName(optionClassElement);
    }

    public void generate() throws IOException {
        JavaFileObject file = processingEnv.getFiler().createSourceFile(Utils.getFullOptionsClassName(optionClassElement), optionClassElement);
        Writer w = file.openWriter();
        w.append("package ").append(packageName).append(";\n");
        appendImports(w);

        w.append("public final class ").append(clazzName).append(" extends ").append(Utils.getSimpleName(optionClassElement)).append(" {\n");

        appendStaticConstructor(w);
        appendOptionFields(w);
        appendConstructor(w);
        appendFactoryMethod(w);
        appendOptionGetters(w);

        for (Consumer<Writer> initializer : optionInitializer) {
            initializer.accept(w);
        }

        w.append("}\n");
        w.close();
    }

    private void appendFactoryMethod(Writer w) throws IOException {
        w.append("  public static ").append(clazzName).append(" create() {\n");
        w.append("    return new ").append(clazzName).append("();\n");
        w.append("  }\n");
    }

    private void appendStaticConstructor(Writer w) throws IOException {
        w.append("  static {\n");
        for (VariableElement e : optionElements) {
            w.append("    OptionSummary.registerOption(\"").append(getCategory()).append("\", \"").append(getNameOfOption(e)).append("\", \"").append(getCommandLineOption(e)).append("\", ").append(
                            getDefaultValueAsString(e)).append(", \"").append(
                                            getHelpOfOption(e)).append("\");\n");
        }
        w.append("  }\n");
    }

    private static String getDefaultValueAsString(VariableElement e) {
        if (Utils.isStringArr(e.asType())) {
            return "java.util.Arrays.toString(" + e.getSimpleName().toString() + ")";
        } else {
            return "String.valueOf(" + e.getSimpleName().toString() + ")";
        }
    }

    private String getCategory() {
        return optionClassElement.getAnnotation(OptionCategory.class).name();
    }

    private static String getNameOfOption(VariableElement optionElement) {
        Option annotation = optionElement.getAnnotation(Option.class);
        if (annotation.name().equals("")) {
            return optionElement.getSimpleName().toString();
        } else {
            return annotation.name();
        }
    }

    private static String getCommandLineOption(VariableElement optionElement) {
        Option annotation = optionElement.getAnnotation(Option.class);
        String commandLineOption = Constants.OPTION_PREFIX + annotation.commandLineName();
        return commandLineOption;
    }

    private static String getHelpOfOption(VariableElement optionElement) {
        Option annotation = optionElement.getAnnotation(Option.class);
        return annotation.help();
    }

    private static void appendImports(Writer w) throws IOException {
        w.append("import com.oracle.truffle.llvm.option.OptionSummary;\n");

    }

    private void appendOptionFields(Writer w) throws IOException {
        for (VariableElement e : optionElements) {
            w.append("  private final ").append(getTypeOptionName(e)).append(" ").append(getInternalOptionName(e)).append(";\n");
        }
    }

    private void appendOptionGetters(Writer w) throws IOException {
        for (VariableElement e : optionElements) {
            w.append("  public ").append(getTypeOptionName(e)).append(" ").append(firstLetterLowerCase(getNameOfOption(e))).append("() {\n");
            w.append("    return ").append(getInternalOptionName(e)).append(";\n");
            w.append("  }\n");
        }
    }

    private static String firstLetterLowerCase(String name) {
        return name.substring(0, 1).toLowerCase() + name.substring(1);
    }

    private void appendConstructor(Writer w) throws IOException {
        w.append("  private ").append(clazzName).append("() {\n");
        for (VariableElement e : optionElements) {
            w.append("    ").append(getInternalOptionName(e)).append(" = ").append(optionInitializer(e)).append(";\n");
        }
        w.append("  }\n");
    }

    private static void appendGetBooleanOption(Writer w) throws IOException {
        w.append("  private static boolean getBooleanOption(String option, boolean defaultValue) {\n");
        w.append("    if (System.getProperty(option) == null) {\n");
        w.append("      return defaultValue;\n");
        w.append("    } else {\n");
        w.append("      return Boolean.getBoolean(option);\n");
        w.append("    }\n");
        w.append("  }\n\n");
    }

    private static void appendGetIntegerOption(Writer w) throws IOException {
        w.append("  private static int getIntegerOption(String option, int defaultValue) {\n");
        w.append("    if (System.getProperty(option) == null) {\n");
        w.append("      return defaultValue;\n");
        w.append("    } else {\n");
        w.append("      return Integer.getInteger(option);\n");
        w.append("    }\n");
        w.append("  }\n\n");
    }

    private static void appendGetStringOption(Writer w) throws IOException {
        w.append("  private static String getStringOption(String option, String defaultValue) {\n");
        w.append("    if (System.getProperty(option) == null) {\n");
        w.append("      return defaultValue;\n");
        w.append("    } else {\n");
        w.append("      return System.getProperty(option);\n");
        w.append("    }\n");
        w.append("  }\n\n");
    }

    private static void appendGetStringArrOption(Writer w) throws IOException {
        w.append("  private static String[] getStringArrOption(String option, String[] defaultValue) {\n");
        w.append("    if (System.getProperty(option) == null) {\n");
        w.append("      return defaultValue;\n");
        w.append("    } else {\n");
        w.append("      return System.getProperty(option).split(\"").append(Constants.OPTION_ARRAY_SEPARATOR).append("\");\n");
        w.append("    }\n");
        w.append("  }\n\n");
    }

    private static String getInternalOptionName(VariableElement option) {
        return "internal_" + getNameOfOption(option);
    }

    private String getTypeOptionName(VariableElement option) {
        if (Utils.isBoolean(processingEnv, option.asType())) {
            return "boolean";
        } else if (Utils.isInt(processingEnv, option.asType())) {
            return "int";
        } else if (Utils.isString(processingEnv, option.asType())) {
            return "String";
        } else if (Utils.isStringArr(option.asType())) {
            return "String[]";
        } else {
            throw new AssertionError();
        }
    }

    private String optionInitializer(VariableElement option) {
        if (Utils.isBoolean(processingEnv, option.asType())) {
            optionInitializer.add(t -> {
                try {
                    appendGetBooleanOption(t);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
            return "getBooleanOption(\"" + getCommandLineOption(option) + "\", " + option.getSimpleName().toString() + ")";
        } else if (Utils.isInt(processingEnv, option.asType())) {
            optionInitializer.add(t -> {
                try {
                    appendGetIntegerOption(t);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
            return "getIntegerOption(\"" + getCommandLineOption(option) + "\", " + option.getSimpleName().toString() + ")";
        } else if (Utils.isString(processingEnv, option.asType())) {
            optionInitializer.add(t -> {
                try {
                    appendGetStringOption(t);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
            return "getStringOption(\"" + getCommandLineOption(option) + "\", " + option.getSimpleName().toString() + ")";
        } else if (Utils.isStringArr(option.asType())) {
            optionInitializer.add(t -> {
                try {
                    appendGetStringArrOption(t);
                } catch (IOException e) {
                    throw new IllegalStateException(e);
                }
            });
            return "getStringArrOption(\"" + getCommandLineOption(option) + "\", " + option.getSimpleName().toString() + ")";
        } else {
            throw new AssertionError();
        }
    }

}
