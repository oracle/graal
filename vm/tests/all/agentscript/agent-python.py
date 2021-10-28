print(f"Python: Insight version {insight.version} is launching")
insight.on("source", lambda env : print(f"Python: observed loading of {env.name}"))
print("Python: Hooks are ready!")


def onEnter(ctx, frame):
    print(f"minusOne class-based filter {frame.n}")


class Roots:
    roots = True

    def sourceFilter(self, src):
        return src.name == "agent-fib.js"

    def rootNameFilter(self, n):
        return n == "minusOne"


insight.on("enter", onEnter, Roots())


def onEnterWithDict(ctx, frame):
    print(f"minusTwo dict-based filter {frame.n}")


def sourceFilter(src):
    return src.name == "agent-fib.js"


def rootNameFilter(n):
    return n == "minusTwo"


insight.on("enter", onEnterWithDict, dict(
    roots=True,
    sourceFilter=sourceFilter,
    rootNameFilter=rootNameFilter,
))
