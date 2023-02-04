A native-image run with the flag `-H:+PrintImageHeapConnectedComponents` will create reports that can help us debug which objects got into the native image heap and why. A component represents a set of objects in the image heap.
The reports divide components into two kinds:
* Special components: Code metadata, Class data, Interned strings table, and Resources
* Connected components: These components consist of objects that refer to one another and are not of special components kind.

When `PrintImageHeapConnectedComponents` flag is enabled, a native-image run will generate three reports with the following names:
* connected_components_{image-name}_{timestamp}.txt
* summary_info_for_every_object_in_connected_components_{image-name}_{timestamp}.json
* access_points_for_connected_components_{image-name}_{timestamp}.json

Connected components are listed in the body of a connected_components_{image-name}_{timestamp}.txt report sorted
in descending order by their sizes.

Here is an example of a connected component summary info in a .connected_components_{image-name}_{timestamp}.txt report:
```
==============================================================================================================================================
================================ ComponentId=21 | Size=3.05KiB | Percentage of total image heap size=0.0866% =================================
   Count     Size   Size%    Cum% Class
      52     1664  53.33%  53.33% java.math.BigInteger
      37      720  23.08%  76.41% java.math.BigInteger[]
      36      576  18.46%  94.87% int[]
       1      160   5.13% 100.00% java.math.BigInteger[][]

Static fields accessing component 21:
	java.math.BigInteger#powerCache

Methods accessing connected component 21:
	java.math.BigInteger#valueOf(long)
	java.math.BigInteger#shiftRightImpl(int)
	java.math.BigInteger#valueOf(long)
	java.math.BigInteger#pow(int)
	java.math.BigInteger#pow(int)
```
We can see that the component size is 3.05KiB and
that it takes 0.0866% of the total image heap size.
Below the connected component summary is the histogram of objects in that connected component. For example, we see
that the component has 52 BigInteger objects, 37 BigInteger[] objects, 36 int[] objects, and 1 BigInteger[][] object.
`Static fields accessing Component 21` segment contains the list of static fields referencing an object from the connected component.
Methods accessing connected components is the list of methods referencing one or more objects from the connected component.

Sometimes too many methods and static fields reference a single component to be displayed in the
`access_points_for_connected_components_{image-name}_{timestamp}.json` report.
For that reason, the report `access_points_for_connected_components_{image-name}_{timestamp}.json` contains, for each
component, a list of method and static field names that reference objects inside a given component.

Report `summary_info_for_every_object_in_connected_components_{image-name}_{timestamp}.json` gives a short summary info
for all objects in the connected_components_{image-name}_{timestamp}.txt report.
`summary_info_for_every_object_in_connected_components_{image-name}_{timestamp}.json` is a json file with the following
structure:
```
{
    "connectedComponents":[
        "componentId":0,
        "sizeInBytes":95920,
        "objects":[
            {
                "className":"[B",
                "identityHashCode":"1118440229",
                "constantValue":"byte[21]{40, 76, 106, 97, 118, ...}",
                "reason":{
                    "kind":"object",
                    "value":"2126035551"
                }
            },
            ...
    ],
    "classData":{
        "sizeInBytes":1245240,
        "objects":[{...},...]
    },
    "codeMetadata":{
        "sizeInBytes":914200,
        "objects":[{...},...]
    },
    "internedStringsTable":{
        "sizeInBytes":17400,
        "objects":[{...},...]
    }
    "resources":{
        "sizeInBytes":0,
        "objects":[]
    }
}
```
Field `connectedComponents` array contains for each component the following fields:
    `componentId` - id from the connected_components_{image-name}_{timestamp}.txt report
    `sizeInBytes` - total size of the component in bytes
    `reason` - is why an object was added to the image heap. The field `kind` can have one of the following values: "object", "method", "staticField", "svmInternal".
                "object" means that another object referenced the object in the image heap, and in that case, the field "value" holds the identityHashCode of that object
                "method" means that the object was added because some method has a reference to it, and in that case, the field "value" holds the method name
                "staticField" means that the object was added because a static field is referencing it and, in that case, the field "value" holds the name of that static field
                "svmInternal" means that the object was added by the internal infrastructure of the native-image
    `objects` - an array of summary info for every object in the component. `identityHashCode` is the objects unique
                identifier. We can use `identityHashCode` to identify why the object was added.
                In the example above we can see that the `reason` that object with an `identityHashCode` value of
                1118440229 was added because it was referenced by another object whose `identityHashCode` value is 2126035551.

Fields `classData`, `codeMetadata`, `resources`, and 'internedStringTable' each contain an array of `objects` that are in these
special component groups and the field `sizeInBytes` which is the total size of the objects in that special group.
