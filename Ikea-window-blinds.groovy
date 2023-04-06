/**
 *
 * Ikea-window-blinds.groovy 
 * 
 * Description: 
 *  Create Window Blinds for Hubitat based on code from Wayne Man
 * 
 * 
 *	Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 *	in compliance with the License. You may obtain a copy of the License at:
 *
 *		http://www.apache.org/licenses/LICENSE-2.0
 *
 *	Unless required by applicable law or agreed to in writing, software distributed under the License is distributed
 *	on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License
 *	for the specific language governing permissions and limitations under the License.
 *
 *
 *  
 *  wman    - added fallback refreshing calls if blinds decide not to update status
 *  wman    - added position event updates to track realtime open percentage in dashboard
 *  wman    - added max open percentage preference (optional)
 *  wman    - added hardOpen function in case you need to reset blind height occasionally esp useful with above function
 *  wman    - added fix to execute the open function if "setposition 100" occurs - this allows Dashboard and Alexa to acknowledge max open level
 *  wman    - fixed bug with final state of blinds, now always ending with either open or closed state
 *  wman    - added hubitat standard debug and info toggles
 *  wman    - added on/off switch events and methods for better group and dashboard compatibility
 *  wman    - limited the reporting frequency of the blind level in order to prevent overloading the zigbee stack on hubitat and removed some legacy code
 *  ccruise - added "max close" preference and logic
 *  
 *  known issue: fingerprint does not seem to be working right 100% just manually assign the driver after device is added if it doesn't detect
 *
 *  IMPORTANT: remember to hit configure button after device is added and save preferences.
 */
import hubitat.zigbee.zcl.DataType

metadata {
    definition(name: "IKEA Window Shade", namespace: "ccruiser", author: "Casey Cruise") {
        capability "Actuator"
        capability "Configuration"
        capability "Refresh"
        capability "Window Shade"
        capability "Health Check"
	    capability "Switch"
	    capability "Switch Level"
        capability "Battery"

        command "pause"
        command "hardOpen"
        
       	attribute "lastCheckin", "String"
        attribute "lastOpened", "String"

        fingerprint inClusters: "0000,0001,0003,0004", manufacturer: "IKEA of Sweden", model: "FYRTUR block-out roller blind"
        fingerprint inClusters: "0000,0001,0003,0004", manufacturer: "IKEA of Sweden", model: "TREDANSEN block-out cellul blind"
    }

    preferences {

        input name: "openLevel", type: "number", defaultValue: 0, range: "0..100", title: "Max open level",
            description: "Max percentage open when Open function is called\n" +
                            "(delete or set value to 0 to disable this)"
        input name: "closeLevel", type: "number", defaultValue: 0, range: "0..100", title: "Max close level",
            description: "Max percentage close when Close function is called\n" +
                            "(delete or set value to 0 to disable this)"
        input name: "debugOutput", type: "bool", title: "Enable debug logging?", defaultValue: true
        input name: "descTextOutput", type: "bool", title: "Enable descriptionText logging?", defaultValue: true
    }
  
}

private getCLUSTER_BATTERY_LEVEL() { 0x0001 }
private getCLUSTER_WINDOW_COVERING() { 0x0102 }
private getCOMMAND_OPEN() { 0x00 }
private getCOMMAND_CLOSE() { 0x01 }
private getCOMMAND_PAUSE() { 0x02 }
private getCOMMAND_GOTO_LIFT_PERCENTAGE() { 0x05 }
private getATTRIBUTE_POSITION_LIFT() { 0x0008 }
private getATTRIBUTE_CURRENT_LEVEL() { 0x0000 }
private getCOMMAND_MOVE_LEVEL_ONOFF() { 0x04 }

private List<Map> collectAttributes(Map descMap) {
	List<Map> descMaps = new ArrayList<Map>()

	descMaps.add(descMap)

	if (descMap.additionalAttrs) {
		descMaps.addAll(descMap.additionalAttrs)
	}

	return descMaps
}

// Parse incoming device messages to generate events
def parse(String description) {
    if (debugOutput) log.debug "description:- ${description}"
    def now = new Date().format("yyyy MMM dd EEE h:mm:ss a", location.timeZone)
    //  send event for heartbeat    
    sendEvent(name: "lastCheckin", value: now)
    if (description?.startsWith("read attr -")) {
        Map descMap = zigbee.parseDescriptionAsMap(description)
        if (descMap?.clusterInt == CLUSTER_WINDOW_COVERING && descMap.value) {
            if (debugOutput) log.debug "attr: ${descMap?.attrInt}, value: ${descMap?.value}, descValue: ${Integer.parseInt(descMap.value, 16)}, ${device.getDataValue("model")}"
            List<Map> descMaps = collectAttributes(descMap)
            def liftmap = descMaps.find { it.attrInt == ATTRIBUTE_POSITION_LIFT }
            if (liftmap && liftmap.value) {
                def newLevel = 100 - zigbee.convertHexToInt(liftmap.value)
                levelEventHandler(newLevel)
            }
        } 
        if (descMap?.clusterInt == CLUSTER_BATTERY_LEVEL && descMap.value) {
            if (debugOutput) log.debug "attr: ${descMap?.attrInt}, value: ${descMap?.value}, descValue: ${Integer.parseInt(descMap.value, 16)}"
            sendEvent(name: "battery", value: Integer.parseInt(descMap.value, 16), unit: "%")
        }
    }
}

