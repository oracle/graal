/*
 * Copyright (c) 2023, Oracle and/or its affiliates. All rights reserved.
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
package jdk.graal.compiler.core.test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jdk.graal.compiler.core.common.NumUtil;
import jdk.graal.compiler.core.common.calc.CanonicalCondition;
import jdk.graal.compiler.core.common.type.IntegerStamp;
import jdk.graal.compiler.core.common.type.PrimitiveStamp;
import jdk.graal.compiler.debug.Assertions;
import jdk.graal.compiler.debug.GraalError;
import jdk.graal.compiler.debug.TTY;
import jdk.graal.compiler.graph.Graph;
import jdk.graal.compiler.graph.Node;
import jdk.graal.compiler.graph.NodeSourcePosition;
import jdk.graal.compiler.nodes.AbstractBeginNode;
import jdk.graal.compiler.nodes.EndNode;
import jdk.graal.compiler.nodes.FixedNode;
import jdk.graal.compiler.nodes.IfNode;
import jdk.graal.compiler.nodes.InvokeWithExceptionNode;
import jdk.graal.compiler.nodes.LogicNode;
import jdk.graal.compiler.nodes.LoopBeginNode;
import jdk.graal.compiler.nodes.MergeNode;
import jdk.graal.compiler.nodes.NodeView;
import jdk.graal.compiler.nodes.ReturnNode;
import jdk.graal.compiler.nodes.StartNode;
import jdk.graal.compiler.nodes.StructuredGraph;
import jdk.graal.compiler.nodes.ValueNode;
import jdk.graal.compiler.nodes.calc.CompareNode;
import jdk.graal.compiler.nodes.calc.IntegerEqualsNode;
import jdk.graal.compiler.nodes.calc.IsNullNode;
import jdk.graal.compiler.nodes.java.LoadFieldNode;
import jdk.graal.compiler.nodes.java.MethodCallTargetNode;
import jdk.graal.compiler.nodes.spi.CoreProviders;
import jdk.graal.compiler.nodes.util.GraphUtil;
import jdk.graal.compiler.phases.util.GraphOrder;
import jdk.graal.compiler.util.EconomicHashMap;
import jdk.graal.compiler.util.EconomicHashSet;
import jdk.vm.ci.meta.JavaKind;
import jdk.vm.ci.meta.MetaAccessProvider;
import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * Verify assertion code usage in the GraalVM compiler and NI code base. Assertions are used in
 * defensive programming to guard against uncommon errors or verify invariants. When writing new
 * assertions think about if some invariant should be an assertion (which have to be enabled
 * explicitly) or a call to {@link GraalError#guarantee(boolean, String)} which is an always on
 * invariant.
 *
 * The rules for assertions in the GraalVM code base are the following:
 * <ul>
 *
 * <li>All calls to {@code assert someCondition: "Mandatory error message";} must have a proper
 * error message</li>
 *
 * <li>Assertions with trivial conditions that can be analyzed by looking at the source code:
 * Trivial conditions include {@code assert a!=null;} null checks and comparisons against constants
 * checking a != condition and boolean assertions. See
 * {@link #trivialCondition(IfNode, Collection, boolean)} for details.</li>
 *
 * <li>Statically excluded methods that are known to produce correct and meaningful assertion error
 * messages: they are defined in {@link #propagateStaticExcludeList()}.</li>
 *
 * <li>Assertion checks that call another method: valid if the other method has assertion checks
 * with error messages. For example {@code assert myCall();} where the callee uses
 *
 * <pre>
 * static boolean myCall() {
 *     assert sth : "Must hold";
 *     return true;
 * }
 * </pre>
 *
 * assertions with error messages. This definition is transitive, i.e., the callee with the final
 * assertion message can be multiple calls down the call chain.</li>
 * </ul>
 *
 * Analysis works by processing various methods and recording their miss-use of assertions. If
 * assertions are missing messages but are calling other functions verification of these functions
 * is delayed. In a final step all methods on the call graph are analyzed and functions calling
 * methods in assertions without error messages are reported as errors if the callee methods do not
 * have assertion error messages themselves.
 *
 * To see more examples of allowed and disallowed assertion usage patterns check
 * {@link VerifyAssertionUsageTest}.
 */
public class VerifyAssertionUsage extends VerifyStringFormatterUsage {

    /**
     * Set to true to debug a problem reaching a fixpoint in {@link #postProcess}.
     */
    private final boolean log;

