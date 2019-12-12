/*
 * Copyright (c) 2018, 2019, Oracle and/or its affiliates. All rights reserved.
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

import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.security.CodeSource;
import java.security.ProtectionDomain;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.ServiceConfigurationError;
import java.util.ServiceLoader;
import java.util.function.Consumer;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.graalvm.component.installer.CommandInput.CatalogFactory;
import static org.graalvm.component.installer.CommonConstants.PATH_COMPONENT_STORAGE;
import org.graalvm.component.installer.commands.AvailableCommand;
import org.graalvm.component.installer.commands.InfoCommand;
import org.graalvm.component.installer.commands.InstallCommand;
import org.graalvm.component.installer.commands.ListInstalledCommand;
import org.graalvm.component.installer.commands.PostInstCommand;
import org.graalvm.component.installer.commands.PreRemoveCommand;
import org.graalvm.component.installer.commands.RebuildImageCommand;
import org.graalvm.component.installer.commands.UninstallCommand;
import org.graalvm.component.installer.commands.UpgradeCommand;
import org.graalvm.component.installer.model.CatalogContents;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.persist.DirectoryStorage;
import org.graalvm.component.installer.remote.CatalogIterable;
import org.graalvm.component.installer.remote.RemoteCatalogDownloader;

/**
 * The launcher.
 */
public class ComponentInstaller {
    private static final Logger LOG = Logger.getLogger(ComponentInstaller.class.getName());

    public static final String GRAAL_DEFAULT_RELATIVE_PATH = "../.."; // NOI18N

    private static final Environment SIMPLE_ENV = new Environment("help", Collections.emptyList(), Collections.emptyMap()).enableStacktraces(); // NOI18N

    private String command;
    private InstallerCommand cmdHandler;
    private LinkedList<String> cmdlineParams;
    private List<String> parameters = Collections.emptyList();
    private Path graalHomePath;
    private Path storagePath;

    static final Map<String, InstallerCommand> commands = new HashMap<>();
    public static final Map<String, String> globalOptions = new HashMap<>();
    public static final Map<String, String> componentOptions = new HashMap<>();

    @SuppressWarnings("deprecation")
    static void initCommands() {
        // not necessary except for tests to cleanup extra items
        commands.clear();
        globalOptions.clear();

        // options for commands working with component sources:
        componentOptions.put(Commands.OPTION_CATALOG, "");
        componentOptions.put(Commands.OPTION_FILES, "");
        componentOptions.put(Commands.OPTION_URLS, "");
        componentOptions.put(Commands.OPTION_FOREIGN_CATALOG, "s");
        componentOptions.put(Commands.OPTION_FILES_OLD, "=L");

        componentOptions.put(Commands.LONG_OPTION_FILES, Commands.OPTION_FILES);
        componentOptions.put(Commands.LONG_OPTION_CATALOG, Commands.OPTION_CATALOG);
        componentOptions.put(Commands.LONG_OPTION_URLS, Commands.OPTION_URLS);
        componentOptions.put(Commands.LONG_OPTION_FOREIGN_CATALOG, Commands.OPTION_FOREIGN_CATALOG);
        componentOptions.put(Commands.LONG_OPTION_FILES_OLD, Commands.OPTION_FILES);

        commands.put("install", new InstallCommand()); // NOI18N
        commands.put("remove", new UninstallCommand()); // NOI18N
        commands.put("list", new ListInstalledCommand()); // NOI18N
        commands.put("available", new AvailableCommand()); // NOI18N
        commands.put("info", new InfoCommand()); // NOI18N
        commands.put("rebuild-images", new RebuildImageCommand()); // NOI18N
        commands.put("update", new UpgradeCommand()); // NOI18N
        // commands.put("update", new UpgradeCommand(false)); // NOI18N

        // commands used internally by system scripts, names intentionally hashed.
        commands.put("#postinstall", new PostInstCommand()); // NOI18N
        commands.put("#preremove", new PreRemoveCommand()); // NOI18N

        globalOptions.put(Commands.OPTION_VERBOSE, "");
        globalOptions.put(Commands.OPTION_DEBUG, "");
        globalOptions.put(Commands.OPTION_HELP, "");

        globalOptions.put(Commands.LONG_OPTION_VERBOSE, Commands.OPTION_VERBOSE);
        globalOptions.put(Commands.LONG_OPTION_DEBUG, Commands.OPTION_DEBUG);
        globalOptions.put(Commands.LONG_OPTION_HELP, Commands.OPTION_HELP);

        globalOptions.put(Commands.OPTION_AUTO_YES, "");
        globalOptions.put(Commands.LONG_OPTION_AUTO_YES, Commands.OPTION_AUTO_YES);

        globalOptions.put(Commands.OPTION_NON_INTERACTIVE, "");

        // for simplicity, these options are global, but still commands that use them should
        // declare them explicitly.
        globalOptions.putAll(componentOptions);

    }

    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle(
                    "org.graalvm.component.installer.Bundle"); // NOI18N

