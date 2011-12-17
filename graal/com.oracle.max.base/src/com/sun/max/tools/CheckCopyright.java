/*
 * Copyright (c) 2011, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.tools;

import java.io.*;
import java.util.*;
import java.util.regex.*;

import com.sun.max.ide.*;
import com.sun.max.program.*;
import com.sun.max.program.option.*;

/**
 * A program to check the existence and correctness of the copyright notice on a given set of Maxine sources.
 * Sources are defined to be those under management by Mercurial and various options are available
 * to limit the set of sources scanned.
 */

public class CheckCopyright {

    static class YearInfo {

        final int firstYear;
        final int lastYear;

        YearInfo(int firstYear, int lastYear) {
            this.firstYear = firstYear;
            this.lastYear = lastYear;
        }

        @Override
        public boolean equals(Object other) {
            final YearInfo yearInfo = (YearInfo) other;
            return yearInfo.firstYear == firstYear && yearInfo.lastYear == lastYear;
        }

        @Override
        public int hashCode() {
            return firstYear ^ lastYear;
        }
    }

    static class Info extends YearInfo {

        final String fileName;

        Info(String fileName, int firstYear, int lastYear) {
            super(firstYear, lastYear);
            this.fileName = fileName;
        }

        @Override
        public String toString() {
            return fileName + " " + firstYear + ", " + lastYear;
        }
    }

    enum CopyrightKind {
        STAR("star"),
        HASH("hash");

        private static Map<String, CopyrightKind> copyrightMap;
        private static final String COPYRIGHT_REGEX = "com.oracle.max.base/.copyright.regex";
        private static String copyrightFiles = "bin/max|.*/makefile|.*/Makefile|.*\\.sh|.*\\.bash|.*\\.mk|.*\\.java|.*\\.c|.*\\.h";
        private static Pattern copyrightFilePattern;
        private final String suffix;
        private String copyright;
        Pattern copyrightPattern;

        CopyrightKind(String suffix) {
            this.suffix = suffix;
        }

        static void addCopyrightFilesPattern(String pattern) {
            copyrightFiles += "|" + pattern;
        }

        void readCopyright()  throws IOException {
            final File file = new File(workSpaceDirectory, COPYRIGHT_REGEX + "." + suffix);
            assert file.exists();
            byte[] b = new byte[(int) file.length()];
            FileInputStream is = new FileInputStream(file);
            is.read(b);
            is.close();
            copyright = new String(b);
            copyrightPattern = Pattern.compile(copyright, Pattern.DOTALL);
        }

        /**
         * Returns a matcher for the modification year from copyright.
         *
         * @param fileContent
         * @return modification year matcher or null if copyright not expected
         */
        static Matcher getCopyrightMatcher(String fileName, String fileContent) {
            if (copyrightMap == null) {
                copyrightFilePattern = Pattern.compile(copyrightFiles);
                copyrightMap = new HashMap<String, CopyrightKind>();
                copyrightMap.put("java", CopyrightKind.STAR);
                copyrightMap.put("c", CopyrightKind.STAR);
                copyrightMap.put("h", CopyrightKind.STAR);
                copyrightMap.put("mk", CopyrightKind.HASH);
                copyrightMap.put("sh", CopyrightKind.HASH);
                copyrightMap.put("bash", CopyrightKind.HASH);
                copyrightMap.put("", CopyrightKind.HASH);
            }
            if (!copyrightFilePattern.matcher(fileName).matches()) {
                return null;
            }
            final String extension = getExtension(fileName);
            CopyrightKind ck = copyrightMap.get(extension);
            assert ck != null : fileName;
            return ck.copyrightPattern.matcher(fileContent);
        }

        private static String getExtension(String fileName) {
            int index = fileName.lastIndexOf(File.separatorChar);
            if (index > 0) {
                fileName = fileName.substring(index + 1);
            }
            index = fileName.lastIndexOf('.');
            if (index > 0) {
                return fileName.substring(index + 1);
            }
            if (fileName.equals("makefile")) {
                return "mk";
            }
            return "";
        }
    }

