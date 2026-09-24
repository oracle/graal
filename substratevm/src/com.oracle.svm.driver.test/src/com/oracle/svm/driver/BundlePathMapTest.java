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
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import java.io.File;
import java.io.StringWriter;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.jar.Attributes;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Test;

import com.oracle.svm.driver.BundlePathMap.PathStyle;
import com.oracle.svm.driver.BundlePathMap.PortablePath;
import com.oracle.svm.driver.BundlePathMap.RootKind;
import com.oracle.svm.driver.launcher.configuration.BundleArgsParser;
import com.oracle.svm.driver.launcher.configuration.BundleArgsParser.ArgumentGroup;
import com.oracle.svm.driver.launcher.json.BundleJSONParserException;
import com.oracle.svm.shared.option.OptionOrigin;

import jdk.graal.compiler.util.json.JsonWriter;

public class BundlePathMapTest {
    private static final class NoOpAPIOptionHandler extends APIOptionHandler {
        NoOpAPIOptionHandler() {
            super();
        }
    }

    private static final APIOptionHandler noOpAPIOptionHandler = new NoOpAPIOptionHandler();

    @Test
    public void lowersWindowsPathsIntoPortableSyntax() {
        assertEquals(RootKind.DriveAbsolute, source("D:\\work\\app.jar", PathStyle.Windows).kind());
        assertEquals("d/work/app.jar", source("D:\\work\\app.jar", PathStyle.Windows).text());
        assertEquals(RootKind.Relative, source("target\\app.jar", PathStyle.Windows).kind());
        assertEquals("target/app.jar", source("target\\app.jar", PathStyle.Windows).text());
        assertEquals("input/classes/cp/app.jar", BundlePathMap.bundlePath(Path.of("input", "classes", "cp", "app.jar")).text());
    }

    @Test
    public void preservesWindowsPathsThatLookLikePortableSyntax() {
        PortablePath relative = source("win-rel\\target\\app.jar", PathStyle.Windows);
        assertEquals(RootKind.Relative, relative.kind());
        assertEquals("win-rel/target/app.jar", relative.text());

        PortablePath drivePrefixRelative = source("win-drive-rel\\d\\work\\app.jar", PathStyle.Windows);
        assertEquals(RootKind.Relative, drivePrefixRelative.kind());
        assertEquals("win-drive-rel/d/work/app.jar", drivePrefixRelative.text());

        PortablePath directoryRelative = source("\\win\\d\\work\\app.jar", PathStyle.Windows);
        assertEquals(RootKind.DirectoryRelative, directoryRelative.kind());
        assertEquals("win/d/work/app.jar", directoryRelative.text());
    }

    @Test
    public void comparesWindowsPathsCaseInsensitively() {
        PortablePath destination = bundle("input/classes/cp/app.jar");
        PortablePath sourcePath = source("C:\\Lib\\App.jar", PathStyle.Windows);
        Map<PortablePath, PortablePath> pathMap = new HashMap<>();
        pathMap.put(sourcePath, destination);

        assertEquals("c/Lib/App.jar", sourcePath.text());
        assertEquals(destination, pathMap.get(source("c:\\lib\\app.jar", PathStyle.Windows)));
        assertTrue(!source("/Lib/App.jar", PathStyle.Unix).equals(source("/lib/app.jar", PathStyle.Unix)));
    }

    @Test
    public void rejectsMalformedPortableWindowsPrefixes() {
        assertThrows(IllegalArgumentException.class, () -> PortablePath.parseLegacy(PathStyle.Windows, "/win/not-a-drive/app.jar"));
        assertThrows(IllegalArgumentException.class, () -> PortablePath.parseLegacy(PathStyle.Windows, "/win/unc/server"));
        assertThrows(IllegalArgumentException.class, () -> PortablePath.parseLegacy(PathStyle.Windows, "win-drive-rel/not-a-drive/app.jar"));
    }

    @Test
    public void rejectsIncompleteWindowsSourceUNCRoots() {
        // Two leading separators require both a server and a share, not a current-drive path.
        for (String path : List.of("\\\\", "\\\\server", "\\\\server\\", "//", "//server", "//server/")) {
            assertThrows(path, IllegalArgumentException.class, () -> PortablePath.parseSource(PathStyle.Windows, path));
        }
        // A single leading separator is still directory-relative; a complete UNC root is valid.
        assertEquals(RootKind.DirectoryRelative, source("\\server", PathStyle.Windows).kind());
        assertEquals(RootKind.UNC, source("\\\\server\\share", PathStyle.Windows).kind());
        assertEquals(RootKind.UNC, source("//server/share/", PathStyle.Windows).kind());
    }

    @Test
    public void parsesLegacyPortablePathRootKinds() {
        assertEquals(source("target\\app.jar", PathStyle.Windows), PortablePath.parseLegacy(PathStyle.Windows, "win-rel/target/app.jar"));
        assertEquals(source("D:work\\app.jar", PathStyle.Windows), PortablePath.parseLegacy(PathStyle.Windows, "win-drive-rel/d/work/app.jar"));
        assertEquals(source("D:\\work\\app.jar", PathStyle.Windows), PortablePath.parseLegacy(PathStyle.Windows, "/win/d/work/app.jar"));
        assertEquals(source("\\work\\app.jar", PathStyle.Windows), PortablePath.parseLegacy(PathStyle.Windows, "/win/root/work/app.jar"));
        assertEquals(source("\\\\server\\share\\app.jar", PathStyle.Windows), PortablePath.parseLegacy(PathStyle.Windows, "/win/unc/server/share/app.jar"));
        assertEquals(source("/work/app.jar", PathStyle.Unix), PortablePath.parseLegacy(PathStyle.Unix, "/work/app.jar"));
    }

    @Test
    public void rendersParsedLegacyPathsInSourceSyntax() {
        assertEquals("target\\app.jar", PortablePath.parseLegacy(PathStyle.Windows, "win-rel/target/app.jar").sourcePathText());
        assertEquals("D:work\\app.jar", PortablePath.parseLegacy(PathStyle.Windows, "win-drive-rel/d/work/app.jar").sourcePathText());
        assertEquals("D:\\work\\app.jar", PortablePath.parseLegacy(PathStyle.Windows, "/win/d/work/app.jar").sourcePathText());
        assertEquals("/work/app.jar", PortablePath.parseLegacy(PathStyle.Unix, "/work/app.jar").sourcePathText());
    }

    @Test
    public void parsesFileURIsWithSourcePlatformSemantics() {
        assertEquals(source("/work/lib/helper jar.jar", PathStyle.Unix), PortablePath.parseFileURI(PathStyle.Unix, URI.create("file:///work/lib/helper%20jar.jar")));
        assertEquals(source("C:\\work\\lib\\helper.jar", PathStyle.Windows), PortablePath.parseFileURI(PathStyle.Windows, URI.create("file:///C:/work/lib/helper.jar")));
        assertEquals(source("\\\\server\\share\\lib\\helper.jar", PathStyle.Windows), PortablePath.parseFileURI(PathStyle.Windows, URI.create("file://server/share/lib/helper.jar")));
    }

    @Test
    public void rejectsWindowsFileURIsWithoutUNCShares() {
        // Validate both authority-based UNC URIs and the authority-free UNC fallback form.
        for (String uri : List.of("file://server/", "file:////server", "file:////server/", "file:////server_name/")) {
            assertThrows(uri, IllegalArgumentException.class, () -> PortablePath.parseFileURI(PathStyle.Windows, URI.create(uri)));
        }
        assertEquals("\\\\server\\share", PortablePath.parseFileURI(PathStyle.Windows, URI.create("file://server/share/")).sourcePathText());
        assertEquals("\\\\server_name\\share", PortablePath.parseFileURI(PathStyle.Windows, URI.create("file:////server_name/share/")).sourcePathText());
    }

