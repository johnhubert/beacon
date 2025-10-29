import React, { FC } from "react";
import { ActivityIndicator, StyleSheet, Text, View } from "react-native";
import { LinearGradient } from "expo-linear-gradient";

const SplashScreen: FC = () => (
  <LinearGradient colors={["#0f172a", "#1e293b"]} style={styles.container}>
    <View style={styles.content}>
      <Text style={styles.title}>Beacon</Text>
      <Text style={styles.subtitle}>Civic accountability, illuminated.</Text>
      <ActivityIndicator size="large" color="#38bdf8" style={styles.spinner} />
    </View>
  </LinearGradient>
);

const styles = StyleSheet.create({
  container: {
    flex: 1,
    alignItems: "center",
    justifyContent: "center"
  },
  content: {
    alignItems: "center"
  },
  title: {
    fontSize: 42,
    fontWeight: "800",
    color: "#f8fafc",
    letterSpacing: 2,
    textTransform: "uppercase"
  },
  subtitle: {
    marginTop: 12,
    fontSize: 16,
    color: "#cbd5f5"
  },
  spinner: {
    marginTop: 32
  }
});

export default SplashScreen;
