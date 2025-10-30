import React, { FC, useCallback, useEffect, useMemo, useRef, useState } from "react";
import {
  ActivityIndicator,
  Dimensions,
  FlatList,
  Image,
  Modal,
  Pressable,
  StyleSheet,
  Text,
  TouchableOpacity,
  View
} from "react-native";
import { Ionicons } from "@expo/vector-icons";

import { OfficialDetail, OfficialSummary, listOfficials } from "../api/officialsApi";
import RepresentativeCard, { RepresentativeMetrics } from "../components/RepresentativeCard";
import { useAuth } from "../providers/AuthProvider";
import { COLORS } from "../theme/colors";

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

const PAGE_SIZE = 10;

const HomeScreen: FC = () => {
  const { state } = useAuth();
  const token = state.token ?? undefined;

  const [officials, setOfficials] = useState<OfficialSummary[]>([]);
  const [page, setPage] = useState<number>(0);
  const [loadingInitial, setLoadingInitial] = useState<boolean>(false);
  const [refreshing, setRefreshing] = useState<boolean>(false);
  const [loadingMore, setLoadingMore] = useState<boolean>(false);
  const [hasMore, setHasMore] = useState<boolean>(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [photoModal, setPhotoModal] = useState<{ url: string; name: string } | null>(null);

  const officialsCountRef = useRef<number>(0);

  type FetchMode = "initial" | "refresh" | "append";

  const fetchPage = useCallback(
    async (targetPage: number, mode: FetchMode) => {
      if (mode === "initial") {
        setLoadingInitial(true);
      } else if (mode === "refresh") {
        setRefreshing(true);
      } else {
        setLoadingMore(true);
      }
      if (mode !== "append") {
        setErrorMessage(null);
      }

      try {
        const pageResults = await listOfficials(targetPage, PAGE_SIZE, token);
        const safeResults = Array.isArray(pageResults) ? pageResults : [];
        setHasMore(safeResults.length === PAGE_SIZE);
        setPage(targetPage);
        setOfficials((previous) => {
          let nextList: OfficialSummary[];
          if (mode === "append") {
            const merged = new Map(previous.map((entry) => [entry.sourceId, entry]));
            safeResults.forEach((entry) => {
              merged.set(entry.sourceId, entry);
            });
            nextList = Array.from(merged.values());
          } else {
            nextList = safeResults;
          }
          officialsCountRef.current = nextList.length;
          return nextList;
        });
        if (safeResults.length === 0 && targetPage === 0) {
          setErrorMessage("No officials are available right now. Please try again shortly.");
        }
      } catch (error) {
        console.error("[home] failed to load officials page", error);
        if (mode !== "append" || officialsCountRef.current === 0) {
          setErrorMessage("We couldn't refresh the roster. Pull to try again shortly.");
        }
      } finally {
        setLoadingInitial(false);
        setRefreshing(false);
        setLoadingMore(false);
      }
    },
    [token]
  );

  useEffect(() => {
    void fetchPage(0, "initial");
  }, [fetchPage]);

  const handleRefresh = useCallback(() => {
    if (loadingInitial || refreshing) {
      return;
    }
    setHasMore(true);
    void fetchPage(0, "refresh");
  }, [fetchPage, loadingInitial, refreshing]);

  const handleLoadMore = useCallback(() => {
    if (loadingInitial || loadingMore || refreshing || !hasMore) {
      return;
    }
    const nextPage = page + 1;
    void fetchPage(nextPage, "append");
  }, [fetchPage, hasMore, loadingInitial, loadingMore, page, refreshing]);

  const handlePhotoPress = useCallback((official: OfficialSummary | OfficialDetail) => {
    if (!official.photoUrl) {
      return;
    }
    const displayName = official.fullName ?? "Official portrait";
    setPhotoModal({ url: official.photoUrl, name: displayName });
  }, []);

  const closePhotoModal = useCallback(() => {
    setPhotoModal(null);
  }, []);

  const listHeader = useMemo(
    () => (
      <View style={styles.header}>
        <Text style={styles.tagline}>Your civic signal dashboard</Text>
        {errorMessage ? <Text style={styles.error}>{errorMessage}</Text> : null}
      </View>
    ),
    [errorMessage]
  );

  const listEmptyComponent = useMemo(
    () => (
      <View style={styles.emptyState}>
        {loadingInitial ? (
          <ActivityIndicator size="large" color={COLORS.accent} />
        ) : (
          <Text style={styles.emptyText}>
            {errorMessage ?? "No officials are available right now. Pull to refresh in a moment."}
          </Text>
        )}
      </View>
    ),
    [errorMessage, loadingInitial]
  );

  const listFooterComponent = useMemo(
    () =>
      loadingMore ? (
        <View style={styles.footerLoader}>
          <ActivityIndicator size="small" color={COLORS.accent} />
        </View>
      ) : null,
    [loadingMore]
  );

  const renderItem = useCallback(
    ({ item }: { item: OfficialSummary }) => (
      <RepresentativeCard
        official={item}
        metrics={buildMetrics(item)}
        roleSubtitle={item.roleTitle}
        onPressPhoto={handlePhotoPress}
      />
    ),
    [handlePhotoPress]
  );

  const keyExtractor = useCallback((item: OfficialSummary) => item.sourceId ?? item.uuid, []);

  const modalImageDimensions = useMemo(() => {
    const window = Dimensions.get("window");
    return {
      width: window.width * 0.9,
      height: window.height * 0.7
    };
  }, []);

  return (
    <View style={styles.container}>
      <FlatList
        data={officials}
        keyExtractor={keyExtractor}
        renderItem={renderItem}
        contentContainerStyle={styles.listContent}
        ListHeaderComponent={listHeader}
        ListEmptyComponent={listEmptyComponent}
        ListFooterComponent={listFooterComponent}
        refreshing={refreshing}
        onRefresh={handleRefresh}
        onEndReachedThreshold={0.4}
        onEndReached={handleLoadMore}
      />

      <Modal
        visible={photoModal !== null}
        transparent
        animationType="fade"
        onRequestClose={closePhotoModal}
      >
        <View style={styles.modalBackdrop}>
          <Pressable style={StyleSheet.absoluteFill} onPress={closePhotoModal} />
          <View style={styles.modalContent}>
            <TouchableOpacity
              style={styles.modalCloseButton}
              onPress={closePhotoModal}
              accessibilityRole="button"
              accessibilityLabel="Close portrait preview"
            >
              <Ionicons name="close" size={26} color={COLORS.textPrimary} />
            </TouchableOpacity>
            {photoModal ? (
              <>
                <View style={[styles.modalImageFrame, modalImageDimensions]}>
                  <Image
                    source={{ uri: photoModal.url }}
                    style={[styles.modalImage, modalImageDimensions]}
                    resizeMode="contain"
                    accessibilityLabel={`${photoModal.name} portrait`}
                  />
                </View>
                <Text style={styles.modalCaption}>{photoModal.name}</Text>
              </>
            ) : null}
          </View>
        </View>
      </Modal>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: COLORS.background
  },
  listContent: {
    padding: 24,
    gap: 24,
    paddingBottom: 48
  },
  header: {
    gap: 8
  },
  tagline: {
    textTransform: "uppercase",
    letterSpacing: 1.5,
    fontSize: 12,
    fontWeight: "600",
    color: COLORS.textSecondary
  },
  error: {
    color: COLORS.danger,
    fontSize: 14
  },
  emptyState: {
    paddingVertical: 80,
    alignItems: "center",
    justifyContent: "center",
    gap: 12
  },
  emptyText: {
    color: COLORS.textSecondary,
    fontSize: 14,
    textAlign: "center",
    paddingHorizontal: 16
  },
  footerLoader: {
    paddingVertical: 24
  },
  modalBackdrop: {
    flex: 1,
    backgroundColor: "rgba(0, 0, 0, 0.8)",
    justifyContent: "center",
    alignItems: "center"
  },
  modalContent: {
    width: "90%",
    alignItems: "center",
    gap: 16
  },
  modalCloseButton: {
    alignSelf: "flex-end",
    padding: 8
  },
  modalImageFrame: {
    borderRadius: 24,
    overflow: "hidden",
    backgroundColor: COLORS.surface,
    justifyContent: "center",
    alignItems: "center"
  },
  modalImage: {
    borderRadius: 24
  },
  modalCaption: {
    color: COLORS.textPrimary,
    fontSize: 16,
    fontWeight: "600",
    textAlign: "center"
  }
});

export default HomeScreen;
