import React, { FC, useState } from "react";
import { Image, StyleSheet, Text, View } from "react-native";
import { LinearGradient } from "expo-linear-gradient";
import { Ionicons } from "@expo/vector-icons";

import { OfficialDetail, OfficialSummary } from "../api/officialsApi";
import { COLORS } from "../theme/colors";

const clampPercent = (value: number): number => {
  if (Number.isNaN(value)) {
    return 0;
  }
  if (!Number.isFinite(value)) {
    return 100;
  }
  return Math.min(Math.max(value, 0), 100);
};

export interface RepresentativeMetrics {
  bigScore: number;
  weeklyDelta: number;
  votes: number;
  statements: number;
  funding: number;
  lastUpdatedLabel: string;
  sourcesVerified: boolean;
}

interface RepresentativeCardProps {
  official: OfficialDetail | OfficialSummary;
  roleSubtitle?: string | null;
  metrics: RepresentativeMetrics;
}

const RepresentativeCard: FC<RepresentativeCardProps> = ({ official, roleSubtitle, metrics }) => {
  // Render a stylized representative summary using verified theme colors.
  const [imageFailed, setImageFailed] = useState<boolean>(false);

  const displayName = official.fullName ?? "Unknown Representative";
  const roleText = official.roleTitle ?? roleSubtitle ?? "Representative";
  const badgeLabel = metrics.sourcesVerified ? "Verified" : "Unverified";

  return (
    <LinearGradient colors={[COLORS.surface, COLORS.surfaceSecondary]} style={styles.card}>
      <View style={styles.headerRow}>
        <View style={styles.avatarWrapper}>
          {official.photoUrl && !imageFailed ? (
            <Image
              source={{ uri: official.photoUrl }}
              style={styles.avatar}
              onError={() => setImageFailed(true)}
              accessibilityLabel={`${displayName} portrait`}
            />
          ) : (
            <View style={styles.avatarFallback}>
              <Ionicons name="person" size={42} color={COLORS.textSecondary} />
            </View>
          )}
        </View>
        <View style={styles.titleBlock}>
          <Text style={styles.nameText}>{displayName}</Text>
          {roleText ? <Text style={styles.roleText}>{roleText}</Text> : null}
          <View style={styles.badgeRow}>
            <Ionicons name="checkmark-circle" size={16} color={COLORS.success} />
            <Text style={styles.badgeText}>{badgeLabel}</Text>
          </View>
        </View>
      </View>

      <View style={styles.section}>
        <Text style={styles.metricLabel}>BIG Score</Text>
        <View style={styles.scoreRow}>
          <View style={styles.scoreBarBackground}>
            <View
              style={[
                styles.scoreBarFill,
                { width: `${clampPercent(metrics.bigScore)}%` }
              ]}
            />
          </View>
          <Text style={styles.scoreValue}>{metrics.bigScore.toFixed(0)}/100</Text>
        </View>
        <Text style={styles.deltaText}>This Week {metrics.weeklyDelta >= 0 ? "+" : ""}{metrics.weeklyDelta}</Text>
      </View>

      <View style={styles.section}>
        <View style={styles.metricRow}>
          <Text style={styles.metricTitle}>Votes</Text>
          <Text style={styles.metricValue}>{metrics.votes}</Text>
        </View>
        <View style={styles.metricRow}>
          <Text style={styles.metricTitle}>Statements</Text>
          <Text style={styles.metricValue}>{metrics.statements}</Text>
        </View>
        <View style={styles.metricRow}>
          <Text style={styles.metricTitle}>Funding</Text>
          <Text style={styles.metricValue}>{metrics.funding}</Text>
        </View>
      </View>

      <View style={styles.footerRow}>
        <Text style={styles.footerText}>
          Sources {metrics.sourcesVerified ? "Verified" : "Pending"} • {metrics.lastUpdatedLabel}
        </Text>
        <View style={styles.primaryButton}>
          <Text style={styles.primaryButtonText}>Tap Issues →</Text>
        </View>
      </View>
    </LinearGradient>
  );
};

const styles = StyleSheet.create({
  card: {
    width: "100%",
    borderRadius: 28,
    padding: 24,
    overflow: "hidden",
    borderWidth: 1,
    borderColor: COLORS.border,
    gap: 20
  },
  headerRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 16
  },
  avatarWrapper: {
    width: 82,
    height: 82,
    borderRadius: 20,
    overflow: "hidden",
    backgroundColor: COLORS.surfaceOverlay,
    borderWidth: 1,
    borderColor: COLORS.border
  },
  avatar: {
    width: "100%",
    height: "100%",
    resizeMode: "cover"
  },
  avatarFallback: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center"
  },
  titleBlock: {
    flex: 1
  },
  nameText: {
    fontSize: 22,
    fontWeight: "700",
    color: COLORS.textPrimary
  },
  roleText: {
    marginTop: 4,
    fontSize: 14,
    fontWeight: "500",
    color: COLORS.textSecondary
  },
  badgeRow: {
    marginTop: 6,
    flexDirection: "row",
    alignItems: "center",
    gap: 6
  },
  badgeText: {
    fontSize: 13,
    color: COLORS.success,
    fontWeight: "600"
  },
  section: {
    gap: 12
  },
  metricLabel: {
    fontSize: 14,
    fontWeight: "600",
    color: COLORS.textSecondary,
    textTransform: "uppercase",
    letterSpacing: 1.1
  },
  scoreRow: {
    flexDirection: "row",
    alignItems: "center",
    gap: 12
  },
  scoreBarBackground: {
    flex: 1,
    height: 10,
    borderRadius: 10,
    backgroundColor: "rgba(255, 255, 255, 0.14)",
    overflow: "hidden"
  },
  scoreBarFill: {
    height: "100%",
    borderRadius: 10,
    backgroundColor: COLORS.accent
  },
  scoreValue: {
    fontSize: 20,
    fontWeight: "700",
    color: COLORS.textPrimary
  },
  deltaText: {
    fontSize: 13,
    color: COLORS.success,
    fontWeight: "500"
  },
  metricRow: {
    flexDirection: "row",
    justifyContent: "space-between",
    alignItems: "center",
    paddingVertical: 6
  },
  metricTitle: {
    fontSize: 14,
    fontWeight: "600",
    color: COLORS.textSecondary
  },
  metricValue: {
    fontSize: 16,
    fontWeight: "700",
    color: COLORS.textPrimary
  },
  footerRow: {
    gap: 12
  },
  footerText: {
    fontSize: 12,
    color: COLORS.textSecondary
  },
  primaryButton: {
    backgroundColor: COLORS.accent,
    borderRadius: 16,
    paddingVertical: 12,
    alignItems: "center"
  },
  primaryButtonText: {
    fontSize: 16,
    fontWeight: "700",
    color: COLORS.textPrimary
  }
});

export default RepresentativeCard;
