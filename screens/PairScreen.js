import React, { Component } from 'react';
import {
  StyleSheet,
  Switch,
  Platform
} from 'react-native';
import { Container, Content, Button, Text, Form, Item, Label, Input, Spinner } from 'native-base';

import {observer} from 'mobx-react';
import applicationState from '../ApplicationState'

@observer
export default class PairScreen extends Component {
  static navigationOptions = ({ navigation }) => ({
    title: 'Pair With Device',
  });

  componentDidMount() {
    console.log("~~ PairScreen requesting state update");
    applicationState.getState();
  }

  render() {
    const { navigate } = this.props.navigation;

    return (
      <Container>
          <Content>
              <Form>
                  { Platform.OS !== 'ios' &&
                  <Item style={{margin: 10}} inlineLabel>
                      <Label>Remember goTenna</Label>
                      <Switch disabled={applicationState.scanning || applicationState.paired} onValueChange={(value) => applicationState.remember_paired_device = value} value={applicationState.remember_paired_device}></Switch>
                  </Item>
                  }

                  <Item style={{margin: 10}} inlineLabel>
                      <Label>Look for Mesh Device</Label>
                      <Switch disabled={applicationState.scanning || applicationState.paired} onValueChange={(value) => applicationState.is_mesh_device = value} value={applicationState.is_mesh_device}></Switch>
                  </Item>
              </Form>
              
              <Button style={{margin: 10}} block disabled={!applicationState.gotenna_configured || applicationState.scanning || applicationState.paired} onPress={() => applicationState.scanForGoTennas() }>
                  <Text>Scan for Device</Text>
              </Button>

              {!applicationState.bluetooth_available &&
                <Text>Bluetooth is not available on this device!</Text>
              }

              {!applicationState.bluetooth_enabled &&
                <Text>Bluetooth is not enabled on this device!</Text>
              }

              {applicationState.scanning && 
                <Spinner />
              }

              {(applicationState.pairing_timed_out && !applicationState.paired) &&
                <Text>Unable to find a goTenna!</Text>
              }

              {applicationState.paired &&
                <Text>A goTenna is paired!</Text>
              }

              {applicationState.paired &&
                <Button style={{margin: 10}} block info disabled={applicationState.scanning} onPress={() => applicationState.getSystemInfo() }>
                    <Text>Get System Info</Text>
                </Button>
              }

              {applicationState.paired &&
                <Button style={{margin: 10}} block info disabled={applicationState.scanning} onPress={() => applicationState.echo() }>
                    <Text>Echo</Text>
                </Button>
              }

              {applicationState.paired &&
              <Form>
                <Item fixedLabel>
                  <Label>GID</Label>
                  <Input keyboardType="numeric" onChangeText={(text) => {applicationState.gid = parseInt(text)}} value={ !Number.isNaN(applicationState.gid) ? applicationState.gid+"" : "0" } />
                </Item>
                <Item fixedLabel>
                  <Label>GID Name</Label>
                  <Input onChangeText={(text) => {applicationState.gid_name = text}} value={applicationState.gid_name} />
                </Item>
              </Form>
              }

              {applicationState.paired && 
                <Button style={{margin: 10}} block info onPress={() => applicationState.setGID() }>
                    <Text>Set GID</Text>
                </Button>
              }

              {applicationState.setting_gid && 
                <Spinner />
              }

              {applicationState.paired &&
                <Button style={{margin: 10}} block danger disabled={applicationState.scanning} onPress={() => applicationState.disconnect() }>
                    <Text>Disconnect</Text>
                </Button>
              }

          </Content>
      </Container>
    );
  }
}

const styles = StyleSheet.create({
  
});