    @Test
    public void representsSourcePathsAsFileURIs() {
        assertEquals(URI.create("file:///work/my%20%23%25%5C.jar"), source("/work/my #%\\.jar", PathStyle.Unix).toFileURI());
        assertEquals(URI.create("file:///C:/work/my%20jar.jar"), source("C:\\work\\my jar.jar", PathStyle.Windows).toFileURI());
        assertEquals(URI.create("file://server/share/app.jar"), source("\\\\server\\share\\app.jar", PathStyle.Windows).toFileURI());
        for (PortablePath path : List.of(
                        source("/work/my #%\\.jar", PathStyle.Unix),
                        source("C:\\work\\my jar.jar", PathStyle.Windows),
                        source("\\\\server\\share\\app.jar", PathStyle.Windows),
                        source("\\\\server_name\\share\\app.jar", PathStyle.Windows),
                        source("\\\\fe80--1s3.ipv6-literal.net\\share\\app.jar", PathStyle.Windows))) {
            assertEquals(path, PortablePath.parseFileURI(path.style(), path.toFileURI()));
        }
        assertThrows(IllegalArgumentException.class, () -> source("app.jar", PathStyle.Unix).toFileURI());
    }

    @Test
    public void resolvesManifestURIsWithSourcePlatformSemantics() {
        URI windowsJar = source("C:\\original\\app.jar", PathStyle.Windows).toFileURI();
        // A network-path reference supplies its own authority, not just a local path.
        assertEquals(source("\\\\server\\share\\dep.jar", PathStyle.Windows),
                        PortablePath.parseFileURI(PathStyle.Windows, windowsJar.resolve("//server/share/dep.jar")));
        // A UNC context retains its server for a reference with an absolute URI path.
        URI uncJar = source("\\\\server\\share\\app.jar", PathStyle.Windows).toFileURI();
        assertEquals(source("\\\\server\\other\\dep.jar", PathStyle.Windows),
                        PortablePath.parseFileURI(PathStyle.Windows, uncJar.resolve("/other/dep.jar")));
        // Relative entries are resolved against the original JAR and percent-decoded only afterward.
        assertEquals(source("C:\\lib\\helper jar.jar", PathStyle.Windows),
                        PortablePath.parseFileURI(PathStyle.Windows, windowsJar.resolve("../lib/helper%20jar.jar")));
        URI unixJar = source("/original/app.jar", PathStyle.Unix).toFileURI();
        assertEquals(source("/original/helper\\jar.jar", PathStyle.Unix),
                        PortablePath.parseFileURI(PathStyle.Unix, unixJar.resolve("helper%5Cjar.jar")));
        // Unix file paths cannot silently discard a URI authority.
        assertThrows(IllegalArgumentException.class, () -> PortablePath.parseFileURI(PathStyle.Unix, unixJar.resolve("//server/share/dep.jar")));
        // Query and fragment components survive resolution and are rejected as file paths.
        for (String reference : List.of("#fragment", "?query", "dep.jar#fragment", "dep.jar?query")) {
            assertThrows(IllegalArgumentException.class, () -> PortablePath.parseFileURI(PathStyle.Unix, unixJar.resolve(reference)));
        }
    }

    @Test
    public void resolvesSourcePathsWithoutLeavingPortableDomain() {
        PortablePath windowsAbsoluteParent = source("D:\\work\\app.jar", PathStyle.Windows).getParent();
        assertNotNull(windowsAbsoluteParent);
        assertEquals(source("D:\\work\\lib\\helper.jar", PathStyle.Windows), windowsAbsoluteParent.resolve("lib\\helper.jar"));

        PortablePath windowsRelativeParent = source("win-rel\\target\\app.jar", PathStyle.Windows).getParent();
        assertNotNull(windowsRelativeParent);
        assertEquals(source("win-rel\\lib.jar", PathStyle.Windows), windowsRelativeParent.resolve("..\\lib.jar").normalize());

        PortablePath windowsDriveRelativeParent = source("D:work\\app.jar", PathStyle.Windows).getParent();
        assertNotNull(windowsDriveRelativeParent);
        assertEquals(source("D:lib.jar", PathStyle.Windows), windowsDriveRelativeParent.resolve("..\\lib.jar").normalize());

        PortablePath windowsUncParent = source("\\\\server\\share\\dir\\app.jar", PathStyle.Windows).getParent();
        assertNotNull(windowsUncParent);
        assertEquals(source("\\\\server\\share\\lib.jar", PathStyle.Windows), windowsUncParent.resolve("../lib.jar").normalize());

        PortablePath unixParent = source("/work/dir/app.jar", PathStyle.Unix).getParent();
        assertNotNull(unixParent);
        assertEquals(source("/work/lib.jar", PathStyle.Unix), unixParent.resolve("../lib.jar").normalize());
    }

    @Test
    public void getsWindowsPathParentsWithNativeSemantics() {
        // An empty relative path has no parent.
        assertNull(source("", PathStyle.Windows).getParent());

        // A single relative name has no root to return as its parent.
        assertNull(source("work", PathStyle.Windows).getParent());

        // The parent of a relative path drops its final name.
        assertEquals(source("work", PathStyle.Windows), source("work\\lib", PathStyle.Windows).getParent());

        // A directory-relative root has no parent.
        assertNull(source("\\", PathStyle.Windows).getParent());

        // The parent of the first directory-relative name is its root.
        assertEquals(source("\\", PathStyle.Windows), source("\\work", PathStyle.Windows).getParent());

        // The parent of a directory-relative path drops its final name.
        assertEquals(source("\\work", PathStyle.Windows), source("\\work\\lib", PathStyle.Windows).getParent());

        // A drive-relative root has no parent.
        assertNull(source("C:", PathStyle.Windows).getParent());

        // The parent of the first drive-relative name is its drive-relative root.
        assertEquals(source("C:", PathStyle.Windows), source("C:work", PathStyle.Windows).getParent());

        // The parent of a drive-relative path drops its final name.
        assertEquals(source("C:work", PathStyle.Windows), source("C:work\\lib", PathStyle.Windows).getParent());

        // A drive-absolute root has no parent.
        assertNull(source("C:\\", PathStyle.Windows).getParent());

        // The parent of the first drive-absolute name is its drive root.
        assertEquals(source("C:\\", PathStyle.Windows), source("C:\\work", PathStyle.Windows).getParent());

        // The parent of a drive-absolute path drops its final name.
        assertEquals(source("C:\\work", PathStyle.Windows), source("C:\\work\\lib", PathStyle.Windows).getParent());

        // A UNC root has no parent.
        assertNull(source("\\\\server\\share", PathStyle.Windows).getParent());

        // The parent of the first UNC name is its share root.
        assertEquals(source("\\\\server\\share", PathStyle.Windows), source("\\\\server\\share\\work", PathStyle.Windows).getParent());

        // The parent of a UNC path drops its final name.
        assertEquals(source("\\\\server\\share\\work", PathStyle.Windows), source("\\\\server\\share\\work\\lib", PathStyle.Windows).getParent());
    }

