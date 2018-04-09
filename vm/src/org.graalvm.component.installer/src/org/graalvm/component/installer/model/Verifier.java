/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.graalvm.component.installer.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import static org.graalvm.component.installer.BundleConstants.GRAALVM_CAPABILITY;
import org.graalvm.component.installer.ComponentInstaller;
import org.graalvm.component.installer.DependencyException;
import org.graalvm.component.installer.Feedback;

/**
 *
 * @author sdedic
 */
public class Verifier {
    private final Feedback feedback;
    private final ComponentRegistry registry;
    private final ComponentInfo componentInfo;
    private boolean replaceComponents;
    private boolean ignoreRequirements;
    private boolean collectErrors;

    private List<DependencyException> errors = new ArrayList<>();

    public Verifier(Feedback feedback, ComponentRegistry registry, ComponentInfo componentInfo) {
        this.feedback = feedback.withBundle(ComponentInstaller.class);
        this.registry = registry;
        this.componentInfo = componentInfo;
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }

    public ComponentInfo getComponentInfo() {
        return componentInfo;
    }

    public boolean isReplaceComponents() {
        return replaceComponents;
    }

    public Verifier replaceComponents(boolean _replaceComponents) {
        this.replaceComponents = _replaceComponents;
        return this;
    }

    public boolean isIgnoreRequirements() {
        return ignoreRequirements;
    }

    public Verifier ignoreRequirements(boolean _ignoreRequirements) {
        this.ignoreRequirements = _ignoreRequirements;
        return this;
    }

    public Verifier collect(boolean _collectErrors) {
        this.collectErrors = _collectErrors;
        return this;
    }

    private void addOrThrow(DependencyException ex) {
        if (collectErrors) {
            errors.add(ex);
        } else {
            throw ex;
        }
    }

    public void printRequirements() {
        ComponentInfo info = getComponentInfo();
        Map<String, String> requiredCaps = info.getRequiredGraalValues();
        Map<String, String> graalCaps = registry.getGraalCapabilities();

        if (feedback.verboseOutput("VERIFY_VerboseCheckRequirements", info.getId(), info.getName(), info.getVersionString())) {
            List<String> keys = new ArrayList<>(requiredCaps.keySet());
            Collections.sort(keys);
            String none = feedback.l10n("VERIFY_VerboseCapabilityNone");
            for (String s : keys) {
                String v = graalCaps.get(s);
                feedback.verboseOutput("VERIFY_VerboseCapability", registry.localizeCapabilityName(s), requiredCaps.get(s), v == null ? none : v);
            }
        }
    }

    public List<DependencyException> getErrors() {
        return errors;
    }

    public Verifier validateRequirements() {
        // check the component is not in the registry
        ComponentInfo existing = registry.findComponent(componentInfo.getId());
        if (existing != null && !replaceComponents) {
            addOrThrow(new DependencyException.Conflict(
                            existing.getId(), componentInfo.getVersionString(), existing.getVersionString(),
                            feedback.l10n("VERIFY_ComponentExists",
                                            existing.getName(), existing.getId(), existing.getVersionString())));
        }
        if (ignoreRequirements) {
            return this;
        }
        ComponentInfo info = getComponentInfo();
        Map<String, String> requiredCaps = info.getRequiredGraalValues();
        Map<String, String> graalCaps = registry.getGraalCapabilities();

        if (feedback.verboseOutput("VERIFY_VerboseCheckRequirements", info.getId(), info.getName(), info.getVersionString())) {
            List<String> keys = new ArrayList<>(requiredCaps.keySet());
            Collections.sort(keys);
            String none = feedback.l10n("VERIFY_VerboseCapabilityNone");
            for (String s : keys) {
                String v = graalCaps.get(s);
                feedback.verboseOutput("VERIFY_VerboseCapability", registry.localizeCapabilityName(s), requiredCaps.get(s), v == null ? none : v);
            }
        }

        for (String s : requiredCaps.keySet()) {
            String reqVal = requiredCaps.get(s);
            String graalVal = graalCaps.get(s);

            if (!Objects.equals(graalVal, reqVal)) {
                String val = graalVal != null ? graalVal : feedback.l10n("VERIFY_CapabilityMissing");
                addOrThrow(new DependencyException.Mismatch(
                                GRAALVM_CAPABILITY,
                                s, reqVal, graalVal,
                                feedback.l10n("VERIFY_Dependency_Failed",
                                                info.getName(), registry.localizeCapabilityName(s), reqVal, val)));
            }
        }
        return this;
    }

}
