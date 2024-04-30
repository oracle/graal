/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.processor;

// Checkstyle: allow Class.getSimpleName

import static javax.tools.Diagnostic.Kind.ERROR;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic.Kind;
import javax.tools.FileObject;
import javax.tools.StandardLocation;

import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LineMap;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePath;
import com.sun.source.util.Trees;

import jdk.graal.compiler.processor.AbstractProcessor;

/**
 * This processor visits {@code @BasedOnJDKFile} annotated elements, collect information from the
 * annotation and the annotated element and writes the result as a JSON file to
 * {@link StandardLocation#SOURCE_OUTPUT} for further processing. See {@link #processAnnotation} for
 * the details.
 */
@SupportedAnnotationTypes({BasedOnJDKFileProcessor.ANNOTATION_CLASS_NAME, BasedOnJDKFileProcessor.ANNOTATION_LIST_CLASS_NAME})
public class BasedOnJDKFileProcessor extends AbstractProcessor {

    static final String ANNOTATION_CLASS_NAME = "com.oracle.svm.core.util.BasedOnJDKFile";
    static final String ANNOTATION_LIST_CLASS_NAME = "com.oracle.svm.core.util.BasedOnJDKFile.List";
    static final Pattern FILE_PATTERN = Pattern
                    .compile("^https://github.com/openjdk/jdk/blob/(?<committish>[^/]+)/(?<path>[-_.A-Za-z0-9][-_./A-Za-z0-9]*)(#L(?<lineStart>[0-9]+)(-L(?<lineEnd>[0-9]+))?)?$");
    static final String FILE_PATTERN_STR = "https://github.com/openjdk/jdk/blob/<tag|revision>/path/to/file.ext(#L[0-9]+(-L[0-9]+)?)?";
    public static final int FULL_FILE_LINE_MARKER = 0;

    private final Set<Element> processed = new HashSet<>();
    private Trees trees;
    private boolean isECJ = false;

    @Override
    public synchronized void init(ProcessingEnvironment pe) {
        super.init(pe);
        try {
            this.trees = Trees.instance(pe);
            this.isECJ = false;
        } catch (IllegalArgumentException e) {
            // probably compiling with ECJ
            this.isECJ = true;
        }
    }

    @SuppressWarnings("unchecked")
    private List<? extends AnnotationValue> getAnnotationValues(Element element, TypeElement listTypeElement) {
        AnnotationMirror listMirror = getAnnotation(element, listTypeElement.asType());
        return getAnnotationValue(listMirror, "value", List.class);
    }

    record SourceInfo(String committish, String path, long lineStart, long lineEnd) {
    }

