/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.type.DeclaredType;

final class HotSpotToNativeFactoryParser extends AbstractFactoryParser {

    static final String GENERATE_HOTSPOT_TO_NATIVE_ANNOTATION = "org.graalvm.nativebridge.GenerateHotSpotToNativeFactory";

    private HotSpotToNativeFactoryParser(NativeBridgeProcessor processor, HotSpotToNativeTypeCache typeCache,
                    DeclaredType factoryAnnotationType, DeclaredType serviceAnnotationType) {
        super(processor, typeCache, factoryAnnotationType, serviceAnnotationType);
    }

    @Override
    HotSpotToNativeTypeCache getTypeCache() {
        return (HotSpotToNativeTypeCache) super.getTypeCache();
    }

    @Override
    FactoryDefinitionData createDefinition(DeclaredType annotatedType, AnnotationMirror annotation, DeclaredType initialService,
                    DeclaredType implementation, DeclaredType marshallerConfig, MarshallerData throwableMarshaller) {
        DeclaredType centryPointPredicate = (DeclaredType) getAnnotationValue(annotation, "include");
        DeclaredType nativeIsolateHandler = getNativeIsolateHandler(this, annotatedType, annotation);
        return new HotSpotToNativeFactoryDefinitionData(annotatedType, initialService, implementation, marshallerConfig, throwableMarshaller, centryPointPredicate, nativeIsolateHandler);
    }

    @Override
    AbstractBridgeGenerator createGenerator(DefinitionData definitionData) {
        return new HotSpotToNativeFactoryGenerator(this, (HotSpotToNativeFactoryDefinitionData) definitionData, getTypeCache(),
                        HotSpotToNativeFactoryGenerator.START_POINT_FACTORY_SIMPLE_NAME,
                        HotSpotToNativeFactoryGenerator.END_POINT_FACTORY_SIMPLE_NAME);
    }

    static DeclaredType getNativeIsolateHandler(AbstractFactoryParser parser, DeclaredType annotatedType, AnnotationMirror annotation) {
        DeclaredType handler = (DeclaredType) getAnnotationValue(annotation, "isolateHandler");
        if (handler != null && Utilities.findCustomObjectFactory(handler) == null) {
            CharSequence handlerName = Utilities.getTypeName(handler);
            parser.emitError(annotatedType.asElement(), annotation,
                            "The native isolate handler `%s` must have either an accessible no-argument static method `getInstance()` or a no-argument constructor.%n" +
                                            "To resolve this, add a `%s static getInstance()` method to `%s`, or ensure it has a no-argument constructor.",
                            handlerName, handlerName, handlerName);
        }
        return handler;
    }

    static HotSpotToNativeFactoryParser create(NativeBridgeProcessor processor) {
        HotSpotToNativeTypeCache typeCache = new HotSpotToNativeTypeCache(processor);
        return new HotSpotToNativeFactoryParser(processor, typeCache, typeCache.generateHSToNativeFactory, typeCache.generateHSToNativeBridge);
    }

    static final class HotSpotToNativeFactoryDefinitionData extends FactoryDefinitionData {

        final DeclaredType centryPointPredicate;
        final DeclaredType nativeIsolateHandler;

        HotSpotToNativeFactoryDefinitionData(DeclaredType annotatedType, DeclaredType initialService, DeclaredType implementation, DeclaredType marshallerConfig, MarshallerData throwableMarshaller,
                        DeclaredType centryPointPredicate, DeclaredType nativeIsolateHandler) {
            super(annotatedType, initialService, implementation, marshallerConfig, throwableMarshaller);
            this.centryPointPredicate = centryPointPredicate;
            this.nativeIsolateHandler = nativeIsolateHandler;
        }
    }
}
