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

import org.graalvm.nativebridge.processor.HotSpotToNativeFactoryParser.HotSpotToNativeFactoryDefinitionData;
import org.graalvm.nativebridge.processor.HotSpotToNativeFactoryGenerator.IsolateHandlerSnippet;

import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Types;
import java.util.List;
import java.util.Optional;

final class NativeToNativeFactoryGenerator extends AbstractBridgeGenerator {

    private static final String START_POINT_FACTORY_SIMPLE_NAME = "NativeToNativeFactoryStartPoint";
    private static final String END_POINT_FACTORY_SIMPLE_NAME = "NativeToNativeFactoryEndPoint";

    private final Types types;
    private HotSpotToNativeFactoryGenerator delegateGenerator;
    private HotSpotToNativeFactoryDefinitionData hotSpotToNativeDefinition;
    private boolean sharedImplementationWithHotSpotToNative;

    NativeToNativeFactoryGenerator(NativeToNativeFactoryParser parser, HotSpotToNativeFactoryDefinitionData definitionData, HotSpotToNativeTypeCache typeCache) {
        super(parser, definitionData, typeCache);
        this.types = parser.types;
        this.delegateGenerator = new HotSpotToNativeFactoryGenerator(parser, definitionData, typeCache, START_POINT_FACTORY_SIMPLE_NAME, END_POINT_FACTORY_SIMPLE_NAME);
    }

    @Override
    NativeToNativeFactoryParser getParser() {
        return (NativeToNativeFactoryParser) super.getParser();
    }

    @Override
    HotSpotToNativeFactoryDefinitionData getDefinition() {
        return (HotSpotToNativeFactoryDefinitionData) super.getDefinition();
    }

    @Override
    HotSpotToNativeTypeCache getTypeCache() {
        return (HotSpotToNativeTypeCache) super.getTypeCache();
    }

    @Override
    void configureMultipleDefinitions(List<AbstractBridgeParser.DefinitionData> otherDefinitions) {
        Optional<HotSpotToNativeFactoryDefinitionData> otherHotSpotToNative = otherDefinitions.stream().filter((d) -> d instanceof HotSpotToNativeFactoryDefinitionData).map(
                        HotSpotToNativeFactoryDefinitionData.class::cast).findAny();
        boolean hasOtherHotSpotToNative = otherHotSpotToNative.isPresent();
        hotSpotToNativeDefinition = otherHotSpotToNative.orElse(null);
        sharedImplementationWithHotSpotToNative = hasOtherHotSpotToNative && isCompatibleImplementation(types, getDefinition(), hotSpotToNativeDefinition);
        String factoryStartPointName;
        String factoryEndPointName;
        if (sharedImplementationWithHotSpotToNative) {
            factoryStartPointName = HotSpotToNativeFactoryGenerator.START_POINT_FACTORY_SIMPLE_NAME;
            factoryEndPointName = HotSpotToNativeFactoryGenerator.END_POINT_FACTORY_SIMPLE_NAME;
        } else {
            factoryStartPointName = START_POINT_FACTORY_SIMPLE_NAME;
            factoryEndPointName = END_POINT_FACTORY_SIMPLE_NAME;
        }
        delegateGenerator = new HotSpotToNativeFactoryGenerator(getParser(), getDefinition(), getTypeCache(), factoryStartPointName, factoryEndPointName);
    }

    @Override
    void generateAPI(CodeBuilder builder, CharSequence targetClassSimpleName) {
        builder.lineEnd("");
        IsolateHandlerSnippet isolateHandlerSnippet;
        if (hotSpotToNativeDefinition != null) {
            isolateHandlerSnippet = IsolateHandlerSnippet.composite(
                            sharedImplementationWithHotSpotToNative ? HotSpotToNativeFactoryGenerator.START_POINT_FACTORY_SIMPLE_NAME : START_POINT_FACTORY_SIMPLE_NAME,
                            types, getTypeCache(),
                            hotSpotToNativeDefinition.nativeIsolateHandler,
                            getDefinition().nativeIsolateHandler);
        } else {
            isolateHandlerSnippet = IsolateHandlerSnippet.single(START_POINT_FACTORY_SIMPLE_NAME, getTypeCache(), getDefinition().nativeIsolateHandler);
        }
        delegateGenerator.generateStartPointFactory(builder, isolateHandlerSnippet);
    }

    @Override
    void generateImpl(CodeBuilder builder, CharSequence targetClassSimpleName) {
        if (!sharedImplementationWithHotSpotToNative) {
            if (hotSpotToNativeDefinition != null) {
                delegateGenerator.generateFactoryClassesToInitializeIsolate(builder, targetClassSimpleName);
            } else {
                delegateGenerator.generateImpl(builder, targetClassSimpleName);
            }
        }
    }

    private static boolean isCompatibleImplementation(Types types, HotSpotToNativeFactoryDefinitionData configuration1, HotSpotToNativeFactoryDefinitionData configuration2) {
        return types.isSameType(configuration1.implementation, configuration2.implementation) && isSameTypeOrNull(types, configuration1.centryPointPredicate, configuration2.centryPointPredicate);
    }

    private static boolean isSameTypeOrNull(Types types, TypeMirror t1, TypeMirror t2) {
        if (t1 != null && t2 != null) {
            return types.isSameType(t1, t2);
        }
        return t1 == null && t2 == null;
    }
}
