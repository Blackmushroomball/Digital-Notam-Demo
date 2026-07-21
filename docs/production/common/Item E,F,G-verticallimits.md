# Item E, F, G - vertical limits

As explained in the data encoding section, vertical limits are typically encoded in AIXM as a pair of two attributes:

- a `"...Limit"` attribute, which includes the value and its unit of measurement (as an `"uom"` attribute) with the list of values: `UomDistanceVerticalType`;
- a `"...LimitReference"` attribute, which indicates what reference system is used for the vertical limit value. It uses the following list of values: `CodeVerticalReferenceType`.

The following rules shall be applied for the automatic production of the NOTAM text (decoding of the vertical limits):

| `"...Limit"` Value | `"...Limit"` `@uom` | `"...LimitReference"` | Text to be inserted |
|---|---|---|---|
| GND | any value or NIL | any value or NIL | `"SFC"` |
| UNL | any value or NIL | any value or NIL | `"UNL"` |
| FLOOR | any value or NIL | any value or NIL | `"Its lower limit"` (see Notes below) |
| CEILING | any value or NIL | any value or NIL | `"Its upper limit"` (see Notes below) |
| XXXXX | FT | SFC | `"XXXXXFT AGL"` |
| XXXXX | FT | MSL | `"XXXXXFT AMSL"` |
| XXXXX | FT | W84 | `"XXXXXFT above WGS-84 ellipsoid"` |
| XXXXX | M | SFC | `"XXXXXM AGL"` |
| XXXXX | M | MSL | `"XXXXXM AMSL"` |
| XXXXX | M | W84 | `"XXXXXM above WGS-84 ellipsoid"` |
| XXX | FL | STD | `"FLXXX"` |
| XXX | SM | STD | `"SMXXX"` |

## Notes

- The values FLOOR and CEILING will not occur in the items F or G, just in the decoding of vertical limits in item E of some particular scenarios;
- The decoding for the values having as reference `'W84'` is provided for completeness sake, but they will not occur in the current practice;
- The decoding of `"standard meters"` (SM) is similar to the decoding of the FL values; although this value is not mentioned in the OPADD, there is no reason to exclude it from this decoding, for those regions that use SM instead of FL.
