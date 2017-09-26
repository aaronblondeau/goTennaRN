import React from 'react';
import {
  AppRegistry
} from 'react-native';

import {
  StackNavigator,
} from 'react-navigation';

import HomeScreen from './screens/HomeScreen';
import PairScreen from './screens/PairScreen';
import MessageScreen from './screens/MessageScreen';

const App = StackNavigator({
  Home: { screen: HomeScreen },
  Pair: { screen: PairScreen },
  Message: { screen: MessageScreen },
});

AppRegistry.registerComponent('GoTennaReactNative', () => App);
