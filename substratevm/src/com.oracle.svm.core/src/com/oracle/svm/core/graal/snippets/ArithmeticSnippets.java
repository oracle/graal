/*
 * Copyright (c) 2012, 2023, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.svm.core.graal.snippets;

import static com.oracle.svm.core.util.VMError.shouldNotReachHereUnexpectedInput;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.SLOW_PATH_PROBABILITY;
import static jdk.graal.compiler.nodes.extended.BranchProbabilityNode.probability;

import java.util.Map;

import com.oracle.svm.core.config.ConfigurationValues;
import com.oracle.svm.core.config.ObjectLayout;

import jdk.graal.compiler.api.replacements.Snippet;
import jdk.graal.compiler.api.replacements.Snippet.ConstantParameter;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.Node.NodeIntrinsic;
import jdk.graal.compiler.graph.NodeClass;
import jdk.graal.compiler.nodeinfo.NodeInfo;
import jdk.graal.compiler.nodes.DeoptimizeNode;
import jdk.graal.compiler.nodes.FrameState;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.UnreachableNode;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.IntegerDivRemNode;
import jdk.graal.compiler.nodes.calc.SignedDivNode;
import jdk.graal.compiler.nodes.calc.SignedRemNode;
import jdk.graal.compiler.nodes.calc.UnsignedDivNode;
import jdk.graal.compiler.nodes.calc.UnsignedRemNode;
import jdk.graal.compiler.nodes.extended.GuardingNode;
import jdk.graal.compiler.nodes.spi.LoweringTool;
import jdk.graal.compiler.options.OptionValues;
import jdk.graal.compiler.phases.util.Providers;
import jdk.graal.compiler.replacements.SnippetTemplate;
import jdk.graal.compiler.replacements.SnippetTemplate.Arguments;
import jdk.graal.compiler.replacements.SnippetTemplate.SnippetInfo;
import jdk.graal.compiler.replacements.Snippets;
import jdk.vm.ci.meta.DeoptimizationAction;
import jdk.vm.ci.meta.DeoptimizationReason;
import jdk.vm.ci.meta.JavaKind;

public abstract class ArithmeticSnippets extends SubstrateTemplates implements Snippets {

    @Snippet
    protected static int idivSnippet(int x, int y, @ConstantParameter boolean needsZeroCheck, @ConstantParameter boolean needsBoundsCheck) {
        if (needsZeroCheck) {
            zeroCheck(y);
        }
        if (needsBoundsCheck) {
            if (probability(SLOW_PATH_PROBABILITY, x == Integer.MIN_VALUE)) {
                if (probability(SLOW_PATH_PROBABILITY, y == -1)) {
                    return Integer.MIN_VALUE;
                }
            }
        }
        return safeDiv(x, y);
    }

    @Snippet
    protected static long ldivSnippet(long x, long y, @ConstantParameter boolean needsZeroCheck, @ConstantParameter boolean needsBoundsCheck) {
        if (needsZeroCheck) {
            zeroCheck(y);
        }
        if (needsBoundsCheck) {
            if (probability(SLOW_PATH_PROBABILITY, x == Long.MIN_VALUE)) {
                if (probability(SLOW_PATH_PROBABILITY, y == -1)) {
                    return Long.MIN_VALUE;
                }
            }
        }

        return safeDiv(x, y);
    }

    @Snippet
    protected static int iremSnippet(int x, int y, @ConstantParameter boolean needsZeroCheck, @ConstantParameter boolean needsBoundsCheck) {
        if (needsZeroCheck) {
            zeroCheck(y);
        }
        if (needsBoundsCheck) {
            if (probability(SLOW_PATH_PROBABILITY, x == Integer.MIN_VALUE)) {
                if (probability(SLOW_PATH_PROBABILITY, y == -1)) {
                    return 0;
                }
            }
        }

        return safeRem(x, y);
    }

    @Snippet
    protected static long lremSnippet(long x, long y, @ConstantParameter boolean needsZeroCheck, @ConstantParameter boolean needsBoundsCheck) {
        if (needsZeroCheck) {
            zeroCheck(y);
        }
        if (needsBoundsCheck) {
            if (probability(SLOW_PATH_PROBABILITY, x == Long.MIN_VALUE)) {
                if (probability(SLOW_PATH_PROBABILITY, y == -1)) {
                    return 0;
                }
            }
        }

        return safeRem(x, y);
    }

    @Snippet
    protected static int uidivSnippet(int x, int y, @ConstantParameter boolean needsZeroCheck) {
        if (needsZeroCheck) {
            zeroCheck(y);
        }
        return safeUDiv(x, y);
    }

    @Snippet
    protected static long uldivSnippet(long x, long y, @ConstantParameter boolean needsZeroCheck) {
        if (needsZeroCheck) {
            zeroCheck(y);
        }
        return safeUDiv(x, y);
    }

    @Snippet
    protected static int uiremSnippet(int x, int y, @ConstantParameter boolean needsZeroCheck) {
        if (needsZeroCheck) {
            zeroCheck(y);
        }
        return safeURem(x, y);
    }

    @Snippet
    protected static long ulremSnippet(long x, long y, @ConstantParameter boolean needsZeroCheck) {
        if (needsZeroCheck) {
            zeroCheck(y);
        }
        return safeURem(x, y);
    }

    private static void zeroCheck(int val) {
        if (probability(SLOW_PATH_PROBABILITY, val == 0)) {
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.ArithmeticException);
            throw UnreachableNode.unreachable();
        }
    }

    private static void zeroCheck(long val) {
        if (probability(SLOW_PATH_PROBABILITY, val == 0)) {
            DeoptimizeNode.deopt(DeoptimizationAction.None, DeoptimizationReason.ArithmeticException);
            throw UnreachableNode.unreachable();
        }
    }

    @NodeIntrinsic(SafeSignedDivNode.class)
    private static native int safeDiv(int x, int y);

    @NodeIntrinsic(SafeSignedDivNode.class)
    private static native long safeDiv(long x, long y);

    @NodeIntrinsic(SafeSignedRemNode.class)
    private static native int safeRem(int x, int y);

    @NodeIntrinsic(SafeSignedRemNode.class)
    private static native long safeRem(long x, long y);

    @NodeIntrinsic(SafeUnsignedDivNode.class)
    private static native int safeUDiv(int x, int y);

    @NodeIntrinsic(SafeUnsignedDivNode.class)
    private static native long safeUDiv(long x, long y);

    @NodeIntrinsic(SafeUnsignedRemNode.class)
    private static native int safeURem(int x, int y);

    @NodeIntrinsic(SafeUnsignedRemNode.class)
    private static native long safeURem(long x, long y);

    private final ObjectLayout layout;

    private final SnippetInfo idiv;
    private final SnippetInfo ldiv;
    private final SnippetInfo irem;
    private final SnippetInfo lrem;
    private final SnippetInfo uidiv;
    private final SnippetInfo uldiv;
    private final SnippetInfo uirem;
    private final SnippetInfo ulrem;

    @SuppressWarnings("this-escape")
    protected ArithmeticSnippets(OptionValues options, Providers providers,
                    Map<Class<? extends Node>, NodeLoweringProvider<?>> lowerings, boolean divRemNeedsSignedBoundsCheck) {
        super(options, providers);
        this.layout = ConfigurationValues.getObjectLayout();

        idiv = snippet(providers, ArithmeticSnippets.class, "idivSnippet");
        ldiv = snippet(providers, ArithmeticSnippets.class, "ldivSnippet");
        irem = snippet(providers, ArithmeticSnippets.class, "iremSnippet");
        lrem = snippet(providers, ArithmeticSnippets.class, "lremSnippet");
        uidiv = snippet(providers, ArithmeticSnippets.class, "uidivSnippet");
        uldiv = snippet(providers, ArithmeticSnippets.class, "uldivSnippet");
        uirem = snippet(providers, ArithmeticSnippets.class, "uiremSnippet");
        ulrem = snippet(providers, ArithmeticSnippets.class, "ulremSnippet");

        lowerings.put(SignedDivNode.class, new DivRemLowering(divRemNeedsSignedBoundsCheck));
        lowerings.put(SignedRemNode.class, new DivRemLowering(divRemNeedsSignedBoundsCheck));
        lowerings.put(UnsignedDivNode.class, new DivRemLowering(divRemNeedsSignedBoundsCheck));
        lowerings.put(UnsignedRemNode.class, new DivRemLowering(divRemNeedsSignedBoundsCheck));
        lowerings.put(SafeSignedDivNode.class, new IdentityLowering());
        lowerings.put(SafeSignedRemNode.class, new IdentityLowering());
        lowerings.put(SafeUnsignedDivNode.class, new IdentityLowering());
        lowerings.put(SafeUnsignedRemNode.class, new IdentityLowering());
    }

    protected class DivRemLowering implements NodeLoweringProvider<IntegerDivRemNode> {
        private final boolean needsSignedBoundsCheck;

        protected DivRemLowering(boolean needsSignedBoundsCheck) {
            this.needsSignedBoundsCheck = needsSignedBoundsCheck;
        }

        @Override
        public void lower(IntegerDivRemNode node, LoweringTool tool) {
            if (tool.getLoweringStage() != LoweringTool.StandardLoweringStage.LOW_TIER) {
                // wait for more precise stamp information
                return;
            }
            assert node.getStackKind() == JavaKind.Int || node.getStackKind() == JavaKind.Long;
            SnippetInfo snippet;
            if (node instanceof SignedDivNode) {
                snippet = node.getStackKind() == JavaKind.Int ? idiv : ldiv;
            } else if (node instanceof SignedRemNode) {
                snippet = node.getStackKind() == JavaKind.Int ? irem : lrem;
            } else if (node instanceof UnsignedDivNode) {
                snippet = node.getStackKind() == JavaKind.Int ? uidiv : uldiv;
            } else if (node instanceof UnsignedRemNode) {
                snippet = node.getStackKind() == JavaKind.Int ? uirem : ulrem;
            } else {
                throw shouldNotReachHereUnexpectedInput(node); // ExcludeFromJacocoGeneratedReport
            }
            Arguments args = new Arguments(snippet, node.graph(), tool.getLoweringStage());
            args.add("x", node.getX());
            args.add("y", node.getY());

            IntegerStamp yStamp = (IntegerStamp) node.getY().stamp(NodeView.DEFAULT);
            boolean needsZeroCheck = node.canDeoptimize() && (node.getZeroGuard() == null && yStamp.contains(0));
            args.add("needsZeroCheck", needsZeroCheck);
            if (node instanceof SignedDivNode || node instanceof SignedRemNode) {
                args.add("needsBoundsCheck", needsSignedBoundsCheck);
            }

            template(tool, node, args).instantiate(tool.getMetaAccess(), node, SnippetTemplate.DEFAULT_REPLACER, args);
        }
    }

    public static class IdentityLowering implements NodeLoweringProvider<Node> {
        @Override
        public void lower(Node node, LoweringTool tool) {
            // do nothing and leave node unchanged.
        }
    }
}

@NodeInfo
class SafeSignedDivNode extends SignedDivNode {

    public static final NodeClass<SafeSignedDivNode> TYPE = NodeClass.create(SafeSignedDivNode.class);

    protected SafeSignedDivNode(ValueNode x, ValueNode y) {
        /*
         * All "safe" division nodes use null as the zeroCheck guard. Since the safe nodes are still
         * fixed and no code in the backend depends on the guard information, so having an explicit
         * guard is not necessary. Passing in the guard via a snippet would be tricky (it is not a
         * GuardingNode but an arbitrary ValueNode until after snippet instantiation).
         */
        super(TYPE, x, y, null);
    }

    @Override
    protected SignedDivNode createWithInputs(ValueNode forX, ValueNode forY, GuardingNode forZeroCheck, FrameState forStateBefore) {
        assert forZeroCheck == null;
        // note that stateBefore is irrelevant, as this "safe" variant will not deoptimize
        return new SafeSignedDivNode(forX, forY);
    }

    @Override
    public boolean canFloat() {
        return false;
    }

    @Override
    public boolean canDeoptimize() {
        /*
         * All checks have been done. Returning false is the indicator that no FrameState is
         * necessary anymore for the node.
         */
        return false;
    }
}

