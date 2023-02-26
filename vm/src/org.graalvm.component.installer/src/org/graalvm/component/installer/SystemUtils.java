/*
 * Copyright (c) 2018, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer;

import static org.graalvm.component.installer.CommonConstants.ARCH_AARCH64;
import static org.graalvm.component.installer.CommonConstants.ARCH_AMD64;
import static org.graalvm.component.installer.CommonConstants.ARCH_X8664;
import static org.graalvm.component.installer.CommonConstants.CAP_OS_NAME;
import static org.graalvm.component.installer.CommonConstants.OS_MACOS_DARWIN;
import static org.graalvm.component.installer.CommonConstants.OS_TOKEN_LINUX;
import static org.graalvm.component.installer.CommonConstants.OS_TOKEN_MAC;
import static org.graalvm.component.installer.CommonConstants.OS_TOKEN_MACOS;
import static org.graalvm.component.installer.CommonConstants.OS_TOKEN_WINDOWS;
import static org.graalvm.component.installer.CommonConstants.SYSPROP_ARCH_NAME;
import static org.graalvm.component.installer.CommonConstants.SYSPROP_JAVA_VERSION;
import static org.graalvm.component.installer.CommonConstants.SYSPROP_OS_NAME;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.channels.SeekableByteChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.AclFileAttributeView;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.PosixFileAttributeView;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.graalvm.component.installer.model.ComponentRegistry;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.Locale;

/**
 *
 * @author sdedic
 */
public class SystemUtils {
    /**
     * Path component delimiter.
     */
    public static final char DELIMITER_CHAR = '/'; // NOI18N

    /**
     * Path component delimiter.
     */
    public static final String DELIMITER = "/"; // NOI18N

    private static final String DOT = "."; // NOI18N

    private static final String DOTDOT = ".."; // NOI18N

    private static final String SPLIT_DELIMITER = Pattern.quote(DELIMITER);

    public enum OS {
        WINDOWS(OS_TOKEN_WINDOWS),
        LINUX(OS_TOKEN_LINUX),
        MAC(OS_TOKEN_MACOS),
        UNKNOWN(null);

        private final String name;

        public String getName() {
            return name;
        }

        OS(String name) {
            this.name = name;
        }

        public static String sysName() {
            return System.getProperty(SYSPROP_OS_NAME);
        }

        /**
         * Obtains current OS enum.
         *
         * @return OS enum identifier
         */
        public static OS get() {
            return fromName(sysName());
        }

        public static OS fromName(String osName) {
            return osName == null ? UNKNOWN : fromNameLower(osName.toLowerCase(Locale.ENGLISH));
        }

        private static OS fromNameLower(String osNameLower) {
            if (!osNameLower.isBlank()) {
                if (osNameLower.contains(OS_TOKEN_WINDOWS)) {
                    return WINDOWS;
                }
                if (osNameLower.contains(OS_TOKEN_LINUX)) {
                    return LINUX;
                }
                if (osNameLower.contains(OS_TOKEN_MAC) || osNameLower.contains(OS_MACOS_DARWIN)) {
                    return MAC;
                }
            }
            return UNKNOWN;
        }
    }

    public enum ARCH {
        AMD64(ARCH_AMD64),
        AARCH64(ARCH_AARCH64),
        UNKNOWN(null);

        private final String name;

        public String getName() {
            return name;
        }

        ARCH(String name) {
            this.name = name;
        }

        public static String sysName() {
            return System.getProperty(SYSPROP_ARCH_NAME);
        }

        /**
         * Obtain current ARCH enum.
         *
         * @return ARCH enum identifier
         */
        public static ARCH get() {
            return fromName(sysName());
        }

        public static ARCH fromName(String archName) {
            return archName == null ? UNKNOWN : fromNameLower(archName.toLowerCase(Locale.ENGLISH));
        }

