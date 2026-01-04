/*
 * Copyright (c) 2012, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.infrastructure;

import com.oracle.graal.pointsto.meta.AnalysisUniverse;

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

/**
 * A substitution processor is a facility that allows modifying code elements coming from class
 * files without modifying the class files. It is queried by the {@link AnalysisUniverse} when
 * creating the analysis model of a hosted element (type, method, field) to check if a substitution
 * is available. How a substitution processor is implemented, i.e., when a processor decides to
 * replace an element and how that element is constructed, is not specified. The substitution
 * processors form a chain, created via {@link #chainUpInOrder(SubstitutionProcessor...)} and
 * {@link #extendsTheChain(SubstitutionProcessor, SubstitutionProcessor[])} utility methods, and are
 * queried in the order in which they were installed.
 */
public abstract class SubstitutionProcessor {

    /**
     * Get the substitution of an original type.
     * 
     * @param type the original type
     * @return the substitution type, or the original type if it isn't covered by this substitution
     */
    public ResolvedJavaType lookup(ResolvedJavaType type) {
        return type;
    }

    /**
     * Get the substitution of an original field.
     *
     * @param field the original field
     * @return the substitution field, or the original field if it isn't covered by this
     *         substitution
     */
    public ResolvedJavaField lookup(ResolvedJavaField field) {
        return field;
    }

    /**
     * Get the substitution of an original method.
     *
     * @param method the original method
     * @return the substitution method, or the original method if it isn't covered by this
     *         substitution
     */
    public ResolvedJavaMethod lookup(ResolvedJavaMethod method) {
        return method;
    }

    public static final SubstitutionProcessor IDENTITY = new IdentitySubstitutionProcessor();

    public static void extendsTheChain(SubstitutionProcessor head, SubstitutionProcessor[] tail) {
        ChainedSubstitutionProcessor endOfchain = null;
        SubstitutionProcessor endOfHead = head;

        while (endOfHead instanceof ChainedSubstitutionProcessor) {
            endOfchain = (ChainedSubstitutionProcessor) endOfHead;
            endOfHead = endOfchain.second;
        }

        assert endOfchain != null;
        SubstitutionProcessor[] tailChain = new SubstitutionProcessor[tail.length + 1];
        System.arraycopy(tail, 0, tailChain, 1, tail.length);
        tailChain[0] = endOfHead;
        endOfchain.second = chainUpInOrder(tailChain);
    }

    public static SubstitutionProcessor chainUpInOrder(SubstitutionProcessor... processors) {
        SubstitutionProcessor current = null;

        for (int i = processors.length - 1; i >= 0; i--) {
            if (current == null) {
                current = processors[i];
            } else {
                current = chain(processors[i], current);
            }
        }

        return current;
    }

    private static SubstitutionProcessor chain(SubstitutionProcessor first, SubstitutionProcessor second) {
        if (first == IDENTITY) {
            return second;
        } else if (second == IDENTITY) {
            return first;
        } else {
            return new ChainedSubstitutionProcessor(first, second);
        }
    }

    static final class IdentitySubstitutionProcessor extends SubstitutionProcessor {

        private IdentitySubstitutionProcessor() {
        }
    }

    static final class ChainedSubstitutionProcessor extends SubstitutionProcessor {

        private final SubstitutionProcessor first;
        private SubstitutionProcessor second;

        private ChainedSubstitutionProcessor(SubstitutionProcessor first, SubstitutionProcessor second) {
            this.first = first;
            this.second = second;
        }

        @Override
        public ResolvedJavaType lookup(ResolvedJavaType type) {
            return second.lookup(first.lookup(type));
        }

        @Override
        public ResolvedJavaField lookup(ResolvedJavaField field) {
            return second.lookup(first.lookup(field));
        }

        @Override
        public ResolvedJavaMethod lookup(ResolvedJavaMethod method) {
            return second.lookup(first.lookup(method));
        }
    }

}
