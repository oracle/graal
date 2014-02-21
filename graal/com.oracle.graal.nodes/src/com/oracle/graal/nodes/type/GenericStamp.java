/*
 * Copyright (c) 2012, 2014, Oracle and/or its affiliates. All rights reserved.
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
package com.oracle.graal.nodes.type;

import com.oracle.graal.api.meta.*;
import com.oracle.graal.graph.*;
import com.oracle.graal.nodes.spi.*;

public final class GenericStamp extends Stamp {

    public enum GenericStampType {
        Dependency, Extension, Condition, Void
    }

    private final GenericStampType type;

    protected GenericStamp(GenericStampType type) {
        this.type = type;
    }

    public GenericStampType type() {
        return type;
    }

    @Override
    public Stamp unrestricted() {
        return this;
    }

    @Override
    public Kind getStackKind() {
        if (type == GenericStampType.Void) {
            return Kind.Void;
        } else {
            return Kind.Illegal;
        }
    }

    @Override
    public PlatformKind getPlatformKind(LIRTypeTool tool) {
        throw GraalInternalError.shouldNotReachHere(type + " stamp has no value");
    }

    @Override
    public ResolvedJavaType javaType(MetaAccessProvider metaAccess) {
        assert type == GenericStampType.Void;
        return metaAccess.lookupJavaType(Void.TYPE);
    }

    @Override
    public String toString() {
        return type.toString();
    }

    @Override
    public boolean alwaysDistinct(Stamp other) {
        return false;
    }

    @Override
    public Stamp meet(Stamp other) {
        if (other instanceof IllegalStamp) {
            return other.join(this);
        }
        if (!(other instanceof GenericStamp) || ((GenericStamp) other).type != type) {
            return StampFactory.illegal(Kind.Illegal);
        }
        return this;
    }

    @Override
    public Stamp join(Stamp other) {
        if (other instanceof IllegalStamp) {
            return other.join(this);
        }
        if (!(other instanceof GenericStamp) || ((GenericStamp) other).type != type) {
            return StampFactory.illegal(Kind.Illegal);
        }
        return this;
    }

    @Override
    public boolean isCompatible(Stamp stamp) {
        if (this == stamp) {
            return true;
        }
        if (stamp instanceof GenericStamp) {
            GenericStamp other = (GenericStamp) stamp;
            return type == other.type;
        }
        return false;
    }

    @Override
    public int hashCode() {
        return 31 + ((type == null) ? 0 : type.hashCode());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }
        if (type != ((GenericStamp) obj).type) {
            return false;
        }
        return true;
    }
}
