/*
 * Copyright (c) 2017, 2025, Oracle and/or its affiliates. All rights reserved.
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
package at.ssw.visualizer.dataflow.attributes;

/**
 * This is the interface for attributes that can be applied
 * to a node. Such attributes could be: visibility or
 * full information...
 *
 * @author Stefan Loidl
 */
public interface INodeAttribute {

    /**
     * This methode is meant to return if the attribute
     * is active (from its own point of view). If this
     * method returns false and removable return true the
     * attribute can savely be removed from the attribute list.
     */
    public boolean validate();

    /**
     * Returns if the attribute can be removed from the attributelist
     * after validate returns true.
     * Some attributes are chained to nodes for lifetime. In this
     * case removable always returns false.
     */
    public boolean removeable();
}
