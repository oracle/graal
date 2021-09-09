/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.component.installer.commands;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.graalvm.component.installer.Archive;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.SystemUtils;
import org.graalvm.component.installer.UserAbortException;
import org.graalvm.component.installer.model.ComponentInfo;
import org.graalvm.component.installer.model.ComponentRegistry;
import org.graalvm.component.installer.persist.MetadataLoader;
import org.graalvm.component.installer.remote.FileDownloader;

/**
 * License console "dialog" driver.
 * 
 * @author sdedic
 */
public class LicensePresenter {
    private final Feedback feedback;
    private final ComponentRegistry localRegistry;

    /**
     * Licenses to be processed.
     */
    private final Map<String, List<MetadataLoader>> licensesToAccept;

    private State state = State.NONE;

    /**
     * True, if multiple licenses were present initially.
     */
    private boolean multiLicenses;

    /**
     * The ID (hash) of the license to be displayed.
     */
    private String displayLicenseId;

    enum State {
        /**
         * List of licenses with choice to display some or accept all.
         */
        LIST,

        /**
         * Accepts the input.
         */
        LISTINPUT,

        /**
         * Single license prompt.
         */
        SINGLE,

        /**
         * Display a license, confirm it.
         */
        LICENSE,

        /**
         * Abort.
         */
        NONE
    }

    public LicensePresenter(Feedback feedback, ComponentRegistry localRegistry, Map<String, List<MetadataLoader>> licenseIDs) {
        this.feedback = feedback.withBundle(LicensePresenter.class);
        this.localRegistry = localRegistry;
        this.licensesToAccept = licenseIDs;
    }

    public Map<String, List<MetadataLoader>> getLicensesToAccept() {
        return licensesToAccept;
    }

    public void filterAcceptedLicenses() {
        for (String licId : new ArrayList<>(licensesToAccept.keySet())) {
            Collection<MetadataLoader> loaders = licensesToAccept.get(licId);
            for (MetadataLoader ldr : new ArrayList<>(loaders)) {
                ComponentInfo ci = ldr.getComponentInfo();
                // query the metadata loader (possibly delegates to original software channel
                // provider).
                Date accepted = ldr.isLicenseAccepted(ci, licId);
                if (accepted == null) {
                    accepted = localRegistry.isLicenseAccepted(ci, licId);
                }
                if (accepted != null) {
                    feedback.verboseOutput("INSTALL_LicenseAcceptedAt", ldr.getLicenseType(), accepted, ci.getId(), ci.getName());
                    loaders.remove(ldr);
                }
            }
            if (loaders.isEmpty()) {
                licensesToAccept.remove(licId);
            }
        }
        multiLicenses = licensesToAccept.size() > 1;
    }

    public State getState() {
        return state;
    }

    public boolean isMultiLicenses() {
        return multiLicenses;
    }

    public String getDisplayLicenseId() {
        return displayLicenseId;
    }

    String formatComponentList(String licId) {
        List<MetadataLoader> loaders = licensesToAccept.get(licId);
        String list = null;
        for (MetadataLoader l : loaders) {
            ComponentInfo ci = l.getComponentInfo();
            if (list == null) {
                list = feedback.l10n("INSTALL_LicenseComponentStart", ci.getName());
            } else {
                list = feedback.l10n("INSTALL_LicenseComponentCont", list, ci.getName());
            }
        }
        return list;
    }

    void displayLicenseList() {
        feedback.output("INSTALL_LicensesToAccept");
        int idx = 1;
        for (String licId : licensesToAccept.keySet()) {
            List<MetadataLoader> loaders = licensesToAccept.get(licId);
            String list = formatComponentList(licId);
            feedback.output("INSTALL_AcceptLicenseComponents", loaders.get(0).getLicenseType(), list, idx);
            idx++;
        }
        feedback.outputPart("INSTALL_AcceptAllLicensesPrompt");
        state = State.LISTINPUT;
    }

    private static final Pattern ALL_NUMBERS = Pattern.compile("[0-9]+");

    boolean isFinished() {
        return licensesToAccept.isEmpty();
    }

    void acceptAllLicenses() {
        for (String s : new ArrayList<>(licensesToAccept.keySet())) {
            acceptLicense(s);
        }
        licensesToAccept.clear();
    }

    boolean isYes(String userInput) {
        if (userInput == Feedback.AUTO_YES) {
            return true;
        }
        Pattern p = Pattern.compile(feedback.l10n("INSTALL_AcceptPromptResponseYes@"), Pattern.CASE_INSENSITIVE);
        return p.matcher(userInput).matches();
    }

    boolean isRead(String userInput) {
        Pattern p = Pattern.compile(feedback.l10n("INSTALL_AcceptPromptResponseRead@"), Pattern.CASE_INSENSITIVE);
        return p.matcher(userInput).matches();
    }

    int processUserInputForList() {
        String userInput = feedback.acceptLine(true);
        Pattern p = Pattern.compile(feedback.l10n("INSTALL_AcceptPromptResponseAbort@"), Pattern.CASE_INSENSITIVE);
        if (p.matcher(userInput).matches()) {
            throw new UserAbortException();
        }
        if (isYes(userInput)) {
            acceptAllLicenses();
            state = State.NONE;
            return 0;
        }

        if (!ALL_NUMBERS.matcher(userInput).matches()) {
            feedback.output("INSTALL_LicenseNumberInvalidEntry", licensesToAccept.size());
            return -1;
        }
        int n = Integer.parseInt(userInput);
        if (n < 0 || n > licensesToAccept.size()) {
            feedback.output("INSTALL_LicenseNumberOutOfRange", licensesToAccept.size());
            return -1;
        }
        return n;
    }

