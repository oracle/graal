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
import java.lang.reflect.Array;
import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.hosted.RuntimeJNIAccess;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.graal.compiler.util.SignatureUtil;

/**
 * Registers the JNI configuration for libgraal by parsing the output of the
 * {@code -XX:JVMCILibDumpJNIConfig} VM option.
 */
@Platforms(Platform.HOSTED_ONLY.class)
final class GetJNIConfig implements AutoCloseable {
    /**
     * VM command executed to read the JNI config.
     */
    private final String quotedCommand;

    /**
     * JNI config lines.
     */
    private final List<String> lines;

    /**
     * Loader used to resolve type names in the config.
     */
    private final ClassLoader loader;

    /**
     * Path to intermediate file containing the config. This is deleted unless there is an
     * {@link #error(String, Object...)} parsing the config to make diagnosing the error easier.
     */
    private Path configFilePath;

    int lineNo;

    private GetJNIConfig(ClassLoader loader, Path libgraalJavaHome) {
        this.loader = loader;
        Path javaExe = getJavaExe(libgraalJavaHome);
        configFilePath = Path.of("libgraal_jniconfig.txt");

        String[] command = {javaExe.toFile().getAbsolutePath(), "-XX:+UnlockExperimentalVMOptions", "-XX:+EnableJVMCI", "-XX:JVMCILibDumpJNIConfig=" + configFilePath};
        quotedCommand = Stream.of(command).map(e -> e.indexOf(' ') == -1 ? e : '\'' + e + '\'').collect(Collectors.joining(" "));
        ProcessBuilder pb = new ProcessBuilder(command);
        pb.redirectErrorStream(true);
        Process p;
        try {
            p = pb.start();
        } catch (IOException e) {
            throw new GraalError(e, "Could not run command: %s", quotedCommand);
        }

        String nl = System.lineSeparator();
        String out = new BufferedReader(new InputStreamReader(p.getInputStream())).lines().collect(Collectors.joining(nl));

        int exitValue;
        try {
            exitValue = p.waitFor();
        } catch (InterruptedException e) {
            throw new GraalError(e, "Interrupted waiting for command: %s%n%s", quotedCommand, out);
        }
        if (exitValue != 0) {
            throw new GraalError("Command finished with exit value %d: %s%n%s", exitValue, quotedCommand, out);
        }
        try {
            lines = Files.readAllLines(configFilePath);
        } catch (IOException e) {
            Path cfp = configFilePath;
            configFilePath = null;
            throw new GraalError("Reading JNI config in %s dumped by command: %s%n%s", cfp, quotedCommand, out);
        }
    }

    static Path getJavaExe(Path javaHome) {
        boolean isWindows = GraalServices.getSavedProperty("os.name").contains("Windows");
        Path javaExe = javaHome.resolve(Path.of("bin", isWindows ? "java.exe" : "java"));
        if (!Files.isExecutable(javaExe)) {
            throw new GraalError("Java launcher %s does not exist or is not executable", javaExe);
        }
        return javaExe;
    }

    @Override
    public void close() {
        if (configFilePath != null && Files.exists(configFilePath)) {
            try {
                Files.delete(configFilePath);
                configFilePath = null;
            } catch (IOException e) {
                throw new GraalError(e, "Could not delete %s", configFilePath);
            }
        }
    }

    public static Class<?> forPrimitive(String name) {
        return switch (name) {
            case "Z", "boolean" -> boolean.class;
            case "C", "char" -> char.class;
            case "F", "float" -> float.class;
            case "D", "double" -> double.class;
            case "B", "byte" -> byte.class;
            case "S", "short" -> short.class;
            case "I", "int" -> int.class;
            case "J", "long" -> long.class;
            case "V", "void" -> void.class;
            default -> null;
        };
    }

    private Class<?> findClass(String name) {
        String internalName = name;
        if (name.startsWith("L") && name.endsWith(";")) {
            internalName = name.substring(1, name.length() - 1);
        }
        var primitive = forPrimitive(internalName);
        if (primitive != null) {
            return primitive;
        }
        try {
            return Class.forName(internalName, false, loader);
        } catch (ClassNotFoundException e) {
            String externalName = internalName.replace('/', '.');
            try {
                return Class.forName(externalName, false, loader);
            } catch (ClassNotFoundException e3) {
                // ignore
            }
            throw new GraalError(e, "Cannot find class %s during LibGraal JNIConfiguration registration", name);
        }
    }

