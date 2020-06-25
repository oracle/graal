print(f"Python: Insight version {insight.version} is launching")
insight.on("source", lambda env : print(f"Python: observed loading of {env.name}"))
print("Python: Hooks are ready!")

def onEnter(ctx, frame):
    print(f"minusOne {frame.n}")

class Roots:
    roots = True

    def sourceFilter(self, src):
        return src.name == "agent-fib.js"

    def rootNameFilter(self, n):
        return n == "minusOne"

insight.on("enter", onEnter, Roots())