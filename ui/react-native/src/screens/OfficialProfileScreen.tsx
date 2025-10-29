import React, { FC, useCallback, useEffect, useMemo, useState } from "react";
import {
  ActivityIndicator,
  Image,
  Pressable,
  RefreshControl,
  ScrollView,
  StyleSheet,
  Text,
  View
} from "react-native";
import { LinearGradient } from "expo-linear-gradient";
import { Ionicons } from "@expo/vector-icons";

import { useAuth } from "../providers/AuthProvider";
import { getOfficialBySourceId, listOfficials, type OfficialDetail, type OfficialSummary } from "../api/officialsApi";
import { FEATURED_OFFICIAL_SOURCE_ID } from "../utils/config";
import { ApiError, UnauthorizedError } from "../api/http";

const FALLBACK_MESSAGE = "Unable to load official data.";

type PlaceholderScore = Readonly<{
  label: string;
  value: string;
}>;

const OfficialProfileScreen: FC = () => {
  const { state, logout } = useAuth();
  const [official, setOfficial] = useState<OfficialDetail | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [refreshing, setRefreshing] = useState<boolean>(false);
  const [error, setError] = useState<string>("");

  const placeholderScores = useMemo<PlaceholderScore[]>(
    () => [
      { label: "Civic Impact", value: "82" },
      { label: "Constituent Alignment", value: "75" },
      { label: "Transparency", value: "88" }
    ],
    []
  );

  /**
   * Fetch a featured official (or the first entry in the collection) and cache it in local state.
   * When a session is unauthorized, force a logout so the user can re-authenticate.
   */
  const loadOfficial = useCallback(async () => {
    if (!state.token) {
      return;
    }
    try {
      setError("");
      let detail: OfficialDetail | null = null;
      if (FEATURED_OFFICIAL_SOURCE_ID) {
        detail = await getOfficialBySourceId(FEATURED_OFFICIAL_SOURCE_ID, state.token);
      } else {
        const officials: OfficialSummary[] = await listOfficials(1, state.token);
        if (!officials || officials.length === 0) {
          setError("No officials available yet.");
          setOfficial(null);
          return;
        }
        detail = await getOfficialBySourceId(officials[0].sourceId, state.token);
      }
      setOfficial(detail);
    } catch (err: unknown) {
      console.error("Failed to load official", err);
      if (err instanceof UnauthorizedError) {
        setError("Session expired. Please sign in again.");
        await logout();
        return;
      }
      if (err instanceof ApiError && err.status === 401) {
        setError("Session expired. Please sign in again.");
        await logout();
        return;
      }
      const message = err instanceof Error && err.message ? err.message : FALLBACK_MESSAGE;
      setError(message);
    } finally {
      setLoading(false);
    }
  }, [logout, state.token]);

  useEffect(() => {
    if (state.token) {
      loadOfficial();
    }
  }, [state.token, loadOfficial]);

  const onRefresh = useCallback(async () => {
    setRefreshing(true);
    await loadOfficial();
    setRefreshing(false);
  }, [loadOfficial]);

  const initials = official?.fullName
    ?.split(" ")
    .map((segment) => (segment ? segment[0] : ""))
    .join("")
    .toUpperCase();

  return (
    <LinearGradient colors={["#020617", "#111827"]} style={styles.container}>
      <ScrollView
        contentContainerStyle={styles.content}
        refreshControl={<RefreshControl refreshing={refreshing} onRefresh={onRefresh} tintColor="#38bdf8" />}>
        <View style={styles.headerRow}>
          <Text style={styles.screenTitle}>Your Representative</Text>
          <Pressable onPress={logout} style={styles.logoutButton}>
            <Ionicons name="log-out-outline" size={20} color="#f8fafc" />
            <Text style={styles.logoutLabel}>Sign out</Text>
          </Pressable>
        </View>

        {loading ? (
          <ActivityIndicator size="large" color="#38bdf8" style={styles.loader} />
        ) : error ? (
          <Text style={styles.error}>{error}</Text>
        ) : official ? (
          <View style={styles.card}>
            <View style={styles.profileRow}>
              {official.photoUrl ? (
                <Image source={{ uri: official.photoUrl }} style={styles.avatar} />
              ) : (
                <View style={styles.avatarPlaceholder}>
                  <Text style={styles.avatarInitials}>{initials}</Text>
                </View>
              )}
              <View style={styles.profileInfo}>
                <Text style={styles.name}>{official.fullName}</Text>
                <Text style={styles.role}>{official.roleTitle}</Text>
                <Text style={styles.meta}>
                  {official.partyAffiliation || "Independent"} • {official.jurisdictionRegionCode || "National"}
                </Text>
              </View>
            </View>

            <View style={styles.section}>
              <Text style={styles.sectionTitle}>Term</Text>
              <Text style={styles.sectionText}>
                {formatDateRange(official.termStartDate, official.termEndDate)} ({official.officeStatus})
              </Text>
            </View>

            {official.biographyUrl ? (
              <View style={styles.section}>
                <Text style={styles.sectionTitle}>Biography</Text>
                <Text style={styles.sectionText}>{official.biographyUrl}</Text>
              </View>
            ) : null}

            <View style={styles.scoresGrid}>
              {placeholderScores.map((score) => (
                <View key={score.label} style={styles.scoreCard}>
                  <Text style={styles.scoreValue}>{score.value}</Text>
                  <Text style={styles.scoreLabel}>{score.label}</Text>
                </View>
              ))}
            </View>
          </View>
        ) : (
          <Text style={styles.emptyMessage}>No official data available.</Text>
        )}
      </ScrollView>
    </LinearGradient>
  );
};

