/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
 *
 * This code is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License version 2 only, as
 * published by the Free Software Foundation.
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
package com.oracle.truffle.espresso.jdwp.impl;

import com.oracle.truffle.espresso.jdwp.api.CallFrame;
import com.oracle.truffle.espresso.jdwp.api.JDWPContext;
import com.oracle.truffle.espresso.jdwp.api.KlassRef;

public abstract class EventInfo {

    public abstract KlassRef getType();

    public abstract Object getThread();

    public abstract long getThisId();

    public static final class Klass extends EventInfo {

        private final KlassRef klass;
        private final Object thread;

        public Klass(KlassRef klass, Object thread) {
            this.klass = klass;
            this.thread = thread;
        }

        @Override
        public KlassRef getType() {
            return klass;
        }

        @Override
        public Object getThread() {
            return thread;
        }

        @Override
        public long getThisId() {
            return 0;
        }

    }

    public static final class Frame extends EventInfo {

        private final JDWPContext context;
        private final CallFrame frame;
        private final Object thread;

        public Frame(JDWPContext context, CallFrame frame, Object thread) {
            this.context = context;
            this.frame = frame;
            this.thread = thread;
        }

        @Override
        public KlassRef getType() {
            return (KlassRef) context.getIds().fromId((int) frame.getClassId());
        }

        @Override
        public Object getThread() {
            return thread;
        }

        @Override
        public long getThisId() {
            Object thisObject = frame != null ? frame.getThisValue() : null;
            if (thisObject == null) {
                return 0;
            }
            return context.getIds().getId(thisObject);
        }

    }
}