    private static String quoteString(String s) {
        if (s == null) {
            return "null";
        }
        StringBuilder sb = new StringBuilder(2 + s.length() + 8 /* room for escaping */);
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '"' || c == '\\') {
                sb.append('\\');
                sb.append(c);
            } else if (c < 0x001F) {
                sb.append(String.format("\\u%04x", (int) c));
            } else {
                sb.append(c);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    private void processAnnotation(Element annotatedElement, AnnotationMirror annotationMirror) {
        String annotationValue = Objects.requireNonNull(getAnnotationValue(annotationMirror, "value", String.class));
        SourceInfo targetSourceInfo = parseBasedOnJDKFileAnnotation(annotationValue);
        if (targetSourceInfo == null) {
            // error parsing the annotation
            return;
        }

        String qualifiedName = getQualifiedName(annotatedElement);

        Element[] originatingElements = new Element[]{annotatedElement};
        String uniqueName = getUniqueName(qualifiedName, targetSourceInfo.committish, targetSourceInfo.path, targetSourceInfo.lineStart, targetSourceInfo.lineEnd);

        String filename = "jdk_source_info/" + URLEncoder.encode(uniqueName, StandardCharsets.UTF_8) + ".json";
        SourceInfo annotatedSourceInfo = getAnnotatedSourceInfo(annotatedElement);
        try {
            FileObject file = processingEnv.getFiler().createResource(StandardLocation.SOURCE_OUTPUT, "", filename, originatingElements);
            PrintWriter writer = new PrintWriter(new OutputStreamWriter(file.openOutputStream(), "UTF-8"));
            writer.println("{");
            writer.println("  \"annotatedElement\": {");
            writer.println("    \"qualifiedName\": " + quoteString(qualifiedName) + ",");
            writer.println("    \"annotationValue\": " + quoteString(annotationValue) + ",");
            writer.println("    \"uniqueName\": " + quoteString(uniqueName) + ",");
            printSourceInfo(writer, annotatedSourceInfo, "    ");
            writer.println();
            writer.println("  },");
            writer.println("  \"target\": {");
            printSourceInfo(writer, targetSourceInfo, "    ");
            writer.println();
            writer.println("  }");
            writer.println("}");
            writer.close();
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(isBug367599(e) ? Kind.NOTE : ERROR, e.getMessage(), originatingElements[0]);
        }
    }

    private static String getQualifiedName(Element annotatedElement) {
        if (annotatedElement instanceof TypeElement typeElement) {
            return typeElement.getQualifiedName().toString();
        }
        if (annotatedElement instanceof ExecutableElement executableElement) {
            TypeElement enclosingElement = (TypeElement) executableElement.getEnclosingElement();
            return enclosingElement.getQualifiedName().toString() + "#" + executableElement.getSimpleName();
        }
        if (annotatedElement instanceof VariableElement variableElement) {
            TypeElement enclosingElement = (TypeElement) variableElement.getEnclosingElement();
            return enclosingElement.getQualifiedName().toString() + "#" + variableElement.getSimpleName();
        }
        throw new RuntimeException("Unexpected element class: " + annotatedElement.getClass().getSimpleName());
    }

    private SourceInfo parseBasedOnJDKFileAnnotation(String annotationValue) {
        Matcher matcher = FILE_PATTERN.matcher(annotationValue);
        if (!matcher.matches()) {
            env().getMessager().printMessage(ERROR, String.format("Invalid path: %s%nShould be %s", annotationValue, FILE_PATTERN_STR));
            return null;
        }
        String lineStartStr = matcher.group("lineStart");
        String lineEndStr = matcher.group("lineEnd");
        long lineStart = lineStartStr == null ? FULL_FILE_LINE_MARKER : Long.parseLong(lineStartStr);
        final long lineEnd;
        if (lineEndStr == null) {
            if (lineStartStr != null) {
                // no lineEnd but lineStart -> single line url
                lineEnd = lineStart;
            } else {
                lineEnd = FULL_FILE_LINE_MARKER;
            }
        } else {
            lineEnd = Long.parseLong(lineEndStr);
        }
        return new SourceInfo(matcher.group("committish"), matcher.group("path"), lineStart, lineEnd);
    }

    private SourceInfo getAnnotatedSourceInfo(Element annotatedElement) {
        TreePath tp = this.trees.getPath(annotatedElement);
        CompilationUnitTree cut = tp.getCompilationUnit();
        LineMap lineMap = cut.getLineMap();

        String sourceFileName = cut.getSourceFile().getName();

        SourcePositions sp = trees.getSourcePositions();
        long start = sp.getStartPosition(cut, tp.getLeaf());
        long end = sp.getEndPosition(cut, tp.getLeaf());

        return new SourceInfo(null, sourceFileName, lineMap.getLineNumber(start), lineMap.getLineNumber(end));
    }

    private static String getUniqueName(String qualifiedName, String committish, String path, long lineStart, long lineEnd) {
        return String.format("%s/%s/%s-%s/%s", committish, path, lineStart, lineEnd, qualifiedName);
    }

    private static void printSourceInfo(PrintWriter writer, SourceInfo annotatedSourceInfo, String indent) {
        writer.println(indent + "\"sourceInfo\": {");
        if (annotatedSourceInfo.committish != null) {
            writer.println(indent + "  \"committish\": " + quoteString(annotatedSourceInfo.committish) + ",");
        }
        writer.println(indent + "  \"path\": " + quoteString(annotatedSourceInfo.path) + ",");
        writer.println(indent + "  \"lineStart\": " + annotatedSourceInfo.lineStart + ",");
        writer.println(indent + "  \"lineEnd\": " + annotatedSourceInfo.lineEnd);
        writer.print(indent + "}");
    }

    @Override
    public boolean doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        if (isECJ) {
            // ECJ is not supported
            return true;
        }
        if (roundEnv.processingOver()) {
            return true;
        }

        // handle single annotations
        TypeElement annotationType = getTypeElement(ANNOTATION_CLASS_NAME);
        for (var element : roundEnv.getElementsAnnotatedWith(annotationType)) {
            assert element.getKind().isClass() : "Only classes supported for now: " + element;
            if (processed.add(element)) {
                AnnotationMirror annotationMirror = getAnnotation(element, annotationType.asType());
                processAnnotation(element, annotationMirror);
            }
        }

        // handle repeated annotations
        TypeElement annotationListType = getTypeElement(ANNOTATION_LIST_CLASS_NAME);
        for (var element : roundEnv.getElementsAnnotatedWith(annotationListType)) {
            assert element.getKind().isClass() : "Only classes supported for now: " + element;
            if (processed.add(element)) {
                List<? extends AnnotationValue> list = getAnnotationValues(element, annotationListType);
                if (list != null) {
                    for (var annotationValue : list) {
                        AnnotationMirror mirror = (AnnotationMirror) annotationValue.getValue();
                        processAnnotation(element, mirror);
                    }
                }
            }
        }
        return true;
    }
}
