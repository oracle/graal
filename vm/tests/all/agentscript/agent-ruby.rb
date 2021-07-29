puts("Ruby: Insight version " + insight.version + " is launching")

insight.on("source", -> (env) {
  if env.name.index('agent-fib') or env.name.end_with?('test.rb')
    puts "Ruby: observed loading of " + env[:name]
  end
})
puts("Ruby: Hooks are ready!")

insight.on("enter", -> (ctx, frame) {
    puts("minusOne " + frame.n.to_s)
}, {
  roots: true,
  rootNameFilter: "minusOne",
  sourceFilter: -> (src) {
    return src.name == "agent-fib.js"
  }
})
