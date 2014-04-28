/*
 * Copyright (c) 2009, 2012, Oracle and/or its affiliates. All rights reserved.
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

package com.oracle.graal.compiler.hsail.test.lambda;

import com.oracle.graal.compiler.hsail.test.Vec3;

/**
 * A Body object derived from Vec3 used in NBody tests.
 */
public class Body extends Vec3 {

    public Body(float _x, float _y, float _z, float _m) {
        super(_x, _y, _z);
        m = _m;
        v = new Vec3(0, 0, 0);
    }

    float m;
    Vec3 v;

    public float getX() {
        return x;
    }

    public float getY() {
        return y;
    }

    public float getZ() {
        return z;
    }

    public float getVx() {
        return v.x;
    }

    public float getVy() {
        return v.y;
    }

    public float getVz() {
        return v.z;
    }

    public float getM() {
        return m;
    }

    public void setM(float _m) {
        m = _m;
    }

    public void setX(float _x) {
        x = _x;
    }

    public void setY(float _y) {
        y = _y;
    }

    public void setZ(float _z) {
        z = _z;
    }

    public void setVx(float _vx) {
        v.x = _vx;
    }

    public void setVy(float _vy) {
        v.y = _vy;
    }

    public void setVz(float _vz) {
        v.z = _vz;
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Body)) {
            return false;
        }
        Body oth = (Body) other;
        return (oth.x == x && oth.y == y && oth.z == z && oth.m == m && v.equals(oth.v));
    }

    @Override
    public int hashCode() {
        // TODO Auto-generated method stub
        return super.hashCode();
    }

}
