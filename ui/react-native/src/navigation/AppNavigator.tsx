import React, { FC } from "react";
import { Ionicons } from "@expo/vector-icons";
import { BottomTabNavigationOptions, createBottomTabNavigator } from "@react-navigation/bottom-tabs";
import { RouteProp } from "@react-navigation/native";
import { TextStyle, TouchableOpacity } from "react-native";

import HomeScreen from "../screens/HomeScreen";
import SearchScreen from "../screens/SearchScreen";
import { COLORS } from "../theme/colors";
import { useAuth } from "../providers/AuthProvider";

export type RootTabParamList = {
  Home: undefined;
  Search: undefined;
};

const Tab = createBottomTabNavigator<RootTabParamList>();

export const screenOptions = ({
  route
}: {
  route: RouteProp<RootTabParamList, keyof RootTabParamList>;
}): BottomTabNavigationOptions => ({
  // Provide consistent navigation chrome across tabs with icon mapping.
  tabBarIcon: ({ color, size }: { color: string; size: number }) => {
    const iconName = route.name === "Home" ? "home" : "search";
    return <Ionicons name={iconName} color={color} size={size} />;
  },
  tabBarActiveTintColor: COLORS.textPrimary,
  tabBarInactiveTintColor: COLORS.textSecondary,
  tabBarStyle: {
    backgroundColor: COLORS.surface,
    borderTopColor: COLORS.border
  },
  headerStyle: {
    backgroundColor: COLORS.surface
  },
  headerTintColor: COLORS.textPrimary,
  headerTitleStyle: {
    fontWeight: "700" as TextStyle["fontWeight"]
  },
  headerTitleAlign: "center"
});

const LogoutButton: FC = () => {
  const { logout } = useAuth();
  return (
    <TouchableOpacity
      accessibilityRole="button"
      accessibilityLabel="Log out"
      onPress={() => {
        void logout();
      }}
      style={{ paddingHorizontal: 12, paddingVertical: 6 }}
    >
      <Ionicons name="log-out-outline" size={22} color={COLORS.accent} />
    </TouchableOpacity>
  );
};

const AppNavigator: FC = () => (
  <Tab.Navigator screenOptions={screenOptions}>
    <Tab.Screen
      name="Home"
      component={HomeScreen}
      options={{
        headerRight: () => <LogoutButton />
      }}
    />
    <Tab.Screen name="Search" component={SearchScreen} />
  </Tab.Navigator>
);

export default AppNavigator;
