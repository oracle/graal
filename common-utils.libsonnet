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
    std.join("-", std.filterMap(function(el) el != null, function(el) std.toString(el), a_list))
}
