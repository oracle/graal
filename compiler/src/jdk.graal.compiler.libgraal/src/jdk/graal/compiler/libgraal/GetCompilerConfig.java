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
package jdk.graal.compiler.libgraal;

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

import org.graalvm.nativeimage.ImageInfo;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;

import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.hotspot.CompilerConfig;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.graal.compiler.util.ObjectCopier;

/**
 * Gets the map created in a JVM subprocess by running {@link CompilerConfig}.
 */
@Platforms(Platform.HOSTED_ONLY.class)
public class GetCompilerConfig {

    private static final boolean DEBUG = Boolean.parseBoolean(GraalServices.getSavedProperty("debug." + GetCompilerConfig.class.getName()));

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
     */
    public static Result from(Path javaHome) {
        Path javaExe = GetJNIConfig.getJavaExe(javaHome);
        Map<String, Set<String>> opens = CollectionsUtil.mapOf(
                        // Needed to reflect fields like
                        // java.util.ImmutableCollections.EMPTY
                        "java.base", CollectionsUtil.setOf("java.util"));

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

                        // Required to use Modules class
                        "--add-exports=java.base/jdk.internal.module=jdk.graal.compiler",
                        addExports,
                        "-Djdk.vm.ci.services.aot=true", // Remove after JDK-8346781
                        "-D%s=%s".formatted(ImageInfo.PROPERTY_IMAGE_CODE_KEY, ImageInfo.PROPERTY_IMAGE_CODE_VALUE_BUILDTIME)));

        for (var e : opens.entrySet()) {
            for (String source : e.getValue()) {
                command.add("--add-opens=%s/%s=jdk.graal.compiler".formatted(e.getKey(), source));
            }
        }

        if (Assertions.assertionsEnabled()) {
            command.add("-esa");
            command.add("-ea");
        }

        command.add(CompilerConfig.class.getName());
        String base = GetCompilerConfig.class.getSimpleName() + "_" + ProcessHandle.current().pid();
        Path encodedConfigPath = Path.of(base + ".bin").toAbsolutePath();
        Path debugPath = Path.of(base + ".txt").toAbsolutePath();
        command.add(encodedConfigPath.toString());
        command.add(debugPath.toString());

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
                System.out.printf("[%d] Compiler config output saved in '%s'%n", p.pid(), encodedConfigPath);
                System.out.printf("[%d] Compiler config debug output saved in '%s'%n", p.pid(), debugPath);
            } else {
                Files.deleteIfExists(encodedConfigPath);
                Files.deleteIfExists(debugPath);
            }
            return new Result(encodedConfig, opens);
        } catch (IOException e) {
            throw new GraalError(e);
        }
    }
}
