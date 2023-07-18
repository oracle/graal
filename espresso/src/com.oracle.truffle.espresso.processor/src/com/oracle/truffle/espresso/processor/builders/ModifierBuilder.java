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
package com.oracle.truffle.espresso.processor.builders;

public final class ModifierBuilder extends AbstractCodeBuilder {
    public static String PUBLIC = "public";
    public static String PRIVATE = "private";
    public static String PROTECTED = "protected";
    public static String ABSTRACT = "abstract";
    public static String STATIC = "static";
    public static String FINAL = "final";
    public static String VOLATILE = "volatile";
    public static String NATIVE = "native";
    public static String OVERRIDE = "override";

    private boolean qualifiedAsPublic;
    private boolean qualifiedAsPrivate;
    private boolean qualifiedAsProtected;
    private boolean qualifiedAsStatic;
    private boolean qualifiedAsFinal;
    private boolean qualifiedAsVolatile;
    private boolean qualifiedAsAbstract;
    private boolean qualifiedAsNative;
    private boolean qualifiedAsOverride;

    public ModifierBuilder asPublic() {
        if (qualifiedAsPrivate) {
            throw new IllegalStateException("Cannot qualify as both public and private");
        }
        if (qualifiedAsProtected) {
            throw new IllegalStateException("Cannot qualify as both public and protected");
        }
        this.qualifiedAsPublic = true;
        return this;
    }

    public ModifierBuilder asPrivate() {
        if (qualifiedAsPublic) {
            throw new IllegalStateException("Cannot qualify as both public and private");
        }
        if (qualifiedAsProtected) {
            throw new IllegalStateException("Cannot qualify as both private and protected");
        }
        if (qualifiedAsAbstract) {
            throw new IllegalStateException("Cannot qualify as both abstract and private");
        }
        this.qualifiedAsPrivate = true;
        return this;
    }

    public ModifierBuilder asProtected() {
        if (qualifiedAsPublic) {
            throw new IllegalStateException("Cannot qualify as both public and protected");
        }
        if (qualifiedAsPrivate) {
            throw new IllegalStateException("Cannot qualify as both protected and private");
        }
        this.qualifiedAsProtected = true;
        return this;
    }

    public ModifierBuilder asStatic() {
        this.qualifiedAsStatic = true;
        return this;
    }

    public ModifierBuilder asFinal() {
        if (qualifiedAsAbstract) {
            throw new IllegalStateException("Cannot qualify as both abstract and final");
        }
        this.qualifiedAsFinal = true;
        return this;
    }

    public ModifierBuilder asVolatile() {
        this.qualifiedAsVolatile = true;
        return this;
    }

    public ModifierBuilder asAbstract() {
        this.qualifiedAsAbstract = true;
        return this;
    }

    public ModifierBuilder asNative() {
        this.qualifiedAsNative = true;
        return this;
    }

    public ModifierBuilder asOverride() {
        this.qualifiedAsOverride = true;
        return this;
    }

    public ModifierBuilder combineWith(ModifierBuilder other) {
        qualifiedAsPublic = qualifiedAsPublic || other.qualifiedAsPublic;
        qualifiedAsPrivate = qualifiedAsPrivate || other.qualifiedAsPrivate;
        qualifiedAsProtected = qualifiedAsProtected || other.qualifiedAsProtected;
        qualifiedAsStatic = qualifiedAsStatic || other.qualifiedAsStatic;
        qualifiedAsFinal = qualifiedAsFinal || other.qualifiedAsFinal;
        qualifiedAsVolatile = qualifiedAsVolatile || other.qualifiedAsVolatile;
        qualifiedAsAbstract = qualifiedAsAbstract || other.qualifiedAsAbstract;
        qualifiedAsNative = qualifiedAsNative || other.qualifiedAsNative;
        qualifiedAsOverride = qualifiedAsOverride || other.qualifiedAsOverride;
        return this;
    }

    @Override
    void buildImpl(IndentingStringBuilder sb) {
        if (qualifiedAsPublic) {
            sb.appendSpace(PUBLIC);
        } else if (qualifiedAsPrivate) {
            sb.appendSpace(PRIVATE);
        } else if (qualifiedAsProtected) {
            sb.appendSpace(PROTECTED);
        }

        if (qualifiedAsAbstract) {
            sb.appendSpace(ABSTRACT);
        }

        if (qualifiedAsStatic) {
            sb.appendSpace(STATIC);
        }

        if (qualifiedAsFinal) {
            sb.appendSpace(FINAL);
        }

        if (qualifiedAsVolatile) {
            sb.appendSpace(VOLATILE);
        }

        if (qualifiedAsNative) {
            sb.appendSpace(NATIVE);
        }

        if (qualifiedAsOverride) {
            sb.appendSpace(OVERRIDE);
        }
    }
}
