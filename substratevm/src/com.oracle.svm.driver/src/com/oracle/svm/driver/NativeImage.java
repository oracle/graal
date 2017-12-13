/*
 * Copyright (c) 2017, 2017, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.svm.driver;

import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.jar.Attributes;
import java.util.jar.Manifest;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.graalvm.compiler.api.replacements.Fold;
import org.graalvm.nativeimage.ImageSingletons;

import com.oracle.svm.truffle.nfi.TruffleNFIFeature;

public class NativeImage implements ImageBuilderConfig {

    final LinkedHashSet<String> imageBuilderArgs = new LinkedHashSet<>();
    final LinkedHashSet<Path> imageBuilderClasspath = new LinkedHashSet<>();
    final LinkedHashSet<Path> imageBuilderBootClasspath = new LinkedHashSet<>();
    final LinkedHashSet<String> imageBuilderFeatures = new LinkedHashSet<>();
    final LinkedHashSet<String> imageBuilderSubstitutions = new LinkedHashSet<>();
    final LinkedHashSet<String> imageBuilderResourceBundles = new LinkedHashSet<>();
    final LinkedHashSet<String> imageBuilderJavaArgs = new LinkedHashSet<>();
    final LinkedHashSet<Path> imageClasspath = new LinkedHashSet<>();
    final LinkedHashSet<String> customJavaArgs = new LinkedHashSet<>();
    final LinkedHashSet<String> customImageBuilderArgs = new LinkedHashSet<>();
    final LinkedHashSet<Path> customImageClasspath = new LinkedHashSet<>();
    final LinkedHashSet<TruffleOptionHandler> usedTruffleLanguages = new LinkedHashSet<>();
    final Path executablePath = Paths.get((String) Compiler.command(new Object[]{"com.oracle.svm.core.posix.GetExecutableName"}));
    final List<NativeImageOptionHandler> optionHandlers = new ArrayList<>();

    final String classArgPrefix = imageBuildState().hostedOptionPrefix + "Class=";
    final String nameArgPrefix = imageBuildState().hostedOptionPrefix + "Name=";

    final Path workDir;
    final Path binDir;

    boolean verbose = Boolean.valueOf(System.getenv("VERBOSE_GRAALVM_LAUNCHERS"));

    public NativeImage() {
        workDir = Paths.get(System.getProperty("user.dir"));
        assert executablePath != null;
        binDir = executablePath.getParent();

        // Default javaArgs needed for image building
        addImageBuilderJavaArgs("-Duser.country=US", "-Duser.language=en");
        addImageBuilderJavaArgs("-Dgraal.EagerSnippets=true");

        addImageBuilderJavaArgs("-Dsubstratevm.version=" + imageBuildState().svmVersion);
        if (imageBuildState().graalvmVersion != null) {
            addImageBuilderJavaArgs("-Dgraalvm.version=" + imageBuildState().graalvmVersion);
            addImageBuilderJavaArgs("-Dorg.graalvm.version=" + imageBuildState().graalvmVersion);
        }

        addImageBuilderJavaArgs("-Xms1G", "-Xss10m");

        imageBuilderArgs.add(imageBuildState().hostedOptionPrefix + "Path=.");

        optionHandlers.add(new OptionHandlerJavascript());
        optionHandlers.add(new OptionHandlerRuby());
        optionHandlers.add(new OptionHandlerLLVM());
        /* the default handler needs to be last */
        optionHandlers.add(new DefaultOptionHandler());
    }

    public static void main(String[] args) {
        NativeImage nativeImage = new NativeImage();

        try {
            if (args.length == 0) {
                nativeImage.showMessage(imageBuildState().usageText);
                System.exit(0);
            }

            nativeImage.init();
            nativeImage.buildImage(args);
        } catch (NativeImageError e) {
            // Checkstyle: stop
            nativeImage.show(System.err, "Error: " + e.getMessage());
            Throwable cause = e.getCause();
            while (cause != null) {
                nativeImage.show(System.err, "Caused by: " + cause);
                cause = cause.getCause();
            }
            // Checkstyle: resume
            System.exit(1);
        }
    }

    @Fold
    static NativeImageSupport imageBuildState() {
        return ImageSingletons.lookup(NativeImageSupport.class);
    }

    private Path canonicalize(Path path) {
        Path absolutePath = path.isAbsolute() ? path : workDir.resolve(path);
        try {
            Path realPath = absolutePath.toRealPath();
            if (!Files.isReadable(realPath)) {
                showError("Classpath entry " + path.toString() + " is not readable");
            }
            return realPath;
        } catch (IOException e) {
            throw showError("Invalid classpath entry " + path.toString());
        }
    }

    private Path[] canonicalize(Path... classpaths) {
        Path[] result = new Path[classpaths.length];
        for (int i = 0; i < result.length; i++) {
            result[i] = canonicalize(classpaths[i]);
        }
        return result;
    }

    @Override
    public boolean isRelease() {
        return !binDir.endsWith("svmbuild");
    }

    @Override
    public Path getRootDir() {
        if (isRelease()) {
            /* The GraalVM root dir where the `release`-file is */
            return binDir.getParent().getParent();
        } else {
            /* The root dir where all the git repos are) */
            return binDir.getParent().getParent().getParent();
        }
    }

    @Override
    public Path getJavaHome() {
        if (isRelease()) {
            return binDir.getParent();
        }

        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome == null) {
            throw showError("Environment variable JAVA_HOME is not set");
        }
        Path javaHomePath = Paths.get(javaHome);
        if (!Files.isExecutable(javaHomePath.resolve(Paths.get("bin", "java")))) {
            throw showError("Environment variable JAVA_HOME is invalid");
        }
        return javaHomePath;
    }

    @Override
    public Path[] getTruffleLanguageJars(TruffleOptionHandler handler) {
        Path languageDir;
        if (isRelease()) {
            languageDir = getRootDir().resolve(Paths.get("jre", "languages", handler.getTruffleLanguageDirectory()));
        } else {
            throw showError("TODO - In dev mode get truffle-language jar file from mx _classpath");
        }
        try {
            Path[] paths = Files.list(languageDir).filter(p -> p.toString().endsWith(".jar")).collect(Collectors.toList()).toArray(new Path[0]);
            if (paths.length == 0) {
                throw new NativeImageError("Could not find any jar files for language " + handler.getTruffleLanguageName());
            }
            return paths;
        } catch (NoSuchElementException | IOException e) {
            throw new NativeImageError("Error while looking for jar files for language " + handler.getTruffleLanguageName(), e);
        }
    }

    @Override
    public void addImageBuilderClasspath(Path... classpaths) {
        imageBuilderClasspath.addAll(Arrays.asList(canonicalize(classpaths)));
    }

    @Override
    public void addImageBuilderBootClasspath(Path... classpaths) {
        imageBuilderBootClasspath.addAll(Arrays.asList(canonicalize(classpaths)));
    }

    @Override
    public void addImageBuilderJavaArgs(String... javaArgs) {
        imageBuilderJavaArgs.addAll(Arrays.asList(javaArgs));
    }

    @Override
    public void addImageBuilderArgs(String... args) {
        imageBuilderArgs.addAll(Arrays.asList(args));
    }

    @Override
    public void addImageBuilderFeatures(String... names) {
        imageBuilderFeatures.addAll(Arrays.asList(names));
    }

    @Override
    public void addImageBuilderSubstitutions(String... substitutions) {
        imageBuilderSubstitutions.addAll(Arrays.asList(substitutions));
    }

    @Override
    public void addImageBuilderResourceBundles(String... resourceBundles) {
        imageBuilderResourceBundles.addAll(Arrays.asList(resourceBundles));
    }

    @Override
    public void addImageClasspath(Path... classpaths) {
        imageClasspath.addAll(Arrays.asList(canonicalize(classpaths)));
    }

    @Override
    public void addCustomImageClasspath(Path... classpaths) {
        customImageClasspath.addAll(Arrays.asList(canonicalize(classpaths)));
    }

    @Override
    public void addCustomJavaArgs(String... javaArgs) {
        customJavaArgs.addAll(Arrays.asList(javaArgs));
    }

    @Override
    public void addCustomImageBuilderArgs(String... args) {
        customImageBuilderArgs.addAll(Arrays.asList(args));
    }

    @Override
    public void addTruffleLanguage(TruffleOptionHandler handler) {
        usedTruffleLanguages.add(handler);
    }

    @Override
    public void setVerbose(boolean val) {
        verbose = val;
    }

    @Override
    public void showVerboseMessage(String message) {
        // Checkstyle: stop
        if (verbose) {
            show(System.out, message);
        }
        // Checkstyle: resume
    }

    @Override
    public void showMessage(String message) {
        // Checkstyle: stop
        show(System.out, message);
        // Checkstyle: resume
    }

    @Override
    public void showWarning(String message) {
        // Checkstyle: stop
        show(System.err, "Warning: " + message);
        // Checkstyle: resume
    }

    @SuppressWarnings("serial")
    static class NativeImageError extends Error {
        NativeImageError(String message) {
            super(message);
        }

        NativeImageError(String message, Throwable cause) {
            super(message, cause);
        }
    }

    @Override
    public Error showError(String message) {
        throw new NativeImageError(message);
    }

    private void show(PrintStream outStream, String message) {
        String result = message;
        result = result.replaceAll("\\$\\{TOOL_NAME\\}", executablePath.getFileName().toString());
        StringBuilder sb = new StringBuilder();
        for (NativeImageOptionHandler handler : optionHandlers) {
            if (handler instanceof DefaultOptionHandler) {
                continue;
            }
            if (handler instanceof TruffleOptionHandler) {
                TruffleOptionHandler truffleHandler = (TruffleOptionHandler) handler;
                sb.append("    --");
                sb.append(truffleHandler.getTruffleLanguageOptionName());
                sb.append("\n                  build ");
                sb.append(truffleHandler.getTruffleLanguageName());
                sb.append(" image\n");
            }
        }
        result = result.replaceAll("\\$\\{TRUFFLE_HANDLER_OPTIONS\\}", sb.toString());
        outStream.println(result);
    }

    @Override
    public void forEachJarInDirectory(Path dir, Consumer<? super Path> action) {
        try {
            Stream<Path> libs = Files.list(dir);
            libs.filter(f -> f.getFileName().toString().toLowerCase().endsWith(".jar")).forEach(action);
        } catch (IOException e) {
            showError("Unable to use jar-files from directory " + dir);
        }
    }

    private void init() {
        if (isRelease()) {
            Path svmDir = getRootDir().resolve("jre/lib/svm");
            addImageBuilderArgs(imageBuildState().hostedOptionPrefix + "InspectServerContentPath=" + svmDir.resolve("inspect"));
            Path clibariesDir = svmDir.resolve("clibraries").resolve(imageBuildState().platform + "-amd64");
            addImageBuilderArgs(imageBuildState().hostedOptionPrefix + "CLibraryPath=" + clibariesDir);
            addImageBuilderClasspath(
                            svmDir.resolve("svm.jar"),
                            svmDir.resolve("objectfile.jar"),
                            svmDir.resolve("pointsto.jar"));
            addImageClasspath(svmDir.resolve("library-support.jar"));
            if (Files.exists(svmDir.resolve("svm-enterprise.jar"))) {
                addImageBuilderClasspath(svmDir.resolve("svm-enterprise.jar"));
            }
            if (Files.exists(svmDir.resolve("library-support-enterprise.jar"))) {
                addImageClasspath(svmDir.resolve("library-support-enterprise.jar"));
            }

            Path graalDir = getRootDir().resolve("jre/lib/jvmci");
            forEachJarInDirectory(graalDir, this::addImageBuilderClasspath);

            if (!imageBuilderClasspath.stream().anyMatch(p -> p.getFileName().toString().equals("jvmci-api.jar"))) {
                Path jvmciDir = getJavaHome().resolve("lib/jvmci");
                if (!jvmciDir.resolve("jvmci-api.jar").toFile().exists()) {
                    jvmciDir = getJavaHome().resolve("jre/lib/jvmci");
                }
                forEachJarInDirectory(jvmciDir, this::addImageBuilderClasspath);
            }

            Path bootDir = getRootDir().resolve("jre/lib/boot");
            forEachJarInDirectory(bootDir, this::addImageBuilderBootClasspath);
        }
    }

    private String pathToString(Path p) {
        return p.toString();
    }

    private void buildImage(String[] args) {
        List<String> leftoverArgs = processArgs(args);

        if (usedTruffleLanguages.isEmpty()) {
            /* For non-truffle language builds ... */
        } else if (usedTruffleLanguages.size() == 1) {
            TruffleOptionHandler singleLanguage = usedTruffleLanguages.iterator().next();
            String launcherClass = singleLanguage.getLauncherClass();
            if (launcherClass != null) {
                addImageBuilderArgs(classArgPrefix + launcherClass);
            }
            singleLanguage.applyTruffleLanguageShellOptions(this);
            TruffleOptionHandler.applyTruffleShellOptions(this);
        } else if (usedTruffleLanguages.size() > 1) {
            TruffleOptionHandler.applyTruffleLanguagePolyglotShellOptions(this, usedTruffleLanguages);
            TruffleOptionHandler.applyTruffleShellOptions(this);
        }

        /* If no classpath was specified put workdir on classpath */
        if (customImageClasspath.isEmpty()) {
            addCustomImageClasspath(Paths.get("."));
        }
        addImageClasspath(customImageClasspath.toArray(new Path[0]));

        addImageBuilderJavaArgs("-Dcom.oracle.graalvm.isaot=true");

        ProcessBuilder pb = new ProcessBuilder();
        List<String> command = pb.command();
        command.addAll(Arrays.asList(pathToString(getJavaHome().resolve("bin/java")), "-server"));
        command.addAll(Arrays.asList("-XX:+UnlockExperimentalVMOptions", "-XX:+EnableJVMCI", "-XX:-UseJVMCIClassLoader", "-XX:+UseJVMCICompiler", "-Dgraal.CompileGraalWithC1Only=false"));
        // Ensure Truffle can load the SubstrateTruffleRuntime from the app class path
        command.add("-Dtruffle.TrustAllTruffleRuntimeProviders=true");
        command.addAll(Arrays.asList("-d64", "-noverify"));

        if (!imageBuilderBootClasspath.isEmpty()) {
            command.add(imageBuilderBootClasspath.stream().map(this::pathToString).collect(Collectors.joining(":", "-Xbootclasspath/a:", "")));
        }

        command.addAll(Arrays.asList("-cp", imageBuilderClasspath.stream().map(this::pathToString).collect(Collectors.joining(":"))));

        addImageBuilderJavaArgs(customJavaArgs.toArray(new String[0]));
        command.addAll(imageBuilderJavaArgs);

        command.add("com.oracle.svm.hosted.NativeImageGeneratorRunner");

        LinkedHashSet<Path> imagecp = new LinkedHashSet<>(imageBuilderClasspath);
        imagecp.addAll(imageClasspath);
        command.addAll(Arrays.asList("-imagecp", imagecp.stream().map(this::pathToString).collect(Collectors.joining(":"))));

        addImageBuilderArgs(customImageBuilderArgs.toArray(new String[0]));
        command.addAll(imageBuilderArgs);

        if (!imageBuilderFeatures.isEmpty()) {
            command.add(imageBuilderFeatures.stream().collect(Collectors.joining(",", imageBuildState().hostedOptionPrefix + "Features=", "")));
        }

        if (!imageBuilderSubstitutions.isEmpty()) {
            command.add(imageBuilderSubstitutions.stream().collect(Collectors.joining(",", imageBuildState().hostedOptionPrefix + "SubstitutionResources=", "")));
        }

        if (!imageBuilderResourceBundles.isEmpty()) {
            command.add(imageBuilderResourceBundles.stream().collect(Collectors.joining(",", imageBuildState().hostedOptionPrefix + "IncludeResourceBundles=", "")));
        }

        ArrayDeque<String> imageBuilderArgsDeque = new ArrayDeque<>(imageBuilderArgs);
        /* Determine if this should build an executable */
        boolean buildExecutable = true;
        for (String imageBuilderArg : (Iterable<String>) imageBuilderArgsDeque::descendingIterator) {
            // @formatter:off
            if ((imageBuilderArg.startsWith(imageBuildState().hostedOptionPrefix + "Kind=") && imageBuilderArg.endsWith("SHARED_LIBRARY"))
                    || imageBuilderArg.equals(imageBuildState().hostedOptionPrefix + "+PrintFlags")
                    || imageBuilderArg.equals(imageBuildState().runtimeOptionPrefix + "+PrintFlags")) {
                // @formatter:on
                buildExecutable = false;
                break;
            }
        }

        if (buildExecutable) {
            String mainClass = null;
            for (String imageBuilderArg : (Iterable<String>) imageBuilderArgsDeque::descendingIterator) {
                if (imageBuilderArg.startsWith(classArgPrefix)) {
                    mainClass = imageBuilderArg.substring(classArgPrefix.length());
                    break;
                }
            }

            List<String> extraImageArgs = new ArrayList<>();
            ListIterator<String> leftoverArgsItr = leftoverArgs.listIterator();
            while (leftoverArgsItr.hasNext()) {
                String leftoverArg = leftoverArgsItr.next();
                if (!leftoverArg.startsWith("-")) {
                    leftoverArgsItr.remove();
                    extraImageArgs.add(leftoverArg);
                }
            }

            /* Main-class from customImageBuilderArgs counts as explicitMainClass */
            boolean explicitMainClass = customImageBuilderArgs.stream().anyMatch(arg -> arg.startsWith(classArgPrefix));

            if (extraImageArgs.isEmpty()) {
                if (mainClass == null) {
                    showError("Please specify class containing the main entry point method. (see -help)");
                }
            } else {
                /* extraImageArgs main-class overrules previous main-class specification */
                explicitMainClass = true;
                mainClass = extraImageArgs.remove(0);
                command.add(classArgPrefix + mainClass);
            }

            if (extraImageArgs.isEmpty()) {
                /* No explicit image name, define image name by other means */
                if (!customImageBuilderArgs.stream().anyMatch(arg -> arg.startsWith(nameArgPrefix))) {
                    /* Also no explicit image name given as customImageBuilderArgs */
                    if (explicitMainClass) {
                        /* Use main-class lower case as image name */
                        command.add(nameArgPrefix + mainClass.toLowerCase());
                    } else if (!imageBuilderArgs.stream().anyMatch(arg -> arg.startsWith(nameArgPrefix))) {
                        /* Although very unlikely, report missing image-name if needed. */
                        showError("Missing image-name. Use " + nameArgPrefix + "<imagename> to provide one.");
                    }
                }
            } else {
                /* extraImageArgs executable name overrules previous specification */
                command.add(nameArgPrefix + extraImageArgs.remove(0));
            }
        }

        for (String leftoverArg : leftoverArgs) {
            command.add(leftoverArg);
        }

        if (verbose) {
            // Checkstyle: stop
            System.err.println("Executing [");
            System.err.println(command.stream().collect(Collectors.joining(" \\\n")));
            System.err.println("]");
            // Checkstyle: resume
        }

        try {
            Process p = pb.inheritIO().start();
            int exitStatus = p.waitFor();
            if (exitStatus != 0) {
                showError("Image building with exit status " + exitStatus);
            }
        } catch (IOException | InterruptedException e) {
            showError(e.getMessage());
        }
    }

    private List<String> processArgs(String[] args) {
        List<String> leftoverArgs = new ArrayList<>();
        Queue<String> arguments = new ArrayDeque<>(Arrays.asList(args));
        while (!arguments.isEmpty()) {
            boolean consumed = false;
            for (NativeImageOptionHandler handler : optionHandlers) {
                int numArgs = arguments.size();
                if (handler.consume(arguments, this)) {
                    assert arguments.size() < numArgs : "OptionHandler pretends to consume argument(s) but isn't: " + handler.getInfo();
                    consumed = true;
                    break;
                }
            }
            if (!consumed) {
                leftoverArgs.add(arguments.poll());
            }
        }
        return leftoverArgs;
    }

    class DefaultOptionHandler implements NativeImageOptionHandler {
        @Override
        public String getInfo() {
            return "Common image-building options";
        }

        @Override
        public boolean consume(Queue<String> args, ImageBuilderConfig config) {
            String headArg = args.peek();
            switch (headArg) {
                case "-?":
                case "-help":
                    args.poll();
                    config.showMessage(imageBuildState().helpText);
                    System.exit(0);
                    return true;
                case "-X":
                    args.poll();
                    config.showMessage(imageBuildState().helpTextX);
                    System.exit(0);
                    return true;
                case "-cp":
                case "-classpath":
                    args.poll();
                    String cpArgs = args.poll();
                    if (cpArgs == null) {
                        config.showError("-cp requires class path specification");
                    }
                    for (String cp : cpArgs.split(":")) {
                        config.addCustomImageClasspath(Paths.get(cp));
                    }
                    return true;
                case "-jar":
                    args.poll();
                    String jarFilePathStr = args.poll();
                    if (jarFilePathStr == null || !jarFilePathStr.endsWith(".jar")) {
                        config.showError("-jar requires jar file specification");
                    }
                    Path jarFilePath = Paths.get(jarFilePathStr);
                    if (!handleJarFileArg(jarFilePath.toFile(), config)) {
                        config.showError("no main manifest attribute, in " + jarFilePath);
                    }
                    return true;
                case "-verbose":
                    args.poll();
                    config.setVerbose(true);
                    return true;
                case "--debug-attach":
                    args.poll();
                    config.addImageBuilderJavaArgs("-Xdebug", "-Xrunjdwp:transport=dt_socket,server=y,address=8000,suspend=y");
                    return true;
                case "-nfi":
                    args.poll();
                    config.addImageBuilderFeatures(TruffleNFIFeature.class.getName());
                    Path truffleDir = getRootDir().resolve("jre/lib/truffle");
                    config.addImageBuilderClasspath(truffleDir.resolve("truffle-nfi.jar"));
                    return true;
            }
            if (headArg.startsWith(imageBuildState().hostedOptionPrefix) || headArg.startsWith(imageBuildState().runtimeOptionPrefix)) {
                args.poll();
                config.addCustomImageBuilderArgs(headArg);
                return true;
            }
            String javaArgsPrefix = "-D";
            if (headArg.startsWith(javaArgsPrefix)) {
                args.poll();
                config.addCustomJavaArgs(headArg);
                return true;
            }
            if (headArg.startsWith("-J")) {
                args.poll();
                if (headArg.equals("-J")) {
                    config.showError("The -J option should not be followed by a space");
                } else {
                    config.addCustomJavaArgs(headArg.substring(2));
                }
                return true;
            }
            String debugOption = "-g";
            if (headArg.equals(debugOption)) {
                args.poll();
                config.addImageBuilderArgs(imageBuildState().hostedOptionPrefix + "Debug=2");
                return true;
            }
            String optimizeOption = "-O";
            if (headArg.startsWith(optimizeOption)) {
                args.poll();
                if (headArg.equals(optimizeOption)) {
                    config.showError("The " + optimizeOption + " option should not be followed by a space");
                } else {
                    config.addImageBuilderArgs(imageBuildState().hostedOptionPrefix + "Optimize=" + headArg.substring(2));
                }
                return true;
            }
            String enableRuntimeAssertions = "-ea";
            if (headArg.equals(enableRuntimeAssertions)) {
                args.poll();
                config.addImageBuilderArgs(imageBuildState().hostedOptionPrefix + "+RuntimeAssertions");
                return true;
            }
            return false;
        }

        boolean handleJarFileArg(File file, ImageBuilderConfig config) {
            try {
                Manifest manifest = null;
                for (FastJar.Entry entry : FastJar.list(file)) {
                    if ("META-INF/MANIFEST.MF".equals(entry.name)) {
                        manifest = new Manifest(FastJar.getInputStream(file, entry));
                    }
                }
                if (manifest == null) {
                    return false;
                }
                Attributes mainAttributes = manifest.getMainAttributes();
                String mainClass = mainAttributes.getValue("Main-Class");
                if (mainClass == null) {
                    return false;
                }
                config.addImageBuilderArgs(classArgPrefix + mainClass);
                String jarFileName = file.getName().toString();
                String jarFileNameBase = jarFileName.substring(0, jarFileName.length() - 4);
                config.addImageBuilderArgs(nameArgPrefix + jarFileNameBase);
                config.addImageClasspath(file.toPath());
                String classPath = mainAttributes.getValue("Class-Path");
                /* Missing Class-Path Attribute is tolerable */
                if (classPath != null) {
                    for (String cp : classPath.split(" +")) {
                        config.addImageClasspath(Paths.get(cp));
                    }
                }
            } catch (IOException e) {
                return false;
            }
            return true;
        }
    }
}
