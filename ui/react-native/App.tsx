import React, { FC } from "react";
import { NavigationContainer, DefaultTheme, Theme } from "@react-navigation/native";
import { StatusBar } from "expo-status-bar";
import { SafeAreaProvider } from "react-native-safe-area-context";

import AppNavigator from "./src/navigation/AppNavigator";
import { AuthProvider, useAuth } from "./src/providers/AuthProvider";
import LoginScreen from "./src/screens/LoginScreen";
import { COLORS } from "./src/theme/colors";

const navigationTheme: Theme = {
  ...DefaultTheme,
  colors: {
    ...DefaultTheme.colors,
    primary: COLORS.corePurple,
    background: COLORS.background,
    card: COLORS.surface,
    text: COLORS.textPrimary,
    border: COLORS.border,
    notification: COLORS.accent
  }
};

const RootView: FC = () => {
  const { state } = useAuth();

  if (!state.token) {
    return <LoginScreen />;
  }

  return <AppNavigator />;
};

const App: FC = () => (
  <SafeAreaProvider>
    <AuthProvider>
      <NavigationContainer theme={navigationTheme}>
        <StatusBar style="light" />
        <RootView />
      </NavigationContainer>
    </AuthProvider>
  </SafeAreaProvider>
);

export default App;
