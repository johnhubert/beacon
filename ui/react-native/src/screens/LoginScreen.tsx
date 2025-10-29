import React, { FC, useCallback, useEffect, useMemo, useState } from "react";
import {
  ActivityIndicator,
  Alert,
  ImageBackground,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
  type ImageSourcePropType
} from "react-native";
import * as WebBrowser from "expo-web-browser";
import * as Google from "expo-auth-session/providers/google";
import Constants from "expo-constants";
import { LinearGradient } from "expo-linear-gradient";
import { Ionicons } from "@expo/vector-icons";

import { useAuth } from "../providers/AuthProvider";

WebBrowser.maybeCompleteAuthSession();

const BACKGROUND_IMAGE: ImageSourcePropType = {
  uri: "https://images.unsplash.com/photo-1505843513577-22bb7d21e455?auto=format&fit=crop&w=1600&q=80"
};

const DUMMY_CLIENT_ID = "https://auth.local.invalid/placeholder";

const LoginScreen: FC = () => {
  const { loginWithDemo, loginWithGoogleToken, isDevMode, options } = useAuth();
  const [username, setUsername] = useState<string>("demo");
  const [password, setPassword] = useState<string>("demo");
  const [pending, setPending] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string>("");

  const googleIds = useMemo(
    () => ({
      web: options.webClientId ?? DUMMY_CLIENT_ID,
      android: options.androidClientId ?? DUMMY_CLIENT_ID,
      ios: options.iosClientId ?? DUMMY_CLIENT_ID
    }),
    [options.webClientId, options.androidClientId, options.iosClientId]
  );

  const isExpoGo = Constants.appOwnership === "expo";

  const clientIdsForAuth = useMemo(
    () => ({
      web: googleIds.web,
      android: isExpoGo ? googleIds.web : googleIds.android,
      ios: isExpoGo ? googleIds.web : googleIds.ios
    }),
    [googleIds, isExpoGo]
  );

  const [request, googleResponse, promptAsync] = Google.useIdTokenAuthRequest({
    clientId: clientIdsForAuth.web,
    iosClientId: clientIdsForAuth.ios,
    androidClientId: clientIdsForAuth.android
  });

  const handleGoogleToken = useCallback(
    async (idToken: string) => {
      try {
        setPending(true);
        await loginWithGoogleToken(idToken);
      } catch (error: unknown) {
        console.error("Google login failed", error);
        const message = error instanceof Error ? error.message : "Unable to sign in with Google.";
        setErrorMessage(message);
      } finally {
        setPending(false);
      }
    },
    [loginWithGoogleToken]
  );

  const handleDemoLogin = useCallback(async () => {
    setErrorMessage("");
    try {
      setPending(true);
      await loginWithDemo(username.trim(), password);
    } catch (error: unknown) {
      console.error("Demo login failed", error);
      const message = error instanceof Error ? error.message : "Demo credentials were rejected.";
      setErrorMessage(message);
    } finally {
      setPending(false);
    }
  }, [loginWithDemo, password, username]);

  useEffect(() => {
    if (googleResponse?.type === "success") {
      const idToken = googleResponse.params?.id_token;
      if (idToken) {
        handleGoogleToken(idToken);
      }
    } else if (googleResponse?.type === "error") {
      setErrorMessage("Google authentication failed. Please try again.");
    }
  }, [googleResponse, handleGoogleToken]);

  const onGooglePress = useCallback(() => {
    setErrorMessage("");
    const platformClientId = (() => {
      if (!options.googleEnabled) {
        return null;
      }
      if (isExpoGo) {
        return options.webClientId;
      }
      if (Platform.OS === "android") {
        return options.androidClientId ?? options.webClientId;
      }
      if (Platform.OS === "ios") {
        return options.iosClientId ?? options.webClientId;
      }
      return options.webClientId;
    })();

    if (!platformClientId) {
      Alert.alert(
        "Google Sign-in",
        "Google authentication is not currently enabled. Please contact support or use demo credentials."
      );
      return;
    }
    promptAsync().catch((error: Error) => {
      console.error("Google prompt failed", error);
      setErrorMessage(error.message || "Unable to start Google authentication.");
    });
  }, [options.googleEnabled, options.androidClientId, options.iosClientId, options.webClientId, promptAsync]);

  return (
    <ImageBackground source={BACKGROUND_IMAGE} style={styles.background}>
      <LinearGradient
        colors={["rgba(15, 23, 42, 0.92)", "rgba(15, 23, 42, 0.75)"]}
        style={styles.overlay}
      >
        <KeyboardAvoidingView
          behavior={Platform.OS === "ios" ? "padding" : "height"}
          style={styles.container}
        >
          <View style={styles.header}>
            <Text style={styles.title}>Beacon</Text>
            <Text style={styles.subtitle}>
              Track your representatives across the issues that matter.
            </Text>
          </View>

          <View style={styles.card}>
            <Pressable
              style={[styles.primaryButton, pending && styles.disabled]}
              onPress={onGooglePress}
              disabled={pending || !request || !options.googleEnabled}
            >
              {pending ? (
                <ActivityIndicator color="#0f172a" />
              ) : (
                <>
                  <Ionicons name="logo-google" size={20} color="#0f172a" style={styles.buttonIcon} />
                  <Text style={styles.primaryButtonText}>Continue with Google</Text>
                </>
              )}
            </Pressable>

            {isDevMode && (
              <View style={styles.demoSection}>
                <Text style={styles.sectionLabel}>Development access</Text>
                <TextInput
                  autoCapitalize="none"
                  autoCorrect={false}
                  value={username}
                  editable={!pending}
                  onChangeText={setUsername}
                  style={styles.input}
                  placeholder="Username"
                  placeholderTextColor="#94a3b8"
                />
                <TextInput
                  secureTextEntry
                  value={password}
                  editable={!pending}
                  onChangeText={setPassword}
                  style={styles.input}
                  placeholder="Password"
                  placeholderTextColor="#94a3b8"
                />
                <Pressable
                  style={[styles.secondaryButton, pending && styles.disabled]}
                  onPress={handleDemoLogin}
                  disabled={pending}
                >
                  {pending ? (
                    <ActivityIndicator color="#e2e8f0" />
                  ) : (
                    <Text style={styles.secondaryButtonText}>Sign in with demo credentials</Text>
                  )}
                </Pressable>
              </View>
            )}

            {errorMessage ? <Text style={styles.error}>{errorMessage}</Text> : null}
          </View>

          <Text style={styles.footerText}>
            By continuing, you agree to the civic data usage guidelines.
          </Text>
        </KeyboardAvoidingView>
      </LinearGradient>
    </ImageBackground>
  );
};

