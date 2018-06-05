/*
 * Copyright (c) 2016, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.dsl.test.interop;

import com.oracle.truffle.api.dsl.Specialization;
import com.oracle.truffle.api.dsl.test.interop.ValidTruffleObject15MRFactory.NodeThatCausesUnsupportedSpecializationExceptionNodeGen;
import com.oracle.truffle.api.frame.VirtualFrame;
import com.oracle.truffle.api.interop.CanResolve;
import com.oracle.truffle.api.interop.MessageResolution;
import com.oracle.truffle.api.interop.Resolve;
import com.oracle.truffle.api.interop.TruffleObject;
import com.oracle.truffle.api.nodes.Node;

@SuppressWarnings("unused")
@MessageResolution(receiverType = ValidTruffleObject15.class)
public class ValidTruffleObject15MR {

    @Resolve(message = "WRITE")
    public abstract static class WriteNode15 extends Node {

        @Child private NodeThatCausesUnsupportedSpecializationException err = NodeThatCausesUnsupportedSpecializationExceptionNodeGen.create();

        protected Object access(VirtualFrame frame, ValidTruffleObject15 receiver, String name, Object value) {
            err.executeWithTarget(frame, name);
            return 0;
        }
    }

    protected abstract static class NodeThatCausesUnsupportedSpecializationException extends Node {

        public abstract Object executeWithTarget(VirtualFrame frame, Object o);

        @Specialization
        public Object error(VirtualFrame frame, int err) {
            return null;
        }

    }

    @CanResolve
    public abstract static class LanguageCheck15 extends Node {

        protected boolean test(VirtualFrame frame, TruffleObject receiver) {
            return receiver instanceof ValidTruffleObject15;
        }
    }

}
