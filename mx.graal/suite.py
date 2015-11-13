import mx
JDK9 = mx.get_jdk(tag='default').javaCompliance >= "1.9"

def deps(l):
    """
    If using JDK9, replaces dependencies starting with 'jvmci:' with 'JVMCI'.
    Otherwise, excludes "JVMCI".
    """
    if JDK9:
        res = []
        for e in l:
            if e.startswith("jvmci:"):
                if not "JVMCI" in res:
                    res.append("JVMCI")
            else:
                res.append(e)
        return res
    else:
        return [d for d in l if d != "JVMCI"]

def libs(d):
    """
    If not using JDK9, excludes "JVMCI" library.
    """
    if not JDK9:
        del d["JVMCI"]
    return d

def suites(l):
    """ Filters out suites named 'jvmci' if using JDK9. """
    return [s for s in l if not JDK9 or not s.get('name') == "jvmci"]

suite = {
  "mxversion" : "5.5.14",
  "name" : "graal",

  "imports" : {
    "suites": suites([
            {
               "name" : "jvmci",
               "optional" : "true",
               "version" : "2dea101cdfe9aacf55083cf5bd6f84cb23106f4e",
               "urls" : [
                    {"url" : "http://lafo.ssw.uni-linz.ac.at/hg/graal-jvmci-8", "kind" : "hg"},
                    {"url" : "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind" : "binary"},
                ]
            },
            {
               "name" : "truffle",
               "version" : "0d4b0e4263ee95e3b0576409d7311489d4f5d463",
               "urls" : [
                    {"url" : "http://lafo.ssw.uni-linz.ac.at/hg/truffle", "kind" : "hg"},
                    {"url" : "https://curio.ssw.jku.at/nexus/content/repositories/snapshots", "kind" : "binary"},
                ]
            },
    ])
   },

  "defaultLicense" : "GPLv2-CPE",

  "libraries" : libs({

    # ------------- Libraries -------------

    "DACAPO" : {
      "urls" : [
        "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/dacapo-9.12-bach.jar",
        "http://softlayer.dl.sourceforge.net/project/dacapobench/9.12-bach/dacapo-9.12-bach.jar",
      ],
      "sha1" : "2626a9546df09009f6da0df854e6dc1113ef7dd4",
    },

    "DACAPO_SCALA" : {
      "urls" : [
        "https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/dacapo-scala-0.1.0-20120216.jar",
        "http://repo.scalabench.org/snapshots/org/scalabench/benchmarks/scala-benchmark-suite/0.1.0-SNAPSHOT/scala-benchmark-suite-0.1.0-20120216.103539-3.jar",
      ],
      "sha1" : "59b64c974662b5cf9dbd3cf9045d293853dd7a51",
    },

    "JAVA_ALLOCATION_INSTRUMENTER" : {
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/java-allocation-instrumenter/java-allocation-instrumenter-8f0db117e64e.jar"],
      "sha1" : "476d9a44cd19d6b55f81571077dfa972a4f8a083",
      "bootClassPathAgent" : "true",
    },

    "JMH" : {
      "sha1" : "be2e08e6776191e9c559a65b7d34e92e86b4fa5c",
      "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/jmh/jmh-runner-1.10.4.jar"],
    },

    # Library that allows Graal to compile against JVMCI without the jvmci suite.
    # This library is not added to the boot class path at run time and so code
    # compiled against this library must be run on (JVMCI enabled) JDK9.
    "JVMCI" : {
        "sha1" : "9482b9ba7760c09cd95d78f08ce28171b4081268",
        "urls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/jvmci-1648ffb0158e.jar"],
        "sourceSha1" : "b34c184cb1d383aec07bcadefadff01543f10222",
        "sourceUrls" : ["https://lafo.ssw.uni-linz.ac.at/pub/graal-external-deps/jvmci-1648ffb0158e.src.zip"],
        "license": "GPLv2-CPE",
     },
  }),

  "projects" : {

    # ------------- NFI -------------

    "com.oracle.nfi" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.7",
    },

    "com.oracle.nfi.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["test"],
      "dependencies" : deps([
        "com.oracle.nfi",
        "jvmci:JVMCI_API",
        "mx:JUNIT",
      ]),
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
    },

    # ------------- Graal -------------

    "com.oracle.graal.debug" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "checkstyle" : "com.oracle.graal.graph",
      "dependencies" : deps([
        "jvmci:JVMCI_API",
      ]),
      "annotationProcessors" : deps(["jvmci:JVMCI_OPTIONS_PROCESSOR"]),
      "javaCompliance" : "1.8",
      "workingSets" : "JVMCI,Debug",
    },

    "com.oracle.graal.debug.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JUNIT",
        "com.oracle.graal.debug",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "JVMCI,Debug,Test",
    },

    "com.oracle.graal.code" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : deps([
        "jvmci:JVMCI_SERVICE",
        "jvmci:JVMCI_API",
      ]),
      "annotationProcessors" : deps(["jvmci:JVMCI_SERVICE_PROCESSOR"]),
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal",
    },

    "com.oracle.graal.api.collections" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "API,Graal",
    },

    "com.oracle.graal.api.directives" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "API,Graal",
    },

    "com.oracle.graal.api.directives.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "checkstyle" : "com.oracle.graal.graph",
      "dependencies" : [
        "com.oracle.graal.compiler.test",
      ],
      "javaCompliance" : "1.8",
      "workingSets" : "API,Graal",
    },

    "com.oracle.graal.api.runtime" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : deps([
        "jvmci:JVMCI_API",
      ]),
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "API,Graal",
    },

    "com.oracle.graal.api.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JUNIT",
        "com.oracle.graal.api.runtime",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "API,Graal,Test",
    },

    "com.oracle.graal.api.replacements" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : deps(["jvmci:JVMCI_API"]),
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "API,Graal,Replacements",
    },

    "com.oracle.graal.hotspot" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : deps([
        "jvmci:JVMCI_HOTSPOT",
        "com.oracle.graal.api.runtime",
        "com.oracle.graal.replacements",
        "com.oracle.graal.runtime",
        "com.oracle.graal.code",
      ]),
      "checkstyle" : "com.oracle.graal.graph",
      "annotationProcessors" : deps([
        "GRAAL_NODEINFO_PROCESSOR",
        "GRAAL_COMPILER_MATCH_PROCESSOR",
        "GRAAL_REPLACEMENTS_VERIFIER",
        "jvmci:JVMCI_OPTIONS_PROCESSOR",
        "jvmci:JVMCI_SERVICE_PROCESSOR",
      ]),
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,HotSpot",
    },

    "com.oracle.graal.hotspot.amd64" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.compiler.amd64",
        "com.oracle.graal.hotspot",
        "com.oracle.graal.replacements.amd64",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "annotationProcessors" : deps([
        "jvmci:JVMCI_SERVICE_PROCESSOR",
        "GRAAL_NODEINFO_PROCESSOR"
      ]),
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,HotSpot,AMD64",
    },

    "com.oracle.graal.hotspot.sparc" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.hotspot",
        "com.oracle.graal.compiler.sparc",
        "com.oracle.graal.replacements.sparc",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "annotationProcessors" : deps(["jvmci:JVMCI_SERVICE_PROCESSOR"]),
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,HotSpot,SPARC",
    },

    "com.oracle.graal.hotspot.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.replacements.test",
        "com.oracle.graal.hotspot",
      ],
      "annotationProcessors" : ["GRAAL_NODEINFO_PROCESSOR"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,HotSpot,Test",
    },

    "com.oracle.graal.hotspot.amd64.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.asm.amd64",
        "com.oracle.graal.hotspot.test",
      ],
      "annotationProcessors" : ["GRAAL_NODEINFO_PROCESSOR"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,HotSpot,AMD64,Test",
    },

    "com.oracle.graal.nodeinfo" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Graph",
    },

    "com.oracle.graal.nodeinfo.processor" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "checkstyle" : "com.oracle.graal.graph",
      "dependencies" : [
        "com.oracle.graal.nodeinfo",
      ],
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Graph",
    },

    "com.oracle.graal.graph" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.nodeinfo",
        "com.oracle.graal.compiler.common",
        "com.oracle.graal.api.collections",
      ],
      "javaCompliance" : "1.8",
      "annotationProcessors" : deps([
        "jvmci:JVMCI_OPTIONS_PROCESSOR",
        "GRAAL_NODEINFO_PROCESSOR"
      ]),
      "workingSets" : "Graal,Graph",
    },

    "com.oracle.graal.graph.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "checkstyle" : "com.oracle.graal.graph",
      "dependencies" : [
        "mx:JUNIT",
        "com.oracle.graal.api.test",
        "com.oracle.graal.graph",
      ],
      "annotationProcessors" : ["GRAAL_NODEINFO_PROCESSOR"],
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Graph,Test",
    },

    "com.oracle.graal.asm" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : deps(["jvmci:JVMCI_API"]),
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Assembler",
    },

    "com.oracle.graal.asm.amd64" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.asm",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Assembler,AMD64",
    },

    "com.oracle.graal.asm.sparc" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.asm",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Assembler,SPARC",
    },

    "com.oracle.graal.bytecode" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Java",
    },

    "com.oracle.graal.asm.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.code",
        "com.oracle.graal.test",
        "com.oracle.graal.debug",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Assembler,Test",
    },

    "com.oracle.graal.asm.amd64.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.asm.test",
        "com.oracle.graal.asm.amd64",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Assembler,AMD64,Test",
    },

    "com.oracle.graal.lir" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.compiler.common",
        "com.oracle.graal.asm",
      ],
      "annotationProcessors" : deps(["jvmci:JVMCI_OPTIONS_PROCESSOR"]),
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,LIR",
    },

    "com.oracle.graal.lir.jtt" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.jtt",
      ],
      "annotationProcessors" : ["GRAAL_NODEINFO_PROCESSOR"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,LIR",
      "findbugs" : "false",
    },

    "com.oracle.graal.lir.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JUNIT",
        "com.oracle.graal.lir",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,LIR",
    },

    "com.oracle.graal.lir.amd64" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.lir",
        "com.oracle.graal.asm.amd64",
      ],
      "annotationProcessors" : deps(["jvmci:JVMCI_OPTIONS_PROCESSOR"]),
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,LIR,AMD64",
    },

    "com.oracle.graal.lir.sparc" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.asm.sparc",
        "com.oracle.graal.lir",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,LIR,SPARC",
    },

    "com.oracle.graal.word" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.graal.nodes"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["GRAAL_NODEINFO_PROCESSOR"],
      "workingSets" : "API,Graal",
    },

    "com.oracle.graal.replacements" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.api.directives",
        "com.oracle.graal.java",
        "com.oracle.graal.loop.phases",
        "com.oracle.graal.word",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "annotationProcessors" : deps([
        "jvmci:JVMCI_OPTIONS_PROCESSOR",
        "GRAAL_REPLACEMENTS_VERIFIER",
        "GRAAL_NODEINFO_PROCESSOR",
      ]),
      "workingSets" : "Graal,Replacements",
    },

    "com.oracle.graal.replacements.amd64" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
          "com.oracle.graal.replacements",
          "com.oracle.graal.lir.amd64",
          "com.oracle.graal.compiler",
          ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "annotationProcessors" : [
        "GRAAL_NODEINFO_PROCESSOR",
      ],
      "workingSets" : "Graal,Replacements,AMD64",
    },

    "com.oracle.graal.replacements.sparc" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
          "com.oracle.graal.replacements",
          "com.oracle.graal.compiler",
          ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Replacements,SPARC",
    },

    "com.oracle.graal.replacements.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.compiler.test",
        "com.oracle.graal.replacements",
      ],
      "annotationProcessors" : ["GRAAL_NODEINFO_PROCESSOR"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Replacements,Test",
      "jacoco" : "exclude",
    },

    "com.oracle.graal.replacements.verifier" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.api.replacements",
        "com.oracle.graal.graph",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Replacements",
    },

    "com.oracle.graal.nodes" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.graph",
        "com.oracle.graal.api.replacements",
        "com.oracle.graal.lir",
        "com.oracle.graal.bytecode",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "annotationProcessors" : [
        "GRAAL_NODEINFO_PROCESSOR",
        "GRAAL_REPLACEMENTS_VERIFIER",
      ],
      "workingSets" : "Graal,Graph",
    },

    "com.oracle.graal.nodes.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.graal.compiler.test"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Graph",
    },

    "com.oracle.graal.phases" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.graal.nodes"],
      "annotationProcessors" : deps(["jvmci:JVMCI_OPTIONS_PROCESSOR"]),
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Phases",
    },

    "com.oracle.graal.phases.common" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.graal.phases"],
      "annotationProcessors" : deps([
        "GRAAL_NODEINFO_PROCESSOR",
        "jvmci:JVMCI_OPTIONS_PROCESSOR"
      ]),
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Phases",
    },

    "com.oracle.graal.phases.common.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.api.test",
        "com.oracle.graal.runtime",
        "mx:JUNIT",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Test",
    },

    "com.oracle.graal.virtual" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.graal.phases.common"],
      "annotationProcessors" : deps([
        "jvmci:JVMCI_OPTIONS_PROCESSOR",
        "GRAAL_NODEINFO_PROCESSOR"
      ]),
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Phases",
    },

    "com.oracle.graal.virtual.bench" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : ["JMH"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["JMH"],
      "workingSets" : "Graal,Bench",
    },

    "com.oracle.graal.microbenchmarks" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "JMH",
        "com.oracle.graal.api.test",
        "com.oracle.graal.java",
        "com.oracle.graal.runtime",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "annotationProcessors" : ["JMH"],
      "workingSets" : "Graal,Bench",
    },

    "com.oracle.graal.loop" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.graal.nodes"],
      "annotationProcessors" : deps(["jvmci:JVMCI_OPTIONS_PROCESSOR"]),
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal",
    },

    "com.oracle.graal.loop.phases" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
	 "com.oracle.graal.loop",
	 "com.oracle.graal.phases.common",
       ],
      "annotationProcessors" : deps(["jvmci:JVMCI_OPTIONS_PROCESSOR"]),
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Phases",
    },

    "com.oracle.graal.compiler" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.virtual",
        "com.oracle.graal.loop.phases",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "annotationProcessors" : deps([
        "jvmci:JVMCI_SERVICE_PROCESSOR",
        "jvmci:JVMCI_OPTIONS_PROCESSOR",
      ]),
      "workingSets" : "Graal",
    },

    "com.oracle.graal.compiler.match.processor" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.compiler",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Codegen",
    },

    "com.oracle.graal.compiler.amd64" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.compiler",
        "com.oracle.graal.lir.amd64",
        "com.oracle.graal.java",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "annotationProcessors" : deps([
        "GRAAL_NODEINFO_PROCESSOR",
        "GRAAL_COMPILER_MATCH_PROCESSOR",
      ]),
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,AMD64",
    },

    "com.oracle.graal.compiler.amd64.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : deps([
        "com.oracle.graal.lir.jtt",
        "com.oracle.graal.lir.amd64",
        "jvmci:JVMCI_HOTSPOT"
      ]),
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,AMD64,Test",
    },

    "com.oracle.graal.compiler.sparc" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.compiler",
        "com.oracle.graal.lir.sparc",
        "com.oracle.graal.java"
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "annotationProcessors" : deps([
        "GRAAL_NODEINFO_PROCESSOR",
        "GRAAL_COMPILER_MATCH_PROCESSOR",
      ]),
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,SPARC",
    },

    "com.oracle.graal.compiler.sparc.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : deps([
        "com.oracle.graal.lir.jtt",
        "jvmci:JVMCI_HOTSPOT"
      ]),
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,SPARC,Test",
    },

    "com.oracle.graal.runtime" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : ["com.oracle.graal.compiler"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal",
    },

    "com.oracle.graal.java" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.phases",
        "com.oracle.graal.graphbuilderconf",
      ],
      "annotationProcessors" : deps(["jvmci:JVMCI_OPTIONS_PROCESSOR"]),
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Java",
    },

    "com.oracle.graal.graphbuilderconf" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.nodes",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Java",
    },

    "com.oracle.graal.compiler.common" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.debug",
      ],
      "annotationProcessors" : deps(["jvmci:JVMCI_OPTIONS_PROCESSOR"]),
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Java",
    },

    "com.oracle.graal.printer" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.code",
        "com.oracle.graal.java",
        "com.oracle.graal.compiler",
      ],
      "annotationProcessors" : deps([
        "jvmci:JVMCI_OPTIONS_PROCESSOR",
        "jvmci:JVMCI_SERVICE_PROCESSOR"
      ]),
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Graph",
    },

    "com.oracle.graal.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "mx:JUNIT",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Test",
    },

    "com.oracle.graal.compiler.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.api.directives",
        "com.oracle.graal.java",
        "com.oracle.graal.test",
        "com.oracle.graal.runtime",
        "com.oracle.graal.graph.test",
        "JAVA_ALLOCATION_INSTRUMENTER",
      ],
      "annotationProcessors" : ["GRAAL_NODEINFO_PROCESSOR"],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Test",
      "jacoco" : "exclude",
    },

    "com.oracle.graal.jtt" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.compiler.test",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Test",
      "jacoco" : "exclude",
      "findbugs" : "false",
    },

    # ------------- GraalTruffle -------------

    "com.oracle.graal.truffle" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "truffle:TRUFFLE_API",
        "com.oracle.graal.api.runtime",
        "com.oracle.graal.runtime",
        "com.oracle.graal.replacements",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "annotationProcessors" : deps([
        "GRAAL_NODEINFO_PROCESSOR",
        "GRAAL_REPLACEMENTS_VERIFIER",
        "jvmci:JVMCI_OPTIONS_PROCESSOR",
        "jvmci:JVMCI_SERVICE_PROCESSOR",
        "truffle:TRUFFLE_DSL_PROCESSOR",
      ]),
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Truffle",
      "jacoco" : "exclude",
    },

    "com.oracle.graal.truffle.test" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.truffle",
        "com.oracle.graal.compiler.test",
        "truffle:TRUFFLE_SL",
      ],
      "annotationProcessors" : [
        "GRAAL_NODEINFO_PROCESSOR",
        "truffle:TRUFFLE_DSL_PROCESSOR"
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal,Truffle,Test",
      "jacoco" : "exclude",
    },

    "com.oracle.graal.truffle.hotspot" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.truffle",
        "com.oracle.graal.hotspot",
        "com.oracle.nfi",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "annotationProcessors" : deps([
        "jvmci:JVMCI_OPTIONS_PROCESSOR",
        "jvmci:JVMCI_SERVICE_PROCESSOR"
      ]),
      "workingSets" : "Graal,Truffle",
    },

    "com.oracle.graal.truffle.hotspot.amd64" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.truffle.hotspot",
        "com.oracle.graal.hotspot.amd64",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "annotationProcessors" : deps([
        "jvmci:JVMCI_SERVICE_PROCESSOR",
      ]),
      "workingSets" : "Graal,Truffle",
    },

    "com.oracle.graal.truffle.hotspot.sparc" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.truffle.hotspot",
        "com.oracle.graal.asm.sparc",
      ],
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "annotationProcessors" : deps(["jvmci:JVMCI_SERVICE_PROCESSOR"]),
      "workingSets" : "Graal,Truffle,SPARC",
    },

    # ------------- Salver -------------

    "com.oracle.graal.salver" : {
      "subDir" : "graal",
      "sourceDirs" : ["src"],
      "dependencies" : [
        "com.oracle.graal.java",
      ],
      "annotationProcessors" : deps([
        "jvmci:JVMCI_OPTIONS_PROCESSOR",
        "jvmci:JVMCI_SERVICE_PROCESSOR",
      ]),
      "checkstyle" : "com.oracle.graal.graph",
      "javaCompliance" : "1.8",
      "workingSets" : "Graal",
    },
  },

  "distributions" : {

    # ------------- Distributions -------------

    "GRAAL_NODEINFO" : {
      "subDir" : "graal",
      "dependencies" : [
        "com.oracle.graal.nodeinfo",
      ],
    },

    "GRAAL_API" : {
      "subDir" : "graal",
      "dependencies" : [
        "com.oracle.graal.api.replacements",
        "com.oracle.graal.api.runtime",
        "com.oracle.graal.graph",
      ],
      "exclude" : deps(["JVMCI"]),
      "distDependencies" : deps([
        "jvmci:JVMCI_API",
        "GRAAL_NODEINFO",
      ]),
    },

    "GRAAL_COMPILER" : {
      "subDir" : "graal",
      "dependencies" : [
        "com.oracle.graal.compiler",
      ],
      "exclude" : deps(["JVMCI"]),
      "distDependencies" : [
        "GRAAL_API",
      ],
    },

    "GRAAL" : {
      "subDir" : "graal",
      "dependencies" : [
        "com.oracle.graal.replacements",
        "com.oracle.graal.runtime",
        "com.oracle.graal.code",
        "com.oracle.graal.printer",
        "com.oracle.graal.compiler.amd64",
        "com.oracle.graal.replacements.amd64",
        "com.oracle.graal.compiler.sparc",
        "com.oracle.graal.replacements.sparc",
        "com.oracle.graal.salver",
      ],
      "exclude" : deps(["JVMCI"]),
      "distDependencies" : [
        "GRAAL_API",
        "GRAAL_COMPILER",
      ],
    },

    "GRAAL_HOTSPOT" : {
      "subDir" : "graal",
      "dependencies" : [
        "com.oracle.graal.hotspot.amd64",
        "com.oracle.graal.hotspot.sparc",
        "com.oracle.graal.hotspot",
      ],
      "exclude" : deps(["JVMCI"]),
      "distDependencies" : deps([
        "jvmci:JVMCI_HOTSPOT",
        "GRAAL_COMPILER",
        "GRAAL",
      ]),
    },

    "GRAAL_TEST" : {
      "subDir" : "graal",
      "dependencies" : [
        "com.oracle.graal.api.test",
        "com.oracle.graal.api.directives.test",
        "com.oracle.graal.asm.amd64.test",
        "com.oracle.graal.compiler.amd64.test",
        "com.oracle.graal.compiler.sparc.test",
        "com.oracle.graal.hotspot.amd64.test",
        "com.oracle.graal.jtt",
        "com.oracle.graal.lir.jtt",
        "com.oracle.graal.lir.test",
        "com.oracle.graal.nodes.test",
        "com.oracle.graal.phases.common.test",
      ],
      "distDependencies" : deps([
        "GRAAL_HOTSPOT",
        "jvmci:JVMCI_HOTSPOT",
      ]),
      "exclude" : deps([
        "mx:JUNIT",
        "JAVA_ALLOCATION_INSTRUMENTER",
        "JVMCI"
      ]),
    },

    "GRAAL_TRUFFLE" : {
      "subDir" : "graal",
      "dependencies" : [
        "com.oracle.graal.truffle",
      ],
      "distDependencies" : [
        "GRAAL",
        "truffle:TRUFFLE_API",
      ],
    },

    "GRAAL_TRUFFLE_HOTSPOT" : {
      "subDir" : "graal",
      "dependencies" : [
        "com.oracle.graal.truffle.hotspot.amd64",
        "com.oracle.graal.truffle.hotspot.sparc"
      ],
      "distDependencies" : [
        "GRAAL_HOTSPOT",
        "GRAAL_TRUFFLE",
        "truffle:TRUFFLE_API",
      ],
    },

    "GRAAL_TRUFFLE_TEST" : {
      "subDir" : "graal",
      "dependencies" : [
        "com.oracle.graal.truffle.test"
      ],
      "distDependencies" : [
        "GRAAL_TEST",
        "GRAAL_TRUFFLE",
        "truffle:TRUFFLE_SL",
      ],
    },

    "GRAAL_NODEINFO_PROCESSOR" : {
      "subDir" : "graal",
      "dependencies" : ["com.oracle.graal.nodeinfo.processor"],
      "distDependencies" : [
        "GRAAL_NODEINFO",
      ],
    },

    "GRAAL_REPLACEMENTS_VERIFIER" : {
      "subDir" : "graal",
      "dependencies" : ["com.oracle.graal.replacements.verifier"],
      "distDependencies" : [
        "GRAAL_API",
      ],
    },

    "GRAAL_COMPILER_MATCH_PROCESSOR" : {
      "subDir" : "graal",
      "dependencies" : ["com.oracle.graal.compiler.match.processor"],
      "distDependencies" : deps([
        "GRAAL_COMPILER",
        "jvmci:JVMCI_SERVICE_PROCESSOR",
      ])
    },
  },
}
