{
  # composable: make an object composable
  #
  # When composing objects with `+`, the RHS overrides the LHS when fields collide,
  # unless the RHS defines fields with `+:`. This is especially an issue when
  # importing nested objects from JSON, where all fields are implicitly defined
  # with `:`. To solve this, `composable` essentially turns `:` fields into `+:`.
  #
  # See the following example:
  #
  # input.jsonnet
  # <code>
  # {
  #   composable:: ...,
  #   obj1:: {
  #     key1: {
  #       foo: "foo",
  #     },
  #     key2: "key2",
  #   },
  #   obj2:: {
  #     key1: {
  #       baz : "baz",
  #     },
  #     key3: "key3",
  #   },
  #   res1: self.obj1 + self.obj2,
  #   res2: self.composable(self.obj1) + self.composable(self.obj2),
  # }
  # </code>
  #
  # Output of jsonnet input.jsonnet
  # <code>
  # {
  #    "res1": {
  #       "key1": {
  #          "baz": "baz"
  #       },
  #       "key2": "key2",
  #       "key3": "key3"
  #    },
  #    "res2": {
  #       "key1": {
  #          "baz": "baz",
  #          "foo": "foo"
  #       },
  #       "key2": "key2",
  #       "key3": "key3"
  #    }
  # }
  # </code>
  # Note the missing `res1.key1.foo`!
  #
  local _composable(o) =
    std.foldl(function(obj, key)
      obj +
       if std.type(o[key]) == "object" then
         { [key] +: _composable(o[key]) }
       else
         { [key] : o[key] },
      std.objectFields(o),
      {}
    ),
  # exported name
  composable(o) :: _composable(o),

  # prefixes the given number with 'jdk'
  prefixed_jdk(jdk_version)::
    if jdk_version == null || std.length(std.toString(jdk_version)) == 0 then
      null
    else
      "jdk" + std.toString(jdk_version),

  # generate a string of hyphen-separated items from the given list, skipping null values
  hyphenize(a_list)::
    std.join("-", std.filterMap(function(el) el != null, function(el) std.toString(el), a_list)),

  # Pattern for a guard.includes clause that captures all top-level CI files.
  top_level_ci:: ["*.json", "*.jsonnet", "*.libsonnet", "ci/**"],

  # Adds a CI build predicate to `build` if it is a gate such that it is only
  # run if a top level CI file or a non-documentation file in any of `suites` has been updated
  add_gate_predicate(build, suites, extra_includes=[], extra_excludes=[])::
    if std.member(build.targets, "gate") then
    build + {
      guard+: {
        includes+: [ suite + "/**"      for suite in suites ] + extra_includes + $.top_level_ci,
        excludes+: [ suite + "/docs/**" for suite in suites ] + [ "**.md" ] + extra_excludes
      }
    }
  else
    build,

  # std.get is not available in all versions
  std_get(o, f, default=null, inc_hidden=true)::
    local objectHas = if inc_hidden then std.objectHasAll else std.objectHas;
    if objectHas(o, f) then o[f] else default
  ,

  # Makes all properties of an object visible. By default, this function will turn hidden properties to public ones.
  # Some properties, namely functions, cannot be manifested as json. Trying to do so will result in an error.
  # Sometimes, e.g., for error reporting or debugging, it is useful to print it nevertheless, ignoring the
  # functions.
  make_visible(o, inc_hidden=true)::
    local objectFields = if inc_hidden then std.objectFieldsAll else std.objectFields;
    if std.type(o) == "array" then
      [$.make_visible(e, inc_hidden=inc_hidden) for e in o]
    else if std.type(o) == "object" then
      {
        [key] : $.make_visible(o[key], inc_hidden=inc_hidden)
        for key in objectFields(o)
      }
    else if std.type(o) == "function" then
      "<function>"
    else
      o
    ,
}