    /*
     * GR-49601: only check non-boolean assertion calls for now.
     */
    private final boolean verifyBooleanAssertionConditions;

    public static final String ENABLE_LOGGING_PROPERTY_NAME = "test.graal.assert.enableLog";

    public static final String ENABLE_BOOLEAN_ASSERTION_CONDITION_CHEKING = "test.graal.assert.enableBooleabConditions";

    public static final String ALL_PATHS_MUST_ASSERT_PROPERTY_NAME = "test.graal.assert.allPathsMustAssert";

    /**
     * Determine if all paths used during assertion checking in callees must have assertions used.
     * This is a strict rule we do not enforce at the moment but it should give full assertion
     * checking coverage on all paths. That is in patterns like this
     *
     * <pre>
     * void caller() {
     *     assert callee();
     * }
     *
     * boolean callee() {
     *     if (sth) {
     *         // no assert call
     *     } else {
     *         assert sthA;
     *     }
     *     return true;
     * }
     * </pre>
     *
     * we force that in all paths of callee there is an assertion check (a call to an assert). Which
     * in this example is violated by the true branch.
     */
    private final boolean allPathsMustAssert;

    /**
     * Meta-access to do all the necessary resolutions.
     */
    private final MetaAccessProvider metaAccess;

    /**
     * The {@code ResolvedJavaType} for the {@link AssertionError} type.
     */
    private final ResolvedJavaType assertionType;

    /**
     * The {@link AssertionError#AssertionError()} ctor for the assertion type without arguments.
     */
    private ResolvedJavaMethod emptyCtor;

    /**
     * The {@link AssertionError#AssertionError()} ctors for the assertion type with arguments.
     */
    private final Set<ResolvedJavaMethod> nonEmptyAssertionCtor = new EconomicHashSet<>();

    /**
     * All methods that are ignored when checking for missed assertion messages.
     */
    private final ArrayList<ResolvedJavaMethod> excludeAssertionCalls = new ArrayList<>();

    public VerifyAssertionUsage(MetaAccessProvider metaAccess) {
        this.metaAccess = metaAccess;
        assertionType = metaAccess.lookupJavaType(AssertionError.class);

        for (ResolvedJavaMethod ctor : assertionType.getDeclaredConstructors()) {
            if (ctor.getSignature().getParameterCount(false) == 0) {
                // can only be one empty ctor
                if (emptyCtor != null) {
                    throw new VerificationError("2 empty ctors for AssertionError type ....impossible %s %s ", emptyCtor, ctor);
                }
                emptyCtor = ctor;
            } else {
                nonEmptyAssertionCtor.add(ctor);
            }
        }

        log = Boolean.getBoolean(ENABLE_LOGGING_PROPERTY_NAME);
        verifyBooleanAssertionConditions = Boolean.getBoolean(ENABLE_BOOLEAN_ASSERTION_CONDITION_CHEKING);

        propagateStaticExcludeList();

        allPathsMustAssert = Boolean.getBoolean(ALL_PATHS_MUST_ASSERT_PROPERTY_NAME);
    }

    private void getMethodFromType(Class<?> c, String methodName, ArrayList<ResolvedJavaMethod> result) {
        for (ResolvedJavaMethod m : metaAccess.lookupJavaType(c).getDeclaredMethods()) {
            if (m.getName().equals(methodName)) {
                result.add(m);
            }
        }
    }

    private void propagateStaticExcludeList() {
        getMethodFromType(Assertions.class, "assertionsEnabled", excludeAssertionCalls);
        getMethodFromType(Assertions.class, "detailedAssertionsEnabled", excludeAssertionCalls);
        getMethodFromType(GraphOrder.class, "assertNonCyclicGraph", excludeAssertionCalls);
        getMethodFromType(GraphOrder.class, "assertSchedulableGraph", excludeAssertionCalls);
        getMethodFromType(NumUtil.class, "assertPositiveDouble", excludeAssertionCalls);
        getMethodFromType(NumUtil.class, "assertFiniteDouble", excludeAssertionCalls);
        getMethodFromType(NumUtil.class, "assertNonNegativeInt", excludeAssertionCalls);
        getMethodFromType(NumUtil.class, "assertPositiveInt", excludeAssertionCalls);
        getMethodFromType(NumUtil.class, "assertNonNegativeLong", excludeAssertionCalls);
        getMethodFromType(NumUtil.class, "assertNonNegativeDouble", excludeAssertionCalls);
        getMethodFromType(NumUtil.class, "assertArrayLength", excludeAssertionCalls);
        getMethodFromType(Graph.class, "verify", excludeAssertionCalls);
        getMethodFromType(GraphUtil.class, "assertIsConstant", excludeAssertionCalls);
        getMethodFromType(Node.class, "isAlive", excludeAssertionCalls);
    }

