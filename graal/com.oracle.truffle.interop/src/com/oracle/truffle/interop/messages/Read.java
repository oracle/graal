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
package com.oracle.truffle.interop.messages;

import com.oracle.truffle.api.interop.messages.*;

public final class Read implements Message {
    private final Object receiver;
    private final Argument id;

    private Read(Object receiver, Argument id) {
        this.receiver = receiver;
        this.id = id;
    }

    public static Read create(Receiver receiver, Argument id) {
        return new Read(receiver, id);
    }

    public static Read create(Message receiver, Argument id) {
        return new Read(receiver, id);
    }

    public Argument getId() {
        return id;
    }

    public Object getReceiver() {
        return receiver;
    }

    public boolean matchStructure(Object message) {
        if (!(message instanceof Read)) {
            return false;
        }
        Read m1 = this;
        Read m2 = (Read) message;
        return MessageUtil.compareMessage(m1.getReceiver(), m2.getReceiver()) && MessageUtil.compareMessage(m1.getId(), m2.getId());
    }

    @Override
    public String toString() {
        return String.format("Read(%s, %s)", receiver.toString(), id.toString());
    }
}
