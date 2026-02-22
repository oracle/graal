package com.oracle.svm.hosted.analysis.ai.checker.appliers;

import com.oracle.svm.hosted.analysis.ai.checker.core.FactAggregator;
import com.oracle.svm.hosted.analysis.ai.checker.facts.FactKind;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

/**
 * Global registry for {@link FactApplier} instances grouped by {@link FactKind}.
 * <p>
 * Appliers are registered once and reused across methods. A suite can pick the
 * relevant appliers based on the facts present for a given method.
 */
public final class FactApplierRegistry {

    private static final Map<FactKind, List<FactApplier>> REGISTRY = new EnumMap<>(FactKind.class);

    static {
        register(FactKind.CONSTANT, new InvokeConstantFoldingApplier());
        register(FactKind.CONSTANT, new ConstantStampApplier());
        register(FactKind.CONDITION_TRUTH, new ConditionTruthnessApplier());
    }

    private FactApplierRegistry() {
    }

    /**
     * Registers an applier for the given fact kind. Appends preserve registration order.
     */
    public static synchronized void register(FactKind kind, FactApplier applier) {
        REGISTRY.computeIfAbsent(kind, k -> new ArrayList<>()).add(applier);
    }

    /**
     * Registers multiple appliers for a given fact kind.
     */
    public static synchronized void registerAll(FactKind kind, Collection<? extends FactApplier> appliers) {
        if (appliers == null || appliers.isEmpty()) {
            return;
        }
        REGISTRY.computeIfAbsent(kind, k -> new ArrayList<>()).addAll(appliers);
    }

    /**
     * Returns an unmodifiable view of all appliers in registration order across all kinds.
     */
    public static List<FactApplier> getAllAppliers() {
        List<FactApplier> all = new ArrayList<>();
        for (List<FactApplier> list : REGISTRY.values()) {
            all.addAll(list);
        }
        return Collections.unmodifiableList(all);
    }

    /**
     * Returns an unmodifiable list of appliers associated with a specific kind.
     */
    public static List<FactApplier> getAppliersFor(FactKind kind) {
        List<FactApplier> list = REGISTRY.get(kind);
        return list == null ? List.of() : Collections.unmodifiableList(list);

    }

    /**
     * Computes a de-duplicated, ordered list of appliers that are relevant to the facts
     * available in the provided aggregator. Preserves registration order per kind.
     */
    public static List<FactApplier> getRelevantAppliers(FactAggregator aggregator) {
        if (aggregator == null || aggregator.isEmpty()) {
            return List.of();
        }
        LinkedHashSet<FactApplier> result = new LinkedHashSet<>();
        for (FactKind kind : FactKind.values()) {
            if (!aggregator.factsOfKind(kind).isEmpty()) {
                result.addAll(getAppliersFor(kind));
            }
        }
        return List.copyOf(result);
    }
}
