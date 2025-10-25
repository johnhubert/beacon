import React from "react";
import { StatusBar } from "expo-status-bar";
import { useEffect, useState } from "react";
import { Platform, ScrollView, StyleSheet, Text, View } from "react-native";

const API_BASE_URL =
  (process.env.EXPO_PUBLIC_API_BASE_URL || "").replace(/\/$/, "");

export default function App() {
  const [latestEvent, setLatestEvent] = useState("Waiting for live updates...");
  const [tickCount, setTickCount] = useState(0);
  const [connectionStatus, setConnectionStatus] = useState("Connecting...");

  useEffect(() => {
    if (typeof EventSource === "undefined") {
      setConnectionStatus("SSE unavailable on this platform");
      return undefined;
    }

    const eventsUrl = API_BASE_URL ? `${API_BASE_URL}/api/events` : "/api/events";
    const source = new EventSource(eventsUrl);
    source.onopen = () => setConnectionStatus("Connected to live event stream");
    source.onerror = () => setConnectionStatus("Reconnecting...");
    source.onmessage = (event) => {
      setTickCount((prev) => prev + 1);
      if (event?.data) {
        try {
          const payload = JSON.parse(event.data);
          setLatestEvent(`${payload.message} @ ${payload.publishedAt}`);
        } catch {
          setLatestEvent(event.data);
        }
      }
    };

    return () => {
      source.close();
    };
  }, []);

  return (
    <View style={styles.container}>
      <StatusBar style="light" />
      <View style={styles.hero}>
        <Text style={styles.title}>Beacon React Native Scaffolding</Text>
        <Text style={styles.subtitle}>
          Build once with Expo + React Native, stream data via Spring Boot SSE.
        </Text>
      </View>
      <ScrollView contentContainerStyle={styles.card}>
        <View style={styles.cardSection}>
          <Text style={styles.label}>SSE Connection</Text>
          <Text style={styles.value}>{connectionStatus}</Text>
        </View>
        <View style={styles.cardSection}>
          <Text style={styles.label}>Latest Event</Text>
          <Text style={styles.value}>{latestEvent}</Text>
        </View>
        <View style={styles.cardSection}>
          <Text style={styles.label}>Events Received</Text>
          <Text style={styles.value}>{tickCount}</Text>
        </View>
      </ScrollView>
      <View style={styles.hint}>
        <Text style={styles.hintText}>
          Launch Expo Go via `npm run start` to preview on devices, or visit the
          nginx endpoint at http://localhost:8080.
        </Text>
        <Text style={styles.hintText}>
          API base: {API_BASE_URL || "relative to current origin"}
        </Text>
        <Text style={styles.hintText}>
          Platform: {Platform.OS.toUpperCase()} • React {React.version}
        </Text>
      </View>
    </View>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: "#050505",
    alignItems: "center",
    justifyContent: "center",
    paddingTop: 64
  },
  hero: {
    width: "100%",
    paddingHorizontal: 24,
    marginBottom: 32
  },
  title: {
    fontSize: 28,
    fontWeight: "700",
    color: "#f1f5f9",
    marginBottom: 8
  },
  subtitle: {
    fontSize: 16,
    color: "#94a3b8"
  },
  card: {
    width: "90%",
    backgroundColor: "#111826",
    borderRadius: 16,
    padding: 24,
    shadowColor: "#000",
    shadowOpacity: 0.2,
    shadowOffset: { width: 0, height: 8 },
    shadowRadius: 16,
    elevation: 5
  },
  cardSection: {
    marginBottom: 16
  },
  label: {
    fontSize: 14,
    color: "#64748b",
    letterSpacing: 0.75
  },
  value: {
    fontSize: 18,
    color: "#f8fafc",
    fontWeight: "600"
  },
  hint: {
    marginTop: 24,
    paddingHorizontal: 24
  },
  hintText: {
    fontSize: 13,
    color: "#94a3b8",
    textAlign: "center"
  }
});