    private void check(boolean condition, String format, Object... args) {
        if (!condition) {
            throw error(format, args);
        }
    }

    private GraalError error(String format, Object... args) {
        Path path = configFilePath;
        configFilePath = null; // prevent deletion
        String errorMessage = format.formatted(args);
        String errorLine = lines.get(lineNo - 1);
        throw new GraalError("Line %d of %s: %s%n%s%n%s generated by command: %s",
                        lineNo, path.toAbsolutePath(), errorMessage, errorLine, path, quotedCommand);

    }

    /**
     * Registers the JNI configuration for libgraal by parsing the output of the
     * {@code -XX:JVMCILibDumpJNIConfig} VM option.
     *
     * @param loader used to resolve type names in the config
     */
    @SuppressWarnings("try")
    public static void register(ClassLoader loader, Path libgraalJavaHome) {
        try (GetJNIConfig source = new GetJNIConfig(loader, libgraalJavaHome)) {
            Map<String, Class<?>> classes = new EconomicHashMap<>();
            for (String line : source.lines) {
                source.lineNo++;
                String[] tokens = line.split(" ");
                source.check(tokens.length >= 2, "Expected at least 2 tokens");
                String className = tokens[1].replace('/', '.');
                Class<?> clazz = classes.get(className);
                if (clazz == null) {
                    clazz = source.findClass(className);
                    RuntimeJNIAccess.register(clazz);
                    RuntimeJNIAccess.register(Array.newInstance(clazz, 0).getClass());
                    classes.put(className, clazz);
                }

                switch (tokens[0]) {
                    case "field": {
                        source.check(tokens.length == 4, "Expected 4 tokens for a field");
                        String fieldName = tokens[2];
                        try {
                            RuntimeJNIAccess.register(clazz.getDeclaredField(fieldName));
                        } catch (NoSuchFieldException e) {
                            throw source.error("Field %s.%s not found", clazz.getTypeName(), fieldName);
                        } catch (NoClassDefFoundError e) {
                            throw source.error("Could not register field %s.%s: %s", clazz.getTypeName(), fieldName, e);
                        }
                        break;
                    }
                    case "method": {
                        source.check(tokens.length == 4, "Expected 4 tokens for a method");
                        String methodName = tokens[2];
                        String signature = tokens[3];
                        ArrayList<String> buffer = new ArrayList<>();
                        SignatureUtil.parseSignature(signature, buffer);
                        Class<?>[] parameters = buffer.stream()//
                                        .map(source::findClass)//
                                        .toList()//
                                        .toArray(new Class<?>[0]);
                        assert Arrays.stream(parameters).allMatch(pclazz -> pclazz.getClassLoader() == null || pclazz.getClassLoader() == loader);
                        try {
                            if ("<init>".equals(methodName)) {
                                Constructor<?> cons = clazz.getDeclaredConstructor(parameters);
                                RuntimeJNIAccess.register(cons);
                                if (Throwable.class.isAssignableFrom(clazz) && !Modifier.isAbstract(clazz.getModifiers())) {
                                    if (usedInTranslatedException(parameters)) {
                                        RuntimeReflection.register(clazz);
                                        RuntimeReflection.register(cons);
                                    }
                                }
                            } else {
                                RuntimeJNIAccess.register(clazz.getDeclaredMethod(methodName, parameters));
                            }
                        } catch (NoSuchMethodException e) {
                            throw source.error("Method %s.%s%s not found: %s", clazz.getTypeName(), methodName, signature, e);
                        } catch (NoClassDefFoundError e) {
                            throw source.error("Could not register method %s.%s%s: %s", clazz.getTypeName(), methodName, signature, e);
                        }
                        break;
                    }
                    case "class": {
                        source.check(tokens.length == 2, "Expected 2 tokens for a class");
                        break;
                    }
                    default: {
                        throw source.error("Unexpected token: " + tokens[0]);
                    }
                }
            }
        }
    }

    /**
     * Determines if a throwable constructor with the signature specified by {@code parameters} is
     * potentially called via reflection in {@code jdk.vm.ci.hotspot.TranslatedException}.
     */
    private static boolean usedInTranslatedException(Class<?>[] parameters) {
        return parameters.length == 0 || (parameters.length == 1 && parameters[0] == String.class);
    }
}
