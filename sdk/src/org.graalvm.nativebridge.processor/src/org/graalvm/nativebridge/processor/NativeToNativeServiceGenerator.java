/*
 * Copyright (c) 2021, 2025, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.nativebridge.processor.HotSpotToNativeServiceParser.HotSpotToNativeServiceDefinitionData;

import javax.lang.model.element.Modifier;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;

final class NativeToNativeServiceGenerator extends AbstractNativeServiceGenerator {

    private static final String START_POINT_SIMPLE_NAME = "NativeToNativeStartPoint";
    private static final String END_POINT_SIMPLE_NAME = "NativeToNativeEndPoint";

    private final HotSpotToNativeTypeCache typeCache;
    private HotSpotToNativeServiceGenerator delegateGenerator;
    private FactoryMethodInfo factoryMethod;
    private boolean selectingFactory;
    private boolean sharedImplementationWithHotSpotToNative;

    NativeToNativeServiceGenerator(AbstractServiceParser parser, HotSpotToNativeTypeCache typeCache, HotSpotToNativeServiceDefinitionData definitionData) {
        super(parser, typeCache, definitionData, BinaryNameCache.create(definitionData, true, parser.types, parser.elements, typeCache));
        this.typeCache = typeCache;
        this.delegateGenerator = new HotSpotToNativeServiceGenerator(parser, typeCache, definitionData, START_POINT_SIMPLE_NAME, END_POINT_SIMPLE_NAME);
        this.factoryMethod = resolveFactoryMethod(FACTORY_METHOD_NAME, START_POINT_SIMPLE_NAME, END_POINT_SIMPLE_NAME);
    }

    @Override
    HotSpotToNativeServiceDefinitionData getDefinition() {
        return (HotSpotToNativeServiceDefinitionData) super.getDefinition();
    }

    @Override
    void configureMultipleDefinitions(List<DefinitionData> otherDefinitions) {
        Optional<DefinitionData> otherHotSpotToNative = otherDefinitions.stream().filter((d) -> d instanceof HotSpotToNativeServiceDefinitionData).findAny();
        boolean hasOtherHotSpotToNative = otherHotSpotToNative.isPresent();
        HotSpotToNativeServiceDefinitionData hotSpotToNativeDefinition = (HotSpotToNativeServiceDefinitionData) otherHotSpotToNative.orElse(null);
        sharedImplementationWithHotSpotToNative = hasOtherHotSpotToNative &&
                        isCompatibleImplementation(types, getDefinition(), hotSpotToNativeDefinition);
        selectingFactory = hasOtherHotSpotToNative && !isCompatibleImplementation(types, getDefinition(), hotSpotToNativeDefinition);
        String startPointName = START_POINT_SIMPLE_NAME;
        String endPointName = END_POINT_SIMPLE_NAME;
        if (sharedImplementationWithHotSpotToNative) {
            startPointName = HotSpotToNativeServiceGenerator.START_POINT_SIMPLE_NAME;
            endPointName = HotSpotToNativeServiceGenerator.END_POINT_SIMPLE_NAME;
            factoryMethod = new FactoryMethodInfo(FACTORY_METHOD_NAME, startPointName, endPointName, factoryMethod.factoryMethodParameters,
                            factoryMethod.constructorParameters, factoryMethod.superCallParameters);
        }
        delegateGenerator = new HotSpotToNativeServiceGenerator(getParser(), typeCache, getDefinition(), startPointName, endPointName);
    }

    @Override
    void generateCommonCustomDispatchFactoryReturn(CodeBuilder builder) {
        CharSequence startPoint;
        if (selectingFactory) {
            startPoint = newSelectStartPoint(builder);
        } else {
            startPoint = newSimpleStartPoint(builder);

        }
        builder.lineStart("return ").write(startPoint).lineEnd(";");
    }

    @Override
    void generateAPI(CodeBuilder builder, CharSequence targetClassSimpleName) {
        if (!getDefinition().hasCustomDispatch()) {
            builder.lineEnd("");
            if (selectingFactory) {
                // Generate factory selecting among HotSpotToNative and NativeToNative
                generateSharedToNativeFactories(builder);
            } else {
                // Generate only NativeToNative factory
                generateStartPointFactory(builder, factoryMethod);
            }
        }
    }

    @Override
    void generateImpl(CodeBuilder builder, CharSequence targetClassSimpleName) {
        if (!sharedImplementationWithHotSpotToNative) {
            delegateGenerator.generateImpl(builder, targetClassSimpleName);
        }
    }

    @Override
    MarshallerSnippet marshallerSnippets(AbstractServiceParser.MarshallerData marshallerData) {
        throw new UnsupportedOperationException("Should not reach here");
    }

    private void generateSharedToNativeFactories(CodeBuilder builder) {
        // Factory method that selects either the NativeToNative or HotSpotToNative entry point.
        builder.methodStart(EnumSet.of(Modifier.STATIC), factoryMethod.name, getDefinition().annotatedType,
                        factoryMethod.factoryMethodParameters, Collections.emptyList());
        builder.indent();
        builder.lineStart("return ").write(newSelectStartPoint(builder)).lineEnd(";");
        builder.dedent();
        builder.line("}");
    }

    private CharSequence newSelectStartPoint(CodeBuilder builder) {
        return newSelectStartPoint(builder, parameterNames(factoryMethod.constructorParameters));
    }

    private CharSequence newSelectStartPoint(CodeBuilder builder, CharSequence[] parameters) {
        return new CodeBuilder(builder).invokeStatic(typeCache.imageInfo, "inImageCode").write(" ? ").//
                        write(newSimpleStartPoint(builder, parameters)).write(" : ").//
                        newInstance(HotSpotToNativeServiceGenerator.START_POINT_SIMPLE_NAME, parameters).build();
    }

    private CharSequence newSimpleStartPoint(CodeBuilder builder) {
        return newSimpleStartPoint(builder, parameterNames(factoryMethod.constructorParameters));
    }

    private CharSequence newSimpleStartPoint(CodeBuilder builder, CharSequence[] parameters) {
        return new CodeBuilder(builder).newInstance(factoryMethod.startPointSimpleName, parameters).build();
    }

    private static boolean isCompatibleImplementation(Types types, HotSpotToNativeServiceDefinitionData configuration1, HotSpotToNativeServiceDefinitionData configuration2) {
        return types.isSameType(configuration1.marshallerConfig, configuration2.marshallerConfig) && isSameTypeOrNull(types, configuration1.centryPointPredicate, configuration2.centryPointPredicate);
    }

    private static boolean isSameTypeOrNull(Types types, TypeMirror t1, TypeMirror t2) {
        if (t1 != null && t2 != null) {
            return types.isSameType(t1, t2);
        }
        return t1 == null && t2 == null;
    }
}