    /**
     * All the infos for each method encountered during checking all Graal code. That is, methods
     * that are analyzed as well as JDK code used by Graal. {@link MethodInfo#root} determines if a
     * method was checked as a Graal method or not.
     */
    private Map<ResolvedJavaMethod, MethodInfo> methodInfos = Collections.synchronizedMap(new EconomicHashMap<>());
    /**
     * All calls to assertions without error messages - these will be checked for exclude lists and
     * else reported as errors.
     */
    private List<AssertionCall> assertionCallsWithoutMessage = Collections.synchronizedList(new ArrayList<>());
    /*
     * All calls to assertions with error messages - these will be checked for superfluous error
     * messages that can never trigger.
     */
    private List<AssertionCall> assertionCallsWithMessage = Collections.synchronizedList(new ArrayList<>());

    private MethodInfo getMethodInfo(ResolvedJavaMethod m) {
        if (methodInfos.containsKey(m)) {
            return methodInfos.get(m);
        }
        MethodInfo info = new MethodInfo(m);
        methodInfos.put(m, info);
        return info;
    }

    /**
     * Intern data structure to keep track of all the data necessary to verify assertion usage/users
     * for a certain method.
     */
    private static class MethodInfo {
        final ResolvedJavaMethod method;
        /**
         * Dynamic property - a method is a root if its visited during
         * {@link VerifyAssertionUsage#verify(StructuredGraph, CoreProviders)}.
         */
        boolean root;
        /**
         * Static property - for each return statement the method must return {@code true}.
         */
        String allPathsAssertionDominated;

        boolean allPathAssertionDominated() {
            return allPathsAssertionDominated == null && calleesToPatchAsAssertionToBeCorrect.isEmpty();
        }

        /**
         * Dynamic property - all callees seen when assessing if all paths of a method used as
         * assertion actually have an assertion. That is - all paths returning {@code true} must
         * also be dominated by an assertion if they are doing method calls.
         */
        Set<ResolvedJavaMethod> calleesToPatchAsAssertionToBeCorrect = new EconomicHashSet<>();

        /**
         * Static property - a call is used as return, that can be verified but that cannot be used
         * as indication for superfluous messages.
         */
        boolean returnIsACall;

        /**
         * Dynamic property - defines if this method behaves according to assertion rules of Graal -
         * that is - all assertion calls are with messages, not superfluous, if they are missing an
         * assertion they are calling a method that itself has all the checks etc. So for example
         *
         * <pre>
         * void use() {
         *     assert callee(); // no message
         * }
         *
         * boolean callee() {
         *     if (abcd()) { // this call needs to properly behave wrt to assertion messages, else we are
         *                   // risking that this returns false, but the caller returns true and no
         *                   // assertion was actually checked on that path
         *         return true;
         *     }
         *     return true;
         * }
         * </pre>
         */
        boolean correctAssertionMethod;

        /**
         * Dynamic property - defines if this method actually calls an assertion (transitively).
         */
        boolean callsAssertionTransitively;

        /**
         * All methods called from assertions in the form of {@code assert myCall();} without an
         * assertion message. Any of these callees will be post processed and reported as an error
         * if the callee does not call an assertion with a message.
         */
        Set<ResolvedJavaMethod> assertionCalleesWithoutAssertionMessage = new EconomicHashSet<>();

        Set<ResolvedJavaMethod> callees = new EconomicHashSet<>();

        MethodInfo(ResolvedJavaMethod method) {
            super();
            this.method = method;
        }

