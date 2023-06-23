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

import org.graalvm.nativebridge.processor.AbstractBridgeParser.DefinitionData;
import org.graalvm.nativebridge.processor.HotSpotToNativeBridgeParser.HotSpotToNativeDefinitionData;
import org.graalvm.nativebridge.processor.HotSpotToNativeBridgeParser.TypeCache;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

final class NativeToNativeBridgeGenerator extends AbstractBridgeGenerator {

    static final String START_POINT_FACTORY_NAME = "createNativeToNative";
    static final String COMMON_START_POINT_FACTORY_NAME = "create";
    private static final String START_POINT_SIMPLE_NAME = "NativeToNativeStartPoint";
    private static final String END_POINT_SIMPLE_NAME = "NativeToNativeEndPoint";

    private final HotSpotToNativeBridgeGenerator delegateGenerator;
    private final TypeCache typeCache;
    private FactoryMethodInfo factoryMethod;
    private boolean commonFactory;
    private boolean sharedImplementation;

    NativeToNativeBridgeGenerator(AbstractBridgeParser parser, TypeCache typeCache, DefinitionData definitionData) {
        super(parser, definitionData, typeCache, BinaryNameCache.create(definitionData, true, parser.types, parser.elements, typeCache));
        delegateGenerator = new HotSpotToNativeBridgeGenerator(parser, typeCache, definitionData,
                        new String[]{HotSpotToNativeBridgeGenerator.SHARED_START_POINT_SIMPLE_NAME, HotSpotToNativeBridgeGenerator.SHARED_END_POINT_SIMPLE_NAME},
                        new String[]{START_POINT_SIMPLE_NAME, END_POINT_SIMPLE_NAME});
        this.typeCache = typeCache;
        this.factoryMethod = resolveFactoryMethod(START_POINT_FACTORY_NAME, START_POINT_SIMPLE_NAME, END_POINT_SIMPLE_NAME);
        sharedImplementation = false;
    }

    @Override
    void configureMultipleDefinitions(List<DefinitionData> otherDefinitions) {
        Optional<DefinitionData> hotSpotToNativeOrNull = otherDefinitions.stream().filter((d) -> d instanceof HotSpotToNativeDefinitionData).findAny();
        this.commonFactory = hotSpotToNativeOrNull.isPresent();
        this.sharedImplementation = commonFactory && isCompatible(types, (HotSpotToNativeDefinitionData) definitionData, (HotSpotToNativeDefinitionData) hotSpotToNativeOrNull.get());
        if (sharedImplementation) {
            delegateGenerator.setShared(true);
            factoryMethod = new FactoryMethodInfo(START_POINT_FACTORY_NAME, HotSpotToNativeBridgeGenerator.SHARED_START_POINT_SIMPLE_NAME, HotSpotToNativeBridgeGenerator.SHARED_END_POINT_SIMPLE_NAME,
                            factoryMethod.parameters, factoryMethod.superCallParameters);
        }
    }

    @Override
    void generateAPI(CodeBuilder builder, CharSequence targetClassSimpleName) {
        builder.lineEnd("");
        generateStartPointFactory(builder, factoryMethod);
        if (commonFactory) {
            builder.lineEnd("");
            generateCommonFactory(builder);
        }
    }

    @Override
    void generateImpl(CodeBuilder builder, CharSequence targetClassSimpleName) {
        if (!sharedImplementation) {
            delegateGenerator.generateImpl(builder, targetClassSimpleName);
        }
    }

    @Override
    MarshallerSnippet marshallerSnippets(AbstractBridgeParser.MarshallerData marshallerData) {
        throw new UnsupportedOperationException("Should not reach here");
    }

    private void generateCommonFactory(CodeBuilder builder) {
        builder.methodStart(EnumSet.of(Modifier.STATIC), COMMON_START_POINT_FACTORY_NAME, definitionData.annotatedType,
                        factoryMethod.parameters, Collections.emptyList());
        CharSequence[] params = factoryMethod.parameters.stream().map((p) -> p.name).toArray(CharSequence[]::new);
        builder.indent();
        builder.lineStart("if (").invokeStatic(typeCache.imageInfo, "inImageCode").write(")").lineEnd(" {");
        builder.indent();
        builder.lineStart("return ").invoke(null, START_POINT_FACTORY_NAME, params).lineEnd(";");
        builder.dedent();
        builder.line("} else {");
        builder.indent();
        builder.lineStart("return ").invoke(null, HotSpotToNativeBridgeGenerator.START_POINT_FACTORY_NAME, params).lineEnd(";");
        builder.dedent();
        builder.line("}");
        builder.dedent();
        builder.line("}");
    }

    static boolean isCompatible(Types types, HotSpotToNativeDefinitionData configuration1, HotSpotToNativeDefinitionData configuration2) {
        return types.isSameType(configuration1.jniConfig, configuration2.jniConfig) && isSameTypeOrNull(types, configuration1.centryPointPredicate, configuration2.centryPointPredicate);
    }

    private static boolean isSameTypeOrNull(Types types, TypeMirror t1, TypeMirror t2) {
        if (t1 != null && t2 != null) {
            return types.isSameType(t1, t2);
        }
        return t1 == null && t2 == null;
    }
}
