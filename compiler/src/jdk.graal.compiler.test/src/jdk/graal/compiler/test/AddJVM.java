/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.test;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;

/**
 * Copies a JVM library from one JDK into another. For example, copy {@code lib/server/libjvm.so}
 * from a fastdebug JDK into {@code lib/fastdebug} of a product JDK which can then be used with
 * {@code java -fastdebug}.
 */
public class AddJVM {

    final PrintStream out = System.out;

    private void help() {
        String prog = AddJVM.class.getSimpleName();
        out.printf("Usage: %s src-java-home src-vm-name dst-java-home dst-vm-name%n", prog);
        out.printf("%n");
        out.printf("Example:%n");
        out.printf("  %s $HOME/.mx/jdks/labsjdk-ce-20-debug-jvmci-23.0-b08 server $HOME/.mx/jdks/labsjdk-ce-20-jvmci-23.0-b08 fastdebug%n", prog);
    }

    private static void error(String format, Object... args) {
        throw new Error(String.format(format, args));
    }

    private static Path exists(Path path) {
        if (!Files.exists(path)) {
            error("%s does not exist", path);
        }
        return path;
    }

    private static Path dir(Path path) {
        if (!Files.isDirectory(exists(path))) {
            error("%s is not a directory", path);
        }
        return path;
    }

    private void run(String[] args) throws Exception {
        if (args.length != 4) {
            help();
            System.exit(1);
        }
        Path srcJavaHome = dir(Path.of(args[0]));
        String srcVmName = args[1];
        Path dstJavaHome = dir(Path.of(args[2]));
        String dstVmName = args[3];

        boolean isWindows = System.getProperty("os.name").toLowerCase().contains("win");
        String vmLibParent = isWindows ? "bin" : "lib";
        Path srcVm = dir(srcJavaHome.resolve(Path.of(vmLibParent, srcVmName)));
        Path dstVm = dstJavaHome.resolve(Path.of(vmLibParent, dstVmName));
        Path dstJvmCfg = exists(dstJavaHome.resolve(Path.of("lib", "jvm.cfg")));

        copyJvm(srcVm, dstVm);
        updateJvmCfg(dstVmName, dstJvmCfg);
    }

    /**
     * Copies a JVM library directory.
     *
     * @param srcVm source JVM library directory
     * @param dstVm destination JVM library directory (must not exist)
     */
    private static void copyJvm(Path srcVm, Path dstVm) throws IOException {
        if (Files.exists(dstVm)) {
            error("Cannot overwrite existing VM in %s. Delete %s and retry.", dstVm, dstVm);
        }
        Files.createDirectories(dstVm);

        Files.list(srcVm).forEach(e -> {
            // Do not copy class shared archives as they are coupled to
            // lib/modules in the src JDK.
            String name = e.getFileName().toString();
            if (!name.endsWith(".jsa")) {
                Path dst = dstVm.resolve(name);
                try {
                    Files.copy(e, dst);
                } catch (IOException ex) {
                    error("Error copying %s to %s: %s", e, dst, ex);
                }
            }
        });
    }

    /**
     * Update {@code lib/jvm.cfg} to ensure it has an entry for {@code vmName}.
     *
     * @param vmName name of directory containing a JVM library (e.g. "server")
     * @param jvmCfg path to the {@code jvm.cfg} file to update
     */
    private static void updateJvmCfg(String vmName, Path jvmCfg) throws IOException {
        // LinkedHashMap preserves ordering which is meaningful in jvm.cfg
        LinkedHashMap<String, String> cfg = new LinkedHashMap<>();
        for (String line : Files.readString(jvmCfg).split("\n")) {
            if (line.startsWith("#")) {
                cfg.put(line, "");
            } else {
                String[] parts = line.split("\s", 2);
                if (parts.length != 2) {
                    error("malformed line in %s:%n%s", jvmCfg, line);
                }
                cfg.put(parts[0], parts[1]);
            }
        }
        cfg.put("-" + vmName, "KNOWN");

        String newCfg = cfg.entrySet().stream().//
                        map(e -> e.getKey() + " " + e.getValue()).//
                        collect(Collectors.joining("\n", "", "\n"));
        Files.writeString(jvmCfg, newCfg);
    }

    public static void main(String[] args) throws Exception {
        new AddJVM().run(args);
    }
}