        private static ARCH fromNameLower(String archNameLower) {
            if (!archNameLower.isBlank()) {
                if (archNameLower.contains(ARCH_AARCH64)) {
                    return AARCH64;
                }
                if (archNameLower.contains(ARCH_AMD64) || archNameLower.contains(ARCH_X8664)) {
                    return AMD64;
                }
            }
            return UNKNOWN;
        }
    }

    public static boolean nonBlankString(String string) {
        return string != null && !string.isBlank();
    }

    /**
     * Creates a proper {@link Path} from string representation. The string representation uses
     * <b>forward slashes</b> to delimit fileName components.
     * 
     * @param p the fileName specification
     * @return Path instance
     */
    public static Path fromCommonString(String p) {
        String[] comps = p.split(SPLIT_DELIMITER);
        if (comps.length == 1) {
            return Paths.get(comps[0]);
        }
        int l = comps.length - 1;
        String s = comps[0];
        System.arraycopy(comps, 1, comps, 0, l);
        comps[l] = ""; // NOI18N
        return Paths.get(s, comps);
    }

    /**
     * Creates a fileName from user-provided string. It will split the string according to OS
     * fileName component delimiter, then fileName the Path instance
     * 
     * @param p user-provided string, with OS-dependent delimiters
     * @return Path that correspond to the user-supplied string
     */
    public static Path fromUserString(String p) {
        return Paths.get(p);
    }

    /**
     * Sanity check wrapper around {@link Paths#get}. This wrapper checks that the string does NOT
     * contain any fileName component delimiter (slash, backslash).
     * 
     * @param s
     * @return Path that corresponds to the filename
     */
    public static Path fileName(String s) {
        if ((s.indexOf(DELIMITER_CHAR) >= 0) ||
                        ((DELIMITER_CHAR != File.separatorChar) && (s.indexOf(File.separatorChar) >= 0))) {
            throw new IllegalArgumentException(s);
        }
        if (DOT.equals(s) || DOTDOT.equals(s)) {
            throw new IllegalArgumentException(s);
        }
        return Paths.get(s);
    }

    /**
     * Creates a path string using {@link #DELIMITER_CHAR} as a separator. Returns the same values
     * on Windows and UNIXes.
     * 
     * @param p path to convert
     * @return path string
     */
    public static String toCommonPath(Path p) {
        StringBuilder sb = new StringBuilder();
        boolean next = false;
        for (Path comp : p) {
            if (next) {
                sb.append(DELIMITER_CHAR);
            }
            String compS = comp.toString();
            if (File.separatorChar != DELIMITER_CHAR) {
                compS = compS.replace(File.separator, DELIMITER);
            }
            sb.append(compS);
            next = true;
        }
        return sb.toString();
    }

    /**
     * Checks if running on Windows.
     * 
     * @return true, if on Windows.
     */
    public static boolean isWindows() {
        return OS.get().equals(OS.WINDOWS);
    }

    /**
     * Checks if running on Linux.
     * 
     * @return true, if on Linux.
     */
    public static boolean isLinux() {
        return OS.get().equals(OS.LINUX);
    }

    /**
     * Checks if running on Mac.
     *
     * @return true, if on Mac.
     */
    public static boolean isMac() {
        return OS.get().equals(OS.MAC);
    }

    /**
     * Checks if the path is relative and does not go above its root. The path may contain
     * {@code ..} components, but they must not traverse outside the relative subtree.
     * 
     * @param p the path, in common notation (slashes)
     * @return the path
     * @trows IllegalArgumentException if the path is invalid
     */
    public static Path fromCommonRelative(String p) {
        if (p == null) {
            return null;
        }
        return fromArray(checkRelativePath(null, p));
    }

    private static Path fromArray(String[] comps) {
        if (comps.length == 1) {
            return Paths.get(comps[0]);
        }
        int l = comps.length - 1;
        String s = comps[0];
        System.arraycopy(comps, 1, comps, 0, l);
        comps[l] = ""; // NOI18N
        return Paths.get(s, comps);
    }

