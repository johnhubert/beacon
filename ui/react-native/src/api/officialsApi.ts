import { apiRequest } from "./http";

export interface OfficialSummary {
  uuid: string;
  sourceId: string;
  fullName: string;
  partyAffiliation: string | null;
  roleTitle: string | null;
  photoUrl: string | null;
  presenceScore: number | null;
  activityScore: number | null;
  overallScore: number | null;
  lastRefreshedAt: string | null;
}

export interface AttendanceSummary {
  sessionsAttended: number;
  sessionsTotal: number;
  votesParticipated: number;
  votesTotal: number;
  presenceScore: number;
  participationScore: number;
  overallScore: number;
}

export interface AttendanceSnapshot {
  periodLabel: string;
  periodStart: string | null;
  periodEnd: string | null;
  sessionsAttended: number;
  sessionsTotal: number;
  votesParticipated: number;
  votesTotal: number;
  presenceScore: number;
  participationScore: number;
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
  attendanceSummary: AttendanceSummary | null;
}

export const listOfficials = (page = 0, pageSize = 25, token?: string): Promise<OfficialSummary[]> => {
  const params = new URLSearchParams({
    page: String(page),
    pageSize: String(pageSize)
  });
  return apiRequest<OfficialSummary[]>(`/api/officials?${params.toString()}`, {
    method: "GET",
    token
  });
};

export const getOfficialBySourceId = (sourceId: string, token?: string): Promise<OfficialDetail> =>
  apiRequest<OfficialDetail>(`/api/officials/${encodeURIComponent(sourceId)}`, {
    method: "GET",
    token
  });

export const getAttendanceHistory = (sourceId: string, token?: string): Promise<AttendanceSnapshot[]> =>
  apiRequest<AttendanceSnapshot[]>(`/api/officials/${encodeURIComponent(sourceId)}/attendance-history`, {
    method: "GET",
    token
  });