    protected void acceptLicense(String licenseId) {
        String licText;
        try {
            licText = loadLicenseText(licenseId);
        } catch (IOException ex) {
            throw feedback.failure("INSTALL_ErrorHandlingLicenses", ex, ex.getLocalizedMessage());
        }
        for (MetadataLoader ldr : licensesToAccept.get(licenseId)) {
            Boolean result = null;
            try {
                // first ask the metadata loader delegate.
                result = ldr.recordLicenseAccepted(ldr.getComponentInfo(), licenseId, licText, null);
            } catch (IOException ex) {
                feedback.error("WARN_LicenseNotRecorded", ex, licenseId, ex.getLocalizedMessage());
            }
            if (result == null) {
                localRegistry.acceptLicense(ldr.getComponentInfo(), licenseId, licText);
            }
        }
        licensesToAccept.remove(licenseId);
    }

    void displaySingleLicense() {
        String licId = licensesToAccept.keySet().iterator().next();
        MetadataLoader ldr = licensesToAccept.get(licId).get(0);
        String type = ldr.getLicenseType();
        String compList = formatComponentList(licId);
        feedback.output("INSTALL_AcceptLicense", compList, type);
        feedback.outputPart("INSTALL_AcceptSingleLicense");
        String input = feedback.acceptLine(true);

        if (isYes(input)) {
            acceptLicense(licId);
            return;
        } else if (isRead(input)) {
            displayLicenseId = licId;
            state = State.LICENSE;
        } else {
            throw new UserAbortException();
        }
    }

    void displayLicenseText() throws IOException {
        String text = loadLicenseText(displayLicenseId);
        feedback.verbatimOut(text, false);
        feedback.output("INSTALL_AcceptLicensePrompt");
        String input = feedback.acceptLine(true);

        if (isYes(input)) {
            acceptLicense(displayLicenseId);
            state = multiLicenses ? State.LIST : State.SINGLE;
        } else if (!multiLicenses) {
            throw new UserAbortException();
        } else {
            state = State.LIST;
        }
    }

    boolean isLicenseRemote(String licenseId) {
        MetadataLoader ldr = licensesToAccept.get(licenseId).get(0);
        String licPath = ldr.getLicensePath();
        return SystemUtils.isRemotePath(licPath); // NOI18N
    }

    /**
     * Loads license text from the archive.
     * 
     * @param licenseId
     * @return text of the license
     * @throws IOException
     */
    String loadLicenseText(String licenseId) throws IOException {
        MetadataLoader ldr = licensesToAccept.get(licenseId).get(0);
        if (isLicenseRemote(licenseId)) {
            return downloadLicenseText(licenseId, ldr);
        } else {
            return loadFileLicenseText(ldr);
        }
    }

    private Map<String, String> remoteLicenseContents = new HashMap<>();

    String downloadLicenseText(String id, MetadataLoader ldr) throws IOException {
        String c = remoteLicenseContents.get(id);
        if (c != null) {
            return c;
        }
        String t = ldr.getLicenseType();
        String label;

        if (!t.equals(ldr.getLicensePath())) {
            label = feedback.l10n("INSTALL_DownloadLicenseName", t);
        } else {
            label = feedback.l10n("INSTALL_DownloadLicenseFile");
        }
        c = String.join("\n", Files.readAllLines(
                        downloadLicenseText(label, ldr.getLicensePath()).toPath())); // NOI18N
        remoteLicenseContents.put(id, c);
        return c;
    }

    File downloadLicenseText(String label, String url) throws IOException {
        FileDownloader dn = new FileDownloader(
                        label,
                        new URL(url),
                        feedback);

        dn.download();
        return dn.getLocalFile();
    }

    String loadFileLicenseText(MetadataLoader ldr) throws IOException {
        // may require a download of the archive
        try (Archive a = ldr.getArchive()) {
            String licensePath = ldr.getLicensePath();
            Archive.FileEntry licenseEntry = null;

            for (Archive.FileEntry e : a) {
                String n = e.getName();
                if (n.startsWith("/")) {
                    n = n.substring(1);
                } else if (n.startsWith("./")) {
                    n = n.substring(2);
                }
                if (n.equals(licensePath)) {
                    licenseEntry = e;
                    break;
                }
            }

            if (licenseEntry == null) {
                throw new IOException(feedback.l10n("INSTALL_LicenseNotFound", licensePath));
            }

            try (InputStream es = a.getInputStream(licenseEntry);
                            InputStreamReader esr = new InputStreamReader(es, "UTF-8");
                            BufferedReader buf = new BufferedReader(esr)) {
                return buf.lines().collect(Collectors.joining("\n")); // NOI18N
            }
        }
    }

    void init() {
        filterAcceptedLicenses();
        state = multiLicenses ? State.LIST : State.SINGLE;
    }

    void run() {
        init();
        try {
            while (!isFinished()) {
                singleStep();
            }
        } catch (IOException ex) {
            throw feedback.failure("INSTALL_ErrorHandlingLicenses", ex, ex.getLocalizedMessage());
        }
    }

    void singleStep() throws IOException {
        switch (state) {
            case LISTINPUT:
                int choice = processUserInputForList();
                switch (choice) {
                    case -1:
                        break;
                    case 0:
                        state = State.NONE;
                        break;
                    default:
                        List<String> ids = new ArrayList<>(licensesToAccept.keySet());
                        displayLicenseId = ids.get(choice - 1);
                        state = State.LICENSE;
                }
                break;
            case SINGLE:
                displaySingleLicense();
                break;
            case LICENSE:
                displayLicenseText();
                break;
            case NONE:
                break;
            case LIST:
                displayLicenseList();
                break;
            default:
                throw new AssertionError(state.name());
        }
    }
}
