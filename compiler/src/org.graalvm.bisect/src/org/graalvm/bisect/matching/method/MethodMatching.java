/*
 * Copyright (c) 2022, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.bisect.matching.method;

import org.graalvm.bisect.core.ExecutedMethod;
import org.graalvm.bisect.core.ExperimentId;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a matching between methods of two experiments and the matching of their respective compilations.
 */
public interface MethodMatching {
    /**
     * Gets the list of pairs of matched methods, each of which holds a matching of its compilations.
     * @return the list of matched methods
     */
    List<MatchedMethod> getMatchedMethods();

    /**
     * Gets the list of the methods that do not have a pair.
     * @return the list of methods without a pair
     */
    List<ExtraMethod> getExtraMethods();

    /**
     * Represents one pair of methods and a matching between their respective compilations.
     */
    class MatchedMethod {
        /**
         * Gets the signature of the matched methods. Both methods must have the same signature.
         * @see ExecutedMethod#getCompilationMethodName()
         * @return the compilation method name
         */
        public String getCompilationMethodName() {
            return compilationMethodName;
        }

        /**
         * Gets the list of pairs of matched compilations of this method.
         * @return the list of pairs of matched compilations
         */
        public List<MatchedExecutedMethod> getMatchedExecutedMethods() {
            return matchedExecutedMethods;
        }

        /**
         * Gets the list of compilations of this method that do not have a match.
         * @return the list of compilations of this method that were not matched
         */
        public List<ExtraExecutedMethod> getExtraExecutedMethods() {
            return extraExecutedMethods;
        }

        private final String compilationMethodName;
        private final ArrayList<MatchedExecutedMethod> matchedExecutedMethods = new ArrayList<>();
        private final ArrayList<ExtraExecutedMethod> extraExecutedMethods = new ArrayList<>();

        MatchedMethod(String compilationMethodName) {
            this.compilationMethodName = compilationMethodName;
        }

        public MatchedExecutedMethod addMatchedExecutedMethod(ExecutedMethod method1, ExecutedMethod method2) {
            MatchedExecutedMethod matchedExecutedMethod = new MatchedExecutedMethod(method1, method2);
            matchedExecutedMethods.add(matchedExecutedMethod);
            return matchedExecutedMethod;
        }

        public ExtraExecutedMethod addExtraExecutedMethod(ExecutedMethod method, ExperimentId experimentId) {
            ExtraExecutedMethod extraExecutedMethod = new ExtraExecutedMethod(experimentId, method);
            extraExecutedMethods.add(extraExecutedMethod);
            return extraExecutedMethod;
        }
    }

    /**
     * Represents a pair of two matched compilations (executions) of the same method.
     */
    class MatchedExecutedMethod {
        /**
         * Gets the matched executed method from the first experiment.
         * @return the matched executed method from the first experiment
         */
        public ExecutedMethod getMethod1() {
            return method1;
        }

        /**
         * Gets the matched executed method from the second experiment.
         * @return the matched executed method from the second experiment
         */
        public ExecutedMethod getMethod2() {
            return method2;
        }

        private final ExecutedMethod method1;
        private final ExecutedMethod method2;

        MatchedExecutedMethod(ExecutedMethod method1, ExecutedMethod method2) {
            this.method1 = method1;
            this.method2 = method2;
        }
    }

    /**
     * Represents an executed method that was not matched with any method from the other experiment.
     */
    class ExtraExecutedMethod {
        private final ExperimentId experimentId;
        private final ExecutedMethod executedMethod;

        ExtraExecutedMethod(ExperimentId experimentId, ExecutedMethod executedMethod) {
            this.experimentId = experimentId;
            this.executedMethod = executedMethod;
        }

        /**
         * Gets the ID of the experiment to which this executed method belongs.
         * @return the ID of the experiment of this executed method
         */
        public ExperimentId getExperimentId() {
            return experimentId;
        }

        public ExecutedMethod getExecutedMethod() {
            return executedMethod;
        }
    }

    /**
     * Represents a Java method that was not matched with any other Java method from the other experiment.
     */
    class ExtraMethod {
        /**
         * Gets the ID of the experiment to which this Java method belongs.
         * @return the ID of the experiment of this Java method
         */
        public ExperimentId getExperimentId() {
            return experimentId;
        }

        private final ExperimentId experimentId;

        /**
         * Gets the compilation method name of this Java method.
         * @return the compilation name of this method
         * @see ExecutedMethod#getCompilationMethodName()
         */
        public String getCompilationMethodName() {
            return compilationMethodName;
        }

        private final String compilationMethodName;

        ExtraMethod(ExperimentId experimentId, String compilationMethodName) {
            this.experimentId = experimentId;
            this.compilationMethodName = compilationMethodName;
        }
    }
}
