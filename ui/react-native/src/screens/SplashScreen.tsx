import React, { FC } from "react";
import { Image, StyleSheet, Text, View } from "react-native";

import { COLORS } from "../theme/colors";

const SplashScreen: FC = () => {
  // Minimal branded loading view while auth bootstrap runs.
  return (
    <View style={styles.container}>
      <Image source={require("../../images/logo.png")} style={styles.logo} resizeMode="contain" />
      <Text style={styles.title}>Welcome to Beacon</Text>
    </View>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: COLORS.corePurple,
    alignItems: "center",
    justifyContent: "center"
  },
  logo: {
    width: 160,
    height: 160
  },
  title: {
    marginTop: 24,
    fontSize: 20,
    fontWeight: "700",
    color: COLORS.textPrimary
  }
});

export default SplashScreen;
