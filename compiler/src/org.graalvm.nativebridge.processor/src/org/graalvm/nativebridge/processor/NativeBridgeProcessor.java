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

import org.graalvm.compiler.processor.AbstractProcessor;
import org.graalvm.nativebridge.processor.AbstractBridgeParser.DefinitionData;
import org.graalvm.nativebridge.processor.AbstractBridgeParser.ParseException;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@SupportedAnnotationTypes({
                HotSpotToNativeBridgeParser.GENERATE_HOTSPOT_TO_NATIVE_ANNOTATION,
                NativeToHotSpotBridgeParser.GENERATE_NATIVE_TO_HOTSPOT_ANNOTATION,
})
public final class NativeBridgeProcessor extends AbstractProcessor {

    public NativeBridgeProcessor() {
    }

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latest();
    }

    @Override
    protected boolean doProcess(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Map<AbstractBridgeParser, List<DefinitionData>> toGenerate = new HashMap<>();
        Set<? extends Element> annotatedElements = roundEnv.getElementsAnnotatedWith(getTypeElement(HotSpotToNativeBridgeParser.GENERATE_HOTSPOT_TO_NATIVE_ANNOTATION));
        if (!annotatedElements.isEmpty()) {
            HotSpotToNativeBridgeParser parser = HotSpotToNativeBridgeParser.create(this);
            List<DefinitionData> parsedData = new ArrayList<>();
            toGenerate.put(parser, parsedData);
            parse(parser, annotatedElements, parsedData);
        }
        annotatedElements = roundEnv.getElementsAnnotatedWith(getTypeElement(NativeToHotSpotBridgeParser.GENERATE_NATIVE_TO_HOTSPOT_ANNOTATION));
        if (!annotatedElements.isEmpty()) {
            NativeToHotSpotBridgeParser parser = NativeToHotSpotBridgeParser.create(this);
            List<DefinitionData> parsedData = new ArrayList<>();
            toGenerate.put(parser, parsedData);
            parse(parser, annotatedElements, parsedData);
        }
        for (Map.Entry<AbstractBridgeParser, List<DefinitionData>> e : toGenerate.entrySet()) {
            AbstractBridgeGenerator generator = e.getKey().getGenerator();
            for (DefinitionData data : e.getValue()) {
                try {
                    generator.generate(data);
                } catch (IOException ioe) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, ioe.getMessage(), data.annotatedType.asElement());
                }
            }
        }
        return true;
    }

    private void parse(AbstractBridgeParser parser, Set<? extends Element> annotatedElements, List<DefinitionData> into) {
        for (Element element : annotatedElements) {
            try {
                DefinitionData data = parser.parse(element);
                into.add(data);
            } catch (ParseException parseException) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, parseException.getMessage(),
                                parseException.getElement(), parseException.getAnnotation());
            }
        }
    }
}
