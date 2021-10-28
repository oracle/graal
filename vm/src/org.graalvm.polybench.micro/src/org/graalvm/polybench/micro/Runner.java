/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
package org.graalvm.polybench.micro;

import com.oracle.truffle.api.CallTarget;
import com.oracle.truffle.api.dsl.Cached;
import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.interop.InteropLibrary;
import com.oracle.truffle.api.interop.InvalidArrayIndexException;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.interop.UnknownIdentifierException;
import com.oracle.truffle.api.library.ExportLibrary;
import com.oracle.truffle.api.library.ExportMessage;
import com.oracle.truffle.api.nodes.DirectCallNode;
import com.oracle.truffle.api.nodes.ExplodeLoop;
import com.oracle.truffle.api.nodes.IndirectCallNode;
import java.util.ArrayList;

@ExportLibrary(InteropLibrary.class)
public final class Runner implements TruffleObject {

    final Microbench config;
    final Workload run;

    public Runner(Microbench config, CallTarget workload, Object[] preparedState) {
        this.config = config;
        this.run = new Workload(workload, preparedState);
    }

    @ExportLibrary(InteropLibrary.class)
    static final class Workload implements TruffleObject {

        final CallTarget workload;
        final Object[] preparedState;

        Workload(CallTarget workload, Object[] preparedState) {
            this.workload = workload;
            this.preparedState = preparedState;
        }

        @ExportMessage
        boolean isExecutable() {
            return workload != null;
        }

        @ExportMessage
        static final class Execute {

            @Specialization(guards = "callNode.getCallTarget() == self.workload")
            static Object doCached(Workload self, Object[] args,
                            @Cached("create(self.workload)") DirectCallNode callNode) {
                assert args.length == 0;
                return callNode.call(self.preparedState);
            }

            @Specialization(replaces = "doCached")
            static Object doGeneric(Workload self, Object[] args,
                            @Cached IndirectCallNode callNode) {
                assert args.length == 0;
                return callNode.call(self.workload, self.preparedState);
            }
        }
    }

    @FunctionalInterface
    private interface MemberGetter {

        Object get(Runner runner);
    }

    private enum Member {
        batchSize(r -> r.config.batchSize),
        warmupIterations(r -> r.config.warmupIterations),
        iterations(r -> r.config.iterations),
        unit(r -> r.config.unit),
        run(r -> r.run);

        final MemberGetter getter;

        Member(MemberGetter getter) {
            this.getter = getter;
        }
    }

    @ExportMessage
    boolean hasMembers() {
        assert config != null;
        return true;
    }

    @ExportMessage
    @ExplodeLoop
    boolean isMemberReadable(String member) {
        for (Member m : Member.values()) {
            if (m.name().equals(member)) {
                return m.getter.get(this) != null;
            }
        }
        return false;
    }

    @ExportMessage
    @ExplodeLoop
    Object readMember(String member) throws UnknownIdentifierException {
        for (Member m : Member.values()) {
            if (m.name().equals(member)) {
                return m.getter.get(this);
            }
        }
        throw UnknownIdentifierException.create(member);
    }

    @ExportMessage
    Object getMembers(@SuppressWarnings("unused") boolean includeInternal) {
        return new MemberList(this);
    }

    @ExportLibrary(InteropLibrary.class)
    static final class MemberList implements TruffleObject {

        private final String[] members;

        MemberList(Runner runner) {
            ArrayList<String> ret = new ArrayList<>();

            for (Member m : Member.values()) {
                if (m.getter.get(runner) != null) {
                    ret.add(m.name());
                }
            }

            this.members = ret.toArray(new String[0]);
        }

        @ExportMessage
        boolean hasArrayElements() {
            assert members != null;
            return true;
        }

        @ExportMessage
        long getArraySize() {
            return members.length;
        }

        @ExportMessage
        Object readArrayElement(long index) throws InvalidArrayIndexException {
            if (Long.compareUnsigned(index, members.length) < 0) {
                return members[(int) index];
            } else {
                throw InvalidArrayIndexException.create(index);
            }
        }

        @ExportMessage
        boolean isArrayElementReadable(long index) {
            return index < members.length;
        }
    }
}
