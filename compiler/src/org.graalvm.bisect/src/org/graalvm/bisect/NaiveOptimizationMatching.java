package org.graalvm.bisect;

import org.graalvm.bisect.core.ExperimentId;
import org.graalvm.bisect.core.optimization.Optimization;

import java.util.ArrayList;
import java.util.List;

public class NaiveOptimizationMatching implements OptimizationMatching {
    public List<ExtraOptimization> getExtraOptimizations() {
        return extraOptimizations;
    }

    private final ArrayList<ExtraOptimization> extraOptimizations = new ArrayList<>();

    static class ExtraOptimization {
        public ExperimentId getExperimentId() {
            return experimentId;
        }

        public Optimization getOptimization() {
            return optimization;
        }

        private final ExperimentId experimentId;
        private final Optimization optimization;

        ExtraOptimization(ExperimentId experimentId, Optimization optimization) {
            this.experimentId = experimentId;
            this.optimization = optimization;
        }

        @Override
        public boolean equals(Object object) {
            if (!(object instanceof ExtraOptimization)) {
                return false;
            }
            ExtraOptimization other = (ExtraOptimization) object;
            return experimentId == other.experimentId && optimization.equals(other.optimization);
        }
    }

    public void addExtraOptimization(Optimization optimization, ExperimentId experimentId) {
        ExtraOptimization extraOptimization = new ExtraOptimization(experimentId, optimization);
        extraOptimizations.add(extraOptimization);
    }
}
