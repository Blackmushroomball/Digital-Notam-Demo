import { sendJson } from "./api.js";

export const analyseAtsaNewAssociations = (payload) =>
  sendJson("/api/scenarios/atsa-new/associations", "POST", payload);
export const getActivationPreview = (payload) =>
  sendJson("/api/scenarios/atsa-act/activation-preview", "POST", payload);
export const getNavPreview = (payload) =>
  sendJson("/api/scenarios/nav-uns/preview", "POST", payload);
