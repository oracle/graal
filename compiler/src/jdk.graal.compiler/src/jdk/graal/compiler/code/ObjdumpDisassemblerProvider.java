/*
 * Copyright (c) 2019, 2020, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.code;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jdk.graal.compiler.code.CompilationResult.CodeAnnotation;
import jdk.graal.compiler.core.common.NativeImageSupport;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.options.Option;
import jdk.graal.compiler.options.OptionKey;
import jdk.graal.compiler.options.OptionType;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.serviceprovider.GraalServices;
import jdk.graal.compiler.serviceprovider.ServiceProvider;
import jdk.graal.compiler.util.CollectionsUtil;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.vm.ci.code.CodeCacheProvider;
import jdk.vm.ci.code.CodeUtil;
import jdk.vm.ci.code.CodeUtil.DefaultRefMapFormatter;
import jdk.vm.ci.code.CodeUtil.RefMapFormatter;
import jdk.vm.ci.code.InstalledCode;
import jdk.vm.ci.code.Register;
import jdk.vm.ci.code.RegisterConfig;
import jdk.vm.ci.code.TargetDescription;
import jdk.vm.ci.code.site.Call;
import jdk.vm.ci.code.site.DataPatch;
import jdk.vm.ci.code.site.Infopoint;

/**
 * A provider that uses the {@code GNU objdump} utility to disassemble code.
 */
@ServiceProvider(DisassemblerProvider.class)
public class ObjdumpDisassemblerProvider implements DisassemblerProvider {

    static class Options {
        // @formatter:off
        @Option(help = "Comma separated list of candidate GNU objdump executables. If not specified, " +
                "disassembling via GNU objdump is disabled. Otherwise, the first existing executable in the list is used.",
                type = OptionType.Debug)
        static final OptionKey<String> ObjdumpExecutables = new OptionKey<>(null);
        // @formatter:on
    }

    // cached validity of candidate objdump executables.
    private static final Map<String, Boolean> objdumpCache = new EconomicHashMap<>();

    private static Process createProcess(String[] cmd) {
        ProcessBuilder pb = new ProcessBuilder(cmd);
        try {
            return pb.start();
        } catch (IOException e) {
            TTY.printf("WARNING: Error executing '%s' (%s)%n", String.join(" ", cmd), e);
        }
        return null;
    }

    @Override
    public boolean isAvailable(OptionValues options) {
        return getObjdump(options) != null;
    }

    @Override
    public String disassembleCompiledCode(OptionValues options, CodeCacheProvider codeCache, CompilationResult compResult) {
        if (NativeImageSupport.inRuntimeCode() && !ENABLE_OBJDUMP) {
            throw new GraalError("Objdump not available");
        }
        String objdump = getObjdump(options);
        if (objdump == null) {
            return null;
        }
        File tmp = null;
        try {
            tmp = File.createTempFile("compiledBinary", ".bin");
            try (FileOutputStream fos = new FileOutputStream(tmp)) {
                fos.write(compResult.getTargetCode());
            }

            String[] cmdline;
            String arch = GraalServices.getSavedProperty("os.arch");
            if (arch.equals("amd64") || arch.equals("x86_64")) {
                cmdline = new String[]{objdump, "-D", "-b", "binary", "-M", "x86-64", "-m", "i386", tmp.getAbsolutePath()};
            } else if (arch.equals("aarch64")) {
                cmdline = new String[]{objdump, "-D", "-b", "binary", "-m", "aarch64", tmp.getAbsolutePath()};
            } else {
                return null;
            }

            Pattern p = Pattern.compile(" *(([0-9a-fA-F]+):\t.*)");

            TargetDescription target = codeCache.getTarget();
            RegisterConfig regConfig = codeCache.getRegisterConfig();
            Register fp = regConfig.getFrameRegister();
            RefMapFormatter slotFormatter = new DefaultRefMapFormatter(target.wordSize, fp, 0);

            Map<Integer, String> annotations = new EconomicHashMap<>();
            for (DataPatch site : compResult.getDataPatches()) {
                putAnnotation(annotations, site.pcOffset, "{" + site.reference.toString() + "}");
            }
            for (CompilationResult.CodeMark mark : compResult.getMarks()) {
                putAnnotation(annotations, mark.pcOffset, mark.id.getName());
            }
            for (CodeAnnotation a : compResult.getCodeAnnotations()) {
                putAnnotation(annotations, a.getPosition(), a.toString());
            }
            for (Infopoint infopoint : compResult.getInfopoints()) {
                if (infopoint instanceof Call call) {
                    if (call.debugInfo != null) {
                        putAnnotation(annotations, call.pcOffset + call.size, CodeUtil.append(new StringBuilder(100), call.debugInfo, slotFormatter).toString());
                    }
                    putAnnotation(annotations, call.pcOffset, "{" + codeCache.getTargetName(call) + "}");
                } else {
                    if (infopoint.debugInfo != null) {
                        putAnnotation(annotations, infopoint.pcOffset, CodeUtil.append(new StringBuilder(100), infopoint.debugInfo, slotFormatter).toString());
                    }
                    putAnnotation(annotations, infopoint.pcOffset, "{infopoint: " + infopoint.reason + "}");
                }
            }
            Process proc = createProcess(cmdline);
            if (proc == null) {
                return null;
            }
            InputStream is = proc.getInputStream();
            StringBuilder sb = new StringBuilder();

            InputStreamReader isr = new InputStreamReader(is);
            try (BufferedReader br = new BufferedReader(isr)) {
                String line;
                while ((line = br.readLine()) != null) {
                    Matcher m = p.matcher(line);
                    if (m.find()) {
                        int address = Integer.parseInt(m.group(2), 16);
                        String annotation = annotations.get(address);
                        if (annotation != null) {
                            annotation = annotation.replace("\n", "\n; ");
                            sb.append("; ").append(annotation).append('\n');
                        }
                        line = m.replaceAll("0x$1");
                    }
                    sb.append(line).append("\n");
                }
            }
            try (BufferedReader ebr = new BufferedReader(new InputStreamReader(proc.getErrorStream()))) {
                String errLine = ebr.readLine();
                if (errLine != null) {
                    System.err.println("Error output from executing: " + CollectionsUtil.mapAndJoin(cmdline, e -> quoteShellArg(String.valueOf(e)), " "));
                    do {
                        System.err.println(errLine);
                    } while ((errLine = ebr.readLine()) != null);
                }
            }
            return sb.toString();
        } catch (IOException e) {
            e.printStackTrace(TTY.out);
            return null;
        } finally {
            if (tmp != null) {
                tmp.delete();
            }
        }
    }

