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
package com.oracle.svm.driver;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.Test;

import jdk.graal.compiler.util.json.JsonWriter;

public class BundlePathMapTest {

    @Test
    public void lowersWindowsPathsIntoPortableSyntax() {
        assertEquals("/win/d/work/app.jar", BundlePathMap.encodeSourcePathText("D:\\work\\app.jar", BundlePathMap.PathStyle.Windows));
        assertEquals("win-rel/target/app.jar", BundlePathMap.encodeSourcePathText("target\\app.jar", BundlePathMap.PathStyle.Windows));
        assertEquals("input/classes/cp/app.jar", BundlePathMap.bundlePath(Path.of("input", "classes", "cp", "app.jar")).text());
    }

    @Test
    public void parsesPortableMappingsIntoPortableInternalPaths() throws Exception {
        Path tempFile = Files.createTempFile("bundle-paths", ".json");
        try (JsonWriter writer = new JsonWriter(tempFile)) {
            BundlePathMap.printPathMapping(Map.entry(Path.of("D:\\work\\app.jar"), Path.of("input", "classes", "cp", "app.jar")), writer, BundlePathMap.PathStyle.Windows, true);
        }

        Map<Path, Path> pathMap = new HashMap<>();
        BundlePathMap.parseAndRegister(new java.io.StringReader("[" + Files.readString(tempFile) + "]"), pathMap);

        assertEquals(Path.of("/win/d/work/app.jar"), pathMap.keySet().iterator().next());
        assertEquals(Path.of("input/classes/cp/app.jar"), pathMap.values().iterator().next());
    }

    @Test
    public void rewritesWindowsClasspathEntriesToExtractedBundleInputs() {
        Map<Path, Path> canonicalizations = Map.of(
                        Path.of("win-rel/target/app.jar"), Path.of("/win/d/work/target/app.jar"),
                        Path.of("win-rel/lib/helper.jar"), Path.of("/win/d/work/lib/helper.jar"));
        Map<Path, Path> substitutions = Map.of(
                        Path.of("/win/d/work/target/app.jar"), Path.of("input/classes/cp/app.jar"),
                        Path.of("/win/d/work/lib/helper.jar"), Path.of("input/classes/cp/helper.jar"));

        Path bundleRoot = Path.of("/bundle");
        BundleSupportArgumentRewriter rewriter = new BundleSupportArgumentRewriter(null, BundlePathMap.PathStyle.Windows, canonicalizations, substitutions, bundleRoot);
        List<String> rewritten = rewriter.rewrite(List.of("-cp", "target\\app.jar;lib\\helper.jar"));

        String expectedClassPath = bundleRoot.resolve(Path.of("input/classes/cp/app.jar")) + File.pathSeparator + bundleRoot.resolve(Path.of("input/classes/cp/helper.jar"));
        assertEquals(List.of("-cp", expectedClassPath), rewritten);
    }

    @Test
    public void rewritesInlineWindowsClasspathEntriesToExtractedBundleInputs() {
        Map<Path, Path> canonicalizations = Map.of(
                        Path.of("win-rel/target/app.jar"), Path.of("/win/d/work/target/app.jar"),
                        Path.of("win-rel/lib/helper.jar"), Path.of("/win/d/work/lib/helper.jar"));
        Map<Path, Path> substitutions = Map.of(
                        Path.of("/win/d/work/target/app.jar"), Path.of("input/classes/cp/app.jar"),
                        Path.of("/win/d/work/lib/helper.jar"), Path.of("input/classes/cp/helper.jar"));

        Path bundleRoot = Path.of("/bundle");
        BundleSupportArgumentRewriter rewriter = new BundleSupportArgumentRewriter(null, BundlePathMap.PathStyle.Windows, canonicalizations, substitutions, bundleRoot);
        List<String> rewritten = rewriter.rewrite(List.of("--class-path=target\\app.jar;lib\\helper.jar"));

        String expectedClassPath = bundleRoot.resolve(Path.of("input/classes/cp/app.jar")) + File.pathSeparator + bundleRoot.resolve(Path.of("input/classes/cp/helper.jar"));
        assertEquals(List.of("--class-path=" + expectedClassPath), rewritten);
    }

    @Test
    public void matchesOnlyJavaLauncherInlineClasspathSpellings() {
        assertNotNull(DriverPathOptions.matchAny("--class-path=cp"));
        assertNull(DriverPathOptions.matchAny("-cp=cp"));
        assertNull(DriverPathOptions.matchAny("-classpath=cp"));
    }

    @Test
    public void matchesOnlyJavaLauncherInlineModulePathSpellings() {
        assertNotNull(DriverPathOptions.matchAny("--module-path=mods"));
        assertNull(DriverPathOptions.matchAny("-p=mods"));
    }

    @Test
    public void rewritesAbsoluteWindowsImageNameToPlainFileName() {
        BundleSupportArgumentRewriter rewriter = new BundleSupportArgumentRewriter(null, BundlePathMap.PathStyle.Windows, Map.of(), Map.of(), Path.of("/bundle"));

        List<String> rewritten = rewriter.rewrite(List.of("-o", "C:\\Users\\paul\\Labs\\ni-bundles\\spring-petclinic\\target\\spring-petclinic"));

        assertEquals(List.of("-o", "spring-petclinic"), rewritten);
    }

    @Test
    public void rewritesRelativeWindowsImageNameToCurrentPlatformPath() {
        BundleSupportArgumentRewriter rewriter = new BundleSupportArgumentRewriter(null, BundlePathMap.PathStyle.Windows, Map.of(), Map.of(), Path.of("/bundle"));

        List<String> rewritten = rewriter.rewrite(List.of("-o", "target\\spring-petclinic"));

        assertEquals(List.of("-o", Path.of("target", "spring-petclinic").toString()), rewritten);
    }

    @Test
    public void serializesDerivedBundleArgsWithBundleFilePaths() {
        List<String> queueSnapshot = List.of(
                        "-cp", "/tmp/bundleRoot-18438110581797259606/input/classes/cp/cp", "HelloJava",
                        "--pgo",
                        "--bundle-create=simple-bundle-pgo.nib");
        List<String> currentBuildArgs = List.of("-cp", "/tmp/bundleRoot-18438110581797259606/input/classes/cp/cp", "HelloJava");
        List<String> bundleFileBuildArgs = List.of("-cp", "cp", "HelloJava");

        List<String> serialized = BundleSupport.serializeUpdatedBundleArgs(queueSnapshot, currentBuildArgs, bundleFileBuildArgs);

        assertEquals(List.of("-cp", "cp", "HelloJava", "--pgo", "--bundle-create=simple-bundle-pgo.nib"), serialized);
    }

    @Test
    public void filtersIdentityCanonicalizationsFromBundleFileOutput() {
        Map<Path, Path> canonicalizations = Map.of(
                        Path.of("/win/c/work/app.jar"), Path.of("/win/c/work/app.jar"),
                        Path.of("win-rel/target/app.jar"), Path.of("/win/c/work/target/app.jar"));

        List<Map.Entry<Path, Path>> filtered = BundlePathMap.withoutIdentityMappings(canonicalizations).collect(Collectors.toList());

        assertEquals(List.of(Map.entry(Path.of("win-rel/target/app.jar"), Path.of("/win/c/work/target/app.jar"))), filtered);
    }
}
