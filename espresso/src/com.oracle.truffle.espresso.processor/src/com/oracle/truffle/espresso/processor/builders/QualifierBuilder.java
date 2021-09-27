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

public final class QualifierBuilder extends AbstractCodeBuilder {
    private boolean qualifiedAsPublic;
    private boolean qualifiedAsPrivate;
    private boolean qualifiedAsStatic;
    private boolean qualifiedAsFinal;
    private boolean qualifiedAsVolatile;
    private boolean qualifiedAsAbstract;
    private boolean qualifiedAsNative;
    private boolean qualifiedAsOverride;

    public QualifierBuilder asPublic() {
        this.qualifiedAsPublic = true;
        return this;
    }

    public QualifierBuilder asPrivate() {
        this.qualifiedAsPrivate = true;
        return this;
    }

    public QualifierBuilder asStatic() {
        this.qualifiedAsStatic = true;
        return this;
    }

    public QualifierBuilder asFinal() {
        this.qualifiedAsFinal = true;
        return this;
    }

    public QualifierBuilder asVolatile() {
        this.qualifiedAsVolatile = true;
        return this;
    }

    public QualifierBuilder asAbstract() {
        this.qualifiedAsAbstract = true;
        return this;
    }

    public QualifierBuilder asNative() {
        this.qualifiedAsNative = true;
        return this;
    }

    public QualifierBuilder asOverride() {
        this.qualifiedAsOverride = true;
        return this;
    }

    public QualifierBuilder combineWith(QualifierBuilder other) {
        qualifiedAsPublic = qualifiedAsPublic || other.qualifiedAsPublic;
        qualifiedAsPrivate = qualifiedAsPrivate || other.qualifiedAsPrivate;
        qualifiedAsStatic = qualifiedAsStatic || other.qualifiedAsStatic;
        qualifiedAsFinal = qualifiedAsFinal || other.qualifiedAsFinal;
        qualifiedAsVolatile = qualifiedAsVolatile || other.qualifiedAsVolatile;
        qualifiedAsAbstract = qualifiedAsAbstract || other.qualifiedAsAbstract;
        qualifiedAsNative = qualifiedAsNative || other.qualifiedAsNative;
        qualifiedAsOverride = qualifiedAsOverride || other.qualifiedAsOverride;
        return this;
    }

    @Override
    String build() {
        StringBuilder sb = new StringBuilder();

        if (qualifiedAsPublic && qualifiedAsPrivate) {
            throw new IllegalStateException("Cannot qualify as both public and private");
        }
        if (qualifiedAsAbstract) {
            if (qualifiedAsPrivate) {
                throw new IllegalStateException("Cannot qualify as both abstract and private");
            }
            if (qualifiedAsStatic) {
                throw new IllegalStateException("Cannot qualify as both abstract and static");
            }
            if (qualifiedAsFinal) {
                throw new IllegalStateException("Cannot qualify as both abstract and final");
            }
        }

        if (qualifiedAsPublic) {
            sb.append("public ");
        } else if (qualifiedAsPrivate) {
            sb.append("private ");
        }

        if (qualifiedAsAbstract) {
            sb.append("abstract ");
        } else if (qualifiedAsStatic) {
            sb.append("static ");
        }

        if (qualifiedAsFinal) {
            sb.append("final ");
        }

        if (qualifiedAsVolatile) {
            sb.append("volatile ");
        }

        if (qualifiedAsNative) {
            sb.append("native ");
        }

        if (qualifiedAsOverride) {
            sb.append("override ");
        }

        return sb.toString();
    }
}