        boolean canBeCalledWithoutErrorMessage() {
            /*
             * If we are (transitively) calling an assertion then we can check if we do that
             * correctly
             */
            if (callsAssertionTransitively) {
                return correctAssertionMethod;
            } else {
                /*
                 * else we never call an assertion and thus cannot be used as in an assertion
                 * without message
                 */
                return false;
            }
        }

        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Method info for ").append(method).append(System.lineSeparator());
            sb.append("\tValid asserion usage ?").append(correctAssertionMethod).append(System.lineSeparator());
            sb.append("\troot?").append(root).append(System.lineSeparator());
            sb.append("\tcalls assertion(transitively)?").append(callsAssertionTransitively).append(System.lineSeparator());
            sb.append("\tValid use as assertion method based on paths?").append(allPathsAssertionDominated).append(System.lineSeparator());
            for (ResolvedJavaMethod assertionCalleeNotVerifiedYet : assertionCalleesWithoutAssertionMessage) {
                sb.append("\tcalls ").append(assertionCalleeNotVerifiedYet).append(System.lineSeparator());
            }
            for (ResolvedJavaMethod assertionCalleeNotVerifiedYet : calleesToPatchAsAssertionToBeCorrect) {
                sb.append("\ttoPatchToBeCorrect ").append(assertionCalleeNotVerifiedYet).append(System.lineSeparator());
            }
            return sb.toString();
        }
    }

    /**
     * Data structure used to analyze assertion usage: every call to an assert in Graal will have
     * such an entry created.
     */
    private static class AssertionCall {
        /**
         * The position in the caller.
         */
        private final NodeSourcePosition nsp;
        /**
         * A callee if it exists. Can be {@code null}.
         */
        private final ResolvedJavaMethod callee;

        AssertionCall(NodeSourcePosition nsp, ResolvedJavaMethod callee) {
            this.nsp = nsp;
            this.callee = callee;
        }
    }

    private static final int MAX_CALL_GRAPH_ITERATIONS = 128;

    /**
     * Iterate the call graph and verify remaining properties wrt assertions in the compiler
     * codebase. That is, remove all methods that call assertion messages themselves from the list
     * of all other methods. Do this as a fix point and report any violations at the end.
     */
    public void postProcess() {

        fixpointCallGraph();

        StringBuilder allErrorMessages = new StringBuilder();

        reportMissingAssertions(allErrorMessages);

        reportSuperfluousAssertions(allErrorMessages);

        reportAssertionCallsWithIncompleteReturnPaths(allErrorMessages);

        if (!allErrorMessages.isEmpty()) {
            throw new VerificationError(allErrorMessages.toString());
        }
    }

    private void reportAssertionCallsWithIncompleteReturnPaths(StringBuilder allErrorMessages) {
        StringBuilder sbAssertionCallees = new StringBuilder();
        for (AssertionCall ac : assertionCallsWithoutMessage) {
            propagateInvalidAssertionBody(sbAssertionCallees, ac);
        }
        if (!sbAssertionCallees.isEmpty()) {
            allErrorMessages.append(sbAssertionCallees);
            allErrorMessages.append(System.lineSeparator());
        }
    }

    private void propagateInvalidAssertionBody(StringBuilder sbAssertionCallees, AssertionCall ac) {
        ResolvedJavaMethod callee = ac.callee;
        MethodInfo calleeInfo = getMethodInfo(callee);
        if (calleeInfo != null) {
            if (!calleeInfo.allPathAssertionDominated()) {
                sbAssertionCallees.append("Assertion call in ").append(formatNSP(ac.nsp));
                sbAssertionCallees.append("to ").append(callee).append(" is not correct ");
                boolean useAnd = false;
                if (calleeInfo.allPathsAssertionDominated != null) {
                    sbAssertionCallees.append(" because ");
                    sbAssertionCallees.append(calleeInfo.allPathsAssertionDominated);
                    useAnd = true;
                    sbAssertionCallees.append(" this can mean some paths in the callee are using assertions, others are not, still the caller needs a message.");
                }
                if (!calleeInfo.calleesToPatchAsAssertionToBeCorrect.isEmpty()) {
                    if (useAnd) {
                        sbAssertionCallees.append(" and ");
                    }
                    sbAssertionCallees.append(" because the following methods are not known to fully assert correctly on all paths: ");
                    sbAssertionCallees.append(calleeInfo.calleesToPatchAsAssertionToBeCorrect);
                }
                sbAssertionCallees.append(System.lineSeparator());
                sbAssertionCallees.append("Please add an assertion message to the caller to fix this");
                sbAssertionCallees.append(System.lineSeparator());
            }
        }
    }

    private void reportSuperfluousAssertions(StringBuilder allErrorMessages) {
        StringBuilder sbSuperfluousAssertions = new StringBuilder();

        for (AssertionCall ac : assertionCallsWithMessage) {
            ResolvedJavaMethod callee = ac.callee;
            MethodInfo calleeInfo = getMethodInfo(callee);
            if (calleeInfo != null && calleeInfo.callsAssertionTransitively && calleeInfo.correctAssertionMethod && calleeInfo.allPathAssertionDominated() && !calleeInfo.returnIsACall) {
                sbSuperfluousAssertions.append("Superfluous assertion error message in ");
                sbSuperfluousAssertions.append(formatNSP(ac.nsp));
                sbSuperfluousAssertions.append(" because callee ");
                sbSuperfluousAssertions.append(callee);
                sbSuperfluousAssertions.append(" in the condition always throws an exception or returns true, please remove.");
                sbSuperfluousAssertions.append(System.lineSeparator());
            }
        }

        if (!sbSuperfluousAssertions.isEmpty()) {
            allErrorMessages.append(sbSuperfluousAssertions);
            allErrorMessages.append(System.lineSeparator());
        }
    }

    private void reportMissingAssertions(StringBuilder allErrorMessages) {
        List<String> missingAssertionMessages = new ArrayList<>();
        for (AssertionCall ac : assertionCallsWithoutMessage) {
            ResolvedJavaMethod callee = ac.callee;
            if (callee == null || !getMethodInfo(callee).canBeCalledWithoutErrorMessage()) {
                missingAssertionMessages.add(formatNSP(ac.nsp));
            }
        }
        if (!missingAssertionMessages.isEmpty()) {
            String sep = String.format("%n  ");
            allErrorMessages.append(String.format("Found the assertions that need error messages at:%s%s%n" +
                            "Please fix all above assertions such that they have error messages. " +
                            "Consider using API from %s to format assertion error messages with more context.",
                            sep, String.join(sep, missingAssertionMessages), Assertions.class));
            allErrorMessages.append(System.lineSeparator());
        }
    }

    private void fixpointCallGraph() throws VerificationError {
        // iterate the call graph, all methods become correct assertion methods if all callees are
        // correct assertion methods
        int iteration = 0;
        boolean change = true;
        while (change) {
            if (log) {
                TTY.printf("Before iteration %d%n", iteration);
                for (Map.Entry<ResolvedJavaMethod, MethodInfo> e : methodInfos.entrySet()) {
                    TTY.printf("%s", e.getValue());
                }
            }
            if (iteration > MAX_CALL_GRAPH_ITERATIONS) {
                throw new VerificationError("Call graph processing does not reach a fixpoint, iteration was %s, run with logging enabled to figure out the problem.", iteration);
            }
            change = false;
            for (MethodInfo info : methodInfos.values()) {
                if (!info.root) {
                    continue;
                }
                /*
                 * Propagate assertion usage through the call graph
                 */
                if (processCallees(iteration, info)) {
                    change = true;
                }
                /*
                 * Remove all methods that are marked as correct assertion users
                 */
                if (processAssertionWithoutMsgCallees(iteration, info)) {
                    change = true;
                }
                /*
                 * Remove all patch methods that are marked as correct assertion users
                 */
                if (processPatchSet(iteration, info)) {
                    change = true;
                }
                /*
                 * If we have not been a correct assertion user before, but now we no longer call
                 * one and we ourselves are, if we are using assertions actually (calls assertion
                 * transitively), dominating all our paths with assertions if we are bool methods,
                 * we are now a correct assertion message.
                 */
                if (!info.correctAssertionMethod && info.assertionCalleesWithoutAssertionMessage.isEmpty() && info.allPathAssertionDominated()) {
                    info.correctAssertionMethod = true;
                    change = true;
                }
            }
            iteration++;
        }
    }

    private boolean processCallees(int iteration, MethodInfo callerInfo) {
        var calleeIt = callerInfo.callees.iterator();
        boolean change = false;
        while (calleeIt.hasNext()) {
            ResolvedJavaMethod callee = calleeIt.next();
            MethodInfo calleeInfo = methodInfos.get(callee);
            if (!callerInfo.callsAssertionTransitively && calleeInfo != null && calleeInfo.callsAssertionTransitively) {
                callerInfo.callsAssertionTransitively = true;
                change = true;
                if (log) {
                    TTY.printf("Change was true, caller %s did not call assertions but callee %s did so caller does as well, iteration %s%n", callerInfo.method, callee, iteration);
                }
            }
        }
        return change;

    }

    private boolean processAssertionWithoutMsgCallees(int iteration, MethodInfo callerInfo) {
        var calleeIt = callerInfo.assertionCalleesWithoutAssertionMessage.iterator();
        boolean change = false;
        while (calleeIt.hasNext()) {
            ResolvedJavaMethod callee = calleeIt.next();
            MethodInfo calleeInfo = methodInfos.get(callee);
            if (calleeInfo != null && calleeInfo.correctAssertionMethod && calleeInfo.callsAssertionTransitively) {
                calleeIt.remove();
                if (log) {
                    TTY.printf("Change was true, %s was removed from not valid calls of %s doing iteration %s%n", callee, callerInfo.method, iteration);
                }
            }
        }
        return change;
    }

    private boolean processPatchSet(int iteration, MethodInfo callerInfo) {
        boolean change = false;

        var patchIT = callerInfo.calleesToPatchAsAssertionToBeCorrect.iterator();
        while (patchIT.hasNext()) {
            ResolvedJavaMethod callee = patchIT.next();
            MethodInfo calleeInfo = methodInfos.get(callee);
            /*
             * Determine if the assertion logic for callees requires boolean callees to do some kind
             * of (transitive) assertion checking on all paths to be considered correct if there is
             * a callee called.
             */
            final boolean calleeMustCallAssertionMethodOnAllPaths = allPathsMustAssert;

            /*
             * If a callee is not in our bounds we say its fine for patching, eventually we will
             * come to a root processed one, that is then checked properly.
             */
            if (calleeInfo == null || calleeInfo.correctAssertionMethod && (!calleeMustCallAssertionMethodOnAllPaths || calleeInfo.callsAssertionTransitively)) {
                patchIT.remove();
                if (log) {
                    TTY.printf("Change was true, %s was removed from patch calls of %s doing iteration %s%n", callee, callerInfo.method, iteration);
                }
                // can make more stuff actually correct assertions
                change = true;
            }
        }
        return change;
    }

    @Override
    protected void verify(StructuredGraph graph, CoreProviders context) {
        final ResolvedJavaMethod checkedMethod = graph.method();
        if (checkedMethod == null) {
            throw new VerificationError("Checked callee graph %s has null method", checkedMethod);
        }

        if (checkedMethod.getDeclaringClass().getName().startsWith("Ljdk/graal/compiler/graphio")) {
            // This package is mirrored from the visualizer so explicitly ignore it
            return;
        }

        MethodInfo checkedMethodInfo = getMethodInfo(checkedMethod);
        checkedMethodInfo.allPathsAssertionDominated = verifyAllReturnPathsReturnTrue(graph, checkedMethodInfo);
        checkedMethodInfo.root = true;

        boolean correctAssertionMethod = true;
        boolean callsAssertionTransitively = false;

        checkCalleesLoop: for (MethodCallTargetNode t : graph.getNodes(MethodCallTargetNode.TYPE)) {

            if (graph.getDebug().isDumpEnabledForMethod()) {
                TTY.printf("");
            }

            ResolvedJavaMethod callee = t.targetMethod();
            InvokeWithExceptionNode invoke = (InvokeWithExceptionNode) t.invoke();
            NodeSourcePosition assertionNSP = t.getNodeSourcePosition();

            checkedMethodInfo.callees.add(callee);

            if (callsAssertWithoutMsg(callee) || callsAssertWithMsg(callee)) {
                callsAssertionTransitively = true;

                boolean invalidAdded = false;

                // walk back and figure out if this assert is dominated by specific allowed
                // conditions
                for (FixedNode f : GraphUtil.predecessorIterable(invoke)) {
                    if (f instanceof AbstractBeginNode ab && ab.predecessor() instanceof IfNode ifc) {
                        if (ifc.trueSuccessor() == ab) {
                            if (trivialCondition(ifc, excludeAssertionCalls, true)) {
                                continue checkCalleesLoop;
                            }

                            // we are only interested in assertion usage that call boolean methods
                            ResolvedJavaMethod conditionTarget = booleanCallee(ifc);
                            if (conditionTarget != null) {
                                if (callsAssertWithoutMsg(callee)) {
                                    correctAssertionMethod = false;
                                    checkedMethodInfo.assertionCalleesWithoutAssertionMessage.add(conditionTarget);
                                    assertionCallsWithoutMessage.add(new AssertionCall(assertionNSP, conditionTarget));
                                    invalidAdded = true;
                                } else if (callsAssertWithMsg(callee)) {
                                    assertionCallsWithMessage.add(new AssertionCall(assertionNSP, conditionTarget));
                                }
                            }
                            break;
                        } else if (ifc.falseSuccessor() == ab) {
                            if (trivialCondition(ifc, excludeAssertionCalls, false)) {
                                continue checkCalleesLoop;
                            }
                            break;
                        }
                    }
                }

                if (!invalidAdded) {
                    /*
                     * Even if we cannot decode the complexity of conditions involved we should
                     * report all invalid assertions
                     */
                    if (callsAssertWithoutMsg(callee)) {
                        correctAssertionMethod = false;
                        assertionCallsWithoutMessage.add(new AssertionCall(assertionNSP, null));
                    }
                }
            }

        }

        if (callsAssertionTransitively) {
            /*
             * Only if we are actually calling assertions we should reason about this method being
             * used for assertions.
             */
            correctAssertionMethod = correctAssertionMethod && checkedMethodInfo.allPathAssertionDominated();
        }

        checkedMethodInfo.callsAssertionTransitively = callsAssertionTransitively;
        checkedMethodInfo.correctAssertionMethod = correctAssertionMethod;
    }

    /**
     * Verify that for the given graph all return paths return true like
     *
     * <pre>
     * boolean foo() {
     *     if (sth()) {
     *         assert sthElse();
     *         return true;
     *     }
     *     throw AssertionError();
     * }
     * </pre>
     *
     * so that if {@code foo} is used as an assertion condition like
     *
     * <pre>
     * assert foo();
     * </pre>
     *
     * the assertion error message can be dropped because any assertion errors will fire in the
     * callee. For this to be useful it must be guaranteed that each return path of the method is
     * (a) returning constant {@code true} and (b) every return is dominated by either a direct
     * assertion call or a method call that itself does assertion checking (recursive definition).
     *
     * Note that (b) is a very strict rule that mandates that every path eventually (fuzzy
     * definition in the recursion) does an assertion call. We employ this rule to ensure that there
     * are no un-necessary paths without assertion checking that caller sof assertion messages might
     * overlook.
     *
     * A note on performance: note that we eagerly collect this data for each boolean method during
     * the calls to {@link #verify(StructuredGraph, CoreProviders)} because verify is called in a
     * multi-threaded fashion for multiple methods concurrently. Call graph processing is
     * single-threaded, thus we collect this info up front and reduce single threaded processing
     * time later.
     */
    private String verifyAllReturnPathsReturnTrue(StructuredGraph graph, MethodInfo info) {
        if (!graph.method().getSignature().getReturnKind().equals(JavaKind.Boolean)) {
            return null;
        }
        for (ReturnNode ret : graph.getNodes().filter(ReturnNode.class)) {
            ValueNode retVal = ret.result();
            if (retVal instanceof InvokeWithExceptionNode iwe) {
                info.calleesToPatchAsAssertionToBeCorrect.add(iwe.getTargetMethod());
                info.returnIsACall = true;
            } else {
                if (!(retVal.isConstant() && retVal.asJavaConstant().asLong() == 1)) {
                    return "Return val " + retVal + " is not constant true";
                }
                if (!walkBack(ret, new AssertionDominated(info))) {
                    return "Return " + ret + " is not dominated by an assertion call itself, this path is not checked.";
                }
            }
        }
        return null;
    }

    /**
     * Utility data structure to check a graph for a given set of (assertion) callees.
     */
    private class AssertionDominated {
        final MethodInfo info;

        AssertionDominated(MethodInfo info) {
            this.info = info;
        }

        boolean abort(FixedNode f) {
            if (f instanceof LoadFieldNode lf) {
                /*
                 * If we are loading $assertionsDisabled field we treat that as having found an
                 * assertion path - after all this is quite a fuzzy search here and we want to keep
                 * it simple.
                 */
                ResolvedJavaField field = lf.field();
                if (field.isSynthetic() && field.getName().startsWith("$assertionsDisabled")) {
                    return true;
                }
            }
            if (f instanceof InvokeWithExceptionNode iwe) {
                ResolvedJavaMethod callee = iwe.callTarget().targetMethod();
                if (callsAssertWithoutMsg(callee) || callsAssertWithMsg(callee)) {
                    return true;
                } else {
                    if (!excludeListedInvokes(callee, excludeAssertionCalls)) {
                        info.calleesToPatchAsAssertionToBeCorrect.add(callee);
                    }
                    return true;
                }
            }
            if (f instanceof StartNode) {
                // we reached the start, all good
                return true;
            }
            return false;
        }
    }

    private boolean walkBack(FixedNode f, AssertionDominated ad) {
        FixedNode cur = f;
        while (cur != null) {
            if (ad.abort(cur)) {
                return true;
            }
            Node pred = cur.predecessor();
            if (pred instanceof LoopBeginNode lb) {
                pred = lb.forwardEnd();
            } else if (pred instanceof MergeNode m) {
                boolean allVerified = false;
                for (EndNode end : m.forwardEnds()) {
                    allVerified = allVerified || walkBack(end, ad);
                }
                return allVerified;
            }
            cur = (FixedNode) pred;
        }
        return false;
    }

    private boolean callsAssertWithMsg(ResolvedJavaMethod callee) {
        return nonEmptyAssertionCtor.contains(callee);
    }

    private boolean callsAssertWithoutMsg(ResolvedJavaMethod callee) {
        return callee.equals(emptyCtor);
    }

    private static String formatNSP(NodeSourcePosition methodCallTargetNSP) {
        String nsp = methodCallTargetNSP.toString().replace("at ", "");
        nsp = nsp.substring(0, nsp.indexOf("[") - 1);
        return nsp;
    }

    /**
     * Determine if the given if node's condition that is used in an assertion like this
     *
     * <pre>
     * assert trivialC;
     * </pre>
     *
     * which in code looks like this
     *
     * <pre>
     * if (!trivialC) {
     *     throw new AssertionError();
     * }
     * </pre>
     *
     * is considered "trivial". Trivial conditions are ones that do not mandate assertion error
     * messages like null checks, because the surrounding code ensures enough context information is
     * present.
     */
    private boolean trivialCondition(IfNode ifC, Collection<ResolvedJavaMethod> excludeList, boolean trueSucc) {
        LogicNode condition = ifC.condition();
        if (condition instanceof IsNullNode) {
            /*
             * Null assertion failures are trivially understood when looking at the source code.
             */
            return true;
        }
        ResolvedJavaMethod target = booleanCallee(ifC);
        if (target != null) {
            if (excludeListedInvokes(target, excludeList)) {
                return true;
            }
        }
        if (!verifyBooleanAssertionConditions) {
            if (condition instanceof CompareNode c) {
                boolean trueConstantCheckIsFailure = trueSucc;

                // we are comparing something against each other, if one of the cases is a constant
                // there is no need to check those case
                ValueNode x = c.getX();
                ValueNode y = c.getY();

                // exclude boolean conditions - they do not contribute much to the knowledge
                if (c.condition() == CanonicalCondition.EQ) {
                    if (x.isJavaConstant() || y.isConstant()) {
                        if (trueConstantCheckIsFailure) {
                            // true is false so we are a constant, enough knowledge
                            return true;
                        }
                    }
                }

                // exclude boolean conditions - they do not contribute much to the knowledge
                if (c.condition() == CanonicalCondition.EQ) {
                    // boolean comparisons
                    if (x.stamp(NodeView.DEFAULT) instanceof IntegerStamp xStamp && y.stamp(NodeView.DEFAULT) instanceof IntegerStamp yStamp) {
                        if (xStamp.getBits() == 32) {
                            if (x.isJavaConstant()) {
                                return yStamp.mayBeSet() == 0b1;
                            } else if (y.isJavaConstant()) {
                                return xStamp.mayBeSet() == 0b1;
                            }
                        }
                    }
                }
                return c.condition() == CanonicalCondition.EQ && !(x.stamp(NodeView.DEFAULT) instanceof PrimitiveStamp) && (x.isJavaConstant() || y.isJavaConstant());
            }
        }
        return false;
    }

    /**
     * Determine if the given if node's condition that is used in an assertion call like this
     *
     * <pre>
     * assert booleanRet();
     * </pre>
     *
     * which in code looks like this
     *
     * <pre>
     * if (booleanRet() == 0) {
     *     throw new AssertionError();
     * }
     * </pre>
     *
     * is actually a boolean method that is used. If so return the method, else return null.
     */
    private static ResolvedJavaMethod booleanCallee(IfNode ifC) {
        LogicNode condition = ifC.condition();
        if (condition instanceof IntegerEqualsNode) {
            IntegerEqualsNode ie = (IntegerEqualsNode) condition;
            if (ie.getY().isConstant() && ie.getY().asJavaConstant().asLong() == 0) {
                if (ie.getX() instanceof InvokeWithExceptionNode) {
                    InvokeWithExceptionNode iwe = (InvokeWithExceptionNode) ie.getX();
                    return iwe.callTarget().targetMethod();
                }
            }
        }
        return null;
    }

    /**
     * Determine if the given callee is listed in the excluded callees for assertion checking and
     * can thus be ignored.
     */
    private static boolean excludeListedInvokes(ResolvedJavaMethod target, Collection<ResolvedJavaMethod> excludeList) {
        return excludeList.contains(target);
    }

}
