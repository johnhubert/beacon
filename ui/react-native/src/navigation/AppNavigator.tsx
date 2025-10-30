import React, { FC } from "react";
import { Ionicons } from "@expo/vector-icons";
import { BottomTabNavigationOptions, createBottomTabNavigator } from "@react-navigation/bottom-tabs";
import { RouteProp } from "@react-navigation/native";
import { TextStyle } from "react-native";

import HomeScreen from "../screens/HomeScreen";
import SearchScreen from "../screens/SearchScreen";
import { COLORS } from "../theme/colors";

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
  }
});

const AppNavigator: FC = () => (
  <Tab.Navigator screenOptions={screenOptions}>
    <Tab.Screen name="Home" component={HomeScreen} />
    <Tab.Screen name="Search" component={SearchScreen} />
  </Tab.Navigator>
);

export default AppNavigator;
