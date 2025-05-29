/*
 * Copyright (c) 2013, 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.visualizer.upgrader;

import org.netbeans.util.Util;
import org.openide.filesystems.FileUtil;
import org.openide.util.EditableProperties;

import javax.swing.JDialog;
import javax.swing.JOptionPane;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map.Entry;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Does copy of files according to include/exclude patterns.
 *
 * @author Jiri Skrivanek
 */
final class CopyFiles extends Object {

    private File sourceRoot;
    private File targetRoot;
    private EditableProperties currentProperties;
    private Set<String> includePatterns = new HashSet<String>();
    private Set<String> excludePatterns = new HashSet<String>();
    private HashMap<String, String> translatePatterns = new HashMap<String, String>(); // <originalPath, newPath>
    private static final Logger LOGGER = Logger.getLogger(CopyFiles.class.getName());

    private CopyFiles(File source, File target, File patternsFile) {
        this.sourceRoot = source;
        this.targetRoot = target;
        try (InputStream is = new FileInputStream(patternsFile);
             Reader reader = new InputStreamReader(is, "utf-8")) { // NOI18N
            readPatterns(reader);
        } catch (IOException ex) {
            // set these to null to stop further copying (see copyDeep method)
            sourceRoot = null;
            targetRoot = null;
            LOGGER.log(Level.WARNING, "Import settings will not proceed: {0}", ex.getMessage());
            // show error message and continue
            JDialog dialog = Util.createJOptionDialog(new JOptionPane(ex, JOptionPane.ERROR_MESSAGE), "Import settings will not proceed");
            dialog.setVisible(true);
            return;
        }
    }

    public static void copyDeep(File source, File target, File patternsFile) throws IOException {
        CopyFiles copyFiles = new CopyFiles(source, target, patternsFile);
        if (copyFiles.sourceRoot == null || copyFiles.targetRoot == null) {
            return; // IOException was thrown in CopyFiles constructor, probably netbeans.import could not be located
        }
        LOGGER.fine("Copying from: " + copyFiles.sourceRoot + "\nto: " + copyFiles.targetRoot);  //NOI18N
        copyFiles.copyFolder(copyFiles.sourceRoot);
    }

    private void copyFolder(File sourceFolder) throws IOException {
        File[] srcChildren = sourceFolder.listFiles();
        if (srcChildren == null) {
            LOGGER.info(sourceFolder + " is not a directory or is invalid.");  //NOI18N
            return;
        }
        for (File child : srcChildren) {
            if (child.isDirectory()) {
                copyFolder(child);
            } else {
                copyFile(child);
            }
        }
    }

    /**
     * Returns slash separated path relative to given root.
     */
    private static String getRelativePath(File root, File file) {
        String result = file.getAbsolutePath().substring(root.getAbsolutePath().length());
        result = result.replace('\\', '/');  //NOI18N
        if (result.startsWith("/") && !result.startsWith("//")) {  //NOI18N
            result = result.substring(1);
        }
        return result;
    }

    /**
     * Copy source file to target file. It creates necessary sub folders.
     *
     * @param sourceFile source file
     * @param targetFile target file
     * @throws java.io.IOException if copying fails
     */
    private static void copyFile(File sourceFile, File targetFile) throws IOException {
        ensureParent(targetFile);


        try (InputStream ins = new FileInputStream(sourceFile);
             OutputStream out = new FileOutputStream(targetFile)) {
            FileUtil.copy(ins, out);
        }
    }

    private void checkConfinement(File target) throws IOException {
        File canRoot = targetRoot.getCanonicalFile();
        File canTarget = target.getCanonicalFile();
        if (!canTarget.getPath().startsWith(canRoot.getPath())) {
            throw new IOException("Write outside config root");
        }
    }

