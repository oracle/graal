/*
 * Copyright (c) 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.api.interop;

import com.oracle.truffle.api.nodes.DirectCallNode;

final class CachedObjectAccessNode extends ObjectAccessNode {
    @Child private DirectCallNode callTarget;
    @Child private ObjectAccessNode next;

    @Child private DirectCallNode languageCheckAsNode;
    private final ForeignAccess languageCheck;

    @Child private ForeignAccessArguments accessArguments = new ForeignAccessArguments();

    protected CachedObjectAccessNode(DirectCallNode callTarget, ObjectAccessNode next, ForeignAccess languageCheck, DirectCallNode languageCheckAsNode) {
        this.callTarget = callTarget;
        this.next = next;
        this.languageCheck = languageCheck;
        this.languageCheckAsNode = languageCheckAsNode;
        if (this.languageCheckAsNode != null) {
            this.languageCheckAsNode.forceInlining();
        }
        this.callTarget.forceInlining();
    }

    @Override
    public Object executeWith(TruffleObject receiver, Object[] arguments) {
        if (accept(receiver)) {
            return callTarget.call(accessArguments.executeCreate(receiver, arguments));
        } else {
            return next.executeWith(receiver, arguments);
        }
    }

    private boolean accept(TruffleObject receiver) {
        if ((languageCheckAsNode != null && (boolean) languageCheckAsNode.call(new Object[]{receiver}))) {
            return true;
        } else if (languageCheckAsNode == null && languageCheck.canHandle(receiver)) {
            return true;
        } else {
            return false;
        }
    }

}
