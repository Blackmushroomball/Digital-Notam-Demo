# Straight Line

The gml:GeodesicString shall be used as default encoding for straight lines. The reasons are:

- A geodesic interpolation is the mathematical generalization of the notion of “straight line” on the surface of the Earth;
- The result of the interpolation on the surface of the Earth does not depend on the CRS used; by contrast, a linear interpolation would result on different curves on the surface of the Earth, depending on the CRS used (in fact, this will be exploited in order to encode lines along a parallel, see further down).

The pairs of lat/long coordinates can be encoded as either a sequence of gml:pos or, more compact, using a gml:posList element:

```xml
<aixm:Surface gml:id="S01" srsName="urn:ogc:def:crs:EPSG::4326">
    <gml:patches>
        <gml:PolygonPatch>
            <gml:exterior>
                <gml:Ring>
                    <gml:curveMember>
                        <gml:Curve gml:id="C001">
                            <gml:segments>
                                <gml:GeodesicString>
                                    <gml:posList>52.18556 5.20833 52.20611 5.2875 52.18917
                                    5.29889 52.16917 5.29889 52.18556 5.20833</gml:posList>
                                </gml:GeodesicString>
                            </gml:segments>
                        </gml:Curve>
                    </gml:curveMember>
                </gml:Ring>
            </gml:exterior>
        </gml:PolygonPatch>
    </gml:patches>
</aixm:Surface>
```