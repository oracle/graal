print("Python: Insight version {} is launching".format(insight.version))
insight.on("source", lambda env : print("Python: observed loading of {}".format(env.name)))
print("Python: Hooks are ready!")

def onEnter(ctx, frame):
    print('minusOne {}'.format(frame.n))

class Roots:
    roots = True

    def sourceFilter(self, src):
        return src.name == 'agent-fib.js'

    def rootNameFilter(self, n):
        return n == "minusOne"

insight.on("enter", onEnter, Roots())