    public static Path resolveRelative(Path baseDir, String p) {
        if (baseDir == null) {
            return null;
        }
        if (p == null) {
            return null;
        }
        String[] comps = checkRelativePath(baseDir, p);
        return baseDir.resolve(fromArray(comps));
    }

    public static Path fromCommonRelative(Path base, String p) {
        if (p == null) {
            return null;
        } else if (base == null) {
            return fromCommonRelative(p);
        }
        String[] comps = checkRelativePath(base, p);
        return base.resolveSibling(fromArray(comps));
    }

    /**
     * Resolves a relative path against a base, does not allow the path to go above the base root.
     * 
     * @param base base Path
     * @param p path string
     * @throws IllegalArgumentException on invalid path
     */
    public static void checkCommonRelative(Path base, String p) {
        if (p == null) {
            return;
        }
        checkRelativePath(base, p);
    }

    private static String[] checkRelativePath(Path base, String p) {
        if (p.startsWith(DELIMITER)) {
            throw new IllegalArgumentException("Absolute path");
        }
        String[] comps = p.split(SPLIT_DELIMITER);
        int d = base == null ? 0 : base.normalize().getNameCount() - 1;
        int fromIndex = 0;
        int toIndex = 0;
        for (String s : comps) {
            if (s.isEmpty()) {
                fromIndex++;
                continue;
            }
            if (DOTDOT.equals(s)) {
                d--;
                if (d < 0) {
                    throw new IllegalArgumentException("Relative path reaches above root");
                }
            } else {
                d++;
            }
            if (toIndex < fromIndex) {
                comps[toIndex] = comps[fromIndex];
            }
            fromIndex++;
            toIndex++;
        }
        if (fromIndex == toIndex) {
            return comps;
        } else {
            // return without the empty parts
            String[] newcomps = new String[toIndex];
            System.arraycopy(comps, 0, newcomps, 0, toIndex);
            return newcomps;
        }
    }

    private static final Pattern OLD_VERSION_PATTERN = Pattern.compile("([0-9]+\\.[0-9]+(\\.[0-9]+)?(\\.[0-9]+)?)(-([a-z]+)([0-9]+)?)?");

    /**
     * Will transform some widely used formats to RPM-style. Currently it transforms:
     * <ul>
     * <li>1.0.1-dev[.x] => 1.0.1.0-0.dev[.x]
     * <li>1.0.0 => 1.0.0.0
     * </ul>
     * 
     * @param v
     * @return normalized version
     */
    public static String normalizeOldVersions(String v) {
        if (v == null) {
            return null;
        }
        Matcher m = OLD_VERSION_PATTERN.matcher(v);
        if (!m.matches()) {
            return v;
        }
        String numbers = m.group(1);
        String rel = m.group(5);
        String relNo = m.group(6);

        if (numbers.startsWith("0.")) {
            return v;
        }

        if (rel == null) {
            if (m.group(3) == null) {
                return numbers + ".0";
            } else {
                return numbers;
            }
        } else {
            if (m.group(3) == null) {
                numbers = numbers + ".0";
            }
            return numbers + "-0." + rel + (relNo == null ? "" : "." + relNo);
        }
    }

    public static Path getGraalVMJDKRoot(ComponentRegistry reg) {
        if (OS.MAC.equals(OS.fromName(reg.getGraalCapabilities().get(CAP_OS_NAME)))) {
            return Paths.get("Contents", "Home");
        } else {
            return Paths.get("");
        }
    }

    /**
     * Finds a file owner. On POSIX systems, returns owner of the file. On Windows (ACL fs view)
     * returns the owner principal's name.
     * 
     * @param file the file to test
     * @return owner name
     */
    public static String findFileOwner(Path file) throws IOException {
        PosixFileAttributeView posix = file.getFileSystem().provider().getFileAttributeView(file, PosixFileAttributeView.class);
        if (posix != null) {
            return posix.getOwner().getName();
        }
        AclFileAttributeView acl = file.getFileSystem().provider().getFileAttributeView(file, AclFileAttributeView.class);
        if (acl != null) {
            return acl.getOwner().getName();
        }
        return null;
    }

