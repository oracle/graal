/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
    MarshallerSnippets marshallerSnippets(AbstractBridgeParser.MarshallerData marshallerData) {
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
