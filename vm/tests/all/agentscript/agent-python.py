print("Python: Insight version {} is launching".format(insight.version))
insight.on("source", lambda env : print("Python: observed loading of {}".format(env.name)))
print("Python: Hooks are ready!")