    @Test
    public void resolvesWindowsPathsWithNativeSemantics() {
        PortablePath driveAbsolute = source("C:\\work", PathStyle.Windows);

        // An empty path leaves the base unchanged.
        assertEquals(driveAbsolute, driveAbsolute.resolve(""));

        // A relative path is appended to the base.
        assertEquals(source("C:\\work\\lib\\helper.jar", PathStyle.Windows), driveAbsolute.resolve("lib\\helper.jar"));

        // A directory-relative path replaces the base names but keeps its drive root.
        assertEquals(source("C:\\lib\\helper.jar", PathStyle.Windows), driveAbsolute.resolve("\\lib\\helper.jar"));

        // A drive-relative path on the same drive is appended to an absolute base.
        assertEquals(source("C:\\work\\lib\\helper.jar", PathStyle.Windows), driveAbsolute.resolve("C:lib\\helper.jar"));

        // A drive-relative path on a different drive remains unchanged.
        assertEquals(source("D:lib\\helper.jar", PathStyle.Windows), driveAbsolute.resolve("D:lib\\helper.jar"));

        // An absolute path replaces the base.
        assertEquals(source("D:\\lib\\helper.jar", PathStyle.Windows), driveAbsolute.resolve("D:\\lib\\helper.jar"));

        PortablePath driveRelative = source("C:work", PathStyle.Windows);

        // A directory-relative path keeps the base drive and makes the result drive-absolute.
        assertEquals(source("C:\\lib\\helper.jar", PathStyle.Windows), driveRelative.resolve("\\lib\\helper.jar"));

        // A drive-relative path cannot be appended to a drive-relative base.
        assertEquals(source("C:lib\\helper.jar", PathStyle.Windows), driveRelative.resolve("C:lib\\helper.jar"));

        PortablePath unc = source("\\\\server\\share\\work", PathStyle.Windows);

        // A directory-relative path replaces the names but keeps the UNC root.
        assertEquals(source("\\\\server\\share\\lib\\helper.jar", PathStyle.Windows), unc.resolve("\\lib\\helper.jar"));

        PortablePath directoryRelative = source("\\work", PathStyle.Windows);

        // A directory-relative path has a root but is not absolute.
        assertFalse(directoryRelative.isAbsolute());

        // The parent of the first name is the directory-relative root.
        assertEquals(source("\\", PathStyle.Windows), directoryRelative.getParent());

        // Without a drive or UNC root, a directory-relative path replaces the base.
        assertEquals(source("\\lib\\helper.jar", PathStyle.Windows), directoryRelative.resolve("\\lib\\helper.jar"));
    }

    @Test
    public void normalizesWindowsPathsWithNativeSemantics() {
        // `..` cancels `work`.
        assertEquals(source("C:\\lib", PathStyle.Windows), source("C:\\work\\..\\lib", PathStyle.Windows).normalize());

        // Nothing precedes `..`; it cannot move above `C:\`.
        assertEquals(source("C:\\lib", PathStyle.Windows), source("C:\\..\\lib", PathStyle.Windows).normalize());

        // First `..` cancels `work`; the second cannot escape `C:\`.
        assertEquals(source("C:\\lib", PathStyle.Windows), source("C:\\work\\..\\..\\lib", PathStyle.Windows).normalize());

        // A directory-relative path cannot escape its root.
        assertEquals(source("\\lib", PathStyle.Windows), source("\\work\\..\\..\\lib", PathStyle.Windows).normalize());

        // A UNC path cannot move above the share root.
        assertEquals(source("\\\\server\\share\\lib", PathStyle.Windows), source("\\\\server\\share\\..\\lib", PathStyle.Windows).normalize());

        // The unknown base directory may have a parent.
        assertEquals(source("..\\lib", PathStyle.Windows), source("..\\lib", PathStyle.Windows).normalize());

        // One `..` cancels `work`; the unmatched one remains.
        assertEquals(source("..\\lib", PathStyle.Windows), source("work\\..\\..\\lib", PathStyle.Windows).normalize());

        // This is relative to drive C's current directory, so `..` must remain.
        assertEquals(source("C:..\\lib", PathStyle.Windows), source("C:..\\lib", PathStyle.Windows).normalize());

        // After cancelling `work`, the remaining `..` may move above C's current directory.
        assertEquals(source("C:..\\lib", PathStyle.Windows), source("C:work\\..\\..\\lib", PathStyle.Windows).normalize());
    }