def levelEventHandler(currentLevel) {
    def lastLevel = device.currentValue("level")
    if (debugOutput) log.debug "levelEventHandle - currentLevel: ${currentLevel} lastLevel: ${lastLevel}"
    if (lastLevel == "undefined" || currentLevel == lastLevel) { //Ignore invalid reports
        if (debugOutput) log.debug "undefined lastLevel"
        runIn(3, "updateFinalState", [overwrite:true])
    } else {
        sendEvent(name: "level", value: currentLevel)
        sendEvent(name: "position", value: currentLevel)
        if (currentLevel == 0 || currentLevel >= 97) {
            sendEvent(name: "windowShade", value: currentLevel == 0 ? "closed" : "open")
            sendEvent(name: "switch", value: level == 0 ? "off" : "on", displayed: false)
        } else {
            if (lastLevel < currentLevel) {
                sendEvent([name:"windowShade", value: "opening"])
            } else if (lastLevel > currentLevel) {
                sendEvent([name:"windowShade", value: "closing"])
            }
        }
    }
    if (lastLevel != currentLevel) {
        if (debugOutput) log.debug "newlevel: ${newLevel} currentlevel: ${currentLevel} lastlevel: ${lastLevel}"
        runIn(5, refresh)
    }
}

def updateFinalState() {
    def level = device.currentValue("level")
    if (debugOutput) log.debug "updateFinalState: ${level}"
    if(closeLevel)
    {
        dataL = closeLevel.toInteger()
        sendEvent(name: "windowShade", value: level == dataL ? "closed" : "open")
        sendEvent(name: "switch", value: level == dataL ? "off" : "on", displayed: false)
    }
    else
    {
        sendEvent(name: "windowShade", value: level == 0 ? "closed" : "open")
        sendEvent(name: "switch", value: level == 0 ? "off" : "on", displayed: false)

    }
}

def close() {
    if (descTextOutput) log.info "close()"
    runIn(5, refresh)
    if (closeLevel) {
        if (descTextOutput) log.info "closeLevel() set to "+closeLevel
        dataC = closeLevel.toInteger()
        //zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_CLOSE)
        zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_GOTO_LIFT_PERCENTAGE, zigbee.convertToHexString(100 - dataC, 2))
    } else {
        zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_CLOSE)
    }
    
}

def open() {
    if (descTextOutput) log.info "open()"
    runIn(5, refresh)
    if (openLevel) {
        setLevel(openLevel)
    } else {
        hardOpen()
    }
}

def on() {
    open()
}

def off() {
    close()
}

def hardOpen() {
    if (descTextOutput) log.info "hardOpen()"
    runIn(5, refresh)
    zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_OPEN)
    
}

def setLevel(data, rate = null) {
    runIn(5, refresh)
    data = data.toInteger()
    if (descTextOutput) log.info "setLevel()"
    if (data == 100) {
        open()
    } else {
        def cmd
        cmd = zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_GOTO_LIFT_PERCENTAGE, zigbee.convertToHexString(100 - data, 2))
        return cmd
    }
}

def pause() {
    if (descTextOutput) log.info "pause()"
    zigbee.command(CLUSTER_WINDOW_COVERING, COMMAND_PAUSE)
}


def refresh() {
    if (descTextOutput) log.info "refresh()"
    
    def cmds
    cmds = zigbee.readAttribute(CLUSTER_WINDOW_COVERING, ATTRIBUTE_POSITION_LIFT) + zigbee.readAttribute(CLUSTER_BATTERY_LEVEL, 0x0021) 

    return cmds
}

def configure() {
    if (descTextOutput) log.info "configure()"

    if (debugOutput) log.debug "Configuring Reporting and Bindings."

    def cmds
    cmds = zigbee.configureReporting(CLUSTER_WINDOW_COVERING, ATTRIBUTE_POSITION_LIFT, DataType.UINT8, 3, 600, 0x01) + zigbee.configureReporting(CLUSTER_BATTERY_LEVEL, 0x0021, DataType.UINT8, 600, 21600, 0x01)

    return refresh() + cmds
}

def setPosition(value){
	setLevel(value)
}

def stopPositionChange()
{
    if (debugOutput) log.info "stopPositionChange()"
    pause()
}

def updated() {
	unschedule()
	if (debugOutput) runIn(1800,logsOff)
}

def logsOff(){
	log.warn "debug logging disabled..."
	device.updateSetting("debugOutput",[value:"false",type:"bool"])
}
