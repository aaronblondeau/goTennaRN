import React from 'react';
import {
  AppRegistry
} from 'react-native';

import {
  StackNavigator,
} from 'react-navigation';

import HomeScreen from './screens/HomeScreen';
import PairScreen from './screens/PairScreen';

const App = StackNavigator({
  Home: { screen: HomeScreen },
  Pair: { screen: PairScreen },
});

AppRegistry.registerComponent('GoTennaReactNative', () => App);
