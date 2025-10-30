import React, { FC, useCallback, useState } from "react";
import {
  ActivityIndicator,
  KeyboardAvoidingView,
  Platform,
  Pressable,
  StyleSheet,
  Text,
  TextInput,
  View,
  Image
} from "react-native";

import { useAuth } from "../providers/AuthProvider";
import { COLORS } from "../theme/colors";

const LoginScreen: FC = () => {
  const { loginWithDemo } = useAuth();
  const [username, setUsername] = useState<string>("demo");
  const [password, setPassword] = useState<string>("demo");
  const [pending, setPending] = useState<boolean>(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  const handleDemoLogin = useCallback(async () => {
    setErrorMessage(null);
    try {
      setPending(true);
      await loginWithDemo(username.trim(), password);
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : "Demo credentials were rejected.";
      console.error("[login] demo login failed", error);
      setErrorMessage(message);
    } finally {
      setPending(false);
    }
  }, [loginWithDemo, password, username]);

  return (
    <KeyboardAvoidingView
      style={styles.container}
      behavior={Platform.OS === "ios" ? "padding" : undefined}
    >
      <View style={styles.logoWrapper}>
        <Image
          source={require("../../images/logo.png")}
          style={styles.logoImage}
          resizeMode="contain"
          accessible
          accessibilityLabel="Beacon logo"
        />
      </View>

      <View style={styles.card}>

        <Pressable style={[styles.secondaryButton, styles.disabledButton]} disabled>
          <Text style={styles.secondaryButtonText}>Login with Google (coming soon)</Text>
        </Pressable>

        <View style={styles.form}>
          <TextInput
            style={styles.input}
            value={username}
            onChangeText={setUsername}
            placeholder="Username"
            placeholderTextColor="rgba(255, 255, 255, 0.4)"
            autoCapitalize="none"
            autoComplete="username"
            accessible
            accessibilityLabel="Demo username"
          />
          <TextInput
            style={styles.input}
            value={password}
            onChangeText={setPassword}
            placeholder="Password"
            placeholderTextColor="rgba(255, 255, 255, 0.4)"
            secureTextEntry
            autoComplete="password"
            accessible
            accessibilityLabel="Demo password"
          />
          <Pressable
            style={[styles.primaryButton, pending && styles.disabled]}
            onPress={handleDemoLogin}
            disabled={pending}
          >
            {pending ? (
              <ActivityIndicator color={COLORS.corePurple} />
            ) : (
              <Text style={styles.primaryButtonText}>Sign in</Text>
            )}
          </Pressable>
        </View>

        {errorMessage ? <Text style={styles.error}>{errorMessage}</Text> : null}
      </View>
    </KeyboardAvoidingView>
  );
};

const styles = StyleSheet.create({
  container: {
    flex: 1,
    backgroundColor: COLORS.corePurple,
    paddingHorizontal: 24,
    justifyContent: "center"
  },
  logoWrapper: {
    alignItems: "center",
    marginBottom: 32
  },
  logoImage: {
    width: 160,
    height: 160
  },
  card: {
    backgroundColor: COLORS.surface,
    borderRadius: 24,
    padding: 24,
    borderWidth: 1,
    borderColor: COLORS.border,
    gap: 20
  },
  form: {
    gap: 12
  },
  input: {
    backgroundColor: COLORS.surfaceOverlay,
    borderRadius: 12,
    paddingHorizontal: 16,
    paddingVertical: 12,
    color: COLORS.textPrimary,
    borderWidth: 1,
    borderColor: COLORS.border
  },
  primaryButton: {
    backgroundColor: COLORS.textPrimary,
    borderRadius: 16,
    paddingVertical: 14,
    alignItems: "center"
  },
  primaryButtonText: {
    color: COLORS.corePurple,
    fontWeight: "700",
    fontSize: 16
  },
  secondaryButton: {
    backgroundColor: COLORS.surfaceOverlay,
    borderRadius: 14,
    paddingVertical: 12,
    alignItems: "center",
    borderWidth: 1,
    borderColor: COLORS.border
  },
  secondaryButtonText: {
    color: COLORS.textSecondary,
    fontWeight: "600"
  },
  disabledButton: {
    opacity: 0.6
  },
  disabled: {
    opacity: 0.6
  },
  error: {
    marginTop: 12,
    color: COLORS.danger,
    fontSize: 13,
    textAlign: "center"
  }
});

export default LoginScreen;
