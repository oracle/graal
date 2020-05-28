insight.on("enter", -> a1 {
  puts "What's #{a1}?"
}, Truffle::Interop.hash_keys_as_members(statements: true))
