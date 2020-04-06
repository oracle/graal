# agent-script.R
cat("R: Initializing GraalVM Insight script\n")

agent@on('source', function(env) {
    cat("R: observed loading of ", env$name, "\n")
})

cat("R: Hooks are ready!\n")
