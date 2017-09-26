import React, { Component } from 'react';
import {
  StyleSheet,
  Switch
} from 'react-native';
import { Container, Content, Button, Text, Form, Item, Label, Input, Spinner } from 'native-base';

import {observer} from 'mobx-react';
import applicationState from '../ApplicationState'

@observer
export default class MessageScreen extends Component {
  static navigationOptions = ({ navigation }) => ({
    title: 'Messages',
  });

  render() {
    const { navigate } = this.props.navigation;

    return (
      <Container>
          <Content>
              <Form>
                <Item fixedLabel>
                  <Label>Recipient GID</Label>
                  <Input keyboardType="numeric" onChangeText={(text) => {applicationState.one_to_one_recipient_gid = parseInt(text)}} value={ !Number.isNaN(applicationState.one_to_one_recipient_gid) ? applicationState.one_to_one_recipient_gid+"" : "0" } />
                </Item>
                <Item fixedLabel>
                  <Label>Message</Label>
                  <Input onChangeText={(text) => {applicationState.one_to_one_message = text}} value={applicationState.one_to_one_message} />
                </Item>
              </Form>

              {applicationState.paired != true &&
                <Text>No goTenna is paired - please connect first!</Text>
              }
              
              <Button style={{margin: 10}} block disabled={!applicationState.paired} onPress={() => applicationState.sendOneToOne() }>
                  <Text>Send One To One Message</Text>
              </Button>

              

          </Content>
      </Container>
    );
  }
}

const styles = StyleSheet.create({
  
});