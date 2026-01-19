suite = {
    "name": "vm",
    "version" : "25.1.0",
    "mxversion": "7.67.0",
    "release" : False,
    "groupId" : "org.graalvm",

    "url" : "http://www.graalvm.org/",
    "developer" : {
        "name" : "GraalVM Development",
        "email" : "graalvm-dev@oss.oracle.com",
        "organization" : "Oracle Corporation",
        "organizationUrl" : "http://www.graalvm.org/",
    },
    "scm" : {
      "url" : "https://github.com/oracle/graal",
      "read" : "https://github.com/oracle/graal.git",
      "write" : "git@github.com:oracle/graal.git",
    },
    "capture_suite_commit_info": False,
    "defaultLicense" : "GPLv2-CPE",
    "imports": {
        "suites": [
            {
                "name": "sdk",
                "subdir": True,
            },
            {
                "name": "truffle",
                "subdir": True,
            },
            # Dynamic imports for components:
            {
                "name": "graal-nodejs",
                "subdir": True,
                "dynamic": True,
                "version": "27a07e07862edc287972b6aa41f3e94d78be7a20",
                "urls" : [
                    {"url" : "https://github.com/graalvm/graaljs.git", "kind" : "git"},
                ]
            },
            {
                "name": "graal-js",
                "subdir": True,
                "dynamic": True,
                "version": "27a07e07862edc287972b6aa41f3e94d78be7a20",
                "urls": [
                    {"url": "https://github.com/graalvm/graaljs.git", "kind" : "git"},
                ]
            },
            {
                "name": "fastr",
                "version": "6e5e07a23c5dce133a07701d6c49afcfd9cee86c",
                "dynamic": True,
                "urls": [
                    {"url": "https://github.com/oracle/fastr.git", "kind": "git"},
                ]
            },
            {
                "name": "graalpython",
                "version": "adde3b7b30655a2e556aafc0753a7057baf4220b",
                "dynamic": True,
                "urls": [
                    {"url": "https://github.com/graalvm/graalpython.git", "kind": "git"},
                ]
            },
            {
                "name": "polybenchmarks",
                "version": "a04259d72ab94b6245d3ce775833c8e8a3af42fa",
                "dynamic": True,
                "urls": [
                    {"url": "https://github.com/graalvm/polybenchmarks.git", "kind": "git"},
                ]
            },
        ]
    },
    "distributions": {
        "VM_GRAALVM_SUPPORT": {
            "native": True,
            "description": "VM support distribution for the GraalVM",
            "layout": {
                "./": ["file:GRAALVM-README.md"],
                "LICENSE.txt": "file:LICENSE_GRAALVM_CE",
                "THIRD_PARTY_LICENSE.txt": "file:THIRD_PARTY_LICENSE_CE.txt",
            },
            "maven": False,
        },
    },
}
