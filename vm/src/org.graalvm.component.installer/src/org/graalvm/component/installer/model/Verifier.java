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
package org.graalvm.component.installer.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.graalvm.component.installer.BundleConstants;
import static org.graalvm.component.installer.BundleConstants.GRAALVM_CAPABILITY;
import org.graalvm.component.installer.ComponentCollection;
import org.graalvm.component.installer.ComponentInstaller;
import org.graalvm.component.installer.DependencyException;
import org.graalvm.component.installer.Feedback;
import org.graalvm.component.installer.SuppressFBWarnings;
import org.graalvm.component.installer.Version;

public class Verifier {
    private final Feedback feedback;
    private final ComponentRegistry localRegistry;
    private final ComponentCollection catalog;
    private boolean silent;
    private boolean replaceComponents;
    private boolean ignoreRequirements;
    private boolean collectErrors;
    private boolean ignoreExisting;
    private boolean collectVersion;

    private Version.Match versionMatch;
    private Version minVersion;
    private List<DependencyException> errors = new ArrayList<>();

    public Verifier(Feedback feedback, ComponentRegistry registry, ComponentCollection catalog) {
        this.feedback = feedback.withBundle(ComponentInstaller.class);
        this.localRegistry = registry;
        this.catalog = catalog;
    }

    public boolean isCollectVersion() {
        return collectVersion;
    }

    public Verifier collectVersion(boolean collect) {
        this.collectVersion = collect;
        return this;
    }

    public Version getMinVersion() {
        return minVersion;
    }

    public boolean isSilent() {
        return silent;
    }

    public void setSilent(boolean silent) {
        this.silent = silent;
    }

    public Version.Match getVersionMatch() {
        return versionMatch;
    }

