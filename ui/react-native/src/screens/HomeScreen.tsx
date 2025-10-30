import React, { FC, useCallback, useEffect, useState } from "react";
import { RefreshControl, ScrollView, StyleSheet, Text, View } from "react-native";

import {
  OfficialDetail,
  OfficialSummary,
  getOfficialBySourceId,
  listOfficials
} from "../api/officialsApi";
import RepresentativeCard, { RepresentativeMetrics } from "../components/RepresentativeCard";
import { useAuth } from "../providers/AuthProvider";
import { COLORS } from "../theme/colors";
import { CONFIG } from "../utils/config";

const clampScore = (value: number): number => Math.min(Math.max(value, 0), 100);

const buildMetrics = (official: OfficialSummary | OfficialDetail): RepresentativeMetrics => {
  const attendance = "attendanceSummary" in official ? official.attendanceSummary : null;
  const presence = attendance?.presenceScore ?? official.presenceScore ?? 0;
  const activity = attendance?.participationScore ?? official.activityScore ?? 0;
  const bigScoreSource = attendance?.overallScore ?? official.overallScore ?? Math.round((presence + activity) / 2);

  const bigScore = clampScore(bigScoreSource);
  const presenceScore = clampScore(presence);
  const activityScore = clampScore(activity);

  const lastUpdated = official.lastRefreshedAt
    ? new Date(official.lastRefreshedAt).toLocaleTimeString(undefined, {
        hour: "numeric",
        minute: "2-digit"
      })
    : "Recently updated";

  return {
    bigScore,
    presenceScore,
    activityScore,
    lastUpdatedLabel: lastUpdated,
    sourcesVerified: true
  };
};

const HomeScreen: FC = () => {
  const { state } = useAuth();
  const [official, setOfficial] = useState<OfficialDetail | OfficialSummary | null>(null);
  const [metrics, setMetrics] = useState<RepresentativeMetrics | null>(null);
  const [loading, setLoading] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const fetchFeaturedOfficial = useCallback(async () => {
    // Load a highlighted official or fallback to a random roster entry.
    setLoading(true);
    setErrorMessage(null);
    try {
      let selected: OfficialDetail | OfficialSummary | null = null;
      const token = state.token ?? undefined;

      if (CONFIG.featuredOfficialSourceId) {
        try {
          selected = await getOfficialBySourceId(CONFIG.featuredOfficialSourceId, token);
        } catch (error) {
          console.error("[home] unable to load featured official, falling back to random", error);
        }
      }

      if (!selected) {
        const officials = await listOfficials(50, token);
        if (!officials || officials.length === 0) {
          setErrorMessage("No officials are available right now. Please try again shortly.");
          return;
        }
        const withPortrait = officials.filter((item) => item.photoUrl);
        const pool = withPortrait.length > 0 ? withPortrait : officials;
        const chosen = pool[Math.floor(Math.random() * pool.length)];

        try {
          selected = await getOfficialBySourceId(chosen.sourceId, token);
        } catch (detailError) {
          console.warn("[home] official detail failed; using summary instead", detailError);
          selected = chosen;
        }
      }

      if (!selected) {
        setErrorMessage("Failed to load an official profile.");
        return;
      }

      setOfficial(selected);
      setMetrics(buildMetrics(selected));
    } catch (error) {
      console.error("[home] unexpected error while loading official", error);
      setErrorMessage("We hit a snag loading profiles. Pull to refresh in a moment.");
    } finally {
      setLoading(false);
    }
  }, [state.token]);

  useEffect(() => {
    void fetchFeaturedOfficial();
  }, [fetchFeaturedOfficial]);

  return (
    <ScrollView
      style={styles.container}
      contentContainerStyle={styles.contentContainer}
      refreshControl={<RefreshControl refreshing={loading} onRefresh={fetchFeaturedOfficial} />}
    >
      <View style={styles.header}>
        <Text style={styles.tagline}>Your civic signal dashboard</Text>
        <Text style={styles.title}>Home</Text>
      </View>

      {errorMessage ? <Text style={styles.error}>{errorMessage}</Text> : null}

      {official && metrics ? (
        <RepresentativeCard official={official} metrics={metrics} roleSubtitle={official.roleTitle} />
      ) : null}
    </ScrollView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: COLORS.background
  },
  contentContainer: {
    padding: 24,
    gap: 24
  },
  header: {
    gap: 6
  },
  tagline: {
    textTransform: "uppercase",
    letterSpacing: 1.5,
    fontSize: 12,
    fontWeight: "600",
    color: COLORS.textSecondary
  },
  title: {
    fontSize: 28,
    fontWeight: "800",
    color: COLORS.textPrimary
  },
  error: {
    color: COLORS.danger,
    fontSize: 14
  }
});

export default HomeScreen;
