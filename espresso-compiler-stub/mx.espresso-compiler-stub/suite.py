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
        "organizationUrl": "https://www.graalvm.org/",
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
                    "jdk.vm.ci.meta.annotation",
                    "jdk.vm.ci.runtime",
                ],
            },
            "javaCompliance": "21+",
            "checkstyle": "com.oracle.truffle.espresso",
            # Reference to jdk.vm.ci.meta.annotation
            # causes spotbugs analysis to fail due to missing classes
            "spotbugs": "false"
        },

        "com.oracle.truffle.espresso.vmaccess": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "compiler:GRAAL",
                "compiler:VMACCESS",
                "espresso:ESPRESSO_JVMCI",
                "sdk:POLYGLOT",
                "com.oracle.truffle.espresso.graal",
            ],
            "requires": [
                "jdk.internal.vm.ci",
                "java.logging",
            ],
            "requiresConcealed": {
                "jdk.internal.vm.ci": [
                    "jdk.vm.ci.meta",
                    "jdk.vm.ci.meta.annotation",
                    "jdk.vm.ci.code",
                    "jdk.vm.ci.code.site",
                    "jdk.vm.ci.code.stack",
                    "jdk.vm.ci.common",
                    "jdk.vm.ci.amd64",
                    "jdk.vm.ci.aarch64",
                    "jdk.vm.ci.services",
                    "jdk.vm.ci.runtime",
                ],
            },
            "javaCompliance": "21+",
            "checkstyle": "com.oracle.truffle.espresso",
            # Reference to jdk.vm.ci.meta.annotation
            # causes spotbugs analysis to fail due to missing classes
            "spotbugs": "false"
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
                "name": "jdk.graal.compiler.espresso",
                "exports": [
                    "com.oracle.truffle.espresso.graal to jdk.graal.compiler.espresso.vmaccess",
                ],
                "requiresConcealed": {
                    "jdk.graal.compiler": [
                        "jdk.graal.compiler.api.replacements",
                        "jdk.graal.compiler.api.runtime",
                        "jdk.graal.compiler.bytecode",
                        "jdk.graal.compiler.code",
                        "jdk.graal.compiler.core.common",
                        "jdk.graal.compiler.core.common.alloc",
                        "jdk.graal.compiler.core.common.memory",
                        "jdk.graal.compiler.core.common.spi",
                        "jdk.graal.compiler.core.common.type",
                        "jdk.graal.compiler.core.target",
                        "jdk.graal.compiler.debug",
                        "jdk.graal.compiler.graph",
                        "jdk.graal.compiler.nodes",
                        "jdk.graal.compiler.nodes.gc",
                        "jdk.graal.compiler.nodes.graphbuilderconf",
                        "jdk.graal.compiler.nodes.loop",
                        "jdk.graal.compiler.nodes.memory",
                        "jdk.graal.compiler.nodes.memory.address",
                        "jdk.graal.compiler.nodes.spi",
                        "jdk.graal.compiler.options",
                        "jdk.graal.compiler.phases.tiers",
                        "jdk.graal.compiler.phases.util",
                        "jdk.graal.compiler.replacements",
                        "jdk.graal.compiler.runtime",
                        "jdk.graal.compiler.word",
                    ],
                },
            },
            "dependencies": [
                "com.oracle.truffle.espresso.graal",
            ],
            "distDependencies": [
                "compiler:GRAAL",
                "espresso:ESPRESSO_JVMCI",
            ],
            "useModulePath": True,
            "description": "A dummy GraalJVMCICompiler implementation for Espresso",
            "maven": False,
        },

        "ESPRESSO_VMACCESS": {
            "subDir": "src",
            "moduleInfo": {
                "name": "jdk.graal.compiler.espresso.vmaccess",
                "requires": [
                    "jdk.internal.vm.ci",
                    "jdk.graal.compiler",
                    "jdk.graal.compiler.vmaccess",
                    "transitive org.graalvm.polyglot",
                ],
                "exports": [
                    "com.oracle.truffle.espresso.vmaccess",
                ],
                "requiresConcealed": {
                    "jdk.graal.compiler": [
                        "jdk.graal.compiler.api.replacements",
                        "jdk.graal.compiler.core.common.spi",
                        "jdk.graal.compiler.debug",
                        "jdk.graal.compiler.nodes.loop",
                        "jdk.graal.compiler.nodes.spi",
                        "jdk.graal.compiler.phases.util",
                        "jdk.graal.compiler.word",
                    ],
                },
            },
            "dependencies": [
                "com.oracle.truffle.espresso.vmaccess",
            ],
            "distDependencies": [
                "sdk:POLYGLOT",
                "compiler:GRAAL",
                "compiler:VMACCESS",
                "espresso:ESPRESSO_JVMCI",
                "ESPRESSO_GRAAL",
            ],
            "useModulePath": True,
            "maven": False,
        },
    }
}
