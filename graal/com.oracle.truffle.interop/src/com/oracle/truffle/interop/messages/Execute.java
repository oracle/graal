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

public final class Execute implements Message {
    private final Object receiver;
    private final int arity;

    public static Execute create(Receiver receiver, int arity) {
        return new Execute(receiver, arity);
    }

    public static Execute create(Message receiver, int arity) {
        return new Execute(receiver, arity);
    }

    private Execute(Object receiver, int arity) {
        this.receiver = receiver;
        this.arity = arity;
    }

    public Object getReceiver() {
        return receiver;
    }

    public int getArity() {
        return arity;
    }

    public boolean matchStructure(Object message) {
        if (!(message instanceof Execute)) {
            return false;
        }
        Execute m1 = this;
        Execute m2 = (Execute) message;
        return MessageUtil.compareMessage(m1.getReceiver(), m2.getReceiver());
    }

    @Override
    public String toString() {
        return String.format("Execute(%s)", receiver.toString());
    }

}