const styles = StyleSheet.create({
  background: {
    flex: 1,
    resizeMode: "cover"
  },
  overlay: {
    flex: 1,
    paddingHorizontal: 24,
    paddingVertical: 48
  },
  container: {
    flex: 1,
    justifyContent: "center"
  },
  header: {
    marginBottom: 32
  },
  title: {
    fontSize: 48,
    fontWeight: "800",
    color: "#f8fafc",
    letterSpacing: 3
  },
  subtitle: {
    marginTop: 12,
    fontSize: 18,
    color: "#cbd5f5",
    lineHeight: 26
  },
  card: {
    backgroundColor: "rgba(15, 23, 42, 0.85)",
    borderRadius: 20,
    padding: 24,
    shadowColor: "#0f172a",
    shadowOpacity: 0.35,
    shadowRadius: 18,
    shadowOffset: { width: 0, height: 12 }
  },
  primaryButton: {
    backgroundColor: "#f8fafc",
    borderRadius: 12,
    paddingVertical: 14,
    flexDirection: "row",
    justifyContent: "center",
    alignItems: "center"
  },
  primaryButtonText: {
    fontSize: 16,
    fontWeight: "700",
    color: "#0f172a"
  },
  buttonIcon: {
    marginRight: 12
  },
  demoSection: {
    marginTop: 24
  },
  sectionLabel: {
    color: "#cbd5f5",
    marginBottom: 12,
    fontSize: 14,
    fontWeight: "600"
  },
  input: {
    backgroundColor: "rgba(30, 41, 59, 0.9)",
    borderRadius: 10,
    paddingHorizontal: 16,
    paddingVertical: 12,
    color: "#f8fafc",
    marginBottom: 12,
    borderWidth: 1,
    borderColor: "rgba(148, 163, 184, 0.3)"
  },
  secondaryButton: {
    marginTop: 4,
    backgroundColor: "rgba(59, 130, 246, 0.25)",
    borderRadius: 10,
    paddingVertical: 12,
    alignItems: "center"
  },
  secondaryButtonText: {
    color: "#e2e8f0",
    fontWeight: "600",
    fontSize: 15
  },
  disabled: {
    opacity: 0.6
  },
  error: {
    marginTop: 16,
    color: "#fca5a5",
    fontSize: 14,
    textAlign: "center"
  },
  footerText: {
    marginTop: 24,
    color: "#94a3b8",
    fontSize: 13,
    textAlign: "center"
  }
});

export default LoginScreen;
