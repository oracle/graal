/*
 * Copyright (c) 2008, 2011, Oracle and/or its affiliates. All rights reserved.
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
package com.sun.max.asm.dis;

import java.util.*;

import com.sun.max.asm.gen.*;

/**
 * A facility for mapping addresses to {@linkplain DisassembledLabel labels} and {@linkplain DisassembledObject
 * disassembled objects}. Labels are only used to identify addresses that are a) explicitly targeted by an operand of a
 * disassembled object {@linkplain #add(DisassembledObject) added} to the map or b) explicitly
 * {@linkplain #add(ImmediateArgument, String) added}.
 */
public class AddressMapper {

    private int serial;
    private int unnamedLabels;
    private final Map<ImmediateArgument, DisassembledObject> objectMap = new HashMap<ImmediateArgument, DisassembledObject>();
    private final Map<ImmediateArgument, DisassembledLabel> labelMap = new TreeMap<ImmediateArgument, DisassembledLabel>(new Comparator<ImmediateArgument>() {
        public int compare(ImmediateArgument o1, ImmediateArgument o2) {
            final long l1 = o1.asLong();
            final long l2 = o2.asLong();
            if (l1 < l2) {
                return -1;
            } else if (l1 > l2) {
                return 1;
            }
            return 0;
        }
    });

    /**
     * Gets the label for a given address.
     *
     * @param address the address for which a label is requested
     * @return the label for {@code address} or null if no label exists for that address
     */
    public synchronized DisassembledLabel labelAt(ImmediateArgument address) {
        fixupUnnamedLabels();
        return labelMap.get(address);
    }

    /**
     * Gets the label for a given disassembled object.
     *
     * @param disassembledObject the disassembled object for which a label is requested
     * @return the label corresponding to {@code disassembledObject}'s start address or null if no label exists for that address
     */
    public synchronized DisassembledLabel labelAt(DisassembledObject disassembledObject) {
        fixupUnnamedLabels();
        return labelAt(disassembledObject.startAddress());
    }

    /**
     * Adds a mapping from a given address to a given name.
     *
     * @return the previous mapping (if any) for {@code address}
     */
    public synchronized DisassembledLabel add(ImmediateArgument address, String name) {
        return labelMap.put(address, new DisassembledLabel(address, name));
    }

    /**
     * Adds a mapping for the address {@linkplain DisassembledObject#targetAddress() targeted} by a given disassembled object.
     * If {@code disassembledObject} does not target another object, then no update is made to this mapping.
     *
     * @param disassembledObject the disassembled object to consider
     */
    public void add(DisassembledObject disassembledObject) {
        add(Arrays.asList(new DisassembledObject[] {disassembledObject}));
    }

    /**
     * Adds a mapping for the addresses {@linkplain DisassembledObject#targetAddress() targeted} by a given sequence of
     * disassembled objects. If none of {@code disassembledObjects} target other objects, then no update is made to
     * this mapping.
     *
     * @param disassembledObjects the disassembled objects to consider
     */
    public synchronized void add(List<DisassembledObject> disassembledObjects) {
        for (DisassembledObject disassembledObject : disassembledObjects) {
            final ImmediateArgument address = disassembledObject.startAddress();
            objectMap.put(address, disassembledObject);
        }
        for (DisassembledObject disassembledObject : disassembledObjects) {
            final ImmediateArgument targetAddress = disassembledObject.targetAddress();
            if (targetAddress != null) {
                final DisassembledObject targetDisassembledObject = objectMap.get(targetAddress);
                if (targetDisassembledObject == null) {
                    labelMap.remove(targetAddress);
                } else {
                    DisassembledLabel label = labelMap.get(targetAddress);
                    if (label == null || label.target() != targetDisassembledObject) {
                        label = new UnnamedLabel(targetDisassembledObject);
                        final DisassembledLabel oldValue = labelMap.put(targetAddress, label);
                        if (!(oldValue instanceof UnnamedLabel)) {
                            unnamedLabels++;
                        }
                    }
                }
            }
        }
    }

    private static class UnnamedLabel extends DisassembledLabel {
        UnnamedLabel(DisassembledObject targetDisassembledObject) {
            super(targetDisassembledObject, "L?");
        }
    }

    private void fixupUnnamedLabels() {
        while (unnamedLabels != 0) {
            for (Map.Entry<ImmediateArgument, DisassembledLabel> entry : labelMap.entrySet()) {
                final DisassembledLabel label = entry.getValue();
                if (label instanceof UnnamedLabel) {
                    entry.setValue(new DisassembledLabel(label.target(), "L" + (++serial)));
                    unnamedLabels--;
                }
            }
        }
    }

    /**
     * Computes the maximum length of any label name in this mapping.
     */
    public synchronized int maximumLabelNameLength() {
        fixupUnnamedLabels();
        int max = 0;
        for (DisassembledLabel label : labelMap.values()) {
            final String name = label.name();
            if (name.length() > max) {
                max = name.length();
            }
        }
        return max;
    }
}
