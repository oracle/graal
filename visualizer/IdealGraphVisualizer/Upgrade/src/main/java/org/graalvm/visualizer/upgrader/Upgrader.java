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

import org.graalvm.visualizer.upgrader.impl.UpgradeFromOldDev;
import org.graalvm.visualizer.upgrader.impl.UpgradeFrom_0_26;
import org.netbeans.util.Util;
import org.openide.modules.InstalledFileLocator;
import org.openide.util.NbBundle;

import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.SwingUtilities;
import java.awt.BorderLayout;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Portions of the file taken from o.n.upgrader.
 *
 * @author sdedic, odouda
 */
public abstract class Upgrader {
    private static final Logger LOGGER = Logger.getLogger(Upgrader.class.getName());
    /**
     * Former versions have to be added here. Newer first.
     */
    private static final List<String> FORMER_VERSIONS = Arrays.asList("0.26", "dev"); // NOI18N
    /**
     * Newer Upgraders needs to be "registered" here. Upgrader may be ommited if
     * no changes are needed for particular version.
     */
    private static final List<Upgrader> KNOWN_UPGRADERS = Arrays.asList(new UpgradeFrom_0_26(), new UpgradeFromOldDev());

    protected static final String NETBEANS_USERDIR_ROOT = "netbeans.default_userdir_root"; // NOI18N
    protected static final String IMPORTED_MARK = "var/imported"; // NOI18N
    protected static final String NETBEANS_USER = "netbeans.user"; // NOI18N

    /**
     * References version from which the import is done, so Upgraders could
     * change behavior accordingly.
     */
    protected static String importingFromVersion;
    private static final Stack<Upgrader> versionUpgrade = new Stack<>();

    public static void main(String[] args) {
        LOGGER.fine("Starting importer");
        if (LOGGER.isLoggable(Level.FINEST)) {
            logAllPossibleInfo();
        }
        File sourceFolder = findFormerVersionFolder();

        if (sourceFolder == null) {
            LOGGER.fine("Didn't found former or dev dir to import from.");
            //no source to import from
            return;
        }

        prepareImport();

        LOGGER.fine("Using dir: " + sourceFolder.getAbsolutePath() + " for import.");
        showUpgradeDialog(sourceFolder);
    }

    private static File findFormerVersionFolder() {
        LOGGER.fine("Looking for former version dir.");
        String defaultUserdirRoot = System.getProperty(NETBEANS_USERDIR_ROOT); // NOI18N
        File userHomeFile;
        File sourceFolder;
        if (defaultUserdirRoot != null) {
            userHomeFile = new File(defaultUserdirRoot);
        } else {
            LOGGER.fine("User dir: " + NETBEANS_USERDIR_ROOT + " not found.");
            defaultUserdirRoot = System.getProperty(NETBEANS_USER);
            if (defaultUserdirRoot == null) {
                LOGGER.fine("User dir: " + NETBEANS_USER + " not found.");
                return null;
            }
            // guess - this will work hopefully on Windows, where the obsolete launcher does not define netbeans.default_userdir_root
            userHomeFile = new File(defaultUserdirRoot).getParentFile();
        }
        LOGGER.fine("Using userdir root: " + userHomeFile.getAbsolutePath());
        for (String version : FORMER_VERSIONS) {
            sourceFolder = new File(userHomeFile.getAbsolutePath(), version);
            if (sourceFolder.exists() && sourceFolder.isDirectory()) {
                if (version.equals("dev") && (wasDev(sourceFolder) || newDev(sourceFolder))) {
                    LOGGER.fine("Former dev version dir: " + sourceFolder.getAbsolutePath() + " won't be used.");
                    return null;
                }
                importingFromVersion = version;
                LOGGER.fine("Found former version dir: " + sourceFolder.getAbsolutePath());
                return sourceFolder;
            }
        }
        LOGGER.fine("Former version dir not found.");
        return null;
    }

    private static boolean newDev(File devUserFile) {
        File importedFile = new File(devUserFile, IMPORTED_MARK);
        return importedFile.exists();
        // NOT importing from a _new_ "dev" version; new versions have the imported flag on
    }

    private static boolean wasDev(File sourceFolder) {
        String currentUserDir = System.getProperty(NETBEANS_USER);
        return currentUserDir == null || new File(currentUserDir).equals(sourceFolder);
        // we run the dev version as well -- the user must
        // know what he's doing.
    }

