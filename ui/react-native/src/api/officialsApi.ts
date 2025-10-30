import { apiRequest } from "./http";

export interface OfficialSummary {
  uuid: string;
  sourceId: string;
  fullName: string;
  partyAffiliation: string | null;
  roleTitle: string | null;
  photoUrl: string | null;
  lastRefreshedAt: string | null;
}

export interface OfficialDetail extends OfficialSummary {
  legislativeBodyUuid: string;
  jurisdictionRegionCode: string | null;
  districtIdentifier: string | null;
  termStartDate: string | null;
  termEndDate: string | null;
  officeStatus: string | null;
  biographyUrl: string | null;
  versionHash: string | null;
}

export const listOfficials = (limit = 25, token?: string): Promise<OfficialSummary[]> =>
  apiRequest<OfficialSummary[]>(`/api/officials?limit=${limit}`, {
    method: "GET",
    token
  });

export const getOfficialBySourceId = (sourceId: string, token?: string): Promise<OfficialDetail> =>
  apiRequest<OfficialDetail>(`/api/officials/${encodeURIComponent(sourceId)}`, {
    method: "GET",
    token
  });
