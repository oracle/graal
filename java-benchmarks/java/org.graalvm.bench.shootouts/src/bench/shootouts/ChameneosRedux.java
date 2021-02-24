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
 * contributed by Michael Barker
 * modified by Daryl Griffith
 */

import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.infra.Blackhole;

import java.util.concurrent.Exchanger;
import java.util.concurrent.Phaser;
import java.util.concurrent.atomic.AtomicInteger;

@State(Scope.Benchmark)
public class ChameneosRedux {

    @Param("20000")
    static int chameneosReduxN;

    static {

        Colour.BLUE.setColours(Colour.BLUE, Colour.YELLOW, Colour.RED);
        Colour.RED.setColours(Colour.YELLOW, Colour.RED, Colour.BLUE);
        Colour.YELLOW.setColours(Colour.RED, Colour.BLUE, Colour.YELLOW);
    }

    private static Phaser startMeeting(final boolean isFirst
            , final int n
            , final Blackhole blackhole
            , final Colour... colours) {
        final int len = colours.length;
        final MeetingPlace place = new MeetingPlace(n);
        final Creature[] creatures = new Creature[len];
        final Phaser latch = new CreaturePhaser(isFirst, colours, creatures, len, blackhole);

        for (int i = 0; i < creatures.length; i++) {
            creatures[i] = new Creature(place, colours[i], latch);
            creatures[i].start();
        }
        return latch;
    }

    @Benchmark
    public static void bench(Blackhole blackhole) {
        //int n = 20000;

        startMeeting(true, chameneosReduxN, blackhole, Colour.BLUE, Colour.RED, Colour.YELLOW);
        Phaser phaser = startMeeting(false, chameneosReduxN, blackhole, Colour.BLUE, Colour.RED, Colour.YELLOW,
                Colour.RED, Colour.YELLOW, Colour.BLUE, Colour.RED,
                Colour.YELLOW, Colour.RED, Colour.BLUE);
        for (final Colour c1 : Colour.values()) {
            for (final Colour c2 : Colour.values()) {
                StringBuilder sb = new StringBuilder();
                sb.append(c1.toString())
                        .append(" + ")
                        .append(c2.toString())
                        .append(" -> ")
                        .append(c1.complement(c2).toString())
                        .append('\n');
                blackhole.consume(sb.toString());
            }
        }
        phaser.awaitAdvance(phaser.getPhase());
    }

    enum Colour {

        BLUE {
            @Override
            Colour complement(Colour colour) {
                return colour.blue;
            }

            @Override
            public String toString() {
                return "blue";
            }
        },
        RED {
            @Override
            Colour complement(Colour colour) {
                return colour.red;
            }

            @Override
            public String toString() {
                return "red";
            }
        },
        YELLOW {
            @Override
            Colour complement(Colour colour) {
                return colour.yellow;
            }

            @Override
            public String toString() {
                return "yellow";
            }
        };

        private Colour blue;
        private Colour red;
        private Colour yellow;

        private void setColours(Colour blue, Colour red, Colour yellow) {
            this.blue = blue;
            this.red = red;
            this.yellow = yellow;
        }

        abstract Colour complement(Colour colour);
    }

    static final class CreatureExchange {

        Colour colour;
        int id;
    }

    static final class MeetingPlace {

        private final Exchanger<CreatureExchange> exchanger = new Exchanger<>();
        private final AtomicInteger meetingsLeft = new AtomicInteger();

        public MeetingPlace(final int meetings) {
            meetingsLeft.set(meetings + meetings);
        }

        public CreatureExchange meet(final CreatureExchange info) {
            final int meetings = meetingsLeft.decrementAndGet();

            if (meetings >= 0) {
                try {
                    return exchanger.exchange(info);
                } catch (InterruptedException ex) {
                }
            }
            return null;
        }
    }

    static final class Creature extends Thread {

        private final CreatureExchange exchange = new CreatureExchange();
        private final MeetingPlace place;
        private final Phaser phaser;
        private int count = 0;
        private int sameCount = 0;

        public Creature(final MeetingPlace place
                , final Colour colour
                , final Phaser phaser) {
            this.place = place;
            this.phaser = phaser;
            exchange.id = System.identityHashCode(this);
            exchange.colour = colour;
        }

        @Override
        public void run() {
            CreatureExchange otherCreature;

            for (; ; ) {
                otherCreature = place.meet(exchange);
                if (otherCreature == null) {
                    phaser.arrive();
                    break;
                }
                exchange.colour = exchange.colour.complement(otherCreature.colour);
                count++;
                if (exchange.id == otherCreature.id) {
                    sameCount++;
                }
            }
        }

        public int printAndGetCount() {
            return count;
        }

        public int getSameCount() {
            return sameCount;
        }
    }

    final static class CreaturePhaser extends Phaser {

        static final String[] NUMBERS = {
                "zero", "one", "two", "three", "four", "five",
                "six", "seven", "eight", "nine"
        };
        static final Object lock = new Object();
        static boolean firstHasNotFinished = true;
        final boolean isFirst;
        final Colour[] colours;
        final Creature[] creatures;
        final Blackhole blackhole;

        public CreaturePhaser(final boolean isFirst
                , final Colour[] colours
                , final Creature[] creatures
                , final int phases
                , final Blackhole blackhole) {
            super(phases);
            this.isFirst = isFirst;
            this.colours = colours;
            this.creatures = creatures;
            this.blackhole = blackhole;
        }

        @Override
        protected boolean onAdvance(final int phase
                , final int registeredParties) {
            synchronized (lock) {
                if (!isFirst) {
                    while (firstHasNotFinished) {
                        try {
                            lock.wait();
                        } catch (InterruptedException ex) {
                        }
                    }
                }
                for (final Colour colour : colours) {
                    blackhole.consume(colour.toString());
                }

                int total = 0;
                for (final Creature creature : creatures) {
                    total += creature.printAndGetCount();
                    blackhole.consume(getNumber(creature.getSameCount()));
                }
                blackhole.consume(getNumber(total));
                if (isFirst) {
                    firstHasNotFinished = false;
                    lock.notify();
                } else {
                    // reset back to true for subsequent runs
                    firstHasNotFinished = true;
                }
            }
            return true;
        }

        private String getNumber(final int n) {
            final StringBuilder sb = new StringBuilder();
            final String nStr = Integer.toString(n);

            for (int i = 0; i < nStr.length(); i++) {
                sb.append(" ");
                sb.append(NUMBERS[Character.getNumericValue(nStr.charAt(i))]);
            }
            sb.append('\n');
            return sb.toString();
        }
    }
}