    @Test
    public void resolvesOnlyBundleRelativePathsAgainstBundleRoot() {
        assertEquals(Path.of("/bundle/input/classes/cp/app.jar"), BundlePathMap.resolveBundlePath(Path.of("/bundle"), bundle("input/classes/cp/app.jar")));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsResolvingSourcePathAgainstBundleRoot() {
        BundlePathMap.resolveBundlePath(Path.of("/bundle"), source("D:\\work\\app.jar", PathStyle.Windows));
    }

    @Test
    public void parsesPortableMappingsWithoutDiscardingTheirType() throws Exception {
        Path tempFile = Files.createTempFile("bundle-paths", ".json");
        try (JsonWriter writer = new JsonWriter(tempFile)) {
            BundlePathMap.printPathMapping(Map.entry(source("D:\\work\\app.jar", PathStyle.Windows), bundle("input/classes/cp/app.jar")), writer);
        }

        Map<PortablePath, PortablePath> pathMap = new HashMap<>();
        BundlePathMap.parseAndRegister(new java.io.StringReader("[" + Files.readString(tempFile) + "]"), pathMap);

        assertEquals(source("D:\\work\\app.jar", PathStyle.Windows), pathMap.keySet().iterator().next());
        assertEquals(bundle("input/classes/cp/app.jar"), pathMap.values().iterator().next());
    }

    @Test
    public void roundTripsUnavailableSubstitutions() throws Exception {
        PortablePath original = source("\\\\server\\share\\missing.jar", PathStyle.Windows);
        StringWriter text = new StringWriter();
        try (JsonWriter writer = new JsonWriter(text)) {
            writer.append('[');
            BundlePathMap.printPathMapping(Map.entry(original, PortablePath.UNAVAILABLE), writer);
            writer.append(']');
        }
        assertTrue(text.toString().contains("""
                        "dst":{"style":"BundleRelative","kind":"Unavailable"}"""));
        Map<PortablePath, PortablePath> paths = new HashMap<>();
        BundlePathMap.parseAndRegister(new java.io.StringReader(text.toString()), paths);
        assertEquals(Map.of(original, PortablePath.UNAVAILABLE), paths);
    }

    @Test
    public void rejectsMalformedUnavailableSubstitutions() {
        String mapping = """
                        [{"src":{"style":"Unix","kind":"Absolute","text":"missing.jar"},
                          "dst":%s}]
                        """;
        for (String invalid : List.of("""
                        {"style":"Unix","kind":"Unavailable"}
                        """, """
                        {"style":"Windows","kind":"Unavailable"}
                        """, """
                        {"style":"BundleRelative","kind":"Unavailable","text":""}
                        """, """
                        {"style":"BundleRelative","kind":"Unavailable","text":"path"}
                        """, """
                        {"style":"BundleRelative","kind":"Unavailable","text":null}
                        """)) {
            assertThrows(BundleJSONParserException.class, () -> BundlePathMap.parseAndRegister(
                            new java.io.StringReader(mapping.formatted(invalid)), new HashMap<>()));
        }
        // A sentinel records a substitution result; it can never be an original source path.
        assertThrows(BundleJSONParserException.class, () -> BundlePathMap.parseAndRegister(new java.io.StringReader("""
                        [{"src":{"style":"BundleRelative","kind":"Unavailable"},
                          "dst":{"style":"BundleRelative","kind":"Relative","text":"input/file"}}]
                        """), new HashMap<>()));
    }

    @Test
    public void requiresTextForOrdinaryPortablePaths() {
        String mapping = """
                        [{"src":%s,"dst":%s}]
                        """;
        String valid = """
                        {"style":"BundleRelative","kind":"Relative","text":"input/file"}
                        """;
        for (String invalid : List.of("""
                        {"style":"Unix","kind":"Relative"}
                        """, """
                        {"style":"Unix","kind":"Relative","text":null}
                        """, """
                        {"style":"Unix"}
                        """, """
                        {"style":"Unix","text":null}
                        """)) {
            assertThrows(BundleJSONParserException.class, () -> BundlePathMap.parseAndRegister(
                            new java.io.StringReader(mapping.formatted(invalid, valid)), new HashMap<>()));
            assertThrows(BundleJSONParserException.class, () -> BundlePathMap.parseAndRegister(
                            new java.io.StringReader(mapping.formatted(valid, invalid)), new HashMap<>()));
        }
    }

    @Test
    public void rejectsUnavailableCanonicalizations() throws Exception {
        Path file = Files.createTempDirectory("bundle-canonicalizations-test").resolve("invalid.nib");
        writeAndReloadBundle(newBundleSupport(), file);
        try (FileSystem fs = FileSystems.newFileSystem(file)) {
            // Replace /input/stage/path_canonicalizations.json inside invalid.nib via ZIP filesystem access.
            Files.writeString(fs.getPath("/input/stage/path_canonicalizations.json"), """
                            [{"src":{"style":"Unix","kind":"Absolute","text":"missing.jar"},
                              "dst":{"style":"BundleRelative","kind":"Unavailable"}}]
                            """);
        }
        NativeImage nativeImage = new NativeImage(new NativeImage.BuildConfiguration(Path.of("."), Path.of("."), List.of()));
        assertThrows(BundleJSONParserException.class, () -> BundleSupport.create(nativeImage, "--bundle-apply=" + file, new NativeImage.ArgumentQueue(OptionOrigin.originUser)));
    }

    @Test
    public void rejectsPathOperationsOnUnavailableSentinel() {
        PortablePath unavailable = PortablePath.UNAVAILABLE;
        assertThrows(IllegalStateException.class, unavailable::sourcePathText);
        assertThrows(IllegalStateException.class, unavailable::platformRelativePathText);
        assertThrows(IllegalStateException.class, unavailable::isAbsolute);
        assertThrows(IllegalStateException.class, unavailable::getFileName);
        assertThrows(IllegalStateException.class, unavailable::getParent);
        assertThrows(IllegalStateException.class, unavailable::normalize);
        assertThrows(IllegalStateException.class, unavailable::toFileURI);
        assertThrows(IllegalStateException.class, () -> unavailable.resolve("child"));
        assertThrows(IllegalArgumentException.class, () -> BundlePathMap.resolveBundlePath(Path.of("bundle"), unavailable));
    }

    @Test
    public void reprintsPortableMappingsWithoutEncodingAgain() throws Exception {
        Path tempFile = Files.createTempFile("bundle-paths", ".json");
        try (JsonWriter writer = new JsonWriter(tempFile)) {
            BundlePathMap.printPathMapping(Map.entry(PortablePath.parseLegacy(PathStyle.Windows, "win-rel/cp"),
                            PortablePath.parseLegacy(PathStyle.Windows, "/win/c/work/cp")), writer);
        }

        assertEquals("""
                        {"src":{"style":"Windows","kind":"Relative","text":"cp"},\
                        "dst":{"style":"Windows","kind":"DriveAbsolute","text":"c/work/cp"}}""",
                        Files.readString(tempFile));
    }

    @Test
    public void rewritesWindowsClasspathEntriesToExtractedBundleInputs() {
        Map<PortablePath, PortablePath> canonicalizations = Map.of(
                        source("target\\app.jar", PathStyle.Windows), source("D:\\work\\target\\app.jar", PathStyle.Windows),
                        source("lib\\helper.jar", PathStyle.Windows), source("D:\\work\\lib\\helper.jar", PathStyle.Windows));
        Map<PortablePath, PortablePath> substitutions = Map.of(
                        source("D:\\work\\target\\app.jar", PathStyle.Windows), bundle("input/classes/cp/app.jar"),
                        source("D:\\work\\lib\\helper.jar", PathStyle.Windows), bundle("input/classes/cp/helper.jar"));

        Path bundleRoot = Path.of("/bundle");
        BundleSupportArgumentRewriter rewriter = newRewriter(PathStyle.Windows, canonicalizations, substitutions, bundleRoot);
        List<String> rewritten = rewriter.rewrite(List.of("-cp", "target\\app.jar;lib\\helper.jar"));

        String expectedClassPath = bundleRoot.resolve(Path.of("input/classes/cp/app.jar")) + File.pathSeparator + bundleRoot.resolve(Path.of("input/classes/cp/helper.jar"));
        assertEquals(List.of("-cp", expectedClassPath), rewritten);
    }

    @Test
    public void rewritesInlineWindowsClasspathEntriesToExtractedBundleInputs() {
        Map<PortablePath, PortablePath> canonicalizations = Map.of(
                        source("target\\app.jar", PathStyle.Windows), source("D:\\work\\target\\app.jar", PathStyle.Windows),
                        source("lib\\helper.jar", PathStyle.Windows), source("D:\\work\\lib\\helper.jar", PathStyle.Windows));
        Map<PortablePath, PortablePath> substitutions = Map.of(
                        source("D:\\work\\target\\app.jar", PathStyle.Windows), bundle("input/classes/cp/app.jar"),
                        source("D:\\work\\lib\\helper.jar", PathStyle.Windows), bundle("input/classes/cp/helper.jar"));

        Path bundleRoot = Path.of("/bundle");
        BundleSupportArgumentRewriter rewriter = newRewriter(PathStyle.Windows, canonicalizations, substitutions, bundleRoot);
        List<String> rewritten = rewriter.rewrite(List.of("--class-path=target\\app.jar;lib\\helper.jar"));

        String expectedClassPath = bundleRoot.resolve(Path.of("input/classes/cp/app.jar")) + File.pathSeparator + bundleRoot.resolve(Path.of("input/classes/cp/helper.jar"));
        assertEquals(List.of("--class-path=" + expectedClassPath), rewritten);
    }

    @Test
    public void rewritesUnsubstitutedWindowsClasspathWithoutLegacyEncoding() {
        BundleSupportArgumentRewriter rewriter = newRewriter(PathStyle.Windows, Map.of(), Map.of(), Path.of("/bundle"));

        List<String> rewritten = rewriter.rewrite(List.of("--class-path=win-rel\\target\\app.jar"));

        assertEquals(List.of("--class-path=" + Path.of("win-rel", "target", "app.jar")), rewritten);
    }

    @Test
    public void preservesUnsubstitutedAbsoluteSourcePath() {
        PathStyle style = PathStyle.currentSourceStyle();
        Path originalPath = Path.of("original", "missing.jar").toAbsolutePath();
        BundleSupportArgumentRewriter rewriter = newRewriter(style, Map.of(), Map.of(), Path.of("/bundle"));

        List<String> rewritten = rewriter.rewrite(List.of("--class-path=" + originalPath));

        assertEquals(List.of("--class-path=" + originalPath), rewritten);
    }

    @Test
    public void ignoresManifestQueriesAndFragmentsWithoutCapturingPaths() throws Exception {
        for (String entry : List.of("", "#fragment", "?query", "ignored.jar#fragment", "ignored.jar?query")) {
            assertManifestEntryIgnored(entry);
        }
    }

    @Test
    public void ignoresFilesystemInvalidManifestPaths() throws Exception {
        assertManifestEntryIgnored("bad%00.jar");
        if (PathStyle.currentSourceStyle() == PathStyle.Windows) {
            assertManifestEntryIgnored("bad%3C.jar");
        }
    }

    private static void assertManifestEntryIgnored(String entry) throws Exception {
        Path fixtureRoot = newBundleSupport().rootDir;
        for (String name : List.of("app.jar", "helper.jar", "ignored.jar")) {
            try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(fixtureRoot.resolve(name)))) {
                jar.finish();
            }
        }
        BundleSupport bundleSupport = newBundleSupport();
        Path appJar = bundleSupport.substituteClassPath(fixtureRoot.resolve("app.jar"));
        Attributes attributes = new Attributes();
        attributes.put(Attributes.Name.CLASS_PATH, entry + " helper.jar");
        LinkedHashSet<Path> classpath = new LinkedHashSet<>();
        bundleSupport.nativeImage.handleClassPathAttribute(classpath, appJar, attributes);