    public Verifier setVersionMatch(Version.Match versionMatch) {
        this.versionMatch = versionMatch;
        return this;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public boolean isCollectErrors() {
        return collectErrors;
    }

    public void setCollectErrors(boolean collectErrors) {
        this.collectErrors = collectErrors;
    }

    public boolean isIgnoreExisting() {
        return ignoreExisting;
    }

    public Verifier ignoreExisting(boolean ignore) {
        this.ignoreExisting = ignore;
        return this;
    }

    public boolean isReplaceComponents() {
        return replaceComponents;
    }

    public Verifier replaceComponents(boolean value) {
        this.replaceComponents = value;
        return this;
    }

    public boolean isIgnoreRequirements() {
        return ignoreRequirements;
    }

    public Verifier ignoreRequirements(boolean value) {
        this.ignoreRequirements = value;
        return this;
    }

    public Verifier collect(boolean value) {
        this.collectErrors = value;
        return this;
    }

    private void addOrThrow(DependencyException ex) {
        if (collectErrors) {
            errors.add(ex);
        } else {
            throw ex;
        }
    }

    public void printRequirements(ComponentInfo info) {
        if (isSilent()) {
            return;
        }
        Map<String, String> requiredCaps = info.getRequiredGraalValues();
        Map<String, String> graalCaps = localRegistry.getGraalCapabilities();

        if (feedback.verboseOutput("VERIFY_VerboseCheckRequirements", catalog.shortenComponentId(info), info.getName(), info.getVersionString())) {
            List<String> keys = new ArrayList<>(requiredCaps.keySet());
            Collections.sort(keys);
            String none = feedback.l10n("VERIFY_VerboseCapabilityNone");
            for (String s : keys) {
                String v = graalCaps.get(s);
                feedback.verboseOutput("VERIFY_VerboseCapability", localRegistry.localizeCapabilityName(s), requiredCaps.get(s), v == null ? none : v);
            }
        }
    }

    public List<DependencyException> getErrors() {
        return errors;
    }

    public boolean shouldInstall(ComponentInfo componentInfo) {
        if (replaceComponents) {
            return true;
        }
        ComponentInfo existing = localRegistry.findComponent(componentInfo.getId());
        return existing == null ||
                        existing.getVersion().compareTo(componentInfo.getVersion()) < 0;
    }

    @SuppressWarnings("StringEquality")
    @SuppressFBWarnings(value = "ES_COMPARING_STRINGS_WITH_EQ", justification = "intentional comparison of strings using ==")
    public Verifier validateRequirements(ComponentInfo componentInfo) {
        errors.clear();
        // check the component is not in the registry
        ComponentInfo existing = localRegistry.findComponent(componentInfo.getId());
        if (existing != null) {
            if (existing.getVersion().compareTo(componentInfo.getVersion()) >= 0) {
                if (!ignoreExisting && !replaceComponents) {
                    addOrThrow(new DependencyException.Conflict(
                                    existing.getId(), componentInfo.getVersionString(), existing.getVersionString(),
                                    feedback.l10n("VERIFY_ComponentExists",
                                                    existing.getName(), existing, existing.getVersionString())));
                }
            }
        }
        if (ignoreRequirements) {
            return this;
        }
        Map<String, String> requiredCaps = componentInfo.getRequiredGraalValues();
        Map<String, String> graalCaps = localRegistry.getGraalCapabilities();
        boolean verbose = feedback.verboseOutput(null);
        if (!isSilent() && verbose) {
            feedback.verboseOutput("VERIFY_VerboseCheckRequirements", catalog.shortenComponentId(componentInfo), componentInfo.getName(), componentInfo.getVersionString());
            List<String> keys = new ArrayList<>(requiredCaps.keySet());
            Collections.sort(keys);
            String none = feedback.l10n("VERIFY_VerboseCapabilityNone");
            for (String s : keys) {
                String v = graalCaps.get(s);
                feedback.verboseOutput("VERIFY_VerboseCapability", localRegistry.localizeCapabilityName(s), requiredCaps.get(s), v == null ? none : v);
            }
        }

        for (String s : requiredCaps.keySet()) {
            String reqVal = requiredCaps.get(s);
            String graalVal = graalCaps.get(s);
            boolean matches;
            if (BundleConstants.GRAAL_VERSION.equals(s)) {
                if (versionMatch != null) {
                    Version cv = Version.fromString(
                                    reqVal.toLowerCase());
                    matches = versionMatch.test(cv);
                } else {
                    Version rv = Version.fromString(reqVal);
                    Version gv = Version.fromString(graalVal);
                    matches = rv.installVersion().equals(gv.installVersion());
                }
                if (!matches) {
                    Version rq = Version.fromString(reqVal);
                    Version gv = localRegistry.getGraalVersion();
                    int n = gv.compareTo(rq);
                    if (n > 0) {
                        addOrThrow(new DependencyException.Mismatch(
                                        GRAALVM_CAPABILITY,
                                        s, reqVal, graalVal,
                                        feedback.l10n("VERIFY_ObsoleteGraalVM",
                                                        componentInfo.getName(), reqVal, gv.displayString())));
                    } else if (collectVersion) {
                        minVersion = rq;
                    } else {
                        addOrThrow(new DependencyException.Mismatch(
                                        GRAALVM_CAPABILITY,
                                        s, reqVal, graalVal,
                                        feedback.l10n("VERIFY_UpdateGraalVM",
                                                        componentInfo.getName(), reqVal, gv.displayString())));
                    }
                }
                continue;
            } else {
                matches = matches(reqVal, graalVal);
            }
            if (!matches) {
                String val = graalVal != null ? graalVal : feedback.l10n("VERIFY_CapabilityMissing");
                addOrThrow(new DependencyException.Mismatch(
                                componentInfo.getId(),
                                s, reqVal, graalVal,
                                feedback.l10n("VERIFY_Dependency_Failed",
                                                componentInfo.getName(), localRegistry.localizeCapabilityName(s), reqVal, val)));
            }
        }
        return this;
    }

    private static boolean matches(String reqVal, String graalVal) {
        return !((reqVal != graalVal) && (reqVal == null || graalVal == null || (reqVal.replace('-', '_').compareToIgnoreCase(graalVal.replace('-', '_')) != 0)));
    }

}
