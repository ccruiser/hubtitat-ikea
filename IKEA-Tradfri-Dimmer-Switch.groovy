/**
 *  IKEA Tr&aring;dfri Dimmer
 *
 *  Copyright 2017 Jonas Laursen
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *  in compliance with the License. You may obtain a copy of the License at:
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *  on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *  for the specific language governing permissions and limitations under the License.
 *
 *  Updated by Royski to change the clockwise and anti-clockwise on/off
 *   2023-Apr-05 : Updated Zigbee parsing with accurate 
 *               : added checkPresence() method
 *               : updated logging statements to include description details
 *               : re worked logic for parsing
 */
metadata {
	definition (name: "IKEA Trådfri Dimmer as Switch", namespace: "dk.decko", author: "Jonas Laursen") {
        capability "Sensor"
		capability "Configuration"
		capability "Switch"
        capability "Refresh"
		
	fingerprint endpointId: "01", profileId: "0104", deviceId: "0810", deviceVersion: "02", inClusters: "0000, 0001, 0003, 0009, 0B05, 1000", outClusters: "0003, 0004, 0006, 0008, 0019, 1000"
	//fingerprint endpointId: "01", profileId: "C05E", deviceId: "0810", deviceVersion: "02", inClusters: "0000, 0001, 0003, 0009, 0B05, 1000", outClusters: "0003, 0004, 0006, 0008, 0019, 1000"
	}
	
	main("switch")
}

preferences {
	
		input name: "logEnable", type: "bool", title: "Enable debug logging", defaultValue: true
		input name: "txtEnable", type: "bool", title: "Enable descriptionText logging", defaultValue: true
	
}

def logsOff(){
	log.warn "Ikea Switch debug logging disabled."
	device.updateSetting("logEnable",[value:"false",type:"bool"])
}

def updated(){
    log.info "Ikea Switch updated invoked."
	log.warn "Ikea Switch debug logging is: ${logEnable == true}"
	log.warn "Ikea Switch description logging is: ${txtEnable == true}"
	if (logEnable) runIn(1800,logsOff)
}

// parse events into attributes
def parse(String description) {
	//log.debug "Catch all: $description"
	if (logEnable) log.debug zigbee.parseDescriptionAsMap(description)

    //String tZigBeeOn   = "0104 0102 01 01 0040 00 EED7 01 00 0000 00 00"
    //String tZigBeeOff  = "0104 0102 01 01 0040 00 EED7 01 00 0000 01 00"
    //String tZigBeeHold = "0104 0102 01 01 0040 00 EED7 01 00 0000 02 00" 
	def map = zigbee.parseDescriptionAsMap(description)
    int intVarCount = 0
    int intCmdArrayIndex = 12
    String tZigBeeCmd = "-1"
    String[] zbVals = description.split(' ')
    for( String values : zbVals )
    {
      intVarCount = intVarCount + 1
      if (intVarCount == intCmdArrayIndex)
      {
          if (logEnable) log.debug "IKEA Switch command found a varible at index "+intCmdArrayIndex+" '"+values+"'"
          tZigBeeCmd = values
          break;
      }
    }
    // the command values for the "open" and "close" are reversed per the switch label
    switch(tZigBeeCmd) {

          case "00": 
            if (txtEnable) log.info "IKEA Switch set to on"
            if (logEnable) log.debug "zigbee command: "+description
            sendEvent(name: "switch", value: "on")
          break; 
          case "01": 
            if (txtEnable) log.info "IKEA Switch set to off"
            if (logEnable) log.debug "zigbee command: "+description
            sendEvent(name: "switch", value: "off")
          break; 
          case "02": 
            log.warn "IKEA Switch button held; no implemented logic"
            if (logEnable) log.debug "zigbee command: "+description
          break;
          default: 
            log.warn "Ikea Switch unknown command for "+description 
          break; 
    }
}
  

def off() {
	sendEvent(name: "switch", value: "off")
}

def on() {
	sendEvent(name: "switch", value: "on")
}


def refresh() {
	if (txtEnable) log.info "Ikea Switch dimmer Refresh invoked"
    zigbee.onOffRefresh() + zigbee.onOffConfig()
}

def configure() {
	if (logEnable) log.debug "Ikea Switch configure invoked"
	["zdo bind 0x${device.deviceNetworkId} 0x01 0x01 8 {${device.zigbeeId}} {}"]
}

def checkPresence() {
    if (logEnable) log.debug "Ikea Switch checkPresence invoked; no action taken"
}