    /**
     * Pattern for a single shell command argument that does not need to be quoted.
     */
    private static final Pattern SAFE_SHELL_ARG = Pattern.compile("[A-Za-z0-9@%_\\-+=:,./]+");

    /**
     * Reliably quote a string as a single shell command argument.
     */
    public static String quoteShellArg(String arg) {
        if (arg.isEmpty()) {
            return "\"\"";
        }
        Matcher m = SAFE_SHELL_ARG.matcher(arg);
        if (m.matches()) {
            return arg;
        }
        // See http://stackoverflow.com/a/1250279
        return "'" + arg.replace("'", "'\"'\"'") + "'";
    }

    private static final String ENABLE_OBJDUMP_PROP = "debug.jdk.graal.enableObjdump";

    /**
     * Support for objdump is excluded by default from native images (including libgraal) to reduce
     * the image size. It also reduces security concerns related to running subprocesses.
     *
     * To objdump during development, set the {@value #ENABLE_OBJDUMP_PROP} system property to true
     * when building native images.
     */
    private static final boolean ENABLE_OBJDUMP = Boolean.parseBoolean(GraalServices.getSavedProperty(ENABLE_OBJDUMP_PROP));

    private static boolean objdumpUnsupportedWarned;

    /**
     * Searches for a valid GNU objdump executable.
     */
    private static String getObjdump(OptionValues options) {
        // for security, user must provide the possible objdump locations.
        String candidates = Options.ObjdumpExecutables.getValue(options);
        if (candidates != null && !candidates.isEmpty()) {
            if (NativeImageSupport.inRuntimeCode() && !ENABLE_OBJDUMP) {
                if (!objdumpUnsupportedWarned) {
                    // Ignore races or multiple isolates - an extra warning is ok
                    objdumpUnsupportedWarned = true;
                    TTY.printf("WARNING: Objdump not supported as the %s system property was false when building.%n",
                                    ENABLE_OBJDUMP_PROP);
                }
                return null;
            }

            for (String candidate : candidates.split(",")) {
                synchronized (objdumpCache) {
                    // first checking to see if a cached verdict for this candidate exists.
                    Boolean cachedQuery = objdumpCache.get(candidate);
                    if (cachedQuery != null) {
                        if (cachedQuery) {
                            return candidate;
                        } else {
                            // this candidate was previously determined to not be acceptable.
                            continue;
                        }
                    }
                    String[] cmd = {candidate, "--version"};
                    try {
                        Process proc = createProcess(cmd);
                        if (proc == null) {
                            // bad candidate.
                            objdumpCache.put(candidate, Boolean.FALSE);
                            return null;
                        }
                        InputStream is = proc.getInputStream();
                        int exitValue = proc.waitFor();
                        if (exitValue == 0) {
                            byte[] buf = new byte[is.available()];
                            int pos = 0;
                            while (pos < buf.length) {
                                int read = is.read(buf, pos, buf.length - pos);
                                pos += read;
                            }
                            String output = new String(buf);
                            if (output.contains("GNU objdump")) {
                                // this candidate meets the criteria.
                                objdumpCache.put(candidate, Boolean.TRUE);
                                return candidate;
                            }
                        }
                    } catch (IOException | InterruptedException e) {
                        TTY.printf("WARNING: Error reading input from '%s' (%s)%n", String.join(" ", cmd), e);
                    }
                    // bad candidate.
                    objdumpCache.put(candidate, Boolean.FALSE);
                }
            }
        }
        return null;
    }

    private static void putAnnotation(Map<Integer, String> annotations, int idx, String txt) {
        String newAnnotation = annotations.getOrDefault(idx, "") + "\n" + txt;
        annotations.put(idx, newAnnotation);
    }

    @Override
    public String disassembleInstalledCode(CodeCacheProvider codeCache, CompilationResult compResult, InstalledCode code) {
        return "<unavailable>";
    }

    @Override
    public String getName() {
        return "objdump";
    }
}
