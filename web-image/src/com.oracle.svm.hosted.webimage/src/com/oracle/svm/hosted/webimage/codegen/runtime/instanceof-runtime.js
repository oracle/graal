/*
 * Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
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
/**
 * Determine whether object is of type (or subtype) of mirror.
 */
function isA(allowsnull, object, hub) {
    if (object === null) {
        return allowsnull ? true : false;
    }

    return slotTypeCheck(hub, object.hub);
}

/**
 * Same as isA, but only accepts the exact type, no subtype.
 */
function isExact(allowsnull, object, hub) {
    if (object === null) {
        return allowsnull ? true : false;
    }

    return object.hub === hub;
}

/**
 * This is a JavaScript implementation of TypeSnippets::slotTypeCheck.
 *
 * The type check slots in 'hub' are used for the 'start', 'range', and 'slot'
 * arguments in the original Java method.
 *
 * Basically checks if hub is a supertype of checkedHub
 */
function slotTypeCheck(hub, checkedHub) {
    if (DEBUG_CHECKS) {
        Guarantee(!!hub, "slotTypeCheck: hub must be defined");
        Guarantee(!!checkedHub, "slotTypeCheck: checkedHub must be defined");
    }
    const start = hub.$t["java.lang.Class"].$f["typeCheckStart"];
    const range = hub.$t["java.lang.Class"].$f["typeCheckRange"];
    const slot = hub.$t["java.lang.Class"].$f["typeCheckSlot"];

    const checkedTypeID = checkedHub.$t["java.lang.Class"].$f["closedTypeWorldTypeCheckSlots"][slot];
    return unsignedCompareLessI32(checkedTypeID - start, range);
}
