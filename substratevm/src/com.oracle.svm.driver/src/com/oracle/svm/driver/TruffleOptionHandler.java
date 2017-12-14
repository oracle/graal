/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.svm.driver;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Queue;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Stream;

import com.oracle.svm.truffle.TruffleFeature;

abstract class TruffleOptionHandler implements NativeImageOptionHandler {
    @Override
    public final boolean consume(Queue<String> args, ImageBuilderConfig config) {
        if (!("--" + getTruffleLanguageOptionName()).equals(args.peek())) {
            return false;
        }
        args.poll();

        if (config.isRelease()) {
            Path truffleDir = config.getRootDir().resolve("jre/lib/truffle");
            config.addImageBuilderBootClasspath(truffleDir.resolve("truffle-api.jar"));
        }

        config.addImageBuilderJavaArgs(
                        "-Dgraalvm.locatorDisabled=true",
                        "-Dcom.oracle.truffle.aot=true",
                        "-Dtruffle.TruffleRuntime=com.oracle.svm.truffle.api.SubstrateTruffleRuntime");

        config.addImageBuilderSubstitutions("com/oracle/truffle/tools/chromeinspector/aot/substitutions.json");
        config.addImageBuilderFeatures(TruffleFeature.class.getName());

        applyTruffleLanguageOptions(config, config.getTruffleLanguageJars(this));
        config.addTruffleLanguage(this);

        return true;
    }

    static void applyTruffleShellOptions(ImageBuilderConfig config) {
        Path graalvmDir = config.getRootDir().resolve(Paths.get("jre", "lib", "graalvm"));

        Stream<Path> jars;
        try {
            if (Files.isDirectory(graalvmDir)) {
                jars = Files.list(graalvmDir).filter(Files::isRegularFile).filter(p -> p.getFileName().toString().toLowerCase().endsWith(".jar"));
            } else {
                jars = Stream.empty();
            }
        } catch (IOException e) {
            throw new NativeImage.NativeImageError("Error while listing jars in " + graalvmDir, e);
        }

        StringJoiner launcherClassPath = new StringJoiner(File.pathSeparator, "-Dorg.graalvm.launcher.classpath=", "");

        jars.forEachOrdered(p -> {
            launcherClassPath.add(p.relativize(config.getRootDir()).toString());
            config.addImageClasspath(p);
        });

        config.addImageBuilderJavaArgs(launcherClassPath.toString());

        Path toolsDir = config.getRootDir().resolve(Paths.get("jre", "tools"));
        if (Files.exists(toolsDir)) {
            for (File language : toolsDir.toFile().listFiles()) {
                if (language.getName().startsWith(".")) {
                    continue;
                }
                if (language.isDirectory()) {
                    for (File jar : language.listFiles()) {
                        addJar(config, jar.toPath());
                    }
                } else {
                    addJar(config, language.toPath());
                }
            }
        }
    }

    private static void addJar(ImageBuilderConfig config, Path path) {
        if (Files.isRegularFile(path) && path.getFileName().toString().toLowerCase().endsWith(".jar")) {
            config.addImageBuilderClasspath(path);
        }
    }

    static void applyTruffleLanguagePolyglotShellOptions(ImageBuilderConfig config, Set<TruffleOptionHandler> usedTruffleLanguages) {
        if (config.isRelease()) {
            /*
             * We assume that each language will require roughly 1 GB of ram. This estimation is
             * based on how different images behave w.r.t. memory consumption.
             */
            int requiredGigabytesOfMemory = 4 + usedTruffleLanguages.size();
            config.addImageBuilderJavaArgs("-Xmx" + requiredGigabytesOfMemory + "G");

            StringJoiner launcherClasses = new StringJoiner(",");
            for (TruffleOptionHandler language : usedTruffleLanguages) {
                String launcherClass = language.getLauncherClass();
                if (launcherClass != null) {
                    launcherClasses.add(launcherClass);
                }
            }

            config.addImageBuilderJavaArgs(
                            "-Dcom.oracle.graalvm.launcher.launcherclasses=" + launcherClasses);

            config.addImageBuilderArgs(
                            "-H:Class=org.graalvm.launcher.PolyglotLauncher",
                            "-H:Name=polyglot");

            /*
             * config.addImageBuilderResourceBundles( "com.oracle.graalvm.Bundle",
             * "com.oracle.graalvm.cmdopts.Bundle");
             */
        } else {
            throw new NativeImage.NativeImageError("Building image with PolyglotShell only works in release mode");
        }
    }

    abstract String getTruffleLanguageName();

    abstract String getTruffleLanguageOptionName();

    String getTruffleLanguageDirectory() {
        return getTruffleLanguageOptionName();
    }

    abstract void applyTruffleLanguageOptions(ImageBuilderConfig config, Path... truffleLanguageJar);

    abstract void applyTruffleLanguageShellOptions(ImageBuilderConfig config);

    abstract String getLauncherClass();
}
