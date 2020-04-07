# agent-script.R
cat("R: Initializing T-Trace script\n")

agent@on('source', function(env) {
    cat("R: observed loading of ", env$name, "\n")
})

cat("R: Hooks are ready!\n")