        // Ignore the invalid entry, but still capture and process the following valid dependency.
        Path helperJar = bundleSupport.resolveSubstitutedPath(source(fixtureRoot.resolve("helper.jar").toString(), PathStyle.currentSourceStyle()));
        assertNotNull(helperJar);
        assertEquals(List.of(helperJar), List.copyOf(classpath));
        assertNull(bundleSupport.resolveSubstitutedPath(source(fixtureRoot.resolve("ignored.jar").toString(), PathStyle.currentSourceStyle())));
        assertNull(bundleSupport.resolveSubstitutedPath(source(fixtureRoot.toString(), PathStyle.currentSourceStyle())));
    }

    @Test
    public void preservesDistinctUnavailableSourceIdentities() throws Exception {
        List<PortablePath> originals = List.of(
                        source("\\\\server\\share\\first.jar", PathStyle.Windows),
                        source("\\\\server\\share\\second.jar", PathStyle.Windows),
                        source("D:\\missing.jar", PathStyle.Windows),
                        source("/missing.jar", PathStyle.Unix));
        Path fixtureRoot = newBundleSupport().rootDir;
        BundleSupport bundleSupport = newBundleSupport();
        for (PortablePath original : originals) {
            bundleSupport.recordUnavailablePath(original);
        }
        // UNC sentinels are exercised on every host without accessing a network share.
        for (int generation = 0; generation < 2; generation++) {
            bundleSupport = writeAndReloadBundle(bundleSupport, fixtureRoot.resolve("unavailable-" + generation + ".nib"));
            LinkedHashSet<Path> placeholders = new LinkedHashSet<>();
            for (PortablePath original : originals) {
                Path unavailable = bundleSupport.resolveSubstitutedPath(original);
                assertNotNull(unavailable);
                assertTrue(Files.notExists(unavailable));
                assertTrue(Files.isRegularFile(unavailable.getParent()));
                assertTrue(placeholders.add(unavailable));
                assertEquals(original, bundleSupport.originalPortablePath(unavailable));
                assertEquals(unavailable, bundleSupport.recordUnavailablePath(original));
            }
        }
    }

    @Test
    public void recordsUnavailableInputsBeforeEnablingDerivation() throws Exception {
        Path fixtureRoot = newBundleSupport().rootDir;
        Path missing = fixtureRoot.resolve("missing.jar");
        BundleSupport bundleSupport = writeAndReloadBundle(newBundleSupport(), fixtureRoot.resolve("original.nib"));
        // Additional arguments can be processed before a later --bundle-create option.
        bundleSupport.nativeImage.addCustomImageClasspath(missing.toString());
        writeJar(missing, "");
        bundleSupport = writeAndReloadBundle(bundleSupport, fixtureRoot.resolve("derived.nib"));
        Path unavailable = bundleSupport.resolveSubstitutedPath(source(missing.toString(), PathStyle.currentSourceStyle()));
        assertNotNull(unavailable);
        assertTrue(Files.notExists(unavailable));
        assertEquals(unavailable, bundleSupport.substituteClassPath(missing));
    }

    @Test
    public void preservesUnavailableExplicitInputsAcrossDerivedBundles() throws Exception {
        Path fixtureRoot = newBundleSupport().rootDir;
        Path missingClassPath = fixtureRoot.resolve("missing-cp.jar");
        Path missingModulePath = fixtureRoot.resolve("missing-mp.jar");
        BundleSupport bundleSupport = newBundleSupport("--class-path=" + missingClassPath, "--module-path=" + missingModulePath);
        bundleSupport.nativeImage.addCustomImageClasspath(missingClassPath.toString());
        bundleSupport.nativeImage.addImageModulePath(missingModulePath, false, false);
        bundleSupport = writeAndReloadBundle(bundleSupport, fixtureRoot.resolve("original.nib"));

        // Files appearing after capture must not become inputs on this or any later replay.
        writeJar(missingClassPath, "");
        writeJar(missingModulePath, "");
        for (int generation = 0; generation < 2; generation++) {
            for (Path original : List.of(missingClassPath, missingModulePath)) {
                Path unavailable = bundleSupport.resolveSubstitutedPath(source(original.toString(), PathStyle.currentSourceStyle()));
                assertNotNull(unavailable);
                assertTrue(Files.notExists(unavailable));
                assertTrue(Files.isRegularFile(unavailable.getParent()));
                assertEquals(unavailable, bundleSupport.substituteClassPath(original));
                assertEquals(unavailable, bundleSupport.substituteModulePath(original));
            }
            for (String arg : bundleSupport.getNativeImageArgs()) {
                Path rewritten = Path.of(arg.substring(arg.indexOf('=') + 1));
                assertTrue(rewritten.startsWith(bundleSupport.rootDir));
                assertTrue(Files.notExists(rewritten));
                if (arg.startsWith("--class-path=")) {
                    bundleSupport.nativeImage.addCustomImageClasspath(rewritten.toString());
                } else {
                    bundleSupport.nativeImage.addImageModulePath(rewritten, false, false);
                }
            }
            bundleSupport = writeAndReloadBundle(bundleSupport, fixtureRoot.resolve("derived-" + generation + ".nib"));
        }
    }

    @Test
    public void preservesUnavailableManifestInputsAcrossDerivedBundles() throws Exception {
        for (boolean absolute : List.of(false, true)) {
            Path fixtureRoot = newBundleSupport().rootDir;
            Path app = fixtureRoot.resolve("app.jar");
            Path missing = fixtureRoot.resolve("missing.jar");
            Path helper = fixtureRoot.resolve("helper.jar");
            Path unexpected = fixtureRoot.resolve("unexpected.jar");
            String manifestClassPath = (absolute ? missing.toUri().toString() : "missing.jar") + " helper.jar";
            writeJar(app, manifestClassPath);
            writeJar(helper, "");
            writeJar(unexpected, "");
            BundleSupport bundleSupport = newBundleSupport("--class-path=" + app);
            bundleSupport.nativeImage.addCustomImageClasspath(app.toString());
            bundleSupport = writeAndReloadBundle(bundleSupport, fixtureRoot.resolve("original.nib"));

            // The driver must not add the newly available JAR or its unexpected dependency.
            writeJar(missing, "unexpected.jar");
            for (int generation = 0; generation < 2; generation++) {
                Path capturedApp = bundleSupport.resolveSubstitutedPath(source(app.toString(), PathStyle.currentSourceStyle()));
                Path capturedHelper = bundleSupport.resolveSubstitutedPath(source(helper.toString(), PathStyle.currentSourceStyle()));
                Attributes attributes = new Attributes();
                attributes.put(Attributes.Name.CLASS_PATH, manifestClassPath);
                LinkedHashSet<Path> classpath = new LinkedHashSet<>();
                bundleSupport.nativeImage.handleClassPathAttribute(classpath, capturedApp, attributes);
                assertEquals(List.of(capturedHelper), List.copyOf(classpath));
                Path unavailable = bundleSupport.resolveSubstitutedPath(source(missing.toString(), PathStyle.currentSourceStyle()));
                assertNotNull(unavailable);
                assertTrue(Files.notExists(unavailable));
                assertNull(bundleSupport.resolveSubstitutedPath(source(unexpected.toString(), PathStyle.currentSourceStyle())));
                bundleSupport = writeAndReloadBundle(bundleSupport, fixtureRoot.resolve("derived-" + generation + ".nib"));
            }
        }
    }

    @Test
    public void loadsLegacyIdentitySubstitutionsAsUncapturedPaths() throws Exception {
        Path fixtureRoot = newBundleSupport().rootDir;
        Path missingAbsolute = fixtureRoot.resolve("missing.jar");
        Path missingRelative = Path.of("legacy-missing-relative.jar");
        Path capturedSource = fixtureRoot.resolve("captured.jar");
        String capturedDestination = "input/classes/cp/captured.jar";
        Path legacyBundle = fixtureRoot.resolve("legacy.nib");
        Map<String, String> entries = Map.of(
                        "META-INF/nibundle.properties", """
                                        BundleFileVersionMajor=0
                                        BundleFileVersionMinor=9
                                        NativeImagePlatform=%s
                                        """.formatted(NativeImage.platform),
                        "input/stage/path_canonicalizations.json", json(List.of(
                                        Map.of("src", "missing.jar", "dst", missingAbsolute.toString()),
                                        Map.of("src", capturedSource.toString(), "dst", capturedSource.toString()))),
                        "input/stage/path_substitutions.json", json(List.of(
                                        Map.of("src", missingAbsolute.toString(), "dst", missingAbsolute.toString()),
                                        Map.of("src", missingRelative.toString(), "dst", missingRelative.toString()),
                                        Map.of("src", capturedSource.toString(), "dst", capturedDestination))),
                        "input/stage/build.json", json(List.of("--class-path=missing.jar", "--module-path=" + missingRelative)),
                        capturedDestination, "captured input");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(legacyBundle))) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        NativeImage nativeImage = new NativeImage(new NativeImage.BuildConfiguration(Path.of("."), Path.of("."), List.of()));
        nativeImage.bundleSupport = BundleSupport.create(nativeImage, "--bundle-apply=" + legacyBundle, new NativeImage.ArgumentQueue(OptionOrigin.originUser));
        BundleSupport bundleSupport = nativeImage.bundleSupport;

        // Legacy identity substitutions become guaranteed-missing placeholders, not native paths.
        Path unavailableAbsolute = bundleSupport.resolveSubstitutedPath(source(missingAbsolute.toString(), PathStyle.currentSourceStyle()));
        Path unavailableRelative = bundleSupport.resolveSubstitutedPath(source(missingRelative.toString(), PathStyle.currentSourceStyle()));
        assertNotNull(unavailableAbsolute);
        assertNotNull(unavailableRelative);
        assertTrue(Files.notExists(unavailableAbsolute));
        assertTrue(Files.notExists(unavailableRelative));
        assertFalse(unavailableAbsolute.equals(unavailableRelative));
        assertEquals(List.of("--class-path=" + unavailableAbsolute, "--module-path=" + unavailableRelative), bundleSupport.getNativeImageArgs());
        assertEquals(unavailableAbsolute, bundleSupport.restoreBundlePath(Path.of("missing.jar")));
        // Canonicalization identities must not hide genuine captured-file substitutions.
        assertEquals(bundleSupport.rootDir.resolve(capturedDestination), bundleSupport.substituteClassPath(capturedSource));
        assertEquals("captured input", Files.readString(bundleSupport.rootDir.resolve(capturedDestination)));

        // Deriving a bundle must retain the old do-not-capture marker even if the file appears.
        BundleSupport.create(nativeImage, "--bundle-create", new NativeImage.ArgumentQueue(OptionOrigin.originUser));
        writeJar(missingAbsolute, "");
        for (int generation = 0; generation < 2; generation++) {
            bundleSupport = writeAndReloadBundle(bundleSupport, fixtureRoot.resolve("derived-" + generation + ".nib"));
            Path unavailable = bundleSupport.substituteClassPath(missingAbsolute);
            assertTrue(Files.notExists(unavailable));
            assertTrue(Files.isRegularFile(unavailable.getParent()));
            assertEquals(unavailable, bundleSupport.substituteModulePath(missingAbsolute));
            assertEquals("captured input", Files.readString(bundleSupport.substituteClassPath(capturedSource)));
            assertTrue(Files.notExists(bundleSupport.substituteModulePath(missingRelative)));
        }
    }

    @Test
    public void preservesCanonicalizationWithoutSubstitution() {
        PathStyle style = PathStyle.currentSourceStyle();
        Path originalPath = Path.of("original", "missing.jar").toAbsolutePath();
        Map<PortablePath, PortablePath> canonicalizations = Map.of(
                        source("missing.jar", style), source(originalPath.toString(), style));
        BundleSupportArgumentRewriter rewriter = newRewriter(style, canonicalizations, Map.of(), Path.of("/bundle"));

        // A missing input must retain its recorded location instead of using the replay directory.
        assertEquals(List.of("--class-path=" + originalPath, "--module-path=" + originalPath),
                        rewriter.rewrite(List.of("--class-path=missing.jar", "--module-path=missing.jar")));
    }

    @Test
    public void preservesUnsubstitutedWindowsRootsOnWindows() {
        assumeTrue(PathStyle.currentSourceStyle() == PathStyle.Windows);
        BundleSupportArgumentRewriter rewriter = newRewriter(PathStyle.Windows, Map.of(), Map.of(), Path.of("/bundle"));

        // A directory-relative path retains the current drive root, not the working directory.
        assertEquals(List.of("--class-path=\\libs\\dep.jar"), rewriter.rewrite(List.of("--class-path=\\libs\\dep.jar")));
        // A drive-relative path retains the specified drive's working directory.
        assertEquals(List.of("--module-path=D:libs\\dep.jar"), rewriter.rewrite(List.of("--module-path=D:libs\\dep.jar")));
    }

    @Test
    public void keepsForeignRootedPathsMissingWithoutSplittingPathLists() {
        BundleSupport bundleSupport = newBundleSupport();
        PathStyle foreignStyle = PathStyle.currentSourceStyle() == PathStyle.Unix ? PathStyle.Windows : PathStyle.Unix;
        List<String> originalPaths = foreignStyle == PathStyle.Windows
                        ? List.of("D:\\missing.jar", "\\libs\\dep.jar", "D:missing.jar", "\\\\server\\share\\missing.jar")
                        : List.of("/missing.jar");
        BundleSupportArgumentRewriter rewriter = new BundleSupportArgumentRewriter(noOpAPIOptionHandler, foreignStyle, Map.of(), Map.of(),
                        bundleSupport::lowerToNativePath);
        String pathList = String.join(foreignStyle == PathStyle.Windows ? ";" : ":", originalPaths);

        for (String option : List.of("--class-path=", "--module-path=")) {
            String rewritten = rewriter.rewrite(List.of(option + pathList)).getFirst().substring(option.length());
            String[] entries = rewritten.split(Pattern.quote(File.pathSeparator), -1);
            // Check the consumer's splitting, not just the intermediate argument string.
            assertEquals(originalPaths.size(), entries.length);
            for (int i = 0; i < entries.length; i++) {
                Path missingPath = Path.of(entries[i]);
                PortablePath original = source(originalPaths.get(i), foreignStyle);
                assertTrue(missingPath.startsWith(bundleSupport.rootDir));
                assertTrue(Files.notExists(missingPath));
                // Its parent is a regular file, so this placeholder cannot become a directory.
                assertTrue(Files.isRegularFile(missingPath.getParent()));
                assertEquals(original, bundleSupport.originalPortablePath(missingPath));
                assertEquals(missingPath, bundleSupport.resolveUnsubstitutedPath(original));
            }
        }
    }

    @Test
    public void preservesForeignCanonicalIdentityForMissingInputs() {
        BundleSupport bundleSupport = newBundleSupport();
        PathStyle foreignStyle = PathStyle.currentSourceStyle() == PathStyle.Unix ? PathStyle.Windows : PathStyle.Unix;
        PortablePath canonical = source(foreignStyle == PathStyle.Windows ? "D:\\original\\missing.jar" : "/original/missing.jar", foreignStyle);
        BundleSupportArgumentRewriter rewriter = new BundleSupportArgumentRewriter(noOpAPIOptionHandler, foreignStyle,
                        Map.of(source("missing.jar", foreignStyle), canonical), Map.of(), bundleSupport::lowerToNativePath);

        String argument = rewriter.rewrite(List.of("--class-path=missing.jar")).getFirst();
        Path missingPath = Path.of(argument.substring("--class-path=".length()));
        assertTrue(Files.notExists(missingPath));
        assertSame(canonical, bundleSupport.originalPortablePath(missingPath));
    }

    @Test
    public void restoresCanonicalizationWithoutCapturedFile() throws Exception {
        BundleSupport bundleSupport = newBundleSupport();
        Path replayDirectory = bundleSupport.nativeImage.config.getWorkingDirectory().toAbsolutePath().normalize();
        Path ambientFile = Files.createTempFile(replayDirectory, "bundle-ambient-", ".jar");
        try {
            Path relativePath = ambientFile.getFileName();
            Path originalPath = replayDirectory.resolve("original").resolve(relativePath);
            bundleSupport.recordCanonicalization(relativePath, originalPath);

            // Replay must ignore the ambient file even though the recorded source was not captured.
            assertTrue(Files.exists(ambientFile));
            assertFalse(Files.exists(originalPath));
            assertEquals(originalPath, bundleSupport.nativeImage.canonicalize(relativePath));
            assertEquals(source(originalPath.toString(), PathStyle.currentSourceStyle()), bundleSupport.originalPortablePath(originalPath));
        } finally {
            Files.delete(ambientFile);
        }
    }

    @Test
    public void matchesOnlyJavaLauncherInlineClasspathSpellings() {
        assertNotNull(matchAny("--class-path=cp"));
        assertNull(matchAny("-cp=cp"));
        assertNull(matchAny("-classpath=cp"));
    }

    @Test
    public void matchesOnlyJavaLauncherInlineModulePathSpellings() {
        assertNotNull(matchAny("--module-path=mods"));
        assertNull(matchAny("-p=mods"));
    }

    @Test
    public void matchesInlineConfigurationsPathSpelling() {
        assertNotNull(matchAny("--configurations-path=config"));
    }

    @Test
    public void matchesSplitConfigurationsPathSpelling() {
        ArrayDeque<String> args = new ArrayDeque<>(List.of("--configurations-path", "config"));

        DriverPathOptions.Match match = DriverPathOptions.matchAny(args);

        assertNotNull(match);
        assertTrue(args.isEmpty());
    }

    @Test
    public void consumesInlineExpertOptionsDetailSpelling() {
        NativeImage nativeImage = new NativeImage(new NativeImage.BuildConfiguration(Path.of("."), Path.of("."), List.of()));
        NativeImage.ArgumentQueue args = new NativeImage.ArgumentQueue(OptionOrigin.originUser);
        args.add("--expert-options-detail=AbortOnTypeReachable");

        assertTrue(nativeImage.cmdLineOptionHandler.consume(args));
        assertTrue(args.isEmpty());
    }

    @Test
    public void consumesSplitExpertOptionsDetailSpelling() {
        NativeImage nativeImage = new NativeImage(new NativeImage.BuildConfiguration(Path.of("."), Path.of("."), List.of()));
        NativeImage.ArgumentQueue args = new NativeImage.ArgumentQueue(OptionOrigin.originUser);
        args.add("--expert-options-detail");
        args.add("AbortOnTypeReachable");

        assertTrue(nativeImage.cmdLineOptionHandler.consume(args));
        assertTrue(args.isEmpty());
    }

    @Test
    public void consumesSplitPathOptionArgumentsWhenMatching() {
        ArrayDeque<String> args = new ArrayDeque<>(List.of("-cp", "cp", "Hello"));

        DriverPathOptions.Match match = DriverPathOptions.matchAny(args);

        assertNotNull(match);
        assertEquals(List.of("Hello"), List.copyOf(args));
    }

    @Test
    public void rewritesWindowsSourceImagePathsToFileNames() {
        BundleSupportArgumentRewriter rewriter = newRewriter(PathStyle.Windows, Map.of(), Map.of(), Path.of("/bundle"));

        // Discard Windows source roots and directories regardless of the replay platform.
        for (String imageName : List.of("C:\\dir\\image", "\\dir\\image", "C:dir\\image", "\\\\server\\share\\dir\\image", "dir\\image", "dir/image", "image")) {
            assertEquals(imageName, List.of("-o", "image"), rewriter.rewrite(List.of("-o", imageName)));
        }
    }

    @Test
    public void preservesNativeImageFileNameSyntax() {
        PathStyle style = PathStyle.currentSourceStyle();
        BundleSupportArgumentRewriter rewriter = newRewriter(style, Map.of(), Map.of(), Path.of("/bundle"));
        String relativeImageName = style == PathStyle.Windows ? "\\out\\app" : "my\\app";
        String absoluteImageName = style == PathStyle.Windows ? "D:\\out\\app" : "/out/my\\app";
        String fileName = style == PathStyle.Windows ? "app" : "my\\app";

        // Discard directories but preserve the Unix filename's literal backslash.
        assertEquals(List.of("-o", fileName), rewriter.rewrite(List.of("-o", relativeImageName)));
        assertEquals(List.of("-o", fileName), rewriter.rewrite(List.of("-o", absoluteImageName)));
    }

    @Test
    public void rewritesUnixSourceImagePathsToFileNames() {
        BundleSupportArgumentRewriter rewriter = newRewriter(PathStyle.Unix, Map.of(), Map.of(), Path.of("/bundle"));

        for (String imageName : List.of("/foo/bar/image_name", "foo/bar/image_name", "image_name")) {
            assertEquals(imageName, List.of("-o", "image_name"), rewriter.rewrite(List.of("-o", imageName)));
        }
    }

    @Test
    public void rejectsImageOutputPathsWithoutFileNames() {
        for (PathStyle style : List.of(PathStyle.Unix, PathStyle.Windows)) {
            BundleSupportArgumentRewriter rewriter = newRewriter(style, Map.of(), Map.of(), Path.of("/bundle"));
            List<String> imageNames = style == PathStyle.Windows ? List.of("", "\\", "C:\\", "C:", "\\\\server\\share\\") : List.of("", "/");
            for (String imageName : imageNames) {
                NativeImage.NativeImageError error = assertThrows(NativeImage.NativeImageError.class, () -> rewriter.rewrite(List.of("-o", imageName)));
                assertEquals("Invalid image output argument '-o " + imageName + "': path must contain a filename.", error.getMessage());
            }
        }
    }

    @Test
    public void preservesArgumentPlatformsWhenSerializingDerivedBundleArgs() {
        List<String> queueSnapshot = List.of(
                        "-cp", "/tmp/bundleRoot-18438110581797259606/input/classes/cp/cp", "HelloJava",
                        "--pgo",
                        "-cp", "/opt/new.jar",
                        "--bundle-create=simple-bundle-pgo.nib");
        List<String> currentBuildArgs = List.of("-cp", "/tmp/bundleRoot-18438110581797259606/input/classes/cp/cp", "HelloJava", "--pgo");
        List<ArgumentGroup> bundleFileBuildArgGroups = List.of(
                        new ArgumentGroup("windows-amd64", List.of("-cp", "C:\\work\\cp", "HelloJava")),
                        new ArgumentGroup("linux-amd64", List.of("--pgo")));

        List<ArgumentGroup> serialized = BundleSupport.serializeUpdatedBundleArgs(queueSnapshot, currentBuildArgs, bundleFileBuildArgGroups, "linux-amd64");

        assertEquals(List.of(
                        new ArgumentGroup("windows-amd64", List.of("-cp", "C:\\work\\cp", "HelloJava")),
                        new ArgumentGroup("linux-amd64", List.of("--pgo")),
                        new ArgumentGroup("linux-amd64", List.of("-cp", "/opt/new.jar", "--bundle-create=simple-bundle-pgo.nib"))), serialized);
    }

    @Test
    public void parsesLegacyBuildArgsAsSinglePlatformGroup() throws Exception {
        List<ArgumentGroup> groups = BundleArgsParser.parseBuildArgumentGroups(new java.io.StringReader("""
                        ["-cp", "C:\\\\work\\\\app.jar"]
                        """), "windows-amd64", true);

        assertEquals(List.of(new ArgumentGroup("windows-amd64", List.of("-cp", "C:\\work\\app.jar"))), groups);
        assertEquals(PathStyle.Windows, PathStyle.fromBundlePlatform(groups.getFirst().platform()));
    }

    @Test
    public void parsesBuildArgumentGroupsWithPlatformProvenance() throws Exception {
        List<ArgumentGroup> groups = BundleArgsParser.parseBuildArgumentGroups(new java.io.StringReader("""
                        [
                          {
                            "platform": "windows-amd64",
                            "args": ["-cp", "C:\\\\work\\\\app.jar"]
                          },
                          {
                            "platform": "linux-amd64",
                            "args": ["-cp", "/opt/new.jar"]
                          }
                        ]
                        """), "ignored", false);

        assertEquals(List.of(
                        new ArgumentGroup("windows-amd64", List.of("-cp", "C:\\work\\app.jar")),
                        new ArgumentGroup("linux-amd64", List.of("-cp", "/opt/new.jar"))), groups);
        assertEquals(PathStyle.Windows, PathStyle.fromBundlePlatform(groups.get(0).platform()));
        assertEquals(PathStyle.Unix, PathStyle.fromBundlePlatform(groups.get(1).platform()));
    }

    @Test
    public void filtersIdentityCanonicalizationsFromBundleFileOutput() {
        Map<PortablePath, PortablePath> canonicalizations = Map.of(
                        source("C:\\work\\app.jar", PathStyle.Windows), source("C:\\work\\app.jar", PathStyle.Windows),
                        source("target\\app.jar", PathStyle.Windows), source("C:\\work\\target\\app.jar", PathStyle.Windows));

        List<Map.Entry<PortablePath, PortablePath>> filtered = BundlePathMap.withoutIdentityMappings(canonicalizations).collect(Collectors.toList());

        assertEquals(List.of(Map.entry(source("target\\app.jar", PathStyle.Windows), source("C:\\work\\target\\app.jar", PathStyle.Windows))), filtered);
    }

    @Test
    public void preservesLoadedAndNewWindowsMappingsWhenCreatingDerivedBundle() throws Exception {
        Map<PortablePath, PortablePath> mappings = new HashMap<>();
        BundlePathMap.parseAndRegister(new java.io.StringReader("""
                        [
                          {
                            "src": {
                              "style": "Windows",
                              "text": "win-rel/cp"
                            },
                            "dst": {
                              "style": "BundleRelative",
                              "text": "input/classes/cp"
                            }
                          }
                        ]
                        """), mappings);
        mappings.put(source("win-rel\\new-cp", PathStyle.Windows), bundle("input/classes/cp/new-cp"));
        mappings.put(source("win-drive-rel\\new-cp", PathStyle.Windows), bundle("input/classes/cp/drive-relative-new-cp"));

        Path tempFile = Files.createTempFile("bundle-derived-paths", ".json");
        try (JsonWriter writer = new JsonWriter(tempFile)) {
            BundlePathMap.printPathMapping(mappings.entrySet().stream().filter(entry -> entry.getKey().text().equals("cp")).findFirst().orElseThrow(), writer);
            writer.append(',');
            BundlePathMap.printPathMapping(mappings.entrySet().stream().filter(entry -> entry.getKey().text().equals("win-rel/new-cp")).findFirst().orElseThrow(), writer);
            writer.append(',');
            BundlePathMap.printPathMapping(mappings.entrySet().stream().filter(entry -> entry.getKey().text().equals("win-drive-rel/new-cp")).findFirst().orElseThrow(), writer);
        }

        assertEquals("""
                        {"src":{"style":"Windows","kind":"Relative","text":"cp"},"dst":{"style":"BundleRelative","kind":"Relative","text":"input/classes/cp"}},\
                        {"src":{"style":"Windows","kind":"Relative","text":"win-rel/new-cp"},"dst":{"style":"BundleRelative","kind":"Relative","text":"input/classes/cp/new-cp"}},\
                        {"src":{"style":"Windows","kind":"Relative","text":"win-drive-rel/new-cp"},"dst":{"style":"BundleRelative","kind":"Relative","text":"input/classes/cp/drive-relative-new-cp"}}""",
                        Files.readString(tempFile));
    }

    private static String json(Object value) throws Exception {
        StringWriter text = new StringWriter();
        try (JsonWriter writer = new JsonWriter(text)) {
            writer.print(value);
        }
        return text.toString();
    }

    private static void writeJar(Path path, String classPath) throws Exception {
        Manifest manifest = new Manifest();
        manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");
        manifest.getMainAttributes().put(Attributes.Name.CLASS_PATH, classPath);
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(path), manifest)) {
            jar.finish();
        }
    }

    private static BundleSupport writeAndReloadBundle(BundleSupport bundleSupport, Path file) {
        bundleSupport.writeBundle = true;
        bundleSupport.nativeImage.buildExecutable = false;
        bundleSupport.updateBundleLocation(file, true);
        bundleSupport.complete();
        NativeImage nativeImage = new NativeImage(new NativeImage.BuildConfiguration(Path.of("."), Path.of("."), List.of()));
        nativeImage.bundleSupport = BundleSupport.create(nativeImage, "--bundle-apply=" + file, new NativeImage.ArgumentQueue(OptionOrigin.originUser));
        return nativeImage.bundleSupport;
    }

    private static BundleSupport newBundleSupport(String... args) {
        NativeImage nativeImage = new NativeImage(new NativeImage.BuildConfiguration(Path.of("."), Path.of("."), List.of(args)));
        nativeImage.bundleSupport = BundleSupport.create(nativeImage, "--bundle-create", new NativeImage.ArgumentQueue(OptionOrigin.originUser));
        return nativeImage.bundleSupport;
    }

    private static BundleSupportArgumentRewriter newRewriter(PathStyle style, Map<PortablePath, PortablePath> canonicalizations,
                    Map<PortablePath, PortablePath> substitutions, Path bundleRoot) {
        BundleSupport bundleSupport = newBundleSupport();
        return new BundleSupportArgumentRewriter(noOpAPIOptionHandler, style, canonicalizations, substitutions,
                        (original, substitution) -> substitution == null || substitution.kind() == RootKind.Unavailable
                                        ? bundleSupport.lowerToNativePath(original, substitution)
                                        : BundlePathMap.resolveBundlePath(bundleRoot, substitution));
    }

    private static PortablePath source(String path, PathStyle style) {
        return PortablePath.parseSource(style, path);
    }

    private static PortablePath bundle(String path) {
        return BundlePathMap.bundlePath(Path.of(path));
    }

    private static DriverPathOptions.Match matchAny(String... args) {
        return DriverPathOptions.matchAny(new ArrayDeque<>(List.of(args)));
    }
}
