import React, { FC } from "react";
import { NavigationContainer, DefaultTheme, type Theme } from "@react-navigation/native";
import { StatusBar } from "expo-status-bar";
import { SafeAreaProvider } from "react-native-safe-area-context";

import AppNavigator from "./src/navigation/AppNavigator";
import { AuthProvider, useAuth } from "./src/providers/AuthProvider";
import SplashScreen from "./src/screens/SplashScreen";
import LoginScreen from "./src/screens/LoginScreen";

const navigationTheme: Theme = {
  ...DefaultTheme,
  colors: {
    ...DefaultTheme.colors,
    background: "transparent"
  }
};

const RootView: FC = () => {
  const { state } = useAuth();

  if (state.loading) {
    return <SplashScreen />;
  }

  if (!state.token) {
    return <LoginScreen />;
  }

  return <AppNavigator />;
};

const App: FC = () => {
  return (
    <SafeAreaProvider>
      <AuthProvider>
        <NavigationContainer theme={navigationTheme}>
          <StatusBar style="light" />
          <RootView />
        </NavigationContainer>
      </AuthProvider>
    </SafeAreaProvider>
  );
};

export default App;
