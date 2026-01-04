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
package jdk.graal.compiler.hotspot;

import java.io.FileOutputStream;
import java.io.PrintStream;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Formatter;
import java.util.List;
import java.util.Locale;

import org.graalvm.collections.EconomicMap;
import org.graalvm.collections.MapCursor;

import jdk.graal.compiler.core.common.Fields;
import jdk.graal.compiler.core.common.LibGraalSupport;
import jdk.graal.compiler.core.common.spi.ForeignCallSignature;
import jdk.graal.compiler.core.target.Backend;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.hotspot.meta.HotSpotHostForeignCallsProvider;
import jdk.graal.compiler.hotspot.meta.HotSpotProviders;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.truffle.hotspot.HotSpotTruffleCompilerImpl;
import jdk.graal.compiler.util.ObjectCopier;
import jdk.vm.ci.hotspot.HotSpotJVMCIRuntime;

/**
 * A command line program that initializes the compiler data structures to be serialized into the
 * libgraal image. The data structures are returned in a map with the following entries:
 * <ul>
 * *
 * <li>"encodedSnippets" -> value returned by
 * {@link SymbolicSnippetEncoder#encodeSnippets(OptionValues)}</li>
 * <li>"snippetNodeClasses" -> a {@link #snippetNodeClassesToJSON JSON dump} of the snippet node
 * classes</li>
 * <li>"foreignCallSignatures" -> value that is passed to
 * {@link jdk.graal.compiler.hotspot.HotSpotForeignCallLinkage.Stubs#initStubs}</li>
 * </ul>
 */
@LibGraalSupport.HostedOnly
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

        List<ForeignCallSignature> foreignCallSignatures = getForeignCallSignatures(replacements, options);
        EncodedSnippets encodedSnippets = getEncodedSnippets(replacements, options);
        List<Field> externalValueFields = ObjectCopier.getExternalValueFields();

        EconomicMap<String, Object> encodedObjects = EconomicMap.create();
        encodedObjects.put("encodedSnippets", encodedSnippets);
        encodedObjects.put("snippetNodeClasses", snippetNodeClassesToJSON(encodedSnippets));
        encodedObjects.put("foreignCallSignatures", foreignCallSignatures);

        try (PrintStream debugStream = new PrintStream(new FileOutputStream(args[1]))) {
            ObjectCopier.Encoder encoder = new ObjectCopier.Encoder(externalValueFields, debugStream) {
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
            byte[] encoded = ObjectCopier.encode(encoder, encodedObjects);
            Files.write(Path.of(args[0]), encoded);
        }
    }

    private static EncodedSnippets getEncodedSnippets(HotSpotReplacementsImpl replacements, OptionValues options) {
        GraalError.guarantee(!HotSpotReplacementsImpl.snippetsAreEncoded(), "snippets should not be encoded");
        SymbolicSnippetEncoder snippetEncoder = replacements.maybeInitializeEncoder();
        return snippetEncoder.encodeSnippets(options);
    }

    private static List<ForeignCallSignature> getForeignCallSignatures(HotSpotReplacementsImpl replacements, OptionValues options) {
        List<ForeignCallSignature> sigs = new ArrayList<>();
        EconomicMap<ForeignCallSignature, HotSpotForeignCallLinkage> foreignCalls = collectForeignCalls(replacements, options);
        MapCursor<ForeignCallSignature, HotSpotForeignCallLinkage> cursor = foreignCalls.getEntries();
        while (cursor.advance()) {
            sigs.add(cursor.getKey());
            HotSpotForeignCallLinkage linkage = cursor.getValue();
            if (linkage != null && linkage.isCompiledStub()) {
                // Construct the stub graph so that all types it uses are registered in
                // SymbolicSnippetEncoder.snippetTypes
                linkage.getStub().findTypesInGraph();
            }
        }
        return sigs;
    }

    private static EconomicMap<ForeignCallSignature, HotSpotForeignCallLinkage> collectForeignCalls(HotSpotReplacementsImpl replacements,
                    OptionValues options) {
        EconomicMap<ForeignCallSignature, HotSpotForeignCallLinkage> allForeignCalls = EconomicMap.create();
        HotSpotProviders providers = replacements.getProviders();
        collectForeignCalls(providers.getForeignCalls(), allForeignCalls);

        /*
         * Instantiate the Truffle compiler to collect its foreign calls as well. Ensure the
         * snippets are registered using this encoder.
         */
        replacements.shareSnippetEncoder();
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

    /**
     * Gets the info in {@code encodedSnippets.getSnippetNodeClasses()} used by
     * {@link jdk.graal.compiler.nodes.GraphEncoder} as JSON. This is used to verify that the
     * {@link NodeClass} in snippets have the same layout in the encoding and decoding JVM
     * processes.
     */
    public static String snippetNodeClassesToJSON(EncodedSnippets encodedSnippets) {
        Formatter out = new Formatter();
        out.format("[");
        String sep = "";
        for (NodeClass<?> nc : encodedSnippets.getSnippetNodeClasses()) {
            out.format("%s{\"clazz\":\"%s\"", sep, nc.getClazz().getName());
            formatFields(out, "inputs", nc.getInputEdges());
            formatFields(out, "properties", nc.getData());
            formatFields(out, "successors", nc.getSuccessorEdges());
            out.format("}");
            sep = ",";
        }
        out.format("]");
        return out.toString();
    }

    private static void formatFields(Formatter out, String name, Fields fields) {
        out.format(",\"%s\":[", name);
        String sep = "";
        for (int i = 0; i < fields.getCount(); i++) {
            out.format("%s\"%s.%s:%s\"",
                            sep,
                            fields.getDeclaringClass(i).getName(),
                            fields.getName(i),
                            fields.getType(i).getName());
            sep = ",";
        }
        out.format("]");
    }
}
