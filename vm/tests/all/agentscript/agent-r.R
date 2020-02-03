# agent-script.R
cat("R: Initializing T-Trace script\n")
# print(names(agent)) # <- triggers assertion in T-Trace (getMembers)

agent@on('source', function(env) {
    cat("R: observed loading of ", env$name, "\n")
})

cat("R: Hooks are ready!\n")
