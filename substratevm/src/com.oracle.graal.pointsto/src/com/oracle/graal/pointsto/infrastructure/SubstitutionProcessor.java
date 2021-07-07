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

import jdk.vm.ci.meta.ResolvedJavaField;
import jdk.vm.ci.meta.ResolvedJavaMethod;
import jdk.vm.ci.meta.ResolvedJavaType;

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
     * Get the original type.
     * 
     * @param type the result of a substitution
     * @return the original type of the substitution
     */
    public ResolvedJavaType resolve(ResolvedJavaType type) {
        return type;
    }

    public ResolvedJavaField lookup(ResolvedJavaField field) {
        return field;
    }

    public ResolvedJavaMethod lookup(ResolvedJavaMethod method) {
        return method;
    }

    public ResolvedJavaMethod resolve(ResolvedJavaMethod method) {
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

    public static SubstitutionProcessor chain(SubstitutionProcessor first, SubstitutionProcessor second) {
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
        public ResolvedJavaType resolve(ResolvedJavaType type) {
            return first.resolve(second.resolve(type));
        }

        @Override
        public ResolvedJavaField lookup(ResolvedJavaField field) {
            return second.lookup(first.lookup(field));
        }

        @Override
        public ResolvedJavaMethod lookup(ResolvedJavaMethod method) {
            return second.lookup(first.lookup(method));
        }

        @Override
        public ResolvedJavaMethod resolve(ResolvedJavaMethod method) {
            return first.resolve(second.resolve(method));
        }
    }

}
