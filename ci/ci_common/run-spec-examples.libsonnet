local tools  = import "tools.libsonnet";
local _impl  = import "run-spec-impl.libsonnet";
local r      = import "run-spec.libsonnet";
{
  examples:: {
    desugaring::
      local _input = {
        "task": r.platform_spec({
            "linux:amd64": r.task_spec({ "target": "gate" }),
            "linux:amd64:jdk19": r.task_spec({ "name": "task" }),
        })
      };
      local _result = {
        "linux": {
          "amd64": {
            "jdk19": r.task_spec({ "name": "task" }) + r.include,
          } +
          r.task_spec({ "target": "gate" }) + r.include,
        }
      };
      std.assertEqual(_impl.get_platform_spec(_impl.desugar_task_dict(_input).task), _result)
    ,
    desugaring_includes::
      local _input = {
        "task": r.platform_spec({
            "<all-os>": r.task_spec({ "target": "gate" }),
            "linux": r.task_spec({ "os": "linux" }),
        })
      };
      local _result = {
        "<all-os>": r.task_spec({ "target": "gate" }) + r.include,
        "linux": r.task_spec({ "os": "linux" }) + r.include,
      };
      std.assertEqual(_impl.get_platform_spec(_impl.desugar_task_dict(_input).task), _result)
    ,
    desugaring_star:
      local _input_star = {
        "task": r.platform_spec({
            "*:amd64:jdk19": r.task_spec({}),
            "linux:*:jdk19": r.task_spec({}),
            "linux:amd64:*": r.task_spec({}),
            "*": r.task_spec({}),
            "*:aarch64": r.task_spec({}),
            "darwin:*": r.task_spec({}),
        })
      };
      local _input_long = {
        "task": r.platform_spec({
            "<all-os>:amd64:jdk19": r.task_spec({}),
            "linux:<all-arch>:jdk19": r.task_spec({}),
            "linux:amd64:<all-jdk>": r.task_spec({}),
            "<all-os>": r.task_spec({}),
            "<all-os>:aarch64": r.task_spec({}),
            "darwin:<all-arch>": r.task_spec({}),
        })
      };
      std.assertEqual(_impl.get_platform_spec(_impl.desugar_task_dict(_input_long).task), _impl.get_platform_spec(_impl.desugar_task_dict(_input_star).task))
    ,
    pushdown::
      local _input = {
        "task": _impl.add_platform_spec({
          "<all-os>" : r.exclude,
          "linux": {
            "amd64": {
              "jdk19": r.task_spec({ "name": "task" }) + r.include,
            } +
            r.task_spec({ "target": "gate" }) + r.include,
          }
        })
      };
      local _result = {
        "linux": {
          "amd64": {
            "jdk11": r.task_spec({
              "target": "gate",
            }),
            "jdk17": r.task_spec({
              "target": "gate",
            }),
            "jdk19": r.task_spec({
              "name": "task",
              "target": "gate",
            }),
          },
        },
      };
      std.assertEqual(_impl.get_platform_spec(_impl.push_down_task_dict(_input).task), _result)
    ,
    pushdown_all_os_exclude::
      local _input = {
        "task": _impl.add_platform_spec({
          "<all-os>" : r.exclude,
          "linux": {
            "amd64": r.include,
          }
        })
      };
      // The exclude is overridden because ["linux"]["amd64] is more specific than ["<all-os>"].
      local _result = {
        "linux": {
          "amd64": {
            "jdk11": {},
            "jdk17": {},
            "jdk19": {},
          },
        },
      };
      std.assertEqual(_impl.get_platform_spec(_impl.push_down_task_dict(_input).task), _result)
    ,
    pushdown_all_jdk_exclude::
      local _input = {
        "task": _impl.add_platform_spec({
          "<all-os>" : {
            "<all-arch>": {
              "<all-jdk>": r.exclude
            }
          },
          "linux": {
            "amd64": r.include + {
              "jdk19": r.include
            },
          }
        })
      };
      // The include on ["linux"]["amd64"] has no effect because ["<all-os>"]["<all-arch>"]["<all-jdk>"]
      // is more specific than ["linux"]["amd64"].
      //
      // The include on ["linux"]["amd64"]["jdk19"] works because it is as specific as the exclude on
      // ["<all-os>"]["<all-arch>"]["<all-jdk>"], but the former is applied later than the wildcard
      // variant.
      local _result = {
        "linux": {
          "amd64": {
            "jdk19": {},
          },
        },
      };
      std.assertEqual(_impl.get_platform_spec(_impl.push_down_task_dict(_input).task), _result)
    ,
    generate_variants::
      // The feature map is a two-level object. The first level is the name of the feature,
      // for example "gc". The second level defines the feature values as well as the build config
      // for each feature value.`For example "serialgc" and "g1gc". Inside the second level comes
      // the build config associated with the feature. It can be specialized by os/arch/jdk, but
      // usually having a top-level "<all-os>" entry is sufficient.
      local _feature_map = {
        gc: {
          serialgc: {
            "<all-os>"+: _impl.exclude + _impl.task_spec({features+:"SerialGC"}),
          },
          g1gc: {
            "<all-os>"+: _impl.exclude + _impl.task_spec({features+:"G1GC"}),
          },
        },
        libc: {
          musl: {
            "<all-os>"+: _impl.exclude + _impl.task_spec({features+:"Musl"}),
          },
        },
      };
      local _input = {
        "job": _impl.generate_variants({
          // * is a wildcard for selecting all feature values
          "gc:*": {
            // this will be expanded to all values of "gc", "serialgc" and "g1gc"
            "linux": {
              "amd64": _impl.include
            }
          },
          // Features can be restricted to certain values using "feature(:value)+".
          "gc:serialgc": {
            // Features can be nested, the following will add a job with serialgc and musl for linux amd64.
            // By default, the features are applied ordered by their feature name (i.e., gc, libc).
            // This order can be overridden by passing an array of feature names to the
            // "generate_variants" call via the "order" parameter.
            "libc:musl": {
              "linux": {
                "amd64": _impl.include
              }
            }
          },
        }, feature_map=_feature_map)
      };
      //
      local _result =
      {
        "gc:serialgc": {
          "<all-os>": {
            "<build-config>": {
              "features": "SerialGC"
            },
            "<exclude>": true
          },
          "linux": {
            "amd64": {
              "<exclude>": false
            }
          }
        },
        "gc:serialgc_libc:musl": {
          "<all-os>": {
            "<build-config>": {
              "features": "SerialGCMusl"
            },
            "<exclude>": true
          },
          "linux": {
            "amd64": {
              "<exclude>": false
            }
          }
        },
        "gc:g1gc": {
          "<all-os>": {
            "<build-config>": {
              "features": "G1GC"
            },
            "<exclude>": true
          },
          "linux": {
            "amd64": {
              "<exclude>": false
            }
          }
        }
      };
      std.assertEqual(_impl.get_platform_spec(_impl.desugar_task_dict(_input).job).variants, _result)
    ,
    expand_variants::
      local _input = {
        "task": _impl.add_platform_spec({
          "linux": {
            "amd64": {
              "jdk19": r.task_spec({ "name": "task" }),
            }
          },
          "variants": {
            "myvariant": {
              "linux": {
                "amd64": {
                  "jdk19": r.task_spec({ "name": self.task_variant}),
                }
              }
            }
          }
        })
      };
      local _result = {
        "linux": {
          "amd64": {
            "jdk19": [
              r.task_spec({"name": "task"}),
              r.task_spec({"name": "myvariant"}),
            ],
          },
        },
      };
      std.assertEqual(_impl.get_platform_spec(_impl.expand_variants_task_dict(_input).task), _result)
    ,
    multiply::
      local _input = {
        "task": r.task_spec({ "name": self.task_name, "platform" : std.join("-", [self.os, self.arch, self.jdk]) }) + _impl.add_platform_spec({
          "linux": {
            "amd64": {
              "jdk19": [r.add_multiply(
                  [
                    r.task_spec({"batch": 1}),
                    r.task_spec({"batch": 2}),
                  ]
                )
              ],
            }
          },
        })
      };
      local _result =  {
        "task": [
          {
            "batch": 1,
            "name": "task",
            "platform": "linux-amd64-jdk19",
          },
          {
            "batch": 2,
            "name": "task",
            "platform": "linux-amd64-jdk19",
          },
        ]
      };
      std.assertEqual(_impl.generate_builds_dict(_input), _result)
    ,
    evaluate_late::
      local _input = {
        "task": [
          {
            "name": "task",
          } + r.evaluate_late({
            "second": function (b) {
                "name" : b.name + "-second"
            },
            "first": function (b) {
                "name" : b.name + "-first"
            },
          }),
        ]
      };
      local _result =  {
        "task": [
          {
            "name": "task-first-second",
          },
        ]
      };
      std.assertEqual(_impl.apply_evaluate_late(_input), _result)
    ,
  }
}
