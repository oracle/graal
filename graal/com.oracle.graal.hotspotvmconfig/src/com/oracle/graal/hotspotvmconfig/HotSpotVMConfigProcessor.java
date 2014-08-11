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
package com.oracle.graal.hotspotvmconfig;

import java.io.*;
import java.lang.annotation.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.*;

import javax.annotation.processing.*;
import javax.lang.model.*;
import javax.lang.model.element.*;
import javax.tools.Diagnostic.Kind;
import javax.tools.*;

import com.oracle.graal.compiler.common.*;

@SupportedAnnotationTypes({"com.oracle.graal.hotspotvmconfig.HotSpotVMConstant", "com.oracle.graal.hotspotvmconfig.HotSpotVMFlag", "com.oracle.graal.hotspotvmconfig.HotSpotVMField",
                "com.oracle.graal.hotspotvmconfig.HotSpotVMType", "com.oracle.graal.hotspotvmconfig.HotSpotVMValue"})
public class HotSpotVMConfigProcessor extends AbstractProcessor {

    public HotSpotVMConfigProcessor() {
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    /**
     * Set to true to enable logging to a local file during annotation processing. There's no normal
     * channel for any debug messages and debugging annotation processors requires some special
     * setup.
     */
    private static final boolean DEBUG = false;

    private PrintWriter log;

    /**
     * Logging facility for debugging the annotation processor.
     */

    private PrintWriter getLog() {
        if (log == null) {
            try {
                // Create the log file within the generated source directory so it's easy to find.
                // /tmp isn't platform independent and java.io.tmpdir can map anywhere, particularly
                // on the mac.
                FileObject file = processingEnv.getFiler().createResource(StandardLocation.SOURCE_OUTPUT, "", getClass().getSimpleName() + "log");
                log = new PrintWriter(new FileWriter(file.toUri().getPath(), true));
            } catch (IOException e) {
                // Do nothing
            }
        }
        return log;
    }

    private void logMessage(String format, Object... args) {
        if (!DEBUG) {
            return;
        }
        PrintWriter bw = getLog();
        if (bw != null) {
            bw.printf(format, args);
            bw.flush();
        }
    }

    private void logException(Throwable t) {
        if (!DEBUG) {
            return;
        }
        PrintWriter bw = getLog();
        if (bw != null) {
            t.printStackTrace(bw);
            bw.flush();
        }
    }

    /**
     * Bugs in an annotation processor can cause silent failure so try to report any exception
     * throws as errors.
     */
    private void reportExceptionThrow(Element element, Throwable t) {
        if (element != null) {
            logMessage("throw for %s:\n", element);
        }
        logException(t);
        errorMessage(element, "Exception throw during processing: %s %s", t, Arrays.toString(Arrays.copyOf(t.getStackTrace(), 4)));
    }

    //@formatter:off
    String[] prologue = new String[]{
        "// The normal wrappers CommandLineFlags::boolAt and CommandLineFlags::intxAt skip constant flags",
        "static bool boolAt(char* name, bool* value) {",
        "  Flag* result = Flag::find_flag(name, strlen(name), true, true);",
        "  if (result == NULL) return false;",
        "  if (!result->is_bool()) return false;",
        "  *value = result->get_bool();",
        "  return true;",
        "}",
        "",
        "static bool intxAt(char* name, intx* value) {",
        "  Flag* result = Flag::find_flag(name, strlen(name), true, true);",
        "  if (result == NULL) return false;",
        "  if (!result->is_intx()) return false;",
        "  *value = result->get_intx();",
        "  return true;",
        "}",
        "",
        "#define set_boolean(name, value) vmconfig_oop->bool_field_put(fs.offset(), value)",
        "#define set_byte(name, value) vmconfig_oop->byte_field_put(fs.offset(), (jbyte)value)",
        "#define set_short(name, value) vmconfig_oop->short_field_put(fs.offset(), (jshort)value)",
        "#define set_int(name, value) vmconfig_oop->int_field_put(fs.offset(), (int)value)",
        "#define set_long(name, value) vmconfig_oop->long_field_put(fs.offset(), value)",
        "#define set_address(name, value) do { set_long(name, (jlong) value); } while (0)",
        "",
        "#define set_optional_boolean_flag(varName, flagName) do { bool flagValue; if (boolAt((char*) flagName, &flagValue)) { set_boolean(varName, flagValue); } } while (0)",
        "#define set_optional_int_flag(varName, flagName) do { intx flagValue; if (intxAt((char*) flagName, &flagValue)) { set_int(varName, flagValue); } } while (0)",
        "#define set_optional_long_flag(varName, flagName) do { intx flagValue; if (intxAt((char*) flagName, &flagValue)) { set_long(varName, flagValue); } } while (0)",
        "",
        "void VMStructs::initHotSpotVMConfig(oop vmconfig_oop) {",
        "  InstanceKlass* vmconfig_klass = InstanceKlass::cast(vmconfig_oop->klass());",
        "",
    };
    //@formatter:on

    String outputName = "HotSpotVMConfig.inline.hpp";
    String outputDirectory = "hotspot";

    private void createFiles(Map<String, VMConfigField> annotations, Element element) {

        Filer filer = processingEnv.getFiler();
        try (PrintWriter out = createSourceFile(outputDirectory, outputName, filer, element)) {

            for (String line : prologue) {
                out.println(line);
            }

            Map<String, Integer> expectedValues = new HashMap<>();
            for (VMConfigField value : annotations.values()) {
                if (!value.optional) {
                    String key = value.define != null ? value.define : "";
                    if (expectedValues.get(key) == null) {
                        expectedValues.put(key, 1);
                    } else {
                        expectedValues.put(key, expectedValues.get(key) + 1);
                    }
                }
            }

            out.printf("  int expected = %s;%n", expectedValues.get(""));
            for (Entry<String, Integer> entry : expectedValues.entrySet()) {
                if (entry.getKey().equals("")) {
                    continue;
                }
                out.printf("#if %s%n", entry.getKey());
                out.printf("  expected += %s;%n", entry.getValue());
                out.printf("#endif%n");
            }
            out.println("  int assigned = 0;");
            out.println("  for (JavaFieldStream fs(vmconfig_klass); !fs.done(); fs.next()) {");

            Set<String> fieldTypes = new HashSet<>();
            for (VMConfigField key : annotations.values()) {
                fieldTypes.add(key.getType());
            }
            // For each type of field, generate a switch on the length of the symbol and then do a
            // direct compare. In general this reduces each operation to 2 tests plus a string
            // compare. Being more perfect than that is probably not worth it.
            for (String type : fieldTypes) {
                String sigtype = type.equals("boolean") ? "bool" : type;
                out.println("    if (fs.signature() == vmSymbols::" + sigtype + "_signature()) {");
                Set<Integer> lengths = new HashSet<>();
                for (Entry<String, VMConfigField> entry : annotations.entrySet()) {
                    if (entry.getValue().getType().equals(type)) {
                        lengths.add(entry.getKey().length());
                    }
                }
                out.println("      switch (fs.name()->utf8_length()) {");
                for (int len : lengths) {
                    out.println("        case " + len + ":");
                    for (Entry<String, VMConfigField> entry : annotations.entrySet()) {
                        if (entry.getValue().getType().equals(type) && entry.getKey().length() == len) {
                            out.println("          if (fs.name()->equals(\"" + entry.getKey() + "\")) {");
                            entry.getValue().emit(out);
                            out.println("            continue;");
                            out.println("          }");
                        }
                    }
                    out.println("          continue;");
                }
                out.println("      } // switch");
                out.println("      continue;");
                out.println("    } // if");
            }
            out.println("  } // for");
            out.println("  guarantee(assigned == expected, \"Didn't find all fields during init of HotSpotVMConfig.  Maybe recompile?\");");
            out.println("}");
        }
    }

    protected PrintWriter createSourceFile(String pkg, String relativeName, Filer filer, Element... originatingElements) {
        try {
            // Ensure Unix line endings to comply with Graal code style guide checked by Checkstyle
            FileObject sourceFile = filer.createResource(StandardLocation.SOURCE_OUTPUT, pkg, relativeName, originatingElements);
            logMessage("%s\n", sourceFile);
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

    static class VMConfigField {
        final String setter;
        final String define;
        private boolean optional;
        final VariableElement field;

        public VMConfigField(VariableElement field, HotSpotVMField value) {
            this.field = field;
            define = archDefines(value.archs());
            String type = field.asType().toString();
            String name = value.name();
            int i = name.lastIndexOf("::");
            switch (value.get()) {
                case OFFSET:
                    setter = String.format("set_%s(\"%s\", offset_of(%s, %s));", type, field.getSimpleName(), name.substring(0, i), name.substring(i + 2));
                    break;
                case ADDRESS:
                    setter = String.format("set_address(\"%s\", &%s);", field.getSimpleName(), name);
                    break;
                case VALUE:
                    setter = String.format("set_%s(\"%s\", (%s) (intptr_t) %s);", type, field.getSimpleName(), type, name);
                    break;
                default:
                    throw new GraalInternalError("unexpected type: " + value.get());
            }
        }

        public VMConfigField(VariableElement field, HotSpotVMType value) {
            this.field = field;
            define = null; // ((HotSpotVMType) annotation).archs();
            String type = field.asType().toString();
            setter = String.format("set_%s(\"%s\", sizeof(%s));", type, field.getSimpleName(), value.name());
        }

        public VMConfigField(VariableElement field, HotSpotVMValue value) {
            this.field = field;
            String[] defines = value.defines();
            int length = defines.length;
            if (length != 0) {
                for (int i = 0; i < length; i++) {
                    defines[i] = "defined(" + defines[i] + ")";
                }
                define = String.join(" || ", defines);
            } else {
                define = null; // ((HotSpotVMValue) annotation).archs();
            }
            String type = field.asType().toString();
            if (value.get() == HotSpotVMValue.Type.ADDRESS) {
                setter = String.format("set_address(\"%s\", %s);", field.getSimpleName(), value.expression());
            } else {
                setter = String.format("set_%s(\"%s\", %s);", type, field.getSimpleName(), value.expression());
            }
        }

        public VMConfigField(VariableElement field, HotSpotVMConstant value) {
            this.field = field;
            define = archDefines(value.archs());
            String type = field.asType().toString();
            setter = String.format("set_%s(\"%s\", %s);", type, field.getSimpleName(), value.name());
        }

        public VMConfigField(VariableElement field, HotSpotVMFlag value) {
            this.field = field;
            define = archDefines(value.archs());
            optional = value.optional();
            String type = field.asType().toString();
            if (value.optional()) {
                setter = String.format("set_optional_%s_flag(\"%s\",  \"%s\");", type, field.getSimpleName(), value.name());
            } else {
                setter = String.format("set_%s(\"%s\", %s);", type, field.getSimpleName(), value.name());
            }
        }

        public String getType() {
            return field.asType().toString();
        }

        private static String archDefine(String arch) {
            switch (arch) {
                case "amd64":
                    return "defined(AMD64)";
                case "sparcv9":
                    return "(defined(SPARC) && defined(_LP64))";
                case "sparc":
                    return "defined(SPARC)";
                default:
                    throw new GraalInternalError("unexpected arch: " + arch);
            }
        }

        private static String archDefines(String[] archs) {
            if (archs == null || archs.length == 0) {
                return null;
            }
            if (archs.length == 1) {
                return archDefine(archs[0]);
            }
            String[] defs = new String[archs.length];
            int i = 0;
            for (String arch : archs) {
                defs[i++] = archDefine(arch);
            }
            return String.join(" || ", defs);
        }

        public void emit(PrintWriter out) {
            if (define != null) {
                out.printf("#if %s\n", define);
            }
            out.printf("            %s%n", setter);
            if (!optional) {
                out.printf("            assigned++;%n");
            }
            if (define != null) {
                out.printf("#endif\n");
            }
        }

    }

    @SuppressWarnings("unchecked")
    private <T extends Annotation> void collectAnnotations(RoundEnvironment roundEnv, Map<String, VMConfigField> annotationMap, Class<T> annotationClass,
                    BiFunction<VariableElement, T, VMConfigField> builder) {
        for (Element element : roundEnv.getElementsAnnotatedWith(annotationClass)) {
            Annotation constant = element.getAnnotation(annotationClass);
            if (element.getKind() != ElementKind.FIELD) {
                errorMessage(element, "%s annotations may only be on fields", annotationClass.getSimpleName());
            }
            if (annotationClass == HotSpotVMValue.class) {
                HotSpotVMValue value = (HotSpotVMValue) constant;
                if (value.get() == HotSpotVMValue.Type.ADDRESS && !element.asType().toString().equals("long")) {
                    errorMessage(element, "HotSpotVMValue with get == ADDRESS must be of type long, but found %s", element.asType());
                }
            }
            if (currentTypeElement == null) {
                currentTypeElement = element.getEnclosingElement();
            } else {
                if (!currentTypeElement.equals(element.getEnclosingElement())) {
                    errorMessage(element, "Multiple types encountered.  Only HotSpotVMConfig is supported");
                }
            }
            annotationMap.put(element.getSimpleName().toString(), builder.apply((VariableElement) element, (T) constant));
        }
    }

    private void errorMessage(Element element, String format, Object... args) {
        processingEnv.getMessager().printMessage(Kind.ERROR, String.format(format, args), element);
    }

    Element currentTypeElement = null;

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return true;
        }
        logMessage("Starting round %s %s\n", roundEnv, annotations);
        try {

            currentTypeElement = null;

            // First collect all the annotations.
            Map<String, VMConfigField> annotationMap = new HashMap<>();
            collectAnnotations(roundEnv, annotationMap, HotSpotVMConstant.class, (e, v) -> new VMConfigField(e, v));
            collectAnnotations(roundEnv, annotationMap, HotSpotVMFlag.class, (e, v) -> new VMConfigField(e, v));
            collectAnnotations(roundEnv, annotationMap, HotSpotVMField.class, (e, v) -> new VMConfigField(e, v));
            collectAnnotations(roundEnv, annotationMap, HotSpotVMType.class, (e, v) -> new VMConfigField(e, v));
            collectAnnotations(roundEnv, annotationMap, HotSpotVMValue.class, (e, v) -> new VMConfigField(e, v));

            if (annotationMap.isEmpty()) {
                return true;
            }

            logMessage("type element %s\n", currentTypeElement);
            createFiles(annotationMap, currentTypeElement);

        } catch (Throwable t) {
            reportExceptionThrow(null, t);
        }

        return true;
    }
}
