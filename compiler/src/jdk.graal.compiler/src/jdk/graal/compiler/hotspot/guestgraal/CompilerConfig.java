/*
 * Copyright (c) 2024, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.hotspot.guestgraal;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.net.URI;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import jdk.graal.compiler.core.common.spi.ForeignCallSignature;
import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.hotspot.HotSpotForeignCallLinkage;
import jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import jdk.graal.compiler.truffle.hotspot.HotSpotTruffleCompilerImpl;
import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;
import org.graalvm.word.LocationIdentity;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.EncodedSnippets;
import jdk.graal.compiler.hotspot.HotSpotGraalCompiler;
import jdk.graal.compiler.hotspot.HotSpotGraalRuntimeProvider;
import jdk.graal.compiler.hotspot.HotSpotReplacementsImpl;
import jdk.graal.compiler.hotspot.SymbolicSnippetEncoder;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.util.ObjectCopier;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;

/**
 * A command line program that initializes the compiler data structures to be serialized into the
 * libgraal image. The data structures are returned in a map with the following entries:
 * <ul>
 * *
 * <li>"encodedSnippets" -> value returned by
 * {@link SymbolicSnippetEncoder#encodeSnippets(OptionValues)}</li>
 * <li>"foreignCallSignatures" -> value that is passed to
 * {@link jdk.graal.compiler.hotspot.HotSpotForeignCallLinkage.Stubs#initStubs}</li>
 * </ul>
 */
public class CompilerConfig {

    /**
     * A program that prints the compiler configuration serialized to a String by
     * {@link ObjectCopier#encode}. The output is printed to the file path in {@code args[0]}.
     */
    public static void main(String[] args) throws Exception {
        HotSpotGraalCompiler graalCompiler = (HotSpotGraalCompiler) HotSpotJVMCIRuntime.runtime().getCompiler();
        HotSpotGraalRuntimeProvider graalRuntime = graalCompiler.getGraalRuntime();
        HotSpotProviders hostProviders = graalRuntime.getHostProviders();
        HotSpotReplacementsImpl replacements = (HotSpotReplacementsImpl) hostProviders.getReplacements();
        OptionValues options = graalRuntime.getCapability(OptionValues.class);

        List<ForeignCallSignature> foreignCallSignatures = getForeignCallSignatures(replacements, options, graalRuntime);
        EncodedSnippets encodedSnippets = getEncodedSnippets(replacements, options);
        List<Field> externalValues = getExternalValues();

        EconomicMap<String, Object> encodedObjects = EconomicMap.create();
        encodedObjects.put("encodedSnippets", encodedSnippets);
        encodedObjects.put("foreignCallSignatures", foreignCallSignatures);

        ObjectCopier.Encoder encoder = new ObjectCopier.Encoder(externalValues) {
            @Override
            protected ClassInfo makeClassInfo(Class<?> declaringClass) {
                ClassInfo ci = ClassInfo.of(declaringClass);
                for (var f : ci.fields().values()) {
                    // Avoid problems with identity hash codes
                    GraalError.guarantee(!f.getName().toLowerCase(Locale.ROOT).contains("hash"), "Cannot serialize hash field: %s", f);
                }
                return ci;
            }
        };
        String encoded = ObjectCopier.encode(encoder, encodedObjects);

        Files.writeString(Path.of(args[0]), encoded);
    }

    private static EncodedSnippets getEncodedSnippets(HotSpotReplacementsImpl replacements, OptionValues options) {
        SymbolicSnippetEncoder snippetEncoder = replacements.maybeInitializeEncoder();
        return snippetEncoder.encodeSnippets(options);
    }

    private static List<ForeignCallSignature> getForeignCallSignatures(HotSpotReplacementsImpl replacements, OptionValues options, HotSpotGraalRuntimeProvider graalRuntime) {
        List<ForeignCallSignature> sigs = new ArrayList<>();
        EconomicMap<ForeignCallSignature, HotSpotForeignCallLinkage> foreignCalls = collectForeignCalls(replacements, options);
        MapCursor<ForeignCallSignature, HotSpotForeignCallLinkage> cursor = foreignCalls.getEntries();
        while (cursor.advance()) {
            ForeignCallSignature sig = cursor.getKey();
            HotSpotForeignCallLinkage linkage = cursor.getValue();
            sigs.add(sig);
            if (linkage != null) {
                // Construct the stub so that all types it uses are registered in
                // SymbolicSnippetEncoder.snippetTypes
                linkage.finalizeAddress(graalRuntime.getHostBackend());
            }
        }
        return sigs;
    }

