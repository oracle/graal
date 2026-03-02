/*
 * Copyright (c) 2026, 2026, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.configure.command.sbom;

import java.nio.file.Path;
import java.util.List;

import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.Platform;
import org.graalvm.nativeimage.Platforms;
import org.graalvm.nativeimage.c.CContext;
import org.graalvm.nativeimage.c.function.CFunction;
import org.graalvm.nativeimage.c.function.CFunction.Transition;
import org.graalvm.nativeimage.c.function.CLibrary;
import org.graalvm.nativeimage.c.type.CCharPointer;
import org.graalvm.nativeimage.c.type.CTypeConversion;
import org.graalvm.nativeimage.c.type.CTypeConversion.CCharPointerHolder;

/**
 * Provides Java-level access to the native {@code libextract_sbom} implementation.
 */
@CContext(SbomExtractLibraryDirectives.class)
@CLibrary(value = "extract_sbom", requireStatic = true)
public class SbomExtractLibrary {

    public static int extractSbom(Path executable) {
        CCharPointerHolder exe = CTypeConversion.toCString(executable.toAbsolutePath().toString());
        return extractSbomNative(exe.get());
    }

    @CFunction(value = "extract_sbom", transition = Transition.NO_TRANSITION)
    private static native int extractSbomNative(CCharPointer executable);

}

@Platforms(Platform.HOSTED_ONLY.class)
class SbomExtractLibraryDirectives implements CContext.Directives {
    /**
     * True if {@link SbomExtractLibrary} should be linked.
     */
    @Override
    public boolean isInConfiguration() {
        return Platform.includedIn(Platform.LINUX.class) && ImageSingletons.contains(SbomExtractFeature.class);
    }

    @Override
    public List<String> getLibraries() {
        return List.of("elf");
    }
}
