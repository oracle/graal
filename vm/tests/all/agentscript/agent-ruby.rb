puts("Ruby: Insight version " + insight[:version] + " is launching")

insight.on("source", -> (env) { 
  puts "Ruby: observed loading of " + env[:name]
})
puts("Ruby: Hooks are ready!")

config = Truffle::Interop.hash_keys_as_members({
  roots: true,
  rootNameFilter: "minusOne",
  sourceFilter: -> (src) {
    return src[:name] == "agent-fib.js"
  }
})

insight.on("enter", -> (ctx, frame) {
    puts("minusOne " + frame[:n].to_s)
}, config)