    /**
     * Copy given file to target root dir if matches include/exclude patterns.
     * If properties pattern is applicable, it copies only matching keys.
     *
     * @param sourceFile source file
     * @throws java.io.IOException if copying fails
     */
    private void copyFile(File sourceFile) throws IOException {
        String relativePath = getRelativePath(sourceRoot, sourceFile);
        currentProperties = null;
        boolean includeFile = false;
        Set<String> includeKeys = new HashSet<String>();
        Set<String> excludeKeys = new HashSet<String>();
        for (String pattern : includePatterns) {
            if (pattern.contains("#")) {  //NOI18N
                includeKeys.addAll(matchingKeys(relativePath, pattern));
            } else {
                if (relativePath.matches(pattern)) {
                    includeFile = true;
                    includeKeys.clear();  // include entire file
                    break;
                }
            }
        }
        if (includeFile || !includeKeys.isEmpty()) {
            // check excludes
            for (String pattern : excludePatterns) {
                if (pattern.contains("#")) {  //NOI18N
                    excludeKeys.addAll(matchingKeys(relativePath, pattern));
                } else {
                    if (relativePath.matches(pattern)) {
                        includeFile = false;
                        includeKeys.clear();  // exclude entire file
                        break;
                    }
                }
            }
        }
        LOGGER.log(Level.FINEST, "{0}, includeFile={1}, includeKeys={2}, excludeKeys={3}", new Object[]{relativePath, includeFile, includeKeys, excludeKeys});  //NOI18N
        if (!includeFile && includeKeys.isEmpty()) {
            // nothing matches
            return;
        }

        for (Entry<String, String> entry : translatePatterns.entrySet()) {
            if (relativePath.startsWith(entry.getKey())) {
                String value = entry.getValue();
                LOGGER.log(Level.INFO, "Translating old relative path: {0}", relativePath);  //NOI18N
                relativePath = value + relativePath.substring(entry.getKey().length());
                LOGGER.log(Level.INFO, "                   to new one: {0}", relativePath);  //NOI18N
            }
        }

        File targetFile = new File(targetRoot, relativePath);
        checkConfinement(targetFile);
        LOGGER.log(Level.FINE, "Path: {0}", relativePath);  //NOI18N
        if (includeKeys.isEmpty() && excludeKeys.isEmpty()) {
            // copy entire file
            copyFile(sourceFile, targetFile);
        } else {
            if (!includeKeys.isEmpty()) {
                currentProperties.keySet().retainAll(includeKeys);
            }
            currentProperties.keySet().removeAll(excludeKeys);
            // copy just selected keys
            LOGGER.log(Level.FINE, "  Only keys: {0}", currentProperties.keySet());
            ensureParent(targetFile);
            try (OutputStream out = new FileOutputStream(targetFile)) {
                currentProperties.store(out);
            }
        }
    }

    /**
     * Returns set of keys matching given pattern.
     *
     * @param relativePath      path relative to sourceRoot
     * @param propertiesPattern pattern like file.properties#keyPattern
     * @return set of matching keys, never null
     * @throws IOException if properties cannot be loaded
     */
    private Set<String> matchingKeys(String relativePath, String propertiesPattern) throws IOException {
        Set<String> matchingKeys = new HashSet<String>();
        String[] patterns = propertiesPattern.split("#", 2);
        String filePattern = patterns[0];
        String keyPattern = patterns[1];
        if (relativePath.matches(filePattern)) {
            if (currentProperties == null) {
                currentProperties = getProperties(relativePath);
            }
            for (String key : currentProperties.keySet()) {
                if (key.matches(keyPattern)) {
                    matchingKeys.add(key);
                }
            }
        }
        return matchingKeys;
    }

    /**
     * Returns properties from relative path.
     *
     * @param relativePath relative path
     * @return properties from relative path.
     * @throws IOException if cannot open stream
     */
    private EditableProperties getProperties(String relativePath) throws IOException {
        EditableProperties properties = new EditableProperties(false);
        InputStream in = null;
        try {
            in = new FileInputStream(new File(sourceRoot, relativePath));
            properties.load(in);
        } finally {
            if (in != null) {
                in.close();
            }
        }
        return properties;
    }