    public static void forSoftwareChannels(boolean report, Consumer<SoftwareChannel.Factory> callback) {
        ServiceLoader<SoftwareChannel.Factory> channels = ServiceLoader.load(SoftwareChannel.Factory.class);
        for (Iterator<SoftwareChannel.Factory> it = channels.iterator(); it.hasNext();) {
            try {
                SoftwareChannel.Factory ch = it.next();
                callback.accept(ch);
            } catch (ServiceConfigurationError | Exception ex) {
                if (report) {
                    LOG.log(Level.SEVERE,
                                    MessageFormat.format(BUNDLE.getString("ERROR_SoftwareChannelBroken"), ex.getLocalizedMessage()));
                }
            }
        }
    }

    static {
        initCommands();
        forSoftwareChannels(true, (ch) -> {
            ch.init(SIMPLE_ENV, SIMPLE_ENV);
            globalOptions.putAll(ch.globalOptions());
        });
    }

    ComponentInstaller(String[] args) {
        cmdlineParams = new LinkedList<>(Arrays.asList(args));
    }

    protected void printUsage(Feedback output) {
        output.error("INFO_InstallerVersion", null, CommonConstants.INSTALLER_VERSION); // NOI18N
        printHelp(output);
    }

    private static void printHelp(Feedback output) {
        StringBuilder extra = new StringBuilder();

        forSoftwareChannels(false, (ch) -> {
            ch.init(SIMPLE_ENV, output);
            String s = ch.globalOptionsHelp();
            if (s != null) {
                extra.append(s);
            }
        });
        String extraS;

        if (extra.length() != 0) {
            extraS = output.l10n("INFO_UsageExtensions", extra.toString());
        } else {
            extraS = ""; // NOI18N
        }

        output.message("INFO_Usage", extraS); // NOI18N
    }

    static void printErr(String messageKey, Object... args) {
        SIMPLE_ENV.message(messageKey, args);
    }

    static RuntimeException err(String messageKey, Object... args) {
        printErr(messageKey, args);
        printHelp(SIMPLE_ENV);
        System.exit(1);
        throw new RuntimeException("should not reach here");
    }

    protected RuntimeException error(String messageKey, Object... args) {
        return err(messageKey, args);
    }

    private Environment env;
    private CommandInput input;
    private Feedback feedback;

    CommandInput getInput() {
        return input;
    }

    void setInput(CommandInput input) {
        this.input = input;
    }

    Feedback getFeedback() {
        return feedback;
    }

    void setFeedback(Feedback feedback) {
        this.feedback = feedback;
    }

