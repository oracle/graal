/*
 * Copyright (c) 2019, Oracle and/or its affiliates. All rights reserved.
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
package bench.misc;

import org.openjdk.jmh.annotations.*;

import java.util.Arrays;
import java.util.Random;

enum Employment {
    EMPLOYED,
    UNEMPLOYED
}

@State(Scope.Benchmark)
public class Stream {
    static final int PERSONS_NR = 1_000;
    static final double EMPLOYMENT_RATIO = 0.5;
    static final int MAX_AGE = 100;
    static final int MAX_SALARY = 200_000;
    static Person[] persons = new Person[PERSONS_NR];

    @Setup
    public static void init() {

        /* Create data set with a deterministic random seed. */
        Random random = new Random(42);
        for (int i = 0; i < PERSONS_NR; i++) {
            persons[i] = new Person(
                    random.nextDouble() >= EMPLOYMENT_RATIO ? Employment.EMPLOYED : Employment.UNEMPLOYED,
                    random.nextInt(MAX_SALARY),
                    random.nextInt(MAX_AGE));
        }
    }

    @Benchmark
    public static double bench() {
        return Arrays.stream(persons).filter(p -> p.getEmployment() == Employment.EMPLOYED)
                .filter(p -> p.getSalary() > 100_000)
                .mapToInt(Person::getAge).filter(age -> age >= 40).average().getAsDouble();
    }
}

class Person {
    private final Employment employment;
    private final int age;
    private final int salary;

    public Person(Employment employment, int height, int age) {
        this.employment = employment;
        this.salary = height;
        this.age = age;
    }

    public int getSalary() {
        return salary;
    }

    public int getAge() {
        return age;
    }

    public Employment getEmployment() {
        return employment;
    }
}