@NodeInfo
class SafeSignedRemNode extends SignedRemNode {
    public static final NodeClass<SafeSignedRemNode> TYPE = NodeClass.create(SafeSignedRemNode.class);

    protected SafeSignedRemNode(ValueNode x, ValueNode y) {
        super(TYPE, x, y, null);
    }

    @Override
    protected SignedRemNode createWithInputs(ValueNode forX, ValueNode forY, GuardingNode forZeroCheck) {
        assert forZeroCheck == null;
        return new SafeSignedRemNode(forX, forY);
    }

    @Override
    public boolean canFloat() {
        return false;
    }

    @Override
    public boolean canDeoptimize() {
        /*
         * All checks have been done. Returning false is the indicator that no FrameState is
         * necessary anymore for the node.
         */
        return false;
    }
}

@NodeInfo
class SafeUnsignedDivNode extends UnsignedDivNode {
    public static final NodeClass<SafeUnsignedDivNode> TYPE = NodeClass.create(SafeUnsignedDivNode.class);

    protected SafeUnsignedDivNode(ValueNode x, ValueNode y) {
        super(TYPE, x, y, null);
    }

    @Override
    public boolean canDeoptimize() {
        /*
         * All checks have been done. Returning false is the indicator that no FrameState is
         * necessary anymore for the node.
         */
        return false;
    }
}

@NodeInfo
class SafeUnsignedRemNode extends UnsignedRemNode {
    public static final NodeClass<SafeUnsignedRemNode> TYPE = NodeClass.create(SafeUnsignedRemNode.class);

    protected SafeUnsignedRemNode(ValueNode x, ValueNode y) {
        super(TYPE, x, y, null);
    }

    @Override
    public boolean canDeoptimize() {
        /*
         * All checks have been done. Returning false is the indicator that no FrameState is
         * necessary anymore for the node.
         */
        return false;
    }
}
