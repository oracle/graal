package com.oracle.svm.hosted.analysis.ai.example.access.inter;

import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPath;
import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPathBase;
import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPathConstants;
import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPathMap;
import com.oracle.svm.hosted.analysis.ai.domain.access.ClassAccessPathBase;
import com.oracle.svm.hosted.analysis.ai.domain.numerical.IntInterval;
import com.oracle.svm.hosted.analysis.ai.fixpoint.state.AbstractState;
import com.oracle.svm.hosted.analysis.ai.log.AbstractInterpretationLogger;
import com.oracle.svm.hosted.analysis.ai.log.LoggerVerbosity;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.ValueNode;

import java.util.List;

public class AccessPathIntervalSummary implements Summary<AccessPathMap<IntInterval>> {
    private final AccessPathMap<IntInterval> preCondition;
    private final AccessPathMap<IntInterval> postCondition;
    private final Invoke invoke;

    public AccessPathIntervalSummary(AccessPathMap<IntInterval> preCondition, Invoke invoke) {
        this.preCondition = preCondition;
        this.invoke = invoke;
        this.postCondition = new AccessPathMap<>(new IntInterval());
    }

    @Override
    public Invoke getInvoke() {
        return invoke;
    }

    @Override
    public AccessPathMap<IntInterval> getPreCondition() {
        return preCondition;
    }

    @Override
    public AccessPathMap<IntInterval> getPostCondition() {
        return postCondition;
    }

    @Override
    public boolean subsumesSummary(Summary<AccessPathMap<IntInterval>> other) {
        if (!(other instanceof AccessPathIntervalSummary otherAccessPathIntervalSummary)) {
            return false;
        }
        if (!(invoke.getTargetMethod().equals(other.getInvoke().getTargetMethod()))) {
            return false;
        }

        return preCondition.leq(otherAccessPathIntervalSummary.preCondition);
    }

    @Override
    public void finalizeSummary(AbstractState<AccessPathMap<IntInterval>> calleeAbstractState) {
        AccessPathMap<IntInterval> calleePostCondition = calleeAbstractState.getReturnDomain();
        var logger = AbstractInterpretationLogger.getInstance();
        logger.log("CalleePostCondition: " + calleePostCondition, LoggerVerbosity.DEBUG);
        if (calleePostCondition.isBot()) {
            postCondition.setToBot();
            return;
        }

        if (calleePostCondition.isTop()) {
            postCondition.setToTop();
            return;
        }

        /*
          This method should prepare the abstract context to be propagated back to the caller.
          We need to remove all the parts that can't escape back to the caller.
          We will only keep: 1. Objects that were passed as arguments to the callee, and all access paths that have these objects as their base.
                             2. Static fields that were accessible in the callee
                             3. Object that is returned from the callee ( if the return type is an object ) and all access paths with the returned object as their base.
                             4. IntInterval that is returned from the callee ( if the return type is an int )
         */

        /* Ad 1. */
        NodeInputList<ValueNode> args = invoke.callTarget().arguments();
        for (int i = 0; i < args.size(); i++) {
            ValueNode arg = args.get(i);
            if (!arg.getStackKind().isObject()) {
                continue;
            }

            /* Get all access paths with the base provided as an argument */
            List<AccessPath> accessPathsWithBase = calleePostCondition.getAccessPathsWithBasePrefix(AccessPathConstants.PARAM_PREFIX + i);
            for (AccessPath accessPath : accessPathsWithBase) {
                IntInterval value = calleePostCondition.get(accessPath); /* Get the value that was computed in the method body */
                this.postCondition.put(accessPath, value);
            }
        }

        /* Ad 2. */
        for (AccessPath accessPath : calleePostCondition.getValue().getMap().keySet()) {
            if (!(accessPath.getBase() instanceof ClassAccessPathBase)) {
                continue;
            }
            IntInterval value = calleePostCondition.get(accessPath);
            this.postCondition.put(accessPath, value);
        }

        /* Ad 3. */
        if (invoke.getTargetMethod().getSignature().getReturnKind().isObject()) {
            List<AccessPath> accessPaths = calleePostCondition.getAccessPathsWithBasePrefix(AccessPathConstants.RETURN_PREFIX);
            for (AccessPath accessPath : accessPaths) {
                IntInterval value = calleePostCondition.get(accessPath);
                this.postCondition.put(accessPath, value);
            }
        }

        /* Ad 4. */
        if (invoke.getTargetMethod().getSignature().getReturnKind().isNumericInteger()) {
            /* We retrieve the return# access path -> we will have only one, retrieve its value */
            List<AccessPath> accessPaths = calleePostCondition.getAccessPathsWithBasePrefix(AccessPathConstants.RETURN_PREFIX);
            AccessPath path = accessPaths.getFirst();
            this.postCondition.put(path, calleePostCondition.get(path));
        }
    }

    @Override
    public AccessPathMap<IntInterval> applySummary(AccessPathMap<IntInterval> domain) {
        AccessPathMap<IntInterval> mergedEnv = new AccessPathMap<>(new IntInterval());
        if (domain.isTop()) {
            mergedEnv.setToTop();
            return mergedEnv;
        }

        /* We rename all the prefixes from access paths in the post-condition, since we will be returning them to the caller */
        AccessPathMap<IntInterval> renamedEnv = new AccessPathMap<>(new IntInterval());
        for (AccessPath accessPath : this.postCondition.getValue().getMap().keySet()) {
            IntInterval value = this.postCondition.get(accessPath);
            AccessPathBase newBase;
            if (accessPath.getBase().toString().startsWith(AccessPathConstants.PARAM_PREFIX)) {
                newBase = accessPath.getBase().removePrefix(AccessPathConstants.PARAM_PREFIX + "\\d+");
            } else if (accessPath.getBase().toString().startsWith(AccessPathConstants.RETURN_PREFIX)) {
                newBase = accessPath.getBase().removePrefix(AccessPathConstants.RETURN_PREFIX);
            } else {
                newBase = accessPath.getBase();
            }
            AccessPath newAccessPath = new AccessPath(newBase).appendAccesses(accessPath.getElements());
            renamedEnv.put(newAccessPath, value);
        }


        for (AccessPath path : domain.getAccessPaths()) {
            if (renamedEnv.containsAccessPath(path)) {
                IntInterval renamedValue = renamedEnv.get(path);
                mergedEnv.put(path, renamedValue);
            } else {
                mergedEnv.put(path, domain.get(path));
            }
        }

        // Add paths that are only in renamedEnv
        for (AccessPath path : renamedEnv.getAccessPaths()) {
            if (!domain.getValue().getMap().containsKey(path)) {
                mergedEnv.put(path, renamedEnv.get(path));
            }
        }

        return mergedEnv;
    }
}
