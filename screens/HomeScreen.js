import React, { Component } from 'react';
import {
  StyleSheet,
} from 'react-native';
import { Container, Content, Button, Text } from 'native-base';

import {observer} from 'mobx-react';
import applicationState from '../ApplicationState'

@observer
export default class HomeScreen extends Component {
  static navigationOptions = ({ navigation }) => ({
    title: 'goTenna',
  });

  render() {
    const { navigate } = this.props.navigation;
    return (
      <Container>
          <Content>
              <Button style={{margin: 10}} block onPress={() => navigate('Pair') }>
                  <Text>Pair With goTenna</Text>
              </Button>
          </Content>
      </Container>
    );
  }
}

const styles = StyleSheet.create({
  
});