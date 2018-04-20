/*
 * Copyright (c) 2018, Oracle and/or its affiliates. All rights reserved.
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
import org.graalvm.component.installer.model.ComponentRegistry;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.AccessDeniedException;
import java.nio.file.DirectoryNotEmptyException;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;
import static org.graalvm.component.installer.CommonConstants.PATH_COMPONENT_STORAGE;
import org.graalvm.component.installer.commands.InfoCommand;
import org.graalvm.component.installer.commands.InstallCommand;
import org.graalvm.component.installer.commands.ListInstalledCommand;
import org.graalvm.component.installer.commands.RebuildImageCommand;
import org.graalvm.component.installer.commands.UninstallCommand;
import org.graalvm.component.installer.persist.DirectoryStorage;
import org.graalvm.component.installer.persist.RemoteCatalogDownloader;

/**
 * The launcher.
 */
public final class ComponentInstaller {
    private static final String GRAAL_DEFAULT_RELATIVE_PATH = "../../..";

    private String[] mainArguments;
    private String command;
    private InstallerCommand cmdHandler;
    private LinkedList<String> cmdlineParams;
    private List<String> parameters = Collections.emptyList();
    private Path graalHomePath;
    private Path storagePath;
    private String catalogURL;

    static final Map<String, InstallerCommand> commands = new HashMap<>();
    static final Map<String, String> globalOptions = new HashMap<>();

    static {
        commands.put("install", new InstallCommand()); // NOI18N
        commands.put("uninstall", new UninstallCommand()); // NOI18N
        commands.put("list", new ListInstalledCommand()); // NOI18N
        commands.put("info", new InfoCommand()); // NOI18N
        commands.put("rebuild-images", new RebuildImageCommand()); // NOI18N

        globalOptions.put(Commands.OPTION_VERBOSE, "");
        globalOptions.put(Commands.OPTION_DEBUG, "");
        globalOptions.put(Commands.OPTION_HELP, "");
        globalOptions.put(Commands.OPTION_CATALOG, "");
        globalOptions.put(Commands.OPTION_FOREIGN_CATALOG, "s");
        globalOptions.put(Commands.OPTION_URLS, "");
        globalOptions.put(Commands.OPTION_NO_DOWNLOAD_PROGRESS, "");
    }

    private static final ResourceBundle BUNDLE = ResourceBundle.getBundle(
                    "org.graalvm.component.installer.Bundle"); // NOI18N

    private ComponentInstaller(String[] args) {
        this.mainArguments = args;
    }

    private static void printUsage() {
        System.err.println(MessageFormat.format(BUNDLE.getString("INFO_InstallerVersion"), CommonConstants.INSTALLER_VERSION)); // NOI18N
        printHelp();
    }

    private static void printHelp() {
        System.err.println(BUNDLE.getString("INFO_Usage")); // NOI18N
    }

    static void err(String messageKey, Object... args) {
        String s;

        if (args == null || args.length == 0) {
            s = BUNDLE.getString(messageKey);
        } else {
            s = MessageFormat.format(BUNDLE.getString(messageKey), args);
        }
        System.err.println(s);
        printHelp();
        System.exit(1);
    }

    private Environment env;