    private static EconomicMap<ForeignCallSignature, HotSpotForeignCallLinkage> collectForeignCalls(HotSpotReplacementsImpl replacements,
                    OptionValues options) {
        EconomicMap<ForeignCallSignature, HotSpotForeignCallLinkage> allForeignCalls = EconomicMap.create();
        HotSpotProviders providers = replacements.getProviders();
        collectForeignCalls(providers.getForeignCalls(), allForeignCalls);

        // Instantiate the Truffle compiler to collect its foreign calls as well
        for (Backend truffleBackend : HotSpotTruffleCompilerImpl.ensureBackendsInitialized(options)) {
            HotSpotProviders truffleProviders = (HotSpotProviders) truffleBackend.getProviders();
            collectForeignCalls(truffleProviders.getForeignCalls(), allForeignCalls);
        }
        return allForeignCalls;
    }

    private static void collectForeignCalls(HotSpotHostForeignCallsProvider foreignCalls,
                    EconomicMap<ForeignCallSignature, HotSpotForeignCallLinkage> allForeignCalls) {
        foreignCalls.forEachForeignCall((sig, linkage) -> {
            if (linkage == null || linkage.isCompiledStub()) {
                if (!allForeignCalls.containsKey(sig)) {
                    allForeignCalls.put(sig, linkage);
                }
            }
        });
    }

    private static List<Field> getExternalValues() throws IOException {
        List<Field> externalValues = new ArrayList<>();
        addImmutableCollectionsFields(externalValues);
        addStaticFinalObjectFields(LocationIdentity.class, externalValues);

        try (FileSystem fs = FileSystems.newFileSystem(URI.create("jrt:/"), Collections.emptyMap())) {
            for (String module : List.of("jdk.internal.vm.ci", "jdk.graal.compiler", "com.oracle.graal.graal_enterprise")) {
                Path top = fs.getPath("/modules/" + module);
                try (Stream<Path> files = Files.find(top, Integer.MAX_VALUE, (path, attrs) -> attrs.isRegularFile())) {
                    files.forEach(p -> {
                        String fileName = p.getFileName().toString();
                        if (fileName.endsWith(".class") && !fileName.equals("module-info.class")) {
                            // Strip module prefix and convert to dotted form
                            int nameCount = p.getNameCount();
                            String className = p.subpath(2, nameCount).toString().replace('/', '.');
                            // Strip ".class" suffix
                            className = className.replace('/', '.').substring(0, className.length() - ".class".length());
                            try {
                                Class<?> graalClass = Class.forName(className);
                                addStaticFinalObjectFields(graalClass, externalValues);
                            } catch (ClassNotFoundException e) {
                                throw new GraalError(e);
                            }
                        }
                    });
                }
            }
        }
        return externalValues;
    }

    /**
     * Adds the static, final, non-primitive fields of non-enum {@code declaringClass} to
     * {@code fields}. In the process, the fields are made {@linkplain Field#setAccessible
     * accessible}.
     */
    private static void addStaticFinalObjectFields(Class<?> declaringClass, List<Field> fields) {
        if (Enum.class.isAssignableFrom(declaringClass)) {
            return;
        }
        for (Field field : declaringClass.getDeclaredFields()) {
            int fieldModifiers = field.getModifiers();
            int fieldMask = Modifier.STATIC | Modifier.FINAL;
            if ((fieldModifiers & fieldMask) != fieldMask) {
                continue;
            }
            if (field.getType().isPrimitive()) {
                continue;
            }
            field.setAccessible(true);
            fields.add(field);
        }
    }

    /**
     * Adds the EMPTY* fields from {@code java.util.ImmutableCollections} to {@code fields}, making
     * them {@linkplain Field#setAccessible accessible} in the process.
     */
    private static void addImmutableCollectionsFields(List<Field> fields) {
        Class<?> c = List.of().getClass().getDeclaringClass();
        GraalError.guarantee(c.getName().equals("java.util.ImmutableCollections"), "Incompatible ImmutableCollections class");
        for (Field f : c.getDeclaredFields()) {
            if (f.getName().startsWith("EMPTY")) {
                int modifiers = f.getModifiers();
                GraalError.guarantee(Modifier.isStatic(modifiers), "Expect %s to be static", f);
                GraalError.guarantee(Modifier.isFinal(modifiers), "Expect %s to be final", f);
                GraalError.guarantee(!f.getType().isPrimitive(), "Expect %s to be non-primitive", f);
                f.setAccessible(true);
                fields.add(f);
            }
        }
    }
}