    Environment setupEnvironment(SimpleGetopt go) {
        Environment e = new Environment(command, parameters, go.getOptValues());
        setInput(e);
        setFeedback(e);

        finddGraalHome();
        e.setGraalHome(graalHomePath);
        // Use our own GraalVM's trust store contents; also bypasses embedded trust store
        // when running AOT.
        Path trustStorePath = SystemUtils.resolveRelative(SystemUtils.getRuntimeBaseDir(e.getGraalHomePath()),
                        "lib/security/cacerts"); // NOI18N
        System.setProperty("javax.net.ssl.trustStore", trustStorePath.normalize().toString()); // NOI18N
        DirectoryStorage storage = new DirectoryStorage(e, storagePath, graalHomePath);
        storage.setJavaVersion("" + SystemUtils.getJavaMajorVersion(e));
        e.setLocalRegistry(new ComponentRegistry(e, storage));
        FileOperations fops = FileOperations.createPlatformInstance(e, e.getGraalHomePath());
        e.setFileOperations(fops);

        return e;
    }

    protected SimpleGetopt createOptionsObject(Map<String, String> opts) {
        return new SimpleGetopt(opts);
    }

    SimpleGetopt createOptions(LinkedList<String> cmdline) {
        SimpleGetopt go = createOptionsObject(globalOptions);
        go.setParameters(cmdline);
        for (String s : commands.keySet()) {
            go.addCommandOptions(s, commands.get(s).supportedOptions());
        }
        go.process();
        command = go.getCommand();
        cmdHandler = commands.get(command);
        Map<String, String> optValues = go.getOptValues();
        if (cmdHandler == null) {
            if (optValues.containsKey(Commands.OPTION_HELP)) {
                // regular Environment cannot be initialized.
                printUsage(SIMPLE_ENV);
                return null;
            }
            error("ERROR_MissingCommand"); // NOI18N
        }
        parameters = go.getPositionalParameters();
        return go;
    }

    public String getCommand() {
        return command;
    }

    public List<String> getParameters() {
        return parameters;
    }

    int processOptions(LinkedList<String> cmdline) {
        if (cmdline.size() < 1) {
            printUsage(SIMPLE_ENV);
            return 1;
        }
        SimpleGetopt go = createOptions(cmdline);
        if (go == null) {
            return 0;
        }
        // also sets up input and feedback.
        env = setupEnvironment(go);
        forSoftwareChannels(true, (ch) -> {
            ch.init(input, feedback);
        });

        int srcCount = 0;
        if (input.hasOption(Commands.OPTION_FILES)) {
            srcCount++;
        }
        if (input.hasOption(Commands.OPTION_URLS)) {
            srcCount++;
        }
        if (srcCount > 1) {
            error("ERROR_MultipleSourcesUnsupported");
        }

        if (input.hasOption(Commands.OPTION_AUTO_YES)) {
            env.setAutoYesEnabled(true);
        }
        if (input.hasOption(Commands.OPTION_NON_INTERACTIVE)) {
            env.setNonInteractive(true);
        }

        // explicit location
        String catalogURL = getExplicitCatalogURL();
        String builtinCatLocation = getReleaseCatalogURL();
        RemoteCatalogDownloader downloader = new RemoteCatalogDownloader(
                        input,
                        feedback,
                        catalogURL);
        if (builtinCatLocation == null) {
            builtinCatLocation = feedback.l10n("Installer_BuiltingCatalogURL");
        }
        downloader.setDefaultCatalog(builtinCatLocation); // NOI18N
        CatalogFactory cFactory = (CommandInput in, ComponentRegistry lreg) -> {
            RemoteCatalogDownloader nDownloader;
            if (lreg == in.getLocalRegistry()) {
                nDownloader = downloader;
            } else {
                nDownloader = new RemoteCatalogDownloader(in, env,
                                downloader.getOverrideCatalogSpec());
            }
            CatalogContents col = new CatalogContents(env, nDownloader.getStorage(), lreg);
            col.setRemoteEnabled(downloader.isRemoteSourcesAllowed());
            return col;
        };
        env.setCatalogFactory(cFactory);
        boolean builtinsImplied = true;
        boolean setIterable = true;
        if (input.hasOption(Commands.OPTION_FILES)) {
            FileIterable fi = new FileIterable(env, env);
            fi.setCatalogFactory(cFactory);
            env.setFileIterable(fi);

            // optionally resolve local dependencies against parent directories
            // of specified files.
            builtinsImplied = false;
            if (input.hasOption(Commands.OPTION_LOCAL_DEPENDENCIES)) {
                while (env.hasParameter()) {
                    String s = env.nextParameter();
                    Path p = SystemUtils.fromUserString(s);
                    if (p != null) {
                        Path parent = p.getParent();
                        if (parent != null && Files.isDirectory(parent)) {
                            SoftwareChannelSource localSource = new SoftwareChannelSource(parent.toUri().toString(), null);
                            downloader.addLocalChannelSource(localSource);
                        }
                    }
                }
                env.resetParameters();
            }
            setIterable = false;
        } else if (input.hasOption(Commands.OPTION_URLS)) {
            DownloadURLIterable dit = new DownloadURLIterable(env, env);
            dit.setCatalogFactory(cFactory);
            env.setFileIterable(dit);
            setIterable = false;
            builtinsImplied = false;
        }

        if (setIterable) {
            env.setFileIterable(new CatalogIterable(env, env));
        }
        downloader.setRemoteSourcesAllowed(builtinsImplied || env.hasOption(Commands.OPTION_CATALOG) ||
                        env.hasOption(Commands.OPTION_FOREIGN_CATALOG));
        return -1;
    }