    private int processCommand() {
        SimpleGetopt go = new SimpleGetopt(globalOptions);
        go.setParameters(cmdlineParams);
        for (String s : commands.keySet()) {
            go.addCommandOptions(s, commands.get(s).supportedOptions());
        }
        go.process();
        cmdHandler = commands.get(go.getCommand());
        if (cmdHandler == null) {
            err("ERROR_MissingCommand"); // NOI18N
        }
        Map<String, String> optValues = go.getOptValues();
        parameters = go.getPositionalParameters();

        try {
            env = new Environment(command, null, parameters, optValues);
            finddGraalHome();
            env.setGraalHome(graalHomePath);
            env.setLocalRegistry(new ComponentRegistry(env, new DirectoryStorage(
                            env, storagePath, graalHomePath)));

            catalogURL = optValues.get(Commands.OPTION_FOREIGN_CATALOG);
            if (catalogURL != null || optValues.containsKey(Commands.OPTION_CATALOG)) {
                RemoteCatalogDownloader downloader = new RemoteCatalogDownloader(
                                env,
                                env.getLocalRegistry(),
                                getCatalogURL(env));
                env.setComponentRegistry(downloader);
                env.setFileIterable(new CatalogIterable(env, env, downloader));
                if (optValues.containsKey(Commands.OPTION_URLS)) {
                    err("ERROR_MultipleSourcesUnsupported");
                }
            } else if (optValues.containsKey(Commands.OPTION_URLS)) {
                env.setFileIterable(new DownloadURLIterable(env, env));
            }
            cmdHandler.init(env, env.withBundle(cmdHandler.getClass()));
            return cmdHandler.execute();
        } catch (FileAlreadyExistsException ex) {
            env.error("INSTALLER_FileExists", ex, ex.getMessage()); // NOI18N
            return 2;
        } catch (NoSuchFileException ex) {
            env.error("INSTALLER_FileDoesNotExist", ex, ex.getMessage()); // NOI18N
            return 2;
        } catch (AccessDeniedException ex) {
            env.error("INSTALLER_AccessDenied", ex, ex.getMessage());
            return 2;
        } catch (DirectoryNotEmptyException ex) {
            env.error("INSTALLER_DirectoryNotEmpty", ex, ex.getMessage()); // NOI18N
            return 2;
        } catch (IOException ex) {
            env.error("INSTALLER_IOException", ex, ex.getMessage()); // NOI18N
            return 2;
        } catch (MetadataException ex) {
            env.error("INSTALLER_InvalidMetadata", ex, ex.getMessage()); // NOI18N
            return 3;
        } catch (InstallerStopException ex) {
            env.error("INSTALLER_Error", ex, ex.getMessage()); // NOI18N
            return 3;
        } catch (RuntimeException ex) {
            env.error("INSTALLER_InternalError", ex, ex.getMessage()); // NOI18N
            return 3;
        }
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
        String graalHome = System.getProperty("GRAAL_HOME", System.getenv("GRAAL_HOME")); // NOI18N
        Path graalPath = null;
        if (graalHome != null) {
            graalPath = Paths.get(graalHome);
        } else {
            URL loc = getClass().getProtectionDomain().getCodeSource().getLocation();
            try {
                File f = new File(loc.toURI());
                if (f != null) {
                    graalPath = f.toPath().resolve(GRAAL_DEFAULT_RELATIVE_PATH).normalize();
                }
            } catch (URISyntaxException ex) {
                Logger.getLogger(ComponentInstaller.class.getName()).log(Level.SEVERE, null, ex);
            }
        }
        if (graalPath == null) {
            throw env.failure("ERROR_NoGraalVMDirectory", null);
        }
        if (!Files.isDirectory(graalPath) || !Files.exists(graalPath.resolve("release"))) {
            throw env.failure("ERROR_InvalidGraalVMDirectory", null, graalPath);
        }
        if (!Files.isDirectory(storagePath = graalPath.resolve(PATH_COMPONENT_STORAGE))) {
            throw env.failure("ERROR_InvalidGraalVMDirectory", null, graalPath);
        }
        graalHomePath = graalPath;
        return graalPath;
    }

    public void run() {
        if (mainArguments.length < 1) {
            printUsage();
            System.exit(1);
        }
        try {
            cmdlineParams = new LinkedList<>(Arrays.asList(mainArguments));

            System.exit(processCommand());
        } catch (Exception ex) {
            System.err.println(MessageFormat.format(
                            BUNDLE.getString("ERROR_InternalError"), ex.getMessage())); // NOI18N
            ex.printStackTrace();
            System.exit(3);
        }

    }

    private URL getCatalogURL(Feedback f) {
        String def = catalogURL != null ? catalogURL : f.l10n("Installer_BuiltingCatalogURL");
        String s = System.getProperty(CommonConstants.SYSPROP_CATALOG_URL, def);
        try {
            return new URL(s);
        } catch (MalformedURLException ex) {
            throw f.failure("INSTALLER_InvalidCatalogURL", ex, s);
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        new ComponentInstaller(args).run();
    }
}
