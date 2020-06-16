# agent-script.R
cat("R: Initializing GraalVM Insight script\n", file=stderr())

agent@on('source', function(env) {
    cat("R: observed loading of ", env$name, "\n", file=stderr())
})

cat("R: Hooks are ready!\n", file=stderr())
