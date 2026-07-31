import { requestJson } from "./api.js";

export const getAirports = () => requestJson("/api/baseline/airports");
export const getAirspaces = () => requestJson("/api/baseline/airspaces");
export const getNavaids = () => requestJson("/api/baseline/navaids");
export const getRunways = (airport) =>
  requestJson(`/api/baseline/runways?airport=${encodeURIComponent(airport)}`);
