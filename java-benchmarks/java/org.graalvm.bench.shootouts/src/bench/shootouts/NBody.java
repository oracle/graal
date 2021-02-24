/*
 * Copyright (c) 2021, Oracle and/or its affiliates. All rights reserved.
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
/*
 * Copyright (c) 2004-2008 Brent Fulgham, 2005-2020 Isaac Gouy
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification, are permitted
 * provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this list of conditions
 * and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice, this list of
 * conditions and the following disclaimer in the documentation and/or other materials provided with
 * the distribution.
 *
 * 3. Neither the name "The Computer Language Benchmarks Game" nor the name "The Benchmarks Game" nor
 * the name "The Computer Language Shootout Benchmarks" nor the names of its contributors may be used
 * to endorse or promote products derived from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR
 * IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY,
 * WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY
 * WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package bench.shootouts;
/*
 * The Computer Language Benchmarks Game
 * http://benchmarksgame.alioth.debian.org/
 *
 * contributed by Mark C. Lewis
 * modified slightly by Chad Whipkey
 * modified slightly by Stefan Feldbinder
 * modified slightly by Tagir Valeev
 */

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

@State(Scope.Benchmark)
public class NBody {

    @Param("100000")
    static int nBodyN;

    @Benchmark
    public static void bench(Blackhole blackhole) {
        NBodySystem bodies = new NBodySystem();
        blackhole.consume(bodies.energy());
        for (int i = 0; i < nBodyN; ++i)
            bodies.advance(0.01);
        blackhole.consume(bodies.energy());
    }
}

final class NBodySystem {
    private static final int LENGTH = 5;

    private Body[] bodies;

    public NBodySystem() {
        bodies = new Body[]{
                Body.sun(),
                Body.jupiter(),
                Body.saturn(),
                Body.uranus(),
                Body.neptune()
        };

        double px = 0.0;
        double py = 0.0;
        double pz = 0.0;
        for (int i = 0; i < LENGTH; ++i) {
            px += bodies[i].vx * bodies[i].mass;
            py += bodies[i].vy * bodies[i].mass;
            pz += bodies[i].vz * bodies[i].mass;
        }
        bodies[0].offsetMomentum(px, py, pz);
    }

    public void advance(double dt) {
        Body[] b = bodies;
        for (int i = 0; i < LENGTH - 1; ++i) {
            Body iBody = b[i];
            double iMass = iBody.mass;
            double ix = iBody.x, iy = iBody.y, iz = iBody.z;

            for (int j = i + 1; j < LENGTH; ++j) {
                Body jBody = b[j];
                double dx = ix - jBody.x;
                double dy = iy - jBody.y;
                double dz = iz - jBody.z;

                double dSquared = dx * dx + dy * dy + dz * dz;
                double distance = Math.sqrt(dSquared);
                double mag = dt / (dSquared * distance);

                double jMass = jBody.mass;

                iBody.vx -= dx * jMass * mag;
                iBody.vy -= dy * jMass * mag;
                iBody.vz -= dz * jMass * mag;

                jBody.vx += dx * iMass * mag;
                jBody.vy += dy * iMass * mag;
                jBody.vz += dz * iMass * mag;
            }
        }

        for (int i = 0; i < LENGTH; ++i) {
            Body body = b[i];
            body.x += dt * body.vx;
            body.y += dt * body.vy;
            body.z += dt * body.vz;
        }
    }

    public double energy() {
        double dx, dy, dz, distance;
        double e = 0.0;

        for (int i = 0; i < bodies.length; ++i) {
            Body iBody = bodies[i];
            e += 0.5 * iBody.mass *
                    (iBody.vx * iBody.vx
                            + iBody.vy * iBody.vy
                            + iBody.vz * iBody.vz);

            for (int j = i + 1; j < bodies.length; ++j) {
                Body jBody = bodies[j];
                dx = iBody.x - jBody.x;
                dy = iBody.y - jBody.y;
                dz = iBody.z - jBody.z;

                distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
                e -= (iBody.mass * jBody.mass) / distance;
            }
        }
        return e;
    }
}

final class Body {
    static final double PI = 3.141592653589793;
    static final double SOLAR_MASS = 4 * PI * PI;
    static final double DAYS_PER_YEAR = 365.24;

    public double x, y, z, vx, vy, vz, mass;

    public Body() {
    }

    static Body jupiter() {
        Body p = new Body();
        p.x = 4.84143144246472090e+00;
        p.y = -1.16032004402742839e+00;
        p.z = -1.03622044471123109e-01;
        p.vx = 1.66007664274403694e-03 * DAYS_PER_YEAR;
        p.vy = 7.69901118419740425e-03 * DAYS_PER_YEAR;
        p.vz = -6.90460016972063023e-05 * DAYS_PER_YEAR;
        p.mass = 9.54791938424326609e-04 * SOLAR_MASS;
        return p;
    }

    static Body saturn() {
        Body p = new Body();
        p.x = 8.34336671824457987e+00;
        p.y = 4.12479856412430479e+00;
        p.z = -4.03523417114321381e-01;
        p.vx = -2.76742510726862411e-03 * DAYS_PER_YEAR;
        p.vy = 4.99852801234917238e-03 * DAYS_PER_YEAR;
        p.vz = 2.30417297573763929e-05 * DAYS_PER_YEAR;
        p.mass = 2.85885980666130812e-04 * SOLAR_MASS;
        return p;
    }

    static Body uranus() {
        Body p = new Body();
        p.x = 1.28943695621391310e+01;
        p.y = -1.51111514016986312e+01;
        p.z = -2.23307578892655734e-01;
        p.vx = 2.96460137564761618e-03 * DAYS_PER_YEAR;
        p.vy = 2.37847173959480950e-03 * DAYS_PER_YEAR;
        p.vz = -2.96589568540237556e-05 * DAYS_PER_YEAR;
        p.mass = 4.36624404335156298e-05 * SOLAR_MASS;
        return p;
    }

    static Body neptune() {
        Body p = new Body();
        p.x = 1.53796971148509165e+01;
        p.y = -2.59193146099879641e+01;
        p.z = 1.79258772950371181e-01;
        p.vx = 2.68067772490389322e-03 * DAYS_PER_YEAR;
        p.vy = 1.62824170038242295e-03 * DAYS_PER_YEAR;
        p.vz = -9.51592254519715870e-05 * DAYS_PER_YEAR;
        p.mass = 5.15138902046611451e-05 * SOLAR_MASS;
        return p;
    }

    static Body sun() {
        Body p = new Body();
        p.mass = SOLAR_MASS;
        return p;
    }

    Body offsetMomentum(double px, double py, double pz) {
        vx = -px / SOLAR_MASS;
        vy = -py / SOLAR_MASS;
        vz = -pz / SOLAR_MASS;
        return this;
    }
}
