/*
 * Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.graal.hotspot;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.graalvm.collections.UnmodifiableEconomicMap;
import org.graalvm.collections.UnmodifiableMapCursor;

import com.oracle.graal.pointsto.api.PointstoOptions;
import com.oracle.svm.core.option.HostedOptionKey;
import com.oracle.svm.core.option.RuntimeOptionKey;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.HotSpotGraalOptionValues;
import jdk.graal.compiler.hotspot.libgraal.CompilerConfig;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.util.ObjectCopier;
import org.graalvm.nativeimage.ImageInfo;

/**
 * Gets the map created in a JVM subprocess by running {@link CompilerConfig}.
 */
public class GetCompilerConfig {

    private static final boolean DEBUG = Boolean.getBoolean("debug." + GetCompilerConfig.class.getName());

    /**
     * Result returned by {@link GetCompilerConfig#from}.
     *
     * @param encodedConfig the {@linkplain CompilerConfig config} serialized to a string
     * @param opens map from a module to the set of packages opened to the module defining
     *            {@link ObjectCopier}. These packages need to be opened when decoding the returned
     *            string back to an object.
     */
    public record Result(byte[] encodedConfig, Map<String, Set<String>> opens) {
    }

    /**
     * Tests whether {@code module} is in the boot layer.
     *
     * @param javaExe java executable
     * @param module name of the module to test
     */
    private static boolean isInBootLayer(Path javaExe, String module) {
        String search = "jrt:/" + module;
        String[] command = {javaExe.toString(), "--show-module-resolution", "--version"};
        try {
            Process p = new ProcessBuilder(command).start();
            BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
            String line;
            boolean found = false;
            while ((line = reader.readLine()) != null) {
                if (line.contains(search)) {
                    found = true;
                }
            }
            int exitValue = p.waitFor();
            if (exitValue != 0) {
                throw new GraalError("Command finished with exit value %d: %s", exitValue, String.join(" ", command));
            }
            return found;
        } catch (Exception e) {
            throw new GraalError(e, "Error running command: %s", String.join(" ", command));
        }
    }

    /**
     * Launches the JVM in {@code javaHome} to run {@link CompilerConfig}.
     *
     * @param javaHome the value of the {@code java.home} system property reported by a Java
     *            installation directory that includes the Graal classes in its runtime image
     * @param options the options passed to native-image
     */
    public static Result from(Path javaHome, OptionValues options) {
        Path javaExe = GetJNIConfig.getJavaExe(javaHome);
        UnmodifiableEconomicMap<OptionKey<?>, Object> optionsMap = options.getMap();
        UnmodifiableMapCursor<OptionKey<?>, Object> entries = optionsMap.getEntries();
        Map<String, Set<String>> opens = Map.of(
                        // Needed to reflect fields like
                        // java.util.ImmutableCollections.EMPTY
                        "java.base", Set.of("java.util"));

        // Only modules in the boot layer can be the target of --add-exports
        String addExports = "--add-exports=java.base/jdk.internal.misc=jdk.graal.compiler";
        String ee = "com.oracle.graal.graal_enterprise";
        if (isInBootLayer(javaExe, ee)) {
            addExports += "," + ee;
        }

        List<String> command = new ArrayList<>(List.of(
                        javaExe.toFile().getAbsolutePath(),
                        "-XX:+UnlockExperimentalVMOptions",
                        "-XX:+EnableJVMCI",
                        "-XX:-UseJVMCICompiler", // avoid deadlock with jargraal
                        addExports,
                        "-Djdk.vm.ci.services.aot=true",
                        "-D%s=%s".formatted(ImageInfo.PROPERTY_IMAGE_CODE_KEY, ImageInfo.PROPERTY_IMAGE_CODE_VALUE_BUILDTIME)));

        Module module = ObjectCopier.class.getModule();
        String target = module.isNamed() ? module.getName() : "ALL-UNNAMED";
        for (var e : opens.entrySet()) {
            for (String source : e.getValue()) {
                command.add("--add-opens=%s/%s=%s".formatted(e.getKey(), source, target));
            }
        }

        // Propagate compiler options
        while (entries.advance()) {
            OptionKey<?> key = entries.getKey();
            if (key instanceof RuntimeOptionKey || key instanceof HostedOptionKey) {
                // Ignore Native Image options
                continue;
            }
            if (key.getDescriptor().getDeclaringClass().getModule().equals(PointstoOptions.class.getModule())) {
                // Ignore points-to analysis options
                continue;
            }
            command.add("-D%s%s=%s".formatted(HotSpotGraalOptionValues.GRAAL_OPTION_PROPERTY_PREFIX, key.getName(), entries.getValue()));
        }

        command.add(CompilerConfig.class.getName());
        Path encodedConfigPath = Path.of(GetCompilerConfig.class.getSimpleName() + "_" + ProcessHandle.current().pid() + ".txt").toAbsolutePath();
        command.add(encodedConfigPath.toString());

        String quotedCommand = command.stream().map(e -> e.indexOf(' ') == -1 ? e : '\'' + e + '\'').collect(Collectors.joining(" "));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.inheritIO();
        Process p;
        try {
            p = pb.start();
        } catch (IOException ex) {
            throw new GraalError("Error running command: %s%n%s", quotedCommand, ex);
        }

        try {
            int exitValue = p.waitFor();
            if (exitValue != 0) {
                throw new GraalError("Command finished with exit value %d (look for stdout and stderr above): %s", exitValue, quotedCommand);
            }
        } catch (InterruptedException e) {
            throw new GraalError("Interrupted waiting for command: %s", quotedCommand);
        }
        try {
            byte[] encodedConfig = Files.readAllBytes(encodedConfigPath);
            if (DEBUG) {
                System.out.printf("[%d] Executed: %s%n", p.pid(), quotedCommand);
                System.out.printf("[%d] Output saved in %s%n", p.pid(), encodedConfigPath);
            } else {
                Files.deleteIfExists(encodedConfigPath);
            }
            return new Result(encodedConfig, opens);
        } catch (IOException e) {
            throw new GraalError(e);
        }
    }
}
