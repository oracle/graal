/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.nativebridge.processor;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.PackageElement;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.JavaFileObject;

import org.graalvm.compiler.processor.AbstractProcessor;
import org.graalvm.nativebridge.processor.AbstractBridgeParser.AbstractTypeCache;
import org.graalvm.nativebridge.processor.AbstractBridgeParser.DefinitionData;

@SupportedAnnotationTypes({
                HotSpotToNativeBridgeParser.GENERATE_HOTSPOT_TO_NATIVE_ANNOTATION,
                NativeToHotSpotBridgeParser.GENERATE_NATIVE_TO_HOTSPOT_ANNOTATION,
                NativeToNativeBridgeParser.GENERATE_NATIVE_TO_NATIVE_ANNOTATION
})
public final class NativeBridgeProcessor extends AbstractProcessor {

    public NativeBridgeProcessor() {
    }

    @Override
    protected boolean doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Map<TypeElement, List<AbstractBridgeGenerator>> toGenerate = new HashMap<>();
        Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(getTypeElement(HotSpotToNativeBridgeParser.GENERATE_HOTSPOT_TO_NATIVE_ANNOTATION));
        if (!annotatedElements.isEmpty()) {
            HotSpotToNativeBridgeParser parser = HotSpotToNativeBridgeParser.create(this);
            parse(parser, annotatedElements, toGenerate);
        }
        annotatedElements = roundEnv.getElementsAnnotatedWith(getTypeElement(NativeToHotSpotBridgeParser.GENERATE_NATIVE_TO_HOTSPOT_ANNOTATION));
        if (!annotatedElements.isEmpty()) {
            NativeToHotSpotBridgeParser parser = NativeToHotSpotBridgeParser.create(this);
            parse(parser, annotatedElements, toGenerate);
        }
        annotatedElements = roundEnv.getElementsAnnotatedWith(getTypeElement(NativeToNativeBridgeParser.GENERATE_NATIVE_TO_NATIVE_ANNOTATION));
        if (!annotatedElements.isEmpty()) {
            NativeToNativeBridgeParser parser = NativeToNativeBridgeParser.create(this);
            parse(parser, annotatedElements, toGenerate);
        }
        for (List<AbstractBridgeGenerator> generatorsForElement : toGenerate.values()) {
            for (AbstractBridgeGenerator generator : generatorsForElement) {
                List<DefinitionData> otherDefinitions = generatorsForElement.stream().filter((g) -> g != generator).map((g) -> g.definitionData).collect(Collectors.toList());
                if (!otherDefinitions.isEmpty()) {
                    generator.configureMultipleDefinitions(otherDefinitions);
                }
            }
        }
        for (Entry<TypeElement, List<AbstractBridgeGenerator>> e : toGenerate.entrySet()) {
            TypeElement annotatedElement = e.getKey();
            List<AbstractBridgeGenerator> generators = e.getValue();
            PackageElement owner = Utilities.getEnclosingPackageElement(annotatedElement);
            AbstractTypeCache typeCache = generators.get(0).parser.typeCache;
            CodeBuilder builder = new CodeBuilder(owner, env().getTypeUtils(), typeCache);
            CharSequence targetClassSimpleName = annotatedElement.getSimpleName() + "Gen";
            builder.classStart(EnumSet.of(Modifier.FINAL), targetClassSimpleName, null, Collections.emptyList());
            builder.indent();
            for (AbstractBridgeGenerator generator : generators) {
                generator.generateAPI(builder, targetClassSimpleName);
            }
            for (AbstractBridgeGenerator generator : generators) {
                generator.generateImpl(builder, targetClassSimpleName);
            }
            builder.dedent();
            builder.line("}");  // Enclosing class end
            try {
                writeSourceFile(annotatedElement, targetClassSimpleName, builder.build());
            } catch (IOException ioe) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, ioe.getMessage(), annotatedElement);
            }
        }
        return true;
    }

    private static void parse(AbstractBridgeParser parser, Set<? extends Element> annotatedElements, Map<TypeElement, List<AbstractBridgeGenerator>> into) {
        for (Element element : annotatedElements) {
            DefinitionData data = parser.parse(element);
            if (data == null) {
                // Parsing error
                continue;
            }
            into.computeIfAbsent((TypeElement) element, (k) -> new ArrayList<>()).add(parser.createGenerator(data));
        }
    }

    private void writeSourceFile(TypeElement annotatedElement, CharSequence targetClassSimpleName, String content) throws IOException {
        String sourceFileFQN = String.format("%s.%s",
                        Utilities.getEnclosingPackageElement(annotatedElement).getQualifiedName().toString(),
                        targetClassSimpleName);
        JavaFileObject sourceFile = env().getFiler().createSourceFile(sourceFileFQN, annotatedElement);
        try (PrintWriter out = new PrintWriter(sourceFile.openWriter())) {
            out.print(content);
        }
    }
}