    static boolean licenseTracking = false;

    public static boolean isLicenseTrackingEnabled() {
        return licenseTracking;
    }

    public static String parseURLParameters(String s, Map<String, String> params) {
        int q = s.indexOf('?'); // NOI18N
        if (q == -1) {
            return s;
        }
        String queryString = s.substring(q + 1);
        for (String parSpec : queryString.split("&")) { // NOI18N
            String[] nameAndVal = parSpec.split("="); // NOI18N
            String n;
            String v;

            n = URLDecoder.decode(nameAndVal[0], UTF_8);
            if (n.isEmpty()) {
                continue;
            }
            v = nameAndVal.length > 1 ? URLDecoder.decode(nameAndVal[1], UTF_8) : "";
            params.put(n, v);
        }
        return s.substring(0, q);
    }

    public static int getJavaMajorVersion(CommandInput cmd) {
        String v = cmd.getLocalRegistry() != null ? cmd.getLocalRegistry().getJavaVersion() : null;
        if (v == null) {
            cmd.getParameter(SYSPROP_JAVA_VERSION, true);
        }
        if (v != null) {
            return interpretJavaMajorVersion(v);
        } else {
            return getJavaMajorVersion();
        }
    }

    public static String buildUrlStringWithParameters(String baseURL, Map<String, ? extends Collection<String>> params) {
        if (params == null || params.isEmpty()) {
            return baseURL;
        }
        StringBuilder url = new StringBuilder(baseURL);
        url.append('?');
        for (Map.Entry<String, ? extends Collection<String>> entry : params.entrySet()) {
            Collection<String> vals = entry.getValue();
            String key = entry.getKey();
            if (key == null || key.isBlank() || vals == null || vals.isEmpty()) {
                continue;
            }
            for (String val : vals) {
                url.append(URLEncoder.encode(entry.getKey(), UTF_8));
                url.append('=');
                url.append(URLEncoder.encode(val, UTF_8));
                url.append('&');
            }
        }
        return url.substring(0, url.length() - 1);
    }

    public static int getJavaMajorVersion() {
        return interpretJavaMajorVersion(System.getProperty(SYSPROP_JAVA_VERSION));
    }

    /**
     * Interprets String as a java version, returning the major version number.
     * 
     * @param v the version string
     * @return the major version number, or zero if unknown
     */
    public static int interpretJavaMajorVersion(String v) {
        try {
            return Integer.parseInt(v.startsWith("1.") ? v.substring(2) : v); // NOI18N
        } catch (NumberFormatException | NullPointerException ex) {
            return 0;
        }
    }

    /**
     * Provides the location of jre lib path, optionally the arch-dependent dir. The location
     * depends on JDK version.
     * 
     * @param graalPath graalVM installation root
     * @param archDir if true, returns the arch-dependent path.
     * @return jre lib dir
     */
    public static Path getRuntimeLibDir(Path graalPath, boolean archDir) {
        Path newLibPath;
        Path base = getRuntimeBaseDir(graalPath);
        switch (OS.get()) {
            case LINUX:
                Path temp = base.resolve(Paths.get("lib")); // NOI18N
                if (!archDir || SystemUtils.getJavaMajorVersion() >= 10) {
                    newLibPath = temp;
                } else {
                    newLibPath = temp.resolve(Paths.get(OS.sysName()));
                }
                break;
            case MAC:
                newLibPath = base.resolve(Paths.get("lib")); // NOI18N
                break;
            case WINDOWS:
                newLibPath = base.resolve(Paths.get("bin")); // NOI18N
                break;
            case UNKNOWN:
            default:
                return null;
        }
        return newLibPath;
    }