    int doProcessCommand() throws IOException {
        cmdHandler.init(input, feedback.withBundle(cmdHandler.getClass()));
        return cmdHandler.execute();
    }

    private int processCommand(LinkedList<String> cmds) {
        int retcode = 0;
        try {
            retcode = processOptions(cmds);
            if (retcode >= 0) {
                return retcode;
            }
            retcode = doProcessCommand();
        } catch (FileAlreadyExistsException ex) {
            feedback.error("INSTALLER_FileExists", ex, ex.getLocalizedMessage()); // NOI18N
            return 2;
        } catch (NoSuchFileException ex) {
            feedback.error("INSTALLER_FileDoesNotExist", ex, ex.getLocalizedMessage()); // NOI18N
            return 2;
        } catch (AccessDeniedException ex) {
            feedback.error("INSTALLER_AccessDenied", ex, ex.getLocalizedMessage());
            return 2;
        } catch (DirectoryNotEmptyException ex) {
            feedback.error("INSTALLER_DirectoryNotEmpty", ex, ex.getLocalizedMessage()); // NOI18N
            return 2;
        } catch (IOError | IOException ex) {
            feedback.error("INSTALLER_IOException", ex, ex.getLocalizedMessage()); // NOI18N
            return 2;
        } catch (MetadataException ex) {
            feedback.error("INSTALLER_InvalidMetadata", ex, ex.getLocalizedMessage()); // NOI18N
            return 3;
        } catch (UserAbortException ex) {
            feedback.error("ERROR_Aborted", ex, ex.getLocalizedMessage()); // NOI18N
            return 4;
        } catch (InstallerStopException ex) {
            feedback.error("INSTALLER_Error", ex, ex.getLocalizedMessage()); // NOI18N
            return 3;
        } catch (RuntimeException ex) {
            feedback.error("INSTALLER_InternalError", ex, ex.getLocalizedMessage()); // NOI18N
            return 3;
        } finally {
            if (env != null) {
                try {
                    env.close();
                } catch (IOException ex) {
                }
            }
        }
        return retcode;
    }

