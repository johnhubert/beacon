import React, { FC } from "react";
import { createBottomTabNavigator } from "@react-navigation/bottom-tabs";
import { Ionicons } from "@expo/vector-icons";

import OfficialProfileScreen from "../screens/OfficialProfileScreen";
import SearchScreen from "../screens/SearchScreen";

type RootTabParamList = {
  Home: undefined;
  Search: undefined;
};

const Tab = createBottomTabNavigator<RootTabParamList>();

const iconForRoute = (routeName: keyof RootTabParamList): keyof typeof Ionicons.glyphMap => {
  switch (routeName) {
    case "Home":
      return "home-outline";
    case "Search":
      return "search-outline";
    default:
      return "ellipse-outline";
  }
};

const AppNavigator: FC = () => (
  <Tab.Navigator
    screenOptions={({ route }) => ({
      headerShown: false,
      tabBarStyle: {
        backgroundColor: "rgba(2, 6, 23, 0.92)",
        borderTopColor: "rgba(148, 163, 184, 0.2)",
        paddingBottom: 6,
        paddingTop: 6,
        height: 64
      },
      tabBarActiveTintColor: "#38bdf8",
      tabBarInactiveTintColor: "#94a3b8",
      tabBarIcon: ({ color, size }) => (
        <Ionicons name={iconForRoute(route.name as keyof RootTabParamList)} size={size ?? 24} color={color ?? "#94a3b8"} />
      )
    })}
  >
    <Tab.Screen name="Home" component={OfficialProfileScreen} />
    <Tab.Screen name="Search" component={SearchScreen} />
  </Tab.Navigator>
);

export default AppNavigator;
