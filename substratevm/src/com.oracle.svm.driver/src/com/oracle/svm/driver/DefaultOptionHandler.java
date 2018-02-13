/*
 * Copyright (c) 2016, 2017, Oracle and/or its affiliates. All rights reserved.
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
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Queue;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

class DefaultOptionHandler extends NativeImage.OptionHandler<NativeImage> {

    DefaultOptionHandler(NativeImage nativeImage) {
        super(nativeImage);
    }

    @Override
    public boolean consume(Queue<String> args) {
        String headArg = args.peek();
        switch (headArg) {
            case "-?":
            case "-help":
                args.poll();
                nativeImage.showMessage(NativeImage.buildContext().helpText);
                System.exit(0);
                return true;
            case "-X":
                args.poll();
                nativeImage.showMessage(NativeImage.buildContext().helpTextX);
                System.exit(0);
                return true;
            case "-cp":
            case "-classpath":
                args.poll();
                String cpArgs = args.poll();
                if (cpArgs == null) {
                    NativeImage.showError("-cp requires class path specification");
                }
                for (String cp : cpArgs.split(":")) {
                    nativeImage.addCustomImageClasspath(Paths.get(cp));
                }
                return true;
            case "-jar":
                args.poll();
                String jarFilePathStr = args.poll();
                if (jarFilePathStr == null) {
                    NativeImage.showError("-jar requires jar file specification");
                }
                handleJarFileArg(Paths.get(jarFilePathStr).toFile());
                return true;
            case "-verbose":
                args.poll();
                nativeImage.setVerbose(true);
                return true;
        }

        String debugAttach = "-debug-attach";
        if (headArg.startsWith(debugAttach)) {
            String debugAttachArg = args.poll();
            String portSuffix = debugAttachArg.substring(debugAttach.length());
            int debugPort = 8000;
            if (!portSuffix.isEmpty()) {
                try {
                    debugPort = Integer.parseInt(portSuffix.substring(1));
                } catch (NumberFormatException e) {
                    NativeImage.showError("Invalid -debug-attach option: " + debugAttachArg);
                }
            }
            nativeImage.addImageBuilderJavaArgs("-Xdebug", "-Xrunjdwp:transport=dt_socket,server=y,address=" + debugPort + ",suspend=y");
            return true;
        }

        if (headArg.startsWith(NativeImage.oH) || headArg.startsWith(NativeImage.oR)) {
            args.poll();
            nativeImage.addCustomImageBuilderArgs(headArg);
            return true;
        }
        String javaArgsPrefix = "-D";
        if (headArg.startsWith(javaArgsPrefix)) {
            args.poll();
            nativeImage.addCustomJavaArgs(headArg);
            return true;
        }
        if (headArg.startsWith("-J")) {
            args.poll();
            if (headArg.equals("-J")) {
                NativeImage.showError("The -J option should not be followed by a space");
            } else {
                nativeImage.addCustomJavaArgs(headArg.substring(2));
            }
            return true;
        }
        String debugOption = "-g";
        if (headArg.equals(debugOption)) {
            args.poll();
            nativeImage.addImageBuilderArg(NativeImage.oHDebug + 2);
            return true;
        }
        String optimizeOption = "-O";
        if (headArg.startsWith(optimizeOption)) {
            args.poll();
            if (headArg.equals(optimizeOption)) {
                NativeImage.showError("The " + optimizeOption + " option should not be followed by a space");
            } else {
                nativeImage.addImageBuilderArg(NativeImage.oHOptimize + headArg.substring(2));
            }
            return true;
        }
        String enableRuntimeAssertions = "-ea";
        if (headArg.equals(enableRuntimeAssertions)) {
            args.poll();
            nativeImage.addImageBuilderArg(NativeImage.oH + '+' + NativeImage.RuntimeAssertions);
            return true;
        }
        return false;
    }

    private void handleJarFileArg(File file) {
        try {
            Manifest manifest = null;
            for (FastJar.Entry entry : FastJar.list(file)) {
                if ("META-INF/MANIFEST.MF".equals(entry.name)) {
                    manifest = new Manifest(FastJar.getInputStream(file, entry));
                }
            }
            if (manifest == null) {
                return;
            }
            Attributes mainAttributes = manifest.getMainAttributes();
            String mainClass = mainAttributes.getValue("Main-Class");
            if (mainClass == null) {
                return;
            }
            nativeImage.addImageBuilderArg(NativeImage.oHClass + mainClass);
            String jarFileName = file.getName().toString();
            String jarFileNameBase = jarFileName.substring(0, jarFileName.length() - 4);
            nativeImage.addImageBuilderArg(NativeImage.oHName + jarFileNameBase);
            Path filePath = file.toPath();
            nativeImage.addImageClasspath(filePath);
            String classPath = mainAttributes.getValue("Class-Path");
            /* Missing Class-Path Attribute is tolerable */
            if (classPath != null) {
                for (String cp : classPath.split(" +")) {
                    Path manifestClassPath = Paths.get(cp);
                    if (!manifestClassPath.isAbsolute()) {
                        /* Resolve relative manifestClassPath against directory containing jar */
                        manifestClassPath = filePath.getParent().resolve(manifestClassPath);
                    }
                    nativeImage.addImageClasspath(manifestClassPath);
                }
            }
        } catch (IOException e) {
            NativeImage.showError("Given file does not appear to be a jar-file: " + file, e);
        }
    }
}