const formatDateRange = (start: string | null | undefined, end: string | null | undefined): string => {
  const toDisplay = (value: string | null | undefined): string => {
    if (!value) {
      return "TBD";
    }
    const date = new Date(value);
    return date.toLocaleDateString();
  };
  return `${toDisplay(start)} - ${toDisplay(end)}`;
};

const styles = StyleSheet.create({
  container: {
    flex: 1
  },
  content: {
    paddingHorizontal: 20,
    paddingVertical: 32
  },
  headerRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    marginBottom: 20
  },
  screenTitle: {
    fontSize: 24,
    fontWeight: "700",
    color: "#f8fafc"
  },
  logoutButton: {
    flexDirection: "row",
    alignItems: "center",
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 9999,
    borderWidth: 1,
    borderColor: "rgba(148, 163, 184, 0.4)"
  },
  logoutLabel: {
    color: "#f8fafc",
    marginLeft: 8,
    fontSize: 13
  },
  loader: {
    marginTop: 80
  },
  error: {
    marginTop: 48,
    color: "#fca5a5",
    fontSize: 16,
    textAlign: "center"
  },
  emptyMessage: {
    marginTop: 48,
    color: "#cbd5f5",
    fontSize: 16,
    textAlign: "center"
  },
  card: {
    backgroundColor: "rgba(15, 23, 42, 0.85)",
    borderRadius: 20,
    padding: 24,
    shadowColor: "#000",
    shadowOpacity: 0.3,
    shadowRadius: 20,
    shadowOffset: { width: 0, height: 18 }
  },
  profileRow: {
    flexDirection: "row",
    alignItems: "center",
    marginBottom: 24
  },
  avatar: {
    width: 96,
    height: 96,
    borderRadius: 48,
    borderWidth: 2,
    borderColor: "#38bdf8"
  },
  avatarPlaceholder: {
    width: 96,
    height: 96,
    borderRadius: 48,
    alignItems: "center",
    justifyContent: "center",
    backgroundColor: "rgba(59, 130, 246, 0.2)",
    borderWidth: 1,
    borderColor: "rgba(148, 163, 184, 0.6)"
  },
  avatarInitials: {
    color: "#e0f2fe",
    fontSize: 28,
    fontWeight: "700"
  },
  profileInfo: {
    marginLeft: 20,
    flex: 1
  },
  name: {
    fontSize: 24,
    fontWeight: "700",
    color: "#f8fafc"
  },
  role: {
    marginTop: 4,
    color: "#bae6fd",
    fontSize: 15
  },
  meta: {
    marginTop: 6,
    color: "#94a3b8",
    fontSize: 13
  },
  section: {
    marginTop: 16
  },
  sectionTitle: {
    color: "#e2e8f0",
    fontSize: 14,
    textTransform: "uppercase",
    letterSpacing: 1.2,
    marginBottom: 6
  },
  sectionText: {
    color: "#cbd5f5",
    fontSize: 15,
    lineHeight: 22
  },
  scoresGrid: {
    flexDirection: "row",
    justifyContent: "space-between",
    marginTop: 24
  },
  scoreCard: {
    flex: 1,
    marginHorizontal: 4,
    backgroundColor: "rgba(59, 130, 246, 0.12)",
    borderRadius: 16,
    paddingVertical: 18,
    alignItems: "center"
  },
  scoreValue: {
    fontSize: 24,
    fontWeight: "700",
    color: "#38bdf8"
  },
  scoreLabel: {
    marginTop: 6,
    fontSize: 13,
    color: "#cbd5f5",
    textAlign: "center"
  }
});

export default OfficialProfileScreen;
