# Item C - end of validity

There exist three situations, depending on the values of the feature `...TimeSlice.validTime.TimePeriod.endPosition` and of the associated `Event.estimatedValidity` attribute:

| `...TimeSlice.validTime.TimePeriod.endPosition` | `...TimeSlice.validTime.TimePeriod.endPosition@indeterminatePosition` | `Event.estimatedValidity` | item C value |
|---|---|---|---|
| `NIL` | `'unknown'` | `NIL` | **PERM** |
| `NIL` | `'unknown'` | specified | `Event.estimatedValidity` value formatted according to the NOTAM syntax for this field: `yymmddhhmm`, followed by *EST*.<br><br>See also the **Important Note** below this table, concerning values that have `00:00` as `hh:mm` group. |
| specified | `NIL` | `NIL` | `...TimeSlice.validTime.TimePeriod.endPosition` value formatted according to the NOTAM syntax for this field: `yymmddhhmm`.<br><br>See also the **Important Note** below this table, concerning values that have `00:00` as `hh:mm` group. |

***Important Note:*** According to the AIXM Temporality Concept version 1.1, item 4.4.1, the end of day is coded as `"00:00"` of the next day. The current NOTAM practice is that `"2359"` is used as `hhmm` group for the `"end of the day"`. Therefore, if the value that is used for extracting the date/time group for insertion in item C has `00:00` as `hh:mm` group (or `00:00:00` as `hh:mm:ss` group, when the seconds value is also included) shall be converted into **2359 of the previous calendar date!**

