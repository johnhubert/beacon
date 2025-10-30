import React, { FC } from "react";
import { StyleSheet, Text, View } from "react-native";

import { COLORS } from "../theme/colors";

const SearchScreen: FC = () => {
  // Placeholder copy until dedicated search UI is implemented.
  return (
    <View style={styles.container}>
      <Text style={styles.title}>Search</Text>
      <Text style={styles.subtitle}>Search capabilities are coming soon.</Text>
      <Text style={styles.body}>
        Use the Home tab to explore a curated representative while we finalize the search experience.
      </Text>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: COLORS.background,
    padding: 24,
    justifyContent: "center",
    gap: 12
  },
  title: {
    fontSize: 28,
    fontWeight: "800",
    color: COLORS.textPrimary
  },
  subtitle: {
    fontSize: 16,
    fontWeight: "600",
    color: COLORS.textSecondary
  },
  body: {
    fontSize: 14,
    lineHeight: 20,
    color: COLORS.textSecondary
  }
});

export default SearchScreen;
