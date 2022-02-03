/*
 * Copyright (c) 2021, 2021, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.truffle.espresso.impl;

import com.oracle.truffle.api.staticobject.StaticShape;
import com.oracle.truffle.espresso.classfile.RuntimeConstantPool;

public final class RedefineAddedField extends Field {

    private Field compatibleField;
    private StaticShape<ExtensionFieldObject.ExtensionFieldObjectFactory> extensionShape;

    public RedefineAddedField(ObjectKlass.KlassVersion holder, LinkedField linkedField, RuntimeConstantPool pool, boolean isDelegation) {
        super(holder, linkedField, pool);
        if (!isDelegation) {
            StaticShape.Builder shapeBuilder = StaticShape.newBuilder(getDeclaringKlass().getEspressoLanguage());
            shapeBuilder.property(linkedField, linkedField.getParserField().getPropertyType(), isFinalFlagSet());
            this.extensionShape = shapeBuilder.build(ExtensionFieldObject.FieldStorageObject.class, ExtensionFieldObject.ExtensionFieldObjectFactory.class);
        }
    }

    public static Field createDelegationField(Field field) {
        // update holder to latest klass version to ensure we
        // only re-resolve again when the class is redefined
        RedefineAddedField delegationField = new RedefineAddedField(field.getDeclaringKlass().getKlassVersion(), field.linkedField, field.pool, true);
        delegationField.setCompatibleField(field);
        return delegationField;
    }

    @Override
    public void setCompatibleField(Field field) {
        compatibleField = field;
    }

    @Override
    public boolean hasCompatibleField() {
        return compatibleField != null;
    }

    @Override
    public Field getCompatibleField() {
        return compatibleField;
    }

    @Override
    public StaticShape<ExtensionFieldObject.ExtensionFieldObjectFactory> getExtensionShape() {
        return extensionShape;
    }

}
