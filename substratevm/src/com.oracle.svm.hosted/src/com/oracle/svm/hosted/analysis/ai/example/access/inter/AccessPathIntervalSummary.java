package com.oracle.svm.hosted.analysis.ai.example.access.inter;

import com.oracle.svm.hosted.analysis.ai.domain.EnvironmentDomain;
import com.oracle.svm.hosted.analysis.ai.domain.IntInterval;
import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPath;
import com.oracle.svm.hosted.analysis.ai.domain.access.AccessPathBase;
import com.oracle.svm.hosted.analysis.ai.domain.access.ClassAccessPathBase;
import com.oracle.svm.hosted.analysis.ai.summary.Summary;
import jdk.graal.compiler.graph.NodeInputList;
import jdk.graal.compiler.nodes.Invoke;
import jdk.graal.compiler.nodes.ValueNode;

import java.util.List;

public class AccessPathIntervalSummary implements Summary<EnvironmentDomain<IntInterval>> {
    private final EnvironmentDomain<IntInterval> preCondition;
    private final EnvironmentDomain<IntInterval> postCondition;
    private final Invoke invoke;

    public AccessPathIntervalSummary(EnvironmentDomain<IntInterval> preCondition, Invoke invoke) {
        this.preCondition = preCondition;
        this.invoke = invoke;
        this.postCondition = new EnvironmentDomain<>(new IntInterval());
    }

    @Override
    public Invoke getInvoke() {
        return invoke;
    }

    @Override
    public EnvironmentDomain<IntInterval> getPreCondition() {
        return preCondition;
    }

    @Override
    public EnvironmentDomain<IntInterval> getPostCondition() {
        return postCondition;
    }

    @Override
    public boolean subsumesSummary(Summary<EnvironmentDomain<IntInterval>> other) {
        if (!(other instanceof AccessPathIntervalSummary otherAccessPathIntervalSummary)) {
            return false;
        }
        if (!(invoke.getTargetMethod().equals(other.getInvoke().getTargetMethod()))) {
            return false;
        }

        return preCondition.leq(otherAccessPathIntervalSummary.preCondition);
    }

    @Override
    public void finalizeSummary(EnvironmentDomain<IntInterval> calleePostCondition) {
        /*
          This method should prepare the abstract context to be propagated back to the caller.
          We need to remove all the parts that can't escape back to the caller.
          We will only keep: 1. Objects that were passed as arguments to the callee, and all access paths that have these objects as their base.
                             2. Static fields that were accessible in the callee ( they could have changed in the method )
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
            List<AccessPath> accessPathsWithBase = calleePostCondition.getAccessPathsWithBasePrefix("param" + i);
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
            List<AccessPath> accessPaths = calleePostCondition.getAccessPathsWithBasePrefix("return#");
            for (AccessPath accessPath : accessPaths) {
                IntInterval value = calleePostCondition.get(accessPath);
                this.postCondition.put(accessPath, value);
            }
        }

        /* Ad 4. */
        if (invoke.getTargetMethod().getSignature().getReturnKind().isPrimitive()) {
            this.postCondition.setExprValue(preCondition.getExprValue());
        }
    }

    @Override
    public EnvironmentDomain<IntInterval> applySummary(EnvironmentDomain<IntInterval> domain) {
        /* We rename all the prefixes from access paths in the post-condition, since we will be returning them to the caller */
        EnvironmentDomain<IntInterval> renamedEnv = new EnvironmentDomain<>(new IntInterval());
        for (AccessPath accessPath : this.postCondition.getValue().getMap().keySet()) {
            IntInterval value = this.postCondition.get(accessPath);
            AccessPathBase newBase;
            if (accessPath.getBase().toString().startsWith("param")) {
                newBase = accessPath.getBase().removePrefix("param\\d+");
            } else if (accessPath.getBase().toString().startsWith("return#")) {
                newBase = accessPath.getBase().removePrefix("return#");
            } else {
                newBase = accessPath.getBase();
            }
            AccessPath newAccessPath = new AccessPath(newBase).appendAccesses(accessPath.getElements());
            renamedEnv.put(newAccessPath, value);
        }

        EnvironmentDomain<IntInterval> mergedEnv = new EnvironmentDomain<>(new IntInterval());

        for (AccessPath path : domain.getValue().getMap().keySet()) {
            if (renamedEnv.getValue().getMap().containsKey(path)) {
                // Path exists in both - use the more precise value from the callee
                IntInterval renamedValue = renamedEnv.get(path);
                mergedEnv.put(path, renamedValue);
            } else {
                // Path only in caller - keep it
                mergedEnv.put(path, domain.get(path));
            }
        }

        // Add paths that are only in renamedEnv
        for (AccessPath path : renamedEnv.getValue().getMap().keySet()) {
            if (!domain.getValue().getMap().containsKey(path)) {
                mergedEnv.put(path, renamedEnv.get(path));
            }
        }

        // If expression values exist in either, merge them
        if (domain.hasExprValue() || renamedEnv.hasExprValue()) {
            if (domain.hasExprValue() && renamedEnv.hasExprValue()) {
                mergedEnv.setExprValue(domain.getExprValue().meet(renamedEnv.getExprValue()));
            } else if (domain.hasExprValue()) {
                mergedEnv.setExprValue(domain.getExprValue());
            } else {
                mergedEnv.setExprValue(renamedEnv.getExprValue());
            }
        }

        return mergedEnv;
    }
}