    private static List<YearInfo> infoList = new ArrayList<YearInfo>();
    private static int currentYear = Calendar.getInstance().get(Calendar.YEAR);
    private static final OptionSet options = new OptionSet(true);
    private static final Option<Boolean> help = options.newBooleanOption("help", false, "Show help message and exit.");
    private static final Option<List<String>> FILES_TO_CHECK = options.newStringListOption("files",
                    null, ',', "list of files to check");
    private static final Option<String> FILE_LIST = options.newStringOption("filelist",
                    null, "file containing list of files to check");
    private static final Option<Boolean> HG_ALL = options.newBooleanOption("all", false, "check all hg managed files requiring a copyright (hg status --all)");
    private static final Option<Boolean> HG_MODIFIED = options.newBooleanOption("modified", false, "check all modified hg managed files requiring a copyright (hg status)");
    private static final Option<Boolean> HG_OUTGOING = options.newBooleanOption("outgoing", false, "check outgoing hg managed files requiring a copyright (hg outgoing)");
    private static final Option<Integer> HG_LOG = options.newIntegerOption("last", 0, "check hg managed files requiring a copyright in last N changesets (hg log -l N)");
    private static final Option<List<String>> PROJECT = options.newStringListOption("projects", null, ',', "filter files to specific projects");
    private static final Option<String> OUTGOING_REPO = options.newStringOption("repo", null, "override outgoing repository");
    private static final Option<Boolean> EXHAUSTIVE = options.newBooleanOption("exhaustive", false, "check all hg managed files");
    private static final Option<Boolean> FIX = options.newBooleanOption("fix", false, "fix copyright errors");
    private static final Option<String> FILE_PATTERN = options.newStringOption("filepattern", null, "append additiional file patterns for copyright checks");
    private static final Option<Boolean> REPORT_ERRORS = options.newBooleanOption("reporterrors", true, "report non-fatal errors");
    private static final Option<Boolean> CONTINUE_ON_ERROR = options.newBooleanOption("continueonerror", false, "continue after normally fatal error");
    private static final Option<String> HG_PATH = options.newStringOption("hgpath", "hg", "path to hg executable");
    private static final String NON_EXISTENT_FILE = "abort: cannot follow nonexistent file:";
    private static String hgPath;
    private static boolean error;
    private static File workSpaceDirectory;


    public static void main(String[] args) {
        Trace.addTo(options);
        // parse the arguments
        options.parseArguments(args).getArguments();
        if (help.getValue()) {
            options.printHelp(System.out, 100);
            return;
        }

        hgPath = HG_PATH.getValue();

        workSpaceDirectory = JavaProject.findWorkspaceDirectory();

        if (FILE_PATTERN.getValue() != null) {
            CopyrightKind.addCopyrightFilesPattern(FILE_PATTERN.getValue());
        }

        try {
            CopyrightKind.STAR.readCopyright();
            CopyrightKind.HASH.readCopyright();
            List<String> filesToCheck = null;
            if (HG_ALL.getValue()) {
                filesToCheck = getAllFiles(true);
            } else if (HG_OUTGOING.getValue()) {
                filesToCheck = getOutgoingFiles();
            } else if (HG_MODIFIED.getValue()) {
                filesToCheck = getAllFiles(false);
            } else if (HG_LOG.getValue() > 0) {
                filesToCheck = getLastNFiles(HG_LOG.getValue());
            } else if (FILE_LIST.getValue() != null) {
                filesToCheck = readFileList(FILE_LIST.getValue());
            } else {
                filesToCheck = FILES_TO_CHECK.getValue();
            }
            if (filesToCheck != null && filesToCheck.size() > 0) {
                processFiles(filesToCheck);
            } else {
                System.out.println("nothing to check");
            }
            System.exit(error ? 1 : 0);
        } catch (Exception ex) {
            throw ProgramError.unexpected("processing failed", ex);
        }
    }

    private static void processFiles(List<String> fileNames) throws Exception {
        final List<String> projects = PROJECT.getValue();
        for (String fileName : fileNames) {
            if (projects == null || isInProjects(fileName, projects)) {
                Trace.line(1, "checking " + fileName);
                try {
                    final List<String> logInfo = hglog(fileName);
                    final Info info = getInfo(fileName, true, logInfo);
                    checkFile(fileName, info);
                } catch (ProgramError e) {
                    System.err.println("COPYRIGHT CHECK WARNING: error while processing " + fileName);
                }
            }
        }
    }

