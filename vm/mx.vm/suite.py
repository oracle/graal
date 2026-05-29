suite = {
    "name": "vm",
    "mxversion": "7.67.0",
    "version_from" : "sdk",
    "release_from" : "sdk",
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
                "version": "e440a712bb04c2d9775ba15278b90670fb1e1fed",
                "urls" : [
                    {"url" : "https://github.com/graalvm/graaljs.git", "kind" : "git"},
                ]
            },
            {
                "name": "graal-js",
                "subdir": True,
                "dynamic": True,
                "version": "e440a712bb04c2d9775ba15278b90670fb1e1fed",
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
                "version": "55d6f652f4fed55a503624b1a8b2cdd3319e6126",
                "dynamic": True,
                "urls": [
                    {"url": "https://github.com/graalvm/graalpython.git", "kind": "git"},
                ]
            },
            {
                "name": "polybenchmarks",
                "version": "692ba4ccdb4ca317525ea342940cf96c45806c6e",
                "dynamic": True,
                "urls": [
                    {"url": "https://github.com/graalvm/polybenchmarks.git", "kind": "git"},
                ]
            },
            # dynamic import for the 'barista' bench suite
            {
                "name": "barista",
                "subdir": False,
                "version": "0.6.5",
                "foreign": True, # barista is not an mx suite
                "dynamic": True,
                "urls": [
                    {"url": "https://github.com/barista-benchmarks/barista.git", "kind" : "git"},
                ],
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
