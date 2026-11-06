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
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.junit.Test;

import com.oracle.svm.core.util.ArchiveSupport;
import com.oracle.svm.driver.BundlePathMap.PathStyle;
import com.oracle.svm.driver.BundlePathMap.PortablePath;
import com.oracle.svm.driver.BundlePathMap.RootKind;
import com.oracle.svm.driver.launcher.configuration.BundleArgsParser;
import com.oracle.svm.driver.launcher.configuration.BundleArgsParser.ArgumentGroup;
import com.oracle.svm.driver.launcher.json.BundleJSONParserException;
import com.oracle.svm.shared.option.BundleMember.Role;
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
    public void preservesUnixComponentsUntilNativeMaterialization() {
        PortablePath original = source("reports/draft\\final.json", PathStyle.Unix);
        String nativeFileName = File.separatorChar == '\\' ? "draft\uFFFDfinal.json" : "draft\\final.json";

        // Portable parsing and filename selection preserve the literal backslash on every host.
        assertEquals("reports/draft\\final.json", original.text());
        assertEquals("draft\\final.json", original.getFileName().text());
        // Native materialization replaces the character only on Windows, retaining two components.
        assertEquals(Path.of("reports", nativeFileName).toString(), original.platformRelativePathText());
        assertEquals(nativeFileName, original.getFileName().platformRelativePathText());
    }

    @Test
    public void relativePathRenderingRejectsAbsolutePathsForBothSourcePlatforms() {
        for (PortablePath absolute : List.of(source("/out/app", PathStyle.Unix), source("C:\\out\\app", PathStyle.Windows))) {
            // Rendering must enforce the relative-only contract regardless of source syntax.
            assertThrows(IllegalArgumentException.class, absolute::platformRelativePathText);
        }
    }

    /// Replays a Unix bundle whose two input filenames differ only by a backslash versus U+FFFD.
    /// Windows materializes both basenames with U+FFFD, but numbered slots retain their identities.
    /// Derivation must record the materialized destinations while retaining the original Unix lookup keys.
    @Test
    public void replaysAndDerivesUnixBackslashFilenames() throws Exception {
        Path fixture = Files.createTempDirectory("bundle-backslash-test");
        Path bundleFile = fixture.resolve("unix.nib");
        String destination = "input/classes/cp/0/my\\app.jar";
        PortablePath original = source("/source/my\\app.jar", PathStyle.Unix);
        PortablePath replacementOriginal = source("/source/my\uFFFDapp.jar", PathStyle.Unix);
        Map<String, String> entries = Map.of(
                        "META-INF/nibundle.properties", """
                                        BundleFileVersionMajor=3
                                        BundleFileVersionMinor=0
                                        NativeImagePlatform=linux-amd64
                                        """,
                        "input/stage/path_canonicalizations.json", "[]",
                        "input/stage/path_substitutions.json", json(List.of(
                                        Map.of("src", Map.of("style", "Unix", "kind", "Absolute", "text", original.text()),
                                                        "dst", Map.of("style", "BundleRelative", "kind", "Relative", "text", destination)),
                                        Map.of("src", Map.of("style", "Unix", "kind", "Absolute", "text", replacementOriginal.text()),
                                                        "dst", Map.of("style", "BundleRelative", "kind", "Relative", "text", "input/classes/cp/1/my\uFFFDapp.jar")))),
                        "input/stage/build.json", json(List.of(Map.of("platform", "linux-amd64", "args",
                                        List.of("--class-path=/source/my\\app.jar:/source/my\uFFFDapp.jar", "-o", "/out/my\\app")))),
                        destination, "backslash input",
                        "input/classes/cp/1/my\uFFFDapp.jar", "replacement input");
        // Create the archive directly so its Unix entry names are independent of the test host.
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(bundleFile))) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        NativeImage nativeImage = new NativeImage(new NativeImage.BuildConfiguration(Path.of("."), Path.of("."), List.of()));
        nativeImage.bundleSupport = BundleSupport.create(nativeImage, "--bundle-apply=" + bundleFile, new NativeImage.ArgumentQueue(OptionOrigin.originUser));
        BundleSupport support = nativeImage.bundleSupport;
        String nativeFileName = File.separatorChar == '\\' ? "my\uFFFDapp.jar" : "my\\app.jar";
        String nativeImageName = File.separatorChar == '\\' ? "my\uFFFDapp" : "my\\app";
        for (int generation = 0; generation < 3; generation++) {
            if (generation > 0) {
                // Save the extracted files into a derived bundle and replay it in a fresh root directory.
                support = writeAndReloadBundle(support, fixture.resolve("derived-" + generation + ".nib"));
            }
            // Extracted layout (the first basename becomes my\uFFFDapp.jar on Windows):
            // bundle-root/input/classes/cp/0/my\app.jar
            // bundle-root/input/classes/cp/1/my\uFFFDapp.jar
            Path capturedInput = support.resolveSubstitutedPath(original);
            Path replacementInput = support.resolveSubstitutedPath(replacementOriginal);
            // Original Unix paths resolve to the actual extracted files, including after derivation.
            assertEquals(support.classPathDir.resolve("0").resolve(nativeFileName), capturedInput);
            assertEquals("backslash input", Files.readString(capturedInput));
            // Numbered slots prevent the two source filenames from becoming the same Windows input.
            assertEquals(support.classPathDir.resolve("1/my\uFFFDapp.jar"), replacementInput);
            assertEquals("replacement input", Files.readString(replacementInput));
            // Argument rewriting uses the same native destinations and preserves the image basename.
            assertEquals(List.of("--class-path=" + capturedInput + File.pathSeparator + replacementInput, "-o", nativeImageName), support.getNativeImageArgs());
        }
    }

    /// Applies a format 2.0 bundle with a flat input and an old output substitution, then derives it.
    /// The input stays at its recorded location, the output cannot become an input, and the new bundle
    /// advertises format 3.0 without losing access to the captured file.
    @Test
    public void upgradesVersionTwoFlatInputsToVersionThree() throws Exception {
        Path fixture = Files.createTempDirectory("bundle-version-two-test");
        Path bundleFile = fixture.resolve("version-two.nib");
        PortablePath original = source("/source/app.jar", PathStyle.Unix);
        PortablePath oldOutput = source("/source/report.json", PathStyle.Unix);
        Map<String, String> entries = Map.of(
                        "META-INF/nibundle.properties", """
                                        BundleFileVersionMajor=2
                                        BundleFileVersionMinor=0
                                        NativeImagePlatform=linux-amd64
                                        """,
                        "input/stage/path_canonicalizations.json", "[]",
                        "input/stage/path_substitutions.json", json(List.of(
                                        Map.of("src", Map.of("style", "Unix", "kind", "Absolute", "text", original.text()),
                                                        "dst", Map.of("style", "BundleRelative", "kind", "Relative", "text", "input/classes/cp/app.jar")),
                                        Map.of("src", Map.of("style", "Unix", "kind", "Absolute", "text", oldOutput.text()),
                                                        "dst", Map.of("style", "BundleRelative", "kind", "Relative", "text", "output/other/report.json")))),
                        "input/stage/build.json", json(List.of(Map.of("platform", "linux-amd64", "args", List.of("--class-path=/source/app.jar")))),
                        "input/classes/cp/app.jar", "version two input");
        // The archive models the flat layout written by the format 2.0 driver:
        // bundle-root/input/classes/cp/app.jar
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(bundleFile))) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        NativeImage nativeImage = new NativeImage(new NativeImage.BuildConfiguration(Path.of("."), Path.of("."), List.of()));
        nativeImage.bundleSupport = BundleSupport.create(nativeImage, "--bundle-apply=" + bundleFile, new NativeImage.ArgumentQueue(OptionOrigin.originUser));
        BundleSupport support = nativeImage.bundleSupport;
        Path capturedInput = support.resolveSubstitutedPath(original);
        // Applying 2.0 resolves its recorded flat root, without needing the original source file.
        assertEquals(support.classPathDir.resolve("app.jar"), capturedInput);
        assertEquals("version two input", Files.readString(capturedInput));
        // Historical output mappings are discarded rather than treated as captured inputs.
        assertNull(support.resolveSubstitutedPath(oldOutput));

        // Derive the loaded 2.0 bundle and apply the resulting bundle in a fresh root directory.
        Path derivedFile = fixture.resolve("version-three.nib");
        BundleSupport replay = writeAndReloadBundle(support, derivedFile);
        try (JarFile archive = new JarFile(derivedFile.toFile())) {
            Map<String, String> properties = ArchiveSupport.loadProperties(archive.getInputStream(archive.getJarEntry("META-INF/nibundle.properties")));
            // Derived bundles advertise the new format, even when all their inputs retain a flat layout.
            assertEquals("3", properties.get("BundleFileVersionMajor"));
            assertEquals("0", properties.get("BundleFileVersionMinor"));
        }
        Path replayedInput = replay.resolveSubstitutedPath(original);
        // Upgrading the bundle preserves the inherited input location and its contents.
        assertEquals(replay.classPathDir.resolve("app.jar"), replayedInput);
        assertEquals("version two input", Files.readString(replayedInput));
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

        BundleSupport support = newBundleSupport(canonicalizations, substitutions);
        Path bundleRoot = support.rootDir;
        BundleSupportArgumentRewriter rewriter = new BundleSupportArgumentRewriter(support, noOpAPIOptionHandler, PathStyle.Windows);
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

        BundleSupport support = newBundleSupport(canonicalizations, substitutions);
        Path bundleRoot = support.rootDir;
        BundleSupportArgumentRewriter rewriter = new BundleSupportArgumentRewriter(support, noOpAPIOptionHandler, PathStyle.Windows);
        List<String> rewritten = rewriter.rewrite(List.of("--class-path=target\\app.jar;lib\\helper.jar"));

        String expectedClassPath = bundleRoot.resolve(Path.of("input/classes/cp/app.jar")) + File.pathSeparator + bundleRoot.resolve(Path.of("input/classes/cp/helper.jar"));
        assertEquals(List.of("--class-path=" + expectedClassPath), rewritten);
    }

    @Test
    public void rewritesUnsubstitutedWindowsClasspathWithoutLegacyEncoding() {
        BundleSupportArgumentRewriter rewriter = newRewriter(PathStyle.Windows, Map.of(), Map.of());

        List<String> rewritten = rewriter.rewrite(List.of("--class-path=win-rel\\target\\app.jar"));

        assertEquals(List.of("--class-path=" + Path.of("win-rel", "target", "app.jar")), rewritten);
    }

    @Test
    public void preservesUnsubstitutedAbsoluteSourcePath() {
        PathStyle style = PathStyle.currentSourceStyle();
        Path originalPath = Path.of("original", "missing.jar").toAbsolutePath();
        BundleSupportArgumentRewriter rewriter = newRewriter(style, Map.of(), Map.of());

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
        if (PathStyle.Windows.isCurrent()) {
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
        BundleSupportArgumentRewriter rewriter = newRewriter(style, canonicalizations, Map.of());

        // A missing input must retain its recorded location instead of using the replay directory.
        assertEquals(List.of("--class-path=" + originalPath, "--module-path=" + originalPath),
                        rewriter.rewrite(List.of("--class-path=missing.jar", "--module-path=missing.jar")));
    }

    @Test
    public void preservesUnsubstitutedWindowsRootsOnWindows() {
        assumeTrue(PathStyle.Windows.isCurrent());
        BundleSupportArgumentRewriter rewriter = newRewriter(PathStyle.Windows, Map.of(), Map.of());

        // A directory-relative path retains the current drive root, not the working directory.
        assertEquals(List.of("--class-path=\\libs\\dep.jar"), rewriter.rewrite(List.of("--class-path=\\libs\\dep.jar")));
        // A drive-relative path retains the specified drive's working directory.
        assertEquals(List.of("--module-path=D:libs\\dep.jar"), rewriter.rewrite(List.of("--module-path=D:libs\\dep.jar")));
    }

    @Test
    public void keepsForeignRootedPathsMissingWithoutSplittingPathLists() {
        BundleSupport bundleSupport = newBundleSupport();
        PathStyle foreignStyle = PathStyle.Unix.isCurrent() ? PathStyle.Windows : PathStyle.Unix;
        List<String> originalPaths = foreignStyle == PathStyle.Windows
                        ? List.of("D:\\missing.jar", "\\libs\\dep.jar", "D:missing.jar", "\\\\server\\share\\missing.jar")
                        : List.of("/missing.jar");
        BundleSupportArgumentRewriter rewriter = new BundleSupportArgumentRewriter(bundleSupport, noOpAPIOptionHandler, foreignStyle);
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
        PathStyle foreignStyle = PathStyle.Unix.isCurrent() ? PathStyle.Windows : PathStyle.Unix;
        PortablePath canonical = source(foreignStyle == PathStyle.Windows ? "D:\\original\\missing.jar" : "/original/missing.jar", foreignStyle);
        bundleSupport.pathCanonicalizations.put(source("missing.jar", foreignStyle), canonical);
        BundleSupportArgumentRewriter rewriter = new BundleSupportArgumentRewriter(bundleSupport, noOpAPIOptionHandler, foreignStyle);

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
        BundleSupportArgumentRewriter rewriter = newRewriter(PathStyle.Windows, Map.of(), Map.of());

        // Discard Windows source roots and directories regardless of the replay platform.
        for (String imageName : List.of("C:\\dir\\image", "\\dir\\image", "C:dir\\image", "\\\\server\\share\\dir\\image", "dir\\image", "dir/image", "image")) {
            assertEquals(imageName, List.of("-o", "image"), rewriter.rewrite(List.of("-o", imageName)));
        }
    }

    @Test
    public void preservesNativeImageFileNameSyntax() {
        PathStyle style = PathStyle.currentSourceStyle();
        BundleSupportArgumentRewriter rewriter = newRewriter(style, Map.of(), Map.of());
        String relativeImageName = style == PathStyle.Windows ? "\\out\\app" : "my\\app";
        String absoluteImageName = style == PathStyle.Windows ? "D:\\out\\app" : "/out/my\\app";
        String fileName = style == PathStyle.Windows ? "app" : "my\\app";

        // Discard directories but preserve the Unix filename's literal backslash.
        assertEquals(List.of("-o", fileName), rewriter.rewrite(List.of("-o", relativeImageName)));
        assertEquals(List.of("-o", fileName), rewriter.rewrite(List.of("-o", absoluteImageName)));
    }

    @Test
    public void rewritesUnixSourceImagePathsToFileNames() {
        BundleSupportArgumentRewriter rewriter = newRewriter(PathStyle.Unix, Map.of(), Map.of());

        for (String imageName : List.of("/foo/bar/image_name", "foo/bar/image_name", "image_name")) {
            assertEquals(imageName, List.of("-o", "image_name"), rewriter.rewrite(List.of("-o", imageName)));
        }
    }

    @Test
    public void rejectsImageOutputPathsWithoutFileNames() {
        for (PathStyle style : List.of(PathStyle.Unix, PathStyle.Windows)) {
            BundleSupportArgumentRewriter rewriter = newRewriter(style, Map.of(), Map.of());
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

    @Test
    public void pathReferencesReuseCapturedInputsWithoutCapturingNewFiles() throws Exception {
        BundleSupport support = newBundleSupport();
        Path classes = Files.createDirectories(newBundleSupport().rootDir.resolve("classes"));
        assertEquals(classes, support.substituteAuxiliaryPath(classes, Role.InputReference));
        Path captured = support.substituteClassPath(classes);
        assertEquals(captured, support.substituteAuxiliaryPath(classes, Role.InputReference));
        try (Stream<Path> inputs = Files.list(support.auxiliaryDir)) {
            assertEquals(0, inputs.count());
        }
    }

    @Test
    public void replayOutputsBypassInputMappingsForBothSourcePlatforms() {
        for (PathStyle style : List.of(PathStyle.Unix, PathStyle.Windows)) {
            BundleSupport support = newBundleSupport();
            PortablePath original = source(style == PathStyle.Unix ? "/original/classes" : "C:\\original\\classes", style);
            PortablePath captured = bundle("input/classes/cp/0/classes");
            support.pathCanonicalizations.put(source("classes", style), original);
            support.pathSubstitutions.put(original, captured);
            BundleSupportArgumentRewriter rewriter = new BundleSupportArgumentRewriter(support, support.nativeImage.apiOptionHandler, style);
            // Replay must restore the captured classpath input but redirect TempDirectory to the writable output directory.
            assertEquals(List.of("-cp", support.rootDir.resolve("input/classes/cp/0/classes").toString(),
                            "-H:TempDirectory=" + support.auxiliaryOutputDir.resolve("classes")),
                            rewriter.rewrite(List.of("-cp", "classes", "-H:TempDirectory=classes")));
            if (style == PathStyle.Windows) {
                // Different capitalization of the same Windows source path must reuse the existing output destination.
                assertEquals(support.auxiliaryOutputDir.resolve("classes"), support.substituteOutputPath(source("c:\\ORIGINAL\\CLASSES", style)));
            }
        }
    }

    @Test
    public void outputPathsDoNotReuseCapturedInputs() throws Exception {
        checkOverlappingInputAndOutput(false);
    }

    @Test
    public void inputPathsDoNotReuseOutputDestinations() throws Exception {
        checkOverlappingInputAndOutput(true);
    }

    /// Simulates a build that uses the same original directory for both inputs and outputs.
    /// The bundle must capture inputs under input/ and redirect outputs under output/other,
    /// regardless of whether output allocation happens before or after input capture.
    ///
    /// For each input kind (classpath, module-path, and auxiliary), capture the input, write an
    /// output, then delete the original directory and save/reload the bundle. Verify that the
    /// captured input survives, input references still find it,
    /// and output allocation remains separate. Finally, check that a different original output
    /// path with the same basename receives a distinct destination.
    private static void checkOverlappingInputAndOutput(boolean outputFirst) throws Exception {
        List<BiFunction<BundleSupport, Path, Path>> substitutions = List.of(
                        BundleSupport::substituteClassPath, BundleSupport::substituteModulePath,
                        (support, path) -> support.substituteAuxiliaryPath(path, Role.Input));
        for (var substitute : substitutions) {
            Path fixture = Files.createTempDirectory("bundle-overlap-test");
            Path original = Files.createDirectory(fixture.resolve("shared"));
            Files.writeString(original.resolve("input.txt"), "captured input");
            BundleSupport support = newBundleSupport();
            if (outputFirst) {
                // Reserve an output location for fixture/shared before capturing it as an input.
                Path allocatedOutput = support.substituteAuxiliaryPath(original, Role.Output);
                // Processing the output before input capture must still allocate it under output/other.
                assertEquals(support.auxiliaryOutputDir.resolve("shared"), allocatedOutput);
            }
            // Capture directory fixture/shared as an input directory.
            Path input = substitute.apply(support, original);
            // Ensure capture has placed the input under bundle-root/input, regardless of which role was processed first.
            assertTrue(input.startsWith(support.inputDir));
            // Reserve an output location for fixture/shared, or reuse the reservation made above.
            Path allocatedOutput = support.substituteAuxiliaryPath(original, Role.Output);
            // The same original path must resolve to a separate writable destination for outputs.
            assertEquals(support.auxiliaryOutputDir.resolve("shared"), allocatedOutput);
            Files.createDirectories(allocatedOutput);
            Files.writeString(allocatedOutput.resolve("output.txt"), "generated output");
            // Relevant files at this point:
            // fixture/shared/input.txt
            // bundle-root/input/<capture-dir>/0/shared/input.txt
            // bundle-root/output/other/shared/output.txt
            // <capture-dir> is classes/cp, classes/p, or auxiliary, depending on the input role.

            // Remove the source directory so replay can only use the captured input.
            Files.delete(original.resolve("input.txt"));
            Files.delete(original);

            // Now switching to bundle-apply mode
            Path relativeInput = support.rootDir.relativize(input);
            support = writeAndReloadBundle(support, fixture.resolve("overlap.nib"));
            input = support.rootDir.resolve(relativeInput);
            Path restoredInput = substitute.apply(support, original);
            // Replay must restore the input mapping under the newly extracted bundle root.
            assertEquals(input, restoredInput);
            // Resolve fixture/shared as a reference to the already captured input.
            Path referencedInput = support.substituteAuxiliaryPath(original, Role.InputReference);
            // An input reference, such as Preserve's path= selection, must find that same captured input.
            assertEquals(input, referencedInput);
            // The captured contents must survive replay even though the original directory is gone.
            assertEquals("captured input", Files.readString(input.resolve("input.txt")));
            // Recreate the output reservation for fixture/shared in the replayed bundle.
            Path replayedOutput = support.substituteAuxiliaryPath(original, Role.Output);
            // Replay must allocate the output under output/other rather than reuse the input mapping.
            assertEquals(support.auxiliaryOutputDir.resolve("shared"), replayedOutput);
            // Reserve an output location for another original path whose basename is also shared.
            Path collidingOutput = support.substituteAuxiliaryPath(fixture.resolve("other/shared"), Role.Output);
            // A different original output path with the same basename must receive its own destination.
            assertEquals(support.auxiliaryOutputDir.resolve("shared_1"), collidingOutput);
        }
    }

    @Test
    public void rewritesOnlyPrefixedPathEntries() {
        BundleSupport support = newBundleSupport();
        APIOptionHandler handler = support.nativeImage.apiOptionHandler;
        String option = "-H:Preserve@user+api=none,module=java.logging,path=classes,package=my.library.*,all";
        assertEquals("-H:Preserve@user+api=none,module=java.logging,path=captured,package=my.library.*,all",
                        handler.transformBuilderArgument(option, (_, role) -> {
                            assertEquals(Role.InputReference, role);
                            return Path.of("captured");
                        }));
        assertEquals("-H:Preserve@user+api=none,module=java.logging,path=rewritten,package=my.library.*,all",
                        handler.rewriteBundleHostedOptionArgument(option, (_, _) -> "rewritten"));
        for (String value : List.of("", "path=")) {
            String emptyOption = "-H:Preserve=" + value;
            assertEquals(emptyOption, handler.transformBuilderArgument(emptyOption, (_, _) -> {
                throw new AssertionError("Empty selection was treated as a path");
            }));
        }
    }

    @Test
    public void preservesNumberedPathsAcrossBundleDerivation() throws Exception {
        List<BiFunction<BundleSupport, Path, Path>> substitutions = List.of(
                        BundleSupport::substituteClassPath, BundleSupport::substituteModulePath,
                        (support, path) -> support.substituteAuxiliaryPath(path, Role.Input));
        for (var substitute : substitutions) {
            Path fixture = newBundleSupport().rootDir;
            BundleSupport support = newBundleSupport();
            Map<Path, Path> destinations = new LinkedHashMap<>();
            for (int generation = 0; generation < 4; generation++) {
                support.writeBundle = true;
                for (int entry = 0; entry < 2; entry++) {
                    Path original = Files.createDirectories(fixture.resolve(generation + "-" + entry)).resolve("same");
                    if (entry == 0) {
                        Files.createDirectory(original);
                    } else {
                        Files.writeString(original, "input");
                    }
                    Path destination = support.rootDir.relativize(substitute.apply(support, original));
                    for (Path previous : destinations.values()) {
                        assertFalse("Reused slot " + destination.getParent(), previous.getParent().equals(destination.getParent()));
                    }
                    destinations.put(original, destination);
                    Files.delete(original);
                }
                support = writeAndReloadBundle(support, fixture.resolve(generation + ".nib"));
                for (var entry : new ArrayList<>(destinations.entrySet()).reversed()) {
                    assertEquals(entry.getValue(), support.rootDir.relativize(substitute.apply(support, entry.getKey())));
                }
            }
        }
    }

    @Test
    public void preservesFlatOutputPathsAcrossBundleDerivation() throws Exception {
        Path fixture = newBundleSupport().rootDir;
        Path original = fixture.resolve("0");
        Path legacyBundle = fixture.resolve("legacy.nib");
        Map<String, String> entries = Map.of(
                        "META-INF/nibundle.properties", """
                                        BundleFileVersionMajor=0
                                        BundleFileVersionMinor=9
                                        NativeImagePlatform=%s
                                        """.formatted(NativeImage.platform),
                        "input/stage/path_canonicalizations.json", "[]",
                        "input/stage/path_substitutions.json", json(List.of(Map.of("src", original.toString(), "dst", "output/other/0"))),
                        "input/stage/build.json", "[]");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(legacyBundle))) {
            for (var entry : entries.entrySet()) {
                zip.putNextEntry(new ZipEntry(entry.getKey()));
                zip.write(entry.getValue().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
        }
        NativeImage nativeImage = new NativeImage(new NativeImage.BuildConfiguration(Path.of("."), Path.of("."), List.of()));
        nativeImage.bundleSupport = BundleSupport.create(nativeImage, "--bundle-apply=" + legacyBundle, new NativeImage.ArgumentQueue(OptionOrigin.originUser));
        BundleSupport support = nativeImage.bundleSupport;
        for (int generation = 0; generation < 3; generation++) {
            Path oldOutput = support.substituteAuxiliaryPath(original, Role.Output);
            Path newOutput = support.substituteAuxiliaryPath(fixture.resolve("new-" + generation), Role.Output);
            assertEquals(support.rootDir.resolve("output/other/0"), oldOutput);
            assertEquals(support.rootDir.resolve("output/other/new-" + generation), newOutput);
            assertTrue(Files.notExists(oldOutput));
            support = writeAndReloadBundle(support, fixture.resolve("derived-" + generation + ".nib"));
            assertNull(support.resolveSubstitutedPath(source(original.toString(), PathStyle.currentSourceStyle())));
        }
    }

    @Test
    public void relocatesDistinctOutputNamesWithoutChangingSharedTargets() throws Exception {
        Path fixture = newBundleSupport().rootDir;
        Path first = fixture.resolve("first/report.json");
        Path second = fixture.resolve("second/report.json");
        Path naturallySuffixed = fixture.resolve("third/report_1.json");
        BundleSupport support = newBundleSupport();
        for (int generation = 0; generation < 3; generation++) {
            Path output = support.substituteAuxiliaryPath(first, Role.Output);
            assertEquals(support.auxiliaryOutputDir.resolve("report.json"), output);
            assertEquals(output, support.substituteAuxiliaryPath(first, Role.Output));
            assertTrue(Files.notExists(output));
            assertEquals(support.auxiliaryOutputDir.resolve("report_1.json"), support.substituteAuxiliaryPath(naturallySuffixed, Role.Output));
            Path relocated = support.substituteAuxiliaryPath(second, Role.Output);
            assertEquals(support.auxiliaryOutputDir.resolve("report_2.json"), relocated);
            assertEquals(relocated, support.substituteAuxiliaryPath(second, Role.Output));
            assertEquals(output, support.substituteAuxiliaryPath(first, Role.Output));
            assertTrue(Files.notExists(relocated));
            support = writeAndReloadBundle(support, fixture.resolve("outputs-" + generation + ".nib"));
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

    private static BundleSupport newBundleSupport(Map<PortablePath, PortablePath> canonicalizations, Map<PortablePath, PortablePath> substitutions) {
        BundleSupport support = newBundleSupport();
        support.pathCanonicalizations.putAll(canonicalizations);
        support.pathSubstitutions.putAll(substitutions);
        return support;
    }

    private static BundleSupportArgumentRewriter newRewriter(PathStyle style, Map<PortablePath, PortablePath> canonicalizations,
                    Map<PortablePath, PortablePath> substitutions) {
        return new BundleSupportArgumentRewriter(newBundleSupport(canonicalizations, substitutions), noOpAPIOptionHandler, style);
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
