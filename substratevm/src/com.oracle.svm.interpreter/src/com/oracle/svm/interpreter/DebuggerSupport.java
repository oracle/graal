/*
 * Copyright (c) 2023, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.interpreter;

import static com.oracle.svm.interpreter.InterpreterUtil.traceInterpreter;

import java.io.IOException;
import java.lang.reflect.Executable;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.ProcessProperties;
import org.graalvm.word.Pointer;

import com.oracle.graal.pointsto.heap.ImageHeapConstant;
import com.oracle.svm.core.BuildPhaseProvider;
import com.oracle.svm.core.FunctionPointerHolder;
import com.oracle.svm.core.c.CGlobalData;
import com.oracle.svm.core.c.CGlobalDataFactory;
import com.oracle.svm.core.heap.UnknownObjectField;
import com.oracle.svm.core.util.VMError;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaMethod;
import com.oracle.svm.interpreter.metadata.InterpreterResolvedJavaType;
import com.oracle.svm.interpreter.metadata.InterpreterUniverse;
import com.oracle.svm.interpreter.metadata.InterpreterUniverseImpl;
import com.oracle.svm.interpreter.metadata.Lazy;
import com.oracle.svm.interpreter.metadata.MetadataUtil;
import com.oracle.svm.interpreter.metadata.serialization.SerializationContext;
import com.oracle.svm.interpreter.metadata.serialization.Serializers;

import jdk.graal.compiler.api.replacements.Fold;
import jdk.graal.compiler.api.replacements.SnippetReflectionProvider;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

public class DebuggerSupport {
    public static final String IMAGE_INTERP_HASH_SYMBOL_NAME = "__svm_interp_hash";

    public static final CGlobalData<Pointer> IMAGE_INTERP_HASH = CGlobalDataFactory.forSymbol(IMAGE_INTERP_HASH_SYMBOL_NAME);

    private static final SerializationContext.Builder READER_BUILDER = Serializers.newBuilderForInterpreterMetadata();

    private ArrayList<Object> referencesInImage = new ArrayList<>();

    @UnknownObjectField(availability = BuildPhaseProvider.AfterCompilation.class) //
    private final ArrayList<FunctionPointerHolder> methodPointersInImage = new ArrayList<>();

    private final Lazy<InterpreterUniverse> universe;

    @SuppressWarnings("this-escape")
    public DebuggerSupport() {
        this.universe = Lazy.of(() -> {
            logForcedReferencesHistogram(this.referencesInImage, this.methodPointersInImage);
            try {
                return InterpreterUniverseImpl.loadFrom(getUniverseSerializerBuilder(), false, getMetadataHashString(), getMetadataFilePath());
            } catch (IOException e) {
                throw new RuntimeException(e);
            } catch (Exception e) {
                if (InterpreterOptions.InterpreterTraceSupport.getValue() && InterpreterOptions.InterpreterTrace.getValue()) {
                    e.printStackTrace();
                }
                throw VMError.shouldNotReachHere(e);
            }
        });
    }

    @Fold
    public static boolean isEnabled() {
        return ImageSingletons.contains(DebuggerSupport.class);
    }

    @Fold
    public static DebuggerSupport singleton() {
        return ImageSingletons.lookup(DebuggerSupport.class);
    }

    public static Path getMetadataFilePath() {
        return MetadataUtil.metadataFilePath(Path.of(ProcessProperties.getExecutableName()));
    }

    public static String getMetadataHashString() {
        Pointer base = IMAGE_INTERP_HASH.get();
        int length = base.readInt(0);
        byte[] bytes = new byte[length];
        for (int i = 0; i < length; ++i) {
            bytes[i] = base.readByte(4 + i);
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }

    private static void logForcedReferencesHistogram(ArrayList<Object> references, ArrayList<FunctionPointerHolder> methodPointers) {
        traceInterpreter("Forced constants: ").signed(references.size()).newline();
        traceInterpreter("Forced method pointers: ").signed(methodPointers.size()).newline();
        traceInterpreter("Forced constants histogram:");
        EconomicMap<Class<?>, Integer> histogram = EconomicMap.create();
        for (Object object : references) {
            histogram.put(object.getClass(), histogram.get(object.getClass(), 0) + 1);
        }
        MapCursor<Class<?>, Integer> cursor = histogram.getEntries();
        while (cursor.advance()) {
            traceInterpreter("  ").string(cursor.getKey().toString()).string(" ").string(cursor.getValue().toString()).newline();
        }
    }

    /**
     * Returns the interpreter "type" for a specific {@link Class}, or null if it doesn't exist or
     * if the liaison wasn't registered at build time. <b>This is an internal API meant for
     * debugging and testing purposes only.</b>
     */
    // GR-55023: should be in InterpreterSupport
    public static ResolvedJavaType lookupType(Class<?> declaringClass) {
        return InterpreterDirectivesSupportImpl.getInterpreterType(declaringClass);
    }

    /**
     * Returns the interpreter "method" for a specific {@link Executable}, or null if it doesn't
     * exist or if the liaison wasn't registered at build time. <b>This is an internal API meant for
     * debugging and testing purposes only.</b>
     */
    // GR-55023: should be in InterpreterSupport
    public static ResolvedJavaMethod lookupMethod(ResolvedJavaType clazz, String methodName, Class<?> returnType, Class<?>... parameterTypes) {
        VMError.guarantee(clazz instanceof InterpreterResolvedJavaType);
        return InterpreterDirectivesSupportImpl.getInterpreterMethod((InterpreterResolvedJavaType) clazz, methodName, returnType, parameterTypes);
    }

    @SuppressWarnings("static-method")
    public SerializationContext.Builder getUniverseSerializerBuilder() {
        return READER_BUILDER;
    }

    // GR-55023: should be in InterpreterSupport
    public InterpreterUniverse getUniverse() {
        return universe.get();
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    void trimForcedReferencesInImageHeap() {
        Set<Object> unique = Collections.newSetFromMap(new IdentityHashMap<>());
        unique.addAll(this.referencesInImage);
        this.referencesInImage = new ArrayList<>(unique);
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    void ensureConstantIsInImageHeap(SnippetReflectionProvider snippetReflectionProvider, ImageHeapConstant imageHeapConstant) {
        if (imageHeapConstant.isBackedByHostedObject()) {
            Object value = snippetReflectionProvider.asObject(Object.class, imageHeapConstant.getHostedObject());
            VMError.guarantee(value != null);
            referencesInImage.add(value);
        } else {
            throw VMError.shouldNotReachHere("Constant is not backed: " + imageHeapConstant);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void ensureMethodPointerIsInImage(FunctionPointerHolder value) {
        if (value != null) {
            methodPointersInImage.add(value);
        }
    }

    @Platforms(Platform.HOSTED_ONLY.class)
    public void buildMethodIdMapping(ResolvedJavaMethod[] encodedMethods) {
        assert encodedMethods[0] == null;
        for (int i = 1; i < encodedMethods.length; i++) {
            ResolvedJavaMethod method = encodedMethods[i];
            if (method != null) {
                InterpreterResolvedJavaMethod interpreterMethod = BuildTimeInterpreterUniverse.singleton().getMethod(method);
                interpreterMethod.setMethodId(i);
            }
        }
    }
}
