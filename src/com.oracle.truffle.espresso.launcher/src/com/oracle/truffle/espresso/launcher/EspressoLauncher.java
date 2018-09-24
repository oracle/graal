/*
 * Copyright (c) 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.launcher;

import java.io.File;
import java.io.IOException;
import java.nio.file.NoSuchFileException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.jar.JarFile;
import java.util.jar.Manifest;

import org.graalvm.launcher.AbstractLanguageLauncher;
import org.graalvm.options.OptionCategory;
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Context.Builder;
import org.graalvm.polyglot.PolyglotException;
import org.graalvm.polyglot.Source;
import org.graalvm.polyglot.Value;

public class EspressoLauncher extends AbstractLanguageLauncher {
    public static void main(String[] args) {
        new EspressoLauncher().launch(args);
    }

    private String classPathString = null;
    private final ArrayList<String> mainClassArgs = new ArrayList<>();
    private String mainClassName = null;
    private VersionAction versionAction = VersionAction.None;

    @Override
    protected List<String> preprocessArguments(List<String> arguments, Map<String, String> polyglotOptions) {
        ArrayList<String> unrecognized = new ArrayList<>();
        String jarFileName = null;
        for (int i = 0; i < arguments.size(); i++) {
            String arg = arguments.get(i);
            switch (arg) {
                case "-cp":
                case "--class-path":
                    i += 1;
                    if (i < arguments.size()) {
                        classPathString = arguments.get(i);
                    } else {
                        throw abort("Error: " + arg + " requires class path specification");
                    }
                    break;
                case "-jar":
                    i += 1;
                    if (i < arguments.size()) {
                        jarFileName = arguments.get(i);
                    } else {
                        throw abort("Error: " + arg + " requires jar file specification");
                    }
                    break;
                case "-version":
                case "--version":
                    versionAction = VersionAction.PrintAndExit;
                    break;
                case "--show-version":
                    versionAction = VersionAction.PrintAndContinue;
                    break;
                default:
                    if (!arg.startsWith("-")) {
                        mainClassName = arg;
                    } else {
                        unrecognized.add(arg);
                    }
                    break;
            }
            if (mainClassName != null || jarFileName != null) {
                if (jarFileName != null) {
                    // Overwrite class path. For compatibility with the standard java launcher,
                    // this is done silently.
                    classPathString = jarFileName;

                    mainClassName = getMainClassName(jarFileName);
                }
                i += 1;
                if (i < arguments.size()) {
                    mainClassArgs.addAll(arguments.subList(i, arguments.size()));
                }
                break;
            }
        }
        if (mainClassName == null) {
            throw abort(usage());
        }

        return unrecognized;
    }

    private static String usage() {
        String nl = System.lineSeparator();
        // @formatter:off
        return "Usage: java [-options] <mainclass> [args...]" + nl +
               "           (to execute a class)" + nl +
               "   or  java [-options] -jar jarfile [args...]" + nl +
               "           (to execute a jar file)" + nl +
               "where options include:" + nl +
               "    -cp <class search path of directories and zip/jar files>" + nl +
               "    -classpath <class search path of directories and zip/jar files>" + nl +
               "                  A " + File.pathSeparator + " separated list of directories, JAR archives," + nl +
               "                  and ZIP archives to search for class files." + nl +
               "    -? -help      print this help message";
        // @formatter:on
    }

    private static void print(String string) {
        System.out.println(string);
    }

    /**
     * Gets the values of the attribute {@code name} from the manifest in {@code jarFile}.
     *
     * @return the value of the attribute of null if not found
     * @throws IOException if error reading jar file
     */
    public static String getManifestValue(JarFile jarFile, String name) throws IOException {
        final Manifest manifest = jarFile.getManifest();
        if (manifest == null) {
            return null;
        }
        return manifest.getMainAttributes().getValue(name);
    }

    /**
     * Finds the main class name from the command line either explicitly or via the jar file.
     */
    private String getMainClassName(String jarFileName) {
        try {
            final JarFile jarFile = new JarFile(jarFileName);
            String value = getManifestValue(jarFile, "Main-Class");
            if (value == null) {
                throw abort("Could not find main class in jarfile: " + jarFileName);
            }
            return value;
        } catch (IOException e) {
            throw abort("Could not find main class in jarfile: " + jarFileName + " [cause: " + e + "]");
        }
    }

    @Override
    protected void launch(Builder contextBuilder) {
        //contextBuilder.arguments(getLanguageId(), vmArgs(-D... -XX:...)).in(System.in).out(System.out).err(System.err);
        contextBuilder.arguments(getLanguageId(), mainClassArgs.toArray(new String[0])).in(System.in).out(System.out).err(System.err);

        if (classPathString != null) {
            contextBuilder.option("java.classpath", classPathString);
        }

        int rc = 1;
        try (Context context = contextBuilder.build()) {
            runVersionAction(versionAction, context.getEngine());

            try {
                eval(context);
                rc = 0;
            } catch (PolyglotException e) {
                if (!e.isExit()) {
                    e.printStackTrace();
                } else {
                    rc = e.getExitStatus();
                }
            } catch (NoSuchFileException e) {
                e.printStackTrace();
            }
        } catch (IOException e) {
            rc = 1;
            e.printStackTrace();
        }
        System.exit(rc);
    }

    private void eval(Context context) throws IOException {
        // Source src = Source.newBuilder(getLanguageId(), "", "LauncherHelper").build();
        Source src = Source.newBuilder(getLanguageId(), "", mainClassName).build();
        context.eval(src);
        //Value klass = context.eval(src);
        //klass.getMember("loadAndSomething").execute(1, mainClassName).getMember("main").execute(mainClassArgs);
    }

    @Override
    protected String getLanguageId() {
        return "java";
    }

    @Override
    protected void printHelp(OptionCategory maxCategory) {
        print(usage());
    }

    @Override
    protected void collectArguments(Set<String> options) {
        // This list of arguments is used when we are launched through the Polyglot
        // launcher
        options.add("-cp");
        options.add("--class-path");
        options.add("--version");
        options.add("--show-version");
    }
}
