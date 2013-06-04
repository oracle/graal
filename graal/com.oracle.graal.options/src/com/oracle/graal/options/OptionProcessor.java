/*
 * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.options;

import java.io.*;
import java.lang.reflect.*;
import java.util.*;

import javax.annotation.processing.*;
import javax.lang.model.*;
import javax.lang.model.element.*;
import javax.lang.model.element.Modifier;
import javax.lang.model.type.*;
import javax.lang.model.util.*;
import javax.tools.Diagnostic.Kind;
import javax.tools.*;

/**
 * Processes static fields annotated with {@link Option}. An {@link OptionProvider} is generated for
 * each such field that can be accessed as a {@linkplain ServiceLoader service} as follows:
 * 
 * <pre>
 * ServiceLoader&lt;OptionProvider&gt; sl = ServiceLoader.loadInstalled(OptionProvider.class);
 * for (OptionProvider provider : sl) {
 *     // use provider
 * }
 * </pre>
 */
@SupportedSourceVersion(SourceVersion.RELEASE_7)
@SupportedAnnotationTypes({"com.oracle.graal.options.Option"})
public class OptionProcessor extends AbstractProcessor {

    private final Set<Element> processed = new HashSet<>();

    private void processElement(Element element) {
        if (processed.contains(element)) {
            return;
        }
        processed.add(element);

        if (!element.getModifiers().contains(Modifier.STATIC)) {
            processingEnv.getMessager().printMessage(Kind.ERROR, "Option field must be static", element);
            return;
        }

        Option annotation = element.getAnnotation(Option.class);
        assert annotation != null;
        assert element instanceof VariableElement;
        assert element.getKind() == ElementKind.FIELD;
        VariableElement field = (VariableElement) element;
        String fieldName = field.getSimpleName().toString();

        Elements elements = processingEnv.getElementUtils();
        Types types = processingEnv.getTypeUtils();

        TypeMirror fieldType = field.asType();
        if (fieldType.getKind() != TypeKind.DECLARED) {
            processingEnv.getMessager().printMessage(Kind.ERROR, "Option field must be of type " + OptionValue.class.getName(), element);
            return;
        }
        DeclaredType declaredFieldType = (DeclaredType) fieldType;

        TypeMirror optionValueType = elements.getTypeElement(OptionValue.class.getName()).asType();
        if (!types.isSubtype(fieldType, types.erasure(optionValueType))) {
            String msg = String.format("Option field type %s is not a subclass of %s", fieldType, optionValueType);
            processingEnv.getMessager().printMessage(Kind.ERROR, msg, element);
            return;
        }

        if (!field.getModifiers().contains(Modifier.STATIC)) {
            processingEnv.getMessager().printMessage(Kind.ERROR, "Option field must be static", element);
            return;
        }

        String optionName = annotation.name();
        if (optionName.equals("")) {
            optionName = fieldName;
        }

        String optionType = declaredFieldType.getTypeArguments().get(0).toString();
        if (optionType.startsWith("java.lang.")) {
            optionType = optionType.substring("java.lang.".length());
        }

        String pkg = null;
        Element enclosing = element.getEnclosingElement();
        String declaringClass = "";
        String separator = "";
        List<Element> originatingElementsList = new ArrayList<>();
        originatingElementsList.add(field);
        while (enclosing != null) {
            originatingElementsList.add(enclosing);
            if (enclosing.getKind() == ElementKind.CLASS || enclosing.getKind() == ElementKind.INTERFACE) {
                if (enclosing.getModifiers().contains(Modifier.PRIVATE)) {
                    String msg = String.format("Option field cannot be declared in a private %s %s", enclosing.getKind().name().toLowerCase(), enclosing);
                    processingEnv.getMessager().printMessage(Kind.ERROR, msg, element);
                    return;
                }
                declaringClass = enclosing.getSimpleName() + separator + declaringClass;
                separator = ".";
            } else {
                assert enclosing.getKind() == ElementKind.PACKAGE;
                pkg = ((PackageElement) enclosing).getQualifiedName().toString();
            }
            enclosing = enclosing.getEnclosingElement();
        }

        String providerClassName = declaringClass.replace('.', '_') + "_" + fieldName;
        Element[] originatingElements = originatingElementsList.toArray(new Element[originatingElementsList.size()]);

        Filer filer = processingEnv.getFiler();
        try (PrintWriter out = createSourceFile(pkg, providerClassName, filer, originatingElements)) {

            out.println("/*");
            out.println(" * Copyright (c) 2013, Oracle and/or its affiliates. All rights reserved.");
            out.println(" * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.");
            out.println(" *");
            out.println(" * This code is free software; you can redistribute it and/or modify it");
            out.println(" * under the terms of the GNU General Public License version 2 only, as");
            out.println(" * published by the Free Software Foundation.");
            out.println(" *");
            out.println(" * This code is distributed in the hope that it will be useful, but WITHOUT");
            out.println(" * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or");
            out.println(" * FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License");
            out.println(" * version 2 for more details (a copy is included in the LICENSE file that");
            out.println(" * accompanied this code).");
            out.println(" *");
            out.println(" * You should have received a copy of the GNU General Public License version");
            out.println(" * 2 along with this work; if not, write to the Free Software Foundation,");
            out.println(" * Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.");
            out.println(" *");
            out.println(" * Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA");
            out.println(" * or visit www.oracle.com if you need additional information or have any");
            out.println(" * questions.");
            out.println(" */");

            out.println("package " + pkg + ";");
            out.println("");
            if (element.getModifiers().contains(Modifier.PRIVATE)) {
                out.println("import " + Field.class.getName() + ";");
            }
            out.println("import " + OptionValue.class.getName() + ";");
            out.println("import " + OptionProvider.class.getName() + ";");
            out.println("");
            out.println("public class " + providerClassName + " implements " + OptionProvider.class.getSimpleName() + " {");
            out.println("    public String getHelp() {");
            out.println("        return \"" + annotation.help() + "\";");
            out.println("    }");
            out.println("    public String getName() {");
            out.println("        return \"" + optionName + "\";");
            out.println("    }");
            out.println("    public Class getType() {");
            out.println("        return " + optionType + ".class;");
            out.println("    }");
            out.println("    public " + OptionValue.class.getSimpleName() + "<?> getOptionValue() {");
            if (!element.getModifiers().contains(Modifier.PRIVATE)) {
                out.println("        return " + declaringClass + "." + fieldName + ";");
            } else {
                out.println("        try {");
                out.println("            Field field = " + declaringClass + ".class.getDeclaredField(\"" + fieldName + "\");");
                out.println("            field.setAccessible(true);");
                out.println("            return (" + OptionValue.class.getSimpleName() + ") field.get(null);");
                out.println("        } catch (Exception e) {");
                out.println("            throw (InternalError) new InternalError().initCause(e);");
                out.println("        }");
            }
            out.println("    }");
            out.println("}");
        }

        try {
            createProviderFile(pkg, providerClassName, originatingElements);
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Kind.ERROR, e.getMessage(), field);
        }
    }

    private void createProviderFile(String pkg, String providerClassName, Element... originatingElements) throws IOException {
        String filename = "META-INF/providers/" + pkg + "." + providerClassName;
        FileObject file = processingEnv.getFiler().createResource(StandardLocation.CLASS_OUTPUT, "", filename, originatingElements);
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(file.openOutputStream(), "UTF-8"));
        writer.println(OptionProvider.class.getName());
        writer.close();
    }

    protected PrintWriter createSourceFile(String pkg, String relativeName, Filer filer, Element... originatingElements) {
        try {
            // Ensure Unix line endings to comply with Graal code style guide checked by Checkstyle
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

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (roundEnv.processingOver()) {
            return true;
        }

        for (Element element : roundEnv.getElementsAnnotatedWith(Option.class)) {
            processElement(element);
        }

        return true;
    }
}