    private static boolean showUpgradeDialog(final File source) {
        Util.setDefaultLookAndFeel();

        JPanel panel = new JPanel(new BorderLayout());
        panel.add(new AutoUpgradePanel(source.getAbsolutePath()), BorderLayout.CENTER);
        JProgressBar progressBar = new JProgressBar(0, 100);
        progressBar.setValue(0);
        progressBar.setStringPainted(true);
        progressBar.setIndeterminate(true);
        panel.add(progressBar, BorderLayout.SOUTH);
        progressBar.setVisible(false);

        JButton bYES = new JButton("Yes");
        bYES.setMnemonic(KeyEvent.VK_Y);
        JButton bNO = new JButton("No");
        bNO.setMnemonic(KeyEvent.VK_N);
        JButton[] options = new JButton[]{bYES, bNO};
        JOptionPane p = new JOptionPane(panel, JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_OPTION, null, options, bYES);
        JDialog d = Util.createJOptionProgressDialog(p, NbBundle.getMessage(Upgrader.class, "MSG_Confirmation_Title"), source, progressBar);

        // used by tests only
        if (Boolean.getBoolean("netbeans.startup.autoupgrade")) { // NOI18N
            SwingUtilities.invokeLater(() -> p.setValue(JOptionPane.YES_OPTION));
        }
        d.setVisible(true);

        return new Integer(JOptionPane.YES_OPTION).equals(p.getValue());
    }

    public static void doCopyToUserDir(File source) throws IOException {
        File userdir = new File(System.getProperty(NETBEANS_USER, "")); // NOI18N
        File netBeansDir = InstalledFileLocator.getDefault().locate("modules", null, false).getParentFile().getParentFile();  //NOI18N
        File importFile = new File(netBeansDir, "etc/igv.import");  //NOI18N
        LOGGER.fine("Import file: " + importFile); // NOI18N
        LOGGER.info("Importing from " + source + " to " + userdir); // NOI18N
        CopyFiles.copyDeep(source, userdir, importFile);
    }

    public static void doImport() {
        if (versionUpgrade.empty() && LOGGER.isLoggable(Level.FINEST)) {
            LOGGER.finest("No issued Upgraders.");
        }
        while (!versionUpgrade.empty()) {
            Upgrader upgrader = versionUpgrade.pop();
            if (LOGGER.isLoggable(Level.FINEST)) {
                LOGGER.finest("Importing using Upgrader: " + upgrader.getClass().getName());
            }
            upgrader.doVersionImport();
        }
    }

    private static void logAllPossibleInfo() {
        LOGGER.finest(NETBEANS_USER + " = " + System.getProperty(NETBEANS_USER));
        LOGGER.finest(NETBEANS_USERDIR_ROOT + " = " + System.getProperty(NETBEANS_USERDIR_ROOT));
        LOGGER.finest("Former versions = [" + FORMER_VERSIONS.stream().reduce("", (m, n) -> m + ", " + n).substring(2) + "]");
        LOGGER.finest(buildUpgradersInfo());
    }

    private static String buildUpgradersInfo() {
        StringBuilder sb = new StringBuilder();
        for (Upgrader upgrader : KNOWN_UPGRADERS) {
            sb.append("\n\t").append(upgrader.getClass().getName());
            sb.append("\n\t\t").append("Importing from version: ").append(upgrader.getImportingVersion());
            sb.append("\n\t\t").append("Changes: ").append(upgrader.getChangesInfo());
        }
        return sb.toString();
    }

    private static void prepareImport() {
        Map<String, Upgrader> upgraders = new HashMap<>(KNOWN_UPGRADERS.size());
        for (Upgrader upgrader : KNOWN_UPGRADERS) {
            upgraders.put(upgrader.getImportingVersion(), upgrader);
        }

        for (String version : FORMER_VERSIONS) {
            Upgrader upgrader = upgraders.get(version);
            if (upgrader != null) {
                versionUpgrade.push(upgrader);
            }
            if (version.equals(importingFromVersion)) {
                return;
            }
        }
    }

    /**
     * Returned String should be equal to some of the former versions. Otherwise
     * Upgrader won't be used.
     */
    protected abstract String getImportingVersion();

    /**
     * Returned String should briefly introduce changes made by Upgrader.
     */
    protected abstract String getChangesInfo();

    protected abstract void doVersionImport();
}