    /**
     * Finds Graal Home directory. It is either specified by the GRAAL_HOME system property,
     * environment variable, or the executing JAR's location - in the order of precedence.
     * <p/>
     * The location is sanity checked and the method throws {@link FailedOperationException} if not
     * proper Graal dir.
     *
     * @return existing Graal home
     */
    Path finddGraalHome() {
        String graalHome = input.getParameter("GRAAL_HOME", // NOI18N
                        input.getParameter("GRAAL_HOME", false), // NOI18N
                        true);
        Path graalPath = null;
        if (graalHome != null) {
            graalPath = SystemUtils.fromUserString(graalHome);
        } else {
            URL loc = null;
            ProtectionDomain pd = ComponentInstaller.class.getProtectionDomain();
            if (pd != null) {
                CodeSource cs = pd.getCodeSource();
                if (cs != null) {
                    loc = cs.getLocation();
                }
            }
            if (loc != null) {
                try {
                    File f = new File(loc.toURI());
                    Path guParent = f.isFile() ? f.toPath().getParent() : f.toPath();
                    if (guParent != null) {
                        graalPath = guParent.resolve(SystemUtils.fromCommonString(GRAAL_DEFAULT_RELATIVE_PATH)).normalize().toAbsolutePath();
                        Path p = graalPath.getFileName();
                        if (p != null && "lib".equals(p.toString())) { // NOi18N
                            graalPath = graalPath.getParent();
                        }
                    }
                } catch (URISyntaxException ex) {
                    Logger.getLogger(ComponentInstaller.class.getName()).log(Level.SEVERE, null, ex);
                }
            }
        }
        if (graalPath == null) {
            throw SIMPLE_ENV.failure("ERROR_NoGraalVMDirectory", null);
        }
        if (!Files.isDirectory(graalPath) || !Files.exists(graalPath.resolve(SystemUtils.fileName("release")))) {
            throw SIMPLE_ENV.failure("ERROR_InvalidGraalVMDirectory", null, graalPath);
        }
        if (!Files.isDirectory(storagePath = graalPath.resolve(SystemUtils.fromCommonString(PATH_COMPONENT_STORAGE)))) {
            throw SIMPLE_ENV.failure("ERROR_InvalidGraalVMDirectory", null, graalPath);
        }
        graalHomePath = graalPath.normalize();

        String libpath = System.getProperty("java.library.path"); // NOI18N
        if (libpath == null || libpath.isEmpty()) {
            // SVM mode: libpath is not define, define it to the JRE:
            Path newLibPath = SystemUtils.getRuntimeLibDir(graalPath, true);
            if (newLibPath == null) {
                throw SIMPLE_ENV.failure("ERROR_UnknownSystem", null, System.getProperty("os.name")); // NOI18N
            }
            System.setProperty("java.library.path", newLibPath.toString()); // NOI18N
        }
        return graalPath;
    }

    public void run() {
        try {
            System.exit(processCommand(cmdlineParams));
        } catch (UserAbortException ex) {
            SIMPLE_ENV.message("ERROR_Aborted", ex.getMessage()); // NOI18N
        } catch (Exception ex) {
            SIMPLE_ENV.error("ERROR_InternalError", ex, ex.getMessage()); // NOI18N
            System.exit(3);
        }
    }

    String getExplicitCatalogURL() {
        String def = null;
        String cmdLine = input.optValue(Commands.OPTION_FOREIGN_CATALOG);
        if (cmdLine != null) {
            def = cmdLine;
        }
        String envVar = input.getParameter(CommonConstants.ENV_CATALOG_URL, false);
        if (envVar != null) {
            def = envVar;
        }
        String s = input.getParameter(CommonConstants.SYSPROP_CATALOG_URL, def, true);
        if (s == null) {
            return null;
        }
        try {
            URI check = URI.create(s);
            if (check.getScheme() == null || check.getScheme().length() < 2) {
                Path p = SystemUtils.fromUserString(s);
                // convert plain filename to file:// URL.
                if (Files.isReadable(p)) {
                    return p.toFile().toURI().toString();
                }
            }
        } catch (IllegalArgumentException ex) {
            // expected, use the argument as it is.
        }
        return s;
    }

    private String getReleaseCatalogURL() {
        String s = env.getLocalRegistry().getGraalCapabilities().get(CommonConstants.RELEASE_CATALOG_KEY);
        return s;
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        new ComponentInstaller(args).run();
    }
}
