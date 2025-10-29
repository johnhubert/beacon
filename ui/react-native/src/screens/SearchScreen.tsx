import React, { FC } from "react";
import { StyleSheet, Text, View } from "react-native";
import { LinearGradient } from "expo-linear-gradient";
import { Ionicons } from "@expo/vector-icons";

const SearchScreen: FC = () => (
  <LinearGradient colors={["#020617", "#0f172a"]} style={styles.container}>
    <View style={styles.content}>
      <Ionicons name="search-outline" size={48} color="#38bdf8" />
      <Text style={styles.title}>Search coming soon</Text>
      <Text style={styles.subtitle}>
        Soon you will be able to discover representatives by name, district, or
        issue alignment.
      </Text>
    </View>
  </LinearGradient>
);

const styles = StyleSheet.create({
  container: {
    flex: 1,
    justifyContent: "center",
    alignItems: "center"
  },
  content: {
    alignItems: "center",
    paddingHorizontal: 24
  },
  title: {
    marginTop: 16,
    fontSize: 24,
    fontWeight: "700",
    color: "#f8fafc"
  },
  subtitle: {
    marginTop: 12,
    fontSize: 15,
    lineHeight: 22,
    color: "#cbd5f5",
    textAlign: "center"
  }
});

export default SearchScreen;
