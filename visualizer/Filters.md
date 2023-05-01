# Filter API Documentation

Here you can find documentation for Filter API of Ideal Graph Visualizer.   
Custom filters are powerful tool which let you write your own code in JavaScript   
to allow almost unbound manipulation and analysis of GraalVM graphs in Ideal Graph Visualizer.

## Helpers

Our Filter API comes with various prepared helper functions to speedup writing of your own filters,   
helpers are written against [imported classes](#imported-classes) listed below.   
   
- `classSimpleName(className: string): string;`   
returns regexp for easier matching of "class" property to class name (matches all namespaces)<p>&nbsp;</p>
- `colorize(propertyName: string, valueRegexp: string, color: Color);`   
colorizes nodes with matching property value to a color,   
there are already prepared [colors](#global-colors) in [global variables](#global-variables):   
-- black, white, gray, darkGray, lightGray, red, green, blue, cyan, magenta, yellow, orange, pink   
or you can create your own (viz [imported classes](#imported-classes)): `var color = new Color(r, g, b);`<p>&nbsp;</p>
- `colorizeGradient(propertyName: string, min: number, max: number);`   
colorizes nodes with property in gradient manner on interval \[min, max\] (expects value to be a number)   
default coloring goes from `blue` through `yellow` to `red` in linear fassion.<p>&nbsp;</p>
- `colorizeGradientWithMode(propertyName: string, min: number, max: number, gradientMode: string);`   
allows to set traversion between colors to either `"LINEAR"` or `"LOGARITHMIC"` mode.<p>&nbsp;</p>
- `colorizeGradientCustom(propertyName: string, min: number, max: number, gradientMode: string, colors: Color[], fractions: number[], shades: integer);`   
allows to further specify:   
-- `colors`: used colors in order from minimal to maximal value   
-- `fractions`: positions of abovementioned colors on color gradient scale (first is 0, last 1)   
-- `shades`: number of used color shades<p>&nbsp;</p>
- `remove(propertyName: string, valueRegexp: string);`   
removes nodes with matching property value<p>&nbsp;</p>
- `removeIncludingOrphans(propertyName: string, valueRegexp: string);`   
removes nodes with matching property value and possible orphaned nodes<p>&nbsp;</p>
- `removeInputs(propertyName: string, valueRegexp: string, fromIndex: integer = 0, toIndex: integer = Integer.MAX_VALUE);`   
removes input connections of nodes with matching property value   
-- `fromIndex` and `toIndex` denotes starting and ending index of inputs to remove<p>&nbsp;</p>
- `removeUnconectedSlots(removeInputs: boolean, removeOutputs: boolean);`   
removes input/output slots that doesn't have a connection<p>&nbsp;</p>
- `split(propertyName: string, valueRegexp: string, nodeText: string);`   
removes nodes with matching property value and turns them into names slots on predecessor and successor nodes
-- node text allow to extract and use property values as name text by format: "[propertyName]"

## <a name=global-variables></a>Defined global variables

### <a name=global-colors></a>Colors

|Variable name|Content|
|:----:|:----:|
|black|Color.static.black|
|blue|Color.static.blue|
|cyan|Color.static.cyan|
|darkGray|Color.static.darkGray|
|gray|Color.static.gray|
|green|Color.static.green|
|lightGray|Color.static.lightGray|
|magenta|Color.static.magenta|
|orange|Color.static.orange|
|pink|Color.static.pink
|red|Color.static.red|
|yellow|Color.static.yellow|
|white|Color.static.white|

### <a name=global-others></a>Others

|Variable name|Content|Description|
|:----:|:----:|:----:|
|IO|PrintStream|output stream|
|graph|Diagram|current graph|

## <a name=imported-classes></a>Imported classes

Classes imported from Ideal Graph Visualizer Java codebase to allow their creation.   

|JS Class|Java Class|
|:----:|:----:|
|Color|java.awt.Color|
|||
|GraphDocument|org.graalvm.visualizer.data.GraphDocument|
|InputGraph|org.graalvm.visualizer.data.InputGraph|
|||
|Diagram|org.graalvm.visualizer.graph.Diagram|
|Block|org.graalvm.visualizer.graph.Block|
|Figure|org.graalvm.visualizer.graph.Figure|
|FigureSource|org.graalvm.visualizer.graph.FigureSource|
|InputSlot|org.graalvm.visualizer.graph.InputSlot|
|OutputSlot|org.graalvm.visualizer.graph.OutputSlot|
|Connection|org.graalvm.visualizer.graph.Connection|
|||
|MatcherSelector|org.graalvm.visualizer.graph.MatcherSelector|
|InvertSelector|org.graalvm.visualizer.graph.InvertSelector|
|AndSelector|org.graalvm.visualizer.graph.AndSelector|
|OrSelector|org.graalvm.visualizer.graph.OrSelector|
|PredecessorSelector|org.graalvm.visualizer.graph.PredecessorSelector|
|SuccessorSelector|org.graalvm.visualizer.graph.SuccessorSelector|
|||
|Properties|org.graalvm.visualizer.data.Properties|
|Property|org.graalvm.visualizer.data.Property|
|||
|PropertySelector|org.graalvm.visualizer.data.Properties$PropertySelector|
|RegexpPropertyMatcher|org.graalvm.visualizer.data.Properties$RegexpPropertyMatcher|
|EqualityPropertyMatcher|org.graalvm.visualizer.data.Properties$EqualityPropertyMatcher|
|InvertPropertyMatcher|org.graalvm.visualizer.data.Properties$InvertPropertyMatcher|
|||
|ColorFilter|org.graalvm.visualizer.filter.ColorFilter|
|ColorRule|org.graalvm.visualizer.filter.ColorFilter$ColorRule|
|CombineFilter|org.graalvm.visualizer.filter.CombineFilter|
|ConnectionFilter|org.graalvm.visualizer.filter.ConnectionFilter|
|CustomFilter|org.graalvm.visualizer.filter.CustomFilter|
|EdgeColorIndexFilter|org.graalvm.visualizer.filter.EdgeColorIndexFilter|
|GradientColorFilter|org.graalvm.visualizer.filter.GradientColorFilter|
|RemoveFilter|org.graalvm.visualizer.filter.RemoveFilter|
|RemoveRule|org.graalvm.visualizer.filter.RemoveFilter$RemoveRule|
|RemoveInputsFilter|org.graalvm.visualizer.filter.RemoveInputsFilter|
|RemoveInputsRule|org.graalvm.visualizer.filter.RemoveInputsFilter$RemoveInputsRule|
|RemoveSelfLoopsFilter|org.graalvm.visualizer.filter.RemoveSelfLoopsFilter|
|SplitFilter|org.graalvm.visualizer.filter.SplitFilter|
|UnconnectedSlotFilter|org.graalvm.visualizer.filter.UnconnectedSlotFilter|