    /**
     * Returns the JDK's runtime root dir. May be jre or ., depending on Java version
     * 
     * @param jdkInstallDir installation path
     * @return runtime path
     */
    public static Path getRuntimeBaseDir(Path jdkInstallDir) {
        int v = getJavaMajorVersion();
        if (v >= 10) {
            return jdkInstallDir;
        } else if (v > 0) {
            return jdkInstallDir.resolve("jre");  // NOI18N
        }
        // use fallback:
        Path jre = jdkInstallDir.resolve("jre");  // NOI18N
        if (Files.exists(jre) && Files.isDirectory(jre)) {
            return jre;
        } else {
            return jdkInstallDir;
        }
    }

    public static Path copySubtree(Path from, Path to) throws IOException {
        Files.walkFileTree(from, new CopyDirVisitor(from, to));
        return to;
    }

    public static class CopyDirVisitor extends SimpleFileVisitor<Path> {

        private Path fromPath;
        private Path toPath;
        private StandardCopyOption copyOption;

        public CopyDirVisitor(Path fromPath, Path toPath, StandardCopyOption copyOption) {
            this.fromPath = fromPath;
            this.toPath = toPath;
            this.copyOption = copyOption;
        }

        public CopyDirVisitor(Path fromPath, Path toPath) {
            this(fromPath, toPath, StandardCopyOption.REPLACE_EXISTING);
        }

        @Override
        public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs)
                        throws IOException {

            Path targetPath = toPath.resolve(fromPath.relativize(dir));
            if (!Files.exists(targetPath)) {
                Files.createDirectory(targetPath);
            }
            return FileVisitResult.CONTINUE;
        }

