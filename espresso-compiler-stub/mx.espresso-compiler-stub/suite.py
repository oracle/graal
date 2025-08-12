#
# Copyright (c) 2024, 2024, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.
#
# This code is distributed in the hope that it will be useful, but WITHOUT
# ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
# FITNESS FOR A PARTICULAR PURPOSE.  See the GNU General Public License
# version 2 for more details (a copy is included in the LICENSE file that
# accompanied this code).
#
# You should have received a copy of the GNU General Public License version
# 2 along with this work; if not, write to the Free Software Foundation,
# Inc., 51 Franklin St, Fifth Floor, Boston, MA 02110-1301 USA.
#
# Please contact Oracle, 500 Oracle Parkway, Redwood Shores, CA 94065 USA
# or visit www.oracle.com if you need additional information or have any
# questions.
#

suite = {
    "mxversion": "7.58.0",
    "name": "espresso-compiler-stub",
    "version": "24.2.0",
    "release": False,
    "groupId": "org.graalvm.espresso",
    "url": "https://www.graalvm.org/reference-manual/java-on-truffle/",
    "developer": {
        "name": "GraalVM Development",
        "email": "graalvm-dev@oss.oracle.com",
        "organization": "Oracle Corporation",
        "organizationUrl": "http://www.graalvm.org/",
    },
    "scm": {
        "url": "https://github.com/oracle/graal/tree/master/truffle",
        "read": "https://github.com/oracle/graal.git",
        "write": "git@github.com:oracle/graal.git",
    },

    # ------------- licenses

    "defaultLicense": "GPLv2",

    # ------------- imports

    "imports": {
        "suites": [
            {
                "name": "espresso",
                "subdir": True,
            },
            {
                "name": "compiler",
                "subdir": True,
            },
        ],
    },

    # ------------- projects

    "projects": {
        "com.oracle.truffle.espresso.graal": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "espresso:ESPRESSO_JVMCI",
                "compiler:GRAAL"
            ],
            "requires": [
                "jdk.internal.vm.ci",
            ],
            "requiresConcealed": {
                "jdk.internal.vm.ci": [
                    "jdk.vm.ci.code",
                    "jdk.vm.ci.meta",
                    "jdk.vm.ci.runtime",
                ],
            },
            "javaCompliance": "21+",
            "checkstyle": "com.oracle.truffle.espresso",
        },
    },

    # ------------- distributions

    "distributions": {
        "ESPRESSO_COMPILER_SUPPORT": {
            "native": True,
            "description": "Espresso GraalVM support distribution for the espresso compiler stub",
            "platformDependent": True,
            "layout": {
                "lib/": [
                    "dependency:espresso-compiler-stub:ESPRESSO_GRAAL/*",
                ],
            },
            "maven": False,
        },

        "ESPRESSO_GRAAL": {
            "subDir": "src",
            "moduleInfo": {
                "name": "jdk.graal.compiler.espresso"
            },
            "dependencies": [
                "com.oracle.truffle.espresso.graal",
            ],
            "distDependencies": [
                "compiler:GRAAL",
                "espresso:ESPRESSO_JVMCI",
            ],
            "description": "A dummy GraalJVMCICompiler implementation for Espresso",
            "maven": False,
        },
    }
}
