#
# Copyright (c) 2025, 2025, Oracle and/or its affiliates. All rights reserved.
# DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS FILE HEADER.
#
# This code is free software; you can redistribute it and/or modify it
# under the terms of the GNU General Public License version 2 only, as
# published by the Free Software Foundation.  Oracle designates this
# particular file as subject to the "Classpath" exception as provided
# by Oracle in the LICENSE file that accompanied this code.
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
    "name": "espresso-shared",
    "version" : "26.0.0",
    "release" : False,
    "groupId" : "org.graalvm.espresso",
    "url" : "https://www.graalvm.org/reference-manual/java-on-truffle/",
    "developer" : {
        "name" : "GraalVM Development",
        "email" : "graalvm-dev@oss.oracle.com",
        "organization" : "Oracle Corporation",
        "organizationUrl" : "http://www.graalvm.org/",
    },
    "scm" : {
        "url" : "https://github.com/oracle/graal/tree/master/espresso-shared",
        "read" : "https://github.com/oracle/graal.git",
        "write" : "git@github.com:oracle/graal.git",
    },
    "ignore_suite_commit_info": True,

    # ------------- licenses

    "defaultLicense": "GPLv2-CPE",

    # ------------- imports

    "imports": {
        "suites": [
            {
                "name": "truffle",
                "subdir": True,
            },
            {
                "name" : "sdk",
                "subdir": True,
            },
        ],
    },

    # ------------- projects

    "projects": {
        # Shared .class file parser
        "com.oracle.truffle.espresso.classfile": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "sdk:COLLECTIONS",
                "truffle:TRUFFLE_API",
            ],
            "javaCompliance" : "17+",
            "checkstyle": "com.oracle.truffle.espresso.classfile",
            "checkstyleVersion": "10.21.0",
        },

        # Shared link resolver
        "com.oracle.truffle.espresso.shared": {
            "subDir": "src",
            "sourceDirs": ["src"],
            "dependencies": [
                "com.oracle.truffle.espresso.classfile",
            ],
            "javaCompliance" : "17+",
            "checkstyle": "com.oracle.truffle.espresso.classfile",
        },

        # Shared code shaded for SVM
        "com.oracle.svm.espresso": {
            "class": "EspressoSVMShared",
            "shadedProjects": [
                "com.oracle.truffle.espresso.classfile",
                "com.oracle.truffle.espresso.shared",
            ],
            "dependencies": [
                "sdk:COLLECTIONS",
            ],
            "removeAnnotations": [
                "com.oracle.truffle.api.CompilerDirectives.CompilationFinal",
                "com.oracle.truffle.api.CompilerDirectives.TruffleBoundary",
                "com.oracle.truffle.api.nodes.ExplodeLoop",
            ],
            "packageMap": {
                "com.oracle.truffle.espresso": "com.oracle.svm.espresso",
            },
            "eclipseformat": False,
            "javaCompliance" : "17+",
        },
    },

    # ------------- distributions

    "distributions": {
        "ESPRESSO_SHARED": {
            "moduleInfo" : {
                "name" : "org.graalvm.espresso.shared",
                "exports": [
                    "* to org.graalvm.espresso",
                ],
            },
            "description" : "Espresso shared code for runtime class loading",
            "subDir": "src",
            "dependencies": [
                "com.oracle.truffle.espresso.classfile",
                "com.oracle.truffle.espresso.shared",
            ],
            "distDependencies": [
                "sdk:COLLECTIONS",
                "truffle:TRUFFLE_API",
            ],
            "maven" : {
                "tag": ["default", "public"],
            },
            "useModulePath": True,
            "noMavenJavadoc": True,
        },
        "ESPRESSO_SVM": {
            "moduleInfo" : {
                "name" : "org.graalvm.espresso.shared.svm",
                "exports": [
                    "* to org.graalvm.nativeimage.builder",
                ],
            },
            "description" : "Espresso shared code for runtime class loading (shaded for SVM)",
            "subDir": "src",
            "dependencies": [
                "com.oracle.svm.espresso",
            ],
            "distDependencies": [
                "sdk:COLLECTIONS",
            ],
            "maven" : {
                "tag": ["default", "public"],
            },
            "useModulePath": True,
            "noMavenJavadoc": True,
        },
    }
}
