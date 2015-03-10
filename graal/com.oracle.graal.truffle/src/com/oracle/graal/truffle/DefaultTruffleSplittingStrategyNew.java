/*
 * Copyright (c) 2013, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.truffle;

import java.util.*;

import com.oracle.truffle.api.*;
import com.oracle.truffle.api.nodes.*;

public class DefaultTruffleSplittingStrategyNew implements TruffleSplittingStrategy {

    private final int splitStart;
    private final OptimizedDirectCallNode call;
    private final boolean splittingEnabled;
    private boolean splittingForced;
    private TruffleStamp argumentStamp;

    public DefaultTruffleSplittingStrategyNew(OptimizedDirectCallNode call) {
        this.call = call;
        this.splitStart = TruffleCompilerOptions.TruffleSplittingStartCallCount.getValue();
        this.splittingEnabled = isSplittingEnabled(call);
        this.argumentStamp = DefaultTruffleStamp.getInstance();
        if (TruffleCompilerOptions.TruffleSplittingAggressive.getValue()) {
            splittingForced = true;
        }
    }

    private static boolean isSplittingEnabled(OptimizedDirectCallNode call) {
        if (!TruffleCompilerOptions.TruffleSplitting.getValue()) {
            return false;
        }
        if (!call.isCallTargetCloningAllowed()) {
            return false;
        }
        if (TruffleCompilerOptions.TruffleSplittingAggressive.getValue()) {
            return true;
        }
        int size = call.getCallTarget().countNonTrivialNodes();
        if (size > TruffleCompilerOptions.TruffleSplittingMaxCalleeSize.getValue()) {
            return false;
        }
        return true;
    }

    public void forceSplitting() {
        splittingForced = true;
    }

    public void beforeCall(Object[] arguments) {
        newSplitting(arguments);
    }

    public void afterCall(Object returnValue) {
    }

    private void newSplitting(Object[] arguments) {
        CompilerAsserts.neverPartOfCompilation();
        OptimizedCallTarget currentTarget = call.getCurrentCallTarget();

        if (splittingForced) {
            if (!call.isCallTargetCloned()) {
                call.installSplitCallTarget(currentTarget.cloneUninitialized());
            }
            return;
        }

        TruffleStamp oldStamp = argumentStamp;
        TruffleStamp newStamp = oldStamp;
        if (!oldStamp.isCompatible(arguments)) {
            newStamp = oldStamp.joinValue(arguments);
            assert newStamp != oldStamp;
        }

        int calls = call.getCallCount();

        if (oldStamp != newStamp || calls == splitStart || !call.getCurrentCallTarget().getArgumentStamp().equals(newStamp)) {
            currentTarget = runSplitIteration(oldStamp, newStamp, calls);
            currentTarget.mergeArgumentStamp(newStamp);
            argumentStamp = newStamp;
            assert call.getCurrentCallTarget().getArgumentStamp().equals(newStamp);
        }
    }

    private OptimizedCallTarget runSplitIteration(TruffleStamp oldProfile, TruffleStamp newProfile, int calls) {
        OptimizedCallTarget currentTarget = call.getCurrentCallTarget();
        if (!splittingEnabled || calls < splitStart) {
            return currentTarget;
        }

        OptimizedCallTarget target = call.getCallTarget();
        Map<TruffleStamp, OptimizedCallTarget> profiles = target.getSplitVersions();
        OptimizedCallTarget newTarget = currentTarget;

        if (!currentTarget.getArgumentStamp().equals(newProfile)) {
            if (target.getArgumentStamp().equals(newProfile)) {
                // the original target is compatible again.
                // -> we can use the original call target.
                newTarget = target;
            } else if (currentTarget.getKnownCallSiteCount() == 1 && currentTarget.getArgumentStamp().equals(oldProfile)) {
                // we are the only caller + the profile is not polluted by other call sites
                // -> reuse the currentTarget but update the profile if necessary
                newTarget = currentTarget;
                if (currentTarget.getSourceCallTarget() != null) {
                    profiles.remove(oldProfile);
                    profiles.put(newProfile, newTarget);
                }
            } else {
                newTarget = profiles.get(newProfile);
                if (newTarget == null) {
                    // in case no compatible target was found we need to split
                    newTarget = target.cloneUninitialized();
                    profiles.put(newProfile, newTarget);
                }
            }
        }

        call.installSplitCallTarget(newTarget);

        cleanup(currentTarget);
        return newTarget;
    }

    private static void cleanup(OptimizedCallTarget currentTarget) {
        if (currentTarget.getKnownCallSiteCount() == 0 && currentTarget.getSourceCallTarget() != null) {
            OptimizedCallTarget removed = currentTarget.getSourceCallTarget().getSplitVersions().remove(currentTarget.getArgumentStamp());
            if (removed != null) {
                disposeTarget(removed);
            }
        }
    }

    private static void disposeTarget(OptimizedCallTarget removed) {
        removed.getRootNode().accept(new NodeVisitor() {
            public boolean visit(Node node) {
                if (node instanceof OptimizedDirectCallNode) {
                    OptimizedDirectCallNode call = ((OptimizedDirectCallNode) node);
                    call.getCurrentCallTarget().decrementKnownCallSites();
                    cleanup(call.getCurrentCallTarget());
                }
                return true;
            }
        });

    }

}