        @Override
        public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {

            Files.copy(file, toPath.resolve(fromPath.relativize(file)), copyOption);
            return FileVisitResult.CONTINUE;
        }
    }

    public static byte[] toHashBytes(String hashS) {
        String val = hashS.trim();
        if (val.length() < 4) {
            return null;
        }
        char c = val.charAt(2);
        boolean divided = !((c >= '0' && c <= '9') || (c >= 'A' && c <= 'F') || (c >= 'a' && c <= 'f')); // NOI18N
        boolean lenOK;
        int s;
        if (divided) {
            lenOK = (val.length() + 1) % 3 == 0;
            s = (val.length() + 1) / 3;
        } else {
            lenOK = (val.length()) % 2 == 0;
            s = (val.length() + 1) / 2;
        }
        if (!lenOK) {
            return null;
        }
        byte[] digest = new byte[s];
        int dI = 0;
        for (int i = 0; i + 1 < val.length(); i += 2) {
            int b;
            try {
                b = Integer.parseInt(val.substring(i, i + 2), 16);
            } catch (NumberFormatException ex) {
                return null;
            }
            if (b < 0) {
                return null;
            }
            digest[dI++] = (byte) b;
            if (divided) {
                i++;
            }
        }
        return digest;
    }

    /**
     * Formats a digest into a String representation. Prints digest bytes in hex, separates bytes by
     * doublecolon.
     * 
     * @param digest digest bytes
     * @return Strign representation.
     */
    public static String fingerPrint(byte[] digest) {
        return fingerPrint(digest, true);
    }

    /**
     * Formats a digest into a String representation. Prints digest bytes in hex, separates bytes by
     * doublecolon.
     * 
     * @param digest digest bytes
     * @param delimiter if true, put ':' between each two hex digits
     * @return Strign representation.
     */
    public static String fingerPrint(byte[] digest, boolean delimiter) {
        if (digest == null) {
            return null;
        }
        StringBuilder sb = new StringBuilder(digest.length * 3);
        for (int i = 0; i < digest.length; i++) {
            if (delimiter && i > 0) {
                sb.append(':');
            }
            sb.append(String.format("%02x", (digest[i] & 0xff)));
        }
        return sb.toString();
    }

    /**
     * Hashes a string contents.
     * 
     * @param s the input text
     * @param delimiters use : delimiters in the fingerprint
     * @return hex fingerprint of the input text
     */
    public static String digestString(String s, boolean delimiters) {
        try {
            MessageDigest dg = MessageDigest.getInstance("SHA-256"); // NOI18N
            dg.update(s.getBytes());
            byte[] digest = dg.digest();
            return SystemUtils.fingerPrint(digest, delimiters);
        } catch (NoSuchAlgorithmException ex) {
            throw new FailedOperationException(ex.getLocalizedMessage(), ex);
        }
    }

    public static String patternOsName(String os) {
        if (os == null) {
            return null;
        }
        String lc = os.toLowerCase(Locale.ENGLISH);
        switch (lc) {
            case OS_MACOS_DARWIN:
            case OS_TOKEN_MACOS:
                return "(:?" + OS_MACOS_DARWIN + "|" + OS_TOKEN_MACOS + ")";
            default:
                return lc;
        }
    }

    public static String patternOsArch(String arch) {
        if (arch == null) {
            return null;
        }
        String lc = arch.toLowerCase(Locale.ENGLISH);
        switch (lc) {
            case ARCH_AMD64:
            case ARCH_X8664:
                return "(:?" + ARCH_AMD64 + "|" + ARCH_X8664 + ")";
            default:
                return lc;
        }
    }

    /**
     * Normalizes architecture string.
     * 
     * @param os OS name
     * @param arch arch name
     * @return normalized arch name, or {@code null}.
     */
    public static String normalizeArchitecture(String os, String arch) {
        if (arch == null) {
            return null;
        }
        switch (arch.toLowerCase(Locale.ENGLISH)) {
            case ARCH_X8664:
                return ARCH_AMD64;
            default:
                return arch.toLowerCase(Locale.ENGLISH);
        }
    }

    /**
     * Normalizes OS name string.
     * 
     * @param os OS name
     * @param arch arch name
     * @return normalized os name, or {@code null}.
     */
    public static String normalizeOSName(String os, String arch) {
        if (os == null) {
            return null;
        }
        switch (os.toLowerCase(Locale.ENGLISH)) {
            case OS_MACOS_DARWIN:
                return OS_TOKEN_MACOS;
            default:
                return os.toLowerCase(Locale.ENGLISH);
        }
    }

    /**
     * Computes file digest. The default algorithm is SHA-256
     * 
     * @param localFile local file path
     * @param digestAlgo the hash algorithm
     * @return digest bytes
     * @throws java.io.IOException in the case of I/O failure, or a digest failure/not found
     */
    public static byte[] computeFileDigest(Path localFile, String digestAlgo) throws IOException {
        try (SeekableByteChannel s = FileChannel.open(localFile, StandardOpenOption.READ)) {
            ByteBuffer bb = ByteBuffer.allocate(4096);
            long size = Files.size(localFile);
            MessageDigest dg = MessageDigest.getInstance("SHA-256"); // NOI18N
            while (size > 0) {
                if (bb.limit() > size) {
                    bb.limit((int) size);
                }
                int r = s.read(bb);
                if (r < 0) {
                    break;
                }
                bb.flip();
                dg.update(bb);
                bb.clear();
                size -= r;
            }
            return dg.digest();
        } catch (NoSuchAlgorithmException ex) {
            throw new IOException(ex);
        }
    }

    /**
     * Determines if the path is a remote URL. If the passed string is not an absolute URL, attempts
     * to interpret as relative path, which checks 'bad characters' and avoids paths that traverse
     * above the root. Disallows absolute file:// URLs, URLs from file-based catalogs must be given
     * as relative.
     * 
     * @param pathOrURL path or URL to check.
     * @return true, if the path is actually an URL.
     */
    public static boolean isRemotePath(String pathOrURL) {
        try {
            URL u = new URL(pathOrURL);
            String proto = u.getProtocol();
            if ("file".equals(proto)) { // NOI18N
                throw new IllegalArgumentException("Absolute file:// URLs are not permitted.");
            } else {
                return true;
            }
        } catch (MalformedURLException ex) {
            // expected
        }
        // will fail with an exception if the relative path contains bad chars or traverses up
        fromCommonRelative(pathOrURL);
        return false;
    }
}