    /**
     * Creates parent of given file, if doesn't exist.
     */
    private static void ensureParent(File file) throws IOException {
        final File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            if (!parent.mkdirs()) {
                throw new IOException("Cannot create folder: " + parent.getAbsolutePath());  //NOI18N
            }
        }
    }

    /**
     * Reads the include/exclude set from a given reader.
     *
     * @param r reader
     */
    private void readPatterns(Reader r) throws IOException {
        BufferedReader buf = new BufferedReader(r);
        for (; ; ) {
            String line = buf.readLine();
            if (line == null) {
                break;
            }
            line = line.trim();
            if (line.length() == 0 || line.startsWith("#")) {  //NOI18N
                continue;
            }
            if (line.startsWith("include ")) {  //NOI18N
                line = line.substring(8);
                if (line.length() > 0) {
                    includePatterns.addAll(parsePattern(line));
                }
            } else if (line.startsWith("exclude ")) {  //NOI18N
                line = line.substring(8);
                if (line.length() > 0) {
                    excludePatterns.addAll(parsePattern(line));
                }
            } else if (line.startsWith("translate ")) {  //NOI18N
                line = line.substring(10);
                if (line.length() > 0) {
                    String[] translations = line.split("\\|");
                    for (String translation : translations) {
                        String originalPath = translation.substring(0, translation.indexOf("=>"));
                        String newPath = translation.substring(translation.lastIndexOf("=>") + 2);
                        if (translatePatterns.containsKey(originalPath)) {
                            LOGGER.log(Level.INFO, "Translation already exists: {0}. Ignoring new translation: {1}",  //NOI18N
                                    new Object[]{originalPath.concat("=>").concat(translatePatterns.get(originalPath)),
                                            originalPath.concat("=>").concat(newPath)});
                        } else {
                            translatePatterns.put(originalPath, newPath);
                        }
                    }
                }
            } else {
                throw new java.io.IOException("Wrong line: " + line);  //NOI18N
            }
        }
    }

    enum ParserState {

        START,
        IN_KEY_PATTERN,
        AFTER_KEY_PATTERN,
        IN_BLOCK
    }

    /**
     * Parses given compound string pattern into set of single patterns.
     *
     * @param pattern compound pattern in form filePattern1#keyPattern1#|filePattern2#keyPattern2#|filePattern3
     * @return set of single patterns containing just one # (e.g. [filePattern1#keyPattern1, filePattern2#keyPattern2, filePattern3])
     */
    private static Set<String> parsePattern(String pattern) {
        Set<String> patterns = new HashSet<String>();
        if (pattern.contains("#")) {  //NOI18N
            StringBuilder partPattern = new StringBuilder();
            ParserState state = ParserState.START;
            int blockLevel = 0;
            for (int i = 0; i < pattern.length(); i++) {
                char c = pattern.charAt(i);
                switch (state) {
                    case START:
                        if (c == '#') {
                            state = ParserState.IN_KEY_PATTERN;
                            partPattern.append(c);
                        } else if (c == '(') {
                            state = ParserState.IN_BLOCK;
                            blockLevel++;
                            partPattern.append(c);
                        } else if (c == '|') {
                            patterns.add(partPattern.toString());
                            partPattern = new StringBuilder();
                        } else {
                            partPattern.append(c);
                        }
                        break;
                    case IN_KEY_PATTERN:
                        if (c == '#') {
                            state = ParserState.AFTER_KEY_PATTERN;
                        } else {
                            partPattern.append(c);
                        }
                        break;
                    case AFTER_KEY_PATTERN:
                        if (c == '|') {
                            state = ParserState.START;
                            patterns.add(partPattern.toString());
                            partPattern = new StringBuilder();
                        } else {
                            assert false : "Wrong OptionsExport pattern " + pattern + ". Only format like filePattern1#keyPattern#|filePattern2 is supported.";  //NOI18N
                        }
                        break;
                    case IN_BLOCK:
                        partPattern.append(c);
                        if (c == ')') {
                            blockLevel--;
                            if (blockLevel == 0) {
                                state = ParserState.START;
                            }
                        }
                        break;
                }
            }
            patterns.add(partPattern.toString());
        } else {
            patterns.add(pattern);
        }
        return patterns;
    }
}
