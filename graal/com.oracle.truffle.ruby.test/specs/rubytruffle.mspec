class MSpecScript

  Rubyspec = "rubyspec"

  set :command_line, [
    "rubyspec/command_line"
  ]

  set :language, [
    "rubyspec/language"
  ]

  set :tags_patterns, [
      [/rubyspec/, "tags"],
      [/_spec.rb$/, "_tags.txt"]
  ]

  MSpec.enable_feature :encoding
  MSpec.enable_feature :continuation
  MSpec.enable_feature :fiber
  MSpec.enable_feature :fork
  MSpec.enable_feature :generator

  set :files, get(:command_line) + get(:language)

end
