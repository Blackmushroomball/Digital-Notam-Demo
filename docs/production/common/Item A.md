# Item A

The item A shall be generated according to the Event properties `concernedAirspace` or `concernedAirportHeliport`, which need to be coded for all scenarios. Further details are provided with each scenario. The following common formatting rules apply:

- If `concernedAirspace` is used, then the `Airspace.designator` of the associated `Airspace` shall be used
- If `concernedAirportHeliport` is used, then:
  - if the associated `AirportHeliport` has an assigned value for its `locationIndicatorICAO`, this one is used in item A
  - otherwise, if `AirportHeliport.locationIndicatorICAO` is empty, then `CXXX` or `CCXX` shall be used, where `C`/`CC` are the country code. In this case, the `AirportHeliport.name` shall be added at the beginning of item E (see OPADD 2.3.14). Note that this element does not appear in the item E generation pattern diagrams of the various scenario.

