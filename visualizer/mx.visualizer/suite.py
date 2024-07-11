#
# Copyright (c) 2013, 2023, Oracle and/or its affiliates. All rights reserved.
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

suite = {
  "mxversion": "7.27.0",
  "name" : "visualizer",


  "licenses": {
      "GPLv2": {
          "name": "GNU General Public License, version 2",
          "url": "http://www.gnu.org/licenses/old-licenses/gpl-2.0.html"
      },
  },
  "defaultLicense" : "GPLv2",

  "libraries" : {
    "SPOTBUGS_3.1.11" : {
      "urls" : [
        "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/spotbugs-3.1.11.zip",
        "https://repo.maven.apache.org/maven2/com/github/spotbugs/spotbugs/3.1.11/spotbugs-3.1.11.zip",
      ],
      "digest" : "sha512:98572754ab2df4ebc604d286fb8d83a7a053827d522df933cda3bc51f55f22a4123dad34a92954fdcaa3a81bd41dd466fa7ac1c7e4de980101fecef9905763a9",
    },
    "APACHE_BATIK_SVGGEN_1.12" : {
      "digest" : "sha512:c2519ddbaab53123a5cbfaa04aa388de71996e1af97d51ccf9bb527ca320d7fe3f0d6d0badff4e8138b3b1b0ec3c150a894c9c06c3845318afbc7466395d5565",
      "maven" : {
        "groupId" : "org.apache.xmlgraphics",
        "artifactId" : "batik-svggen",
        "version" : "1.12",
      },
      "license" : "Apache-2.0"
    },
    "OPENIDE_UTIL_LOOKUP" : {
      "moduleName" : "org.openide.util.Lookup",
      "digest" : "sha512:21b4f34d2a5ae6259b4f69d18bcdde176f2298eb80ea4c20c5f24072b968857bb3a16c373cccf9cdf57faccfdfd34531a9a181a0bbf84290ad314756efd35cf9",
      "maven" : {
        "groupId" : "org.netbeans.api",
        "artifactId" : "org-openide-util-lookup",
        "version" : "RELEASE122",
      },
      "license" : "Apache-2.0"
    },
    "OPENIDE_UTIL" : {
      "moduleName" : "org.openide.util",
      "digest" : "sha512:a1647e95633573d81b1b4bf648a834475b54cc0d3025294ecbc83685d62b9c8cc71200cd04728afa0af0ab0503bd9029b15dd587a25285643e2aee2f751944be",
      "maven" : {
        "groupId" : "org.netbeans.api",
        "artifactId" : "org-openide-util",
        "version" : "RELEASE122",
      },
      "license" : "Apache-2.0"
    },

    "NETBEANS_14" : {
      "digest" : "sha512:004b943a2542bd98784a3821411da3f0b048b9b101cc4699a96b1bf7789c654f4ad6c73a75d51a9cb0f436c421ce32baa0ad440efe461bd3e6a8e5e8e927f5c6",
      "urls" : ["https://archive.apache.org/dist/netbeans/netbeans/14/netbeans-14-bin.zip"],
    },
  },

  "projects" : {

    "IdealGraphVisualizer" : {
      "subDir" : "IdealGraphVisualizer",
      "sourceDirs" : ["src"],
      "checkstyle" : "Data",
      "class": "NetBeansProject",
      "dist" : "true",
      "dependencies" : [
        "libs.batik",
      ],
      "buildCommands" : ["build-zip"]
    },

    "libs.batik" : {
      "subDir" : "IdealGraphVisualizer",
      "sourceDirs" : ["src"],
      "checkstyle" : "Data",
      "class": "NetBeansProject",
      "mxLibs" : [
        "APACHE_BATIK_SVGGEN_1.12",
      ]
    },
  },
  "distributions": {
    "IGV": {
      "native" : True,
      "relpath" : True,
      "dependencies" : [
        "IdealGraphVisualizer",
      ],
    },

    "IGV_DATA_SETTINGS": {
      "dependencies" : [
        "IGV",
      ],
      "layout" : {
        "./": [
          "file:IdealGraphVisualizer/Data/build/classes/*",
          "file:IdealGraphVisualizer/Settings/build/classes/*"
          ],
      },
      "defaultBuild" : False,
    },

    "IGV_JSONEXPORTER": {
      "dependencies" : [
        "IdealGraphVisualizer",
      ],
      "distDependencies": [
        "IGV_DATA_SETTINGS",
        "OPENIDE_UTIL_LOOKUP",
        "OPENIDE_UTIL",
      ],
      "defaultBuild" : False,
    },
  },
}
