import { requestJson, requestText, sendJson } from "./api.js";

export const getNotams = () => requestJson("/api/notams");
export const saveNotam = (payload, id) =>
  sendJson(
    id ? `/api/notams/${id}` : "/api/notams",
    id ? "PUT" : "POST",
    payload,
  );
export const publishNotam = (id) =>
  requestJson(`/api/notams/${id}/publish`, { method: "POST" });
export const deleteNotam = (id) =>
  requestJson(`/api/notams/${id}`, { method: "DELETE" });
export const getAixm = (id) => requestText(`/api/notams/${id}/aixm`);
export const getCnotam = (id) => requestText(`/api/notams/${id}/cnotam`);
export const importNotamXml = (xml) =>
  requestJson("/api/import", {
    method: "POST",
    headers: { "Content-Type": "application/xml" },
    body: xml,
  });
