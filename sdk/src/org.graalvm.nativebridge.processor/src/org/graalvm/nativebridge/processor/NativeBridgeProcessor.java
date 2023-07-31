/*
 * Copyright (c) 2021, 2023, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * The Universal Permissive License (UPL), Version 1.0
 *
 * Subject to the condition set forth below, permission is hereby granted to any
 * person obtaining a copy of this software, associated documentation and/or
 * data (collectively the "Software"), free of charge and under any and all
 * copyright rights in the Software, and any and all patent rights owned or
 * freely licensable by each licensor hereunder covering either (i) the
 * unmodified Software as contributed to or provided by such licensor, or (ii)
 * the Larger Works (as defined below), to deal in both
 *
 * (a) the Software, and
 *
 * (b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
 * one is included with the Software each a "Larger Work" to which the Software
 * is contributed by such licensors),
 *
 * without restriction, including without limitation the rights to copy, create
 * derivative works of, display, perform, and distribute the Software and make,
 * use, sell, offer for sale, import, export, have made, and have sold the
 * Software and the Larger Work(s), and to sublicense the foregoing rights on
 * either these or other terms.
 *
 * This license is subject to the following condition:
 *
 * The above copyright notice and either this complete permission notice or at a
 * minimum a reference to the UPL must be included in all copies or substantial
 * portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
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
