/*
 * Copyright (c) 2025, Oracle and/or its affiliates. All rights reserved.
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

import static jdk.graal.compiler.serviceprovider.GraalServices.getSavedProperty;

import java.util.Set;

import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.vm.ci.common.JVMCIError;

/**
 * The name of a known OS and architecture.
 *
 * @param osName the name of a known OS (one of {@link #KNOWN_OS_NAMES})
 * @param archName the name of a known architecture (one of {@link #KNOWN_ARCHITECTURES})
 */
public record Platform(String osName, String archName) {
    public Platform {
        GraalError.guarantee(KNOWN_OS_NAMES.contains(osName), "unknown OS name");
        GraalError.guarantee(KNOWN_ARCHITECTURES.contains(archName), "unknown architecture");
    }

    /**
     * Returns the platform of the current host based on system properties.
     */
    public static Platform ofCurrentHost() {
        return new Platform(getCurrentOSName(), getCurrentArchName());
    }

    public static final Set<String> KNOWN_ARCHITECTURES = CollectionsUtil.setOf("amd64", "aarch64", "riscv64");

    public static final Set<String> KNOWN_OS_NAMES = CollectionsUtil.setOf("windows", "linux", "darwin");

    /**
     * Returns the name of the host OS.
     */
    private static String getCurrentOSName() {
        String value = getSavedProperty("os.name");
        switch (value) {
            case "Linux":
                value = "linux";
                break;
            case "SunOS":
                value = "solaris";
                break;
            case "Mac OS X":
                value = "darwin";
                break;
            default:
                // Windows names contain the OS version.
                if (value.startsWith("Windows")) {
                    value = "windows";
                } else {
                    throw new JVMCIError("Unexpected OS name: " + value);
                }
        }
        return value;
    }

    /**
     * Returns the name of the host architecture.
     */
    private static String getCurrentArchName() {
        String arch = getSavedProperty("os.arch");
        if (arch.equals("x86_64")) {
            arch = "amd64";
        }
        return arch;
    }
}
