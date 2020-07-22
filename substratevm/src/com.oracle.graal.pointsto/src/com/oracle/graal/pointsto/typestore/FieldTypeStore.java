/*
 * Copyright (c) 2015, 2017, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.pointsto.typestore;

import java.util.concurrent.atomic.AtomicReferenceFieldUpdater;

import com.oracle.graal.pointsto.BigBang;
import com.oracle.graal.pointsto.flow.FieldTypeFlow;
import com.oracle.graal.pointsto.flow.FrozenFieldFilterTypeFlow;
import com.oracle.graal.pointsto.flow.UnsafeWriteSinkTypeFlow;
import com.oracle.graal.pointsto.flow.context.object.AnalysisObject;
import com.oracle.graal.pointsto.meta.AnalysisField;

/**
 * Store for instance field access type flows.
 */
public abstract class FieldTypeStore {

    private static final AtomicReferenceFieldUpdater<FieldTypeStore, FrozenFieldFilterTypeFlow> FROZEN_FILTER_FLOW_UPDATER = AtomicReferenceFieldUpdater.newUpdater(FieldTypeStore.class,
                    FrozenFieldFilterTypeFlow.class, "frozenFilterFlow");
    private static final AtomicReferenceFieldUpdater<FieldTypeStore, UnsafeWriteSinkTypeFlow> UNSAFE_WRITE_SINK_FLOW_UPDATER = AtomicReferenceFieldUpdater.newUpdater(FieldTypeStore.class,
                    UnsafeWriteSinkTypeFlow.class, "unsafeWriteSinkFlow");

    /** The holder of the field flow. */
    protected final AnalysisObject object;
    protected final AnalysisField field;
    /** A filter flow used for unsafe writes on frozen type state. */
    private volatile FrozenFieldFilterTypeFlow frozenFilterFlow;
    /** A sink used for unsafe writes on frozen type state. */
    private volatile UnsafeWriteSinkTypeFlow unsafeWriteSinkFlow;

    protected FieldTypeStore(AnalysisField field, AnalysisObject object) {
        this.field = field;
        this.object = object;
    }

    public AnalysisObject object() {
        return object;
    }

    public AnalysisField field() {
        return field;
    }

    public abstract FieldTypeFlow writeFlow();

    public abstract FieldTypeFlow readFlow();

    /** Overridden for field type stores that need lazy initialization. */
    public void init(@SuppressWarnings("unused") BigBang bb) {
    }

    public UnsafeWriteSinkTypeFlow unsafeWriteSinkFlow(BigBang bb) {
        assert field.hasUnsafeFrozenTypeState() : "Unsafe write sink flow requested for non unsafe accessed frozen field.";
        // first we create the unsafe write sink
        if (unsafeWriteSinkFlow == null) {
            UNSAFE_WRITE_SINK_FLOW_UPDATER.compareAndSet(this, null, new UnsafeWriteSinkTypeFlow(bb, field));
        }
        // then we build the filter for it.
        if (frozenFilterFlow == null) {
            if (FROZEN_FILTER_FLOW_UPDATER.compareAndSet(this, null, new FrozenFieldFilterTypeFlow(bb, field, unsafeWriteSinkFlow))) {
                frozenFilterFlow.addUse(bb, writeFlow());
                unsafeWriteSinkFlow.addUse(bb, frozenFilterFlow);
            }
        }

        return unsafeWriteSinkFlow;
    }

}
