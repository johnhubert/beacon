import React, { FC, useState } from "react";
import { Image, StyleSheet, Text, TouchableOpacity, View } from "react-native";
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

const formatScore = (value?: number): string => {
  if (value === undefined || Number.isNaN(value) || !Number.isFinite(value)) {
    return "0";
  }
  return value.toFixed(0);
};

export interface RepresentativeMetrics {
  bigScore: number;
  presenceScore: number;
  activityScore: number;
  lastUpdatedLabel: string;
  sourcesVerified: boolean;
}

interface RepresentativeCardProps {
  official: OfficialDetail | OfficialSummary;
  roleSubtitle?: string | null;
  metrics: RepresentativeMetrics;
  onPressPhoto?: (official: OfficialDetail | OfficialSummary) => void;
}

const RepresentativeCard: FC<RepresentativeCardProps> = ({ official, roleSubtitle, metrics, onPressPhoto }) => {
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
            <TouchableOpacity
              style={styles.avatarTouchable}
              activeOpacity={0.8}
              onPress={() => {
                if (onPressPhoto) {
                  onPressPhoto(official);
                }
              }}
              accessibilityRole="button"
              accessibilityLabel={`Expand portrait of ${displayName}`}
            >
              <Image
                source={{ uri: official.photoUrl }}
                style={styles.avatar}
                onError={() => setImageFailed(true)}
                accessibilityLabel={`${displayName} portrait`}
              />
            </TouchableOpacity>
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
          <Text style={styles.scoreValue}>{formatScore(metrics.bigScore)}/100</Text>
        </View>

        <View style={styles.metricSplitRow}>
          <View style={styles.splitMetric}>
            <Text style={styles.splitLabel}>Presence</Text>
            <View style={styles.splitBarBackground}>
              <View
                style={[styles.splitBarFill, { width: `${clampPercent(metrics.presenceScore)}%` }]}
              />
            </View>
            <Text style={styles.splitValue}>{formatScore(metrics.presenceScore)}%</Text>
          </View>
          <View style={styles.splitMetric}>
            <Text style={styles.splitLabel}>Participation</Text>
            <View style={styles.splitBarBackground}>
              <View
                style={[styles.splitBarFill, { width: `${clampPercent(metrics.activityScore)}%` }]}
              />
            </View>
            <Text style={styles.splitValue}>{formatScore(metrics.activityScore)}%</Text>
          </View>
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
  avatarTouchable: {
    flex: 1
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
  metricSplitRow: {
    flexDirection: "row",
    gap: 16
  },
  splitMetric: {
    flex: 1,
    gap: 6
  },
  splitLabel: {
    fontSize: 13,
    fontWeight: "600",
    color: COLORS.textSecondary
  },
  splitBarBackground: {
    height: 8,
    borderRadius: 8,
    backgroundColor: "rgba(255, 255, 255, 0.14)",
    overflow: "hidden"
  },
  splitBarFill: {
    height: "100%",
    borderRadius: 8,
    backgroundColor: COLORS.accent
  },
  splitValue: {
    fontSize: 14,
    fontWeight: "600",
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