    private static boolean isInProjects(String fileName, List<String> projects) {
        final int ix = fileName.indexOf(File.separatorChar);
        if (ix < 0) {
            return false;
        }
        final String fileProject = fileName.substring(0, ix);
        for (String project : projects) {
            if (fileProject.equals(project)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> readFileList(String fileListName) throws IOException {
        final List<String> result = new ArrayList<String>();
        BufferedReader b = null;
        try {
            b = new BufferedReader(new FileReader(fileListName));
            while (true) {
                final String fileName = b.readLine();
                if (fileName == null) {
                    break;
                }
                if (fileName.length() == 0) {
                    continue;
                }
                result.add(fileName);
            }
        } finally {
            if (b != null) {
                b.close();
            }
        }
        return result;
    }

    private static Info getInfo(String fileName, boolean lastOnly, List<String> logInfo) {
        // process sequence of changesets
        int lastYear = 0;
        int firstYear = 0;
        String summary = null;
        int ix = 0;

        while (ix < logInfo.size()) {
            String s = logInfo.get(ix++);
            assert s.startsWith("changeset");
            s = logInfo.get(ix++);
            // process every entry in a given change set
            if (s.startsWith("tag")) {
                s = logInfo.get(ix++);
            }
            if (s.startsWith("branch")) {
                s = logInfo.get(ix++);
            }
            while (s.startsWith("parent")) {
                s = logInfo.get(ix++);
            }
            assert s.startsWith("user");
            s = logInfo.get(ix++);
            assert s.startsWith("date");
            final int csYear = getYear(s);
            summary = logInfo.get(ix++);
            assert summary.startsWith("summary");
            s = logInfo.get(ix++); // blank
            assert s.length() == 0;
            if (lastYear == 0 && summary.contains("change all copyright notices from Sun to Oracle")) {
                // special case of last change being the copyright change, which didn't
                // count as a change of last modification date!
                continue;
            }
            if (lastYear == 0) {
                lastYear = csYear;
                firstYear = lastYear;
            } else {
                firstYear = csYear;
            }
            // if we only want the last modified year, quit now
            if (lastOnly) {
                break;
            }

        }

        // Special case
        if (summary != null && summary.contains("Initial commit of VM sources")) {
            firstYear = 2007;
        }
        if (HG_MODIFIED.getValue()) {
            // We are only looking at modified and, therefore, uncommitted files.
            // This means that the lastYear value will be the current year once the
            // file is committed, so that is what we want to check against.
            lastYear = currentYear;
        }
        return new Info(fileName, firstYear, lastYear);
    }

    private static int getYear(String dateLine) {
        final String[] parts = dateLine.split(" ");
        assert parts[parts.length - 2].startsWith("20");
        return Integer.parseInt(parts[parts.length - 2]);
    }

    private static void checkFile(String c, Info info) throws IOException {
        String fileName = info.fileName;
        File file = new File(fileName);
        if (!file.exists()) {
            System.err.println("COPYRIGHT CHECK WARNING: file " + file + " doesn't exist");
            return;
        }
        int fileLength = (int) file.length();
        byte[] b = new byte[fileLength];
        FileInputStream is = new FileInputStream(file);
        is.read(b);
        is.close();
        final String fileContent = new String(b);
        Matcher copyrightMatcher = CopyrightKind.getCopyrightMatcher(fileName, fileContent);
        if (copyrightMatcher != null) {
            if (copyrightMatcher.matches()) {
                int yearInCopyright;
                int yearInCopyrightIndex;
                yearInCopyright = Integer.parseInt(copyrightMatcher.group(2));
                yearInCopyrightIndex = copyrightMatcher.start(2);
                if (yearInCopyright != info.lastYear) {
                    System.out.println(fileName + " copyright last modified year " + yearInCopyright + ", hg last modified year " + info.lastYear);
                    if (FIX.getValue()) {
                        // Use currentYear as that is what it will be when it's checked in!
                        System.out.println("updating last modified year of " + fileName + " to " + currentYear);
                        final int lx = yearInCopyrightIndex;
                        final String newContent = fileContent.substring(0, lx) + info.lastYear + fileContent.substring(lx + 4);
                        final FileOutputStream os = new FileOutputStream(file);
                        os.write(newContent.getBytes());
                        os.close();
                    } else {
                        error = true;
                    }
                }
            } else {
                System.out.println("ERROR: file " + fileName + " has no copyright");
                error = true;
            }
        } else if (EXHAUSTIVE.getValue()) {
            System.out.println("ERROR: file " + fileName + " has no copyright");
            error = true;
        }
    }


    private static List<String> hglog(String fileName) throws Exception {
        final String[] cmd = new String[] {hgPath, "log", "-f", fileName};
        return exec(null, cmd, true);
    }

    private static List<String> getLastNFiles(int n) throws Exception {
        final String[] cmd = new String[] {hgPath, "log", "-v", "-l", Integer.toString(n)};
        return getFilesFiles(exec(null, cmd, false));
    }

    private static List<String> getAllFiles(boolean all) throws Exception {
        final String[] cmd;
        if (HG_MODIFIED.getValue()) {
            cmd = new String[] {hgPath,  "status"};
        } else {
            cmd = new String[] {hgPath,  "status",  "--all"};
        }
        List<String> output = exec(null, cmd, true);
        final List<String> result = new ArrayList<String>(output.size());
        for (String s : output) {
            final char ch = s.charAt(0);
            if (!(ch == 'R' || ch == 'I' || ch == '?' ||  ch == '!')) {
                result.add(s.substring(2));
            }
        }
        return result;
    }

    private static List<String> getOutgoingFiles() throws Exception {
        final String[] cmd;
        if (OUTGOING_REPO.getValue() == null) {
            cmd = new String[] {hgPath,  "-v", "outgoing"};
        } else {
            cmd = new String[] {hgPath,  "-v", "outgoing", OUTGOING_REPO.getValue()};
        }

        final List<String> output = exec(null, cmd, false); // no outgoing exits with result 1
        return getFilesFiles(output);
    }

    private static List<String> getFilesFiles(List<String> output) {
        // there may be multiple changesets so merge the "files:"
        final Map<String, String> outSet = new TreeMap<String, String>();
        for (String s : output) {
            if (s.startsWith("files:")) {
                int ix = s.indexOf(' ');
                while (ix < s.length() && s.charAt(ix) == ' ') {
                    ix++;
                }
                final String[] files = s.substring(ix).split(" ");
                for (String file : files) {
                    outSet.put(file, file);
                }
            }
        }
        return new ArrayList<String>(outSet.values());
    }

    private static List<String> exec(File workingDir, String[] command, boolean failOnError) throws IOException, InterruptedException {
        List<String> result = new ArrayList<String>();
        if (Trace.hasLevel(2)) {
            Trace.line(2, "Executing process in directory: " + workingDir);
            for (String c : command) {
                Trace.line(2, "  " + c);
            }
        }
        final Process process = Runtime.getRuntime().exec(command, null, workingDir);
        try {
            result = readOutput(process.getInputStream());
            final int exitValue = process.waitFor();
            if (exitValue != 0) {
                final List<String> errorResult = readOutput(process.getErrorStream());
                if (REPORT_ERRORS.getValue()) {
                    System.err.print("execution of command: ");
                    for (String c : command) {
                        System.err.print(c);
                        System.err.print(' ');
                    }
                    System.err.println("failed with result " + exitValue);
                    for (String e : errorResult) {
                        System.err.println(e);
                    }
                }
                if (failOnError && !(CONTINUE_ON_ERROR.getValue() || cannotFollowNonExistentFile(errorResult))) {
                    throw ProgramError.unexpected("terminating");
                }
            }
        } finally {
            process.destroy();
        }
        return result;
    }

    private static boolean cannotFollowNonExistentFile(List<String> errorResult) {
        return errorResult.size() == 1 && errorResult.get(0).startsWith(NON_EXISTENT_FILE);
    }

    private static List<String> readOutput(InputStream is) throws IOException {
        final List<String> result = new ArrayList<String>();
        BufferedReader bs = null;
        try {
            bs = new BufferedReader(new InputStreamReader(is));
            while (true) {
                final String line = bs.readLine();
                if (line == null) {
                    break;
                }
                result.add(line);
            }
        } finally {
            if (bs != null) {
                bs.close();
            }
        }
        return result;
    }

}
